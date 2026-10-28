package com.example.util

import com.example.model.Story
import com.example.model.UserPreferences
import java.text.Normalizer
import java.time.LocalTime
import java.util.Locale

/** One normalization for both stored archive text and the user's search. */
object ArchiveSearch {
    fun normalize(text: String): String = Normalizer.normalize(text.replace('ı', 'i').replace('İ', 'I'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun terms(query: String): List<String> = normalize(query).split(' ').filter(String::isNotBlank).take(6)
    fun like(term: String): String = "%${term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
}

object PersonalizedNews {
    fun matches(story: Story, preferences: UserPreferences): Boolean =
        preferences.followedTopics.any { ArchiveSearch.normalize(it) == ArchiveSearch.normalize(story.topic) } ||
            preferences.followedTeams.any { team ->
                val name = ArchiveSearch.normalize(team)
                name.isNotBlank() && ArchiveSearch.normalize(story.title + " " + story.summary).contains(name)
            }

    /** Ranks followed stories while spacing publishers so one feed cannot dominate the screen. */
    fun rank(stories: List<Story>, preferences: UserPreferences, now: Long = System.currentTimeMillis()): List<Story> {
        val candidates = stories.filter { matches(it, preferences) }.sortedWith(
            compareByDescending<Story> { it.isBreaking }
                .thenByDescending { it.publishedAtEpochMs > now - 24 * 60 * 60_000L }
                .thenByDescending { it.sourceCount }
                .thenByDescending { it.publishedAtEpochMs },
        )
        if (candidates.size < 3) return candidates

        val result = ArrayList<Story>(candidates.size)
        val remaining = candidates.toMutableList()
        val publisherUses = mutableMapOf<String, Int>()
        while (remaining.isNotEmpty()) {
            val nextIndex = remaining.indices.minWithOrNull(
                compareBy<Int> { index ->
                    val publisher = ArchiveSearch.normalize(remaining[index].publisher).ifBlank { "kaynak-bilinmiyor" }
                    publisherUses[publisher] ?: 0
                }.thenBy { it },
            ) ?: break
            val next = remaining.removeAt(nextIndex)
            result += next
            val publisher = ArchiveSearch.normalize(next.publisher).ifBlank { "kaynak-bilinmiyor" }
            publisherUses[publisher] = (publisherUses[publisher] ?: 0) + 1
        }
        return result
    }
}

object ReaderSettings {
    val fontSizes = linkedMapOf("small" to "Küçük", "normal" to "Standart", "large" to "Büyük", "extra_large" to "Çok büyük")
    fun fontScale(value: String): Float = when (value) { "small" -> .9f; "large" -> 1.15f; "extra_large" -> 1.3f; else -> 1f }
}

object NotificationPolicy {
    fun newsKeys(story: Story): Set<String> = (listOf(story.canonicalUrl) + story.comparisons.map { it.url })
        .filter(String::isNotBlank).map { java.util.UUID.nameUUIDFromBytes(NewsStoryDeduplicator.normalizeUrl(it).toByteArray(Charsets.UTF_8)).toString() }.toSet()
        .ifEmpty { setOf("story-${story.id}") }

    fun isQuiet(now: LocalTime, enabled: Boolean, start: String, end: String): Boolean {
        if (!enabled) return false
        val from = runCatching { LocalTime.parse(start) }.getOrNull() ?: return false
        val until = runCatching { LocalTime.parse(end) }.getOrNull() ?: return false
        return when { from == until -> true; from < until -> now >= from && now < until; else -> now >= from || now < until }
    }

    fun isNewStory(publishedAt: Long, since: Long, now: Long): Boolean =
        publishedAt > since && publishedAt in (now - 2 * 60 * 60_000L)..(now + 60_000L)
}
