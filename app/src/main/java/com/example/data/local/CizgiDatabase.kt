package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedArticleEntity::class,
        CachedStoryEntity::class,
        CachedSportStatEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class CizgiDatabase : RoomDatabase() {
    abstract fun savedArticleDao(): SavedArticleDao
    abstract fun cachedStoryDao(): CachedStoryDao
    abstract fun cachedSportStatDao(): CachedSportStatDao

    companion object {
        @Volatile
        private var INSTANCE: CizgiDatabase? = null

        fun getDatabase(context: Context): CizgiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CizgiDatabase::class.java,
                    "cizgi_news_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
