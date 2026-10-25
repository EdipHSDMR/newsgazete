package com.example.data.remote

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
import org.json.JSONArray
import java.time.LocalDate
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class FootballApiService(
    private val httpClient: OkHttpClient,
) {
    private val baseUrl = "${ApiGatewayConfig.BASE_URL}/v1/football"
    private val standingsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<LeagueStanding>>>()
    private val scorersCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<TopScorer>>>()
    private val espnScheduleCache = java.util.concurrent.ConcurrentHashMap<String, EspnFixtureCache>()
    private val espnLiveEventCache = java.util.concurrent.ConcurrentHashMap<String, List<MatchEvent>>()
    private val basketballLeadersCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<TopScorer>>>()
    private val euroLeagueGamesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<SportsMatch>>>()
    @Volatile private var apiFootballUnavailableReason: String? = null

    fun resetApiFootballState() {
        apiFootballUnavailableReason = null
        standingsCache.clear()
        scorersCache.clear()
    }

    private data class EspnFixtureCache(
        val scheduleFetchedAtMs: Long,
        val liveFetchedAtMs: Long,
        val matches: List<SportsMatch>,
    )

    private fun leagueId(competition: String): Int? = when (competition) {
        "UEFA Şampiyonlar Ligi" -> 2
        "UEFA Avrupa Ligi" -> 3
        "UEFA Konferans Ligi" -> 848
        "İngiltere Premier Lig" -> 39
        "İngiltere Championship" -> 40
        "Trendyol 1. Lig" -> 204
        "İspanya La Liga" -> 140
        "İspanya Segunda" -> 141
        "İtalya Serie A" -> 135
        "İtalya Serie B" -> 136
        "Almanya Bundesliga" -> 78
        "Almanya 2. Bundesliga" -> 79
        "Fransa Ligue 1" -> 61
        "Fransa Ligue 2" -> 62
        "İskoçya Premiership" -> 179
        "Belçika Pro League" -> 144
        "Suudi Arabistan Pro League" -> 307
        "Arjantin Primera División" -> 128
        "Hollanda Eredivisie" -> 88
        "Portekiz Primeira Liga" -> 94
        "Trendyol Süper Lig" -> 203
        "Brezilya Serie A" -> 71
        "MLS" -> 253
        "Japonya J1 League" -> 98
        "CONMEBOL Libertadores" -> 13
        "CONMEBOL Sudamericana" -> 11
        else -> null
    }

    private fun footballEspnSlug(competition: String): String? = when (competition) {
        "UEFA Şampiyonlar Ligi" -> "uefa.champions"
        "UEFA Avrupa Ligi" -> "uefa.europa"
        "UEFA Konferans Ligi" -> "uefa.europa.conf"
        "İngiltere Premier Lig" -> "eng.1"
        "İngiltere Championship" -> "eng.2"
        "Trendyol 1. Lig" -> "tur.2"
        "İspanya La Liga" -> "esp.1"
        "İspanya Segunda" -> "esp.2"
        "İtalya Serie A" -> "ita.1"
        "İtalya Serie B" -> "ita.2"
        "Almanya Bundesliga" -> "ger.1"
        "Almanya 2. Bundesliga" -> "ger.2"
        "Fransa Ligue 1" -> "fra.1"
        "Fransa Ligue 2" -> "fra.2"
        "İskoçya Premiership" -> "sco.1"
        "Belçika Pro League" -> "bel.1"
        "Suudi Arabistan Pro League" -> "ksa.1"
        "Arjantin Primera División" -> "arg.1"
        "Hollanda Eredivisie" -> "ned.1"
        "Portekiz Primeira Liga" -> "por.1"
        "Trendyol Süper Lig" -> "tur.1"
        "Brezilya Serie A" -> "bra.1"
        "MLS" -> "usa.1"
        "Japonya J1 League" -> "jpn.1"
        "CONMEBOL Libertadores" -> "conmebol.libertadores"
        "CONMEBOL Sudamericana" -> "conmebol.sudamericana"
        else -> null
    }

    private fun basketballEspnSlug(competition: String): String? = when (competition) {
        "EuroLeague" -> "euroleague"
        "NBA" -> "nba"
        "WNBA" -> "wnba"
        "NBA G League" -> "nba-development"
        "NCAA Erkekler" -> "mens-college-basketball"
        "NCAA Kadınlar" -> "womens-college-basketball"
        else -> null
    }

    private fun hasApiKey(): Boolean = baseUrl.isNotBlank()

    fun topScorersUnavailableMessage(sportType: String): String = when {
        sportType == "BASKETBOL" -> "Bu lig için güncel sayı lideri verisi henüz yayınlanmadı."
        sportType != "FUTBOL" -> "Bu branş için oyuncu liderliği verisi bulunmuyor."
        apiFootballUnavailableReason != null -> apiFootballUnavailableReason!!
        !hasApiKey() -> "Spor veri servisi yapılandırılıyor."
        else -> "Gol krallığı verisi şu anda alınamadı; bağlantıyı veya API kotasını kontrol et."
    }

    private fun rememberApiFootballIssue(json: String) {
        val errors = runCatching { JSONObject(json).opt("errors") }.getOrNull() ?: return
        val messages = when (errors) {
            is org.json.JSONArray -> (0 until errors.length()).mapNotNull { index ->
                errors.optJSONObject(index)?.let { error ->
                    error.keys().asSequence().map { key -> error.optString(key) }.filter(String::isNotBlank).joinToString(" ")
                }
            }
            is JSONObject -> errors.keys().asSequence().map { key -> errors.optString(key) }.filter(String::isNotBlank).toList()
            else -> emptyList()
        }
        val message = messages.joinToString(" ").lowercase(Locale.ROOT)
        apiFootballUnavailableReason = when {
            "suspend" in message -> "API-Football hesabı askıya alınmış; hesabı etkinleştirince gol krallığı otomatik gelir."
            "limit" in message || "quota" in message -> "API-Football günlük isteği dolmuş; kota yenilenince gol krallığı otomatik gelir."
            "invalid" in message || "key" in message -> "API-Football anahtarı geçersiz; paneldeki etkin anahtarı kontrol et."
            else -> null
        }
    }

    private fun currentSeasonYear(): Int {
        val today = LocalDate.now()
        return if (today.monthValue < 7) today.year - 1 else today.year
    }

    suspend fun fetchFixtures(competition: String, sportType: String = "FUTBOL"): List<SportsMatch> = withContext(Dispatchers.IO) {
        when (sportType) {
            "BASKETBOL" -> if (competition == "EuroLeague") fetchEuroLeagueFixtures() else if (basketballEspnSlug(competition) != null) fetchBasketballFixtures(competition) else emptyList()
            "FUTBOL" -> {
                val id = leagueId(competition) ?: return@withContext emptyList()
                if (hasApiKey() && apiFootballUnavailableReason == null) {
                    val today = LocalDate.now()
                    val url = "$baseUrl/fixtures?league=$id&season=${currentSeasonYear()}&from=${today.minusDays(2)}&to=${today.plusDays(7)}&timezone=Europe%2FIstanbul"
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .build()
                        httpClient.newCall(request).execute().use { response ->
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) rememberApiFootballIssue(body)
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
        if (sportType == "BASKETBOL") return@withContext if (competition == "EuroLeague") fetchEuroLeagueStandings() else fetchBasketballStandings(competition)
        if (sportType != "FUTBOL") return@withContext emptyList()
        val id = leagueId(competition) ?: return@withContext emptyList()
        val cacheKey = "$id-${currentSeasonYear()}"
        standingsCache[cacheKey]?.let { (fetchedAt, cached) ->
            if (System.currentTimeMillis() - fetchedAt < STANDINGS_CACHE_MS) return@withContext sortStandings(cached)
        }

        if (hasApiKey() && apiFootballUnavailableReason == null) {
            try {
                val url = "$baseUrl/standings?league=$id&season=${currentSeasonYear()}"
                val request = Request.Builder()
                    .url(url)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) rememberApiFootballIssue(body)
                    if (response.isSuccessful && !body.isNullOrBlank()) {
                        val standings = parseApiSportsStandings(body)
                        if (standings.isNotEmpty()) {
                            val sorted = sortStandings(standings)
                            standingsCache[cacheKey] = System.currentTimeMillis() to sorted
                            return@withContext sorted
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
            }
        }

        val standings = sortStandings(fetchEspnFallbackStandings(competition))
        if (standings.isNotEmpty()) standingsCache[cacheKey] = System.currentTimeMillis() to standings
        standings
    }

    suspend fun fetchTopScorers(competition: String, sportType: String = "FUTBOL"): List<TopScorer> = withContext(Dispatchers.IO) {
        if (sportType == "BASKETBOL") return@withContext fetchBasketballLeaders(competition)
        if (sportType != "FUTBOL") return@withContext emptyList()

        val id = leagueId(competition) ?: return@withContext emptyList()
        val cacheKey = "$id-${currentSeasonYear()}"
        scorersCache[cacheKey]?.let { (fetchedAt, cached) ->
            if (System.currentTimeMillis() - fetchedAt < STANDINGS_CACHE_MS) return@withContext cached
        }

        val scorers = mutableListOf<TopScorer>()
        try {
            if (!hasApiKey() || apiFootballUnavailableReason != null) throw IllegalStateException("API-Football unavailable")
            val url = "$baseUrl/players/topscorers?league=$id&season=${currentSeasonYear()}"
            val request = Request.Builder()
                .url(url)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!body.isNullOrBlank()) rememberApiFootballIssue(body)
                if (response.isSuccessful) {
                    if (!body.isNullOrBlank()) {
                        val entries = JSONObject(body).optJSONArray("response")
                        for (i in 0 until (entries?.length() ?: 0)) {
                            val entry = entries?.optJSONObject(i) ?: continue
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
        if (scorers.isNotEmpty()) {
            scorersCache[cacheKey] = System.currentTimeMillis() to scorers.toList()
            scorers
        } else {
            fetchEspnFootballTopScorers(competition).also { fallback ->
                if (fallback.isNotEmpty()) scorersCache[cacheKey] = System.currentTimeMillis() to fallback
            }
        }
    }

    private fun sortMatches(matches: List<SportsMatch>): List<SportsMatch> = matches
        .distinctBy { it.id }
        .sortedWith(compareBy<SportsMatch> { it.kickoffAtEpochMs }.thenBy { it.homeTeam })

    private fun sortStandings(standings: List<LeagueStanding>): List<LeagueStanding> = standings
        .sortedWith(compareBy<LeagueStanding> { it.groupName.orEmpty() }.thenBy { it.rank }.thenBy { it.team })

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
                        homeScore = homeScore.takeIf { status != MatchStatus.UPCOMING },
                        awayScore = awayScore.takeIf { status != MatchStatus.UPCOMING },
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

            for (groupIndex in 0 until standingsArray.length()) {
                val leagueGroup = standingsArray.optJSONArray(groupIndex) ?: continue
                for (i in 0 until leagueGroup.length()) {
                    val row = leagueGroup.optJSONObject(i) ?: continue
                    val rank = row.optInt("rank", i + 1)
                    val teamObj = row.optJSONObject("team") ?: continue
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
                    val goals = all.optJSONObject("goals") ?: JSONObject()
                    val rawGroup = row.optString("group").takeIf(String::isNotBlank)
                    val groupName = rawGroup ?: if (standingsArray.length() > 1) "Grup ${groupIndex + 1}" else null
                    list.add(
                        LeagueStanding(
                            rank = rank,
                            team = teamName,
                            played = all.optInt("played", 0),
                            won = all.optInt("win", 0),
                            drawn = all.optInt("draw", 0),
                            lost = all.optInt("lose", 0),
                            goalDiff = goalsDiff,
                            points = points,
                            form = form,
                            goalsFor = goals.optInt("for", 0),
                            goalsAgainst = goals.optInt("against", 0),
                            qualificationNote = row.optString("description").takeIf(String::isNotBlank),
                            groupName = groupName,
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private suspend fun fetchEspnFootballFixtures(competition: String): List<SportsMatch> {
        val leagueSlug = footballEspnSlug(competition) ?: return emptyList()

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
                        val score = competitor.optString("score", "").toIntOrNull().takeIf { state != "pre" }
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
            try {
                OffsetDateTime.parse(value).toInstant()
            } catch (_: Exception) {
                return 0L to "Saat bekleniyor"
            }
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

    private fun fetchEuroLeagueGames(): JSONArray? {
        val season = currentSeasonYear()
        return fetchEspnJson("https://api-live.euroleague.net/v2/competitions/E/seasons/E$season/games")
            ?.optJSONArray("data")
    }

    private fun fetchEuroLeagueFixtures(): List<SportsMatch> {
        val season = currentSeasonYear()
        val now = System.currentTimeMillis()
        euroLeagueGamesCache["E$season"]?.let { (fetchedAt, matches) ->
            if (now - fetchedAt < ESPN_LIVE_REFRESH_MS) return matches
        }
        val games = fetchEuroLeagueGames() ?: return emptyList()
        val from = LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val to = LocalDate.now().plusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val matches = (0 until games.length()).mapNotNull { index ->
            val game = games.optJSONObject(index) ?: return@mapNotNull null
            val time = runCatching { Instant.parse(game.optString("utcDate")).toEpochMilli() }.getOrDefault(0L)
            if (time !in from..to) return@mapNotNull null
            val local = game.optJSONObject("local") ?: return@mapNotNull null
            val road = game.optJSONObject("road") ?: return@mapNotNull null
            val homeClub = local.optJSONObject("club") ?: return@mapNotNull null
            val awayClub = road.optJSONObject("club") ?: return@mapNotNull null
            val played = game.optBoolean("played", false)
            val statusText = game.optString("gameStatus").lowercase(Locale.ROOT)
            val live = !played && ("live" in statusText || (time <= now && now - time < 3 * 60 * 60_000L))
            val status = when {
                played -> MatchStatus.FINISHED
                live -> MatchStatus.LIVE
                else -> MatchStatus.UPCOMING
            }
            val homeScore = local.optInt("score", 0).takeIf { status != MatchStatus.UPCOMING }
            val awayScore = road.optInt("score", 0).takeIf { status != MatchStatus.UPCOMING }
            val (_, kickoff) = formatKickoff(game.optString("utcDate"))
            SportsMatch(
                id = "euroleague-${game.optString("id", game.optString("identifier", index.toString()))}",
                competition = "EuroLeague",
                round = game.optString("roundName").ifBlank { game.optJSONObject("phaseType")?.optString("name", "Sezon") ?: "Sezon" },
                homeTeam = homeClub.optString("name", "Ev Sahibi"),
                awayTeam = awayClub.optString("name", "Deplasman"),
                homeScore = homeScore,
                awayScore = awayScore,
                status = status,
                statusLabel = when (status) {
                    MatchStatus.LIVE -> "CANLI"
                    MatchStatus.FINISHED -> "MS"
                    MatchStatus.UPCOMING -> kickoff
                },
                kickoffTime = kickoff,
                kickoffAtEpochMs = time,
                sportType = "BASKETBOL",
                liveAction = if (status == MatchStatus.LIVE) "🏀 Canlı skor: ${homeScore ?: 0} - ${awayScore ?: 0}" else "",
            )
        }.sortedWith(compareBy<SportsMatch> { it.kickoffAtEpochMs }.thenBy { it.homeTeam })
        euroLeagueGamesCache["E$season"] = now to matches
        return matches
    }

    private fun fetchEuroLeagueStandings(): List<LeagueStanding> {
        val season = currentSeasonYear()
        val cacheKey = "euroleague-E$season"
        standingsCache[cacheKey]?.let { (fetchedAt, table) ->
            if (System.currentTimeMillis() - fetchedAt < ESPN_LIVE_REFRESH_MS) return table
        }
        val games = fetchEuroLeagueGames() ?: return emptyList()
        data class TeamRecord(var played: Int = 0, var wins: Int = 0, var losses: Int = 0, var scored: Int = 0, var conceded: Int = 0)
        val records = linkedMapOf<String, TeamRecord>()
        for (index in 0 until games.length()) {
            val game = games.optJSONObject(index) ?: continue
            val phase = game.optJSONObject("phaseType")?.optString("alias").orEmpty()
            val group = game.optJSONObject("group")?.optString("rawName").orEmpty()
            if (!game.optBoolean("played", false) || !(phase.contains("regular", true) || group.contains("regular", true))) continue
            val local = game.optJSONObject("local") ?: continue
            val road = game.optJSONObject("road") ?: continue
            val homeName = local.optJSONObject("club")?.optString("name").orEmpty()
            val awayName = road.optJSONObject("club")?.optString("name").orEmpty()
            if (homeName.isBlank() || awayName.isBlank()) continue
            val homeScore = local.optInt("score", -1)
            val awayScore = road.optInt("score", -1)
            if (homeScore < 0 || awayScore < 0) continue
            val home = records.getOrPut(homeName) { TeamRecord() }
            val away = records.getOrPut(awayName) { TeamRecord() }
            home.played++; away.played++
            home.scored += homeScore; home.conceded += awayScore
            away.scored += awayScore; away.conceded += homeScore
            if (homeScore > awayScore) { home.wins++; away.losses++ } else { away.wins++; home.losses++ }
        }
        val ranked = records.entries.sortedWith(
            compareByDescending<Map.Entry<String, TeamRecord>> { it.value.wins }
                .thenByDescending { it.value.scored - it.value.conceded }
                .thenBy { it.key },
        )
        val table = ranked.mapIndexed { index, (name, row) ->
            LeagueStanding(
                rank = index + 1,
                team = name,
                played = row.played,
                won = row.wins,
                drawn = 0,
                lost = row.losses,
                goalDiff = row.scored - row.conceded,
                points = row.wins * 2,
                groupName = "Regular Season",
                winPercentage = if (row.played == 0) null else row.wins.toDouble() / row.played,
            )
        }
        if (table.isNotEmpty()) standingsCache[cacheKey] = System.currentTimeMillis() to table
        return table
    }

    private suspend fun fetchBasketballFixtures(competition: String): List<SportsMatch> {
        val slug = basketballEspnSlug(competition) ?: return emptyList()
        val cacheKey = "BASKETBOL|$competition"
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        var cache = espnScheduleCache[cacheKey]

        if (cache == null || now - cache.scheduleFetchedAtMs >= ESPN_SCHEDULE_REFRESH_MS) {
            val semaphore = Semaphore(4)
            val schedule = coroutineScope {
                (0L..7L).map { offset ->
                    async {
                        semaphore.withPermit {
                            fetchBasketballScoreboard(competition, slug, today.plusDays(offset))
                        }
                    }
                }.awaitAll().flatten()
            }
            cache = EspnFixtureCache(now, now, sortMatches(schedule))
            espnScheduleCache[cacheKey] = cache
        } else if (now - cache.liveFetchedAtMs >= ESPN_LIVE_REFRESH_MS) {
            val todayMatches = fetchBasketballScoreboard(competition, slug, today)
            val refreshed = cache.matches.filterNot { sameLocalDay(it.kickoffAtEpochMs, today) } + todayMatches
            cache = cache.copy(liveFetchedAtMs = now, matches = sortMatches(refreshed))
            espnScheduleCache[cacheKey] = cache
        }
        return cache.matches
    }

    private fun fetchBasketballScoreboard(competition: String, leagueSlug: String, date: LocalDate): List<SportsMatch> {
        val result = mutableListOf<SportsMatch>()
        try {
            val dateParam = date.format(DateTimeFormatter.BASIC_ISO_DATE)
            val url = "https://site.api.espn.com/apis/site/v2/sports/basketball/$leagueSlug/scoreboard?limit=100&dates=$dateParam"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) return result
                val events = JSONObject(body).optJSONArray("events") ?: return result
                for (i in 0 until events.length()) {
                    val event = events.optJSONObject(i) ?: continue
                    val id = event.optString("id", "basketball-$i")
                    val competitionArray = event.optJSONArray("competitions") ?: continue
                    val game = competitionArray.optJSONObject(0) ?: continue
                    val statusObject = game.optJSONObject("status") ?: JSONObject()
                    val statusType = statusObject.optJSONObject("type") ?: JSONObject()
                    val state = statusType.optString("state", "pre")
                    val detail = statusType.optString("shortDetail")
                        .ifBlank { statusType.optString("detail", "") }
                    val competitors = game.optJSONArray("competitors") ?: continue
                    var homeName = "Ev Sahibi"
                    var awayName = "Deplasman"
                    var homeScore: Int? = null
                    var awayScore: Int? = null
                    for (index in 0 until competitors.length()) {
                        val competitor = competitors.optJSONObject(index) ?: continue
                        val isHome = competitor.optString("homeAway") == "home"
                        val teamName = competitor.optJSONObject("team")?.optString("displayName").orEmpty()
                            .ifBlank { "Takım" }
                    val score = competitor.optString("score").toIntOrNull().takeIf { state != "pre" }
                        if (isHome) {
                            homeName = teamName
                            homeScore = score
                        } else {
                            awayName = teamName
                            awayScore = score
                        }
                    }

                    val status = when (state) {
                        "in" -> MatchStatus.LIVE
                        "post" -> MatchStatus.FINISHED
                        else -> MatchStatus.UPCOMING
                    }
                    val (kickoffAt, kickoffTime) = formatKickoff(event.optString("date"))
                    val scoreText = if (homeScore != null && awayScore != null) "$homeScore - $awayScore" else ""
                    result.add(
                        SportsMatch(
                            id = id,
                            competition = competition,
                            round = event.optJSONObject("season")?.optJSONObject("type")?.optString("name")
                                ?.takeIf(String::isNotBlank) ?: "Sezon",
                            homeTeam = homeName,
                            awayTeam = awayName,
                            homeScore = homeScore,
                            awayScore = awayScore,
                            status = status,
                            statusLabel = when (status) {
                                MatchStatus.LIVE -> listOf("CANLI", detail).filter(String::isNotBlank).joinToString(" · ")
                                MatchStatus.FINISHED -> "MS"
                                MatchStatus.UPCOMING -> "Başlamadı"
                            },
                            kickoffTime = kickoffTime,
                            kickoffAtEpochMs = kickoffAt,
                            sportType = "BASKETBOL",
                            liveAction = if (status == MatchStatus.LIVE && scoreText.isNotBlank()) "🏀 Canlı skor: $scoreText" else "",
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun fetchBasketballStandings(competition: String): List<LeagueStanding> {
        val slug = basketballEspnSlug(competition) ?: return emptyList()
        val cacheKey = "basketball-$slug-${basketballSeasonYear(slug)}"
        standingsCache[cacheKey]?.let { (fetchedAt, cached) ->
            if (System.currentTimeMillis() - fetchedAt < STANDINGS_CACHE_MS) return sortStandings(cached)
        }
        val standings = mutableListOf<LeagueStanding>()
        try {
            val url = "https://site.api.espn.com/apis/v2/sports/basketball/$slug/standings"
            val root = fetchEspnJson(url) ?: return emptyList()
            val groups = root.optJSONArray("children") ?: return emptyList()
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                val groupName = group.optString("name").takeIf(String::isNotBlank)
                val entries = group.optJSONObject("standings")?.optJSONArray("entries") ?: continue
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val teamName = entry.optJSONObject("team")?.optString("displayName").orEmpty()
                    if (teamName.isBlank()) continue
                    val stats = entry.optJSONArray("stats") ?: continue
                    var rank = index + 1
                    var wins = 0
                    var losses = 0
                    var winPercentage: Double? = null
                    var pointDifferential = 0
                    for (statIndex in 0 until stats.length()) {
                        val stat = stats.optJSONObject(statIndex) ?: continue
                        when (stat.optString("name")) {
                            "playoffSeed", "rank" -> rank = stat.optDouble("value", rank.toDouble()).toInt().takeIf { it > 0 } ?: rank
                            "wins" -> wins = stat.optDouble("value", 0.0).toInt()
                            "losses" -> losses = stat.optDouble("value", 0.0).toInt()
                            "winPercent" -> winPercentage = stat.optDouble("value", 0.0)
                            "pointDifferential" -> pointDifferential = stat.optDouble("value", 0.0).toInt()
                        }
                    }
                    standings.add(
                        LeagueStanding(
                            rank = rank,
                            team = teamName,
                            played = wins + losses,
                            won = wins,
                            drawn = 0,
                            lost = losses,
                            goalDiff = pointDifferential,
                            points = 0,
                            form = "—",
                            groupName = groupName,
                            winPercentage = winPercentage,
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        val sorted = sortStandings(standings)
        if (sorted.isNotEmpty()) standingsCache[cacheKey] = System.currentTimeMillis() to sorted
        return sorted
    }

    private suspend fun fetchBasketballLeaders(competition: String): List<TopScorer> {
        if (competition == "EuroLeague") return emptyList()
        val slug = basketballEspnSlug(competition) ?: return emptyList()
        val season = basketballSeasonYear(slug)
        val cacheKey = "$slug-$season-points"
        basketballLeadersCache[cacheKey]?.let { (fetchedAt, cached) ->
            if (System.currentTimeMillis() - fetchedAt < STANDINGS_CACHE_MS) return cached
        }
        val url = "https://sports.core.api.espn.com/v2/sports/basketball/leagues/$slug/seasons/$season/types/2/leaders"
        val root = fetchEspnJson(url) ?: return emptyList()
        val categories = root.optJSONArray("categories") ?: return emptyList()
        val pointsCategory = (0 until categories.length())
            .mapNotNull { categories.optJSONObject(it) }
            .firstOrNull { it.optString("name") == "pointsPerGame" }
            ?: return emptyList()
        val entries = pointsCategory.optJSONArray("leaders") ?: return emptyList()
        val semaphore = Semaphore(4)
        val leaders = coroutineScope {
            (0 until minOf(entries.length(), 10)).map { index ->
                async {
                    semaphore.withPermit {
                        val entry = entries.optJSONObject(index) ?: return@withPermit null
                        val athleteRef = entry.optJSONObject("athlete")?.optString("\$ref").orEmpty()
                        val teamRef = entry.optJSONObject("team")?.optString("\$ref").orEmpty()
                        val athleteAndTeam = coroutineScope {
                            val athlete = async { fetchEspnEntityName(athleteRef) }
                            val team = async { fetchEspnEntityName(teamRef) }
                            athlete.await() to team.await()
                        }
                        val playerName = athleteAndTeam.first
                        if (playerName.isBlank()) return@withPermit null
                        val displayValue = entry.optString("displayValue").ifBlank {
                            entry.optDouble("value", 0.0).toString()
                        }
                        TopScorer(
                            rank = index + 1,
                            player = playerName,
                            team = athleteAndTeam.second.ifBlank { "Takım bilgisi yok" },
                            count = entry.optDouble("value", 0.0).toInt(),
                            matches = 0,
                            type = "SAYI/MAÇ",
                            valueLabel = "$displayValue sayı/maç",
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (leaders.isNotEmpty()) basketballLeadersCache[cacheKey] = System.currentTimeMillis() to leaders
        return leaders
    }

    private suspend fun fetchEspnFootballTopScorers(competition: String): List<TopScorer> {
        val slug = footballEspnSlug(competition) ?: return emptyList()
        val season = currentSeasonYear()
        val cacheKey = "${leagueId(competition)}-$season"
        val url = "https://sports.core.api.espn.com/v2/sports/soccer/leagues/$slug/seasons/$season/types/1/leaders"
        val root = fetchEspnJson(url) ?: return emptyList()
        val categories = root.optJSONArray("categories") ?: return emptyList()
        val goalsCategory = (0 until categories.length())
            .mapNotNull { categories.optJSONObject(it) }
            .firstOrNull { it.optString("name") == "goalsLeaders" }
            ?: (0 until categories.length()).mapNotNull { categories.optJSONObject(it) }
                .firstOrNull { it.optString("name") == "goals" }
            ?: return emptyList()
        val entries = goalsCategory.optJSONArray("leaders") ?: return emptyList()
        val semaphore = Semaphore(4)
        val leaders = coroutineScope {
            (0 until minOf(entries.length(), 20)).map { index ->
                async {
                    semaphore.withPermit {
                        val entry = entries.optJSONObject(index) ?: return@withPermit null
                        val athleteRef = entry.optJSONObject("athlete")?.optString("\$ref").orEmpty()
                        val teamRef = entry.optJSONObject("team")?.optString("\$ref").orEmpty()
                        val names = coroutineScope {
                            val athlete = async { fetchEspnEntityName(athleteRef) }
                            val team = async { fetchEspnEntityName(teamRef) }
                            athlete.await() to team.await()
                        }
                        if (names.first.isBlank() || names.second.isBlank()) return@withPermit null
                        val detail = entry.optString("displayValue")
                        val appearances = Regex("Matches:\\s*(\\d+)", RegexOption.IGNORE_CASE)
                            .find(detail)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        val assists = Regex("Assists:\\s*(\\d+)", RegexOption.IGNORE_CASE)
                            .find(detail)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        TopScorer(
                            rank = index + 1,
                            player = names.first,
                            team = names.second,
                            count = entry.optDouble("value", 0.0).toInt(),
                            secondaryCount = assists,
                            matches = appearances,
                            type = "GOL",
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (leaders.isNotEmpty()) scorersCache[cacheKey] = System.currentTimeMillis() to leaders
        return leaders
    }

    private fun basketballSeasonYear(slug: String): Int {
        val now = LocalDate.now()
        return if (slug == "wnba") now.year else if (now.monthValue >= 7) now.year + 1 else now.year
    }

    private fun fetchEspnJson(url: String): JSONObject? = try {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank()) JSONObject(body) else null
        }
    } catch (_: Exception) {
        null
    }

    private fun fetchEspnEntityName(reference: String): String {
        if (reference.isBlank()) return ""
        val safeUrl = reference.replaceFirst("http://", "https://")
        return fetchEspnJson(safeUrl)?.optString("displayName").orEmpty()
    }

    private fun fetchEspnFallbackStandings(competition: String): List<LeagueStanding> {
        val leagueSlug = when (competition) {
            "UEFA Şampiyonlar Ligi" -> "uefa.champions"
            "UEFA Avrupa Ligi" -> "uefa.europa"
            "UEFA Konferans Ligi" -> "uefa.europa.conf"
            "İngiltere Premier Lig" -> "eng.1"
            "İngiltere Championship" -> "eng.2"
            "Trendyol 1. Lig" -> "tur.2"
            "İspanya La Liga" -> "esp.1"
            "İspanya Segunda" -> "esp.2"
            "İtalya Serie A" -> "ita.1"
            "İtalya Serie B" -> "ita.2"
            "Almanya Bundesliga" -> "ger.1"
            "Almanya 2. Bundesliga" -> "ger.2"
            "Fransa Ligue 1" -> "fra.1"
            "Fransa Ligue 2" -> "fra.2"
            "İskoçya Premiership" -> "sco.1"
            "Belçika Pro League" -> "bel.1"
            "Suudi Arabistan Pro League" -> "ksa.1"
            "Arjantin Primera División" -> "arg.1"
            "Hollanda Eredivisie" -> "ned.1"
            "Portekiz Primeira Liga" -> "por.1"
            "Trendyol Süper Lig" -> "tur.1"
            "Brezilya Serie A" -> "bra.1"
            "MLS" -> "usa.1"
            "Japonya J1 League" -> "jpn.1"
            "CONMEBOL Libertadores" -> "conmebol.libertadores"
            "CONMEBOL Sudamericana" -> "conmebol.sudamericana"
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
                    for (groupIndex in 0 until children.length()) {
                        val child = children.optJSONObject(groupIndex) ?: continue
                        val standingsObj = child.optJSONObject("standings") ?: continue
                        val entries = standingsObj.optJSONArray("entries") ?: continue
                        val groupName = child.optString("name").takeIf(String::isNotBlank)

                        for (i in 0 until entries.length()) {
                            val entry = entries.optJSONObject(i) ?: continue
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
                                val stat = statsArray.optJSONObject(s) ?: continue
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
                                    groupName = groupName,
                                )
                            )
                        }
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

