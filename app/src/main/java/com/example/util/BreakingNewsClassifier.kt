package com.example.util

import com.example.model.Story
import java.util.Locale

/** Keeps stale cached headlines from continuing to look like live breaking news. */
object BreakingNewsClassifier {
    const val MAX_AGE_MS = 2 * 60 * 60 * 1_000L
    private val titlePrefix = Regex("^\\s*(?:⚡\\s*)?son dakika\\b")

    fun isBreaking(
        title: String,
        publishedAtEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
        sourceMarkedBreaking: Boolean = false,
    ): Boolean {
        if (publishedAtEpochMs <= 0L || publishedAtEpochMs > nowEpochMs) return false
        if (nowEpochMs - publishedAtEpochMs > MAX_AGE_MS) return false
        val normalizedTitle = title.lowercase(Locale.forLanguageTag("tr-TR"))
        return sourceMarkedBreaking || titlePrefix.containsMatchIn(normalizedTitle)
    }

    fun latest(stories: List<Story>, nowEpochMs: Long = System.currentTimeMillis()): Story? = stories
        .asSequence()
        .filter { story -> isBreaking(story.title, story.publishedAtEpochMs, nowEpochMs, story.isBreaking) }
        .sortedByDescending { it.publishedAtEpochMs }
        .distinctBy { story -> story.canonicalUrl.ifBlank { story.id } }
        .firstOrNull()
}
