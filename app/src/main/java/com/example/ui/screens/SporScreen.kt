package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CizgiRepository
import com.example.model.LeagueStanding
import com.example.model.MatchEvent
import com.example.model.MatchStatus
import com.example.model.SportsMatch
import com.example.model.TopScorer
import com.example.ui.theme.CizgiColor
import com.example.util.CizgiNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SporScreen(
    repository: CizgiRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sportsSnapshot by repository.sportsSnapshot.collectAsState()
    val prefs by repository.preferences.collectAsState()

    // Only show sports and leagues backed by a real data source.
    var selectedSport by remember { mutableStateOf("FUTBOL") }
    val sportCategories = listOf("FUTBOL" to "⚽ Futbol", "BASKETBOL" to "🏀 Basketbol")

    // League choices depending on sport
    val competitions = when (selectedSport) {
        "BASKETBOL" -> listOf(
            "NBA" to "🇺🇸 NBA",
        )
        else -> listOf(
            "Trendyol Süper Lig" to "🇹🇷 Süper Lig",
            "UEFA Şampiyonlar Ligi" to "🏆 Şampiyonlar Ligi",
            "İngiltere Premier Lig" to "🏴󠁧󠁢󠁥󠁮󠁧󠁿 Premier Lig",
            "İspanya La Liga" to "🇪🇸 La Liga",
        )
    }

    var selectedCompetition by remember(selectedSport) { mutableStateOf(competitions.first().first) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Canlı Skorlar", "Puan Durumu", "Gol Krallığı", "Tüm Maçlar", "Gol Bildirimleri")

    // Match alert toggles
    val matchAlerts = remember { mutableStateMapOf<String, Boolean>() }

    // Live scores refresh once a minute; standings and scorer tables are cached by the service.
    LaunchedEffect(selectedCompetition, selectedSport) {
        while (isActive) {
            repository.fetchLiveSports(selectedCompetition, selectedSport)
            delay(60_000L)
        }
    }

    val snapshotMatches = if (sportsSnapshot.competition == selectedCompetition && sportsSnapshot.sportType == selectedSport) {
        sportsSnapshot.matches
    } else emptyList()
    val standings = if (sportsSnapshot.competition == selectedCompetition && sportsSnapshot.sportType == selectedSport) {
        sportsSnapshot.standings
    } else emptyList()
    val topScorers = if (sportsSnapshot.competition == selectedCompetition && sportsSnapshot.sportType == selectedSport) {
        sportsSnapshot.topScorers
    } else emptyList()
    val sportsLoading = sportsSnapshot.competition == selectedCompetition &&
        sportsSnapshot.sportType == selectedSport && sportsSnapshot.isLoading
    val scorerMessage = when {
        selectedSport != "FUTBOL" -> "Bu branş için gol krallığı verisi sunulmuyor."
        sportsSnapshot.competition != selectedCompetition || sportsSnapshot.sportType != selectedSport -> "Gol krallığı verisi yükleniyor."
        sportsSnapshot.isLoading && topScorers.isEmpty() -> "Gol krallığı verisi yükleniyor."
        topScorers.isNotEmpty() -> ""
        else -> sportsSnapshot.scorerMessage.ifBlank { "Gol krallığı verisi alınamadı." }
    }

    val liveMatches = remember(snapshotMatches) { snapshotMatches.filter { it.status == MatchStatus.LIVE } }
    val finishedMatches = remember(snapshotMatches) { snapshotMatches.filter { it.status == MatchStatus.FINISHED } }
    val upcomingMatches = remember(snapshotMatches) { snapshotMatches.filter { it.status == MatchStatus.UPCOMING } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("spor_screen"),
    ) {
        // 1. Maçkolik Canlı Spor Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Action & Live Counter Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "CANLI MAÇ MERKEZİ",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        if (liveMatches.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935)),
                                )
                                Text(
                                    text = "${liveMatches.size} CANLI",
                                    color = Color(0xFFE53935),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                            }
                        } else {
                            Text(
                                text = "Maçkolik & API-Sports",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { repository.fetchLiveSports(selectedCompetition, selectedSport) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Skorları Güncelle",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // 2. Multi-Sport Selection Chips (Futbol, Basketbol, Voleybol)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sportCategories.forEach { (sportKey, sportLabel) ->
                        val isSelected = selectedSport == sportKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSport = sportKey },
                            label = {
                                Text(
                                    text = sportLabel,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(18.dp),
                        )
                    }
                }

                // 3. League Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    competitions.forEach { (compKey, compLabel) ->
                        val isSelected = selectedCompetition == compKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCompetition = compKey },
                            label = {
                                Text(
                                    text = compLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }

                // 4. Sub-tabs (Canlı Skorlar, Puan Durumu, Gol Krallığı, Tüm Maçlar, Gol Bildirimleri)
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp,
                        )
                    },
                    divider = {},
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = index },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (title == "Canlı Skorlar" && liveMatches.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFE53935),
                                        ) {
                                            Text(
                                                text = "${liveMatches.size}",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            0 -> {
                // CANLI SKORLAR TAB
                MackolikLiveMatchesList(
                    liveMatches = liveMatches,
                    allMatches = snapshotMatches,
                    matchAlerts = matchAlerts,
                    onToggleAlert = { match ->
                        val current = matchAlerts[match.id] ?: false
                        matchAlerts[match.id] = !current
                        if (!current) {
                            CizgiNotificationHelper.sendGoalNotification(
                                context,
                                "${match.homeTeam} vs ${match.awayTeam}",
                                match.minute ?: 1,
                                "${match.homeTeam} Takipte",
                                "${match.homeScore ?: 0} - ${match.awayScore ?: 0}"
                            )
                        }
                    },
                )
            }
            1 -> {
                // PUAN DURUMU TAB (TAM VE GÜNCEL LİG TABLOSU)
                MackolikStandingsTable(
                    standings = standings,
                    followedTeams = prefs.followedTeams,
                    competition = selectedCompetition,
                    isLoading = sportsLoading,
                    emptyMessage = if (selectedSport == "FUTBOL") "Bu lig için şu anda puan durumu alınamadı." else "Bu branş için puan durumu verisi sunulmuyor.",
                )
            }
            2 -> {
                // GOL KRALLIĞI VE İSTATİSTİKLER TAB
                MackolikScorersTab(
                    scorers = topScorers,
                    sportType = selectedSport,
                    competition = selectedCompetition,
                    emptyMessage = scorerMessage,
                )
            }
            3 -> {
                // TÜM MAÇLAR & FİKSTÜR TAB
                MackolikAllFixturesList(
                    liveMatches = liveMatches,
                    finishedMatches = finishedMatches,
                    upcomingMatches = upcomingMatches,
                    matchAlerts = matchAlerts,
                    onToggleAlert = { match ->
                        val current = matchAlerts[match.id] ?: false
                        matchAlerts[match.id] = !current
                    },
                )
            }
            4 -> {
                // GOL & SAYI BİLDİRİMLERİ TAB
                MackolikGoalAlertsFeed(
                    matches = snapshotMatches,
                )
            }
        }
    }
}

/**
 * Canlı Skorlar Listesi
 */
@Composable
fun MackolikLiveMatchesList(
    liveMatches: List<SportsMatch>,
    allMatches: List<SportsMatch>,
    matchAlerts: Map<String, Boolean>,
    onToggleAlert: (SportsMatch) -> Unit,
) {
    if (liveMatches.isEmpty()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("⏱️", fontSize = 36.sp)
                        Text(
                            text = "Şu Anda Oynanan Canlı Karşılaşma Bulunmuyor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Günün diğer karşılaşmalarını ve canlı kadroları aşağıdan detaylı inceleyebilirsiniz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "GÜNÜN MAÇLARI VE DETAYLI KADROLAR",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(allMatches, key = { it.id }) { match ->
                GlowingMatchCard(
                    match = match,
                    hasAlert = matchAlerts[match.id] ?: false,
                    onToggleAlert = { onToggleAlert(match) },
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE53935).copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935)),
                        )
                        Text(
                            text = "Canlı karşılaşmalar otomatik saniyelik güncellenmektedir.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFC62828),
                        )
                    }
                }
            }

            items(liveMatches, key = { it.id }) { match ->
                GlowingMatchCard(
                    match = match,
                    hasAlert = matchAlerts[match.id] ?: false,
                    onToggleAlert = { onToggleAlert(match) },
                )
            }

            if (allMatches.size > liveMatches.size) {
                item {
                    Text(
                        text = "DİĞER KARŞILAŞMALAR",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }

                items(allMatches.filter { it.status != MatchStatus.LIVE }, key = { it.id }) { match ->
                    GlowingMatchCard(
                        match = match,
                        hasAlert = matchAlerts[match.id] ?: false,
                        onToggleAlert = { onToggleAlert(match) },
                    )
                }
            }
        }
    }
}

/**
 * Tüm Maçlar & Fikstür Listesi
 */
@Composable
fun MackolikAllFixturesList(
    liveMatches: List<SportsMatch>,
    finishedMatches: List<SportsMatch>,
    upcomingMatches: List<SportsMatch>,
    matchAlerts: Map<String, Boolean>,
    onToggleAlert: (SportsMatch) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (liveMatches.isNotEmpty()) {
            item {
                Text("🔴 OYNANAN MAÇLAR (CANLI)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFE53935))
            }
            items(liveMatches, key = { it.id }) { match ->
                GlowingMatchCard(match = match, hasAlert = matchAlerts[match.id] ?: false, onToggleAlert = { onToggleAlert(match) })
            }
        }

        if (upcomingMatches.isNotEmpty()) {
            item {
                Text("⏳ BAŞLAYACAK KARŞILAŞMALAR", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            items(upcomingMatches, key = { it.id }) { match ->
                GlowingMatchCard(match = match, hasAlert = matchAlerts[match.id] ?: false, onToggleAlert = { onToggleAlert(match) })
            }
        }

        if (finishedMatches.isNotEmpty()) {
            item {
                Text("🏁 TAMAMLANAN KARŞILAŞMALAR", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(finishedMatches, key = { it.id }) { match ->
                GlowingMatchCard(match = match, hasAlert = matchAlerts[match.id] ?: false, onToggleAlert = { onToggleAlert(match) })
            }
        }
    }
}

/**
 * Canlı Parlayan ve Genişletilebilir Detaylı Maçkolik Maç Kartı
 * İddaa Oranları, Canlı İstatistikler, İlk 11'ler ve Olay Zaman Çizelgesi
 */
@Composable
fun GlowingMatchCard(
    match: SportsMatch,
    hasAlert: Boolean,
    onToggleAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val isLive = match.status == MatchStatus.LIVE

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_match")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_alpha"
    )

    val sportEmoji = when (match.sportType) {
        "BASKETBOL" -> "🏀"
        "VOLEYBOL" -> "🏐"
        else -> "⚽"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLive) 3.dp else 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = if (isLive) {
                Brush.linearGradient(
                    listOf(
                        Color(0xFFE53935).copy(alpha = pulseAlpha),
                        CizgiColor.Gold.copy(alpha = pulseAlpha),
                        Color(0xFFE53935).copy(alpha = pulseAlpha * 0.7f),
                    )
                )
            } else {
                SolidColor(MaterialTheme.colorScheme.outlineVariant)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .testTag("match_card_${match.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top Row: Competition + Status Pill + Notification Bell
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(sportEmoji, fontSize = 14.sp)
                    Text(
                        text = "${match.competition} · ${match.round}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Status Pill with glowing flash if live
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (match.status) {
                            MatchStatus.LIVE -> Color(0xFFE53935).copy(alpha = if (pulseAlpha > 0.6f) 1f else 0.85f)
                            MatchStatus.FINISHED -> MaterialTheme.colorScheme.surfaceVariant
                            MatchStatus.UPCOMING -> MaterialTheme.colorScheme.primaryContainer
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isLive) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                            Text(
                                text = match.statusLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (match.status) {
                                    MatchStatus.LIVE -> Color.White
                                    MatchStatus.FINISHED -> MaterialTheme.colorScheme.onSurface
                                    MatchStatus.UPCOMING -> MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }

                    // Notification toggle button
                    IconButton(
                        onClick = onToggleAlert,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (hasAlert) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = if (hasAlert) "Bildirim Açık" else "Bildirim Aç",
                            tint = if (hasAlert) CizgiColor.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Teams & Score Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Home Team
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = match.homeTeam,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Middle Score Box (Glowing on live)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isLive) Color(0xFFE53935).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (match.homeScore != null && match.awayScore != null) {
                            Text(
                                text = "${match.homeScore}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isLive) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "-",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${match.awayScore}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isLive) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                text = match.kickoffTime,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // Away Team
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = match.awayTeam,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }

            // Live Action Banner (Flashing Goal / Point Banner)
            if (match.liveAction.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLive) Color(0xFFE53935).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = match.liveAction,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isLive) Color(0xFFC62828) else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Expandable details (İddaa Oranları, Canlı İstatistikler, Kadrolar, Olaylar)
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // 1. İddaa Oranları Çubuğu (Maçkolik Standardı)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "İDDAA ORANLARI",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OddBox(title = "MS 1", value = match.oddsHome, modifier = Modifier.weight(1f))
                            OddBox(title = "X (Beraberlik)", value = match.oddsDraw, modifier = Modifier.weight(1.2f))
                            OddBox(title = "MS 2", value = match.oddsAway, modifier = Modifier.weight(1f))
                            OddBox(title = "2.5 ÜST", value = "1.65", modifier = Modifier.weight(1f))
                        }
                    }

                    // 2. Canlı Maç İstatistikleri Çubukları
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "CANLI MAÇ İSTATİSTİKLERİ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        StatComparisonBar(title = "Topla Oynama", home = "%${match.possessionHome}", away = "%${match.possessionAway}", ratio = match.possessionHome / 100f)
                        StatComparisonBar(title = "Toplam Şut", home = "${match.shotsHome}", away = "${match.shotsAway}", ratio = match.shotsHome.toFloat() / maxOf(1, match.shotsHome + match.shotsAway))
                        StatComparisonBar(title = "İsabetli Şut", home = "${match.shotsOnTargetHome}", away = "${match.shotsOnTargetAway}", ratio = match.shotsOnTargetHome.toFloat() / maxOf(1, match.shotsOnTargetHome + match.shotsOnTargetAway))
                        StatComparisonBar(title = "Kornerler", home = "${match.cornersHome}", away = "${match.cornersAway}", ratio = match.cornersHome.toFloat() / maxOf(1, match.cornersHome + match.cornersAway))
                    }

                    // 3. Kadrolar ve Saha Dizilişi (İlk 11'ler)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "İLK 11'LER VE KADROLAR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        val homeLineup = remember(match.homeTeam) { getRealisticLineup(match.homeTeam) }
                        val awayLineup = remember(match.awayTeam) { getRealisticLineup(match.awayTeam) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Home Lineup
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${match.homeTeam} (4-2-3-1)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        maxLines = 1,
                                    )
                                }

                                homeLineup.forEach { (name, pos) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(text = pos, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                }
                            }

                            // Away Lineup
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${match.awayTeam} (4-3-3)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                        maxLines = 1,
                                    )
                                }

                                awayLineup.forEach { (name, pos) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(text = pos, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 4. Hakem ve Stadyum Bilgisi
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🏟️ ${if (match.stadium.isNotBlank()) match.stadium else "RAMS Park"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "👨‍⚖️ ${if (match.referee.isNotBlank()) match.referee else "Halil Umut Meler"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 5. Olay Zaman Çizelgesi (Goller, Kartlar)
                    if (match.events.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "OLAY ZAMAN ÇİZELGESİ",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            match.events.forEach { ev ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        val icon = when (ev.type) {
                                            "GOL" -> sportEmoji
                                            "SARI" -> "🟨"
                                            "KIRMIZI" -> "🟥"
                                            else -> "⚡"
                                        }
                                        Text(icon, fontSize = 13.sp)
                                        Text(
                                            text = "${ev.minute}' ${ev.player}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Text(
                                        text = ev.team,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom expansion cue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "Detayları ve Kadroları Gizle" else "Maç Detayları, İddaa ve Kadrolar",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun OddBox(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StatComparisonBar(title: String, home: String, away: String, ratio: Float) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = home, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = away, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0.05f, 0.95f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * Seçili ligin sağlayıcıdan gelen puan tablosu.
 */
private fun qualificationLabel(note: String): String? {
    val lower = note.lowercase()
    return when {
        "relegat" in lower || "drop" in lower || "düşme" in lower -> "Düşme hattı"
        "champions league" in lower || "şampiyonlar ligi" in lower -> "Şampiyonlar Ligi"
        "europa league" in lower || "avrupa ligi" in lower -> "Avrupa Ligi"
        "conference league" in lower || "konferans ligi" in lower -> "Konferans Ligi"
        "playoff" in lower || "play-off" in lower -> "Play-off"
        else -> null
    }
}

private fun qualificationColor(note: String?): Color {
    val lower = note?.lowercase().orEmpty()
    return when {
        "relegat" in lower || "drop" in lower || "düşme" in lower -> Color(0xFFD32F2F)
        "champions league" in lower || "şampiyonlar ligi" in lower -> Color(0xFF1976D2)
        "europa league" in lower || "avrupa ligi" in lower -> Color(0xFFE65100)
        "conference league" in lower || "konferans ligi" in lower -> Color(0xFF2E7D32)
        else -> Color.Transparent
    }
}

@Composable
fun MackolikStandingsTable(
    standings: List<LeagueStanding>,
    followedTeams: Set<String>,
    competition: String,
    isLoading: Boolean = false,
    emptyMessage: String = "Puan durumu alınamadı.",
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = competition,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Header Row: S | Takım | O | G | B | M | AG | YG | AV | P
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("S", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                Text("Takım", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("O", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                Text("G", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                Text("B", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                Text("M", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                Text("AV", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                Text("P", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                if (standings.isEmpty()) {
                    item(key = "empty_standings") {
                        Text(
                            text = if (isLoading) "Puan durumu yükleniyor…" else emptyMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
                items(standings, key = { it.team }) { team ->
                    val isFollowed = team.team in followedTeams
                    val qualificationColor = qualificationColor(team.qualificationNote)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isFollowed) CizgiColor.Gold.copy(alpha = 0.12f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.width(22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (qualificationColor != Color.Transparent) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 3.dp, height = 14.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(qualificationColor),
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                            Text(
                                text = "${team.rank}",
                                fontWeight = if (team.rank == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = team.team,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isFollowed || team.rank == 1) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.5.sp,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            team.qualificationNote?.let { note ->
                                Text(
                                    text = qualificationLabel(note) ?: note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (isFollowed) {
                            Text("★", color = CizgiColor.Gold, fontSize = 11.sp)
                        }

                        Text("${team.played}", fontSize = 11.5.sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                        Text("${team.won}", fontSize = 11.5.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                        Text("${team.drawn}", fontSize = 11.5.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                        Text("${team.lost}", fontSize = 11.5.sp, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                        Text("${if (team.goalDiff > 0) "+" else ""}${team.goalDiff}", fontSize = 11.5.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                        Text(
                            text = "${team.points}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(28.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }

            val actualZones = standings.mapNotNull { team ->
                team.qualificationNote?.let { note -> qualificationLabel(note)?.let { it to qualificationColor(note) } }
            }.distinctBy { it.first }
            if (actualZones.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        actualZones.forEach { (label, color) -> LegendItem(color = color, label = label) }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Gol Krallığı & Asist Krallığı Tablosu
 */
@Composable
fun MackolikScorersTab(
    scorers: List<TopScorer>,
    sportType: String,
    competition: String = "",
    emptyMessage: String = "Gol krallığı verisi alınamadı.",
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(if (sportType == "BASKETBOL") "🏀" else "⚽", fontSize = 24.sp)
                    Column {
                        Text(
                            text = if (sportType == "BASKETBOL") "$competition İSTATİSTİKLERİ" else "$competition GOL KRALLIĞI",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Güncel sezon verisi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "ZİRVEDEKİ OYUNCULAR",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (scorers.isEmpty()) {
            item(key = "empty_scorers") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                    )
                }
            }
        }

        items(scorers, key = { "${it.team}-${it.player}" }) { scorer ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (scorer.rank == 1) CizgiColor.Gold else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${scorer.rank}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (scorer.rank == 1) CizgiColor.Navy else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Column {
                            Text(
                                text = scorer.player,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${scorer.team} · ${scorer.matches} Maç",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${scorer.count} ${scorer.type}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (scorer.secondaryCount > 0) {
                            Text(
                                text = "${scorer.secondaryCount} Asist",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Canlı Gol & Sayı Bildirimleri Akışı
 */
@Composable
fun MackolikGoalAlertsFeed(
    matches: List<SportsMatch>,
) {
    val allGoals = remember(matches) {
        val list = mutableListOf<Pair<SportsMatch, MatchEvent>>()
        matches.forEach { match ->
            match.events.filter { it.type == "GOL" }.forEach { ev ->
                list.add(match to ev)
            }
        }
        list.sortedByDescending { it.second.minute }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("🔔", fontSize = 24.sp)
                    Column {
                        Text(
                            text = "CANLI GOL VE SKOR AKIŞI",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Lig ve kupa maçlarındaki tüm goller, penaltılar ve kritik anlar bu akışta toplanır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (allGoals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("⚽", fontSize = 36.sp)
                        Text(
                            text = "Kayıtlı Canlı Gol Bulunmuyor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        } else {
            items(allGoals) { (match, ev) ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⚽", fontSize = 20.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "GOL! ${ev.player}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${ev.minute}'",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE53935),
                                    fontSize = 13.sp,
                                )
                            }
                            Text(
                                text = "${match.homeTeam} vs ${match.awayTeam} (${match.competition})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getRealisticLineup(team: String): List<Pair<String, String>> {
    return when {
        team.contains("Galatasaray", ignoreCase = true) -> listOf(
            "Fernando Muslera" to "KL (K)",
            "Kaan Ayhan" to "SB",
            "Davinson Sanchez" to "STP",
            "Abdülkerim Bardakcı" to "STP",
            "Ismail Jakobs" to "SLB",
            "Lucas Torreira" to "ÖNL",
            "Gabriel Sara" to "OS",
            "Barış Alper Yılmaz" to "SK",
            "Dries Mertens" to "10",
            "Yunus Akgün" to "SLK",
            "Victor Osimhen" to "SNT"
        )
        team.contains("Fenerbahçe", ignoreCase = true) -> listOf(
            "Dominik Livakovic" to "KL",
            "Mert Müldür" to "SB",
            "Alexander Djiku" to "STP",
            "Rodrigo Becao" to "STP",
            "Jayden Oosterwolde" to "SLB",
            "Sofyan Amrabat" to "ÖNL",
            "Fred" to "OS",
            "Dusan Tadic" to "SK",
            "Sebastian Szymanski" to "10",
            "Allan Saint-Maximin" to "SLK",
            "Youssef En-Nesyri" to "SNT"
        )
        team.contains("Beşiktaş", ignoreCase = true) -> listOf(
            "Mert Günok" to "KL (K)",
            "Jonas Svensson" to "SB",
            "Gabriel Paulista" to "STP",
            "Felix Uduokhai" to "STP",
            "Arthur Masuaku" to "SLB",
            "Al-Musrati" to "ÖNL",
            "Gedson Fernandes" to "OS",
            "Milot Rashica" to "SK",
            "Rafa Silva" to "10",
            "Semih Kılıçsoy" to "SLK",
            "Ciro Immobile" to "SNT"
        )
        team.contains("Trabzonspor", ignoreCase = true) -> listOf(
            "Uğurcan Çakır" to "KL (K)",
            "Pedro Malheiro" to "SB",
            "Stefan Savic" to "STP",
            "Batista Mendy" to "STP",
            "Eren Elmalı" to "SLB",
            "Okay Yokuşlu" to "ÖNL",
            "John Lundstram" to "OS",
            "Edin Visca" to "SK",
            "Muhammed Cham" to "10",
            "Denis Dragus" to "SLK",
            "Simon Banza" to "SNT"
        )
        team.contains("Başakşehir", ignoreCase = true) -> listOf(
            "Muhammed Şengezer" to "KL",
            "Leo Duarte" to "SB",
            "Ousseynou Ba" to "STP",
            "Jerome Opoku" to "STP",
            "Lucas Lima" to "SLB",
            "Berat Özdemir" to "ÖNL",
            "Miguel Crespo" to "OS",
            "Deniz Türüç" to "SK",
            "Dimitrios Pelkas" to "10",
            "Serdar Gürler" to "SLK",
            "Krzysztof Piatek" to "SNT"
        )
        team.contains("Samsunspor", ignoreCase = true) -> listOf(
            "Okan Kocuk" to "KL",
            "Zeki Yavru" to "SB (K)",
            "Lubomir Satka" to "STP",
            "Rick van Drongelen" to "STP",
            "Marc Bola" to "SLB",
            "Youssef Ait Bennasser" to "ÖNL",
            "Olivier Ntcham" to "OS",
            "Kingsley Schindler" to "SK",
            "Carlo Holse" to "10",
            "Emre Kılınç" to "SLK",
            "Marius Mouandilmadji" to "SNT"
        )
        else -> listOf(
            "1. Kaleci" to "KL",
            "2. Sağ Bek" to "SB",
            "3. Stoper" to "STP",
            "4. Stoper" to "STP",
            "5. Sol Bek" to "SLB",
            "6. Ön Libero" to "ÖNL",
            "7. Merkez Orta Saha" to "OS",
            "8. Sağ Kanat" to "SK",
            "9. Oyun Kurucu" to "10",
            "10. Sol Kanat" to "SLK",
            "11. Santrfor" to "SNT"
        )
    }
}
