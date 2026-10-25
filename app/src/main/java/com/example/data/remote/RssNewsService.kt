package com.example.data.remote

import com.example.model.SourceComparison
import com.example.model.Story
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
import java.util.UUID
import java.util.regex.Pattern

class RssNewsService(private val httpClient: OkHttpClient) {

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
    )
    private val feedSemaphore = Semaphore(4)

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

        val uniqueStories = mergeDuplicateStories(allStories)
            .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
            .take(MAX_STORIES_PER_REFRESH)
            .toMutableList()

        if (uniqueStories.isNotEmpty()) uniqueStories[0] = uniqueStories[0].copy(isManset = true)
        uniqueStories
    }

    /** Reads only a publisher-provided meta description; it never invents or copies article body text. */
    suspend fun fetchSourceDescription(url: String): String? = withContext(Dispatchers.IO) {
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
                extractMetaDescription(html)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun sourceSummary(description: String, maxCharacters: Int = 440): String {
        val text = description.trim()
        if (text.length <= maxCharacters) return text
        val sentences = SENTENCE_BOUNDARY.split(text).filter(String::isNotBlank)
        val complete = StringBuilder()
        for (sentence in sentences) {
            val nextLength = complete.length + (if (complete.isEmpty()) 0 else 1) + sentence.length
            if (nextLength > maxCharacters) break
            if (complete.isNotEmpty()) complete.append(' ')
            complete.append(sentence)
        }
        if (complete.isNotEmpty()) return complete.toString()
        val boundary = text.lastIndexOf(' ', maxCharacters - 2).takeIf { it > 0 } ?: maxCharacters - 1
        return text.take(boundary).trimEnd() + "…"
    }

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

    private fun mergeDuplicateStories(stories: List<Story>): List<Story> {
        val mergedById = LinkedHashMap<String, Story>()
        val idByUrl = mutableMapOf<String, String>()
        val idByTitle = mutableMapOf<String, String>()

        for (story in stories.sortedByDescending { it.publishedAtEpochMs }) {
            val urlKey = normalizeUrl(story.canonicalUrl)
            val titleKey = normalizeTitle(story.title)
            val existingId = (urlKey.takeIf(String::isNotBlank)?.let(idByUrl::get))
                ?: titleKey.takeIf(String::isNotBlank)?.let(idByTitle::get)
            val existing = existingId?.let(mergedById::get)
            if (existing == null) {
                mergedById[story.id] = story
                if (urlKey.isNotBlank()) idByUrl[urlKey] = story.id
                if (titleKey.isNotBlank()) idByTitle[titleKey] = story.id
                continue
            }

            val comparisons = (existing.comparisons + story.comparisons)
                .distinctBy { it.publisher.lowercase(Locale.ROOT) to normalizeUrl(it.url) }
                .take(8)
            mergedById[existing.id] = existing.copy(
                sourceCount = comparisons.size.coerceAtLeast(existing.sourceCount),
                comparisons = comparisons,
                canonicalUrl = existing.canonicalUrl.ifBlank { story.canonicalUrl },
            )
            if (urlKey.isNotBlank()) idByUrl[urlKey] = existing.id
            if (titleKey.isNotBlank()) idByTitle[titleKey] = existing.id
        }

        return mergedById.values.toList()
    }

    private fun normalizeTitle(title: String): String = title
        .lowercase(Locale.forLanguageTag("tr-TR"))
        .replace("[^a-z0-9çğıöşü]".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

    private fun normalizeUrl(url: String): String {
        if (url.isBlank()) return ""
        return try {
            val uri = java.net.URI(url.trim())
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (host.isBlank()) return url.trim().substringBefore('#').trimEnd('/')
            val path = uri.rawPath.orEmpty().trimEnd('/').ifBlank { "/" }
            val query = uri.rawQuery.orEmpty().split('&')
                .filterNot { it.substringBefore('=').lowercase(Locale.ROOT).startsWith("utm_") ||
                    it.substringBefore('=').lowercase(Locale.ROOT) in setOf("fbclid", "gclid") }
                .joinToString("&")
            "https://$host$path" + if (query.isBlank()) "" else "?$query"
        } catch (_: Exception) {
            url.trim().substringBefore('#').trimEnd('/')
        }
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
                val imageUrl = extractImageUrl(itemBlock)

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
                        summary = sourceSummary(sourceText),
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

    private fun extractImageUrl(block: String): String {
        // 1. Check enclosure tag
        val encPattern = Pattern.compile("<enclosure[^>]+url=[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val encMatcher = encPattern.matcher(block)
        if (encMatcher.find()) {
            val u = encMatcher.group(1)
            if (!u.isNullOrBlank() && isLikelyImageUrl(u)) return u
        }

        // 2. Check media:content or media:thumbnail
        val mediaPattern = Pattern.compile("<media:(?:content|thumbnail)[^>]+url=[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val mediaMatcher = mediaPattern.matcher(block)
        if (mediaMatcher.find()) {
            val u = mediaMatcher.group(1)
            if (!u.isNullOrBlank()) return u
        }

        // 3. Check <img> tag inside description or content:encoded
        val imgPattern = Pattern.compile("<img[^>]+src=[\"'](https?://[^\"']+\\.(?:jpg|jpeg|png|webp)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val imgMatcher = imgPattern.matcher(block)
        if (imgMatcher.find()) {
            val u = imgMatcher.group(1)
            if (!u.isNullOrBlank()) return u
        }

        return ""
    }

    private fun isLikelyImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.contains("image") || lower.contains("/resim/") ||
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
        const val MAX_ITEMS_PER_FEED = 300
        const val MAX_STORIES_PER_REFRESH = 3_000
        const val MAX_SOURCE_PAGE_BYTES = 512L * 1024L
        const val MAX_SOURCE_DESCRIPTION_CHARS = 6_000
        val SENTENCE_BOUNDARY = Regex("(?<=[.!?])\\s+")
        val META_TAG = Regex("<meta\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val META_NAME = Regex("(?:property|name)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        val META_CONTENT = Regex("\\bcontent\\s*=\\s*[\"']([^\"']*)[\"']", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }


}
