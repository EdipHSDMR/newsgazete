package com.example.util

import com.example.model.Story
import java.net.URI
import java.util.Locale
import kotlin.math.abs

/** Collapses syndicated copies into one story while retaining the publishers that covered it. */
object NewsStoryDeduplicator {
    private val wordPattern = Regex("[\\p{L}\\p{N}]{2,}")
    private val stopWords = setOf(
        "acaba", "ama", "ancak", "artık", "belli", "bugün", "bu", "daha", "da", "de", "diye",
        "eden", "etti", "gibi", "gündem", "için", "ile", "işte", "kadar", "karşı", "ne", "olan",
        "olarak", "oldu", "son", "sonra", "soru", "ve", "veya", "yeni", "yer", "üzere",
        "açıklama", "canlı", "haber", "haberleri", "türkiye", "turkiye",
    )
    private val headlineNoise = Regex(
        "\\b(?:son dakika|flaş gelişme|işte detaylar|belli oldu|gündemde|gündem oldu|" +
            "öne çıktı|öne çıkıyor|haberleri|haberine|haberinde|açıklama geldi)\\b",
        RegexOption.IGNORE_CASE,
    )

    private class IndexedStory(
        val story: Story,
        val urlKey: String,
        val headlineKey: String,
        val headlineTerms: Set<String>,
        bodyText: String,
    ) {
        val bodyCandidateTerms: Set<String> = bodyTokens(bodyText.take(BODY_CANDIDATE_CHARS))
            .filter { it.length >= MIN_BODY_CANDIDATE_TOKEN_LENGTH }
            .take(MAX_BODY_CANDIDATE_TERMS)
            .toSet()
        // Most stories have no plausible same-story headline candidate, so defer tokenizing
        // their full text. Archive entries can contain many thousands of long article bodies.
        val bodyTerms: Set<String> by lazy(LazyThreadSafetyMode.NONE) { bodyTokens(bodyText) }
    }

    fun merge(stories: List<Story>): List<Story> {
        if (stories.size < 2) return stories

        val merged = LinkedHashMap<String, IndexedStory>()
        val byUrl = mutableMapOf<String, String>()
        val byTitle = mutableMapOf<String, String>()
        val byToken = mutableMapOf<String, MutableSet<String>>()
        val byBodyToken = mutableMapOf<String, MutableSet<String>>()

        for (story in stories.sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenBy { it.id })) {
            val indexed = index(story)
            val candidateIds = linkedSetOf<String>()
            byUrl[indexed.urlKey]?.let(candidateIds::add)
            byTitle[indexed.headlineKey]?.let(candidateIds::add)
            indexed.headlineTerms.forEach { token -> byToken[token]?.let(candidateIds::addAll) }
            indexed.bodyCandidateTerms.forEach { token -> byBodyToken[token]?.let(candidateIds::addAll) }

            val matchId = candidateIds.firstOrNull { id -> merged[id]?.let { isSameStory(it, indexed) } == true }
            if (matchId == null) {
                merged[story.id] = indexed
                addIndexes(indexed, story.id, byUrl, byTitle, byToken, byBodyToken)
                continue
            }

            val combined = index(mergeStory(merged.getValue(matchId).story, story))
            merged[matchId] = combined
            addIndexes(indexed, matchId, byUrl, byTitle, byToken, byBodyToken)
            addIndexes(combined, matchId, byUrl, byTitle, byToken, byBodyToken)
        }
        return merged.values.map { it.story }
    }

    private fun addIndexes(
        indexed: IndexedStory,
        id: String,
        byUrl: MutableMap<String, String>,
        byTitle: MutableMap<String, String>,
        byToken: MutableMap<String, MutableSet<String>>,
        byBodyToken: MutableMap<String, MutableSet<String>>,
    ) {
        if (indexed.urlKey.isNotBlank()) byUrl[indexed.urlKey] = id
        if (indexed.headlineKey.isNotBlank()) byTitle[indexed.headlineKey] = id
        indexed.headlineTerms.forEach { token -> byToken.getOrPut(token) { linkedSetOf() }.add(id) }
        indexed.bodyCandidateTerms.forEach { token -> byBodyToken.getOrPut(token) { linkedSetOf() }.add(id) }
    }

    private fun index(story: Story): IndexedStory = IndexedStory(
        story = story,
        urlKey = normalizeUrl(story.canonicalUrl),
        headlineKey = normalizeHeadline(story.title),
        headlineTerms = headlineTokens(story.title),
        bodyText = story.fullText,
    )

    private fun isSameStory(a: IndexedStory, b: IndexedStory): Boolean {
        if (a.urlKey.isNotBlank() && a.urlKey == b.urlKey) return true

        val publishedA = a.story.publishedAtEpochMs
        val publishedB = b.story.publishedAtEpochMs
        val withinWindow = publishedA <= 0 || publishedB <= 0 || abs(publishedA - publishedB) <= DUPLICATE_WINDOW_MS
        if (withinWindow && a.headlineKey.isNotBlank() && a.headlineKey == b.headlineKey) return true

        val common = a.headlineTerms intersect b.headlineTerms
        val minimumSize = minOf(a.headlineTerms.size, b.headlineTerms.size)
        val sameTopic = a.story.topic.equals(b.story.topic, ignoreCase = true) ||
            a.story.topic == GENERAL_TOPIC || b.story.topic == GENERAL_TOPIC
        if (withinWindow && sameTopic && minimumSize > 0 && common.size >= MIN_SHARED_HEADLINE_TERMS) {
            val overlap = common.size.toDouble() / minimumSize
            val union = (a.headlineTerms union b.headlineTerms).size
            val jaccard = if (union == 0) 0.0 else common.size.toDouble() / union
            if (jaccard >= HEADLINE_JACCARD_THRESHOLD || overlap >= HEADLINE_OVERLAP_THRESHOLD) return true
        }

        // Body comparison is only useful when the headlines share some key terms; this keeps
        // the thousands of stories in the archive fast to compare.
        if (a.bodyTerms.size < MIN_BODY_TERMS || b.bodyTerms.size < MIN_BODY_TERMS) return false
        val commonBodyTerms = (a.bodyTerms intersect b.bodyTerms).size
        val bodyOverlap = commonBodyTerms.toDouble() / minOf(a.bodyTerms.size, b.bodyTerms.size)
        if (withinWindow && sameTopic && common.size >= MIN_BODY_HEADLINE_HINT) {
            return commonBodyTerms >= MIN_SHARED_BODY_TERMS && bodyOverlap >= BODY_OVERLAP_THRESHOLD
        }
        val minimumStrongTerms = if (withinWindow) MIN_STRONG_SHARED_BODY_TERMS else MIN_OLD_STORY_SHARED_BODY_TERMS
        val strongOverlapThreshold = if (withinWindow) STRONG_BODY_OVERLAP_THRESHOLD else OLD_STORY_BODY_OVERLAP_THRESHOLD
        return commonBodyTerms >= minimumStrongTerms && bodyOverlap >= strongOverlapThreshold
    }

    private fun mergeStory(existing: Story, incoming: Story): Story {
        val comparisons = (existing.comparisons + incoming.comparisons)
            .distinctBy { it.publisher.trim().lowercase(Locale.ROOT) to normalizeUrl(it.url) }
            .take(MAX_COMPARISONS)
        val uniquePublishers = comparisons.map { it.publisher.trim().lowercase(Locale.ROOT) }.distinct().size
        val richer = if (incoming.fullText.length > existing.fullText.length) incoming else existing

        return richer.copy(
            id = existing.id,
            sourceCount = maxOf(existing.sourceCount, incoming.sourceCount, uniquePublishers, 1),
            comparisons = comparisons,
            canonicalUrl = existing.canonicalUrl.ifBlank { incoming.canonicalUrl },
            imageUrl = existing.imageUrl.ifBlank { incoming.imageUrl },
            fullText = richer.fullText,
            summary = richer.summary.ifBlank { existing.summary.ifBlank { incoming.summary } },
            isBreaking = existing.isBreaking || incoming.isBreaking,
            isManset = existing.isManset || incoming.isManset,
            isSaved = existing.isSaved || incoming.isSaved,
        )
    }

    private fun normalizeHeadline(value: String): String = headlineNoise.replace(
        value.lowercase(Locale.forLanguageTag("tr-TR")),
        " ",
    ).let(::normalizeWords)

    private fun headlineTokens(value: String): Set<String> = wordPattern.findAll(normalizeHeadline(value))
        .map { it.value }
        .filter { it.length > 2 && it !in stopWords && !it.all(Char::isDigit) }
        .toSet()

    private fun bodyTokens(value: String): Set<String> = wordPattern.findAll(value.lowercase(Locale.forLanguageTag("tr-TR")))
        .map { it.value }
        .filter { it.length > 3 && it !in stopWords && !it.all(Char::isDigit) }
        .toSet()

    private fun normalizeWords(value: String): String = value
        .replace("[^\\p{L}\\p{N}]".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

    private fun normalizeUrl(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val uri = URI(value.trim())
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (host.isBlank()) return@runCatching value.substringBefore('#').trimEnd('/')
            val path = uri.rawPath.orEmpty().trimEnd('/').ifBlank { "/" }
            val query = uri.rawQuery.orEmpty().split('&')
                .filterNot { parameter ->
                    val key = parameter.substringBefore('=').lowercase(Locale.ROOT)
                    key.startsWith("utm_") || key in TRACKING_QUERY_KEYS
                }
                .sorted()
                .joinToString("&")
            "https://$host$path" + if (query.isBlank()) "" else "?$query"
        }.getOrDefault(value.trim().substringBefore('#').trimEnd('/'))
    }

    private const val GENERAL_TOPIC = "Gündem"
    private const val MIN_SHARED_HEADLINE_TERMS = 3
    private const val MIN_BODY_HEADLINE_HINT = 2
    private const val HEADLINE_JACCARD_THRESHOLD = 0.68
    private const val HEADLINE_OVERLAP_THRESHOLD = 0.82
    private const val MIN_BODY_TERMS = 12
    private const val MIN_SHARED_BODY_TERMS = 10
    private const val MIN_STRONG_SHARED_BODY_TERMS = 18
    private const val MIN_OLD_STORY_SHARED_BODY_TERMS = 24
    private const val MIN_BODY_CANDIDATE_TOKEN_LENGTH = 5
    private const val MAX_BODY_CANDIDATE_TERMS = 18
    private const val BODY_CANDIDATE_CHARS = 1_200
    private const val BODY_OVERLAP_THRESHOLD = 0.72
    private const val STRONG_BODY_OVERLAP_THRESHOLD = 0.88
    private const val OLD_STORY_BODY_OVERLAP_THRESHOLD = 0.94
    private const val DUPLICATE_WINDOW_MS = 36 * 60 * 60 * 1_000L
    private const val MAX_COMPARISONS = 8
    private val TRACKING_QUERY_KEYS = setOf("fbclid", "gclid", "ref", "source")
}

