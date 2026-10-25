package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.SourceComparison
import com.example.model.Story
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "news_articles",
    indices = [Index(value = ["publishedAtEpochMs", "id"]), Index(value = ["topic"])],
)
data class NewsArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val topic: String,
    val publisher: String,
    val publishedAt: String,
    val publishedAtEpochMs: Long,
    val readTimeMin: Int,
    val imageUrl: String,
    val isBreaking: Boolean,
    val sourceCount: Int,
    val whatHappened: String,
    val whyItMatters: String,
    val whatIsUnknown: String,
    val fullText: String,
    val paragraphsJson: String,
    val comparisonsJson: String,
    val canonicalUrl: String,
) {
    fun toStory(): Story = Story(
        id = id,
        title = title,
        summary = summary,
        topic = topic,
        publisher = publisher,
        publishedAt = if (publishedAtEpochMs > 0) relativeTime(publishedAtEpochMs) else publishedAt,
        publishedAtEpochMs = publishedAtEpochMs,
        readTimeMin = readTimeMin,
        imageUrl = imageUrl,
        isBreaking = isBreaking,
        sourceCount = sourceCount,
        whatHappened = whatHappened,
        whyItMatters = whyItMatters,
        whatIsUnknown = whatIsUnknown,
        fullText = fullText,
        paragraphs = decodeParagraphs(paragraphsJson),
        comparisons = decodeComparisons(comparisonsJson),
        canonicalUrl = canonicalUrl,
    )

    companion object {
        fun fromStory(story: Story): NewsArticleEntity {
            val paragraphs = JSONArray()
            story.paragraphs.take(12).forEach { paragraphs.put(it.take(1_200)) }

            val comparisons = JSONArray()
            story.comparisons.take(8).forEach { item ->
                comparisons.put(
                    JSONObject()
                        .put("publisher", item.publisher)
                        .put("headline", item.headline)
                        .put("angle", item.angle)
                        .put("publishedTime", item.publishedTime)
                        .put("url", item.url)
                )
            }

            return NewsArticleEntity(
                id = story.id,
                title = story.title.take(500),
                summary = story.summary.take(800),
                topic = story.topic,
                publisher = story.publisher,
                publishedAt = story.publishedAt,
                publishedAtEpochMs = story.publishedAtEpochMs,
                readTimeMin = story.readTimeMin,
                imageUrl = story.imageUrl,
                isBreaking = story.isBreaking,
                sourceCount = story.sourceCount,
                whatHappened = story.whatHappened.take(1_200),
                whyItMatters = story.whyItMatters.take(1_200),
                whatIsUnknown = story.whatIsUnknown.take(1_200),
                fullText = story.fullText.take(6_000),
                paragraphsJson = paragraphs.toString(),
                comparisonsJson = comparisons.toString(),
                canonicalUrl = story.canonicalUrl.take(2_000),
            )
        }

        private fun decodeParagraphs(value: String): List<String> = try {
            val array = JSONArray(value)
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        } catch (_: Exception) {
            emptyList()
        }

        private fun decodeComparisons(value: String): List<SourceComparison> = try {
            val array = JSONArray(value)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                SourceComparison(
                    publisher = item.optString("publisher"),
                    headline = item.optString("headline"),
                    angle = item.optString("angle"),
                    publishedTime = item.optString("publishedTime"),
                    url = item.optString("url"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        private fun relativeTime(timestamp: Long): String {
            val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0)
            val hours = minutes / 60
            val days = hours / 24
            return when {
                minutes <= 10 -> "Az önce"
                minutes < 60 -> "$minutes dk önce"
                hours < 24 -> "$hours saat önce"
                days == 1L -> "Dün"
                days < 7 -> "$days gün önce"
                else -> SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("tr-TR")).format(Date(timestamp))
            }
        }
    }
}
