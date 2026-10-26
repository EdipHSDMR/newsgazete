package com.example.data.local

import androidx.room.*
import com.example.model.NotificationItem
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Entity(tableName = "notification_inbox")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val category: String,
    val title: String,
    val body: String,
    val storyId: String?,
    val createdAt: Long,
    val isRead: Boolean = false,
) {
    fun item(now: Long = System.currentTimeMillis()): NotificationItem {
        val minutes = ((now - createdAt) / 60_000).coerceAtLeast(0)
        val time = when {
            minutes < 1 -> "Az önce"
            minutes < 60 -> "$minutes dk önce"
            minutes < 1_440 -> "${minutes / 60} saat önce"
            else -> Instant.ofEpochMilli(createdAt).atZone(ZoneId.of("Europe/Istanbul"))
                .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
        }
        return NotificationItem(id, category, title, body, time, isRead, storyId, createdAt)
    }
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_inbox ORDER BY createdAt DESC, id DESC LIMIT 100")
    fun observeInbox(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: NotificationEntity): Long

    @Query("UPDATE notification_inbox SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notification_inbox SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notification_inbox WHERE id NOT IN (SELECT id FROM notification_inbox ORDER BY createdAt DESC LIMIT 100)")
    suspend fun prune()

    @Query("DELETE FROM notification_inbox")
    suspend fun clear()
}
