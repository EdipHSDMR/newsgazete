package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Story
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object StorySnapshot {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(story: Story): String = json.encodeToString(story)
    fun decode(value: String): Story? = runCatching { json.decodeFromString<Story>(value) }.getOrNull()
}

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey val storyId: String,
    val storyJson: String,
    val lastReadAt: Long,
    val anchorKey: String,
    val itemOffset: Int,
    val progressPercent: Int,
) {
    fun story(): Story? = StorySnapshot.decode(storyJson)
}
