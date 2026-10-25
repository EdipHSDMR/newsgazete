package com.example.data.remote

import com.example.model.LeagueStanding
import com.example.model.MatchStatus
import com.example.model.SportsMatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** API-Sports Volleyball proxy client. The provider key remains on the server gateway. */
class VolleyballApiService(private val httpClient: OkHttpClient) {
    private val baseUrl = "${ApiGatewayConfig.BASE_URL}/v1/volleyball"
    private val leagueCache = ConcurrentHashMap<String, LeagueRef>()
    private val gameCache = ConcurrentHashMap<String, Pair<Long, List<SportsMatch>>>()
    private val tableCache = ConcurrentHashMap<String, Pair<Long, List<LeagueStanding>>>()

    suspend fun fetchFixtures(competition: String): List<SportsMatch> = withContext(Dispatchers.IO) {
        val league = resolveLeague(competition) ?: return@withContext emptyList()
        gameCache[competition]?.takeIf { System.currentTimeMillis() - it.first < GAME_CACHE_MS }
            ?.let { return@withContext it.second }
        val root = request("games?league=${league.id}&season=${league.season}") ?: return@withContext emptyList()
        val matches = parseGames(root, competition)
            .sortedWith(compareBy<SportsMatch> { it.kickoffAtEpochMs }.thenBy { it.homeTeam })
        if (matches.isNotEmpty()) gameCache[competition] = System.currentTimeMillis() to matches
        matches
    }

    suspend fun fetchStandings(competition: String): List<LeagueStanding> = withContext(Dispatchers.IO) {
        val league = resolveLeague(competition) ?: return@withContext emptyList()
        tableCache[competition]?.takeIf { System.currentTimeMillis() - it.first < TABLE_CACHE_MS }
            ?.let { return@withContext it.second }
        val root = request("standings?league=${league.id}&season=${league.season}") ?: return@withContext emptyList()
        val rows = parseStandings(root)
        if (rows.isNotEmpty()) tableCache[competition] = System.currentTimeMillis() to rows
        rows
    }

    private data class LeagueRef(val id: Int, val season: Int)

    private suspend fun resolveLeague(competition: String): LeagueRef? {
        leagueCache[competition]?.let { return it }
        val tokens = leagueSearchTokens(competition)
        val season = currentSeasonYear()
        val country = if (competition.startsWith("Türkiye", ignoreCase = true)) "Turkey" else "Europe"
        val root = request("leagues?country=${encode(country)}&season=$season")
            ?: request("leagues?search=${encode(tokens.first())}")
            ?: return null
        val leagues = root.optJSONArray("response") ?: return null
        val candidates = (0 until leagues.length()).mapNotNull(leagues::optJSONObject)
        val match = candidates.firstOrNull { item ->
            val name = normalized(item.optString("name"))
            val countryName = normalized(item.optJSONObject("country")?.optString("name").orEmpty())
            val womenLeague = name.contains("women") || name.contains("ladies")
            val genderMatches = when {
                competition.contains("Kadınlar", ignoreCase = true) -> womenLeague
                competition.startsWith("CEV", ignoreCase = true) -> !womenLeague
                else -> true
            }
            genderMatches && tokens.any { token -> name.contains(normalized(token)) } &&
                (country == "Europe" || countryName.contains("turkey") || countryName.contains("turkiye"))
        } ?: return null
        val availableSeasons = match.optJSONArray("seasons")
        val bestSeason = (0 until (availableSeasons?.length() ?: 0))
            .mapNotNull { index ->
                val item = availableSeasons?.optJSONObject(index) ?: return@mapNotNull null
                val value = item.opt("season")
                when (value) {
                    is Number -> value.toInt()
                    else -> value?.toString()?.take(4)?.toIntOrNull()
                }
            }
            .filter { it <= season }
            .maxOrNull() ?: season
        val resolved = LeagueRef(match.optInt("id", 0), bestSeason).takeIf { it.id > 0 } ?: return null
        leagueCache[competition] = resolved
        return resolved
    }

    private fun leagueSearchTokens(name: String): List<String> = when (name) {
        "Türkiye Efeler Ligi" -> listOf("Efeler Ligi", "Efeler")
        "Türkiye Sultanlar Ligi" -> listOf("Sultanlar Ligi", "Sultanlar")
        "CEV Şampiyonlar Ligi Erkekler" -> listOf("Champions League")
        "CEV Şampiyonlar Ligi Kadınlar" -> listOf("Champions League Women", "Champions League")
        "CEV Cup Erkekler" -> listOf("CEV Cup")
        "CEV Cup Kadınlar" -> listOf("CEV Cup Women", "CEV Cup")
        else -> listOf(name)
    }

    private suspend fun request(pathAndQuery: String): JSONObject? {
        return try {
            val url = "$baseUrl/$pathAndQuery"
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) null else JSONObject(body)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun parseGames(root: JSONObject, competition: String): List<SportsMatch> {
        val response = root.optJSONArray("response") ?: return emptyList()
        val now = System.currentTimeMillis()
        val rangeStart = LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rangeEnd = LocalDate.now().plusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return (0 until response.length()).mapNotNull { index ->
            val item = response.optJSONObject(index) ?: return@mapNotNull null
            val teams = item.optJSONObject("teams") ?: return@mapNotNull null
            val home = teams.optJSONObject("home") ?: return@mapNotNull null
            val away = teams.optJSONObject("away") ?: return@mapNotNull null
            val rawDate = item.optString("date")
            val kickoff = parseDate(rawDate).takeIf { it > 0L }
                ?: item.optLong("timestamp", 0L).let { if (it in 1..9_999_999_999L) it * 1000L else it }
            if (kickoff < rangeStart || kickoff > rangeEnd) return@mapNotNull null
            val statusObject = item.optJSONObject("status") ?: JSONObject()
            val statusText = (statusObject.optString("long") + " " + statusObject.optString("short")).lowercase(Locale.ROOT)
            val finished = listOf("finished", "complete", "ended", "ft", "final").any(statusText::contains)
            val live = !finished && (listOf("live", "progress", "set ", "in-play").any(statusText::contains) ||
                (kickoff <= now && now - kickoff < 3 * 60 * 60 * 1000L))
            val status = when {
                finished -> MatchStatus.FINISHED
                live -> MatchStatus.LIVE
                else -> MatchStatus.UPCOMING
            }
            val score = item.optJSONObject("scores") ?: JSONObject()
            val homeSets = scoreTotal(score.opt("home"))
            val awaySets = scoreTotal(score.opt("away"))
            val dateTime = Instant.ofEpochMilli(kickoff).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.forLanguageTag("tr-TR")))
            SportsMatch(
                id = "volleyball-${item.optInt("id", index)}",
                competition = competition,
                round = item.optString("week").ifBlank { item.optString("round", "Sezon") },
                homeTeam = home.optString("name", "Ev Sahibi"),
                awayTeam = away.optString("name", "Deplasman"),
                homeScore = if (status == MatchStatus.UPCOMING) null else homeSets,
                awayScore = if (status == MatchStatus.UPCOMING) null else awaySets,
                status = status,
                statusLabel = when (status) {
                    MatchStatus.LIVE -> "CANLI · ${statusObject.optString("long", "Set oynanıyor")}".trim()
                    MatchStatus.FINISHED -> "MS"
                    MatchStatus.UPCOMING -> dateTime
                },
                kickoffTime = dateTime,
                kickoffAtEpochMs = kickoff,
                sportType = "VOLEYBOL",
                liveAction = if (status == MatchStatus.LIVE && homeSets != null && awaySets != null) "🏐 Set skoru: $homeSets - $awaySets" else "",
            )
        }
    }

    private fun parseStandings(root: JSONObject): List<LeagueStanding> {
        val response = root.optJSONArray("response") ?: return emptyList()
        val output = mutableListOf<LeagueStanding>()
        for (index in 0 until response.length()) {
            val entry = response.optJSONObject(index) ?: continue
            val league = entry.optJSONObject("league")
            val groups = league?.optJSONArray("standings") ?: entry.optJSONArray("standings") ?: continue
            for (groupIndex in 0 until groups.length()) {
                val group = groups.opt(groupIndex)
                val rows = when (group) {
                    is JSONArray -> group
                    is JSONObject -> JSONArray().put(group)
                    else -> continue
                }
                for (rowIndex in 0 until rows.length()) {
                    val row = rows.optJSONObject(rowIndex) ?: continue
                    val team = when (val teamValue = row.opt("team")) {
                        is JSONObject -> teamValue.optString("name")
                        else -> teamValue?.toString().orEmpty()
                    }
                    if (team.isBlank()) continue
                    val all = row.optJSONObject("all") ?: row.optJSONObject("games") ?: JSONObject()
                    val sets = row.optJSONObject("sets") ?: JSONObject()
                    output += LeagueStanding(
                        rank = row.optInt("rank", rowIndex + 1),
                        team = team,
                        played = all.optInt("played", row.optInt("played", 0)),
                        won = all.optInt("win", row.optInt("won", 0)),
                        drawn = 0,
                        lost = all.optInt("lose", row.optInt("lost", 0)),
                        goalDiff = sets.optInt("difference", sets.optInt("diff", 0)),
                        points = row.optInt("points", 0),
                        form = row.optString("form", "—"),
                        goalsFor = sets.optInt("for", sets.optInt("won", 0)),
                        goalsAgainst = sets.optInt("against", sets.optInt("lost", 0)),
                        groupName = row.optString("group").takeIf(String::isNotBlank),
                    )
                }
            }
        }
        return output.sortedWith(compareBy<LeagueStanding> { it.groupName.orEmpty() }.thenBy { it.rank }.thenBy { it.team })
    }

    private fun scoreTotal(raw: Any?): Int? = when (raw) {
        is Number -> raw.toInt()
        is JSONObject -> raw.optInt("total", raw.optInt("sets", raw.optInt("score", 0)))
            .takeIf { it > 0 } ?: (1..5).sumOf { set -> raw.optInt("set$set", 0).let { if (it >= 25 || (set == 5 && it >= 15)) 1 else 0 } }
        else -> raw?.toString()?.toIntOrNull()
    }

    private fun parseDate(value: String): Long = runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { java.time.LocalDateTime.parse(value.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        .getOrDefault(0L)

    private fun normalized(value: String): String = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun currentSeasonYear(): Int = LocalDate.now().let { if (it.monthValue < 8) it.year - 1 else it.year }

    private companion object {
        const val GAME_CACHE_MS = 90_000L
        const val TABLE_CACHE_MS = 60 * 60_000L
    }
}

