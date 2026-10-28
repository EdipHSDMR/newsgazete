package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Story

@Entity(tableName = "saved_articles")
data class SavedArticleEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val summary: String,
    val topic: String,
    val publisher: String,
    val publishedAt: String,
    val readTimeMin: Int = 2,
    val imageUrl: String = "",
    val isBreaking: Boolean = false,
    val isManset: Boolean = false,
    val sourceCount: Int = 1,
    val whatHappened: String = "",
    val whyItMatters: String = "",
    val whatIsUnknown: String = "",
    val fullText: String = "",
    val canonicalUrl: String = "",
    @ColumnInfo(defaultValue = "''") val storyJson: String = "",
    @ColumnInfo(defaultValue = "0") val offlineReady: Boolean = false,
    @ColumnInfo(defaultValue = "''") val offlineImagePath: String = "",
    val savedAtTimestamp: Long = System.currentTimeMillis(),
) {
    val hasOfflineImage: Boolean get() = offlineImagePath.isNotBlank() && java.io.File(offlineImagePath).isFile

    fun toStory(): Story {
        StorySnapshot.decode(storyJson)?.let {
            return it.copy(isSaved = true, imageUrl = if (hasOfflineImage) localImageUri(offlineImagePath) ?: it.imageUrl else it.imageUrl)
        }
        return Story(
            id = id,
            title = title,
            summary = summary,
            topic = topic,
            publisher = publisher,
            publishedAt = publishedAt,
            readTimeMin = readTimeMin,
            imageUrl = if (hasOfflineImage) localImageUri(offlineImagePath) ?: imageUrl else imageUrl,
            isBreaking = isBreaking,
            isManset = isManset,
            sourceCount = sourceCount,
            whatHappened = whatHappened,
            whyItMatters = whyItMatters,
            whatIsUnknown = whatIsUnknown,
            fullText = fullText,
            canonicalUrl = canonicalUrl,
            isSaved = true,
        )
    }

    companion object {
        private fun localImageUri(path: String): String? = java.io.File(path)
            .takeIf { it.isFile }
            ?.toURI()
            ?.toString()

        fun fromStory(story: Story, offlineReady: Boolean = false): SavedArticleEntity {
            return SavedArticleEntity(
                id = story.id,
                storyJson = StorySnapshot.encode(story),
                offlineReady = offlineReady,
                title = story.title,
                summary = story.summary,
                topic = story.topic,
                publisher = story.publisher,
                publishedAt = story.publishedAt,
                readTimeMin = story.readTimeMin,
                imageUrl = story.imageUrl,
                isBreaking = story.isBreaking,
                isManset = story.isManset,
                sourceCount = story.sourceCount,
                whatHappened = story.whatHappened,
                whyItMatters = story.whyItMatters,
                whatIsUnknown = story.whatIsUnknown,
                fullText = story.fullText,
                canonicalUrl = story.canonicalUrl,
                savedAtTimestamp = System.currentTimeMillis(),
            )
        }
    }
}

