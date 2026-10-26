package com.example

import com.example.model.Story
import com.example.model.UserPreferences
import com.example.util.*
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ReaderFeaturesTest {
    private fun story(title: String, topic: String = "Spor", time: Long = 10L) = Story("id-$time", title, "", topic, "Kaynak", "", time)

    @Test fun archiveSearchTreatsTurkishCaseAndDiacriticsConsistently() {
        assertEquals(ArchiveSearch.normalize("İSTANBUL IĞDIR Çığ Şampiyon Ödül"), ArchiveSearch.normalize("istanbul ığdır cig sampiyon odul"))
        assertEquals(listOf("faiz", "karari"), ArchiveSearch.terms("  Faiz, KARARI!  "))
    }

    @Test fun personalFeedIncludesTeamStoriesAndKeepsRecentStoriesFirst() {
        val prefs = UserPreferences(followedTopics = setOf("Ekonomi"), followedTeams = setOf("Beşiktaş"))
        val older = story("Beşiktaş kadrosunu açıkladı", time = 10)
        val newer = story("Faiz kararı açıklandı", "Ekonomi", 20)
        val unrelated = story("Yeni sergi açıldı", "Kültür", 30)
        assertEquals(listOf(newer, older), PersonalizedNews.rank(listOf(older, unrelated, newer), prefs, 50))
    }

    @Test fun quietHoursCrossMidnightAndUseExclusiveEnd() {
        assertTrue(NotificationPolicy.isQuiet(LocalTime.of(23, 0), true, "23:00", "07:00"))
        assertTrue(NotificationPolicy.isQuiet(LocalTime.of(6, 59), true, "23:00", "07:00"))
        assertFalse(NotificationPolicy.isQuiet(LocalTime.of(7, 0), true, "23:00", "07:00"))
        assertFalse(NotificationPolicy.isQuiet(LocalTime.MIDNIGHT, false, "23:00", "07:00"))
    }

    @Test fun notificationsExcludeUnknownOldAndFuturePublicationTimes() {
        val now = 50_000_000L
        assertTrue(NotificationPolicy.isNewStory(now - 10_000, now - 60_000, now))
        assertFalse(NotificationPolicy.isNewStory(0, now - 60_000, now))
        assertFalse(NotificationPolicy.isNewStory(now - 100_000, now - 60_000, now))
        assertFalse(NotificationPolicy.isNewStory(now + 120_000, now - 60_000, now))
    }

    @Test fun syndicatedStoriesKeepAllSourcesAndAttributeTheBodyToItsPublisher() {
        val first = story("Üç ilçede eğitime ara verildi").copy(publisher = "Kaynak A", canonicalUrl = "https://a.example/haber", fullText = "Kısa metin")
        val second = first.copy(id = "second", publisher = "Kaynak B", canonicalUrl = "https://b.example/haber", fullText = "İkinci kaynağın daha ayrıntılı haber metni burada yer alıyor.")
        val merged = NewsStoryDeduplicator.merge(listOf(first, second)).single()
        assertEquals(2, merged.sourceCount)
        assertEquals(setOf("Kaynak A", "Kaynak B"), merged.comparisons.map { it.publisher }.toSet())
        assertEquals("Kaynak B", merged.publisher)
        assertEquals("https://b.example/haber", merged.canonicalUrl)
    }

    @Test fun publisherSectionsPreventGoldAndEuroSportsMisclassification() {
        assertEquals("Spor", NewsTopics.classify("Altın Top ödülünü kim kazandı?", "Ödül töreni düzenlendi", "", "Ekonomi", "https://www.example.com/spor/altin-top"))
        assertEquals("Spor", NewsTopics.classify("EuroLeague sonuçları", "", "", "Gündem", ""))
        assertEquals("Ekonomi", NewsTopics.classify("Altın fiyatları bugün ne kadar?", "", "Ekonomi", "Gündem", ""))
        assertEquals("Sağlık", NewsTopics.classify("Yeni araştırma açıklandı", "", "Sağlık", "Gündem", ""))
    }

    @Test fun aNewPublisherDoesNotCreateANewNotificationForAnAlreadyCoveredStory() {
        val original = story("Karar açıklandı").copy(canonicalUrl = "https://a.example/karar?utm_source=reader")
        val copy = original.copy(id = "other", canonicalUrl = "https://b.example/karar", comparisons = listOf(com.example.model.SourceComparison("A", original.title, "", "", "https://a.example/karar")))
        assertTrue(NotificationPolicy.newsKeys(original).intersect(NotificationPolicy.newsKeys(copy)).isNotEmpty())
        val different = original.copy(id = "different", canonicalUrl = "https://a.example/baska-karar")
        assertTrue(NotificationPolicy.newsKeys(original).intersect(NotificationPolicy.newsKeys(different)).isEmpty())
    }
}
