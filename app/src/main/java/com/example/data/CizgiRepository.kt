package com.example.data

import android.content.Context
import com.example.data.local.CizgiDatabase
import com.example.data.local.SavedArticleEntity
import com.example.data.remote.FootballApiService
import com.example.data.remote.RssNewsService
import com.example.data.remote.WeatherApiService
import com.example.model.FinancialAsset
import com.example.model.LeagueStanding
import com.example.model.NotificationItem
import com.example.model.SportsMatch
import com.example.model.Story
import com.example.model.UserPreferences
import com.example.model.WeatherInfo
import com.example.util.CizgiNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CizgiRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Room Database
    private val database = CizgiDatabase.getDatabase(context)
    private val savedArticleDao = database.savedArticleDao()

    // Remote Services
    private val rssNewsService = RssNewsService(httpClient)
    private val weatherApiService = WeatherApiService(httpClient)
    private val footballApiService = FootballApiService(httpClient)
    private val marketApiService = com.example.data.remote.MarketApiService(httpClient)

    // State flows
    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()

    // Room-persisted saved articles
    val savedArticles: StateFlow<List<Story>> = savedArticleDao.getAllSavedArticles()
        .map { list -> list.map { it.toStory() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _matches = MutableStateFlow<List<SportsMatch>>(emptyList())
    val matches: StateFlow<List<SportsMatch>> = _matches.asStateFlow()

    private val _standings = MutableStateFlow<List<LeagueStanding>>(emptyList())
    val standings: StateFlow<List<LeagueStanding>> = _standings.asStateFlow()

    private val _topScorers = MutableStateFlow<List<com.example.model.TopScorer>>(emptyList())
    val topScorers: StateFlow<List<com.example.model.TopScorer>> = _topScorers.asStateFlow()

    private val _weather = MutableStateFlow(
        WeatherInfo(
            cityName = "İstanbul",
            condition = "Veri bekleniyor",
            currentTempC = 0,
            feelsLikeC = 0,
            humidityPct = 0,
            windKmh = 0,
            uvIndex = 0,
            rainChancePct = 0,
            summary = "Güncel hava verisi yükleniyor.",
            hourly = emptyList(),
            daily = emptyList(),
            lastUpdated = "Veri bekleniyor",
        )
    )
    val weather: StateFlow<WeatherInfo> = _weather.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _financialAssets = MutableStateFlow<List<FinancialAsset>>(emptyList())
    val financialAssets: StateFlow<List<com.example.model.FinancialAsset>> = _financialAssets.asStateFlow()

    private val _bistStocks = MutableStateFlow<List<com.example.data.remote.BISTStock>>(emptyList())
    val bistStocks: StateFlow<List<com.example.data.remote.BISTStock>> = _bistStocks.asStateFlow()

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val bistRetrofitApi = com.example.data.remote.BistRetrofitApi(httpClient)

    init {
        loadSavedPreferences()
        observeSavedArticles()
        refreshAll()
        startContinuousStreams()
    }

    private fun loadSavedPreferences() {
        val prefs = context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
        val theme = prefs.getString("theme", "light") ?: "light"
        val city = prefs.getString("city", "İstanbul") ?: "İstanbul"
        val district = prefs.getString("district", null)
        val fontSize = prefs.getString("font_size", "normal") ?: "normal"
        val followedTopics = prefs.getStringSet("followed_topics", setOf("Türkiye", "Ekonomi", "Teknoloji", "Spor")) ?: emptySet()
        val followedTeams = prefs.getStringSet("followed_teams", setOf("Galatasaray", "Fenerbahçe")) ?: emptySet()

        _preferences.update {
            it.copy(
                themeMode = theme,
                selectedCity = city,
                selectedDistrict = district,
                fontSize = fontSize,
                followedTopics = followedTopics,
                followedTeams = followedTeams,
            )
        }
    }

    private fun observeSavedArticles() {
        scope.launch {
            savedArticles.collect { savedList ->
                val savedIds = savedList.map { it.id }.toSet()
                _stories.update { storiesList ->
                    storiesList.map { story ->
                        story.copy(isSaved = story.id in savedIds)
                    }
                }
            }
        }
    }

    private var newsStreamJob: kotlinx.coroutines.Job? = null
    private var marketStreamJob: kotlinx.coroutines.Job? = null
    private var weatherStreamJob: kotlinx.coroutines.Job? = null
    private var sportsStreamJob: kotlinx.coroutines.Job? = null

    private fun startContinuousStreams() {
        newsStreamJob?.cancel()
        newsStreamJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(15 * 60_000L)
                refreshNewsData()
            }
        }

        marketStreamJob?.cancel()
        marketStreamJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(10 * 60_000L)
                refreshMarketData()
            }
        }

        weatherStreamJob?.cancel()
        weatherStreamJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(30 * 60_000L)
                refreshWeatherData(locationQuery())
            }
        }

        sportsStreamJob?.cancel()
        sportsStreamJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(60 * 60_000L)
                refreshSportsData("Trendyol Süper Lig", "FUTBOL")
            }
        }
    }

    // Room Database 'Sonra Oku' actions
    fun toggleSaveStory(story: Story) {
        scope.launch(Dispatchers.IO) {
            val isCurrentlySaved = savedArticleDao.getSavedArticleById(story.id) != null
            if (isCurrentlySaved) {
                savedArticleDao.deleteArticleById(story.id)
            } else {
                savedArticleDao.insertArticle(SavedArticleEntity.fromStory(story))
            }
        }
    }

    fun toggleSaveStory(storyId: String) {
        toggleSaveStoryById(storyId)
    }

    fun toggleSaveStoryById(storyId: String) {
        val target = _stories.value.firstOrNull { it.id == storyId }
            ?: savedArticles.value.firstOrNull { it.id == storyId }
        if (target != null) {
            toggleSaveStory(target)
        }
    }

    fun clearSavedStories() {
        scope.launch(Dispatchers.IO) {
            savedArticleDao.clearAll()
        }
    }

    fun selectCity(cityName: String) {
        selectCityAndDistrict(cityName, null)
    }

    fun selectCityAndDistrict(city: String, district: String?) {
        _preferences.update { it.copy(selectedCity = city, selectedDistrict = district) }
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("city", city)
            .putString("district", district)
            .apply()
        val locationQuery = if (!district.isNullOrBlank()) "$district, $city" else city
        fetchLiveWeather(locationQuery)
    }

    private val refreshMutex = Mutex()
    private val sentBreakingStoryIds = mutableSetOf<String>()
    private val sentGoalAlertIds = mutableSetOf<String>()
    private var lastBreakingNotifTimeMs = 0L

    fun refreshAll() {
        scope.launch {
            if (!refreshMutex.tryLock()) return@launch
            _isRefreshing.value = true
            try {
                coroutineScope {
                    launch { refreshNewsData() }
                    launch { refreshWeatherData(locationQuery()) }
                    launch { refreshSportsData("Trendyol Süper Lig", "FUTBOL") }
                    launch { refreshMarketData() }
                }
            } finally {
                _isRefreshing.value = false
                refreshMutex.unlock()
            }
        }
    }

    private fun locationQuery(): String {
        val preferences = _preferences.value
        return if (!preferences.selectedDistrict.isNullOrBlank()) {
            "${preferences.selectedDistrict}, ${preferences.selectedCity}"
        } else {
            preferences.selectedCity
        }
    }

    fun fetchRealNewsFeed() {
        scope.launch { refreshNewsData() }
    }

    private suspend fun refreshNewsData() {
        try {
            val liveFeed = rssNewsService.fetchRealNews().take(60)
            if (liveFeed.isEmpty()) return

            val savedIds = savedArticles.value.map { it.id }.toSet()
            _stories.value = liveFeed.map { it.copy(isSaved = it.id in savedIds) }

            val now = System.currentTimeMillis()
            val criticalBreaking = liveFeed.firstOrNull { story ->
                story.isBreaking && story.id !in sentBreakingStoryIds &&
                    (story.title.contains("DEPREM", ignoreCase = true) ||
                     story.title.contains("ACİL", ignoreCase = true) ||
                     story.title.contains("AFAD", ignoreCase = true) ||
                     story.title.contains("FLAŞ", ignoreCase = true) ||
                     story.title.contains("KABİNE", ignoreCase = true) ||
                     story.title.contains("ŞAMPİYON", ignoreCase = true) ||
                     story.title.contains("SON DAKİKA", ignoreCase = true))
            }

            if (criticalBreaking != null && now - lastBreakingNotifTimeMs > 20 * 60 * 1000L) {
                sentBreakingStoryIds.add(criticalBreaking.id)
                lastBreakingNotifTimeMs = now
                val alert = NotificationItem(
                    id = "alert-${criticalBreaking.id}",
                    category = "Son Dakika",
                    title = criticalBreaking.title,
                    body = criticalBreaking.summary,
                    timeAgo = "Az önce",
                    storyId = criticalBreaking.id,
                )
                _notifications.update { list ->
                    if (list.none { it.storyId == criticalBreaking.id }) listOf(alert) + list.take(25) else list
                }
                CizgiNotificationHelper.sendBreakingNotification(
                    context = context,
                    title = criticalBreaking.title,
                    body = criticalBreaking.summary,
                    storyId = criticalBreaking.id,
                )
            }
        } catch (_: Exception) {}
    }

    fun fetchLiveWeather(city: String) {
        scope.launch { refreshWeatherData(city) }
    }

    private suspend fun refreshWeatherData(city: String) {
        try {
            val live = weatherApiService.fetchWeather(city) ?: return
            _weather.value = live
        } catch (_: Exception) {}
    }

    fun fetchLiveSports(competition: String = "Trendyol Süper Lig", sportType: String = "FUTBOL") {
        scope.launch { refreshSportsData(competition, sportType) }
    }

    private suspend fun refreshSportsData(competition: String, sportType: String) {
        try {
            val fixtures = footballApiService.fetchFixtures(competition, sportType)
            _matches.value = fixtures

            fixtures.asSequence()
                .filter { it.status == com.example.model.MatchStatus.LIVE }
                .flatMap { match -> match.events.asSequence().filter { it.type == "GOL" }.map { match to it } }
                .forEach { (match, goal) ->
                    val alertId = "goal-${match.id}-${goal.minute}"
                    if (sentGoalAlertIds.add(alertId)) {
                        val notif = NotificationItem(
                            id = alertId,
                            category = "Spor",
                            title = "⚽ GOL! ${goal.player} (${match.homeTeam} ${match.homeScore} - ${match.awayScore} ${match.awayTeam})",
                            body = "${match.competition}: ${goal.minute}'. dakikada ${goal.player} fileleri havalandırdı!",
                            timeAgo = "${goal.minute}'",
                        )
                        _notifications.update { list -> listOf(notif) + list.take(19) }
                        CizgiNotificationHelper.sendGoalNotification(
                            context = context,
                            matchTitle = "${match.homeTeam} vs ${match.awayTeam}",
                            minute = goal.minute,
                            player = goal.player,
                            score = "${match.homeScore ?: 0} - ${match.awayScore ?: 0}",
                        )
                    }
                }
        } catch (_: Exception) {}

        try {
            _standings.value = footballApiService.fetchStandings(competition, sportType)
        } catch (_: Exception) {}

        try {
            _topScorers.value = footballApiService.fetchTopScorers(competition, sportType)
        } catch (_: Exception) {}
    }

    fun refreshMarkets() {
        scope.launch { refreshMarketData() }
    }

    private suspend fun refreshMarketData() {
        try {
            val assets = marketApiService.fetchCompleteMarketOverview()
            if (assets.isNotEmpty()) _financialAssets.value = assets
        } catch (_: Exception) {}

        try {
            val stocks = bistRetrofitApi.fetchBistWatchlist()
            if (stocks.isNotEmpty()) _bistStocks.value = stocks
        } catch (_: Exception) {}
    }

    fun toggleFollowTopic(topic: String) {
        val current = _preferences.value.followedTopics.toMutableSet()
        if (topic in current) current.remove(topic) else current.add(topic)
        _preferences.update { it.copy(followedTopics = current) }
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("followed_topics", current).apply()
    }

    fun toggleFollowTeam(team: String) {
        val current = _preferences.value.followedTeams.toMutableSet()
        if (team in current) current.remove(team) else current.add(team)
        _preferences.update { it.copy(followedTeams = current) }
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("followed_teams", current).apply()
    }

    fun setThemeMode(mode: String) {
        _preferences.update { it.copy(themeMode = mode) }
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().putString("theme", mode).apply()
    }

    fun setFontSize(size: String) {
        _preferences.update { it.copy(fontSize = size) }
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().putString("font_size", size).apply()
    }

    fun setQuietHours(enabled: Boolean, start: String = "23:00", end: String = "07:00") {
        _preferences.update {
            it.copy(quietHoursEnabled = enabled, quietHoursStart = start, quietHoursEnd = end)
        }
    }

    fun clearSearchHistory() {}

    fun markNotificationRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
    }

    fun markAllNotificationsRead() {
        _notifications.update { list -> list.map { it.copy(isRead = true) } }
    }

    companion object {
        @Volatile
        private var instance: CizgiRepository? = null

        fun getInstance(context: Context): CizgiRepository {
            return instance ?: synchronized(this) {
                instance ?: CizgiRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
