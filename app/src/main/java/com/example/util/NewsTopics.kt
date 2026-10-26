package com.example.util

import java.net.URI

/** Publisher category and URL section take precedence over ambiguous words such as 'altın'. */
object NewsTopics {
    private val sections = mapOf(
        "spor" to "Spor", "sports" to "Spor", "sport" to "Spor", "futbol" to "Spor", "basketbol" to "Spor",
        "ekonomi" to "Ekonomi", "economy" to "Ekonomi", "business" to "Ekonomi", "finans" to "Ekonomi",
        "teknoloji" to "Teknoloji", "technology" to "Teknoloji", "bilim-teknoloji" to "Teknoloji",
        "saglik" to "Sağlık", "health" to "Sağlık", "dunya" to "Dünya", "world" to "Dünya",
        "siyaset" to "Siyaset", "politika" to "Siyaset", "politics" to "Siyaset",
        "kultur-sanat" to "Kültür-Sanat", "kultur" to "Kültür-Sanat", "culture" to "Kültür-Sanat",
    )
    fun fromUrl(url: String): String? = runCatching {
        URI(url).path.orEmpty().split('/').firstNotNullOfOrNull { sections[ArchiveSearch.normalize(it).replace(' ', '-')] }
    }.getOrNull()

    fun classify(title: String, description: String, category: String, defaultTopic: String, url: String): String {
        sections[ArchiveSearch.normalize(category).replace(' ', '-')]?.let { return it }
        fromUrl(url)?.let { return it }
        val text = ArchiveSearch.normalize("$title $description")
        fun has(pattern: String) = Regex("\\b(?:$pattern)\\b").containsMatchIn(text)
        return when {
            has("futbol|basketbol|voleybol|derbi|super lig|uefa|euroleague|euroleauge|ballon d or|altin top|milli takim|gol kralligi|mac kadrosu") -> "Spor"
            defaultTopic != "Gündem" -> defaultTopic
            has("tbmm|milletvekili|anayasa|ak parti|chp|mhp|secim|hukumet") -> "Siyaset"
            has("yapay zeka|yazilim|akilli telefon|siber|nasa|apple|google|microsoft|teknoloji|uydu") -> "Teknoloji"
            has("faiz|enflasyon|merkez bankasi|tcmb|dolar|euro|borsa|bist|altin fiyati|ihracat|ithalat|asgari ucret|butce|kredi|vergi") -> "Ekonomi"
            has("hastane|doktor|tedavi|kanser|saglik|asi|beslenme") -> "Sağlık"
            else -> defaultTopic
        }
    }
}
