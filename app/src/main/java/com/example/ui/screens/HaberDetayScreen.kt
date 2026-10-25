package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.example.data.CizgiRepository
import com.example.model.Story
import com.example.util.NewsSummary
import com.example.ui.components.ModernNewsCard
import com.example.ui.components.PublisherAvatar
import com.example.ui.theme.CizgiColor
import kotlinx.coroutines.launch

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

    // 1. Android Sistem Geri Hareketi Desteği (BackHandler)
    BackHandler(enabled = true) {
        onBack()
    }

    val stories by repository.stories.collectAsState()
    val story = remember(stories, storyId) { stories.firstOrNull { it.id == storyId } }
    if (story == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Haber bulunamadı.", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("Akışa dön") }
        }
        return
    }
    LaunchedEffect(story.id) { repository.refreshStoryDescription(story.id) }
    val detailSummary = remember(story.id, story.summary, story.fullText) {
        NewsSummary.concise(story.summary.ifBlank { story.fullText }, story.title)
    }

    val relatedStories = remember(stories, story) {
        stories.filter { it.id != story.id && (it.topic == story.topic || it.isManset) }.take(3)
    }
    val comparisonPublisherCount = remember(story) {
        (story.comparisons.map { it.publisher } + story.publisher)
            .map { it.trim().lowercase(java.util.Locale.ROOT) }
            .filter(String::isNotBlank)
            .distinct()
            .size
            .coerceAtLeast(1)
    }

    // Okuma fontu boyutu değiştirici (Standart vs Büyük font)
    var isLargeFont by remember { mutableStateOf(false) }

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
                "${currentStory.title}\n\n${currentStory.summary}\n\nÇizgi üzerinden okundu: ${currentStory.canonicalUrl.ifBlank { "https://cizgi.app" }}"
            )
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Haberi Paylaş"))
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
                        // Geri Dön Butonu
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("detail_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri Dön",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        // Kategori Başlığı Rozeti
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = story.topic.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }

                        // Üst Sağ Eylemler: Font Değiştir, Paylaş, Kaydet
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            IconButton(
                                onClick = { isLargeFont = !isLargeFont },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FormatSize,
                                    contentDescription = if (isLargeFont) "Standart Yazı Boyutu" else "Büyük Yazı Boyutu",
                                    tint = if (isLargeFont) CizgiColor.Blue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = { shareCurrentStory(story) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("detail_share_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Haberi Paylaş",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            IconButton(
                                onClick = { repository.toggleSaveStory(story) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("detail_save_button"),
                            ) {
                                Icon(
                                    imageVector = if (story.isSaved) Icons.Filled.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (story.isSaved) "Kaydedildi" else "Kaydet",
                                    tint = if (story.isSaved) CizgiColor.Gold else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp),
                                )
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Başlık & Kategori & Okuma Süresi
                item(key = "header_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                text = "⏱️ ${story.readTimeMin} dakika okuma süresi",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Büyük Manşet Başlık
                        Text(
                            text = story.title,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isLargeFont) 26.sp else 23.sp,
                                lineHeight = if (isLargeFont) 36.sp else 32.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Yayıncı ve Doğrulama Satırı
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PublisherAvatar(publisher = story.publisher, modifier = Modifier.size(34.dp))

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
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Doğrulanmış Kaynak",
                                        tint = CizgiColor.Blue,
                                        modifier = Modifier.size(14.dp),
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

                // 16:9 Büyük Görsel
                if (story.imageUrl.isNotBlank()) {
                    item(key = "hero_image") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CizgiColor.SurfaceVariant),
                            ) {
                                AsyncImage(
                                    model = story.imageUrl,
                                    contentDescription = story.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                text = "Kaynak akışında paylaşılan görsel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }

                // Editöryal Özet Kartı
                item(key = "lead_summary") {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CizgiColor.Navy.copy(alpha = 0.25f))),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
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
                                    text = "KISA ÖZET · KAYNAK METNİNDEN",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = detailSummary.ifBlank { "Bu haber için henüz yeterli kaynak açıklaması yok. Kaynak bağlantısını açarak ayrıntıyı okuyabilirsin." },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = if (isLargeFont) 16.sp else 14.5.sp,
                                        lineHeight = if (isLargeFont) 24.sp else 22.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                // Show additional source text only when it adds details beyond the short summary.
                item(key = "article_paragraphs") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val sourceText = story.fullText.trim()
                        val additionalSourceText = NewsSummary.remainingSourceText(sourceText, detailSummary)
                            .takeIf { it.length >= 80 }.orEmpty()

                        if (additionalSourceText.isNotBlank()) {
                            Text(
                                text = "HABERİN KAYNAK METNİ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val paragraphs = NewsSummary.paragraphs(additionalSourceText).ifEmpty { listOf(additionalSourceText) }
                            paragraphs.forEach { paragraph ->
                                Text(
                                    text = paragraph,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = if (isLargeFont) 18.sp else 16.sp,
                                        lineHeight = if (isLargeFont) 28.sp else 25.sp,
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
                                        text = "FARKLI KAYNAKLAR NASIL GÖRDÜ?",
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
                                        Text("Tüm Kaynak Karşılaştırmalarını İncele ($comparisonPublisherCount Yayıncı)")
                                    }
                                }
                            }
                        }
                    }
                }

                // Özgün Kaynak Butonu (Telif güvencesi & Kaynağında Oku)
                item(key = "source_button") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CizgiColor.BorderLight)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "ŞEFFAFLIK VE KAYNAK BİLGİSİ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Bu içerik ${story.publisher} tarafından yayınlanmış olup tüm telif hakları yayıncı kuruluşa aittir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    val url = story.canonicalUrl.ifBlank { "https://google.com/search?q=${Uri.encode(story.title)}" }
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(browserIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CizgiColor.Navy),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_source_button"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Haberin Kaynağında Oku (${story.publisher})",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // İlgili Haberler Bölümü
                if (relatedStories.isNotEmpty()) {
                    item(key = "related_header") {
                        HorizontalDivider(color = CizgiColor.BorderLight)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "İLGİLİ HABERLER",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    items(relatedStories, key = { it.id }) { related ->
                        ModernNewsCard(
                            story = related,
                            onClick = { onStoryClick(related.id) },
                            onSaveToggle = { repository.toggleSaveStory(related) },
                            onShareClick = { shareCurrentStory(related) },
                        )
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
}

