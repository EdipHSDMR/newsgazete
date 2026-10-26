package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CizgiRepository
import com.example.ui.components.NewsFeedCard
import com.example.ui.theme.CizgiColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KesfetScreen(
    repository: CizgiRepository,
    onStoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stories by repository.stories.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTopicFilter by remember { mutableStateOf<String?>(null) }
    var selectedSourceFilter by remember { mutableStateOf<String?>(null) }
    var filterOptionQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }

    val topicCounts = remember(stories) {
        stories.asSequence()
            .map { it.topic.trim() }
            .filter(String::isNotBlank)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .associate { it.key to it.value }
    }

    val allPublishers = remember(stories) {
        stories.map { it.publisher.trim() }.filter(String::isNotBlank).distinct().sorted()
    }

    val filteredResults = remember(stories, searchQuery, selectedTopicFilter, selectedSourceFilter) {
        stories.filter { story ->
            val matchesQuery = searchQuery.isBlank() ||
                story.title.contains(searchQuery, ignoreCase = true) ||
                story.summary.contains(searchQuery, ignoreCase = true) ||
                story.whatHappened.contains(searchQuery, ignoreCase = true) ||
                story.whyItMatters.contains(searchQuery, ignoreCase = true) ||
                story.topic.contains(searchQuery, ignoreCase = true) ||
                story.publisher.contains(searchQuery, ignoreCase = true)

            val matchesTopic = selectedTopicFilter == null ||
                story.topic.equals(selectedTopicFilter, ignoreCase = true)

            val matchesSource = selectedSourceFilter == null ||
                story.publisher.equals(selectedSourceFilter, ignoreCase = true)

            matchesQuery && matchesTopic && matchesSource
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("kesfet_screen"),
    ) {
        // Search Header Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Archive Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("📚", fontSize = 20.sp)
                    Column {
                        Text(
                            text = "HABER ARŞİVİ VE GEÇMİŞ YILLAR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Geçmiş ve güncel haberleri arayın, tüm yayıncı ve kategorileri filtreleyin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                        )
                    }
                }
            }

            // Search Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Başlık, konu veya yayıncı ara...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Ara",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Temizle",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = CizgiColor.BorderLight,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_text_field"),
                )

                // Filter Sheet Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTopicFilter != null || selectedSourceFilter != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(52.dp),
                    onClick = { showFilterSheet = true },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filtreler",
                            tint = if (selectedTopicFilter != null || selectedSourceFilter != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            if (topicCounts.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = selectedTopicFilter == null,
                        onClick = { selectedTopicFilter = null },
                        label = { Text("Tümü") },
                    )
                    topicCounts.keys.take(6).forEach { topic ->
                        FilterChip(
                            selected = selectedTopicFilter.equals(topic, ignoreCase = true),
                            onClick = {
                                selectedTopicFilter = if (selectedTopicFilter.equals(topic, ignoreCase = true)) null else topic
                            },
                            label = { Text(topic, maxLines = 1) },
                        )
                    }
                }
            }

        }

        // Active filter pills indicator
        if (selectedTopicFilter != null || selectedSourceFilter != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Filtreler:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                selectedTopicFilter?.let { topic ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { selectedTopicFilter = null },
                    ) {
                        Text(
                            text = "$topic ✕",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                selectedSourceFilter?.let { src ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { selectedSourceFilter = null },
                    ) {
                        Text(
                            text = "$src ✕",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = {
                        selectedTopicFilter = null
                        selectedSourceFilter = null
                    },
                ) {
                    Text("Tümünü Temizle", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Results Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "SONUÇLAR (${filteredResults.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (filteredResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🔍", fontSize = 40.sp)
                    Text(
                        text = "Sonuç Bulunamadı",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Farklı bir arama terimi deneyebilir veya filtreleri temizleyebilirsiniz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = filteredResults,
                    key = { it.id },
                ) { story ->
                    NewsFeedCard(
                        story = story,
                        onClick = { onStoryClick(story.id) },
                        onSaveToggle = { repository.toggleSaveStory(story.id) },
                    )
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Arama Filtreleri",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )

                OutlinedTextField(
                    value = filterOptionQuery,
                    onValueChange = { filterOptionQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Kategori veya yayıncı ara...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filterOptionQuery.isNotEmpty()) {
                            IconButton(onClick = { filterOptionQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Filtre aramasını temizle")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                )

                // Konu Filtresi
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Konu Seçimi",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        topicCounts.keys.filter { it.contains(filterOptionQuery, ignoreCase = true) }.forEach { topic ->
                            val isSelected = selectedTopicFilter == topic
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTopicFilter = if (isSelected) null else topic
                                },
                                label = { Text(topic) },
                            )
                        }
                    }
                }

                // Yayıncı Filtresi
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Yayıncı Kaynağı",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        allPublishers.filter { it.contains(filterOptionQuery, ignoreCase = true) }.forEach { pub ->
                            val isSelected = selectedSourceFilter == pub
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedSourceFilter = if (isSelected) null else pub
                                },
                                label = { Text(pub) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = {
                            selectedTopicFilter = null
                            selectedSourceFilter = null
                            filterOptionQuery = ""
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Sıfırla")
                    }

                    androidx.compose.material3.Button(
                        onClick = { showFilterSheet = false },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Uygula (${filteredResults.size})", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

