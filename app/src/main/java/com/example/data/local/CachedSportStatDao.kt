package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedSportStatDao {

    @Query("SELECT * FROM cached_sport_stats WHERE competition = :competition AND statType = :statType ORDER BY rank ASC")
    fun getStatsByCompetitionAndType(competition: String, statType: String): Flow<List<CachedSportStatEntity>>

    @Query("SELECT * FROM cached_sport_stats WHERE competition = :competition AND statType = :statType ORDER BY rank ASC")
    suspend fun getStatsDirect(competition: String, statType: String): List<CachedSportStatEntity>

    @Query("SELECT * FROM cached_sport_stats WHERE statType = :statType ORDER BY rank ASC")
    fun getAllByType(statType: String): Flow<List<CachedSportStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: List<CachedSportStatEntity>)

    @Query("DELETE FROM cached_sport_stats WHERE competition = :competition AND statType = :statType")
    suspend fun deleteStats(competition: String, statType: String)

    @Query("DELETE FROM cached_sport_stats")
    suspend fun clearAll()
}
