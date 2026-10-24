package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.TopScorer

@Entity(tableName = "cached_sport_stats")
data class CachedSportStatEntity(
    @PrimaryKey
    val id: String, // e.g. "comp-type-rank-player"
    val competition: String,
    val statType: String, // "GOL", "ASIST", "KART", "SAYI"
    val rank: Int,
    val player: String,
    val team: String,
    val count: Int,
    val secondaryCount: Int = 0,
    val matches: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val penalties: Int = 0,
    val fetchedTimestamp: Long = System.currentTimeMillis(),
) {
    fun toTopScorer(): TopScorer {
        return TopScorer(
            rank = rank,
            player = player,
            team = team,
            count = count,
            secondaryCount = secondaryCount,
            matches = matches,
            type = statType,
            yellowCards = yellowCards,
            redCards = redCards,
            penalties = penalties,
        )
    }

    companion object {
        fun fromTopScorer(competition: String, scorer: TopScorer): CachedSportStatEntity {
            val safeId = "${competition.take(10)}-${scorer.type}-${scorer.rank}-${scorer.player.take(15)}".replace(" ", "_")
            return CachedSportStatEntity(
                id = safeId,
                competition = competition,
                statType = scorer.type,
                rank = scorer.rank,
                player = scorer.player,
                team = scorer.team,
                count = scorer.count,
                secondaryCount = scorer.secondaryCount,
                matches = scorer.matches,
                yellowCards = scorer.yellowCards,
                redCards = scorer.redCards,
                penalties = scorer.penalties,
                fetchedTimestamp = System.currentTimeMillis(),
            )
        }
    }
}
