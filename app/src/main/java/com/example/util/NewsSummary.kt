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

    fun concise(sourceText: String, title: String = "", maxCharacters: Int = 380): String {
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
        val sentences = if (hasTruncatedEnding && allSentences.size > 1) allSentences.dropLast(1) else allSentences

        if (sentences.isEmpty()) return truncateAtWord(text, maxCharacters)
        if (text.length <= maxCharacters) return sentences.joinToString(" ")

        val chosen = sentences.withIndex()
            .sortedByDescending { (index, sentence) -> score(sentence, index, titleWords) }
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
        return if (result.isNotEmpty()) result.toString() else truncateAtWord(sentences.first(), maxCharacters)
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

    private fun score(sentence: String, index: Int, titleWords: Set<String>): Int {
        val words = wordPattern.findAll(normalize(sentence)).map { it.value }.toSet()
        val titleMatches = words.count { it in titleWords }
        val lengthScore = when (sentence.length) {
            in 70..230 -> 2
            in 45..300 -> 1
            else -> 0
        }
        val hasConcreteDetail = DIGIT.containsMatchIn(sentence) || FACT_VERB.containsMatchIn(sentence)
        return (if (index == 0) 3 else maxOf(0, 2 - index)) + titleMatches * 2 + lengthScore + if (hasConcreteDetail) 1 else 0
    }

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
    private const val MAX_SUMMARY_SENTENCES = 3
    private val TRUNCATED_END = Regex("(?:\\.\\.\\.|…)$")
    private val DIGIT = Regex("\\d")
    private val FACT_VERB = Regex(
        "\\b(açıkladı|duyurdu|belirtti|kabul edildi|reddedildi|başladı|tamamlandı|gerçekleşti|arttı|azaldı|yayımlandı|bulundu|açıldı|kapandı|seçildi|kazandı|kaybetti)\\b",
        RegexOption.IGNORE_CASE,
    )
}
