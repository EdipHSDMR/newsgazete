package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {
    @Query("SELECT * FROM reading_history ORDER BY lastReadAt DESC LIMIT 200")
    fun observeHistory(): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM reading_history WHERE storyId = :id")
    suspend fun readingState(id: String): ReadingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReadingState(state: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE storyId NOT IN (SELECT storyId FROM reading_history ORDER BY lastReadAt DESC LIMIT 200)")
    suspend fun pruneHistory()

    @Query("DELETE FROM reading_history")
    suspend fun clearHistory()
}
