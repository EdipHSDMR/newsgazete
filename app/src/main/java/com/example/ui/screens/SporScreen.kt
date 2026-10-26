package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.theme.MotionVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.CizgiRepository
import com.example.model.*
import com.example.ui.theme.CizgiColor
import com.example.ui.theme.LocalReducedMotion
import com.example.util.CizgiNotificationHelper
import com.example.util.FootballFormation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SporScreen(repository: CizgiRepository, modifier: Modifier = Modifier) {
    val snapshot by repository.sportsSnapshot.collectAsState()
    val preferences by repository.preferences.collectAsState()
    val alerts by repository.matchAlertIds.collectAsState()
    var sport by rememberSaveable { mutableStateOf("FUTBOL") }
    val leagues = if (sport == "BASKETBOL") listOf(
        "EuroLeague" to "EuroLeague", "NBA" to "NBA", "WNBA" to "WNBA", "NBA G League" to "G League",
        "NCAA Erkekler" to "NCAA Erkekler", "NCAA Kadınlar" to "NCAA Kadınlar",
    ) else listOf(
        "Trendyol Süper Lig" to "Süper Lig", "Trendyol 1. Lig" to "1. Lig",
        "UEFA Şampiyonlar Ligi" to "Şampiyonlar Ligi", "UEFA Avrupa Ligi" to "Avrupa Ligi",
        "UEFA Konferans Ligi" to "Konferans Ligi", "İngiltere Premier Lig" to "Premier Lig",
        "İspanya La Liga" to "La Liga", "Almanya Bundesliga" to "Bundesliga",
        "İtalya Serie A" to "Serie A", "Fransa Ligue 1" to "Ligue 1",
        "Hollanda Eredivisie" to "Eredivisie", "Portekiz Primeira Liga" to "Primeira Liga",
        "İskoçya Premiership" to "İskoçya", "Belçika Pro League" to "Belçika",
        "Suudi Arabistan Pro League" to "Suudi Arabistan", "Arjantin Primera División" to "Arjantin",
        "İngiltere Championship" to "Championship", "İspanya Segunda" to "Segunda",
        "İtalya Serie B" to "Serie B", "Almanya 2. Bundesliga" to "2. Bundesliga",
        "Fransa Ligue 2" to "Ligue 2", "MLS" to "MLS", "Brezilya Serie A" to "Brezilya",
        "Japonya J1 League" to "Japonya", "CONMEBOL Libertadores" to "Libertadores",
        "CONMEBOL Sudamericana" to "Sudamericana",
    )
    var league by rememberSaveable(sport) { mutableStateOf(leagues.first().first) }
    var tab by rememberSaveable(sport) { mutableIntStateOf(0) }
    val today = LocalDate.now(ZoneId.of("Europe/Istanbul"))
    val season = if (sport == "BASKETBOL" && league == "WNBA") today.year else if (today.monthValue >= 7) today.year else today.year - 1
    val matches = if (snapshot.sportType == sport && snapshot.competition == league && snapshot.seasonYear == season) snapshot.matches else emptyList()
    val selected = snapshot.sportType == sport && snapshot.competition == league
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(league, sport, season, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                repository.fetchLiveSports(league, sport, season)
                delay(10_000L)
            }
        }
    }
    val context = LocalContext.current
    var pendingMatch by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            repository.setMatchAlerts(true)
            pendingMatch?.let(repository::toggleMatchAlert)
        }
        pendingMatch = null
    }
    fun toggleAlert(match: SportsMatch) {
        if (match.id in alerts) { repository.toggleMatchAlert(match.id); return }
        if (Build.VERSION.SDK_INT >= 33 && !CizgiNotificationHelper.hasNotificationPermission(context)) {
            pendingMatch = match.id
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            repository.setMatchAlerts(true)
            repository.toggleMatchAlert(match.id)
        }
    }
    Column(modifier.fillMaxSize().testTag("spor_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Spor", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            val liveCount = matches.count { it.status == MatchStatus.LIVE }
            if (liveCount > 0) {
                LiveDot()
                Text("$liveCount canlı", color = CizgiColor.Red, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 7.dp))
            }
            IconButton({ repository.fetchLiveSports(league, sport, season) }, Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Refresh, "Skorları yenile", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("FUTBOL" to "Futbol", "BASKETBOL" to "Basketbol").forEach { (key, label) ->
                val color by animateColorAsState(if (sport == key) MaterialTheme.colorScheme.surface else Color.Transparent, animationSpec = if (LocalReducedMotion.current) snap() else tween(180), label = "sport")
                Surface(color = color, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).clickable { sport = key }) {
                    Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (key == "FUTBOL") Icons.Outlined.SportsSoccer else Icons.Outlined.SportsBasketball, null, tint = if (sport == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (sport == key) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            leagues.forEach { (key, name) ->
                SportChip(name, league == key) { league = key }
            }
        }
        val tabs = listOf("Maçlar", "Puan durumu", if (sport == "FUTBOL") "Gol krallığı" else "Sayı liderleri", "Fikstür", "Bildirimler")
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 18.dp, divider = {}) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, style = MaterialTheme.typography.labelLarge, color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) })
            }
        }
        if (selected && ((snapshot.isLoading && matches.isEmpty()) || (tab in 1..2 && snapshot.statsLoading))) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0, 3 -> FixturesList(matches, alerts, tab == 3, ::toggleAlert, { repository.fetchMatchLineups(it) })
                1 -> StandingsList(if (selected) snapshot.standings else emptyList(), sport, preferences.followedTeams, selected && snapshot.statsLoading, repository::toggleFollowTeam)
                2 -> ScorersList(if (selected) snapshot.topScorers else emptyList(), sport, selected && snapshot.statsLoading)
                4 -> MatchEventsFeed(matches)
            }
        }
    }
}

@Composable
private fun SportChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(11.dp), modifier = Modifier.clickable(onClick = onClick),
    ) { Text(label, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), maxLines = 1) }
}

@Composable
private fun LiveDot() {
    val alpha = if (LocalReducedMotion.current) 1f else {
        val transition = rememberInfiniteTransition(label = "live")
        val animated by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "live_dot")
        animated
    }
    Box(Modifier.size(5.dp).clip(CircleShape).background(CizgiColor.Red.copy(alpha = alpha)))
}

@Composable
private fun FixturesList(matches: List<SportsMatch>, alerts: Set<String>, fixtureMode: Boolean, onToggleAlert: (SportsMatch) -> Unit, onLineups: suspend (SportsMatch) -> MatchLineups?) {
    val ordered = remember(matches, fixtureMode) {
        if (fixtureMode) matches.sortedBy { it.kickoffAtEpochMs }
        else matches.sortedWith(compareBy<SportsMatch> { when (it.status) { MatchStatus.LIVE -> 0; MatchStatus.UPCOMING -> 1; MatchStatus.FINISHED -> 2 } }
            .thenBy { if (it.status == MatchStatus.FINISHED) -it.kickoffAtEpochMs else it.kickoffAtEpochMs })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val groups = ordered.groupBy { match -> matchDateLabel(match) }
        groups.forEach { (date, games) ->
            if (date.isNotBlank()) item(key = "date_" + date) { Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) }
            items(games, key = { it.id }) { match -> MatchCard(match, match.id in alerts, { onToggleAlert(match) }, onLineups) }
        }
    }
}

private fun matchDateLabel(match: SportsMatch): String {
    if (match.kickoffAtEpochMs <= 0) return ""
    val date = Instant.ofEpochMilli(match.kickoffAtEpochMs).atZone(ZoneId.of("Europe/Istanbul")).toLocalDate()
    val today = LocalDate.now(ZoneId.of("Europe/Istanbul"))
    return when (date) { today -> "Bugün"; today.plusDays(1) -> "Yarın"; today.minusDays(1) -> "Dün"; else -> date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("tr-TR"))) }
}

@Composable
private fun MatchCard(match: SportsMatch, alerted: Boolean, onAlert: () -> Unit, onLineups: suspend (SportsMatch) -> MatchLineups?) {
    var expanded by rememberSaveable(match.id) { mutableStateOf(false) }
    var lineups by remember(match.id) { mutableStateOf<MatchLineups?>(null) }
    var loading by remember(match.id) { mutableStateOf(false) }
    LaunchedEffect(match.id, expanded) {
        if (expanded && lineups == null) {
            loading = true
            try { lineups = onLineups(match) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
            finally { loading = false }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().then(if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize()).testTag("match_card_" + match.id),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(match.round, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                Surface(color = if (match.status == MatchStatus.LIVE) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (match.status == MatchStatus.LIVE) LiveDot()
                        Text(if (match.status == MatchStatus.UPCOMING && match.statusLabel in setOf("Başlamadı", "")) match.kickoffTime else match.statusLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (match.status == MatchStatus.LIVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onAlert, Modifier.padding(start = 4.dp).size(27.dp)) {
                    Icon(if (alerted) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsNone, if (alerted) "Maç bildirimini kapat" else "Maç bildirimi aç", tint = if (alerted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClubName(match.homeTeam, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 64.dp)) {
                    Text(if (match.status == MatchStatus.UPCOMING) "–" else (match.homeScore?.toString() ?: "–") + " : " + (match.awayScore?.toString() ?: "–"), fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                ClubName(match.awayTeam, Modifier.weight(1f))
            }
            MotionVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                        lineups != null && match.sportType == "FUTBOL" -> FootballLineupView(lineups!!)
                        lineups != null -> BasketballRoster(lineups!!)
                        else -> Text("Kadro henüz paylaşılmadı.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val stats = buildList {
                        if (match.possessionHome != null && match.possessionAway != null) add(Triple("Topla oynama", match.possessionHome, match.possessionAway))
                        if (match.shotsHome != null && match.shotsAway != null) add(Triple("Şut", match.shotsHome, match.shotsAway))
                        if (match.shotsOnTargetHome != null && match.shotsOnTargetAway != null) add(Triple("İsabetli şut", match.shotsOnTargetHome, match.shotsOnTargetAway))
                        if (match.cornersHome != null && match.cornersAway != null) add(Triple("Korner", match.cornersHome, match.cornersAway))
                    }
                    if (stats.isNotEmpty()) {
                        Text("Maç istatistikleri", style = MaterialTheme.typography.titleSmall)
                        stats.forEach { (title, home, away) ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row { Text(home.toString(), style = MaterialTheme.typography.labelLarge); Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(away.toString(), style = MaterialTheme.typography.labelLarge) }
                                LinearProgressIndicator(progress = { home.toFloat() / maxOf(1, home + away) }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                    }
                    if (match.events.isNotEmpty()) EventRows(match.events)
                    if (match.stadium.isNotBlank()) Text(match.stadium, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (match.referee.isNotBlank()) Text("Hakem · " + match.referee, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) "Kapat" else "Maç ayrıntıları", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ClubName(name: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(11.dp)) {
            Text(name.split(" ").filter(String::isNotBlank).take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT), modifier = Modifier.padding(9.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FootballLineupView(lineups: MatchLineups) {
    var home by remember(lineups) { mutableStateOf(true) }
    val team = if (home) lineups.home else lineups.away
    val positions = remember(team) { FootballFormation.arrange(team) }
    val starters = team.players.filter { it.starter }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SportChip(lineups.home.team, home) { home = true }
            SportChip(lineups.away.team, !home) { home = false }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (starters.isNotEmpty()) "İlk 11" else "Kadro", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (starters.isNotEmpty() && team.formation.isNotBlank()) Text(team.formation, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (positions.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(21.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF207758), Color(0xFF15523E))))
                repeat(10) { stripe ->
                    if (stripe % 2 == 0) drawRect(Color.White.copy(alpha = 0.025f), Offset(0f, size.height * stripe / 10f), Size(size.width, size.height / 10f))
                }
                val margin = 12.dp.toPx()
                val line = Color.White.copy(alpha = 0.36f)
                val stroke = Stroke(1.dp.toPx())
                drawRect(line, Offset(margin, margin), Size(size.width - 2 * margin, size.height - 2 * margin), style = stroke)
                drawLine(line, Offset(margin, size.height / 2f), Offset(size.width - margin, size.height / 2f), 1.dp.toPx())
                drawCircle(line, size.width * 0.14f, Offset(size.width / 2f, size.height / 2f), style = stroke)
                val areaWidth = size.width * 0.58f
                val areaHeight = size.height * 0.14f
                drawRect(line, Offset((size.width - areaWidth) / 2f, margin), Size(areaWidth, areaHeight), style = stroke)
                drawRect(line, Offset((size.width - areaWidth) / 2f, size.height - margin - areaHeight), Size(areaWidth, areaHeight), style = stroke)
                drawRect(line, Offset(size.width * 0.37f, margin), Size(size.width * 0.26f, size.height * 0.045f), style = stroke)
                drawRect(line, Offset(size.width * 0.37f, size.height - margin - size.height * 0.045f), Size(size.width * 0.26f, size.height * 0.045f), style = stroke)
            }
            positions.forEach { position ->
                Column(
                    Modifier.offset(x = maxWidth * position.x - 27.dp, y = maxHeight * position.y - 18.dp).width(54.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Jersey(position.player.jerseyNumber)
                    Text(position.player.name, color = Color.White, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else RosterTiles(if (starters.isNotEmpty()) starters else team.players)
        val reserves = if (starters.isNotEmpty()) team.players.filterNot { it.starter } else emptyList()
        if (reserves.isNotEmpty()) {
            Text("Yedekler", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RosterTiles(reserves)
        }
    }
}

@Composable
private fun Jersey(number: String) {
    Box(Modifier.size(width = 30.dp, height = 29.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val shirt = Path().apply {
                moveTo(size.width * 0.25f, 0f); lineTo(0f, size.height * 0.18f); lineTo(size.width * 0.10f, size.height * 0.43f)
                lineTo(size.width * 0.25f, size.height * 0.37f); lineTo(size.width * 0.25f, size.height)
                lineTo(size.width * 0.75f, size.height); lineTo(size.width * 0.75f, size.height * 0.37f)
                lineTo(size.width * 0.90f, size.height * 0.43f); lineTo(size.width, size.height * 0.18f)
                lineTo(size.width * 0.75f, 0f); lineTo(size.width * 0.61f, size.height * 0.10f)
                lineTo(size.width * 0.39f, size.height * 0.10f); close()
            }
            drawPath(shirt, Color(0xFFF3F7FA))
        }
        Text(number.ifBlank { "–" }, color = CizgiColor.Navy, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun RosterTiles(players: List<LineupPlayer>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        players.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { player ->
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(player.jerseyNumber.ifBlank { "–" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(22.dp))
                        Text(player.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BasketballRoster(lineups: MatchLineups) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(lineups.home, lineups.away).forEach { team ->
            Text(team.team, style = MaterialTheme.typography.titleSmall)
            RosterTiles(team.players.sortedByDescending { it.starter })
        }
    }
}

@Composable
private fun StandingsList(rows: List<LeagueStanding>, sport: String, followed: Set<String>, loading: Boolean, onFollow: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rows.isEmpty() && !loading) item { SmallEmpty("Puan durumu henüz paylaşılmadı.") }
        rows.groupBy { it.groupName.orEmpty() }.forEach { (group, teams) ->
            if (group.isNotBlank()) item(key = "group_" + group) { Text(group, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 10.dp)) }
            item(key = "heading_" + group) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#", Modifier.width(24.dp), style = MaterialTheme.typography.labelSmall)
                    Text("Takım", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    (if (sport == "FUTBOL") listOf("O", "G", "B", "M") else listOf("O", "G", "M")).forEach { Text(it, Modifier.width(22.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                    if (sport == "FUTBOL") Text("AV", Modifier.width(30.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                    Text(if (sport == "FUTBOL") "P" else "G%", Modifier.width(34.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(teams, key = { group + it.team }) { team ->
                Surface(color = if (followed.any { team.team.contains(it, true) }) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(team.rank.toString(), Modifier.width(24.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f).clickable { onFollow(team.team) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(team.team, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(if (team.team in followed) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Takımı takip et", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            if (!team.qualificationNote.isNullOrBlank()) Text(team.qualificationNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        (if (sport == "FUTBOL") listOf(team.played, team.won, team.drawn, team.lost) else listOf(team.played, team.won, team.lost)).forEach { Text(it.toString(), Modifier.width(22.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
                        if (sport == "FUTBOL") Text(team.goalDiff.toString(), Modifier.width(30.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                        val value = if (sport == "FUTBOL") team.points.toString() else team.winPercentage?.let { String.format(Locale.ROOT, "%.0f", if (it <= 1.0) it * 100 else it) } ?: "–"
                        Text(value, Modifier.width(34.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScorersList(rows: List<TopScorer>, sport: String, loading: Boolean) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (rows.isEmpty() && !loading) item { SmallEmpty(if (sport == "FUTBOL") "Gol krallığı henüz paylaşılmadı." else "Oyuncu liderleri henüz paylaşılmadı.") }
        items(rows, key = { it.team + it.player }) { player ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(17.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(player.rank.toString().padStart(2, '0'), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(player.player, style = MaterialTheme.typography.titleSmall)
                        Text(player.team, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(player.valueLabel.ifBlank { player.count.toString() }, style = MaterialTheme.typography.titleLarge)
                        Text(if (sport == "FUTBOL") "gol" else "sayı / maç", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchEventsFeed(matches: List<SportsMatch>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val games = matches.filter { it.events.isNotEmpty() }
        items(games, key = { it.id }) { match ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(match.homeTeam + " · " + match.awayTeam, style = MaterialTheme.typography.titleSmall)
                    EventRows(match.events)
                }
            }
        }
        if (games.isEmpty()) item { SmallEmpty("Bu maçlar için henüz olay akışı bulunmuyor.") }
    }
}

@Composable
private fun EventRows(events: List<MatchEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        events.sortedByDescending { it.minute }.take(20).forEach { event ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(event.minute.toString() + "′", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(32.dp))
                Icon(when (event.type) { "GOL", "PENALTI" -> Icons.Outlined.SportsSoccer; else -> Icons.Outlined.Square }, null, modifier = Modifier.size(15.dp), tint = when (event.type) { "SARI" -> CizgiColor.Gold; "KIRMIZI" -> CizgiColor.Red; else -> MaterialTheme.colorScheme.onSurface })
                Column(Modifier.weight(1f)) {
                    Text(event.player, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(event.team, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SmallEmpty(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center)
}
