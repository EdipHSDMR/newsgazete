package com.example

import com.example.data.CizgiSeedData
import com.example.model.MatchStatus
import com.example.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun seedStories_hasMansetAndTopics() {
        val stories = CizgiSeedData.stories
        assertTrue(stories.isNotEmpty())

        val manset = stories.firstOrNull { it.isManset }
        assertNotNull("Manset story must exist", manset)
        assertTrue(manset!!.title.isNotBlank())
        assertTrue(manset.publisher.isNotBlank())

        val topics = stories.map { it.topic }.distinct()
        assertTrue("Must cover multiple topics", topics.size >= 4)

        // Verify estimated reading time and publishers
        for (story in stories) {
            assertTrue("Reading time must be positive", story.readTimeMin > 0)
            assertTrue("Publisher must not be blank", story.publisher.isNotBlank())
        }
    }

    @Test
    fun topics_prioritizesGundemTeknolojiSporDunya() {
        val topics = CizgiSeedData.topics
        assertEquals("Gündem", topics[0])
        assertEquals("Teknoloji", topics[1])
        assertEquals("Spor", topics[2])
        assertEquals("Dünya", topics[3])
    }

    @Test
    fun seedMatches_hasLiveAndUpcoming() {
        val matches = CizgiSeedData.matches
        assertTrue(matches.isNotEmpty())

        val hasLiveOrUpcoming = matches.any { it.status == MatchStatus.LIVE || it.status == MatchStatus.UPCOMING }
        assertTrue(hasLiveOrUpcoming)
    }

    @Test
    fun turkishCities_containsMajorProvinces() {
        val cities = CizgiSeedData.turkishCities
        assertTrue(cities.contains("İstanbul"))
        assertTrue(cities.contains("Ankara"))
        assertTrue(cities.contains("İzmir"))
    }

    @Test
    fun newUsersChooseTheirOwnTopicsAndTeams() {
        val prefs = UserPreferences()
        assertEquals("light", prefs.themeMode)
        assertEquals("İstanbul", prefs.selectedCity)
        assertFalse(prefs.breakingAlerts)
        assertFalse(prefs.topicAlerts)
        assertFalse(prefs.dailyDigest)
        assertTrue(prefs.followedTopics.isEmpty())
        assertTrue(prefs.followedTeams.isEmpty())
    }
}
