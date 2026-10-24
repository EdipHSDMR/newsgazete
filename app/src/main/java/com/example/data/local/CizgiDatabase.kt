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
    ],
    version = 3,
    exportSchema = false,
)
abstract class CizgiDatabase : RoomDatabase() {
    abstract fun savedArticleDao(): SavedArticleDao
    abstract fun cachedStoryDao(): CachedStoryDao
    abstract fun cachedSportStatDao(): CachedSportStatDao
    abstract fun newsArticleDao(): NewsArticleDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
