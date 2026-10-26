package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.SourceImage
import com.example.data.CizgiRepository
import com.example.model.Story
import com.example.ui.components.NewsFeedCard
import com.example.ui.components.PublisherAvatar
import com.example.util.NewsSummary
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HaberDetayScreen(
    storyId: String, repository: CizgiRepository, onBack: () -> Unit,
    onCompareClick: (String) -> Unit, onStoryClick: (String) -> Unit, modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val stories by repository.stories.collectAsState()
    val preferences by repository.preferences.collectAsState()
    val archiveCount by repository.newsArchiveCount.collectAsState()
    val state = rememberLazyListState()
    var archived by remember(storyId) { mutableStateOf<Story?>(null) }
    var article by remember(storyId) { mutableStateOf<Story?>(null) }
    var fetching by remember(storyId) { mutableStateOf(true) }
    var initialLoad by remember(storyId) { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var recommendationLimit by rememberSaveable(storyId) { mutableIntStateOf(12) }
    var requestedSize by remember(storyId) { mutableIntStateOf(-1) }
    var ttsReady by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    val tts = remember(context) { TextToSpeech(context.applicationContext) { ttsReady = it == TextToSpeech.SUCCESS } }
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { speaking = true }
            override fun onDone(id: String?) { speaking = false }
            override fun onError(id: String?) { speaking = false }
        })
        onDispose { tts.stop(); tts.shutdown() }
    }
    BackHandler { onBack() }
    LaunchedEffect(storyId) {
        tts.stop()
        speaking = false
        state.scrollToItem(0)
        archived = repository.loadStoryFromArchive(storyId)
        initialLoad = false
    }
    val story = stories.firstOrNull { it.id == storyId } ?: archived
    LaunchedEffect(story?.id) {
        val current = story ?: return@LaunchedEffect
        fetching = true
        try { article = repository.loadFullArticle(current.id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { article = null }
        finally { fetching = false }
    }
    if (story == null) {
        Box(modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
            if (initialLoad) CircularProgressIndicator()
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Haber bulunamadı", style = MaterialTheme.typography.titleMedium)
                TextButton(onBack) { Text("Akışa dön") }
            }
        }
        return
    }
    val displayed = article?.takeIf { it.fullText.length >= story.fullText.length } ?: story
    val body = displayed.fullText.ifBlank { displayed.paragraphs.joinToString("\n\n") }
    val summary = remember(displayed.id, body, displayed.summary) {
        NewsSummary.bestAvailableSummary(displayed.summary, body, displayed.title, 420)
    }
    val paragraphs = remember(body, summary) {
        NewsSummary.paragraphs(NewsSummary.remainingSourceText(body, summary))
    }
    val related = remember(stories, story.id) {
        val words = Regex("[\\p{L}\\p{N}]{4,}").findAll(story.title.lowercase(Locale.forLanguageTag("tr-TR")))
            .map { it.value }.filterNot { it in setOf("sonra", "için", "haber", "dakika", "canlı", "zaman", "hangi", "nedir") }.toSet()
        stories.asSequence().filter { it.id != story.id && it.canonicalUrl != story.canonicalUrl }
            .map { candidate -> candidate to words.count { candidate.title.contains(it, true) } }
            .filter { it.second >= 2 }.sortedByDescending { it.second }.take(4).map { it.first }.toList()
    }
    val recommendations = remember(stories, story.id, related) {
        val excluded = related.mapTo(mutableSetOf()) { it.id }.apply { add(story.id) }
        stories.filterNot { it.id in excluded || (story.canonicalUrl.isNotBlank() && it.canonicalUrl == story.canonicalUrl) }
            .distinctBy { it.canonicalUrl.ifBlank { it.id } }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.key.toString().removePrefix("more_").removePrefix("related_") } }
            .collect { repository.prepareReadingContent(it) }
    }
    LaunchedEffect(state, recommendations.size, recommendationLimit) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { index ->
            if (index < state.layoutInfo.totalItemsCount - 5) return@collect
            if (recommendationLimit < recommendations.size) recommendationLimit += 16
            else if (requestedSize != stories.size && (stories.size < archiveCount || repository.hasMorePressArchivePages)) {
                requestedSize = stories.size
                repository.loadOlderNews()
            }
        }
    }
    val publicationTime = remember(story.publishedAtEpochMs, story.publishedAt) {
        if (story.publishedAtEpochMs > 0L) Instant.ofEpochMilli(story.publishedAtEpochMs).atZone(ZoneId.of("Europe/Istanbul"))
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("tr-TR"))) else story.publishedAt
    }
    val progress by remember(paragraphs.size) {
        derivedStateOf { (state.firstVisibleItemIndex.toFloat() / maxOf(1, paragraphs.size + 3)).coerceIn(0f, 1f) }
    }
    fun share() {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, listOf(story.title, story.canonicalUrl).filter(String::isNotBlank).joinToString("\n\n")), "Haberi paylaş"))
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("haber_detay_screen")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack, Modifier.size(40.dp).testTag("detail_back_button")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Geri") }
                Text(story.topic, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (summary.isNotBlank()) IconButton({
                    if (speaking) { tts.stop(); speaking = false } else if (ttsReady) {
                        val language = tts.setLanguage(Locale.forLanguageTag("tr-TR"))
                        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Toast.makeText(context, "Bu cihazda Türkçe ses verisi bulunmuyor", Toast.LENGTH_SHORT).show()
                        } else if (tts.speak(summary, TextToSpeech.QUEUE_FLUSH, null, story.id) == TextToSpeech.ERROR) {
                            Toast.makeText(context, "Sesli okuma başlatılamadı", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, enabled = ttsReady, modifier = Modifier.size(36.dp)) {
                    Icon(if (speaking) Icons.Outlined.StopCircle else Icons.Outlined.Headphones, if (speaking) "Dinlemeyi durdur" else "Özeti dinle", modifier = Modifier.size(20.dp))
                }
                IconButton({ repository.toggleSaveStory(story) }, Modifier.size(36.dp).testTag("detail_save_button")) {
                    Icon(if (story.isSaved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder, "Kaydet", modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton({ share() }, Modifier.size(36.dp).testTag("detail_share_button")) { Icon(Icons.Outlined.Share, "Paylaş", modifier = Modifier.size(19.dp)) }
                Box {
                    IconButton({ showMenu = true }, Modifier.size(32.dp)) { Icon(Icons.Outlined.MoreHoriz, "Diğer işlemler") }
                    DropdownMenu(showMenu, { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Bağlantıyı kopyala") }, enabled = story.canonicalUrl.isNotBlank(), leadingIcon = { Icon(Icons.Outlined.Link, null) }, onClick = {
                            showMenu = false
                            if (story.canonicalUrl.isNotBlank()) {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Haber bağlantısı", story.canonicalUrl))
                                Toast.makeText(context, "Bağlantı kopyalandı", Toast.LENGTH_SHORT).show()
                            }
                        })
                        DropdownMenuItem(text = { Text(if (preferences.fontSize == "large") "Standart yazı boyutu" else "Büyük yazı boyutu") }, leadingIcon = { Icon(Icons.Outlined.TextFields, null) }, onClick = {
                            repository.setFontSize(if (preferences.fontSize == "large") "normal" else "large")
                            showMenu = false
                        })
                    }
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
            LazyColumn(
                state = state, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (displayed.imageUrl.isNotBlank()) item(key = "image") {
                    SourceImage(displayed.imageUrl, story.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                }
                item(key = "headline") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(story.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PublisherAvatar(story.publisher, Modifier.size(27.dp))
                            Text(story.publisher, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text("· " + publicationTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (summary.isNotBlank()) item(key = "summary") {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(21.dp), modifier = Modifier.fillMaxWidth().animateContentSize()) {
                        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Kısaca", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                items(paragraphs.size, key = { "paragraph_" + it }) { index ->
                    Text(paragraphs[index], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
                if (fetching && paragraphs.isEmpty()) item(key = "loading_text") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) 0.65f else 1f).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) }
                    }
                }
                item(key = "source") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Kaynak: " + story.publisher + " · " + publicationTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (story.comparisons.map { it.publisher }.distinct().size > 1) TextButton({ onCompareClick(story.id) }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.LibraryBooks, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Diğer kaynakların bakışı", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (related.isNotEmpty()) {
                    item(key = "related_title") { Text("Bu konunun devamı", style = MaterialTheme.typography.titleLarge) }
                    items(related, key = { "related_" + it.id }) { next ->
                        NewsFeedCard(next, { onStoryClick(next.id) }, { repository.toggleSaveStory(next) })
                    }
                }
                if (recommendations.isNotEmpty()) {
                    item(key = "more_title") { Text("Okumaya devam et", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp)) }
                    items(recommendations.take(recommendationLimit), key = { "more_" + it.id }) { next ->
                        NewsFeedCard(next, { onStoryClick(next.id) }, { repository.toggleSaveStory(next) })
                    }
                }
                if (recommendationLimit < recommendations.size || stories.size < archiveCount || repository.hasMorePressArchivePages) item(key = "next_page") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
            }
        }
    }
}
