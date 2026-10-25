package com.example.data.remote

import com.example.model.SourceComparison
import com.example.model.Story
import com.example.util.NewsSummary
import com.example.util.NewsStoryDeduplicator
import android.text.Html
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.UUID
import java.util.regex.Pattern

data class SourcePageMetadata(
    val description: String?,
    val imageUrl: String?,
)

class RssNewsService(private val httpClient: OkHttpClient) {
    private val quickClient = httpClient.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    // Açık, telif dostu kamu & haber RSS akışları
    private val rssSources = listOf(
        Triple("https://www.ensonhaber.com/rss/ensonhaber.xml", "Ensonhaber", "Gündem"),
        Triple("https://feeds.bbci.co.uk/turkce/rss.xml", "BBC Türkçe", "Dünya"),
        Triple("https://www.ntv.com.tr/gundem.rss", "NTV", "Gündem"),
        Triple("https://www.ntv.com.tr/ekonomi.rss", "NTV", "Ekonomi"),
        Triple("https://www.ntv.com.tr/teknoloji.rss", "NTV", "Teknoloji"),
        Triple("https://www.ntv.com.tr/spor.rss", "NTV", "Spor"),
        Triple("https://www.trthaber.com/sondakika.rss", "TRT Haber", "Gündem"),
        Triple("https://www.trthaber.com/ekonomi_articles.rss", "TRT Haber", "Ekonomi"),
        Triple("https://www.trthaber.com/bilim_teknoloji_articles.rss", "TRT Haber", "Teknoloji"),
        Triple("https://www.trthaber.com/spor_articles.rss", "TRT Spor", "Spor"),
        Triple("https://www.haberturk.com/rss", "Habertürk", "Gündem"),
        Triple("https://www.haberturk.com/rss/ekonomi.xml", "Habertürk", "Ekonomi"),
        Triple("https://www.haberturk.com/rss/spor.xml", "Habertürk", "Spor"),
        Triple("https://www.aa.com.tr/tr/rss/default?cat=guncel", "Anadolu Ajansı", "Gündem"),
        Triple("https://www.aa.com.tr/tr/rss/default?cat=ekonomi", "Anadolu Ajansı", "Ekonomi"),
        Triple("https://www.aa.com.tr/tr/rss/default?cat=spor", "Anadolu Ajansı", "Spor"),
        Triple("https://www.cnnturk.com/feed/rss/all/news", "CNN Türk", "Gündem"),
        Triple("https://www.cnnturk.com/feed/rss/ekonomi/news", "CNN Türk", "Ekonomi"),
        Triple("https://www.cnnturk.com/feed/rss/spor/news", "CNN Türk", "Spor"),
        Triple("https://www.diken.com.tr/feed/", "Diken", "Gündem"),
        Triple("https://www.sozcu.com.tr/rss/sondakika.xml", "Sözcü", "Gündem"),
        Triple("https://www.hurriyet.com.tr/rss/gundem", "Hürriyet", "Gündem"),
        Triple("https://www.milliyet.com.tr/rss/rssnew/gundem.xml", "Milliyet", "Gündem"),
        Triple("https://www.press.org.tr/ajans/rss.xml", "Press Haber Ajansı (PHA)", "Gündem"),
    )
    private val feedSemaphore = Semaphore(4)
    private val quickSources = rssSources
        .filter { (_, publisher, _) -> publisher in setOf("Ensonhaber", "BBC Türkçe", "NTV", "TRT Haber", "Habertürk", "Anadolu Ajansı") }
        .distinctBy { it.second }

    /** A small real-news batch for cold starts; remaining feeds are fetched immediately afterward. */
    suspend fun fetchQuickNews(): List<Story> = withContext(Dispatchers.IO) {
        val stories = coroutineScope {
            quickSources.map { (feedUrl, publisherName, defaultTopic) ->
                async {
                    try {
                        val request = Request.Builder()
                            .url(feedUrl)
                            .header("User-Agent", "Newsgazete/1.0 (RSS reader)")
                            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                            .build()
                        quickClient.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) emptyList()
                            else parseRssFeedRobust(response.body?.string().orEmpty(), publisherName, defaultTopic)
                                .take(MAX_ITEMS_PER_FEED)
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll().flatten()
        }
        NewsStoryDeduplicator.merge(stories)
            .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
            .take(NEWS_QUICK_START_LIMIT)
            .toMutableList()
            .also { if (it.isNotEmpty()) it[0] = it[0].copy(isManset = true) }
    }

    suspend fun fetchRealNews(): List<Story> = withContext(Dispatchers.IO) {
        val semaphore = feedSemaphore
        val allStories = coroutineScope {
            rssSources.map { (feedUrl, publisherName, defaultTopic) ->
                async {
                    semaphore.withPermit {
                        try {
                            val request = Request.Builder()
                                .url(feedUrl)
                                .header("User-Agent", "Newsgazete/1.0 (RSS reader)")
                                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                                .build()

                            httpClient.newCall(request).execute().use { response ->
                                if (!response.isSuccessful) return@withPermit emptyList()
                                val xmlData = response.body?.string().orEmpty()
                                if (xmlData.isBlank()) emptyList()
                                else parseRssFeedRobust(xmlData, publisherName, defaultTopic).take(MAX_ITEMS_PER_FEED)
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            }.awaitAll().flatten()
        }

        val uniqueStories = NewsStoryDeduplicator.merge(allStories)
            .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
            .take(MAX_STORIES_PER_REFRESH)
            .toMutableList()

        if (uniqueStories.isNotEmpty()) uniqueStories[0] = uniqueStories[0].copy(isManset = true)
        uniqueStories
    }

    /** Reads only publisher-provided metadata. It never invents or copies article body text. */
    suspend fun fetchSourceMetadata(url: String): SourcePageMetadata? = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return@withContext null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return@withContext null

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Newsgazete/1.0 (RSS reader)")
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.body == null) return@withContext null
                val html = response.peekBody(MAX_SOURCE_PAGE_BYTES).string()
                SourcePageMetadata(
                    description = extractMetaDescription(html),
                    imageUrl = extractMetaImage(html, url),
                ).takeIf { it.description != null || it.imageUrl != null }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sourceSummary(description: String, maxCharacters: Int = 380, title: String = ""): String =
        NewsSummary.concise(description, title, maxCharacters)

    fun sourceParagraphs(description: String): List<String> =
        SENTENCE_BOUNDARY.split(description.trim())
            .map(String::trim)
            .filter(String::isNotBlank)
            .chunked(3)
            .map { it.joinToString(" ") }

    private fun extractMetaDescription(html: String): String? {
        val acceptedNames = setOf("og:description", "twitter:description", "description")
        val metaTags = META_TAG.findAll(html)
        for (match in metaTags) {
            val tag = match.value
            val name = META_NAME.find(tag)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT) ?: continue
            if (name !in acceptedNames) continue
            val content = META_CONTENT.find(tag)?.groupValues?.getOrNull(1).orEmpty()
            val clean = cleanHtml(content)
            if (clean.length >= 40) return clean.take(MAX_SOURCE_DESCRIPTION_CHARS)
        }
        return null
    }

    private fun extractMetaImage(html: String, pageUrl: String): String? {
        val acceptedNames = setOf("og:image", "og:image:url", "twitter:image", "twitter:image:src", "image")
        for (match in META_TAG.findAll(html)) {
            val tag = match.value
            val name = META_NAME.find(tag)?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT)
            if (name in acceptedNames) {
                val content = META_CONTENT.find(tag)?.groupValues?.getOrNull(1).orEmpty()
                normalizeImageUrl(content, pageUrl).takeIf(String::isNotBlank)?.let { return it }
            }
        }

        val imageSource = IMAGE_SOURCE_HREF.find(html)?.groupValues?.getOrNull(1).orEmpty()
        return normalizeImageUrl(imageSource, pageUrl).takeIf(String::isNotBlank)
    }

    /**
     * Regex tabanlı hatasız RSS / Atom Ayrıştırıcı (CDATA, Enclosure, Media ve HTML içeriği tam destekler)
     */
    private fun parseRssFeedRobust(xml: String, publisher: String, defaultTopic: String): List<Story> {
        val list = mutableListOf<Story>()

        // Find all <item>...</item> or <entry>...</entry> blocks
        val itemPattern = Pattern.compile("<(?:[\\w.-]+:)?(?:item|entry)\\b[^>]*>(.*?)</(?:[\\w.-]+:)?(?:item|entry)\\s*>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = itemPattern.matcher(xml)

        while (matcher.find()) {
            val itemBlock = matcher.group(1) ?: continue

            val rawTitle = extractTagContent(itemBlock, "title")
            val rawLink = extractLink(itemBlock)
            val rawDesc = extractTagContent(itemBlock, "description")
                .ifBlank { extractTagContent(itemBlock, "summary") }
                .ifBlank { extractTagContent(itemBlock, "encoded") }
            val rawPubDate = extractTagContent(itemBlock, "pubDate")
                .ifBlank { extractTagContent(itemBlock, "published") }
                .ifBlank { extractTagContent(itemBlock, "updated") }
                .ifBlank { extractTagContent(itemBlock, "date") }
            val rawCategory = extractTagContent(itemBlock, "category")

            val cleanTitle = cleanHtml(rawTitle)
            val cleanDesc = cleanHtml(rawDesc)

            if (cleanTitle.isNotBlank() && cleanTitle.length > 6) {
                // Classify topic intelligently by content and category
                val classifiedTopic = classifyTopic(cleanTitle, cleanDesc, rawCategory, defaultTopic)

                // Keep only an image explicitly supplied by the publisher's feed.
                val imageUrl = extractImageUrl(itemBlock, rawLink)

                val timeAgo = formatPubDate(rawPubDate)
                val readMin = maxOf(2, cleanDesc.length / 220)
                val publishedAtEpochMs = parsePubDateEpochMs(rawPubDate)
                // Keep the existing title-based ID so refreshes update the user's old cache
                // instead of duplicating every story after the app upgrade.
                val id = UUID.nameUUIDFromBytes(cleanTitle.toByteArray(Charsets.UTF_8)).toString()
                val sourceText = cleanDesc.take(MAX_SOURCE_DESCRIPTION_CHARS)

                list.add(
                    Story(
                        id = id,
                        title = cleanTitle,
                        summary = sourceSummary(sourceText, title = cleanTitle),
                        topic = classifiedTopic,
                        publisher = publisher,
                        publishedAt = timeAgo,
                        publishedAtEpochMs = publishedAtEpochMs,
                        readTimeMin = readMin,
                        imageUrl = imageUrl,
                        isBreaking = cleanTitle.contains("son dakika", ignoreCase = true),
                        isManset = false,
                        whatHappened = "",
                        whyItMatters = "",
                        whatIsUnknown = "",
                        fullText = sourceText,
                        paragraphs = sourceParagraphs(sourceText),
                        comparisons = listOf(
                            SourceComparison(
                                publisher = publisher,
                                headline = cleanTitle,
                                angle = "Kaynağın yayımladığı haber",
                                publishedTime = timeAgo,
                                url = rawLink,
                            )
                        ),
                        canonicalUrl = rawLink,
                        isSaved = false,
                    )
                )
            }
        }
        return list
    }

    private fun classifyTopic(title: String, desc: String, category: String, defaultTopic: String): String {
        val combined = "$title $desc $category".lowercase(Locale.forLanguageTag("tr-TR"))

        return when {
            combined.contains("tbmm") || combined.contains("meclis") || combined.contains("cumhurbaşkanı") ||
                    combined.contains("bakan") || combined.contains("hükümet") || combined.contains("seçim") ||
                    combined.contains("chp") || combined.contains("ak parti") || combined.contains("mhp") ||
                    combined.contains("yargı") || combined.contains("kanun") || combined.contains("milletvekili") ||
                    combined.contains("politika") || combined.contains("anayasa") || combined.contains("belediye başkanı") -> "Siyaset"

            combined.contains("faiz") || combined.contains("enflasyon") || combined.contains("merkez bankası") ||
                    combined.contains("tcmb") || combined.contains("dolar") || combined.contains("euro") ||
                    combined.contains("borsa") || combined.contains("bist") || combined.contains("altın") ||
                    combined.contains("ihracat") || combined.contains("ithalat") || combined.contains("asgari ücret") ||
                    combined.contains("bütçe") || combined.contains("kredi") || combined.contains("vergi") ||
                    combined.contains("ekonomi") || combined.contains("fiyat") -> "Ekonomi"

            combined.contains("yapay zeka") || combined.contains("yazılım") || combined.contains("akıllı telefon") ||
                    combined.contains("çip") || combined.contains("robot") || combined.contains("siber") ||
                    combined.contains("nasa") || combined.contains("uzay") || combined.contains("apple") ||
                    combined.contains("google") || combined.contains("microsoft") || combined.contains("teknoloji") ||
                    combined.contains("donanım") || combined.contains("uydu") -> "Teknoloji"

            combined.contains("maç") || combined.contains("futbol") || combined.contains("basketbol") ||
                    combined.contains("voleybol") || combined.contains("derbi") || combined.contains("süper lig") ||
                    combined.contains("uefa") || combined.contains("gol") || combined.contains("transfer") ||
                    combined.contains("şampiyon") || combined.contains("milli takım") || combined.contains("spor") -> "Spor"

            combined.contains("sağlık") || combined.contains("hastane") || combined.contains("ilaç") ||
                    combined.contains("aşı") || combined.contains("doktor") || combined.contains("tedavi") ||
                    combined.contains("kanser") || combined.contains("virüs") || combined.contains("beslenme") -> "Sağlık"

            combined.contains("konser") || combined.contains("festival") || combined.contains("film") ||
                    combined.contains("sinema") || combined.contains("tiyatro") || combined.contains("arkeoloji") ||
                    combined.contains("müze") || combined.contains("sergi") || combined.contains("edebiyat") ||
                    combined.contains("kültür") || combined.contains("sanat") || combined.contains("kitap") -> "Kültür-Sanat"

            combined.contains("dünya") || combined.contains("abd") || combined.contains("rusya") ||
                    combined.contains("ukrayna") || combined.contains("israil") || combined.contains("gazze") ||
                    combined.contains("çin") || combined.contains("avrupa") || combined.contains("nato") ||
                    combined.contains("bm ") || combined.contains("iran") -> "Dünya"

            defaultTopic.isNotBlank() -> defaultTopic
            else -> "Gündem"
        }
    }

    private fun extractTagContent(block: String, tagName: String): String {
        val pattern = Pattern.compile("<(?:[\\w.-]+:)?$tagName\\b(?:\\s+[^>]*)?>(.*?)</(?:[\\w.-]+:)?$tagName\\s*>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(block)
        return if (matcher.find()) {
            cleanHtml(matcher.group(1) ?: "")
        } else ""
    }

    private fun extractLink(block: String): String {
        val hrefPattern = Pattern.compile("<(?:[\\w.-]+:)?link\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE)
        val hrefMatcher = hrefPattern.matcher(block)
        if (hrefMatcher.find()) {
            val href = hrefMatcher.group(1)
            if (!href.isNullOrBlank()) return href
        }

        val textPattern = Pattern.compile("<(?:[\\w.-]+:)?link\\b[^>]*>(.*?)</(?:[\\w.-]+:)?link\\s*>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val textMatcher = textPattern.matcher(block)
        return if (textMatcher.find()) cleanHtml(textMatcher.group(1) ?: "") else ""
    }

    private fun extractImageUrl(block: String, articleUrl: String): String {
        // Publisher media:content is usually the highest resolution image.
        val mediaContent = Regex("<media:content\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (tag in mediaContent.findAll(block)) {
            val url = URL_ATTRIBUTE.find(tag.value)?.groupValues?.getOrNull(1).orEmpty()
            if (url.isNotBlank()) normalizeImageUrl(url, articleUrl).takeIf(String::isNotBlank)?.let { return it }
        }

        // Some feeds publish images only as an enclosure, thumbnail, or inline HTML image.
        val enclosure = Regex("<enclosure\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (tag in enclosure.findAll(block)) {
            val value = tag.value
            val url = URL_ATTRIBUTE.find(value)?.groupValues?.getOrNull(1).orEmpty()
            val type = TYPE_ATTRIBUTE.find(value)?.groupValues?.getOrNull(1).orEmpty()
            if (url.isNotBlank() && (type.startsWith("image/", ignoreCase = true) || isLikelyImageUrl(url))) {
                normalizeImageUrl(url, articleUrl).takeIf(String::isNotBlank)?.let { return it }
            }
        }

        val thumbnail = Regex("<media:thumbnail\\b[^>]*>", RegexOption.IGNORE_CASE)
        for (tag in thumbnail.findAll(block)) {
            val url = URL_ATTRIBUTE.find(tag.value)?.groupValues?.getOrNull(1).orEmpty()
            if (url.isNotBlank()) normalizeImageUrl(url, articleUrl).takeIf(String::isNotBlank)?.let { return it }
        }

        val imageTag = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
        val preferredAttributes = listOf("data-src", "data-original", "data-lazy-src", "data-image", "src", "srcset")
        for (tag in imageTag.findAll(block)) {
            for (attribute in preferredAttributes) {
                val value = attributeValue(tag.value, attribute)
                val candidate = if (attribute == "srcset") value.substringBefore(',').trim().substringBefore(' ') else value
                if (candidate.isNotBlank()) normalizeImageUrl(candidate, articleUrl).takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return ""
    }

    private fun attributeValue(tag: String, attribute: String): String =
        Regex("\\b${Regex.escape(attribute)}\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.getOrNull(1).orEmpty()

    private fun normalizeImageUrl(rawUrl: String, baseUrl: String): String {
        val decoded = Html.fromHtml(rawUrl.trim(), Html.FROM_HTML_MODE_LEGACY).toString().trim()
            .trim('"', '\'', ' ', '\t', '\r', '\n')
        if (decoded.isBlank() || decoded.startsWith("data:", ignoreCase = true)) return ""
        return try {
            val rawUri = URI(decoded)
            val baseUri = URI(baseUrl)
            val resolved = if (rawUri.isAbsolute) rawUri else baseUri.resolve(rawUri)
            if (resolved.scheme !in setOf("http", "https") || resolved.host.isNullOrBlank()) ""
            else resolved.toASCIIString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun isLikelyImageUrl(url: String): Boolean {
        val lower = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".avif") ||
            lower.contains("/jpg/") || lower.contains("/image") || lower.contains("/resim/") ||
            lower.contains("/images/") || lower.contains("trthaber.com")
    }

    private fun cleanHtml(text: String): String {
        val withoutCdata = text.replace("<!\\[CDATA\\[".toRegex(), "")
            .replace("\\]\\]>".toRegex(), "")
            .replace("<script\\b[^>]*>.*?</script>".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace("<style\\b[^>]*>.*?</style>".toRegex(RegexOption.IGNORE_CASE), " ")
        return Html.fromHtml(withoutCdata, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00a0', ' ')
            .replace("[\\s\\u200B]+".toRegex(), " ")
            .trim()
    }

    private fun formatPubDate(pubDate: String): String {
        if (pubDate.isBlank()) return "Tarih bilgisi yok"
        val timestamp = parsePubDateEpochMs(pubDate)
        if (timestamp <= 0) return "Tarih bilgisi yok"
        val diffMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
        val minutes = (diffMs / 60_000L).toInt()
        val hours = (diffMs / 3_600_000L).toInt()
        val days = (diffMs / 86_400_000L).toInt()
        return when {
            minutes <= 10 -> "Az önce"
            minutes < 60 -> "$minutes dk önce"
            hours < 24 -> "$hours saat önce"
            days == 1 -> "Dün"
            days < 7 -> "$days gün önce"
            days < 31 -> "${days / 7} hafta önce"
            else -> SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("tr-TR")).format(Date(timestamp))
        }
    }

    private fun parsePubDateEpochMs(pubDate: String): Long {
        if (pubDate.isBlank()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
        )
        for (pattern in patterns) {
            try {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(pubDate)?.let { return it.time }
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private companion object {
        const val NEWS_QUICK_START_LIMIT = 80
        const val MAX_ITEMS_PER_FEED = 300
        const val MAX_STORIES_PER_REFRESH = 3_000
        const val MAX_SOURCE_PAGE_BYTES = 512L * 1024L
        const val MAX_SOURCE_DESCRIPTION_CHARS = 6_000
        val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+")
        val META_TAG = Regex("<meta\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val META_NAME = Regex("(?:property|name)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val META_CONTENT = Regex("\\bcontent\\s*=\\s*[\"']([^\"']*)[\"']", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val IMAGE_SOURCE_HREF = Regex("<link\\b(?=[^>]*\\brel=[\"'][^\"']*image_src[^\"']*[\"'])[^>]*\\bhref=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
        val URL_ATTRIBUTE = Regex("\\burl\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val TYPE_ATTRIBUTE = Regex("\\btype\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    }


}

