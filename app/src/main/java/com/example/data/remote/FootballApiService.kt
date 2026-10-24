package com.example.data.remote

import com.example.BuildConfig
import com.example.model.LeagueStanding
import com.example.model.MatchEvent
import com.example.model.MatchStatus
import com.example.model.SportsMatch
import com.example.model.TopScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FootballApiService(private val httpClient: OkHttpClient) {

    private val apiKey = BuildConfig.API_FOOTBALL_KEY.trim()
    private val baseUrl = "https://v3.football.api-sports.io"
    private val standingsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<LeagueStanding>>>()
    private val scorersCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<TopScorer>>>()
    private val espnScheduleCache = java.util.concurrent.ConcurrentHashMap<String, EspnFixtureCache>()
    private val espnLiveEventCache = java.util.concurrent.ConcurrentHashMap<String, List<MatchEvent>>()

    private data class EspnFixtureCache(
        val scheduleFetchedAtMs: Long,
        val liveFetchedAtMs: Long,
        val matches: List<SportsMatch>,
    )

    private fun leagueId(competition: String): Int? = when (competition) {
        "UEFA Şampiyonlar Ligi" -> 2
        "İngiltere Premier Lig" -> 39
        "İspanya La Liga" -> 140
        "Trendyol Süper Lig" -> 203
        else -> null
    }

    private fun hasApiKey(): Boolean = apiKey.isNotBlank() && !apiKey.startsWith("YOUR_")

    fun topScorersUnavailableMessage(sportType: String): String = when {
        sportType != "FUTBOL" -> "Bu branş için gol krallığı verisi sunulmuyor."
        !hasApiKey() -> "Gol krallığı verisi için API_FOOTBALL_KEY ayarlanmalı."
        else -> "Gol krallığı verisi şu anda alınamadı; bağlantıyı veya API kotasını kontrol et."
    }

    private fun currentSeasonYear(): Int {
        val today = LocalDate.now()
        return if (today.monthValue < 7) today.year - 1 else today.year
    }

    suspend fun fetchFixtures(competition: String, sportType: String = "FUTBOL"): List<SportsMatch> = withContext(Dispatchers.IO) {
        when (sportType) {
            "BASKETBOL" -> if (competition == "NBA") fetchBasketballFixtures(competition) else emptyList()
            "FUTBOL" -> {
                val id = leagueId(competition) ?: return@withContext emptyList()
                if (hasApiKey()) {
                    val today = LocalDate.now()
                    val url = "$baseUrl/fixtures?league=$id&season=${currentSeasonYear()}&from=${today.minusDays(2)}&to=${today.plusDays(7)}&timezone=Europe%2FIstanbul"
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("x-apisports-key", apiKey)
                            .build()
                        httpClient.newCall(request).execute().use { response ->
                            val body = response.body?.string()
                            if (response.isSuccessful && !body.isNullOrBlank()) {
                                val matches = parseApiSportsFixtures(
                                    defaultComp = competition,
                                    jsonStr = body,
                                    filterLeagueId = id,
                                    sportType = "FUTBOL",
                                )
                                if (matches.isNotEmpty()) return@withContext sortMatches(matches)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                    }
                }
                fetchEspnFootballFixtures(competition)
            }
            else -> emptyList()
        }
    }

    suspend fun fetchStandings(competition: String, sportType: String = "FUTBOL"): List<LeagueStanding> = withContext(Dispatchers.IO) {
        if (sportType != "FUTBOL") return@withContext emptyList()
        val id = leagueId(competition) ?: return@withContext emptyList()
        val cacheKey = "$id-${currentSeasonYear()}"
        standingsCache[cacheKey]?.let { (fetchedAt, cached) ->
            if (System.currentTimeMillis() - fetchedAt < STANDINGS_CACHE_MS) return@withContext cached
        }

        if (hasApiKey()) {
            try {
                val url = "$baseUrl/standings?league=$id&season=${currentSeasonYear()}"
                val request = Request.Builder()
                    .url(url)
                    .header("x-apisports-key", apiKey)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful && !body.isNullOrBlank()) {
                        val standings = parseApiSportsStandings(body)
                        if (standings.isNotEmpty()) {
                            standingsCache[cacheKey] = System.currentTimeMillis() to standings
                            return@withContext standings
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }

        val standings = fetchEspnFallbackStandings(competition)
        if (standings.isNotEmpty()) standingsCache[cacheKey] = System.currentTimeMillis() to standings
        standings
    }

    suspend fun fetchTopScorers(competition: String, sportType: String = "FUTBOL"): List<TopScorer> = withContext(Dispatchers.IO) {
        if (sportType != "FUTBOL" || !hasApiKey()) {
            return@withContext emptyList()
        }

        val id = leagueId(competition) ?: return@withContext emptyList()
        val cacheKey = "$id-${currentSeasonYear()}"
        scorersCache[cacheKey]?.let { (fetchedAt, cached) ->
            if (System.currentTimeMillis() - fetchedAt < STANDINGS_CACHE_MS) return@withContext cached
        }

        val scorers = mutableListOf<TopScorer>()
        try {
            val url = "$baseUrl/players/topscorers?league=$id&season=${currentSeasonYear()}"
            val request = Request.Builder()
                .url(url)
                .header("x-apisports-key", apiKey)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val entries = JSONObject(body).optJSONArray("response") ?: return@withContext emptyList()
                        for (i in 0 until entries.length()) {
                            val entry = entries.optJSONObject(i) ?: continue
                            val player = entry.optJSONObject("player")?.optString("name").orEmpty()
                            val stats = entry.optJSONArray("statistics")?.optJSONObject(0) ?: continue
                            val team = stats.optJSONObject("team")?.optString("name").orEmpty()
                            val goals = stats.optJSONObject("goals") ?: JSONObject()
                            val goalCount = goals.optInt("total", 0)
                            if (player.isBlank() || team.isBlank() || goalCount <= 0) continue
                            val appearances = stats.optJSONObject("games")?.optInt("appearences", 0) ?: 0
                            scorers.add(
                                TopScorer(
                                    rank = scorers.size + 1,
                                    player = player,
                                    team = team,
                                    count = goalCount,
                                    secondaryCount = goals.optInt("assists", 0),
                                    matches = appearances,
                                    type = "GOL",
                                )
                            )
                        }
                        if (scorers.isNotEmpty()) scorersCache[cacheKey] = System.currentTimeMillis() to scorers.toList()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {}
        scorers
    }

    private fun sortMatches(matches: List<SportsMatch>): List<SportsMatch> = matches
        .distinctBy { it.id }
        .sortedWith(compareBy<SportsMatch> { it.kickoffAtEpochMs }.thenBy { it.homeTeam })

    private fun parseApiSportsFixtures(
        defaultComp: String,
        jsonStr: String,
        filterLeagueId: Int?,
        sportType: String
    ): List<SportsMatch> {
        val list = mutableListOf<SportsMatch>()
        try {
            val root = JSONObject(jsonStr)
            val responseArray = root.optJSONArray("response") ?: return list

            for (i in 0 until responseArray.length()) {
                val item = responseArray.getJSONObject(i)
                val fixture = item.getJSONObject("fixture")
                val league = item.getJSONObject("league")
                val teams = item.getJSONObject("teams")
                val goals = item.optJSONObject("goals")
                val eventsArray = item.optJSONArray("events")

                val leagueId = league.optInt("id", 0)
                if (filterLeagueId != null && leagueId != filterLeagueId) continue

                val id = fixture.optInt("id", i).toString()
                val statusObj = fixture.getJSONObject("status")
                val shortStatus = statusObj.optString("short", "NS")
                val elapsed = statusObj.optInt("elapsed", 0)
                val round = league.optString("round", "Hafta")

                val homeObj = teams.getJSONObject("home")
                val awayObj = teams.getJSONObject("away")
                val homeName = homeObj.optString("name", "Ev Sahibi")
                val awayName = awayObj.optString("name", "Deplasman")

                val homeScore = if (goals != null && !goals.isNull("home")) goals.optInt("home") else null
                val awayScore = if (goals != null && !goals.isNull("away")) goals.optInt("away") else null

                val isLive = shortStatus in listOf("1H", "2H", "HT", "ET", "P", "LIVE")
                val isFinished = shortStatus in listOf("FT", "AET", "PEN")

                val status = when {
                    isLive -> MatchStatus.LIVE
                    isFinished -> MatchStatus.FINISHED
                    else -> MatchStatus.UPCOMING
                }

                val statusLabel = when {
                    shortStatus == "HT" -> "İY"
                    isLive -> if (elapsed > 0) "$elapsed' CANLI" else "CANLI"
                    isFinished -> "MS"
                    else -> fixture.optString("date", "").substringAfter("T").take(5).ifBlank { "20:00" }
                }
                val kickoffAt = fixture.optString("date", "")
                val (kickoffAtEpochMs, kickoffTime) = formatKickoff(kickoffAt)

                val events = mutableListOf<MatchEvent>()
                var liveAction = ""
                if (eventsArray != null) {
                    for (e in 0 until eventsArray.length()) {
                        val ev = eventsArray.getJSONObject(e)
                        val evTime = ev.optJSONObject("time")?.optInt("elapsed", 0) ?: 0
                        val evTeam = ev.optJSONObject("team")?.optString("name", "") ?: ""
                        val evPlayer = ev.optJSONObject("player")?.optString("name", "") ?: ""
                        val evType = ev.optString("type", "")
                        val mappedType = when (evType) {
                            "Goal" -> "GOL"
                            "Card" -> if (ev.optString("detail", "").contains("Red", ignoreCase = true)) "KIRMIZI" else "SARI"
                            else -> evType.uppercase()
                        }
                        events.add(
                            MatchEvent(
                                minute = evTime,
                                team = evTeam,
                                player = evPlayer,
                                type = mappedType,
                            )
                        )
                        if (mappedType == "GOL") {
                            liveAction = "⚽ GOL! $evTime' $evPlayer ($evTeam)"
                        }
                    }
                }

                list.add(
                    SportsMatch(
                        id = id,
                        competition = league.optString("name", defaultComp),
                        round = round,
                        homeTeam = homeName,
                        awayTeam = awayName,
                        homeScore = homeScore,
                        awayScore = awayScore,
                        minute = if (elapsed > 0) elapsed else null,
                        status = status,
                        statusLabel = statusLabel,
                        kickoffTime = kickoffTime,
                        kickoffAtEpochMs = kickoffAtEpochMs,
                        events = events,
                        sportType = sportType,
                        liveAction = liveAction,
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseApiSportsStandings(jsonStr: String): List<LeagueStanding> {
        val list = mutableListOf<LeagueStanding>()
        try {
            val root = JSONObject(jsonStr)
            val responseArray = root.optJSONArray("response") ?: return list
            if (responseArray.length() == 0) return list

            val leagueObj = responseArray.getJSONObject(0).optJSONObject("league") ?: return list
            val standingsArray = leagueObj.optJSONArray("standings") ?: return list
            if (standingsArray.length() == 0) return list

            val leagueGroup = standingsArray.getJSONArray(0)
            for (i in 0 until leagueGroup.length()) {
                val row = leagueGroup.getJSONObject(i)
                val rank = row.optInt("rank", i + 1)
                val teamObj = row.getJSONObject("team")
                val teamName = teamObj.optString("name", "Takım")
                val points = row.optInt("points", 0)
                val goalsDiff = row.optInt("goalsDiff", 0)
                val form = row.optString("form", "")
                    .replace("W", "G-")
                    .replace("D", "B-")
                    .replace("L", "M-")
                    .trimEnd('-')
                    .ifBlank { "—" }

                val all = row.optJSONObject("all") ?: JSONObject()
                val played = all.optInt("played", 0)
                val won = all.optInt("win", 0)
                val drawn = all.optInt("draw", 0)
                val lost = all.optInt("lose", 0)
                val goals = all.optJSONObject("goals") ?: JSONObject()

                list.add(
                    LeagueStanding(
                        rank = rank,
                        team = teamName,
                        played = played,
                        won = won,
                        drawn = drawn,
                        lost = lost,
                        goalDiff = goalsDiff,
                        points = points,
                        form = form,
                        goalsFor = goals.optInt("for", 0),
                        goalsAgainst = goals.optInt("against", 0),
                        qualificationNote = row.optString("description").takeIf(String::isNotBlank),
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private suspend fun fetchEspnFootballFixtures(competition: String): List<SportsMatch> {
        val leagueSlug = when (competition) {
            "UEFA Şampiyonlar Ligi" -> "uefa.champions"
            "İngiltere Premier Lig" -> "eng.1"
            "İspanya La Liga" -> "esp.1"
            "Trendyol Süper Lig" -> "tur.1"
            else -> return emptyList()
        }

        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        var cache = espnScheduleCache[competition]
        if (cache == null || now - cache.scheduleFetchedAtMs >= ESPN_SCHEDULE_REFRESH_MS) {
            val semaphore = Semaphore(4)
            val schedule = coroutineScope {
                (0L..7L).map { offset ->
                    async {
                        semaphore.withPermit {
                            fetchEspnScoreboard(competition, leagueSlug, today.plusDays(offset))
                        }
                    }
                }.awaitAll().flatten()
            }
            cache = EspnFixtureCache(now, now, sortMatches(schedule))
            espnScheduleCache[competition] = cache
        } else if (now - cache.liveFetchedAtMs >= ESPN_LIVE_REFRESH_MS) {
            val currentDayMatches = fetchEspnScoreboard(competition, leagueSlug, today)
            val refreshed = cache.matches.filterNot { sameLocalDay(it.kickoffAtEpochMs, today) } + currentDayMatches
            cache = cache.copy(liveFetchedAtMs = now, matches = sortMatches(refreshed))
            espnScheduleCache[competition] = cache
        }

        val result = cache.matches
        val liveMatches = result.filter { it.status == MatchStatus.LIVE }.take(3)
        val summariesToFetch = if (liveMatches.isNotEmpty()) {
            liveMatches
        } else {
            result.asSequence()
                .filter { it.status == MatchStatus.FINISHED && !espnLiveEventCache.containsKey(it.id) }
                .sortedByDescending { it.kickoffAtEpochMs }
                .take(3)
                .toList()
        }
        val eventsByMatch = mutableMapOf<String, List<MatchEvent>>()
        for (match in summariesToFetch) {
            val events = fetchEspnMatchEvents(leagueSlug, match.id)
            eventsByMatch[match.id] = events
            if (match.status == MatchStatus.FINISHED) espnLiveEventCache[match.id] = events
        }
        return sortMatches(result.map { match ->
            val events = eventsByMatch[match.id] ?: espnLiveEventCache[match.id].orEmpty()
            val lastGoal = events.lastOrNull { it.type == "GOL" }
            match.copy(
                events = events,
                liveAction = lastGoal?.let { "⚽ GOL! ${it.minute}' ${it.player} (${it.team})" }
                    ?: match.liveAction,
            )
        })
    }

    private fun fetchEspnScoreboard(competition: String, leagueSlug: String, date: LocalDate): List<SportsMatch> {
        val result = mutableListOf<SportsMatch>()
        try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$leagueSlug/scoreboard?limit=100&dates=${date.format(DateTimeFormatter.BASIC_ISO_DATE)}"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) return result
                val events = JSONObject(body).optJSONArray("events") ?: return result
                for (i in 0 until events.length()) {
                    val event = events.optJSONObject(i) ?: continue
                    val id = event.optString("id", "m-$i")
                    val competitionArray = event.optJSONArray("competitions") ?: continue
                    if (competitionArray.length() == 0) continue
                    val competitionObject = competitionArray.optJSONObject(0) ?: continue
                    val statusObject = competitionObject.optJSONObject("status") ?: JSONObject()
                    val statusType = statusObject.optJSONObject("type") ?: JSONObject()
                    val state = statusType.optString("state", "pre")
                    val clock = statusObject.optDouble("clock", 0.0).toInt()
                    val competitors = competitionObject.optJSONArray("competitors") ?: continue
                    var homeName = "Ev Sahibi"
                    var awayName = "Deplasman"
                    var homeScore: Int? = null
                    var awayScore: Int? = null
                    for (c in 0 until competitors.length()) {
                        val competitor = competitors.optJSONObject(c) ?: continue
                        val isHome = competitor.optString("homeAway") == "home"
                        val name = competitor.optJSONObject("team")?.optString("displayName", "Takım") ?: "Takım"
                        val score = competitor.optString("score", "").toIntOrNull()
                        if (isHome) {
                            homeName = name
                            homeScore = score
                        } else {
                            awayName = name
                            awayScore = score
                        }
                    }
                    val status = when (state) {
                        "in" -> MatchStatus.LIVE
                        "post" -> MatchStatus.FINISHED
                        else -> MatchStatus.UPCOMING
                    }
                    val kickoff = formatKickoff(event.optString("date"))
                    val round = competitionObject.optJSONObject("round")
                        ?.optString("displayName")?.takeIf(String::isNotBlank) ?: "Fikstür"
                    result.add(
                        SportsMatch(
                            id = id,
                            competition = competition,
                            round = round,
                            homeTeam = homeName,
                            awayTeam = awayName,
                            homeScore = homeScore,
                            awayScore = awayScore,
                            minute = if (clock > 0) clock else null,
                            status = status,
                            statusLabel = when (state) {
                                "in" -> if (clock > 0) "$clock' CANLI" else "CANLI"
                                "post" -> "MS"
                                else -> "Başlamadı"
                            },
                            kickoffTime = kickoff.second,
                            kickoffAtEpochMs = kickoff.first,
                            sportType = "FUTBOL",
                            liveAction = if (status == MatchStatus.LIVE && homeScore != null && awayScore != null) {
                                "⚽ Canlı Mücadele: $homeScore - $awayScore"
                            } else "",
                        )
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
        return result
    }

    private fun fetchEspnMatchEvents(leagueSlug: String, eventId: String): List<MatchEvent> {
        val result = mutableListOf<MatchEvent>()
        try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$leagueSlug/summary?event=$eventId"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) return result
                val keyEvents = JSONObject(body).optJSONArray("keyEvents") ?: return result
                for (i in 0 until keyEvents.length()) {
                    val event = keyEvents.optJSONObject(i) ?: continue
                    val type = event.optJSONObject("type") ?: JSONObject()
                    val typeName = type.optString("type").lowercase(Locale.ROOT)
                    val typeText = type.optString("text").lowercase(Locale.ROOT)
                    val mappedType = when {
                        typeName.contains("goal") || typeText == "goal" || typeText == "penalty - scored" -> "GOL"
                        typeName.contains("red-card") || typeText.contains("red card") -> "KIRMIZI"
                        typeName.contains("yellow-card") || typeText.contains("yellow card") -> "SARI"
                        else -> continue
                    }
                    val clock = event.optJSONObject("clock") ?: JSONObject()
                    val minuteText = clock.optString("displayValue")
                    val minute = Regex("\\d+").find(minuteText)?.value?.toIntOrNull()
                        ?: (clock.optDouble("value", 0.0) / 60.0).toInt()
                    val team = event.optJSONObject("team")?.optString("displayName").orEmpty()
                    val participants = event.optJSONArray("participants")
                    val player = participants?.optJSONObject(0)?.optJSONObject("athlete")
                        ?.optString("displayName").orEmpty()
                    if (minute > 0 && team.isNotBlank()) {
                        result.add(MatchEvent(minute = minute, team = team, player = player.ifBlank { team }, type = mappedType))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun sameLocalDay(timestamp: Long, date: LocalDate): Boolean = timestamp > 0L &&
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == date

    private fun formatKickoff(value: String): Pair<Long, String> {
        val instant = try {
            Instant.parse(value)
        } catch (_: Exception) {
            return 0L to "Saat bekleniyor"
        }
        val zone = ZoneId.systemDefault()
        val local = instant.atZone(zone)
        val format = if (local.toLocalDate() == LocalDate.now(zone)) {
            DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("tr-TR"))
        } else {
            DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("tr-TR"))
        }
        return instant.toEpochMilli() to local.format(format)
    }

    private fun fetchBasketballFixtures(competition: String): List<SportsMatch> {
        val result = mutableListOf<SportsMatch>()
        try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (body != null) {
                    val root = JSONObject(body)
                    val events = root.optJSONArray("events") ?: return result
                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        val id = ev.optString("id", "b-$i")
                        val compArray = ev.optJSONArray("competitions") ?: continue
                        if (compArray.length() == 0) continue
                        val compObj = compArray.getJSONObject(0)
                        val statusObj = compObj.optJSONObject("status") ?: JSONObject()
                        val typeObj = statusObj.optJSONObject("type") ?: JSONObject()
                        val state = typeObj.optString("state", "pre")
                        val detail = typeObj.optString("detail", "Başlamadı")

                        val competitors = compObj.optJSONArray("competitors") ?: continue
                        var homeName = "Ev Sahibi"
                        var awayName = "Deplasman"
                        var homeScore: Int? = null
                        var awayScore: Int? = null

                        for (c in 0 until competitors.length()) {
                            val compTeam = competitors.getJSONObject(c)
                            val isHome = compTeam.optString("homeAway") == "home"
                            val name = compTeam.optJSONObject("team")?.optString("displayName", "Takım") ?: "Takım"
                            val score = compTeam.optString("score", "").toIntOrNull()
                            if (isHome) {
                                homeName = name
                                homeScore = score
                            } else {
                                awayName = name
                                awayScore = score
                            }
                        }

                        val status = when (state) {
                            "in" -> MatchStatus.LIVE
                            "post" -> MatchStatus.FINISHED
                            else -> MatchStatus.UPCOMING
                        }

                        val statusLabel = when (state) {
                            "in" -> "CANLI · ${detail.take(8)}"
                            "post" -> "MS"
                            else -> detail.ifBlank { "Yakında" }
                        }

                        result.add(
                            SportsMatch(
                                id = id,
                                competition = "NBA",
                                round = "Normal Sezon",
                                homeTeam = homeName,
                                awayTeam = awayName,
                                homeScore = homeScore,
                                awayScore = awayScore,
                                minute = null,
                                status = status,
                                statusLabel = statusLabel,
                                kickoffTime = detail,
                                events = emptyList(),
                                sportType = "BASKETBOL",
                                liveAction = if (status == MatchStatus.LIVE) "🏀 3 Sayılık Basket!" else "",
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun fetchEspnFallbackStandings(competition: String): List<LeagueStanding> {
        val leagueSlug = when (competition) {
            "UEFA Şampiyonlar Ligi" -> "uefa.champions"
            "İngiltere Premier Lig" -> "eng.1"
            "İspanya La Liga" -> "esp.1"
            "Trendyol Süper Lig" -> "tur.1"
            else -> return emptyList()
        }
        val result = mutableListOf<LeagueStanding>()
        try {
            val url = "https://site.api.espn.com/apis/v2/sports/soccer/$leagueSlug/standings"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (body != null) {
                    val root = JSONObject(body)
                    val children = root.optJSONArray("children") ?: return result
                    if (children.length() == 0) return result
                    val child = children.getJSONObject(0)
                    val standingsObj = child.optJSONObject("standings") ?: return result
                    val entries = standingsObj.optJSONArray("entries") ?: return result

                    for (i in 0 until entries.length()) {
                        val entry = entries.getJSONObject(i)
                        val teamObj = entry.optJSONObject("team") ?: continue
                        val teamName = teamObj.optString("displayName", "Takım")
                        val statsArray = entry.optJSONArray("stats") ?: continue

                        var rank = i + 1
                        var gamesPlayed = 0
                        var wins = 0
                        var ties = 0
                        var losses = 0
                        var gd = 0
                        var points = 0
                        var goalsFor = 0
                        var goalsAgainst = 0
                        val qualificationNote = entry.optJSONObject("note")
                            ?.optString("description")?.takeIf(String::isNotBlank)

                        for (s in 0 until statsArray.length()) {
                            val stat = statsArray.getJSONObject(s)
                            when (stat.optString("name")) {
                                "rank" -> rank = stat.optDouble("value", (i + 1).toDouble()).toInt()
                                "gamesPlayed" -> gamesPlayed = stat.optDouble("value", 0.0).toInt()
                                "wins" -> wins = stat.optDouble("value", 0.0).toInt()
                                "ties" -> ties = stat.optDouble("value", 0.0).toInt()
                                "losses" -> losses = stat.optDouble("value", 0.0).toInt()
                                "pointDifferential" -> gd = stat.optDouble("value", 0.0).toInt()
                                "points" -> points = stat.optDouble("value", 0.0).toInt()
                                "pointsFor" -> goalsFor = stat.optDouble("value", 0.0).toInt()
                                "pointsAgainst" -> goalsAgainst = stat.optDouble("value", 0.0).toInt()
                            }
                        }

                        result.add(
                            LeagueStanding(
                                rank = rank,
                                team = teamName,
                                played = gamesPlayed,
                                won = wins,
                                drawn = ties,
                                lost = losses,
                                goalDiff = gd,
                                points = points,
                                form = "—",
                                goalsFor = goalsFor,
                                goalsAgainst = goalsAgainst,
                                qualificationNote = qualificationNote,
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private companion object {
        const val STANDINGS_CACHE_MS = 60 * 60_000L
        const val ESPN_SCHEDULE_REFRESH_MS = 30 * 60_000L
        const val ESPN_LIVE_REFRESH_MS = 60_000L
    }
}
