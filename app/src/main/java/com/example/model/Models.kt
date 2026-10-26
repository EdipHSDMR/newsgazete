package com.example.model

enum class MatchStatus {
    LIVE,
    UPCOMING,
    FINISHED
}

data class SourceComparison(
    val publisher: String,
    val headline: String,
    val angle: String,
    val publishedTime: String,
    val url: String = "",
)

data class Story(
    val id: String,
    val title: String,
    val summary: String,
    val topic: String,
    val publisher: String,
    val publishedAt: String,
    val publishedAtEpochMs: Long = 0L,
    val readTimeMin: Int = 2,
    val imageUrl: String = "",
    val isBreaking: Boolean = false,
    val isManset: Boolean = false,
    val sourceCount: Int = 1,
    val whatHappened: String = "",
    val whyItMatters: String = "",
    val whatIsUnknown: String = "",
    val fullText: String = "",
    val paragraphs: List<String> = emptyList(),
    val comparisons: List<SourceComparison> = emptyList(),
    val canonicalUrl: String = "",
    val isSaved: Boolean = false,
)

data class MatchEvent(
    val minute: Int,
    val team: String,
    val player: String,
    val type: String, // "GOL", "SARI", "KIRMIZI", "PENALTI"
)

data class SportsMatch(
    val id: String,
    val competition: String,
    val round: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val minute: Int? = null,
    val status: MatchStatus,
    val statusLabel: String,
    val kickoffTime: String,
    val kickoffAtEpochMs: Long = 0L,
    val events: List<MatchEvent> = emptyList(),
    val sportType: String = "FUTBOL",
    val liveAction: String = "",       // e.g. "⚽ GOL! 78' Icardi"
    val oddsHome: String = "",
    val oddsDraw: String = "",
    val oddsAway: String = "",
    val possessionHome: Int? = null,
    val possessionAway: Int? = null,
    val shotsHome: Int? = null,
    val shotsAway: Int? = null,
    val shotsOnTargetHome: Int? = null,
    val shotsOnTargetAway: Int? = null,
    val cornersHome: Int? = null,
    val cornersAway: Int? = null,
    val referee: String = "",
    val stadium: String = "",
    val dataProvider: String = "",
)

data class SportsSnapshot(
    val competition: String = "",
    val sportType: String = "",
    val seasonYear: Int = 0,
    val matches: List<SportsMatch> = emptyList(),
    val standings: List<LeagueStanding> = emptyList(),
    val topScorers: List<TopScorer> = emptyList(),
    val isLoading: Boolean = false,
    val statsLoading: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
    val scorerMessage: String = "",
    val errorMessage: String = "",
)

data class LineupPlayer(
    val name: String,
    val jerseyNumber: String = "",
    val position: String = "",
    val starter: Boolean = false,
    val imageUrl: String = "",
    val formationRow: Int? = null,
    val formationColumn: Int? = null,
    val formationSlot: Int? = null,
)

data class TeamLineup(
    val team: String,
    val players: List<LineupPlayer>,
    val formation: String = "",
)

data class MatchLineups(
    val home: TeamLineup,
    val away: TeamLineup,
)

data class TopScorer(
    val rank: Int,
    val player: String,
    val team: String,
    val count: Int,             // Goals, assists or points
    val secondaryCount: Int = 0, // Assists or rebounds
    val matches: Int = 0,
    val type: String = "GOL",    // "GOL", "ASIST", "SAYI"
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val penalties: Int = 0,
    val valueLabel: String = "",
)

enum class MarketCategory(val label: String) {
    CURRENCY("Döviz"),
    METAL("Altın"),
    COMMODITY("Emtia"),
    CRYPTO("Kripto"),
}

data class FinancialAsset(
    val code: String,          // e.g. "USD/TRY", "EUR/TRY", "GA", "BIST100", "BTC"
    val name: String,          // e.g. "Dolar", "Euro", "Gram Altın", "BIST 100", "Bitcoin"
    val symbol: String,        // "$", "€", "₺", "₿"
    val currentPrice: Double,
    val changePct: Double? = null, // null when the source does not report a real change
    val highPrice: Double? = null,
    val lowPrice: Double? = null,
    val unit: String = "₺",
    val sparkline: List<Float> = emptyList(),
    val category: MarketCategory = MarketCategory.CURRENCY,
    val source: String = "",
    val updatedAtEpochMs: Long = 0L,
    val quoteNote: String = "",
    val buyPrice: Double? = null,
    val sellPrice: Double? = null,
    val isEstimated: Boolean = false,
    val changePeriodLabel: String = "Günlük",
)

data class LeagueStanding(
    val rank: Int,
    val team: String,
    val played: Int,
    val won: Int,
    val drawn: Int,
    val lost: Int,
    val goalDiff: Int,
    val points: Int,
    val form: String = "—",
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    val qualificationNote: String? = null,
    val groupName: String? = null,
    val winPercentage: Double? = null,
)

data class HourlyWeather(
    val hour: String,
    val tempC: Int,
    val condition: String,
    val rainProbPct: Int,
)

data class DailyWeather(
    val dayName: String,
    val condition: String,
    val maxTempC: Int,
    val minTempC: Int,
    val rainProbPct: Int,
)

data class WeatherInfo(
    val cityName: String,
    val condition: String,
    val currentTempC: Int?,
    val feelsLikeC: Int?,
    val humidityPct: Int?,
    val windKmh: Int?,
    val uvIndex: Int?,
    val rainChancePct: Int?,
    val summary: String,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
    val lastUpdated: String = "Az önce",
)

data class NotificationItem(
    val id: String,
    val category: String, // "Son Dakika", "Spor", "Konular", "Gündem"
    val title: String,
    val body: String,
    val timeAgo: String,
    val isRead: Boolean = false,
    val storyId: String? = null,
)

data class UserPreferences(
    val themeMode: String = "light", // "light", "dark", "system"
    val fontSize: String = "normal",  // "normal", "large"
    val selectedCity: String = "İstanbul",
    val selectedDistrict: String? = null,
    val followedTopics: Set<String> = emptySet(),
    val followedTeams: Set<String> = emptySet(),
    val breakingAlerts: Boolean = true,
    val matchAlerts: Boolean = false,
    val dailyDigest: Boolean = true,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: String = "23:00",
    val quietHoursEnd: String = "07:00",
)
