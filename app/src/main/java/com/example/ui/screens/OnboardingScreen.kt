package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.CizgiRepository
import com.example.util.ArchiveSearch

@Composable
fun OnboardingScreen(repository: CizgiRepository, onDone: () -> Unit) {
    val prefs by repository.preferences.collectAsState()
    val stories by repository.stories.collectAsState()
    val archiveTopics by repository.archiveTopics.collectAsState()
    val sports by repository.sportsSnapshot.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var topics by rememberSaveable { mutableStateOf(prefs.followedTopics.toList()) }
    var teams by rememberSaveable { mutableStateOf(prefs.followedTeams.toList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var league by rememberSaveable { mutableStateOf("Trendyol Süper Lig") }
    val topicOptions = (archiveTopics + stories.map { it.topic } + prefs.followedTopics).filter(String::isNotBlank).distinct().sorted()
    val teamOptions = (sports.standings.map { it.team } + sports.matches.flatMap { listOf(it.homeTeam, it.awayTeam) } + teams)
        .filter(String::isNotBlank).distinct().sorted()
    val sport = if (league in setOf("EuroLeague", "NBA")) "BASKETBOL" else "FUTBOL"
    LaunchedEffect(step, league) { if (step == 1) repository.fetchLiveSports(league, sport) }
    fun finish() { repository.completeOnboarding(topics.toSet(), teams.toSet()); onDone() }
    BackHandler { if (step > 0) { step--; query = "" } else finish() }
    Surface(Modifier.fillMaxSize().testTag("onboarding_screen"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("çizgi.", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(::finish) { Text(if (prefs.onboardingCompleted) "Bitti" else "Şimdilik geç") }
            }
            LinearProgressIndicator(progress = { (step + 1) / 2f }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text(if (step == 0) "Gündemini sen seç" else "Takımını takip et", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(if (step == 0) "İlgini çeken konular Senin için akışında bir araya gelsin. Seçimlerini istediğin zaman değiştirebilirsin." else "Maçlarını ve takımla ilgili haberleri bulmak kolaylaşsın. Bildirimleri daha sonra ayarlardan açabilirsin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step == 1) {
                var open by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text(league, Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null) }
                    DropdownMenu(open, { open = false }) {
                        listOf("Trendyol Süper Lig", "Trendyol 1. Lig", "İngiltere Premier Lig", "İspanya La Liga", "EuroLeague", "NBA").forEach { value ->
                            DropdownMenuItem(text = { Text(value) }, onClick = { league = value; open = false })
                        }
                    }
                }
            }
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(if (step == 0) "Konu ara" else "Takım ara") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            val options = if (step == 0) topicOptions else teamOptions
            val filtered = options.filter { ArchiveSearch.normalize(it).contains(ArchiveSearch.normalize(query)) }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.isEmpty()) item {
                    Text(if (sports.isLoading && step == 1 || stories.isEmpty() && step == 0) "Seçenekler yükleniyor. İstersen daha sonra da seçim yapabilirsin." else "Bu lig için takım verisi alınamadı. Başka bir lig seçebilir veya daha sonra deneyebilirsin.", style = MaterialTheme.typography.bodyMedium)
                }
                if (filtered.isEmpty() && options.isNotEmpty()) item { Text("Aramana uygun seçenek bulunamadı.", style = MaterialTheme.typography.bodyMedium) }
                items(filtered, key = { it }) { value ->
                    val selected = value in if (step == 0) topics else teams
                    Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox) {
                        if (step == 0) topics = if (selected) topics - value else topics + value else { teams = if (selected) teams - value else teams + value; if (!selected) repository.rememberSportsSelection() }
                    }) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Checkbox(selected, null)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton({ step--; query = "" }) { Text("Geri") }
                Button({ if (step == 0) { step = 1; query = "" } else finish() }, Modifier.weight(1f).heightIn(min = 50.dp)) {
                    Text(if (step == 0) "Devam et · ${topics.size} konu" else "Akışımı aç · ${teams.size} takım")
                }
            }
        }
    }
}
