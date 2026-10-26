package com.example

import com.example.model.Story
import com.example.util.BreakingNewsClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakingNewsClassifierTest {
    private val now = 1_800_000_000_000L

    @Test
    fun onlyFreshExplicitBreakingTitlesAreMarked() {
        assertTrue(BreakingNewsClassifier.isBreaking("SON DAKİKA: Yeni gelişme", now - 5_000L, now))
        assertTrue(BreakingNewsClassifier.isBreaking("Son Dakika Türkiye'den haber", now - 5_000L, now))
        assertFalse(BreakingNewsClassifier.isBreaking("Eski olayda son dakika gelişmesi", now - 5_000L, now))
        assertFalse(BreakingNewsClassifier.isBreaking("Son Dakika: Eski haber", now - BreakingNewsClassifier.MAX_AGE_MS - 1, now))
        assertFalse(BreakingNewsClassifier.isBreaking("Son Dakika: Tarihi bilinmeyen", 0L, now))
        assertFalse(BreakingNewsClassifier.isBreaking("Son Dakika: Gelecek tarih", now + 1L, now))
    }

    @Test
    fun genericRssCategoryDoesNotCreateBreakingNews() {
        val bulletin = story("rss-legacy", "Günün önemli gelişmeleri ve haber bülteni", now - 1_000L, "https://news.test/bulletin").copy(isBreaking = true)
        assertEquals(null, BreakingNewsClassifier.latest(listOf(bulletin), now))
        val explicit = bulletin.copy(id = "pha-urgent", title = "Kaynağın acil olarak işaretlediği gelişme")
        assertEquals("pha-urgent", BreakingNewsClassifier.latest(listOf(explicit), now)?.id)
    }

    @Test
    fun latestBreakingStoryWinsAndDuplicateUrlIsCollapsed() {
        val olderDuplicate = story("old", "Son Dakika: Haber", now - 60_000L, "https://news.test/story")
        val newestDuplicate = story("new", "Son Dakika: Haber", now - 10_000L, "https://news.test/story")
        val newestUnique = story("latest", "Son Dakika: Başka gelişme", now - 1_000L, "https://news.test/latest")

        assertEquals("latest", BreakingNewsClassifier.latest(listOf(olderDuplicate, newestUnique, newestDuplicate), now)?.id)
    }

    private fun story(id: String, title: String, publishedAt: Long, url: String) = Story(
        id = id,
        title = title,
        summary = "Özet",
        topic = "Gündem",
        publisher = "Kaynak",
        publishedAt = "Az önce",
        publishedAtEpochMs = publishedAt,
        canonicalUrl = url,
    )
}
