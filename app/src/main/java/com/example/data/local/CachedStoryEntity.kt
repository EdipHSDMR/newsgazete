package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.SourceComparison
import com.example.model.Story

@Entity(tableName = "cached_stories")
data class CachedStoryEntity(
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
    val paragraphsJoined: String = "",
    val fetchedTimestamp: Long = System.currentTimeMillis(),
) {
    fun toStory(isSaved: Boolean = false): Story {
        val parsedParagraphs = if (paragraphsJoined.isNotBlank()) {
            paragraphsJoined.split("\n\n").filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        return Story(
            id = id,
            title = title,
            summary = summary,
            topic = topic,
            publisher = publisher,
            publishedAt = publishedAt,
            readTimeMin = readTimeMin,
            imageUrl = imageUrl,
            isBreaking = isBreaking,
            isManset = isManset,
            sourceCount = sourceCount,
            whatHappened = whatHappened,
            whyItMatters = whyItMatters,
            whatIsUnknown = whatIsUnknown,
            fullText = fullText,
            paragraphs = parsedParagraphs,
            comparisons = listOf(
                SourceComparison(
                    publisher = publisher,
                    headline = title,
                    angle = "Haber metni ve ajans bülteni",
                    publishedTime = publishedAt,
                )
            ),
            canonicalUrl = canonicalUrl,
            isSaved = isSaved,
        )
    }

    companion object {
        fun fromStory(story: Story): CachedStoryEntity {
            return CachedStoryEntity(
                id = story.id,
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
                paragraphsJoined = story.paragraphs.joinToString("\n\n"),
                fetchedTimestamp = System.currentTimeMillis(),
            )
        }
    }
}

