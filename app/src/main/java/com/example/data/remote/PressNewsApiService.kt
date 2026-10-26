package com.example.data.remote

import android.text.Html
import com.example.model.SourceComparison
import com.example.model.Story
import com.example.util.NewsSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class PressNewsPage(
    val page: Int,
    val totalPages: Int,
    val total: Int,
    val stories: List<Story>,
)

/** Press.org.tr is accessed through the private service gateway; the provider key never reaches the APK. */
class PressNewsApiService(private val httpClient: OkHttpClient) {
    suspend fun fetchPage(page: Int, pageSize: Int = MAX_PAGE_SIZE): PressNewsPage? = withContext(Dispatchers.IO) {
        if (page < 1) return@withContext null

        try {
            val url = "${ApiGatewayConfig.BASE_URL}/v1/news?page=$page&limit=${pageSize.coerceIn(1, MAX_PAGE_SIZE)}"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Newsgazete/1.0")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: JSONArray()
                val stories = buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        toStory(item)?.let(::add)
                    }
                }
                PressNewsPage(
                    page = json.optInt("page", page),
                    totalPages = json.optInt("total_pages", page),
                    total = json.optInt("total", stories.size),
                    stories = stories,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun toStory(item: JSONObject): Story? {
        val idValue = item.optString("id").trim()
        val title = cleanText(item.optString("title"))
        if (title.length < 5) return null

        val summaryText = cleanText(item.optString("summary"))
        val bodyText = cleanText(item.optString("content_html"))
        val sourceText = bodyText.ifBlank { summaryText }.take(MAX_SOURCE_TEXT_CHARS)
        val summary = NewsSummary.concise(summaryText.ifBlank { bodyText }, title)
        val slug = item.optString("slug").trim()
        val url = normalizeUrl(item.optString("url").ifBlank {
            if (slug.isNotBlank()) "https://www.press.org.tr/ajans/$slug" else ""
        })
        val stableId = idValue.takeIf(String::isNotBlank)
            ?: slug.takeIf(String::isNotBlank)
            ?: UUID.nameUUIDFromBytes("$title|$url".toByteArray(Charsets.UTF_8)).toString()
        val id = "pha-$stableId"
        val epoch = parseDate(item.optString("date_published"))
        val publisherName = cleanText(item.optString("organization")).ifBlank { "Press Haber Ajansı" }
        val publisher = if (publisherName.contains("PHA", ignoreCase = true)) publisherName else "$publisherName (PHA)"
        val timeAgo = relativeTime(epoch)

        return Story(
            id = id,
            title = title,
            summary = summary,
            topic = topic(item.optJSONArray("sectors")),
            publisher = publisher,
            publishedAt = timeAgo,
            publishedAtEpochMs = epoch,
            readTimeMin = maxOf(1, sourceText.split(WHITESPACE).count() / WORDS_PER_MINUTE),
            imageUrl = imageUrl(item),
            isBreaking = title.contains("son dakika", ignoreCase = true),
            isManset = false,
            whatHappened = "",
            whyItMatters = "",
            whatIsUnknown = "",
            fullText = sourceText,
            paragraphs = NewsSummary.paragraphs(sourceText).take(MAX_PARAGRAPHS),
            comparisons = listOf(
                SourceComparison(
                    publisher = publisher,
                    headline = title,
                    angle = "Ajans haberi",
                    publishedTime = timeAgo,
                    url = url,
                ),
            ),
            canonicalUrl = url,
            isSaved = false,
        )
    }

    private fun imageUrl(item: JSONObject): String {
        val primary = item.opt("image")
        val primaryUrl = when (primary) {
            is String -> primary
            is JSONObject -> primary.optString("url").ifBlank { primary.optString("download") }
            else -> ""
        }
        if (primaryUrl.isNotBlank()) normalizeMediaUrl(primaryUrl).takeIf(String::isNotBlank)?.let { return it }

        val images = item.optJSONArray("images") ?: return ""
        for (index in 0 until images.length()) {
            val image = images.optJSONObject(index) ?: continue
            val url = image.optString("url").ifBlank { image.optString("download") }
            normalizeMediaUrl(url).takeIf(String::isNotBlank)?.let { return it }
        }
        return ""
    }

    private fun topic(sectors: JSONArray?): String {
        if (sectors == null || sectors.length() == 0) return "Gündem"
        val first = sectors.optJSONObject(0) ?: return "Gündem"
        val slug = first.optString("slug").lowercase(Locale.ROOT)
        val name = cleanText(first.optString("name"))
        return when (slug) {
            "spor" -> "Spor"
            "finans" -> "Ekonomi"
            "teknoloji" -> "Teknoloji"
            "saglik" -> "Sağlık"
            "kultur-sanat", "medya-eglence" -> "Kültür-Sanat"
            "cevre-surdurulebilirlik" -> "Dünya"
            else -> name.ifBlank { "Gündem" }
        }
    }

    private fun cleanText(value: String): String = Html.fromHtml(
        value.replace("<script\\b[^>]*>.*?</script>".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace("<style\\b[^>]*>.*?</style>".toRegex(RegexOption.IGNORE_CASE), " "),
        Html.FROM_HTML_MODE_LEGACY,
    ).toString()
        .replace('\u00a0', ' ')
        .replace("[\\s\\u200B]+".toRegex(), " ")
        .trim()

    private fun normalizeMediaUrl(value: String): String = normalizeUrl(value, "https://www.press.org.tr/")

    private fun normalizeUrl(value: String, baseUrl: String = ""): String {
        if (value.isBlank()) return ""
        return try {
            val raw = URI(value.trim())
            val resolved = if (raw.isAbsolute) raw else URI(baseUrl).resolve(raw)
            if (resolved.scheme !in setOf("https", "http") || resolved.host.isNullOrBlank()) ""
            else resolved.toASCIIString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseDate(value: String): Long {
        if (value.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
        )
        for (pattern in patterns) {
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(value)?.time
            }.getOrNull()?.let { return it }
        }
        return 0L
    }

    private fun relativeTime(epochMs: Long): String {
        if (epochMs <= 0L) return "Tarih bilgisi yok"
        val diff = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
        val minutes = (diff / 60_000L).toInt()
        val hours = (diff / 3_600_000L).toInt()
        val days = (diff / 86_400_000L).toInt()
        return when {
            minutes < 1 -> "Az önce"
            minutes < 60 -> "$minutes dk önce"
            hours < 24 -> "$hours saat önce"
            days == 1 -> "Dün"
            days < 7 -> "$days gün önce"
            else -> SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("tr-TR")).format(Date(epochMs))
        }
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_SOURCE_TEXT_CHARS = 6_000
        const val MAX_PARAGRAPHS = 24
        const val WORDS_PER_MINUTE = 220
        val WHITESPACE = "\\s+".toRegex()
    }
}

