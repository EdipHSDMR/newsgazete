package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.CizgiRepository
import com.example.model.Story
import com.example.ui.components.NewsFeedCard
import com.example.util.ArchiveSearch
import com.example.util.NewsStoryDeduplicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KesfetScreen(repository: CizgiRepository, onStoryClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val archiveCount by repository.newsArchiveCount.collectAsState()
    val topics by repository.archiveTopics.collectAsState()
    val publishers by repository.archivePublishers.collectAsState()
    val recent by repository.recentSearches.collectAsState()
    val saved by repository.savedArticles.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var publisher by rememberSaveable { mutableStateOf<String?>(null) }
    var fromDay by rememberSaveable { mutableStateOf<String?>(null) }
    var untilDay by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    var optionQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Story>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadToken by remember { mutableIntStateOf(0) }
    val state = rememberLazyListState()
    val zone = ZoneId.of("Europe/Istanbul")
    val active = topic != null || publisher != null || fromDay != null || untilDay != null
    LaunchedEffect(query, topic, publisher, fromDay, untilDay, archiveCount) {
        loading = true; error = null; results = emptyList(); hasMore = true
        var offset = 0
        var lastToken = loadToken
        delay(280)
        val from = fromDay?.let { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }
        val until = untilDay?.let { LocalDate.parse(it).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() }
        suspend fun page() {
            loading = true; error = null
            try {
                val next = repository.searchArchive(query, topic, publisher, from, until, offset)
                results = NewsStoryDeduplicator.merge(results + next.stories)
                offset = next.nextOffset; hasMore = next.hasMore
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Arama tamamlanamadı. Yeniden deneyebilirsin." }
            finally { loading = false }
        }
        page()
        snapshotFlow { Triple(state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, state.layoutInfo.totalItemsCount, loadToken) }.collect { (last, total, token) ->
            if (hasMore && !loading && (last >= total - 5 || token != lastToken) && (error == null || token != lastToken)) {
                lastToken = token; page()
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.key.toString() } }.collect { repository.prepareReadingContent(it) }
    }
    fun rememberQuery() { repository.rememberSearch(query); keyboard?.hide() }
    fun chooseDate(isFrom: Boolean) {
        val old = (if (isFrom) fromDay else untilDay)?.let(LocalDate::parse) ?: LocalDate.now(zone)
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            val chosen = LocalDate.of(year, month + 1, day).toString()
            if (isFrom) { fromDay = chosen; if (untilDay != null && chosen > untilDay!!) untilDay = chosen }
            else { untilDay = chosen; if (fromDay != null && chosen < fromDay!!) fromDay = chosen }
        }, old.year, old.monthValue - 1, old.dayOfMonth)
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }
    Column(modifier.fillMaxSize().testTag("kesfet_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Keşfet", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
            Text("$archiveCount haberlik arşiv", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f).testTag("search_text_field"), singleLine = true,
                label = { Text("Arşivde ara") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "Aramayı temizle") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { rememberQuery() }), shape = RoundedCornerShape(17.dp))
            FilledTonalIconButton({ showFilters = true }, Modifier.size(48.dp)) { Icon(Icons.Outlined.Tune, if (active) "Filtreler etkin, düzenle" else "Arama filtreleri") }
        }
        if (query.isBlank() && recent.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { text -> SuggestionChip({ query = text }, { Text(text) }, icon = { Icon(Icons.Outlined.History, null, Modifier.size(16.dp)) }) }
            TextButton(repository::clearSearchHistory) { Text("Temizle") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(topic == null, { topic = null }, { Text("Tümü") })
            topics.take(8).forEach { value -> FilterChip(topic == value, { topic = if (topic == value) null else value }, { Text(value) }) }
        }
        if (active) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOfNotNull(topic, publisher, fromDay?.let { "Başlangıç: $it" }, untilDay?.let { "Bitiş: $it" }).forEach { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 12.dp)) }
            TextButton({ topic = null; publisher = null; fromDay = null; untilDay = null }) { Text("Sıfırla") }
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading && results.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            if (!loading && results.isEmpty() && error == null) item {
                Column(Modifier.padding(vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Arşivde eşleşen haber bulunamadı", style = MaterialTheme.typography.titleMedium)
                    Text("Arama sözcüklerini veya tarih aralığını değiştirebilirsin. Arşiv, uygulamanın kaynaklardan aldığı haberleri içerir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(results, key = { it.id }) { item ->
                val story = item.copy(isSaved = saved.any { it.id == item.id })
                NewsFeedCard(story, { rememberQuery(); onStoryClick(story.id) }, { repository.toggleSaveStory(story) })
            }
            if (error != null) item { TextButton({ loadToken++ }) { Text("${error} Tekrar dene") } }
            else if (hasMore && results.isNotEmpty()) item {
                if (loading) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                else TextButton({ loadToken++ }, Modifier.fillMaxWidth()) { Text("Daha fazla göster") }
            }
            if (!hasMore && results.isNotEmpty()) item { Text("Arşivdeki eşleşmelerin sonuna geldin.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (showFilters) ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.85f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Aramayı daralt", style = MaterialTheme.typography.titleLarge) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ chooseDate(true) }, Modifier.weight(1f)) { Text(fromDay ?: "Başlangıç tarihi") }
                    OutlinedButton({ chooseDate(false) }, Modifier.weight(1f)) { Text(untilDay ?: "Bitiş tarihi") }
                }
                if (fromDay != null || untilDay != null) TextButton({ fromDay = null; untilDay = null }) { Text("Tarihleri kaldır") }
            }
            item { OutlinedTextField(optionQuery, { optionQuery = it }, Modifier.fillMaxWidth(), label = { Text("Konu veya yayıncı bul") }, singleLine = true) }
            item { Text("Konular", style = MaterialTheme.typography.titleSmall) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topics.filter { ArchiveSearch.normalize(it).contains(ArchiveSearch.normalize(optionQuery)) }.forEach { value ->
                        FilterChip(topic == value, { topic = if (topic == value) null else value }, { Text(value) })
                    }
                }
            }
            item { Text("Yayıncılar", style = MaterialTheme.typography.titleSmall) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    publishers.filter { ArchiveSearch.normalize(it).contains(ArchiveSearch.normalize(optionQuery)) }.forEach { value ->
                        FilterChip(publisher == value, { publisher = if (publisher == value) null else value }, { Text(value) })
                    }
                }
            }
            item { Button({ showFilters = false }, Modifier.fillMaxWidth()) { Text("Sonuçları göster") } }
        }
    }
}
