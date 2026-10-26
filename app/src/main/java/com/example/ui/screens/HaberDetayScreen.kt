package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.CizgiRepository
import com.example.model.Story
import com.example.util.NewsSummary
import com.example.ui.components.ModernNewsCard
import com.example.ui.components.PublisherAvatar
import com.example.ui.theme.CizgiColor
import kotlinx.coroutines.launch
import java.util.Locale
import android.widget.Toast

@Composable
fun HaberDetayScreen(
    storyId: String,
    repository: CizgiRepository,
    onBack: () -> Unit,
    onCompareClick: (String) -> Unit,
    onStoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showFullArticle by remember(storyId) { mutableStateOf(false) }
    var showMoreActions by remember(storyId) { mutableStateOf(false) }
    var fullArticleStory by remember(storyId) { mutableStateOf<Story?>(null) }
    var isLoadingFullArticle by remember(storyId) { mutableStateOf(false) }
    val ttsReady = remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    val textToSpeech = remember(context) {
        TextToSpeech(context.applicationContext) { status -> ttsReady.value = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(textToSpeech) {
        onDispose {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }

    BackHandler(enabled = showFullArticle) { showFullArticle = false }
    BackHandler(enabled = !showFullArticle) { onBack() }

    val stories by repository.stories.collectAsState()
    val preferences by repository.preferences.collectAsState()
    var archivedStory by remember(storyId) { mutableStateOf<Story?>(null) }
    var isLoadingStory by remember(storyId) { mutableStateOf(true) }
    LaunchedEffect(storyId) {
        try {
            archivedStory = repository.loadStoryFromArchive(storyId)
        } catch (_: Exception) {
            archivedStory = null
        } finally {
            isLoadingStory = false
        }
    }
    val story = remember(stories, storyId, archivedStory) {
        stories.firstOrNull { it.id == storyId } ?: archivedStory
    }
    LaunchedEffect(story?.id) {
        story?.let { repository.refreshStoryDescription(it.id) }
    }
    if (story == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isLoadingStory) {
                CircularProgressIndicator()
                Text("Haber açılıyor", modifier = Modifier.padding(top = 12.dp))
            } else {
                Text("Bu haber artık arşivde bulunmuyor.", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("Akışa dön") }
            }
        }
        return
    }
    val summarySources = remember(story.id, story.whatHappened, story.whyItMatters, story.paragraphs, story.fullText, story.summary) {
        listOf(
            story.whatHappened,
            story.whyItMatters,
            story.paragraphs.joinToString(" "),
            story.fullText,
            story.summary,
        ).filter(String::isNotBlank)
    }
    val summarySourceText = remember(summarySources) { summarySources.joinToString("\n\n") }
    val detailSummary = remember(story.id, story.summary, story.fullText, story.paragraphs, story.whatHappened, story.whyItMatters) {
        NewsSummary.bestAvailableSummary(story.summary, summarySourceText, story.title)
            .ifBlank { NewsSummary.concise(summarySourceText, story.title) }
            .ifBlank {
                summarySources.firstOrNull { it.isNotBlank() && !it.trim().equals(story.title.trim(), ignoreCase = true) }
                    ?.let { candidate ->
                        NewsSummary.concise(candidate, story.title).ifBlank {
                            candidate.trim().let { text ->
                                if (text.length <= 280) text else text.take(279).substringBeforeLast(' ') + "…"
                            }
                        }
                    }.orEmpty()
            }
            .ifBlank { story.title }
    }
    val sourceUri = remember(story.canonicalUrl) { runCatching { Uri.parse(story.canonicalUrl) }.getOrNull() }
    val hasSourceUrl = (sourceUri?.scheme == "http" || sourceUri?.scheme == "https") && !sourceUri?.host.isNullOrBlank()

    val relatedStories = remember(stories, story) {
        val trLocale = Locale.forLanguageTag("tr-TR")
        val ignoredWords = setOf(
            "olan", "için", "ile", "daha", "sonra", "gibi", "üzere", "hakkında", "maç", "maçı",
            "canlı", "izle", "şifresiz", "lig", "takım", "saat", "bugün", "son", "dakika", "haber",
            "zaman", "hangi", "nerede", "nasıl", "gözler", "için",
        )
        val titleWords = Regex("[\\p{L}\\p{N}]{4,}")
            .findAll(story.title.lowercase(trLocale))
            .map { it.value }
            .filterNot { it in ignoredWords }
            .toSet()
        stories.asSequence()
            .filter { candidate ->
                candidate.id != story.id &&
                    (story.canonicalUrl.isBlank() || !candidate.canonicalUrl.equals(story.canonicalUrl, ignoreCase = true))
            }
            .map { candidate ->
                val candidateTitle = candidate.title.lowercase(trLocale)
                val sharedWords = titleWords.count { word -> candidateTitle.contains(word) }
                val sameTopic = candidate.topic.equals(story.topic, ignoreCase = true)
                candidate to (sharedWords * 3 + if (sameTopic) 2 else 0)
            }
            .filter { (_, relevance) -> relevance > 0 }
            .sortedWith(compareByDescending<Pair<Story, Int>> { it.second }.thenByDescending { it.first.publishedAtEpochMs })
            .distinctBy { (candidate, _) -> candidate.canonicalUrl.ifBlank { candidate.id }.lowercase(Locale.ROOT) }
            .take(6)
            .map { it.first }
            .toList()
    }
    val comparisonPublisherCount = remember(story) {
        (story.comparisons.map { it.publisher } + story.publisher)
            .map { it.trim().lowercase(java.util.Locale.ROOT) }
            .filter(String::isNotBlank)
            .distinct()
            .size
            .coerceAtLeast(1)
    }

    // Okuma ilerleme çubuğu hesaplaması
    val readingProgress by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems <= 1) 0f
            else {
                val firstVisible = listState.firstVisibleItemIndex.toFloat()
                (firstVisible / (totalItems - 1)).coerceIn(0f, 1f)
            }
        }
    }

    fun shareCurrentStory(currentStory: Story) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                listOfNotNull(
                    currentStory.title,
                    NewsSummary.bestAvailableSummary(currentStory.summary, currentStory.fullText, currentStory.title)
                        .takeIf(String::isNotBlank),
                    currentStory.canonicalUrl.takeIf { value ->
                        val uri = runCatching { Uri.parse(value) }.getOrNull()
                        (uri?.scheme == "http" || uri?.scheme == "https") && !uri?.host.isNullOrBlank()
                    },
                ).joinToString("\n\n")
            )
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Haberi Paylaş"))
    }

    fun requestFullArticle() {
        showFullArticle = true
        val alreadyAvailable = story.fullText.length >= 1_200 ||
            (story.id.startsWith("pha-") && story.fullText.length >= 400)
        if (alreadyAvailable) {
            fullArticleStory = story
            return
        }
        if (isLoadingFullArticle) return
        isLoadingFullArticle = true
        coroutineScope.launch {
            try {
                fullArticleStory = repository.loadFullArticle(story.id)
            } catch (_: Exception) {
                fullArticleStory = null
            } finally {
                isLoadingFullArticle = false
            }
        }
    }

    // Tam ekran kapsayıcı
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("haber_detay_screen"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 2. Tam Ekran Üst Gezinme ve Eylem Çubuğu
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(44.dp).testTag("detail_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = story.topic,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    maxLines = 1,
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { repository.toggleSaveStory(story) },
                                modifier = Modifier.size(40.dp).testTag("detail_save_button"),
                            ) {
                                Icon(
                                    imageVector = if (story.isSaved) Icons.Filled.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (story.isSaved) "Kaydedildi" else "Kaydet",
                                    tint = if (story.isSaved) CizgiColor.Gold else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = { shareCurrentStory(story) },
                                modifier = Modifier.size(40.dp).testTag("detail_share_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Haberi paylaş",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(19.dp),
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showMoreActions = true },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Diğer işlemler")
                                }
                                DropdownMenu(
                                    expanded = showMoreActions,
                                    onDismissRequest = { showMoreActions = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isSpeaking) "Dinlemeyi durdur" else "Özeti dinle") },
                                        leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                                        enabled = ttsReady.value,
                                        onClick = {
                                            showMoreActions = false
                                            if (isSpeaking) {
                                                textToSpeech.stop()
                                                isSpeaking = false
                                            } else if (detailSummary.isNotBlank()) {
                                                textToSpeech.language = Locale.forLanguageTag("tr-TR")
                                                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                                    private fun setSpeaking(value: Boolean) {
                                                        Handler(Looper.getMainLooper()).post { isSpeaking = value }
                                                    }
                                                    override fun onStart(utteranceId: String?) = setSpeaking(true)
                                                    override fun onDone(utteranceId: String?) = setSpeaking(false)
                                                    override fun onError(utteranceId: String?) = setSpeaking(false)
                                                })
                                                val result = textToSpeech.speak(detailSummary, TextToSpeech.QUEUE_FLUSH, null, "cizgi-summary")
                                                if (result == TextToSpeech.ERROR) isSpeaking = false
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (preferences.fontSize == "large") "Yazıyı küçült" else "Yazıyı büyüt") },
                                        leadingIcon = { Icon(Icons.Default.FormatSize, contentDescription = null) },
                                        onClick = {
                                            showMoreActions = false
                                            repository.setFontSize(if (preferences.fontSize == "large") "normal" else "large")
                                        },
                                    )
                                    if (hasSourceUrl) {
                                        DropdownMenuItem(
                                            text = { Text("Bağlantıyı kopyala") },
                                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                            modifier = Modifier.testTag("detail_copy_link_button"),
                                            onClick = {
                                                showMoreActions = false
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                clipboard?.setPrimaryClip(ClipData.newPlainText("Haber bağlantısı", story.canonicalUrl))
                                                Toast.makeText(context, "Haber bağlantısı kopyalandı", Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Okuma İlerleme Çubuğu (Reading progress indicator)
                    LinearProgressIndicator(
                        progress = { readingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp),
                        color = CizgiColor.Navy,
                        trackColor = CizgiColor.BorderLight.copy(alpha = 0.4f),
                    )
                }
            }

            // 3. Makale İçerik Akışı
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Başlık & Kategori & Okuma Süresi
                item(key = "header_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (story.isBreaking) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CizgiColor.Gold,
                                ) {
                                    Text(
                                        text = "⚡ SON DAKİKA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CizgiColor.Navy,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }

                            Text(
                                text = "${story.readTimeMin} dk okuma",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Büyük Manşet Başlık
                        Text(
                            text = story.title,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (preferences.fontSize == "large") 23.sp else 20.sp,
                                lineHeight = if (preferences.fontSize == "large") 31.sp else 27.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Yayıncı bilgisi
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PublisherAvatar(publisher = story.publisher, modifier = Modifier.size(28.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = story.publisher,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = "${story.publishedAt} · ${maxOf(story.sourceCount, comparisonPublisherCount)} kaynak",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Haber görseli
                if (story.imageUrl.isNotBlank()) {
                    item(key = "hero_image") {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2.2f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CizgiColor.SurfaceVariant),
                            ) {
                                AsyncImage(
                                    model = story.imageUrl,
                                    contentDescription = story.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                // Kısa özet
                item(key = "lead_summary") {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CizgiColor.Navy.copy(alpha = 0.25f))),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(48.dp)
                                    .background(CizgiColor.Navy, RoundedCornerShape(2.dp)),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "KISA ÖZET",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = detailSummary,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = if (preferences.fontSize == "large") 16.sp else 14.sp,
                                        lineHeight = if (preferences.fontSize == "large") 24.sp else 21.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // Kaynak Karşılaştırması ve Şeffaflık Bloğu
                if (comparisonPublisherCount > 1) {
                    item(key = "comparison_card") {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CompareArrows,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "DİĞER YAYINLAR",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                story.comparisons.take(2).forEach { comp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        PublisherAvatar(publisher = comp.publisher, modifier = Modifier.size(24.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = comp.publisher,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                text = comp.headline,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { onCompareClick(story.id) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(Icons.Default.CompareArrows, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Diğer yayınları gör")
                                    }
                                }
                            }
                        }
                    }
                }

                // Kaynağı ve uygulama içi okuma eylemi
                item(key = "source_button") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Kaynak",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = story.publisher,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                        OutlinedButton(
                            onClick = ::requestFullArticle,
                            enabled = hasSourceUrl,
                            modifier = Modifier.testTag("open_full_article_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("Devamını oku", maxLines = 1)
                        }
                    }
                }

                // İlgili Haberler Bölümü
                if (relatedStories.isNotEmpty()) {
                    item(key = "related_header") {
                        HorizontalDivider(color = CizgiColor.BorderLight)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "KONUYLA İLGİLİ DAHA FAZLA HABER",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    items(relatedStories, key = { it.id }) { related ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
                            modifier = Modifier.fillMaxWidth().clickable { onStoryClick(related.id) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (related.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = related.imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(width = 68.dp, height = 58.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = "${related.publisher} · ${related.publishedAt}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = related.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Alt Yapışkan Hızlı Çubuk (Geri dön & Başa dön)
        AnimatedVisibility(
            visible = readingProgress > 0.4f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = CizgiColor.Navy,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(48.dp)
                    .clickable {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Başa Dön",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }

    if (showFullArticle) {
        Dialog(
            onDismissRequest = { showFullArticle = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${story.publisher}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Haber", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { showFullArticle = false }) { Text("Kapat") }
                    }
                    HorizontalDivider()
                    when {
                        isLoadingFullArticle -> Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator()
                                Text("Haber yükleniyor", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        fullArticleStory != null -> {
                            val article = fullArticleStory!!
                            val articleParagraphs = remember(article.id, article.fullText, article.paragraphs) {
                                article.paragraphs.filter(String::isNotBlank).ifEmpty { NewsSummary.paragraphs(article.fullText) }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                item(key = "reader_heading") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = article.title,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontSize = if (preferences.fontSize == "large") 23.sp else 21.sp,
                                                lineHeight = if (preferences.fontSize == "large") 32.sp else 28.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "${article.publisher} · ${article.publishedAt}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (article.imageUrl.isNotBlank()) {
                                    item(key = "reader_image") {
                                        AsyncImage(
                                            model = article.imageUrl,
                                            contentDescription = article.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(1.8f).clip(RoundedCornerShape(12.dp)),
                                        )
                                    }
                                }
                                items(articleParagraphs) { paragraph ->
                                    Text(
                                        text = paragraph,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = if (preferences.fontSize == "large") 18.sp else 16.sp,
                                            lineHeight = if (preferences.fontSize == "large") 29.sp else 26.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                if (hasSourceUrl) {
                                    item(key = "reader_source_link") {
                                        TextButton(
                                            onClick = {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.canonicalUrl)))
                                            },
                                            modifier = Modifier.testTag("open_source_button"),
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Yayıncı sayfasını aç")
                                        }
                                    }
                                }
                            }
                        }
                        else -> Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Bu haberin tam metni uygulamada açılamadı.",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Yayıncı metni paylaşmıyor ya da erişimi sınırlıyor olabilir.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (hasSourceUrl) {
                                TextButton(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(story.canonicalUrl)))
                                    },
                                    modifier = Modifier.padding(top = 12.dp).testTag("open_source_button"),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Yayıncı sayfasını aç")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


