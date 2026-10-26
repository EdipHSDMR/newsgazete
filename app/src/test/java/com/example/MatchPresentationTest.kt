package com.example
import com.example.model.LineupPlayer
import com.example.model.TeamLineup
import com.example.util.FootballFormation
import com.example.util.MatchClock
import org.junit.Assert.*
import org.junit.Test

class MatchPresentationTest {
    @Test fun secondsAreNotDisplayedAsMinutes() {
        assertEquals("7′", MatchClock.football(420.0, "").label)
        assertEquals(7, MatchClock.football(420.0, "7'").minute)
        assertEquals("90+4′", MatchClock.football(5640.0, "90'+4'").label)
        assertEquals("Devre", MatchClock.football(2700.0, "45'", "STATUS_HALFTIME").label)
        assertEquals("Canlı", MatchClock.football(42000.0, "").label)
    }

    @Test fun providerFormationKeepsDefendersAndDefensiveMidfielderOnSeparateLines() {
        val codes = listOf("G", "CD-L", "CD-R", "DM", "LB", "RB", "CM-L", "CM-R", "LM", "RM", "F")
        val team = TeamLineup("Takım", codes.mapIndexed { i, code -> LineupPlayer(code, (i + 1).toString(), code, true) }, "4-1-4-1")
        val positions = FootballFormation.arrange(team)
        assertEquals(11, positions.size)
        assertEquals(listOf(1, 4, 1, 4, 1), positions.groupBy { it.line }.values.map { it.size })
        assertEquals(1, positions.first { it.player.position == "CD-L" }.line)
        assertEquals(2, positions.first { it.player.position == "DM" }.line)
        assertTrue(positions.first { it.player.position == "LB" }.x < positions.first { it.player.position == "CD-L" }.x)
        assertTrue(positions.all { it.x in 0.08f..0.92f && it.y in 0.1f..0.9f })
    }

    @Test fun exactProviderGridTakesPrecedenceOverGeneralPositionNames() {
        val team = TeamLineup("Takım", listOf(
            LineupPlayer("Kaleci", "1", "", true, formationRow = 1, formationColumn = 1),
            LineupPlayer("Sol", "3", "", true, formationRow = 2, formationColumn = 1),
            LineupPlayer("Sağ", "2", "", true, formationRow = 2, formationColumn = 2),
        ))
        val positions = FootballFormation.arrange(team)
        assertEquals(2, positions.count { it.line == 1 })
        assertTrue(positions.first { it.player.name == "Sol" }.x < positions.first { it.player.name == "Sağ" }.x)
    }
}
