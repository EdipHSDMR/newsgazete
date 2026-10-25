package com.example.util

import com.example.model.Story
import java.util.Locale

/** Builds discovery labels and the daily brief from the actual, deduplicated news feed. */
object NewsDiscoveryInsights {
    private val wordPattern = Regex("[\\p{L}][\\p{L}\\p{N}]{2,}")
    private val stopWords = setOf(
        "acaba", "açıklama", "açıklaması", "adına", "artık", "başladı", "belli", "bir", "bugün",
        "bulundu", "bu", "çıkıyor", "daha", "dakika", "değil", "etti", "gelen", "gelişme",
        "gündem", "haber", "haberleri", "ile", "için", "işte", "karar", "kendi", "kişi", "kişinin", "kişiye", "olan", "olarak",
        "oldu", "öne", "son", "sonra", "şimdi", "tamamlandı", "türkiye", "ve", "veya", "yeni",
        "yer", "üzere", "ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos",
        "eylül", "ekim", "kasım", "aralık", "saat", "tarih", "tarihi", "tarihinde", "gününde", "yılında",
    )
    private val locale = Locale.forLanguageTag("tr-TR")

    /** Repeated keywords from current headlines; never backed by a hand-written trend list. */
    fun trendingTerms(stories: List<Story>, limit: Int = 10): List<String> {
        val frequencies = mutableMapOf<String, Int>()
        val displayWords = mutableMapOf<String, String>()

        stories.asSequence().take(MAX_TREND_STORIES).forEach { story ->
            wordPattern.findAll(story.title).map { it.value }
                .filter { it.length >= MIN_TREND_WORD_LENGTH }
                .filterNot { it.lowercase(locale) in stopWords || it.all(Char::isDigit) }
                .distinctBy { it.lowercase(locale) }
                .forEach { word ->
                    val key = word.lowercase(locale)
                    frequencies[key] = (frequencies[key] ?: 0) + 1
                    displayWords.getOrPut(key) { formatWord(word) }
                }
        }

        val ranked = frequencies.keys.sortedWith(
            compareByDescending<String> { frequencies[it] ?: 0 }.thenBy { it },
        )
        val repeated = ranked.filter { (frequencies[it] ?: 0) > 1 }
        return (repeated + ranked.filterNot(repeated::contains))
            .distinct()
            .take(limit.coerceAtLeast(0))
            .mapNotNull(displayWords::get)
    }

    /** Five varied, source-grounded bullets from the last day of live news. */
    fun dailyDigest(stories: List<Story>, nowEpochMs: Long = System.currentTimeMillis()): List<String> {
        val recent = stories.asSequence()
            .filter { story ->
                story.publishedAtEpochMs in (nowEpochMs - DAY_MS)..(nowEpochMs + FUTURE_TOLERANCE_MS)
            }
            .take(MAX_DIGEST_CANDIDATES)
            .sortedWith(compareByDescending<Story> { it.isBreaking }.thenByDescending { it.publishedAtEpochMs })
            .toList()
        if (recent.isEmpty()) return emptyList()

        val diverseStories = buildList {
            val usedTopics = mutableSetOf<String>()
            recent.forEach { story ->
                val topic = story.topic.trim().lowercase(locale)
                if (topic.isNotBlank() && usedTopics.add(topic)) add(story)
            }
            val usedIds = mapTo(mutableSetOf()) { it.id }
            recent.filterNot { it.id in usedIds }.forEach { add(it) }
        }

        return diverseStories.asSequence()
            .mapNotNull { story ->
                val source = story.fullText.takeIf { it.length >= MIN_SOURCE_CHARS }
                    ?: story.summary.takeIf { it.length >= MIN_SOURCE_CHARS }
                    ?: return@mapNotNull null
                NewsSummary.concise(source, story.title, MAX_DIGEST_ITEM_CHARS)
                    .takeIf { it.length >= MIN_DIGEST_ITEM_CHARS }
            }
            .distinctBy(::normalize)
            .take(DIGEST_SIZE)
            .toList()
    }

    private fun formatWord(value: String): String = when {
        value.length <= 5 && value.all { !it.isLetter() || it.isUpperCase() } -> value
        else -> value.replaceFirstChar { it.titlecase(locale) }
    }

    private fun normalize(value: String): String = value.lowercase(locale)
        .replace("[^\\p{L}\\p{N}]".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

    private const val MAX_TREND_STORIES = 500
    private const val MIN_TREND_WORD_LENGTH = 4
    private const val MAX_DIGEST_CANDIDATES = 24
    private const val MIN_SOURCE_CHARS = 50
    private const val MIN_DIGEST_ITEM_CHARS = 45
    private const val MAX_DIGEST_ITEM_CHARS = 190
    private const val DIGEST_SIZE = 5
    private const val DAY_MS = 24 * 60 * 60 * 1_000L
    private const val FUTURE_TOLERANCE_MS = 5 * 60 * 1_000L
}
