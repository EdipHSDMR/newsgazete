package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CizgiRepository
import com.example.model.MatchStatus
import com.example.model.Story
import com.example.ui.components.*
import com.example.ui.theme.CizgiColor
import com.example.util.BreakingNewsClassifier
import com.example.util.PersonalizedNews
import kotlinx.coroutines.delay

@Composable
fun AkisScreen(
    repository: CizgiRepository, onStoryClick: (String) -> Unit, onSportsClick: () -> Unit,
    onMarketClick: () -> Unit = {}, modifier: Modifier = Modifier,
    onTopicsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val stories by repository.stories.collectAsState()
    val preferences by repository.preferences.collectAsState()
    val count by repository.newsArchiveCount.collectAsState()
    val matches by repository.matches.collectAsState()
    val refreshing by repository.isRefreshing.collectAsState()
    val digest by repository.dailyDigest.collectAsState()
    val stocks by repository.bistStocks.collectAsState()
    val assets by repository.financialAssets.collectAsState()
    val state = rememberLazyListState()
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.key.toString().removePrefix("hero_") } }
            .collect { repository.prepareReadingContent(it) }
    }
    var visual by rememberSaveable { mutableStateOf(false) }
    var personalized by rememberSaveable { mutableStateOf(preferences.followedTopics.isNotEmpty() || preferences.followedTeams.isNotEmpty()) }
    var visibleLimit by rememberSaveable { mutableIntStateOf(50) }
    var requestedSize by remember { mutableIntStateOf(-1) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); clock = System.currentTimeMillis() } }
    val breaking = remember(stories, clock) { BreakingNewsClassifier.latest(stories, clock) }
    val selectedStories = remember(stories, personalized, preferences.followedTopics, preferences.followedTeams) {
        if (personalized) PersonalizedNews.rank(stories, preferences) else stories
    }
    val hero = selectedStories.firstOrNull()
    val feed = remember(selectedStories, visibleLimit) { selectedStories.drop(1).take(visibleLimit) }
    val match = matches.firstOrNull { it.status == MatchStatus.LIVE }
        ?: matches.firstOrNull { it.status == MatchStatus.UPCOMING }

    LaunchedEffect(state, stories.size, selectedStories.size, personalized, visibleLimit, count) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (stories.isEmpty() || (personalized && selectedStories.isEmpty())) return@collect
            if (last < state.layoutInfo.totalItemsCount - 5) return@collect
            if (visibleLimit < selectedStories.size - 1) visibleLimit += 40
            else if (requestedSize != stories.size && (stories.size < count || repository.hasMorePressArchivePages)) {
                requestedSize = stories.size
                repository.loadOlderNews()
            }
        }
    }
    fun share(story: Story) {
        val text = listOf(story.title, story.canonicalUrl).filter(String::isNotBlank).joinToString("\n\n")
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text).setType("text/plain"), "Haberi paylaş"))
    }
    LazyColumn(
        state = state, modifier = modifier.fillMaxSize().testTag("akis_screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "heading") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (personalized) "Senin için" else "Gündem", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton({ personalized = !personalized }, Modifier.size(34.dp)) {
                    Icon(if (personalized) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Takip ettiğim konular", tint = if (personalized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton({ visual = !visual }, Modifier.size(34.dp)) {
                    Icon(if (visual) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.Dashboard, "Haber görünümünü değiştir", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                IconButton({ repository.refreshAll() }, Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Refresh, "Haberleri yenile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
            }
        }
        if (refreshing && stories.isNotEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant) }
        if (breaking != null) item(key = "breaking") {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(17.dp),
                modifier = Modifier.fillMaxWidth().clickable { onStoryClick(breaking.id) }.testTag("breaking_news_ticker"),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(CizgiColor.Red))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Son dakika", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(breaking.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        if (hero != null) item(key = "hero_" + hero.id) {
            MansetHeroCard(hero, { onStoryClick(hero.id) }, { repository.toggleSaveStory(hero) })
        }
        if (digest.isNotEmpty()) item(key = "digest") { GununOzetiCard(digest) }
        if (stories.isEmpty() && refreshing) items(3) { SkeletonCard() }
        items(feed.take(3), key = { it.id }) { story ->
            if (visual) ModernNewsCard(story, { onStoryClick(story.id) }, { repository.toggleSaveStory(story) }, { share(story) })
            else NewsFeedCard(story, { onStoryClick(story.id) }, { repository.toggleSaveStory(story) })
        }
        if (stocks.isNotEmpty() || assets.isNotEmpty()) item(key = "market") { BistWatchlistCard(stocks, assets, onMarketClick) }
        if (match != null) item(key = "match") { MiniMatchCard(match, onSportsClick) }
        itemsIndexed(feed.drop(3), key = { _, story -> story.id }) { index, story ->
            if (visual || index % 6 == 2) ModernNewsCard(story, { onStoryClick(story.id) }, { repository.toggleSaveStory(story) }, { share(story) })
            else NewsFeedCard(story, { onStoryClick(story.id) }, { repository.toggleSaveStory(story) })
        }
        if (selectedStories.isEmpty() && !refreshing) item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (personalized && preferences.followedTopics.isEmpty()) "İlgi alanlarını seç" else if (personalized) "Takip ettiğin konularda haber bulunamadı" else "Haberler alınamadı", style = MaterialTheme.typography.titleMedium)
                TextButton({ if (personalized && preferences.followedTopics.isEmpty()) onTopicsClick() else if (personalized) personalized = false else repository.refreshAll() }) { Text(if (personalized && preferences.followedTopics.isEmpty()) "Konuları keşfet" else if (personalized) "Tüm haberlere dön" else "Yeniden dene") }
            }
        }
        if (selectedStories.isNotEmpty() && (feed.size < selectedStories.size - 1 || stories.size < count || repository.hasMorePressArchivePages)) item(key = "next_page") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}
