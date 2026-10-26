package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.model.SourceComparison
import com.example.model.Story
import com.example.util.ArchiveSearch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], manifest = Config.NONE)
class ReaderStorageTest {
    @Test fun savedSnapshotsAndReadingPositionsSurviveAnArchiveDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CizgiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val story = Story("saved", "Karar açıklandı", "Ayrıntılı kararın özeti.", "Gündem", "Yayıncı", "Dün", 123_000,
                fullText = "Birinci paragraf.\n\nİkinci paragraf.", paragraphs = listOf("Birinci paragraf.", "İkinci paragraf."),
                comparisons = listOf(SourceComparison("Diğer yayıncı", "Diğer başlık", "", "Dün")), canonicalUrl = "https://example.com/karar")
            db.newsArticleDao().upsertAll(listOf(NewsArticleEntity.fromStory(story)))
            db.savedArticleDao().insertArticle(SavedArticleEntity.fromStory(story, true))
            db.readerDao().saveReadingState(ReadingHistoryEntity(story.id, StorySnapshot.encode(story), 1000, "paragraph_1", 24, 63))
            db.newsArticleDao().clearAll()
            val saved = db.savedArticleDao().getSavedArticleById(story.id)!!
            assertTrue(saved.offlineReady)
            assertEquals(story.copy(isSaved = true), saved.toStory())
            val reading = db.readerDao().readingState(story.id)!!
            assertEquals(story, reading.story())
            assertEquals("paragraph_1", reading.anchorKey)
            assertEquals(24, reading.itemOffset)
            assertEquals(63, reading.progressPercent)
        } finally { db.close() }
    }

    @Test fun archiveSearchFindsOlderUnloadedNewsAndRespectsTheDateBoundary() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CizgiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val older = Story("old", "Iğdır için yeni yatırım kararı", "Bütçe açıklandı.", "Ekonomi", "AA", "", 1000)
            val newer = older.copy(id = "new", publishedAtEpochMs = 2000, publisher = "Diğer")
            db.newsArticleDao().upsertAll(listOf(older, newer).map(NewsArticleEntity::fromStory))
            val term = ArchiveSearch.like(ArchiveSearch.normalize("IĞDIR"))
            val results = db.newsArticleDao().search(term, "%karari%", "%", "%", "%", "%", "Ekonomi", "AA", 1000, 2000, 60, 0)
            assertEquals(listOf("old"), results.map { it.id })
        } finally { db.close() }
    }

    @Test fun notificationIdsAreIdempotentAndReadStateIsStored() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CizgiDatabase::class.java).allowMainThreadQueries().build()
        try {
            val item = NotificationEntity("same-event", "Spor", "Maç başladı", "Takım A – Takım B", null, 1000)
            assertTrue(db.notificationDao().insert(item) > 0)
            assertEquals(-1L, db.notificationDao().insert(item))
            db.notificationDao().markRead(item.id)
            assertTrue(db.notificationDao().observeInbox().first().single().isRead)
        } finally { db.close() }
    }
}
