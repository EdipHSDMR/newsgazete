package com.example.util

import android.text.Html
import java.util.Locale

/** Selects complementary facts from the source; no remote model or invented information. */
object NewsSummary {
    private val locale = Locale.forLanguageTag("tr-TR")
    private val wordPattern = Regex("[\\p{L}\\p{N}]+")
    private val boundary = Regex("(?<=[.!?])\\s+|\\n+")
    private val stopWords = setOf("ama", "ancak", "bir", "bu", "çok", "daha", "da", "de", "diye", "en", "gibi",
        "ile", "için", "kadar", "karşı", "ki", "olan", "olarak", "oldu", "sonra", "ve", "veya", "yeni", "üzere",
        "haber", "son", "dakika", "nedir", "nasıl", "hangi", "zaman", "bugün")
    private val abbreviations = Regex("(?i)(?:\\b(?:Dr|Prof|Doç|Sn|Av|No|Mah|Cad|Sok|Ltd|Tic|AŞ|St)|\\b[A-ZÇĞİÖŞÜ])\\.$")
    private val boilerplate = Regex(
        "gündemindeki yerini aldı|meraklılarının gündeminde|gündeme geldi|merak konusu oldu|" +
            "detaylar haberimizde|ayrıntılar haberimizde|cevabı haberimizde|haber(?:in)? detay(?:ında|larında)|" +
            "bizi takip edin|abone ol|tüm hakları saklıdır|çerez politikası|bildirimleri aç|" +
            "göz kamaştıran|büyüleyici manzara|en çok merak edilen", RegexOption.IGNORE_CASE)
    private val action = Regex(
        "açıkla|duyur|belirt|başla|tamamlan|gerçekleş|arttı|azaldı|yayımlan|bulun|açıldı|kapandı|" +
            "seçildi|kazandı|kaybetti|kayded|meydana gel|yaşan|ölçül|hissedil|tespit edil|düştü|yüksel|" +
            "devam ediyor|karar ver|gözalt|hayatını kaybet|yaralan|tutuklan|yakalan|iptal|onaylan|" +
            "reddet|söyle|konuş|tepki|yanıt|savun|ifade et|vurgula|paylaş|imzala|ziyaret|yüzde|kapsıyor",
        RegexOption.IGNORE_CASE)
    private val timeFact = Regex("\\b\\d{1,2}[.:/]\\d{2}(?:[./]\\d{2,4})?\\b|\\b\\d{1,2}\\s+(?:ocak|şubat|mart|nisan|mayıs|haziran|temmuz|ağustos|eylül|ekim|kasım|aralık)\\b|\\b(?:pazartesi|salı|çarşamba|perşembe|cuma|cumartesi|pazar)\\b", RegexOption.IGNORE_CASE)
    private val causeFact = Regex("çünkü|nedeni|sebebi|bu nedenle|sonucunda|yüzünden|dolayı", RegexOption.IGNORE_CASE)

    fun concise(sourceText: String, title: String = "", maxCharacters: Int = 380): String {
        val titleKey = normalize(title)
        val titleTokens = tokens(title)
        val candidates = sentences(sourceText).map(::compress)
            .filter { it.length >= 16 && normalize(it) != titleKey }
            .filterNot { it.endsWith("?") || boilerplate.containsMatchIn(it) }
            .distinctBy(::normalize)
        if (candidates.isEmpty()) return ""
        val ranked = candidates.mapIndexed { index, sentence ->
            sentence to score(sentence, index, titleTokens, title)
        }.sortedByDescending { it.second }
        val first = ranked.first().first
        val second = ranked.drop(1).firstOrNull { (sentence, _) ->
            similarity(first, sentence) < 0.6 && first.length + sentence.length + 1 <= maxCharacters
        }?.first
        val digest = if (second == null) first else "$first $second"
        return fit(digest, maxCharacters)
    }

    fun bestAvailableSummary(summaryCandidate: String, sourceText: String, title: String, maxCharacters: Int = 380): String {
        val candidate = cleanText(summaryCandidate)
        val source = cleanText(sourceText)
        val generated = concise(source, title, maxCharacters)
        // A source description may be the only available fact. Never blank it merely because it is also the lead.
        if (generated.isNotBlank()) return generated
        if (candidate.isNotBlank() && normalize(candidate) != normalize(title) &&
            !boilerplate.containsMatchIn(candidate)) {
            return concise(candidate, title, maxCharacters).ifBlank { fit(candidate, maxCharacters) }
        }
        return ""
    }

    fun remainingSourceText(sourceText: String, summary: String): String {
        val summaryKeys = sentences(summary).map(::normalize)
        val blocks = paragraphs(sourceText).toMutableList()
        if (blocks.isEmpty()) return ""
        val firstSentences = sentences(blocks.first()).toMutableList()
        while (firstSentences.isNotEmpty() && summaryKeys.any { key ->
                val first = normalize(firstSentences.first())
                first == key || (first.length > 40 && key.startsWith(first))
            }) firstSentences.removeAt(0)
        if (firstSentences.isEmpty()) blocks.removeAt(0) else blocks[0] = firstSentences.joinToString(" ")
        return blocks.joinToString("\n\n")
    }

    /** Preserves the publisher's paragraph breaks and splits unusually long blocks for reading. */
    fun paragraphs(sourceText: String): List<String> {
        val blocks = cleanBlocks(sourceText).split(Regex("\\n\\s*\\n"))
            .map(String::trim).filter { it.isNotBlank() }
        return blocks.flatMap { block ->
            if (block.length <= 480) listOf(block) else {
                val result = mutableListOf<String>()
                var paragraph = ""
                sentences(block).forEach { sentence ->
                    if (paragraph.isNotBlank() && paragraph.length + sentence.length > 430) {
                        result += paragraph
                        paragraph = ""
                    }
                    paragraph = if (paragraph.isBlank()) sentence else "$paragraph $sentence"
                }
                if (paragraph.isNotBlank()) result += paragraph
                result
            }
        }.filterNot { boilerplate.containsMatchIn(it) && it.length < 180 }
    }

    private fun sentences(value: String): List<String> {
        val fragments = boundary.split(cleanBlocks(value).replace(Regex("\\.{3}|…"), "."))
            .map { it.trim().trimStart('•', '-', '–', '—').trim() }.filter(String::isNotBlank)
        val result = mutableListOf<String>()
        fragments.forEach { fragment ->
            if (result.isNotEmpty() && abbreviations.containsMatchIn(result.last())) {
                result[result.lastIndex] = result.last() + " " + fragment
            } else result += fragment
        }
        return result
    }

    private fun score(sentence: String, index: Int, titleTokens: Set<String>, title: String): Int {
        val words = tokens(sentence)
        val overlap = words.count { it in titleTokens }
        val asked = title.lowercase(locale)
        val numeric = Regex("\\d").containsMatchIn(sentence)
        val answersTime = Regex("ne zaman|saat kaç|hangi gün|tarihi").containsMatchIn(asked) && timeFact.containsMatchIn(sentence)
        val answersWhy = Regex("neden|niçin|sebebi").containsMatchIn(asked) && causeFact.containsMatchIn(sentence)
        return overlap * 4 + (if (action.containsMatchIn(sentence)) 7 else 0) +
            (if (numeric) 4 else 0) + (if (answersTime) 24 else 0) +
            (if (answersWhy) 20 else 0) + (if (sentence.length in 45..260) 3 else 0) +
            (if (index in 1..5) 2 else 0) - (if (sentence.length > 440) 5 else 0)
    }

    private fun compress(sentence: String): String = sentence
        .replace(Regex("^(?:Son dakika[:!]?|Öte yandan,?|Edinilen bilgiye göre,?|Alınan bilgiye göre,?)\\s*", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun similarity(a: String, b: String): Double {
        val left = tokens(a)
        val right = tokens(b)
        return left.intersect(right).size.toDouble() / maxOf(1, minOf(left.size, right.size))
    }

    private fun tokens(value: String): Set<String> = wordPattern.findAll(normalize(value))
        .map { it.value }.filter { it.length > 2 && it !in stopWords }.toSet()

    private fun cleanBlocks(value: String): String = Html.fromHtml(
        value.replace("\r\n", "\n").replace(Regex("\n\\s*\n"), "<br><br>")
            .replace(Regex("<script\\b[^>]*>[\\s\\S]*?</script>|<style\\b[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " "),
        Html.FROM_HTML_MODE_LEGACY,
    ).toString().replace('\u00a0', ' ').replace('\u200B', ' ')
        .replace(Regex("[ \\t]+"), " ").replace(Regex("\\n[ \\t]*\\n+"), "\n\n").trim()

    private fun cleanText(value: String): String = cleanBlocks(value).replace(Regex("\\s+"), " ").trim()
    private fun normalize(value: String): String = value.lowercase(locale).replace(Regex("[^\\p{L}\\p{N}]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun fit(value: String, maximum: Int): String {
        if (value.length <= maximum) return value
        val limit = (maximum - 1).coerceAtLeast(1)
        val sentenceEnd = value.take(limit).lastIndexOfAny(charArrayOf('.', '!', '?'))
        if (sentenceEnd >= limit / 2) return value.take(sentenceEnd + 1).trim()
        val wordEnd = value.lastIndexOf(' ', limit).takeIf { it > 0 } ?: limit
        return value.take(wordEnd).trimEnd(',', ';', ':', ' ') + "…"
    }
}
