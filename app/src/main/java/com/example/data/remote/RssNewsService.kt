package com.example.data.remote

import com.example.model.SourceComparison
import com.example.model.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        Triple("https://www.trthaber.com/sondakika.rss", "TRT Haber", "Gündem"),
        Triple("https://www.trthaber.com/ekonomi_articles.rss", "TRT Haber", "Ekonomi"),
        Triple("https://www.trthaber.com/bilim_teknoloji_articles.rss", "TRT Haber", "Teknoloji"),
        Triple("https://www.trthaber.com/spor_articles.rss", "TRT Spor", "Spor"),
        Triple("https://www.haberturk.com/rss", "Habertürk", "Gündem"),
    )

    // Unsplash curated editorial fallback news imagery (high resolution, CDN backed)
    private val topicFallbackImages = mapOf(
        "Siyaset" to listOf(
            "https://images.unsplash.com/photo-1541872703-74c5e44368f9?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1529107386315-e1a2ed48a620?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?auto=format&fit=crop&w=800&q=80",
        ),
        "Ekonomi" to listOf(
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1590283603385-17ffb3a7f29f?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?auto=format&fit=crop&w=800&q=80",
        ),
        "Teknoloji" to listOf(
            "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1485827404703-89b55fcc595e?auto=format&fit=crop&w=800&q=80",
        ),
        "Spor" to listOf(
            "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1574629810360-7efbbe195018?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1546519638-68e109498ffc?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1612872087720-bb876e2e67d1?auto=format&fit=crop&w=800&q=80",
        ),
        "Dünya" to listOf(
            "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1529107386315-e1a2ed48a620?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=800&q=80",
        ),
        "Gündem" to listOf(
            "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1504711434969-e33886168f5c?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=800&q=80",
        ),
        "Kültür-Sanat" to listOf(
            "https://images.unsplash.com/photo-1507676184212-d03ab07a01bf?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1460723237483-7a6dc9d0b212?auto=format&fit=crop&w=800&q=80",
        ),
        "Sağlık" to listOf(
            "https://images.unsplash.com/photo-1505751172876-fa1923c5c528?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=800&q=80",
        ),
        "Türkiye" to listOf(
            "https://images.unsplash.com/photo-1541432901042-2d8bd64b4a9b?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1524231757912-21f4fe3a7200?auto=format&fit=crop&w=800&q=80",
        )
    )

    suspend fun fetchRealNews(): List<Story> = withContext(Dispatchers.IO) {
        val allStories = mutableListOf<Story>()

        for ((feedUrl, publisherName, defaultTopic) in rssSources) {
            try {
                val request = Request.Builder()
                    .url(feedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val xmlData = response.body?.string()
                    if (!xmlData.isNullOrBlank()) {
                        val parsed = parseRssFeedRobust(xmlData, publisherName, defaultTopic)
                        allStories.addAll(parsed)
                    }
                }
            } catch (_: Exception) {}

            if (allStories.size >= 60) {
                break
            }
        }

        // Deduplicate stories by title similarity
        val uniqueStories = mutableListOf<Story>()
        val seenTitles = mutableSetOf<String>()

        for (item in allStories) {
            val simplified = item.title.lowercase().replace("[^a-zçğışöü0-9]".toRegex(), "").take(25)
            if (simplified.length > 5 && simplified !in seenTitles) {
                seenTitles.add(simplified)
                uniqueStories.add(item)
            }
        }

        // Make the first item a Manset
        if (uniqueStories.isNotEmpty()) {
            uniqueStories[0] = uniqueStories[0].copy(isManset = true)
        }
        uniqueStories
    }

    /**
     * Regex tabanlı hatasız RSS / Atom Ayrıştırıcı (CDATA, Enclosure, Media ve HTML içeriği tam destekler)
     */
    private fun parseRssFeedRobust(xml: String, publisher: String, defaultTopic: String): List<Story> {
        val list = mutableListOf<Story>()

        // Find all <item>...</item> or <entry>...</entry> blocks
        val itemPattern = Pattern.compile("<(?:item|entry)>(.*?)</(?:item|entry)>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = itemPattern.matcher(xml)

        var counter = 0
        while (matcher.find()) {
            val itemBlock = matcher.group(1) ?: continue

            val rawTitle = extractTagContent(itemBlock, "title")
            val rawLink = extractLink(itemBlock)
            val rawDesc = extractTagContent(itemBlock, "description").ifBlank { extractTagContent(itemBlock, "summary") }
            val rawPubDate = extractTagContent(itemBlock, "pubDate").ifBlank { extractTagContent(itemBlock, "updated") }
            val rawCategory = extractTagContent(itemBlock, "category")

            val cleanTitle = cleanHtml(rawTitle)
            val cleanDesc = cleanHtml(rawDesc)

            if (cleanTitle.isNotBlank() && cleanTitle.length > 6) {
                // Classify topic intelligently by content and category
                val classifiedTopic = classifyTopic(cleanTitle, cleanDesc, rawCategory, defaultTopic)

                // Extract image
                var imageUrl = extractImageUrl(itemBlock)
                if (imageUrl.isBlank()) {
                    val fallbacks = topicFallbackImages[classifiedTopic] ?: topicFallbackImages["Gündem"] ?: emptyList()
                    if (fallbacks.isNotEmpty()) {
                        imageUrl = fallbacks[counter % fallbacks.size]
                    }
                }

                val timeAgo = formatPubDate(rawPubDate)
                val readMin = maxOf(2, cleanDesc.length / 220)
                val id = UUID.nameUUIDFromBytes(cleanTitle.toByteArray()).toString()

                val paragraphs = if (cleanDesc.isNotBlank() && cleanDesc.contains(". ")) {
                    cleanDesc.split(". ")
                        .filter { it.isNotBlank() }
                        .map { it.trim().removeSuffix(".") + "." }
                } else {
                    listOf(cleanTitle, cleanDesc).filter { it.isNotBlank() }
                } else {
                    listOf(
                        cleanTitle,
                        if (cleanDesc.isNotBlank()) cleanDesc else "Haberin detayları ve resmi açıklamalar kurum bülteninde aktarılmaktadır.",
                        "Gelişmeler kamuoyu ve ilgili kurumlar tarafından yakından takip edilmektedir."
                    )
                }

                list.add(
                    Story(
                        id = id,
                        title = cleanTitle,
                        summary = cleanDesc.take(190) + if (cleanDesc.length > 190) "..." else "",
                        topic = classifiedTopic,
                        publisher = publisher,
                        publishedAt = timeAgo,
                        readTimeMin = readMin,
                        imageUrl = imageUrl,
                        isBreaking = cleanTitle.contains("son dakika", ignoreCase = true),
                        isManset = false,
                        whatHappened = cleanTitle,
                        whyItMatters = cleanDesc.take(140),
                        whatIsUnknown = "",
                        fullText = cleanDesc.ifBlank { cleanTitle },
                        paragraphs = paragraphs,
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
                counter++
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
        val pattern = Pattern.compile("<$tagName(?:\\s+[^>]*)?>(.*?)</$tagName>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(block)
        return if (matcher.find()) {
            cleanHtml(matcher.group(1) ?: "")
        } else ""
    }

    private fun extractLink(block: String): String {
        val hrefPattern = Pattern.compile("<link(?:\\s+[^>]*href=[\"']([^\"']+)[\"'][^>]*)?>", Pattern.CASE_INSENSITIVE)
        val hrefMatcher = hrefPattern.matcher(block)
        if (hrefMatcher.find()) {
            val href = hrefMatcher.group(1)
            if (!href.isNullOrBlank()) return href
        }

        val textPattern = Pattern.compile("<link>(.*?)</link>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
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
        return text.replace("<!\\[CDATA\\[".toRegex(), "")
            .replace("\\]\\]>".toRegex(), "")
            .replace("<[^>]*>".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    private fun formatPubDate(pubDate: String): String {
        if (pubDate.isBlank()) return "Az önce"

        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
        )

        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val date: Date? = sdf.parse(pubDate)
                if (date != null) {
                    val diffMs = System.currentTimeMillis() - date.time
                    val minutes = (diffMs / 60000).toInt()
                    val hours = (diffMs / 3600000).toInt()
                    val days = (diffMs / 86400000).toInt()

                    return when {
                        minutes in 0..10 -> "Az önce"
                        minutes in 11..59 -> "$minutes dk önce"
                        hours in 1..23 -> "$hours saat önce"
                        days == 1 -> "Dün"
                        days in 2..6 -> "$days gün önce"
                        days in 7..30 -> "${days / 7} hafta önce"
                        else -> {
                            val outFormat = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
                            outFormat.format(date)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return "Bugün"
    }


}
