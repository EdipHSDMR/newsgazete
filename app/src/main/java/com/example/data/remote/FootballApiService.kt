package com.example.data.remote

import com.example.BuildConfig
import com.example.model.LeagueStanding
import com.example.model.MatchEvent
import com.example.model.MatchStatus
import com.example.model.SportsMatch
import com.example.model.TopScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate

class FootballApiService(private val httpClient: OkHttpClient) {

    private val apiKey = BuildConfig.API_FOOTBALL_KEY.trim()
    private val baseUrl = "https://v3.football.api-sports.io"

    private val baseUrl = "https://v3.football.api-sports.io"

    private fun leagueId(competition: String): Int = when (competition) {
        "UEFA Şampiyonlar Ligi" -> 2
        "İngiltere Premier Lig" -> 39
        "İspanya La Liga" -> 140
        "A Milli Takım" -> 1
        else -> 203
    }

    private fun currentSeasonYear(): Int {
        val today = LocalDate.now()
        return if (today.monthValue < 7) today.year - 1 else today.year
    }

    suspend fun fetchFixtures(competition: String, sportType: String = "FUTBOL"): List<SportsMatch> = withContext(Dispatchers.IO) {
        when (sportType) {
            "BASKETBOL" -> fetchBasketballFixtures(competition)
            "FUTBOL" -> {
                val liveMatches = fetchEspnFootballFixtures(competition)
                if (liveMatches.isNotEmpty() || apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
                    liveMatches
                } else {
                    val startDate = LocalDate.now()
                    val endDate = startDate.plusDays(7)
                    val urls = listOf(
                        "$baseUrl/fixtures?live=all",
                        "$baseUrl/fixtures?league=${leagueId(competition)}&season=${currentSeasonYear()}&from=$startDate&to=$endDate",
                    )
                    val matches = mutableListOf<SportsMatch>()
                    for ((index, url) in urls.withIndex()) {
                        try {
                            val request = Request.Builder()
                                .url(url)
                                .header("x-apisports-key", apiKey)
                                .build()
                            httpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.string()
                                    if (!body.isNullOrBlank()) {
                                        matches.addAll(
                                            parseApiSportsFixtures(
                                                competition = competition,
                                                jsonStr = body,
                                                filterLeagueId = if (index == 0) leagueId(competition) else null,
                                                sportType = "FUTBOL",
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        if (matches.isNotEmpty()) break
                    }
                    matches
                }
            }
            else -> emptyList()
        }
    }

    suspend fun fetchStandings(competition: String, sportType: String = "FUTBOL"): List<LeagueStanding> = withContext(Dispatchers.IO) {
        if (sportType != "FUTBOL") return@withContext emptyList()

        val liveStandings = fetchEspnFallbackStandings(competition)
        if (liveStandings.isNotEmpty() || apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            return@withContext liveStandings
        }

        val standings = mutableListOf<LeagueStanding>()
        try {
            val url = "$baseUrl/standings?league=${leagueId(competition)}&season=${currentSeasonYear()}"
            val request = Request.Builder()
                .url(url)
                .header("x-apisports-key", apiKey)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) standings.addAll(parseApiSportsStandings(body))
                }
            }
        } catch (_: Exception) {}
        standings
    }

    suspend fun fetchTopScorers(competition: String, sportType: String = "FUTBOL"): List<TopScorer> = withContext(Dispatchers.IO) {
        if (sportType != "FUTBOL" || apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            return@withContext emptyList()
        }

        val scorers = mutableListOf<TopScorer>()
        try {
            val url = "$baseUrl/players/topscorers?league=${leagueId(competition)}&season=${currentSeasonYear()}"
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
                    }
                }
            }
        } catch (_: Exception) {}
        scorers
    }

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
                    isLive -> if (elapsed > 0) "$elapsed' CANLI" else "CANLI"
                    isFinished -> "MS"
                    shortStatus == "HT" -> "İY"
                    else -> fixture.optString("date", "").substringAfter("T").take(5).ifBlank { "20:00" }
                }

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
                        kickoffTime = fixture.optString("date", "").substringAfter("T").take(5).ifBlank { "20:00" },
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
                val form = row.optString("form", "W-W-D-L-W")
                    .replace("W", "G-")
                    .replace("D", "B-")
                    .replace("L", "M-")
                    .trimEnd('-')

                val all = row.optJSONObject("all") ?: JSONObject()
                val played = all.optInt("played", 0)
                val won = all.optInt("win", 0)
                val drawn = all.optInt("draw", 0)
                val lost = all.optInt("lose", 0)

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
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun fetchEspnFootballFixtures(competition: String): List<SportsMatch> {
        val leagueSlug = when (competition) {
            "UEFA Şampiyonlar Ligi" -> "uefa.champions"
            "İngiltere Premier Lig" -> "eng.1"
            "İspanya La Liga" -> "esp.1"
            "A Milli Takım" -> "fifa.world"
            else -> "tur.1"
        }
        val result = mutableListOf<SportsMatch>()
        try {
            val url = "https://site.api.espn.com/apis/site/v2/sports/soccer/$leagueSlug/scoreboard"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (body != null) {
                    val root = JSONObject(body)
                    val events = root.optJSONArray("events") ?: return result
                    for (i in 0 until events.length()) {
                        val ev = events.getJSONObject(i)
                        val id = ev.optString("id", "m-$i")
                        val compArray = ev.optJSONArray("competitions") ?: continue
                        if (compArray.length() == 0) continue
                        val compObj = compArray.getJSONObject(0)
                        val round = compObj.optJSONObject("round")?.optString("displayName", "Fikstür") ?: "Fikstür"
                        val statusObj = compObj.optJSONObject("status") ?: JSONObject()
                        val typeObj = statusObj.optJSONObject("type") ?: JSONObject()
                        val state = typeObj.optString("state", "pre")
                        val detail = typeObj.optString("detail", "Başlamadı")
                        val clock = statusObj.optDouble("clock", 0.0).toInt()

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
                            "in" -> if (clock > 0) "$clock' CANLI" else "CANLI"
                            "post" -> "MS"
                            else -> detail.ifBlank { "Yakında" }
                        }

                        val liveAction = if (status == MatchStatus.LIVE && homeScore != null && awayScore != null) {
                            "⚽ Canlı Mücadele: $homeScore - $awayScore"
                        } else ""

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
                                statusLabel = statusLabel,
                                kickoffTime = detail,
                                events = emptyList(),
                                sportType = "FUTBOL",
                                liveAction = liveAction,
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return result
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
            else -> "tur.1"
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
                                form = "G-B-G-G-M",
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

}
