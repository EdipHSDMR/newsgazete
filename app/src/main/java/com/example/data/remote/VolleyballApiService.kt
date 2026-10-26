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
    @Volatile var lastErrorMessage: String = ""
        private set

    fun clearLastError() {
        lastErrorMessage = ""
    }

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
        val config = leagueConfig(competition)
        if (config == null) {
            lastErrorMessage = "Bu voleybol ligi uygulamada tanımlı değil."
            return null
        }
        val season = currentSeasonYear()
        val country = config.country
        val firstRoot = request("leagues?country=${encode(country)}&season=$season")
        var candidates = firstRoot?.optJSONArray("response")?.let { rows ->
            (0 until rows.length()).mapNotNull(rows::optJSONObject)
        }.orEmpty()
        fun findMatch(items: List<JSONObject>): JSONObject? = items.firstOrNull { item ->
            val name = normalized(item.optString("name"))
            val countryName = normalized(item.optJSONObject("country")?.optString("name").orEmpty())
            name in config.searchNames.map(::normalized) &&
                (country == "Europe" && countryName == "europe" || country == "Turkey" && countryName in setOf("turkey", "turkiye"))
        }
        var match = findMatch(candidates)
        if (match == null) {
            val fallback = request("leagues?search=${encode(config.searchNames.first())}")
            val rows = fallback?.optJSONArray("response")
            candidates = if (rows == null) emptyList() else (0 until rows.length()).mapNotNull(rows::optJSONObject)
            match = findMatch(candidates)
        }
        val exactMatch = match ?: run {
            if (lastErrorMessage.isBlank()) lastErrorMessage = "$competition: sağlayıcıda bu sezon için lig bulunamadı."
            return null
        }
        val availableSeasons = exactMatch.optJSONArray("seasons")
        val bestSeason = selectSeason(availableSeasons, season)
        val resolved = LeagueRef(exactMatch.optInt("id", 0), bestSeason).takeIf { it.id > 0 } ?: run {
            lastErrorMessage = "${competition}: sağlayıcının lig kimliği geçersiz."
            return null
        }
        leagueCache[competition] = resolved
        return resolved
    }

    private data class LeagueConfig(val country: String, val searchNames: List<String>)

    private fun leagueConfig(name: String): LeagueConfig? = when (name) {
        "Türkiye Efeler Ligi" -> LeagueConfig("Turkey", listOf("Efeler Ligi"))
        "Türkiye Sultanlar Ligi" -> LeagueConfig("Turkey", listOf("Sultanlar Ligi Women"))
        "CEV Şampiyonlar Ligi Erkekler" -> LeagueConfig("Europe", listOf("Champions League"))
        "CEV Şampiyonlar Ligi Kadınlar" -> LeagueConfig("Europe", listOf("Champions League Women"))
        "CEV Cup Erkekler" -> LeagueConfig("Europe", listOf("CEV Cup"))
        "CEV Cup Kadınlar" -> LeagueConfig("Europe", listOf("CEV Cup Women"))
        else -> null
    }

    private fun selectSeason(seasons: JSONArray?, fallback: Int): Int {
        val entries = (0 until (seasons?.length() ?: 0)).mapNotNull { index -> seasons?.optJSONObject(index) }
        val today = LocalDate.now()
        val active = entries.firstOrNull { entry ->
            val start = parseSeasonDate(entry.optString("start"))
            val end = parseSeasonDate(entry.optString("end"))
            start != null && end != null && !today.isBefore(start) && !today.isAfter(end)
        }?.let { seasonValue(it.opt("season")) }
        if (active != null) return active
        return entries.mapNotNull { seasonValue(it.opt("season")) }.filter { it <= fallback }.maxOrNull() ?: fallback
    }

    private fun seasonValue(raw: Any?): Int? = when (raw) {
        is Number -> raw.toInt()
        else -> raw?.toString()?.take(4)?.toIntOrNull()
    }

    private fun parseSeasonDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

    private suspend fun request(pathAndQuery: String): JSONObject? {
        return try {
            val url = "$baseUrl/$pathAndQuery"
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastErrorMessage = when (response.code) {
                        404 -> "Voleybol veri servisi bulunamadı (HTTP 404)."
                        429 -> "Voleybol veri kotası doldu. Daha sonra yeniden dene."
                        503 -> "Voleybol veri kaynağı sunucuda yapılandırılmamış."
                        else -> "Voleybol verisi alınamadı (HTTP ${response.code})."
                    }
                    null
                } else if (body.isBlank()) {
                    lastErrorMessage = "Voleybol servisi boş yanıt verdi."
                    null
                } else {
                    JSONObject(body).also { root ->
                        val errors = root.opt("errors")
                        val hasErrors = when (errors) {
                            is JSONObject -> errors.length() > 0
                            is JSONArray -> errors.length() > 0
                            is String -> errors.isNotBlank() && errors != "{}"
                            else -> false
                        }
                        if (hasErrors) {
                            lastErrorMessage = "Voleybol sağlayıcısı isteği kabul etmedi."
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            lastErrorMessage = "Voleybol servisine ulaşılamadı. İnternet bağlantısını ve sunucu dağıtımını kontrol et."
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
            if (listOf("cancelled", "canceled", "abandoned").any(statusText::contains)) return@mapNotNull null
            val live = !finished && listOf("live", "progress", "set ", "set1", "set2", "set3", "set4", "set5", "in-play").any(statusText::contains)
            val status = when {
                finished -> MatchStatus.FINISHED
                live -> MatchStatus.LIVE
                else -> MatchStatus.UPCOMING
            }
            val score = item.optJSONObject("scores") ?: JSONObject()
            val (homeSets, awaySets) = parseSetTotals(score.opt("home"), score.opt("away"))
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
                    MatchStatus.UPCOMING -> when {
                        statusText.contains("postpon") -> "ERTELENDİ · $dateTime"
                        statusText.contains("suspend") || statusText.contains("interrupt") -> "DURAKLATILDI · $dateTime"
                        else -> dateTime
                    }
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

    private fun parseSetTotals(home: Any?, away: Any?): Pair<Int?, Int?> {
        val homeTotal = totalSets(home)
        val awayTotal = totalSets(away)
        if (homeTotal != null && awayTotal != null) return homeTotal to awayTotal

        val homeScores = home as? JSONObject ?: return homeTotal to awayTotal
        val awayScores = away as? JSONObject ?: return homeTotal to awayTotal
        var homeWins = 0
        var awayWins = 0
        for (set in 1..5) {
            val homePoints = setPoints(homeScores, set)
            val awayPoints = setPoints(awayScores, set)
            if (homePoints == null || awayPoints == null || homePoints == 0 && awayPoints == 0) continue
            val target = if (set == 5) 15 else 25
            when {
                homePoints >= target && homePoints - awayPoints >= 2 -> homeWins++
                awayPoints >= target && awayPoints - homePoints >= 2 -> awayWins++
            }
        }
        return homeWins.takeIf { it in 0..3 } to awayWins.takeIf { it in 0..3 }
    }

    private fun totalSets(raw: Any?): Int? = when (raw) {
        is Number -> raw.toInt().takeIf { it in 0..3 }
        is JSONObject -> listOf("total", "sets", "score")
            .asSequence()
            .mapNotNull { key -> raw.opt(key)?.toString()?.toIntOrNull() }
            .firstOrNull { it in 0..3 }
        else -> raw?.toString()?.toIntOrNull()?.takeIf { it in 0..3 }
    }

    private fun setPoints(scores: JSONObject, set: Int): Int? {
        val value = scores.opt("set$set") ?: scores.opt(set.toString()) ?: return null
        return value.toString().toIntOrNull()?.takeIf { it >= 0 }
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
        const val GAME_CACHE_MS = 5 * 60_000L
        const val TABLE_CACHE_MS = 6 * 60 * 60_000L
    }
}

