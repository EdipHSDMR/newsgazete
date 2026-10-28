package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedArticleDao {
    @Query("SELECT * FROM saved_articles ORDER BY savedAtTimestamp DESC")
    fun getAllSavedArticles(): Flow<List<SavedArticleEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_articles WHERE id = :id)")
    fun isArticleSaved(id: String): Flow<Boolean>

    @Query("SELECT * FROM saved_articles WHERE id = :id LIMIT 1")
    suspend fun getSavedArticleById(id: String): SavedArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: SavedArticleEntity)

    @Query("UPDATE saved_articles SET storyJson = :snapshot, fullText = :body, summary = :summary, offlineReady = :ready WHERE id = :id")
    suspend fun updateOffline(id: String, snapshot: String, body: String, summary: String, ready: Boolean)

    @Query("UPDATE saved_articles SET offlineImagePath = :path WHERE id = :id")
    suspend fun updateOfflineImage(id: String, path: String)

    @Query("DELETE FROM saved_articles WHERE id = :id")
    suspend fun deleteArticleById(id: String)

    @Query("DELETE FROM saved_articles")
    suspend fun clearAll()
}

