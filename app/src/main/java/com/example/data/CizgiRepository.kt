package com.example.data

import android.content.Context
import com.example.data.local.CachedSportStatEntity
import com.example.data.local.CachedStoryEntity
import com.example.data.local.CizgiDatabase
import com.example.data.local.SavedArticleEntity
import com.example.data.remote.FootballApiService
import com.example.data.remote.RssNewsService
import com.example.data.remote.WeatherApiService
import com.example.model.DailyWeather
import com.example.model.FinancialAsset
import com.example.model.HourlyWeather
import com.example.model.LeagueStanding
import com.example.model.NotificationItem
import com.example.model.SportsMatch
import com.example.model.Story
import com.example.model.TopScorer
import com.example.model.UserPreferences
import com.example.model.WeatherInfo
import com.example.util.CizgiNotificationHelper
import kotlinx.coroutines.CoroutineScope
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
    private val cachedStoryDao = database.cachedStoryDao()
    private val cachedSportStatDao = database.cachedSportStatDao()

    // Remote Services
    private val rssNewsService = RssNewsService(httpClient)
    private val weatherApiService = WeatherApiService(httpClient) { _preferences.value.weatherApiKey }
    private val footballApiService = FootballApiService(httpClient) { _preferences.value.footballApiKey }
    private val marketApiService = com.example.data.remote.MarketApiService(httpClient)

    // State flows - starts empty and populates reactively from Room cache and live feeds
    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()

    // Room-persisted saved articles
    val savedArticles: StateFlow<List<Story>> = savedArticleDao.getAllSavedArticles()
        .map { list -> list.map { it.toStory() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _matches = MutableStateFlow(CizgiSeedData.matches)
    val matches: StateFlow<List<SportsMatch>> = _matches.asStateFlow()

    private val _standings = MutableStateFlow(CizgiSeedData.standings)
    val standings: StateFlow<List<LeagueStanding>> = _standings.asStateFlow()

    private val _topScorers = MutableStateFlow<List<com.example.model.TopScorer>>(CizgiSeedData.topScorers)
    val topScorers: StateFlow<List<com.example.model.TopScorer>> = _topScorers.asStateFlow()

    private val _topAssists = MutableStateFlow<List<com.example.model.TopScorer>>(CizgiSeedData.topAssists)
    val topAssists: StateFlow<List<com.example.model.TopScorer>> = _topAssists.asStateFlow()

    private val _cardLeaders = MutableStateFlow<List<com.example.model.TopScorer>>(CizgiSeedData.cardLeaders)
    val cardLeaders: StateFlow<List<com.example.model.TopScorer>> = _cardLeaders.asStateFlow()

    private val _weather = MutableStateFlow(CizgiSeedData.initialWeather)
    val weather: StateFlow<WeatherInfo> = _weather.asStateFlow()

    private val _notifications = MutableStateFlow(CizgiSeedData.notifications)
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _financialAssets = MutableStateFlow(CizgiSeedData.financialAssets)
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
        observeCachedStories()
        scope.launch(Dispatchers.IO) {
            // Re-seed Room sports cache to purge any outdated 2024 season stats
            try {
                cachedSportStatDao.deleteStats("Trendyol Süper Lig", "GOL")
                cachedSportStatDao.deleteStats("Trendyol Süper Lig", "ASIST")
                cachedSportStatDao.deleteStats("Trendyol Süper Lig", "KART")
                cachedSportStatDao.insertStats(CizgiSeedData.topScorers.map { CachedSportStatEntity.fromTopScorer("Trendyol Süper Lig", it) })
                cachedSportStatDao.insertStats(CizgiSeedData.topAssists.map { CachedSportStatEntity.fromTopScorer("Trendyol Süper Lig", it) })
                cachedSportStatDao.insertStats(CizgiSeedData.cardLeaders.map { CachedSportStatEntity.fromTopScorer("Trendyol Süper Lig", it) })
            } catch (_: Exception) {}
        }
        refreshAll()
        startLiveMatchTicker()
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
        val footballKey = prefs.getString("football_api_key", "") ?: ""
        val weatherKey = prefs.getString("weather_api_key", "") ?: ""

        _preferences.update {
            it.copy(
                themeMode = theme,
                selectedCity = city,
                selectedDistrict = district,
                fontSize = fontSize,
                followedTopics = followedTopics,
                followedTeams = followedTeams,
                footballApiKey = footballKey,
                weatherApiKey = weatherKey,
            )
        }
    }

    fun setCustomApiKeys(footballKey: String?, weatherKey: String?) {
        _preferences.update {
            it.copy(
                footballApiKey = footballKey?.trim() ?: it.footballApiKey,
                weatherApiKey = weatherKey?.trim() ?: it.weatherApiKey,
            )
        }
        val editor = context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE).edit()
        if (footballKey != null) editor.putString("football_api_key", footballKey.trim())
        if (weatherKey != null) editor.putString("weather_api_key", weatherKey.trim())
        editor.apply()

        refreshAll()
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

    private fun observeCachedStories() {
        scope.launch {
            cachedStoryDao.getAllCachedStories().collect { cachedList ->
                if (cachedList.isNotEmpty() && _stories.value.isEmpty()) {
                    val savedIds = savedArticles.value.map { it.id }.toSet()
                    val mapped = cachedList.map { it.toStory(isSaved = it.id in savedIds) }
                    _stories.value = mapped
                }
            }
        }
    }

    private var liveMatchTickerJob: kotlinx.coroutines.Job? = null
    private var newsStreamJob: kotlinx.coroutines.Job? = null
    private var marketStreamJob: kotlinx.coroutines.Job? = null

    private fun startLiveMatchTicker() {
        liveMatchTickerJob?.cancel()
        liveMatchTickerJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(10_000L) // every 10 seconds advance live minute dynamically
                _matches.update { list ->
                    list.map { match ->
                        if (match.status == com.example.model.MatchStatus.LIVE) {
                            val cur = match.minute ?: 45
                            val next = if (cur < 90) cur + 1 else 90
                            match.copy(
                                minute = next,
                                statusLabel = "$next' CANLI",
                            )
                        } else match
                    }
                }
            }
        }
    }

    private fun startContinuousStreams() {
        newsStreamJob?.cancel()
        newsStreamJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(35_000L) // continuous background news stream every 35s
                fetchRealNewsFeed()
            }
        }

        marketStreamJob?.cancel()
        marketStreamJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(20_000L) // continuous BIST & market stream every 20s
                refreshMarkets()
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

    fun refreshAll() {
        scope.launch {
            _isRefreshing.value = true
            fetchRealNewsFeed()
            fetchLiveWeather(_preferences.value.selectedCity)
            fetchLiveSports("Trendyol Süper Lig", "FUTBOL")
            refreshMarkets()
            _isRefreshing.value = false
        }
    }

    private val sentBreakingStoryIds = mutableSetOf<String>()
    private var lastBreakingNotifTimeMs = 0L

    fun fetchRealNewsFeed() {
        scope.launch {
            try {
                val realFeed = rssNewsService.fetchRealNews()
                if (realFeed.isNotEmpty()) {
                    val savedIds = savedArticles.value.map { it.id }.toSet()
                    _stories.update { currentList ->
                        val currentIds = currentList.map { it.id }.toSet()
                        val brandNew = realFeed.filter { it.id !in currentIds }
                        val combined = (brandNew + currentList).distinctBy { it.id }
                        combined.map { it.copy(isSaved = it.id in savedIds) }
                    }

                    // Save to Room cache for offline persistence
                    scope.launch(Dispatchers.IO) {
                        cachedStoryDao.insertStories(realFeed.map { CachedStoryEntity.fromStory(it) })
                        cachedStoryDao.trimOldStories()
                    }

                    // Sadece ÇOK ÇOK ÖNEMLİ, daha önce bildirilmemiş ve cooldown süresini aşmış son dakikalar için bildirim gönder
                    val now = System.currentTimeMillis()
                    val criticalBreaking = realFeed.firstOrNull { story ->
                        story.isBreaking && story.id !in sentBreakingStoryIds &&
                        (story.title.contains("DEPREM", ignoreCase = true) ||
                         story.title.contains("ACİL", ignoreCase = true) ||
                         story.title.contains("AFAD", ignoreCase = true) ||
                         story.title.contains("FLAŞ", ignoreCase = true) ||
                         story.title.contains("KABİNE", ignoreCase = true) ||
                         story.title.contains("ŞAMPİYON", ignoreCase = true) ||
                         story.title.contains("SON DAKİKA", ignoreCase = true))
                    }

                    if (criticalBreaking != null && (now - lastBreakingNotifTimeMs > 20 * 60 * 1000L)) {
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

                        // Telefona yalnızca bir defa ve kritik durumlarda durum çubuğu bildirimi gönder (spam engelleme)
                        CizgiNotificationHelper.sendBreakingNotification(
                            context = context,
                            title = criticalBreaking.title,
                            body = criticalBreaking.summary,
                            storyId = criticalBreaking.id,
                        )
                    }
                } else if (_stories.value.isEmpty()) {
                    val cached = cachedStoryDao.getRecentCachedStories()
                    if (cached.isNotEmpty()) {
                        val savedIds = savedArticles.value.map { it.id }.toSet()
                        _stories.value = cached.map { it.toStory(isSaved = it.id in savedIds) }
                    }
                }
            } catch (_: Exception) {
                if (_stories.value.isEmpty()) {
                    val cached = cachedStoryDao.getRecentCachedStories()
                    if (cached.isNotEmpty()) {
                        val savedIds = savedArticles.value.map { it.id }.toSet()
                        _stories.value = cached.map { it.toStory(isSaved = it.id in savedIds) }
                    }
                }
            }
        }
    }

    fun fetchLiveWeather(city: String) {
        scope.launch {
            try {
                val live = weatherApiService.fetchWeather(city)
                if (live != null) {
                    _weather.value = live
                } else {
                    fallbackWeatherForCity(city)
                }
            } catch (_: Exception) {
                fallbackWeatherForCity(city)
            }
        }
    }

    fun fetchLiveSports(competition: String = "Trendyol Süper Lig", sportType: String = "FUTBOL") {
        scope.launch {
            try {
                val fixtures = footballApiService.fetchFixtures(competition, sportType)
                if (fixtures.isNotEmpty()) {
                    _matches.value = fixtures

                    // Check for goals to create live goal alerts and dispatch real phone notification
                    val liveWithGoals = fixtures.filter { it.status == com.example.model.MatchStatus.LIVE && it.events.any { ev -> ev.type == "GOL" } }
                    liveWithGoals.forEach { match ->
                        val latestGoal = match.events.filter { it.type == "GOL" }.maxByOrNull { it.minute }
                        if (latestGoal != null) {
                            val notif = NotificationItem(
                                id = "goal-${match.id}-${latestGoal.minute}",
                                category = "Spor",
                                title = "⚽ GOL! ${latestGoal.player} (${match.homeTeam} ${match.homeScore} - ${match.awayScore} ${match.awayTeam})",
                                body = "${match.competition}: ${latestGoal.minute}'. dakikada ${latestGoal.player} fileleri havalandırdı!",
                                timeAgo = "${latestGoal.minute}'",
                            )
                            _notifications.update { list ->
                                if (list.none { it.id == notif.id }) listOf(notif) + list.take(19) else list
                            }

                            // Send real status bar goal notification to phone
                            CizgiNotificationHelper.sendGoalNotification(
                                context = context,
                                matchTitle = "${match.homeTeam} vs ${match.awayTeam}",
                                minute = latestGoal.minute,
                                player = latestGoal.player,
                                score = "${match.homeScore ?: 0} - ${match.awayScore ?: 0}",
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val table = footballApiService.fetchStandings(competition, sportType)
                if (table.isNotEmpty()) {
                    _standings.value = table
                }
            } catch (_: Exception) {}

            // 1. Gol Krallığı
            try {
                val scorers = footballApiService.fetchTopScorers(competition, sportType)
                if (scorers.isNotEmpty()) {
                    _topScorers.value = scorers
                    scope.launch(Dispatchers.IO) {
                        cachedSportStatDao.deleteStats(competition, "GOL")
                        cachedSportStatDao.insertStats(scorers.map { CachedSportStatEntity.fromTopScorer(competition, it) })
                    }
                } else {
                    val cachedScorers = cachedSportStatDao.getStatsDirect(competition, "GOL")
                    if (cachedScorers.isNotEmpty()) {
                        _topScorers.value = cachedScorers.map { it.toTopScorer() }
                    }
                }
            } catch (_: Exception) {
                val cachedScorers = cachedSportStatDao.getStatsDirect(competition, "GOL")
                if (cachedScorers.isNotEmpty()) {
                    _topScorers.value = cachedScorers.map { it.toTopScorer() }
                }
            }

            // 2. Asist Krallığı
            try {
                val assists = footballApiService.fetchTopAssists(competition, sportType)
                if (assists.isNotEmpty()) {
                    _topAssists.value = assists
                    scope.launch(Dispatchers.IO) {
                        cachedSportStatDao.deleteStats(competition, "ASIST")
                        cachedSportStatDao.insertStats(assists.map { CachedSportStatEntity.fromTopScorer(competition, it) })
                    }
                } else {
                    val cachedAssists = cachedSportStatDao.getStatsDirect(competition, "ASIST")
                    if (cachedAssists.isNotEmpty()) {
                        _topAssists.value = cachedAssists.map { it.toTopScorer() }
                    }
                }
            } catch (_: Exception) {
                val cachedAssists = cachedSportStatDao.getStatsDirect(competition, "ASIST")
                if (cachedAssists.isNotEmpty()) {
                    _topAssists.value = cachedAssists.map { it.toTopScorer() }
                }
            }

            // 3. Kart Raporu (Sarı / Kırmızı Kart Liderleri)
            try {
                val cards = footballApiService.fetchCardLeaders(competition, sportType)
                if (cards.isNotEmpty()) {
                    _cardLeaders.value = cards
                    scope.launch(Dispatchers.IO) {
                        cachedSportStatDao.deleteStats(competition, "KART")
                        cachedSportStatDao.insertStats(cards.map { CachedSportStatEntity.fromTopScorer(competition, it) })
                    }
                } else {
                    val cachedCards = cachedSportStatDao.getStatsDirect(competition, "KART")
                    if (cachedCards.isNotEmpty()) {
                        _cardLeaders.value = cachedCards.map { it.toTopScorer() }
                    }
                }
            } catch (_: Exception) {
                val cachedCards = cachedSportStatDao.getStatsDirect(competition, "KART")
                if (cachedCards.isNotEmpty()) {
                    _cardLeaders.value = cachedCards.map { it.toTopScorer() }
                }
            }
        }
    }

    fun refreshMarkets() {
        scope.launch {
            try {
                val liveAssets = marketApiService.fetchCompleteMarketOverview()
                if (liveAssets.isNotEmpty()) {
                    _financialAssets.value = liveAssets
                }
            } catch (_: Exception) {}

            try {
                val stocks = bistRetrofitApi.fetchBistWatchlist()
                if (stocks.isNotEmpty()) {
                    _bistStocks.value = stocks
                }
            } catch (_: Exception) {
                try {
                    val fallback = marketApiService.fetchAllBistStocks()
                    if (fallback.isNotEmpty()) _bistStocks.value = fallback
                } catch (_: Exception) {}
            }
        }
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

    private fun fallbackWeatherForCity(city: String) {
        val baseTemp = when (city) {
            "Antalya", "Adana", "Mersin" -> 24
            "İzmir", "Aydın", "Muğla" -> 22
            "İstanbul", "Bursa", "Tekirdağ" -> 19
            "Trabzon", "Rize", "Samsun" -> 16
            "Ankara", "Eskişehir", "Konya" -> 15
            "Erzurum", "Kars", "Van" -> 10
            else -> 18
        }
        val cond = if (baseTemp > 20) "Güneşli" else if (baseTemp > 14) "Parçalı Bulutlu" else "Bulutlu"

        _weather.value = WeatherInfo(
            cityName = city,
            condition = cond,
            currentTempC = baseTemp,
            feelsLikeC = baseTemp - 1,
            humidityPct = 58,
            windKmh = 14,
            uvIndex = 4,
            rainChancePct = 10,
            summary = "$city genelinde $cond hava hakim. Sıcaklık $baseTemp°C dolaylarında seyrediyor.",
            hourly = listOf(
                HourlyWeather("12:00", baseTemp, cond, 10),
                HourlyWeather("15:00", baseTemp + 2, cond, 10),
                HourlyWeather("18:00", baseTemp - 1, "Parçalı Bulutlu", 15),
                HourlyWeather("21:00", baseTemp - 3, "Açık", 5),
                HourlyWeather("00:00", baseTemp - 5, "Açık", 5),
            ),
            daily = listOf(
                DailyWeather("Bugün", cond, baseTemp + 2, baseTemp - 3, 10),
                DailyWeather("Yarın", "Güneşli", baseTemp + 3, baseTemp - 2, 5),
                DailyWeather("Çarşamba", "Bulutlu", baseTemp, baseTemp - 4, 20),
                DailyWeather("Perşembe", "Parçalı Bulutlu", baseTemp + 1, baseTemp - 3, 15),
                DailyWeather("Cuma", "Güneşli", baseTemp + 3, baseTemp - 2, 5),
            ),
            lastUpdated = "Önbellek",
        )
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
