package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.components.GununOzetiCard
import com.example.ui.components.MansetCompactCard
import com.example.ui.components.MiniMatchCard
import com.example.ui.components.ModernNewsCard
import com.example.ui.components.NewsFeedCard
import com.example.ui.components.SkeletonCard
import com.example.ui.theme.CizgiColor
import com.example.util.BreakingNewsClassifier
import kotlinx.coroutines.delay

@Composable
fun AkisScreen(
    repository: CizgiRepository,
    onStoryClick: (String) -> Unit,
    onSportsClick: () -> Unit,
    onMarketClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stories by repository.stories.collectAsState()
    val newsArchiveCount by repository.newsArchiveCount.collectAsState()
    val matches by repository.matches.collectAsState()
    val isRefreshing by repository.isRefreshing.collectAsState()
    val dailyDigestPoints by repository.dailyDigest.collectAsState()
    val bistStocks by repository.bistStocks.collectAsState()
    val financialAssets by repository.financialAssets.collectAsState()
    var tickerClock by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            tickerClock = System.currentTimeMillis()
        }
    }

    // Visual card mode vs compact list
    var isVisualCardMode by remember { mutableStateOf(false) }
    val filteredStories = stories

    val mansetStory = remember(filteredStories) {
        filteredStories.firstOrNull { it.isManset } ?: filteredStories.firstOrNull()
    }

    val feedStories = remember(filteredStories, mansetStory) {
        if (mansetStory != null) filteredStories.filter { it.id != mansetStory.id } else filteredStories
    }

    val nextMatch = remember(matches) {
        matches.firstOrNull { it.status == com.example.model.MatchStatus.LIVE }
            ?: matches.firstOrNull { it.status == com.example.model.MatchStatus.UPCOMING }
            ?: matches.firstOrNull()
    }

    fun shareStory(story: Story) {
        val sourceUrl = story.canonicalUrl.takeIf { it.startsWith("https://", ignoreCase = true) }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, listOfNotNull(story.title, story.summary, sourceUrl).joinToString("\n\n"))
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Haberi Paylaş"))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("akis_screen"),
    ) {
        // Canlı Son Dakika Ticker Bandı
        val breakingStory = remember(stories, tickerClock) { BreakingNewsClassifier.latest(stories, tickerClock) }
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
            if (isRefreshing && filteredStories.isEmpty()) {
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

                if (dailyDigestPoints.isNotEmpty()) {
                    item(key = "daily_digest") {
                        GununOzetiCard(points = dailyDigestPoints)
                    }
                }

                if (nextMatch != null) {
                    item(key = "live_match_glance") {
                        MiniMatchCard(
                            match = nextMatch,
                            onClick = onSportsClick,
                        )
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
                                text = "SON VE ÖNE ÇIKAN HABERLER",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "${filteredStories.size} haber listeleniyor",
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
                                    text = "Henüz haber bulunamadı",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                TextButton(
                                    onClick = { repository.refreshAll() }
                                ) {
                                    Text("Yeniden Dene")
                                }
                            }
                        }
                    }
                }

                // Manşet Kartı (ilk haber)
                if (mansetStory != null) {
                    item(key = "manset_${mansetStory.id}") {
                        MansetCompactCard(
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

                if (stories.size < newsArchiveCount || repository.hasMorePressArchivePages) {
                    item(key = "load_older_news") {
                        TextButton(
                            onClick = { repository.loadOlderNews() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (stories.size < newsArchiveCount) {
                                    "Daha eski haberleri yükle · ${stories.size}/$newsArchiveCount"
                                } else {
                                    "Daha eski haberleri yükle"
                                }
                            )
                        }
                    }
                }

            }
        }
    }
}

