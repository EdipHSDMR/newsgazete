package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsArticleDao {
    @Query("SELECT * FROM news_articles ORDER BY publishedAtEpochMs DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<NewsArticleEntity>>

    @Query("SELECT COUNT(*) FROM news_articles")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM news_articles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NewsArticleEntity?

    @Query(
        """SELECT * FROM news_articles
           WHERE publishedAtEpochMs < :timestamp
              OR (publishedAtEpochMs = :timestamp AND id < :id)
           ORDER BY publishedAtEpochMs DESC, id DESC
           LIMIT :limit"""
    )
    suspend fun loadOlder(timestamp: Long, id: String, limit: Int): List<NewsArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(articles: List<NewsArticleEntity>)

    @Query("DELETE FROM news_articles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM news_articles")
    suspend fun clearAll()

    @Query(
        """DELETE FROM news_articles
           WHERE id NOT IN (
               SELECT id FROM news_articles
               ORDER BY publishedAtEpochMs DESC, id DESC
               LIMIT :keepLimit
           )"""
    )
    suspend fun pruneOldest(keepLimit: Int)
}

