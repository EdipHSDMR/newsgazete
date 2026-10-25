package com.example.data

import android.content.Context
import com.example.data.local.CizgiDatabase
import com.example.data.local.CachedSportStatEntity
import com.example.data.local.NewsArticleEntity
import com.example.data.local.SavedArticleEntity
import com.example.data.remote.FootballApiService
import com.example.data.remote.PressNewsApiService
import com.example.data.remote.RssNewsService
import com.example.data.remote.WeatherApiService
import com.example.model.FinancialAsset
import com.example.model.LeagueStanding
import com.example.model.NotificationItem
import com.example.model.SportsMatch
import com.example.model.SportsSnapshot
import com.example.model.Story
import com.example.model.UserPreferences
import com.example.model.WeatherInfo
import com.example.util.CizgiNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    private val cachedSportStatDao = database.cachedSportStatDao()
    private val newsArticleDao = database.newsArticleDao()
    private val imageEnrichmentAttempts = mutableMapOf<String, Long>()
    private val pendingImageEnrichment = mutableMapOf<String, Story>()
    private var imageEnrichmentJob: Job? = null
    private var pressArchiveNextPage = 2
    private var pressArchiveTotalPages = 0

    // Remote Services
    private val rssNewsService = RssNewsService(httpClient)
    private val pressNewsApiService = PressNewsApiService(httpClient) { _preferences.value.pressApiKey }
    private val weatherApiService = WeatherApiService(httpClient) { _preferences.value.weatherApiKey }
    private val footballApiService = FootballApiService(httpClient) { _preferences.value.footballApiKey }
    private val marketApiService = com.example.data.remote.MarketApiService(httpClient)

    // State flows
    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()
    val newsArchiveCount: StateFlow<Int> = newsArticleDao.observeCount()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    // Room-persisted saved articles
    val savedArticles: StateFlow<List<Story>> = savedArticleDao.getAllSavedArticles()
        .map { list -> list.map { it.toStory() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _sportsSnapshot = MutableStateFlow(SportsSnapshot())
    val sportsSnapshot: StateFlow<SportsSnapshot> = _sportsSnapshot.asStateFlow()
    val matches: StateFlow<List<SportsMatch>> = _sportsSnapshot
        .map { it.matches }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val standings: StateFlow<List<LeagueStanding>> = _sportsSnapshot
        .map { it.standings }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val topScorers: StateFlow<List<com.example.model.TopScorer>> = _sportsSnapshot
        .map { it.topScorers }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

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

    private val refreshMutex = Mutex()
    private val sentBreakingStoryIds = mutableSetOf<String>()
    private val sentGoalAlertIds = mutableSetOf<String>()
    private var lastBreakingNotifTimeMs = 0L

    private var newsStreamJob: kotlinx.coroutines.Job? = null
    private var marketStreamJob: kotlinx.coroutines.Job? = null
    private var weatherStreamJob: kotlinx.coroutines.Job? = null
    private var activeSportsKey: String? = null
    private var sportsRefreshJob: Job? = null
    private val newsPageMutex = Mutex()
    private val sourceDescriptionAttempts = mutableMapOf<String, Long>()

    init {
        loadSavedPreferences()
        observeSavedArticles()
        observeNewsArchive()
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
        val footballKey = prefs.getString("football_api_key", "") ?: ""
        val weatherKey = prefs.getString("weather_api_key", "") ?: ""
        val pressKey = prefs.getString("press_api_key", "") ?: ""
        pressArchiveNextPage = prefs.getInt("press_archive_next_page", 2).coerceAtLeast(2)
        pressArchiveTotalPages = prefs.getInt("press_archive_total_pages", 0).coerceAtLeast(0)

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
                pressApiKey = pressKey,
            )
        }
    }

    fun setCustomApiKeys(footballKey: String?, weatherKey: String?, pressKey: String? = null) {
        val oldPressKey = _preferences.value.pressApiKey
        _preferences.update {
            it.copy(
                footballApiKey = footballKey?.trim() ?: it.footballApiKey,
                weatherApiKey = weatherKey?.trim() ?: it.weatherApiKey,
                pressApiKey = pressKey?.trim() ?: it.pressApiKey,
            )
        }
        val editor = context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE).edit()
        if (footballKey != null) editor.putString("football_api_key", footballKey.trim())
        if (weatherKey != null) editor.putString("weather_api_key", weatherKey.trim())
        if (pressKey != null) {
            editor.putString("press_api_key", pressKey.trim())
            if (oldPressKey != pressKey.trim()) {
                pressArchiveNextPage = 2
                pressArchiveTotalPages = 0
                editor.putInt("press_archive_next_page", pressArchiveNextPage)
                editor.putInt("press_archive_total_pages", pressArchiveTotalPages)
            }
        }
        editor.apply()
        footballApiService.resetApiFootballState()
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

    }

    private fun observeNewsArchive() {
        scope.launch {
            newsArticleDao.observeLatest(NEWS_PAGE_SIZE).collect { latestArticles ->
                val savedIds = savedArticles.value.map { it.id }.toSet()
                val latest = latestArticles.map { it.toStory().copy(isSaved = it.id in savedIds) }
                _stories.update { current ->
                    val latestIds = latest.mapTo(mutableSetOf()) { it.id }
                    (latest + current.filterNot { it.id in latestIds })
                        .distinctBy { it.id }
                        .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                        .take(MAX_IN_MEMORY_STORIES)
                }
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
        val activeSelection = activeSportsKey?.split("|", limit = 2)
        fetchLiveSports(
            competition = activeSelection?.getOrNull(1) ?: "Trendyol Süper Lig",
            sportType = activeSelection?.getOrNull(0) ?: "FUTBOL",
        )
        scope.launch {
            if (!refreshMutex.tryLock()) return@launch
            _isRefreshing.value = true
            try {
                coroutineScope {
                    launch { refreshNewsData() }
                    launch { refreshWeatherData(locationQuery()) }
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
            val pressKey = _preferences.value.pressApiKey
            val (rssStories, pressPage) = coroutineScope {
                val rss = async { rssNewsService.fetchRealNews() }
                val press = async { if (pressKey.isNotBlank()) pressNewsApiService.fetchPage(page = 1) else null }
                rss.await() to press.await()
            }
            pressPage?.let { page ->
                pressArchiveTotalPages = page.totalPages.coerceAtLeast(page.page)
                if (pressArchiveNextPage > pressArchiveTotalPages && pressArchiveTotalPages > 1) {
                    pressArchiveNextPage = 2
                }
                persistPressArchiveProgress()
            }
            val liveFeed = mergePressAndRss(pressPage?.stories.orEmpty(), rssStories)
            if (liveFeed.isEmpty()) return

            newsArticleDao.upsertAll(liveFeed.map(NewsArticleEntity::fromStory))
            newsArticleDao.pruneOldest(MAX_ARCHIVE_ARTICLES)

            val savedIds = savedArticles.value.map { it.id }.toSet()
            _stories.update { current ->
                val incoming = liveFeed.map { it.copy(isSaved = it.id in savedIds) }
                val incomingIds = incoming.mapTo(mutableSetOf()) { it.id }
                (incoming + current.filterNot { it.id in incomingIds })
                    .distinctBy { it.id }
                    .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                    .take(MAX_IN_MEMORY_STORIES)
            }

            enrichMissingStoryImages(_stories.value.take(NEWS_PAGE_SIZE))

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

    private fun mergePressAndRss(pressStories: List<Story>, rssStories: List<Story>): List<Story> {
        val pressUrls = pressStories.mapNotNullTo(mutableSetOf()) {
            normalizeNewsUrl(it.canonicalUrl).takeIf(String::isNotBlank)
        }
        val pressTitles = pressStories.mapTo(mutableSetOf()) { normalizeNewsTitle(it.title) }
        val combined = pressStories + rssStories.filterNot { story ->
            val url = normalizeNewsUrl(story.canonicalUrl)
            val title = normalizeNewsTitle(story.title)
            (url.isNotBlank() && url in pressUrls) || title in pressTitles
        }
        return combined.distinctBy { it.id }
            .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
            .take(MAX_STORIES_PER_REFRESH)
    }

    private suspend fun loadNextPressArchivePage() {
        if (_preferences.value.pressApiKey.isBlank() || pressArchiveNextPage > pressArchiveTotalPages) return
        val pageNumber = pressArchiveNextPage
        val page = pressNewsApiService.fetchPage(page = pageNumber) ?: return
        pressArchiveTotalPages = maxOf(pressArchiveTotalPages, page.totalPages)
        val savedIds = savedArticles.value.map { it.id }.toSet()
        val incoming = page.stories.map { it.copy(isSaved = it.id in savedIds) }

        if (incoming.isNotEmpty()) {
            newsArticleDao.upsertAll(incoming.map(NewsArticleEntity::fromStory))
            newsArticleDao.pruneOldest(MAX_ARCHIVE_ARTICLES)
            _stories.update { current ->
                (current + incoming)
                    .distinctBy { it.id }
                    .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                    .take(MAX_IN_MEMORY_STORIES)
            }
            enrichMissingStoryImages(incoming)
        }

        pressArchiveNextPage = pageNumber + 1
        persistPressArchiveProgress()
    }

    private fun persistPressArchiveProgress() {
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("press_archive_next_page", pressArchiveNextPage)
            .putInt("press_archive_total_pages", pressArchiveTotalPages)
            .apply()
    }

    private fun normalizeNewsTitle(title: String): String = title
        .lowercase(java.util.Locale.forLanguageTag("tr-TR"))
        .replace("[^a-z0-9çğıöşü]".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

    private fun normalizeNewsUrl(url: String): String = url
        .trim()
        .substringBefore('#')
        .trimEnd('/')
        .lowercase(java.util.Locale.ROOT)

    fun fetchLiveWeather(city: String) {
        scope.launch { refreshWeatherData(city) }
    }

    private suspend fun refreshWeatherData(city: String) {
        try {
            val live = weatherApiService.fetchWeather(city) ?: return
            _weather.value = live
        } catch (_: Exception) {}
    }

    fun loadOlderNews() {
        scope.launch {
            if (!newsPageMutex.tryLock()) return@launch
            try {
                val oldest = _stories.value.minWithOrNull(
                    compareBy<Story> { it.publishedAtEpochMs }.thenBy { it.id }
                ) ?: return@launch
                if (_stories.value.size >= MAX_IN_MEMORY_STORIES) return@launch

                val remainingCapacity = MAX_IN_MEMORY_STORIES - _stories.value.size
                val older = newsArticleDao.loadOlder(
                    timestamp = oldest.publishedAtEpochMs,
                    id = oldest.id,
                    limit = minOf(NEWS_PAGE_SIZE, remainingCapacity),
                )
                if (older.isNotEmpty()) {
                    val savedIds = savedArticles.value.map { it.id }.toSet()
                    val olderStories = older.map { it.toStory().copy(isSaved = it.id in savedIds) }
                    _stories.update { current ->
                        val currentIds = current.mapTo(mutableSetOf()) { it.id }
                        (current + olderStories.filterNot { it.id in currentIds })
                            .take(MAX_IN_MEMORY_STORIES)
                    }
                    enrichMissingStoryImages(olderStories)
                }
                if (older.size < NEWS_PAGE_SIZE) loadNextPressArchivePage()
            } catch (_: Exception) {
            } finally {
                newsPageMutex.unlock()
            }
        }
    }

    fun refreshStoryDescription(storyId: String) {
        val story = _stories.value.firstOrNull { it.id == storyId } ?: return
        if (story.canonicalUrl.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - (sourceDescriptionAttempts[storyId] ?: 0L) < SOURCE_DESCRIPTION_RETRY_MS) return
        sourceDescriptionAttempts[storyId] = now

        scope.launch {
            try {
                val metadata = rssNewsService.fetchSourceMetadata(story.canonicalUrl) ?: return@launch
                val description = metadata.description?.takeIf { it.length >= MIN_SOURCE_DESCRIPTION_CHARS }
                val imageUrl = story.imageUrl.ifBlank { metadata.imageUrl.orEmpty() }
                val fullText = description ?: story.fullText
                val summary = description?.let { rssNewsService.sourceSummary(it, title = story.title) }
                    ?: story.summary.takeIf(String::isNotBlank)
                    ?: rssNewsService.sourceSummary(fullText, title = story.title)
                if (summary == story.summary && (description == null || description == story.fullText.trim()) && imageUrl == story.imageUrl) return@launch

                val updated = story.copy(
                    summary = summary,
                    fullText = fullText,
                    paragraphs = description?.let(rssNewsService::sourceParagraphs) ?: story.paragraphs,
                    readTimeMin = description?.let { maxOf(2, it.length / 220) } ?: story.readTimeMin,
                    imageUrl = imageUrl,
                    whatHappened = if (description != null) "" else story.whatHappened,
                    whyItMatters = if (description != null) "" else story.whyItMatters,
                    whatIsUnknown = if (description != null) "" else story.whatIsUnknown,
                )
                _stories.update { current -> current.map { if (it.id == storyId) updated else it } }
                newsArticleDao.upsertAll(listOf(NewsArticleEntity.fromStory(updated)))
            } catch (_: Exception) {}
        }
    }

    private fun enrichMissingStoryImages(stories: List<Story>) {
        val now = System.currentTimeMillis()
        stories.asSequence()
            .filter { it.imageUrl.isBlank() && it.canonicalUrl.isNotBlank() }
            .filter { now - (imageEnrichmentAttempts[it.id] ?: 0L) >= IMAGE_ENRICHMENT_RETRY_MS }
            .forEach { pendingImageEnrichment[it.id] = it }
        if (imageEnrichmentJob?.isActive == true || pendingImageEnrichment.isEmpty()) return

        imageEnrichmentJob = scope.launch {
            try {
                val semaphore = Semaphore(IMAGE_ENRICHMENT_CONCURRENCY)
                while (pendingImageEnrichment.isNotEmpty()) {
                    val candidates = pendingImageEnrichment.values.take(MAX_IMAGES_PER_ENRICHMENT)
                    candidates.forEach {
                        pendingImageEnrichment.remove(it.id)
                        imageEnrichmentAttempts[it.id] = System.currentTimeMillis()
                    }
                    val missingImages = candidates.filter { candidate ->
                        _stories.value.firstOrNull { it.id == candidate.id }?.imageUrl.isNullOrBlank()
                    }
                    if (missingImages.isEmpty()) continue

                    val results = coroutineScope {
                        missingImages.map { candidate ->
                            async {
                                semaphore.withPermit {
                                    candidate.id to rssNewsService.fetchSourceMetadata(candidate.canonicalUrl)?.imageUrl
                                }
                            }
                        }.awaitAll()
                    }

                    for ((storyId, imageUrl) in results) {
                        if (imageUrl.isNullOrBlank()) continue
                        val current = _stories.value.firstOrNull { it.id == storyId } ?: continue
                        if (current.imageUrl.isNotBlank()) continue
                        val updated = current.copy(imageUrl = imageUrl)
                        _stories.update { list -> list.map { if (it.id == storyId) updated else it } }
                        newsArticleDao.upsertAll(listOf(NewsArticleEntity.fromStory(updated)))
                    }
                }
            } catch (_: Exception) {
                // RSS articles remain readable even if a publisher blocks page metadata requests.
            }
        }
    }

    fun fetchLiveSports(competition: String = "Trendyol Süper Lig", sportType: String = "FUTBOL") {
        val requestKey = "$sportType|$competition"
        if (activeSportsKey != requestKey) {
            activeSportsKey = requestKey
            sportsRefreshJob?.cancel()
            _sportsSnapshot.value = SportsSnapshot(
                competition = competition,
                sportType = sportType,
                isLoading = true,
            )
        }
        if (sportsRefreshJob?.isActive == true) return
        sportsRefreshJob = scope.launch { refreshSportsData(competition, sportType, requestKey) }
    }

    private suspend fun refreshSportsData(competition: String, sportType: String, requestKey: String) {
        try {
            val cachedScorers = cachedSportStatDao.getStatsDirect(competition, scorerCacheType(sportType))
            if (cachedScorers.isNotEmpty() && activeSportsKey == requestKey) {
                _sportsSnapshot.update { it.copy(topScorers = cachedScorers.map { row -> row.toTopScorer() }) }
            }
        } catch (_: Exception) {}

        try {
            val fixtures = footballApiService.fetchFixtures(competition, sportType)
            if (activeSportsKey != requestKey) return
            _sportsSnapshot.update { it.copy(matches = fixtures) }

            fixtures.asSequence()
                .filter { it.status == com.example.model.MatchStatus.LIVE }
                .flatMap { match -> match.events.asSequence().filter { it.type == "GOL" }.map { match to it } }
                .forEach { (match, goal) ->
                    val alertId = "goal-${match.id}-${goal.minute}-${goal.player}"
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {}

        try {
            val currentStandings = footballApiService.fetchStandings(competition, sportType)
            if (activeSportsKey != requestKey) return
            _sportsSnapshot.update { it.copy(standings = currentStandings) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {}

        try {
            val scorers = footballApiService.fetchTopScorers(competition, sportType)
            if (activeSportsKey != requestKey) return
            if (scorers.isNotEmpty()) {
                try {
                    cachedSportStatDao.deleteStats(competition, scorerCacheType(sportType))
                    cachedSportStatDao.insertStats(scorers.map { CachedSportStatEntity.fromTopScorer(competition, it) })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {}
            }
            _sportsSnapshot.update {
                it.copy(
                    topScorers = scorers.ifEmpty { it.topScorers },
                    isLoading = false,
                    updatedAtEpochMs = System.currentTimeMillis(),
                    scorerMessage = if (scorers.isEmpty() && it.topScorers.isEmpty()) {
                        footballApiService.topScorersUnavailableMessage(sportType)
                    } else "",
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (activeSportsKey == requestKey) {
                _sportsSnapshot.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun scorerCacheType(sportType: String): String = if (sportType == "BASKETBOL") "SAYI" else "GOL"

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
        private const val NEWS_PAGE_SIZE = 120
        private const val MAX_STORIES_PER_REFRESH = 3_000
        private const val MAX_IN_MEMORY_STORIES = 10_000
        private const val MAX_ARCHIVE_ARTICLES = 100_000
        private const val MIN_SOURCE_DESCRIPTION_CHARS = 40
        private const val SOURCE_DESCRIPTION_RETRY_MS = 12 * 60 * 60 * 1_000L
        private const val MAX_IMAGES_PER_ENRICHMENT = 24
        private const val IMAGE_ENRICHMENT_CONCURRENCY = 4
        private const val IMAGE_ENRICHMENT_RETRY_MS = 12 * 60 * 60 * 1_000L

        @Volatile
        private var instance: CizgiRepository? = null

        fun getInstance(context: Context): CizgiRepository {
            return instance ?: synchronized(this) {
                instance ?: CizgiRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
