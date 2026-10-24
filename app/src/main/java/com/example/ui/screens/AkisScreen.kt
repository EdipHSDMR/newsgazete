package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CizgiRepository
import com.example.model.Story
import com.example.ui.components.CityDistrictPickerDialog
import com.example.ui.components.GununOzetiCard
import com.example.ui.components.KompaktHavaKarti
import com.example.ui.components.MansetHeroCard
import com.example.ui.components.MiniMatchCard
import com.example.ui.components.ModernNewsCard
import com.example.ui.components.NewsFeedCard
import com.example.ui.components.SkeletonCard
import com.example.ui.theme.CizgiColor

@Composable
fun AkisScreen(
    repository: CizgiRepository,
    onStoryClick: (String) -> Unit,
    onWeatherClick: () -> Unit,
    onSportsClick: () -> Unit,
    onMarketClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stories by repository.stories.collectAsState()
    val weather by repository.weather.collectAsState()
    val matches by repository.matches.collectAsState()
    val prefs by repository.preferences.collectAsState()
    val isRefreshing by repository.isRefreshing.collectAsState()
    val bistStocks by repository.bistStocks.collectAsState()
    val financialAssets by repository.financialAssets.collectAsState()

    // Filter by main topic: Siyaset, Ekonomi, Teknoloji, Gündem, etc.
    var selectedTopic by remember { mutableStateOf("Tümü") }
    // Optional hashtag sub-filter
    var selectedHashtag by remember { mutableStateOf<String?>(null) }
    // City & District Dialog state
    var showCityPicker by remember { mutableStateOf(false) }
    // Visual card mode vs compact list
    var isVisualCardMode by remember { mutableStateOf(true) }

    val categories = listOf(
        "Tümü" to "⚡",
        "Siyaset" to "🏛️",
        "Ekonomi" to "📈",
        "Teknoloji" to "💻",
        "Gündem" to "🔥",
        "Dünya" to "🌍",
        "Spor" to "⚽",
        "Kültür-Sanat" to "🎭",
        "Sağlık" to "🩺",
    )

    val hashtags = listOf(
        "#TBMM",
        "#MerkezBankası",
        "#YapayZeka",
        "#SüperLig",
        "#Enflasyon",
        "#BIST100",
        "#AsgariÜcret",
        "#Kabine",
        "#Olimpiyat",
    )

    // Filter stories dynamically
    val filteredStories = remember(stories, selectedTopic, selectedHashtag) {
        var result = if (selectedTopic == "Tümü") {
            stories
        } else {
            stories.filter { it.topic.equals(selectedTopic, ignoreCase = true) }
        }

        if (!selectedHashtag.isNullOrBlank()) {
            val cleanTag = selectedHashtag!!.removePrefix("#").lowercase()
            result = result.filter {
                it.title.lowercase().contains(cleanTag) ||
                        it.summary.lowercase().contains(cleanTag) ||
                        it.whatHappened.lowercase().contains(cleanTag)
            }
        }
        result
    }

    val mansetStory = remember(filteredStories) {
        filteredStories.firstOrNull { it.isManset } ?: filteredStories.firstOrNull()
    }

    val feedStories = remember(filteredStories, mansetStory) {
        if (mansetStory != null) filteredStories.filter { it.id != mansetStory.id } else filteredStories
    }

    val dailyDigestPoints = remember(stories) {
        if (stories.isNotEmpty()) {
            stories.take(5).map { it.title }
        } else {
            listOf(
                "TCMB Para Politikası Kurulu politika faizini yüzde 50'de sabit tuttu.",
                "TÜBİTAK öncülüğünde Türkiye'nin ilk açık kaynak yapay zeka çip mimarisi tanıtıldı.",
                "Trendyol Süper Lig'de lider Galatasaray ve Fenerbahçe haftayı galibiyetle kapattı.",
                "Birleşmiş Milletler İklim Zirvesi'nde yenilenebilir enerji mutabakatı imzalandı.",
                "Milli okçumuz Mete Gazoz Dünya Kupası finallerinde altın madalyanın sahibi oldu.",
            )
        }
    }

    val nextMatch = remember(matches) {
        matches.firstOrNull { it.status == com.example.model.MatchStatus.LIVE }
            ?: matches.firstOrNull { it.status == com.example.model.MatchStatus.UPCOMING }
            ?: matches.firstOrNull()
    }

    fun shareStory(story: Story) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                "${story.title}\n\n${story.summary}\n\nÇizgi üzerinden okundu: ${story.canonicalUrl.ifBlank { "https://cizgi.app" }}"
            )
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Haberi Paylaş"))
    }

    // City & District Dialog
    if (showCityPicker) {
        CityDistrictPickerDialog(
            currentCity = prefs.selectedCity,
            currentDistrict = prefs.selectedDistrict,
            onDismiss = { showCityPicker = false },
            onSelect = { city, district ->
                repository.selectCityAndDistrict(city, district)
                showCityPicker = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("akis_screen"),
    ) {
        // 1. Haber Akışının En Üstünde: Yatayda Kaydırılabilir Kategori Çip Grubu
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Topic chips row with counters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    categories.forEach { (catName, emoji) ->
                        val isSelected = selectedTopic == catName
                        val count = if (catName == "Tümü") stories.size else stories.count { it.topic.equals(catName, ignoreCase = true) }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTopic = catName
                                selectedHashtag = null
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(emoji, fontSize = 13.sp)
                                    Text(
                                        text = catName,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                        ),
                                    )
                                    if (count > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer,
                                        ) {
                                            Text(
                                                text = "$count",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                labelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("category_chip_$catName"),
                        )
                    }
                }

                // Trending Hashtag Quick Filter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Etiketler:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 2.dp)
                    )

                    hashtags.forEach { tag ->
                        val isTagSelected = selectedHashtag == tag
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isTagSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedHashtag = if (isTagSelected) null else tag
                                }
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.5.sp,
                                fontWeight = if (isTagSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTagSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (selectedHashtag != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { selectedHashtag = null }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Filtreyi Temizle",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Canlı Son Dakika Ticker Bandı
        val breakingStory = remember(stories) { stories.firstOrNull { it.isBreaking } }
        if (breakingStory != null) {
            Surface(
                color = Color(0xFFB71C1C),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStoryClick(breakingStory.id) }
                    .testTag("breaking_news_ticker"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CizgiColor.Gold,
                    ) {
                        Text(
                            text = "⚡ SON DAKİKA",
                            color = CizgiColor.Navy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = breakingStory.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Oku ›",
                        color = CizgiColor.Gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                    )
                }
            }
        }

        // 2. Haber Akışı İçerik Listesi
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Loading Skeletons
            if (isRefreshing) {
                items(3) { SkeletonCard() }
            } else {
                // Canlı BIST 100 ve Emtia Takip Listesi Widget'ı (Retrofit Servisli)
                item(key = "bist_watchlist_strip") {
                    com.example.ui.components.BistWatchlistCard(
                        stocks = bistStocks,
                        financialAssets = financialAssets,
                        onViewMarketsClick = onMarketClick,
                    )
                }

                // Haber akışının üst kısmında kullanıcının konumuna göre canlı hava durumu kartı
                item(key = "top_compact_weather") {
                    val displayName = if (!prefs.selectedDistrict.isNullOrBlank()) {
                        "${prefs.selectedDistrict}, ${prefs.selectedCity}"
                    } else {
                        prefs.selectedCity
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onWeatherClick)
                            .testTag("kompakt_hava_karti"),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Top Row: Location with Pin + District Picker Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable { showCityPicker = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = "Şehir ve İlçe Değiştir",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = "▾",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2E7D32)),
                                    )
                                    Text(
                                        text = "CANLI HAVA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                        ),
                                        color = Color(0xFF2E7D32),
                                    )
                                }
                            }

                            // Temperature and Summary Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    val emoji = when {
                                        weather.condition.contains("Güneş", ignoreCase = true) -> "☀️"
                                        weather.condition.contains("Yağmur", ignoreCase = true) -> "🌧️"
                                        weather.condition.contains("Kar", ignoreCase = true) -> "❄️"
                                        weather.condition.contains("Bulut", ignoreCase = true) -> "⛅"
                                        else -> "🌤️"
                                    }
                                    Text(emoji, fontSize = 32.sp)

                                    Column {
                                        Text(
                                            text = "${weather.currentTempC}°",
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = weather.condition,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Hissedilen: ${weather.feelsLikeC}°",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "Nem: %${weather.humidityPct} · Rüzgar: ${weather.windKmh} km/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "Haftalık Tahmin ›",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Yalnızca "Tümü" veya "Gündem" sekmesindeyken Günün Özeti ve Canlı Spor Maçı Hızlı Bakışı
                if (selectedTopic == "Tümü" || selectedTopic == "Gündem") {
                    item(key = "daily_digest") {
                        GununOzetiCard(points = dailyDigestPoints)
                    }

                    if (nextMatch != null) {
                        item(key = "live_match_glance") {
                            MiniMatchCard(
                                match = nextMatch,
                                onClick = onSportsClick,
                            )
                        }
                    }
                }

                // Section Başlığı ve Görünüm Değiştirici
                item(key = "feed_section_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = if (selectedTopic == "Tümü") "SON VE ÖNE ÇIKAN HABERLER" else "${selectedTopic.uppercase()} HABERLERİ",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${filteredStories.size} haber listeleniyor" + if (!selectedHashtag.isNullOrBlank()) " ($selectedHashtag)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = { isVisualCardMode = !isVisualCardMode },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (isVisualCardMode) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                    contentDescription = if (isVisualCardMode) "Kompakt Liste Görünümü" else "Görsel Kart Görünümü",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = { repository.refreshAll() },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Yenile",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                // Empty state if no stories in selected filter
                if (filteredStories.isEmpty()) {
                    item(key = "empty_filter_state") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("📰", fontSize = 36.sp)
                                Text(
                                    text = "$selectedTopic kategorisinde henüz haber bulunamadı",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                TextButton(
                                    onClick = {
                                        selectedTopic = "Tümü"
                                        selectedHashtag = null
                                    }
                                ) {
                                    Text("Tüm Haberleri Göster")
                                }
                            }
                        }
                    }
                }

                // Manşet Kartı (ilk haber)
                if (mansetStory != null) {
                    item(key = "manset_${mansetStory.id}") {
                        MansetHeroCard(
                            story = mansetStory,
                            onClick = { onStoryClick(mansetStory.id) },
                            onSaveToggle = { repository.toggleSaveStory(mansetStory) },
                        )
                    }
                }

                // Diğer Haber Kartları
                items(feedStories, key = { it.id }) { story ->
                    if (isVisualCardMode) {
                        ModernNewsCard(
                            story = story,
                            onClick = { onStoryClick(story.id) },
                            onSaveToggle = { repository.toggleSaveStory(story) },
                            onShareClick = { shareStory(story) },
                        )
                    } else {
                        NewsFeedCard(
                            story = story,
                            onClick = { onStoryClick(story.id) },
                            onSaveToggle = { repository.toggleSaveStory(story) },
                        )
                    }
                }

                // Kesintisiz Canlı Akış & Arşiv Bilgilendirme Rozeti
                item(key = "continuous_stream_info") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF16A34A)),
                                )
                                Text(
                                    text = "CANLI HABER AKIŞI AKTİF",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                                    color = Color(0xFF16A34A),
                                )
                            }
                            Text(
                                text = "Uygulama açık olduğu sürece haberler arkaplanda sürekli taranıp akışa eklenir. Geçmiş ve eski haberlerin tümüne 'Keşfet' sekmesinden arama yaparak ulaşabilirsiniz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
