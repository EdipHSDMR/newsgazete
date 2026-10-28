package com.example.util

import com.example.model.LineupPlayer
import com.example.model.TeamLineup
import java.text.Normalizer
import java.util.Locale

object MatchClock {
    data class Reading(val label: String, val minute: Int?)

    fun football(seconds: Double, displayClock: String, statusName: String = ""): Reading {
        if (statusName.contains("HALFTIME") || statusName == "HT") return Reading("Devre", 45)
        if (statusName.contains("PENALT") || statusName == "PEN") return Reading("Penaltılar", 120)
        val display = displayClock.replace('’', '\'').replace('′', '\'').trim()
        val shown = Regex("^(\\d{1,3})(?:\\s*['+]?\\s*\\+\\s*(\\d{1,2}))?['\\s]*$").matchEntire(display)
        if (shown != null) {
            val minute = shown.groupValues[1].toIntOrNull()
            val extra = shown.groupValues[2].toIntOrNull()
            if (minute != null && minute in 0..150) {
                return Reading(if (extra != null && extra > 0) "$minute+$extra′" else "$minute′", minute)
            }
        }
        val minute = (seconds / 60.0).toInt().takeIf { seconds.isFinite() && seconds >= 0 && it in 0..150 }
        return Reading(minute?.let { "$it′" } ?: "Canlı", minute)
    }
}

data class PitchPosition(val player: LineupPlayer, val x: Float, val y: Float, val line: Int)

/** Uses the provider's grid or formation, then orders players by their reported position. */
object FootballFormation {
    fun arrange(team: TeamLineup): List<PitchPosition> {
        val starters = team.players.filter { it.starter }
        if (starters.isEmpty()) return emptyList()
        if (starters.all { it.formationRow != null && it.formationColumn != null }) {
            val rows = starters.groupBy { it.formationRow!! }.toSortedMap().values.toList()
            return rows.flatMapIndexed { line, players ->
                positions(players.sortedBy { it.formationColumn }, line, rows.size)
            }
        }
        val keepers = starters.filter { depth(it) == 0 }
        val outfield = starters.filterNot { it in keepers }.sortedWith(compareBy<LineupPlayer> { depth(it) }.thenBy { side(it) })
        val counts = team.formation.split('-').mapNotNull(String::toIntOrNull)
        val rows = mutableListOf<List<LineupPlayer>>()
        if (keepers.isNotEmpty()) rows += keepers
        if (counts.isNotEmpty() && counts.all { it in 1..5 } && counts.sum() == outfield.size) {
            var offset = 0
            counts.forEach { count ->
                rows += outfield.subList(offset, offset + count).sortedBy(::side)
                offset += count
            }
        } else {
            val groups = outfield.groupBy(::depth).toSortedMap().values.toList()
            if (groups.any { it.size > 5 }) return emptyList()
            rows += groups.map { it.sortedBy(::side) }
        }
        return rows.flatMapIndexed { line, players -> positions(players, line, rows.size) }
    }

    private fun positions(players: List<LineupPlayer>, line: Int, lines: Int): List<PitchPosition> =
        players.mapIndexed { index, player ->
            PitchPosition(player, (index + 1f) / (players.size + 1f), 0.87f - 0.73f * line / maxOf(1, lines - 1), line)
        }

    private fun depth(player: LineupPlayer): Int {
        val code = player.position.uppercase(Locale.ROOT)
        return when {
            code in setOf("G", "GK") || "KEEPER" in code -> 0
            code.startsWith("DM") -> 20
            code.startsWith("AM") -> 40
            code in setOf("D", "DF", "LB", "RB", "LWB", "RWB", "SW") || code.startsWith("CD") || code.startsWith("CB") -> 10
            code.startsWith("F") || code in setOf("ST", "CF", "LW", "RW", "SS") -> 50
            else -> 30
        }
    }

    private fun side(player: LineupPlayer): Int {
        val code = player.position.uppercase(Locale.ROOT)
        return when {
            code in setOf("LB", "LM", "LW", "LWB") -> -2
            code.endsWith("-L") -> -1
            code in setOf("RB", "RM", "RW", "RWB") -> 2
            code.endsWith("-R") -> 1
            else -> 0
        }
    }
}

/** Conservative provider-name matching for lineup and fixture attribution. */
object TeamNameMatcher {
    private val ignored = setOf("fc", "cf", "afc", "fk", "sk", "club", "the", "de")

    fun matches(first: String, second: String): Boolean {
        val left = tokens(first)
        val right = tokens(second)
        if (left.isEmpty() || right.isEmpty()) return false
        if (left == right) return true
        // Allow a provider to append a club suffix, but do not match teams that merely share
        // a city or first word (for example Manchester City and Manchester United).
        if (minOf(left.size, right.size) < 2) return false
        return left.containsAll(right) || right.containsAll(left)
    }

    private fun tokens(value: String): Set<String> = Normalizer.normalize(
        value.lowercase(Locale.ROOT),
        Normalizer.Form.NFD,
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it !in ignored }
        .toSet()
}
