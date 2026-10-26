package com.example.util

import android.text.Html
import java.util.Locale

/** Builds a short, source-grounded digest without adding facts that were not in the source text. */
object NewsSummary {
    private val sentenceBoundary = Regex("(?<=[.!?])\\s+")
    private val wordPattern = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "ama", "ancak", "artık", "bir", "bu", "çok", "daha", "da", "de", "diye", "en", "gibi",
        "ile", "için", "kadar", "karşı", "ki", "olan", "olarak", "oldu", "sonra", "ve", "veya",
        "yeni", "üzere",
    )

    fun concise(sourceText: String, title: String = "", maxCharacters: Int = 280): String {
        val text = cleanText(sourceText)
        if (text.isBlank()) return ""
        val hasTruncatedEnding = TRUNCATED_END.containsMatchIn(text)
        val sourceWithoutTruncation = if (hasTruncatedEnding) text.replace(TRUNCATED_END, "").trimEnd() else text

        val titleKey = normalize(title)
        if (normalize(text) == titleKey) return ""
        val titleWords = wordPattern.findAll(titleKey)
            .map { it.value }
            .filter { it.length > 2 && it !in stopWords }
            .toSet()
        val allSentences = sentenceBoundary.split(sourceWithoutTruncation)
            .map { it.trim().trimStart('•', '-', '–', '—').trim() }
            .filter { it.length >= MIN_SENTENCE_CHARS }
            .distinctBy(::normalize)
            .filter { sentence -> normalize(sentence) != titleKey }
            .filterNot(::isBoilerplateLead)
            .filterNot { it.trimEnd().endsWith('?') }
            .filter(::containsSourceFact)
        val sentences = if (hasTruncatedEnding && allSentences.size > 1) allSentences.dropLast(1) else allSentences

        if (sentences.isEmpty()) return ""
        val chosen = sentences.withIndex()
            .sortedByDescending { (index, sentence) -> score(sentence, index, titleWords, title) }
            .take(MAX_SUMMARY_SENTENCES)
            .sortedBy { it.index }
            .map { it.value }

        val result = StringBuilder()
        for (sentence in chosen) {
            val required = sentence.length + if (result.isEmpty()) 0 else 1
            if (result.length + required > maxCharacters) continue
            if (result.isNotEmpty()) result.append(' ')
            result.append(sentence)
        }
        return if (result.isNotEmpty()) result.toString() else truncateAtWord(
            sentences.maxByOrNull { score(it, 0, titleWords, title) } ?: sentences.first(),
            maxCharacters,
        )
    }

    /** Prefer a generated stored summary, but reject a verbatim one-sentence source lead. */
    fun bestAvailableSummary(
        summaryCandidate: String,
        sourceText: String,
        title: String,
        maxCharacters: Int = 280,
    ): String {
        val candidate = cleanText(summaryCandidate).trim()
        if (candidate.isBlank()) return concise(sourceText, title, maxCharacters)
        val candidateSentences = sentenceBoundary.split(candidate).map(String::trim).filter(String::isNotBlank)
        val lead = sentenceBoundary.split(cleanText(sourceText)).firstOrNull()?.trim().orEmpty()
        val candidateKey = normalize(candidate)
        val leadKey = normalize(lead)
        val sourceSentences = sentenceBoundary.split(cleanText(sourceText))
            .map(String::trim)
            .filter(String::isNotBlank)
        val candidateRepeatsSourceLead = candidateSentences.isNotEmpty() &&
            candidateSentences.size <= 2 &&
            sourceSentences.size > candidateSentences.size &&
            candidateSentences.indices.all { index ->
                normalize(candidateSentences[index]) == normalize(sourceSentences[index])
            }
        val isVerbatimLead = candidateRepeatsSourceLead ||
            (candidateSentences.size == 1 && leadKey.isNotBlank() &&
                (candidateKey == leadKey || candidateKey.startsWith(leadKey) || leadKey.startsWith(candidateKey)))
        val titleKey = normalize(title)
        val missesAskedTime = TIME_QUESTION.containsMatchIn(titleKey) &&
            !DATE_OR_TIME.containsMatchIn(candidate)
        val isGenericOrUnfounded = BOILERPLATE_LEAD.containsMatchIn(candidate) ||
            (candidateSentences.size == 1 && !containsSourceFact(candidate))
        if (!isVerbatimLead && !missesAskedTime && !isGenericOrUnfounded) {
            return truncateAtWord(candidate, maxCharacters)
        }
        return concise(sourceText, title, maxCharacters).takeIf { normalize(it) != candidateKey }.orEmpty()
    }
    /** Removes source sentences already shown in the short summary to avoid a repeated lead. */
    fun remainingSourceText(sourceText: String, summary: String): String {
        val sourceSentences = sentenceBoundary.split(cleanText(sourceText)).map(String::trim).filter(String::isNotBlank)
        val summarySentences = sentenceBoundary.split(cleanText(summary)).map(::normalize).filter(String::isNotBlank).toSet()
        var firstRemaining = 0
        while (firstRemaining < sourceSentences.size) {
            val sourceKey = normalize(sourceSentences[firstRemaining])
            val alreadySummarized = sourceKey in summarySentences || summarySentences.any { summaryKey ->
                sourceKey.length >= MIN_SENTENCE_CHARS && summaryKey.startsWith(sourceKey) ||
                    summaryKey.length >= MIN_SENTENCE_CHARS && sourceKey.startsWith(summaryKey)
            }
            if (!alreadySummarized) break
            firstRemaining++
        }
        return sourceSentences.drop(firstRemaining).joinToString(" ").trim()
    }

    fun paragraphs(sourceText: String): List<String> =
        sentenceBoundary.split(cleanText(sourceText))
            .map(String::trim)
            .filter(String::isNotBlank)
            .chunked(3)
            .map { it.joinToString(" ") }

    private fun score(sentence: String, index: Int, titleWords: Set<String>, title: String): Int {
        val words = wordPattern.findAll(normalize(sentence)).map { it.value }.toSet()
        val titleMatches = words.count { it in titleWords }
        val lengthScore = when (sentence.length) {
            in 70..230 -> 2
            in 45..300 -> 1
            else -> 0
        }
        val hasConcreteDetail = DIGIT.containsMatchIn(sentence) || DATE_OR_TIME.containsMatchIn(sentence)
        val hasNewsAction = FACT_VERB.containsMatchIn(sentence)
        val normalizedTitle = normalize(title)
        val asksForTime = TIME_QUESTION.containsMatchIn(normalizedTitle)
        val asksWhy = WHY_QUESTION.containsMatchIn(normalizedTitle)
        val asksForCause = CAUSE_QUESTION.containsMatchIn(normalizedTitle)
        val asksForResult = RESULT_QUESTION.containsMatchIn(normalizedTitle)
        val asksYesNo = YES_NO_QUESTION.containsMatchIn(title.lowercase(Locale.forLanguageTag("tr-TR")))
        val answersTime = asksForTime && DATE_OR_TIME.containsMatchIn(sentence)
        val answersWhy = asksWhy && WHY_ANSWER.containsMatchIn(normalize(sentence))
        val answersCause = asksForCause && CAUSE_ANSWER.containsMatchIn(normalize(sentence))
        val answersResult = asksForResult && RESULT_ANSWER.containsMatchIn(normalize(sentence))
        val answersYesNo = asksYesNo && titleMatches >= 2 && QUESTION_ANSWER_FACT.containsMatchIn(sentence)
        val positionScore = (index.coerceAtMost(4) - 2).coerceIn(-2, 2)
        return titleMatches * 2 + lengthScore + (if (hasConcreteDetail) 5 else 0) +
            (if (hasNewsAction) 4 else 0) + (if (answersTime) 12 else 0) +
            (if (answersWhy) 10 else 0) + (if (answersCause) 8 else 0) +
            (if (answersResult) 8 else 0) + (if (answersYesNo) 12 else 0) + positionScore -
            (if (isBoilerplateLead(sentence)) 8 else 0)
    }

    private fun isBoilerplateLead(sentence: String): Boolean = BOILERPLATE_LEAD.containsMatchIn(sentence)

    private fun containsSourceFact(sentence: String): Boolean =
        DIGIT.containsMatchIn(sentence) || DATE_OR_TIME.containsMatchIn(sentence) ||
            FACT_VERB.containsMatchIn(sentence) || RESULT_ANSWER.containsMatchIn(normalize(sentence))

    private fun cleanText(value: String): String = Html.fromHtml(
        value.replace("<script\\b[^>]*>.*?</script>".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace("<style\\b[^>]*>.*?</style>".toRegex(RegexOption.IGNORE_CASE), " "),
        Html.FROM_HTML_MODE_LEGACY,
    ).toString()
        .replace('\u00a0', ' ')
        .replace("[\\s\\u200B]+".toRegex(), " ")
        .trim()

    private fun normalize(value: String): String = value
        .lowercase(Locale.forLanguageTag("tr-TR"))
        .replace("[^\\p{L}\\p{N}]".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

    private fun truncateAtWord(value: String, maxCharacters: Int): String {
        if (value.length <= maxCharacters) return value
        val limit = maxOf(1, maxCharacters - 1)
        val boundary = value.lastIndexOf(' ', limit).takeIf { it > 0 } ?: limit
        return value.take(boundary).trimEnd() + "…"
    }

    private const val MIN_SENTENCE_CHARS = 24
    private const val MAX_SUMMARY_SENTENCES = 2
    private val TRUNCATED_END = Regex("(?:\\.\\.\\.|…)$")
    private val DIGIT = Regex("\\d")
    private val DATE_OR_TIME = Regex(
        "\\b(?:\\d{1,2}[./:-]){1,2}\\d{2,4}\\b|\\b\\d{1,2}\\s+(?:ocak|şubat|mart|nisan|mayıs|haziran|temmuz|ağustos|eylül|ekim|kasım|aralık)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val TIME_QUESTION = Regex("\\b(?:ne zaman|saat kaç|hangi gün|tarihi ne|ne zaman gerçekleşecek)\\b")
    private val WHY_QUESTION = Regex("\\b(?:neden|niçin|sebebi ne|nedir)\\b")
    private val CAUSE_QUESTION = Regex("\\b(?:sebep|kaynak|neden)\\b")
    private val RESULT_QUESTION = Regex("\\b(?:ne oldu|sonuç|nasıl sonuçlandı|kim kazandı|kaç gol|kaç sayı|mi oldu|mı oldu|mu oldu|mü oldu)\\b")
    private val YES_NO_QUESTION = Regex("\\b(?:mi|mı|mu|mü)\\b.*\\?$")
    private val WHY_ANSWER = Regex("\\b(?:çünkü|nedeni|sebebi|bu nedenle|sonucunda)\\b")
    private val CAUSE_ANSWER = Regex("\\b(?:neden|sebep|sonucunda|yüzünden|dolayı|ile başladı)\\b")
    private val RESULT_ANSWER = Regex("\\b(?:kazandı|kaybetti|sonuçlandı|berabere|arttı|azaldı|tamamlandı|iptal edildi|yaşandı|meydana geldi|kaydedildi|ölçüldü)\\b")
    private val BOILERPLATE_LEAD = Regex(
        "\\b(?:gündemindeki yerini aldı|gündeminde yer aldı|meraklılarının gündeminde|gündeme geldi|" +
            "göz kamaştıran|büyüleyici manzara sunan|dikkatleri üzerine çeken|öne çıkan gelişme|" +
            "haber(?:in)? detay(?:ında|larında)|detaylar haberimizde|merak konusu oldu|" +
            "en çok merak edilenlerin cevabı|cevabı haberde|cevabı haberimizde|ayrıntılar haberde|" +
            "son depremler|son dakika haberleri|afad son depremler)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val FACT_VERB = Regex(
        "\\b(açıkladı|duyurdu|belirtti|kabul edildi|reddedildi|başladı|tamamlandı|gerçekleşti|arttı|azaldı|yayımlandı|bulundu|açıldı|kapandı|seçildi|kazandı|kaybetti|kaydedildi|meydana geldi|yaşandı|ölçüldü|hissedildi|gözlendi|tespit edildi|görüldü|düştü|yükseldi|devam ediyor|etkiliyor|karar verdi|gözaltına alındı|hayatını kaybetti|yaralandı|tutuklandı|yakalandı|iptal edildi|durduruldu|onaylandı|reddetti)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val QUESTION_ANSWER_FACT = Regex(
        "\\b(açıkladı|doğruladı|yalanladı|yaşandı|meydana geldi|kaydedildi|ölçüldü|hissedildi|gözlendi|tespit edildi|görüldü|bulunmadı)\\b",
        RegexOption.IGNORE_CASE,
    )
}

