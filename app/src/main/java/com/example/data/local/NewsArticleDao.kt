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

    @Query("SELECT DISTINCT topic FROM news_articles WHERE topic != '' ORDER BY topic")
    fun observeTopics(): Flow<List<String>>

    @Query("SELECT DISTINCT publisher FROM news_articles WHERE publisher != '' ORDER BY publisher")
    fun observePublishers(): Flow<List<String>>

    @Query("""SELECT * FROM news_articles WHERE
        searchText LIKE :term1 AND searchText LIKE :term2 AND searchText LIKE :term3 AND
        searchText LIKE :term4 AND searchText LIKE :term5 AND searchText LIKE :term6 AND
        (:topic IS NULL OR topic = :topic) AND (:publisher IS NULL OR publisher = :publisher) AND
        (:fromDate IS NULL OR publishedAtEpochMs >= :fromDate) AND
        (:untilDate IS NULL OR publishedAtEpochMs < :untilDate)
        ORDER BY publishedAtEpochMs DESC, id DESC LIMIT :limit OFFSET :offset""")
    suspend fun search(term1: String, term2: String, term3: String, term4: String, term5: String, term6: String,
        topic: String?, publisher: String?, fromDate: Long?, untilDate: Long?, limit: Int, offset: Int): List<NewsArticleEntity>

    @Query("SELECT id, title, summary, publisher, topic, canonicalUrl FROM news_articles WHERE id > :after ORDER BY id LIMIT 300")
    suspend fun topicCandidates(after: String): List<TopicCandidate>

    @Query("UPDATE news_articles SET topic = :topic, searchText = :searchText WHERE id = :id")
    suspend fun updateTopic(id: String, topic: String, searchText: String)

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

data class TopicCandidate(val id: String, val title: String, val summary: String, val publisher: String, val topic: String, val canonicalUrl: String)

