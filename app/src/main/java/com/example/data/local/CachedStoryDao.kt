package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedStoryDao {

    @Query("SELECT * FROM cached_stories ORDER BY fetchedTimestamp DESC")
    fun getAllCachedStories(): Flow<List<CachedStoryEntity>>

    @Query("SELECT * FROM cached_stories ORDER BY fetchedTimestamp DESC LIMIT 80")
    suspend fun getRecentCachedStories(): List<CachedStoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<CachedStoryEntity>)

    @Query("DELETE FROM cached_stories WHERE id NOT IN (SELECT id FROM cached_stories ORDER BY fetchedTimestamp DESC LIMIT 150)")
    suspend fun trimOldStories()

    @Query("DELETE FROM cached_stories")
    suspend fun clearAll()
}
