package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.CizgiRepository
import com.example.ui.components.NewsFeedCard
import com.example.util.ArchiveSearch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun KaydedilenlerScreen(repository: CizgiRepository, onStoryClick: (String) -> Unit, onNavigateHome: () -> Unit, modifier: Modifier = Modifier) {
    val entries by repository.savedEntries.collectAsState()
    val history by repository.readingHistory.collectAsState()
    val downloading by repository.offlineDownloads.collectAsState()
    val errors by repository.offlineErrors.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var offlineOnly by rememberSaveable { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    val normalized = ArchiveSearch.normalize(query)
    val filtered = entries.filter { (!offlineOnly || it.offlineReady) && ArchiveSearch.normalize(it.title + " " + it.publisher).contains(normalized) }
    val recent = history.mapNotNull { row -> row.story()?.let { row to it } }.filter { (_, story) -> ArchiveSearch.normalize(story.title + " " + story.publisher).contains(normalized) }
    val savedIds = entries.mapTo(mutableSetOf()) { it.id }
    if (showClear) AlertDialog(onDismissRequest = { showClear = false }, title = { Text(if (tab == 0) "Kayıtlı haberleri kaldır" else "Okuma geçmişini temizle") },
        text = { Text(if (tab == 0) "Kayıtlı haberler ve çevrimdışı metinleri kitaplıktan kaldırılacak." else "Son okunan haberler ve kaldığın yer bilgileri bu cihazdan silinecek.") },
        confirmButton = { TextButton({ if (tab == 0) repository.clearSavedStories() else repository.clearReadingHistory(); showClear = false }) { Text("Temizle") } },
        dismissButton = { TextButton({ showClear = false }) { Text("Vazgeç") } })
    Column(modifier.fillMaxSize().testTag("kaydedilenler_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kitaplık", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (if (tab == 0) entries.isNotEmpty() else history.isNotEmpty()) IconButton({ showClear = true }) { Icon(Icons.Outlined.DeleteSweep, if (tab == 0) "Kayıtlı haberleri temizle" else "Okuma geçmişini temizle") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == 0, { tab = 0 }, { Text("Kaydedilenler · ${entries.size}") })
            FilterChip(tab == 1, { tab = 1 }, { Text("Geçmiş · ${history.size}") })
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp), singleLine = true, label = { Text("Kitaplıkta ara") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
        if (tab == 0) Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(offlineOnly, { offlineOnly = !offlineOnly }, { Text("Çevrimdışı hazır") }, leadingIcon = { Icon(Icons.Outlined.OfflinePin, null, Modifier.size(16.dp)) })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if ((tab == 0 && filtered.isEmpty()) || (tab == 1 && recent.isEmpty())) item {
                Column(Modifier.padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (tab == 0) Icons.Outlined.BookmarkBorder else Icons.Outlined.History, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (query.isNotBlank() || offlineOnly && tab == 0) "Bu filtreyle eşleşen haber yok" else if (tab == 0) "Okuma listeni oluştur" else "Okuduğun haberler burada", style = MaterialTheme.typography.titleLarge)
                    Text(if (tab == 0) "Yer imine dokunduğunda haber kaydedilir ve erişilebilen tam metni çevrimdışı okumak için indirilir." else "Bir haberi açtığında kaldığın yer otomatik saklanır. Buradan okumaya devam edebilirsin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onNavigateHome) { Text("Haberlere göz at") }
                }
            }
            if (tab == 0) items(filtered, key = { it.id }) { row ->
                val story = row.toStory()
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    NewsFeedCard(story, { onStoryClick(story.id) }, { repository.toggleSaveStory(story) })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (story.id in downloading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(if (row.offlineReady) Icons.Outlined.OfflinePin else Icons.AutoMirrored.Outlined.Article, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        val hasRemoteImage = row.imageUrl.startsWith("https://")
                        val needsDownload = !row.offlineReady || (hasRemoteImage && !row.hasOfflineImage)
                        val state = when {
                            story.id in downloading -> "Metin ve görsel hazırlanıyor"
                            row.offlineReady && row.hasOfflineImage -> "Metin ve görsel çevrimdışı hazır"
                            row.offlineReady && hasRemoteImage -> "Metin hazır · Görsel indirilebilir"
                            row.offlineReady -> "Metin çevrimdışı hazır"
                            else -> "Kısa içerik kayıtlı"
                        }
                        Text(state, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        if (needsDownload && story.id !in downloading) TextButton({ repository.downloadForOffline(story.id) }) { Text(if (row.offlineReady) "Görseli indir" else "İndir") }
                    }
                    errors[story.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (tab == 1) items(recent, key = { it.first.storyId }) { (row, snapshot) ->
                val story = snapshot.copy(isSaved = snapshot.id in savedIds)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    NewsFeedCard(story, { onStoryClick(story.id) }, { repository.toggleSaveStory(story) })
                    val time = Instant.ofEpochMilli(row.lastReadAt).atZone(ZoneId.of("Europe/Istanbul")).format(DateTimeFormatter.ofPattern("dd.MM · HH:mm"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (row.progressPercent >= 95) "Okundu" else "%${row.progressPercent} · Kaldığın yerden devam et", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    LinearProgressIndicator(progress = { row.progressPercent / 100f }, Modifier.fillMaxWidth().height(3.dp))
                }
            }
        }
    }
}
