package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedArticleEntity::class,
        CachedStoryEntity::class,
        CachedSportStatEntity::class,
        NewsArticleEntity::class,
        ReadingHistoryEntity::class,
        NotificationEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class CizgiDatabase : RoomDatabase() {
    abstract fun savedArticleDao(): SavedArticleDao
    abstract fun cachedStoryDao(): CachedStoryDao
    abstract fun cachedSportStatDao(): CachedSportStatDao
    abstract fun newsArticleDao(): NewsArticleDao
    abstract fun readerDao(): ReaderDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: CizgiDatabase? = null

        fun getDatabase(context: Context): CizgiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CizgiDatabase::class.java,
                    "cizgi_news_database",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createNewsArticlesTable(db)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 2 existed in two app builds: one stored these caches, the other stored
                // the paged news archive. Create whichever tables are missing in either database.
                createNewsArticlesTable(db)
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS cached_stories (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        summary TEXT NOT NULL,
                        topic TEXT NOT NULL,
                        publisher TEXT NOT NULL,
                        publishedAt TEXT NOT NULL,
                        readTimeMin INTEGER NOT NULL,
                        imageUrl TEXT NOT NULL,
                        isBreaking INTEGER NOT NULL,
                        isManset INTEGER NOT NULL,
                        sourceCount INTEGER NOT NULL,
                        whatHappened TEXT NOT NULL,
                        whyItMatters TEXT NOT NULL,
                        whatIsUnknown TEXT NOT NULL,
                        fullText TEXT NOT NULL,
                        canonicalUrl TEXT NOT NULL,
                        paragraphsJoined TEXT NOT NULL,
                        fetchedTimestamp INTEGER NOT NULL
                    )""".trimIndent(),
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS cached_sport_stats (
                        id TEXT NOT NULL PRIMARY KEY,
                        competition TEXT NOT NULL,
                        statType TEXT NOT NULL,
                        rank INTEGER NOT NULL,
                        player TEXT NOT NULL,
                        team TEXT NOT NULL,
                        count INTEGER NOT NULL,
                        secondaryCount INTEGER NOT NULL,
                        matches INTEGER NOT NULL,
                        yellowCards INTEGER NOT NULL,
                        redCards INTEGER NOT NULL,
                        penalties INTEGER NOT NULL,
                        fetchedTimestamp INTEGER NOT NULL
                    )""".trimIndent(),
                )
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE news_articles ADD COLUMN searchText TEXT NOT NULL DEFAULT ''")
                // Match ArchiveSearch's Turkish/diacritic normalization for existing archive rows.
                var expression = "title || ' ' || summary || ' ' || topic || ' ' || publisher"
                listOf("İ" to "i", "ı" to "i", "Ç" to "c", "ç" to "c", "Ğ" to "g", "ğ" to "g", "Ö" to "o", "ö" to "o", "Ş" to "s", "ş" to "s", "Ü" to "u", "ü" to "u").forEach { (from, to) ->
                    expression = "REPLACE($expression, '$from', '$to')"
                }
                db.execSQL("UPDATE news_articles SET searchText = LOWER($expression)")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN storyJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN offlineReady INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE saved_articles SET offlineReady = 1 WHERE LENGTH(fullText) >= 1200")
                db.execSQL("CREATE TABLE IF NOT EXISTS reading_history (storyId TEXT NOT NULL PRIMARY KEY, storyJson TEXT NOT NULL, lastReadAt INTEGER NOT NULL, anchorKey TEXT NOT NULL, itemOffset INTEGER NOT NULL, progressPercent INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS notification_inbox (id TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, storyId TEXT, createdAt INTEGER NOT NULL, isRead INTEGER NOT NULL)")
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_articles ADD COLUMN offlineImagePath TEXT NOT NULL DEFAULT ''")
            }
        }

        private fun createNewsArticlesTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS news_articles (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    topic TEXT NOT NULL,
                    publisher TEXT NOT NULL,
                    publishedAt TEXT NOT NULL,
                    publishedAtEpochMs INTEGER NOT NULL,
                    readTimeMin INTEGER NOT NULL,
                    imageUrl TEXT NOT NULL,
                    isBreaking INTEGER NOT NULL,
                    sourceCount INTEGER NOT NULL,
                    whatHappened TEXT NOT NULL,
                    whyItMatters TEXT NOT NULL,
                    whatIsUnknown TEXT NOT NULL,
                    fullText TEXT NOT NULL,
                    paragraphsJson TEXT NOT NULL,
                    comparisonsJson TEXT NOT NULL,
                    canonicalUrl TEXT NOT NULL
                )""".trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_news_articles_publishedAtEpochMs_id ON news_articles(publishedAtEpochMs, id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_news_articles_topic ON news_articles(topic)")
        }
    }
}

