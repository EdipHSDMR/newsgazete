package com.example.data

import android.content.Context
import com.example.data.local.ReadingHistoryEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.StorySnapshot
import com.example.util.ArchiveSearch
import com.example.util.PersonalizedNews
import com.example.util.NotificationPolicy
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalTime
import java.time.ZoneId
import java.time.LocalDate
import com.example.data.local.CizgiDatabase
import com.example.data.local.CachedSportStatEntity
import com.example.data.local.OfflineImageStore
import com.example.data.remote.GatewayHealthService
import com.example.data.local.NewsArticleEntity
import com.example.data.local.SavedArticleEntity
import com.example.data.remote.FootballApiService
import com.example.data.remote.PressNewsApiService
import com.example.data.remote.RssNewsService
import com.example.data.remote.WeatherApiService
import com.example.model.FinancialAsset
import com.example.model.DataSourceDiagnostic
import com.example.model.BackupPayload
import com.example.model.MarketAlert
import com.example.model.LeagueStanding
import com.example.model.NotificationItem
import com.example.model.SportsMatch
import com.example.model.SportsSnapshot
import com.example.model.Story
import com.example.model.UserPreferences
import com.example.model.WeatherInfo
import com.example.util.CizgiNotificationHelper
import com.example.widget.CizgiWidgetProvider
import com.example.util.NewsDiscoveryInsights
import com.example.util.NewsStoryDeduplicator
import com.example.util.NewsSummary
import com.example.util.BreakingNewsClassifier
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CizgiRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val offlineImageStore = OfflineImageStore(context, httpClient)

    // Room Database
    private val database = CizgiDatabase.getDatabase(context)
    private val savedArticleDao = database.savedArticleDao()
    private val readerDao = database.readerDao()
    private val notificationDao = database.notificationDao()
    private val userPrefs = context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
    private val diagnosticsCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val backupCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _sourceDiagnostics = MutableStateFlow(loadSourceDiagnostics())
    val sourceDiagnostics: StateFlow<List<DataSourceDiagnostic>> = _sourceDiagnostics.asStateFlow()
    private val diagnosticLock = Any()
    private val saveMutex = Mutex()
    private val sportsNotificationMutex = Mutex()
    private val inboxMutex = Mutex()
    private val offlineJobs = mutableMapOf<String, Job>()
    private val _offlineDownloads = MutableStateFlow<Set<String>>(emptySet())
    val offlineDownloads = _offlineDownloads.asStateFlow()
    private val _offlineErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val offlineErrors = _offlineErrors.asStateFlow()
    val readingHistory = readerDao.observeHistory().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val archiveTopics = database.newsArticleDao().observeTopics().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val archivePublishers = database.newsArticleDao().observePublishers().stateIn(scope, SharingStarted.Eagerly, emptyList())
    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches = _recentSearches.asStateFlow()
    private val cachedSportStatDao = database.cachedSportStatDao()
    private val newsArticleDao = database.newsArticleDao()
    private val imageEnrichmentAttempts = mutableMapOf<String, Long>()
    private val pendingImageEnrichment = mutableMapOf<String, Story>()
    private var imageEnrichmentJob: Job? = null
    private var readingPrefetchJob: Job? = null
    private val readingPrefetchAttempts = mutableMapOf<String, Long>()
    private val pendingReadingIds = linkedSetOf<String>()
    private val sportsStatsRefreshAt = mutableMapOf<String, Long>()
    private val completeArticleUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val articleLoadLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private var pressArchiveNextPage = 2
    private var pressArchiveTotalPages = 0
    val hasMorePressArchivePages: Boolean
        get() = pressArchiveTotalPages > 1 && pressArchiveNextPage <= pressArchiveTotalPages

    // Remote Services
    private val rssNewsService = RssNewsService(httpClient)
    private val pressNewsApiService = PressNewsApiService(httpClient)
    private val gatewayHealthService = GatewayHealthService(httpClient)
    private val weatherApiService = WeatherApiService(httpClient)
    private val footballApiService = FootballApiService(httpClient)
    private val marketApiService = com.example.data.remote.MarketApiService(httpClient)

    // State flows
    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()
    private val _dailyDigest = MutableStateFlow<List<String>>(emptyList())
    val dailyDigest: StateFlow<List<String>> = _dailyDigest.asStateFlow()
    private var dailyDigestKey = ""
    private var dailyDigestJob: Job? = null
    val newsArchiveCount: StateFlow<Int> = newsArticleDao.observeCount()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    // Room-persisted saved articles
    val savedEntries = savedArticleDao.getAllSavedArticles().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val savedArticles: StateFlow<List<Story>> = savedEntries
        .map { list -> NewsStoryDeduplicator.merge(list.map { it.toStory() }) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _sportsSnapshot = MutableStateFlow(SportsSnapshot())
    val sportsSnapshot: StateFlow<SportsSnapshot> = _sportsSnapshot.asStateFlow()
    private val _matchAlertIds = MutableStateFlow<Set<String>>(emptySet())
    val matchAlertIds: StateFlow<Set<String>> = _matchAlertIds.asStateFlow()
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
            currentTempC = null,
            feelsLikeC = null,
            humidityPct = null,
            windKmh = null,
            uvIndex = null,
            rainChancePct = null,
            summary = "Güncel hava verisi yükleniyor.",
            hourly = emptyList(),
            daily = emptyList(),
            lastUpdated = "Veri bekleniyor",
        )
    )
    val weather: StateFlow<WeatherInfo> = _weather.asStateFlow()

    val notifications: StateFlow<List<NotificationItem>> = notificationDao.observeInbox()
        .map { list -> list.map { it.item() } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _financialAssets = MutableStateFlow<List<FinancialAsset>>(emptyList())
    val financialAssets: StateFlow<List<com.example.model.FinancialAsset>> = _financialAssets.asStateFlow()

    private val _bistStocks = MutableStateFlow<List<com.example.data.remote.BISTStock>>(emptyList())
    val bistStocks: StateFlow<List<com.example.data.remote.BISTStock>> = _bistStocks.asStateFlow()
    private val _marketStatusMessage = MutableStateFlow("")
    val marketStatusMessage: StateFlow<String> = _marketStatusMessage.asStateFlow()
    private val _marketAlerts = MutableStateFlow<List<MarketAlert>>(emptyList())
    val marketAlerts: StateFlow<List<MarketAlert>> = _marketAlerts.asStateFlow()

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val bistRetrofitApi = com.example.data.remote.BistRetrofitApi(httpClient)

    private val refreshMutex = Mutex()
    private val sportsFixtureSelectionsLoaded = mutableSetOf<String>()

    private var newsStreamJob: kotlinx.coroutines.Job? = null
    private var marketStreamJob: kotlinx.coroutines.Job? = null
    private var weatherStreamJob: kotlinx.coroutines.Job? = null
    private var activeSportsKey: String? = null
    private var sportsRefreshJob: Job? = null
    private var sportsStatsJob: Job? = null
    private val newsPageMutex = Mutex()
    private val newsRefreshMutex = Mutex()
    private val pressArchiveMutex = Mutex()
    private val marketRefreshMutex = Mutex()
    private val sourceDescriptionAttempts = mutableMapOf<String, Long>()

    init {
        loadSavedPreferences()
        repairArchiveTopics()
        observeSavedArticles()
        observeNewsArchive()
        refreshAll()
        startContinuousStreams()
    }

    private fun loadSavedPreferences() {
        val prefs = userPrefs
        if (prefs.getBoolean("breaking_alerts", false) && !prefs.contains("breaking_enabled_since")) prefs.edit().putLong("breaking_enabled_since", System.currentTimeMillis()).apply()
        val theme = prefs.getString("theme", "light") ?: "light"
        val city = prefs.getString("city", "İstanbul") ?: "İstanbul"
        val district = prefs.getString("district", null)
        val fontSize = prefs.getString("font_size", "normal") ?: "normal"
        val breakingAlerts = prefs.getBoolean("breaking_alerts", false)
        val matchAlerts = prefs.getBoolean("match_alerts", false)
        val quietHoursEnabled = prefs.getBoolean("quiet_hours_enabled", true)
        val quietHoursStart = prefs.getString("quiet_hours_start", "23:00") ?: "23:00"
        val quietHoursEnd = prefs.getString("quiet_hours_end", "07:00") ?: "07:00"
        val matchAlertIds = prefs.getStringSet("match_alert_ids", emptySet())?.toSet() ?: emptySet()
        val followedTopics = prefs.getStringSet("followed_topics", emptySet()) ?: emptySet()
        val followedTeams = prefs.getStringSet("followed_teams", emptySet()) ?: emptySet()
        _marketAlerts.value = runCatching {
            diagnosticsCodec.decodeFromString<List<MarketAlert>>(prefs.getString("market_alerts", "[]").orEmpty())
        }.getOrDefault(emptyList())
        prefs.edit()
            .remove("football_api_key")
            .remove("weather_api_key")
            .remove("press_api_key")
            .apply()
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
                breakingAlerts = breakingAlerts,
                matchAlerts = matchAlerts,
                onboardingCompleted = prefs.getBoolean("onboarding_completed", false),
                topicAlerts = prefs.getBoolean("topic_alerts", false),
                dailyDigest = prefs.getBoolean("daily_digest", false),
                matchStartAlerts = prefs.getBoolean("match_start_alerts", true),
                matchScoreAlerts = prefs.getBoolean("match_score_alerts", true),
                weatherAlerts = prefs.getBoolean("weather_alerts", false),
                reduceMotion = prefs.getBoolean("reduce_motion", false),
                highContrast = prefs.getBoolean("high_contrast", false),
                quietHoursEnabled = quietHoursEnabled,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
            )
        }
        _matchAlertIds.value = matchAlertIds
        _recentSearches.value = runCatching {
            val array = JSONArray(prefs.getString("recent_searches", "[]"))
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun loadSourceDiagnostics(): List<DataSourceDiagnostic> = runCatching {
        diagnosticsCodec.decodeFromString<List<DataSourceDiagnostic>>(
            userPrefs.getString("source_diagnostics", "[]").orEmpty(),
        )
    }.getOrDefault(emptyList())

    private fun recordSourceDiagnostic(source: String, success: Boolean, message: String) {
        synchronized(diagnosticLock) {
            val now = System.currentTimeMillis()
            val current = _sourceDiagnostics.value
            val previous = current.firstOrNull { it.source == source }
            val updated = DataSourceDiagnostic(
                source = source,
                lastAttemptEpochMs = now,
                lastSuccessEpochMs = if (success) now else previous?.lastSuccessEpochMs ?: 0L,
                consecutiveFailures = if (success) 0 else (previous?.consecutiveFailures ?: 0) + 1,
                message = message.take(180),
            )
            val updatedList = (current.filterNot { it.source == source } + updated)
                .sortedBy { it.source }
                .take(MAX_DIAGNOSTIC_SOURCES)
            _sourceDiagnostics.value = updatedList
            userPrefs.edit().putString("source_diagnostics", diagnosticsCodec.encodeToString(updatedList)).apply()
        }
    }

    private fun repairArchiveTopics() {
        if (userPrefs.getInt("news_topic_rules_version", 0) >= 1) return
        scope.launch(Dispatchers.IO) {
            var after = ""
            while (true) {
                val batch = newsArticleDao.topicCandidates(after)
                if (batch.isEmpty()) break
                for (row in batch) {
                    val topic = com.example.util.NewsTopics.classify(row.title, row.summary, "", row.topic, row.canonicalUrl)
                    if (topic != row.topic) newsArticleDao.updateTopic(row.id, topic, ArchiveSearch.normalize("${row.title} ${row.summary} $topic ${row.publisher}"))
                }
                after = batch.last().id
            }
            userPrefs.edit().putInt("news_topic_rules_version", 1).apply()
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
        scope.launch(Dispatchers.Default) {
            newsArticleDao.observeLatest(INITIAL_NEWS_LOAD_LIMIT).collect { latestArticles ->
                val savedIds = savedArticles.value.map { it.id }.toSet()
                val articleStories = latestArticles.map { it.toStory().copy(isSaved = it.id in savedIds) }
                val storiesById = articleStories.associateBy { it.id }
                val latest = NewsStoryDeduplicator.merge(articleStories).take(MAX_STORIES_PER_REFRESH)
                val retainedIds = latest.mapTo(mutableSetOf()) { it.id }
                val duplicateIds = articleStories.asSequence()
                    .map { it.id }
                    .filterNot { it in retainedIds }
                    .distinct()
                    .toList()
                val updatedRepresentatives = latest.filter { storiesById[it.id] != it }
                if (updatedRepresentatives.isNotEmpty()) {
                    newsArticleDao.upsertAll(updatedRepresentatives.map(NewsArticleEntity::fromStory))
                }
                if (duplicateIds.isNotEmpty()) newsArticleDao.deleteByIds(duplicateIds)
                _stories.update { current ->
                    val latestById = latest.associateBy { it.id }
                    val currentIds = current.mapTo(mutableSetOf()) { it.id }
                    val duplicateSet = duplicateIds.toSet()
                    val added = if (current.isEmpty() || (!newsRefreshMutex.isLocked && !newsPageMutex.isLocked)) latest.filterNot { it.id in currentIds } else emptyList()
                    (added + current.filterNot { it.id in duplicateSet }.map { latestById[it.id] ?: it })
                        .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                        .take(MAX_IN_MEMORY_STORIES)
                }
                refreshDailyDigest(_stories.value)
                CizgiWidgetProvider.update(context, _stories.value.firstOrNull())
            }
        }
    }

    private fun refreshDailyDigest(stories: List<Story>) {
        val candidates = NewsDiscoveryInsights.dailyDigestStories(stories)
        if (candidates.isEmpty()) {
            dailyDigestKey = ""
            dailyDigestJob?.cancel()
            _dailyDigest.value = emptyList()
            return
        }

        val key = candidates.joinToString("|") { story ->
            "${story.id}:${story.fullText.hashCode()}:${story.summary.hashCode()}"
        }
        if (key == dailyDigestKey) return
        dailyDigestKey = key
        dailyDigestJob?.cancel()
        _dailyDigest.value = NewsDiscoveryInsights.dailyDigest(stories)

        dailyDigestJob = scope.launch(Dispatchers.Default) {
            val summaries = candidates.mapNotNull { story ->
                val sourceText = story.fullText.takeIf { it.length >= NewsDiscoveryInsights.MIN_SOURCE_CHARS }
                    ?: story.summary
                NewsSummary.bestAvailableSummary(
                    summaryCandidate = story.summary,
                    sourceText = sourceText,
                    title = story.title,
                    maxCharacters = NewsDiscoveryInsights.MAX_DIGEST_ITEM_CHARS,
                ).takeIf { it.length >= NewsDiscoveryInsights.MIN_DIGEST_ITEM_CHARS }
                    ?: NewsSummary.concise(
                        sourceText,
                        story.title,
                        NewsDiscoveryInsights.MAX_DIGEST_ITEM_CHARS,
                    ).takeIf { it.length >= NewsDiscoveryInsights.MIN_DIGEST_ITEM_CHARS }
            }
            if (dailyDigestKey == key) {
                _dailyDigest.value = summaries
                    .distinctBy { it.lowercase(java.util.Locale.forLanguageTag("tr-TR")) }
                    .take(NewsDiscoveryInsights.DIGEST_SIZE)
            }
        }
    }

    // Room Database 'Sonra Oku' actions
    fun toggleSaveStory(story: Story) {
        scope.launch {
            saveMutex.withLock {
                val old = savedArticleDao.getSavedArticleById(story.id)
                if (old != null) {
                    offlineJobs.remove(story.id)?.cancel()
                    _offlineDownloads.update { it - story.id }
                    _offlineErrors.update { it - story.id }
                    savedArticleDao.deleteArticleById(story.id)
                    old.offlineImagePath.let(offlineImageStore::delete)
                } else {
                    val ready = (completeArticleUntil[story.id] ?: 0) > System.currentTimeMillis()
                    savedArticleDao.insertArticle(SavedArticleEntity.fromStory(story, ready))
                    downloadForOffline(story.id)
                }
            }
        }
    }

    fun downloadForOffline(storyId: String) {
        if (offlineJobs[storyId]?.isActive == true) return
        _offlineErrors.update { it - storyId }
        _offlineDownloads.update { it + storyId }
        offlineJobs[storyId] = scope.launch {
            try {
                val saved = savedArticleDao.getSavedArticleById(storyId) ?: return@launch
                loadStoryFromArchive(storyId)
                val article = loadFullArticle(storyId)
                val failures = mutableListOf<String>()
                if (article != null) {
                    val stableSnapshot = article.copy(imageUrl = saved.imageUrl.ifBlank { article.imageUrl })
                    savedArticleDao.updateOffline(storyId, StorySnapshot.encode(stableSnapshot), article.fullText, article.summary, true)
                } else if (!saved.offlineReady) {
                    failures += "Tam metin alınamadı."
                }
                val imageUrl = saved.imageUrl.ifBlank { article?.imageUrl.orEmpty() }
                if (saved.offlineImagePath.isBlank() && imageUrl.isNotBlank()) {
                    val path = offlineImageStore.download(storyId, imageUrl)
                    if (path != null) savedArticleDao.updateOfflineImage(storyId, path)
                    else failures += "Görsel çevrimdışı indirilemedi. Yeniden deneyebilirsin."
                }
                if (failures.isNotEmpty()) _offlineErrors.update { it + (storyId to failures.joinToString(" ")) }
                else _offlineErrors.update { it - storyId }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _offlineErrors.update { it + (storyId to "İndirme tamamlanamadı. Bağlantını kontrol edip yeniden dene.") } }
            finally { _offlineDownloads.update { it - storyId }; offlineJobs.remove(storyId) }
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
        offlineJobs.values.toList().forEach { it.cancel() }
        offlineJobs.clear()
        _offlineDownloads.value = emptySet()
        _offlineErrors.value = emptyMap()
        scope.launch(Dispatchers.IO) {
            savedArticleDao.clearAll()
            offlineImageStore.clear()
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
        val activeSelection = activeSportsKey?.split("|")
        fetchLiveSports(
            competition = activeSelection?.getOrNull(1) ?: "Trendyol Süper Lig",
            sportType = activeSelection?.getOrNull(0) ?: "FUTBOL",
            seasonYear = activeSelection?.getOrNull(2)?.toIntOrNull() ?: 0,
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

    suspend fun refreshNewsInBackground(): Boolean {
        val refreshed = refreshNewsData()
        // Slowly fill the archive while Android grants background work. One provider page per
        // run keeps quota and mobile data bounded; the next-page cursor survives app restarts.
        if (refreshed && hasMorePressArchivePages) loadNextPressArchivePage(background = true)
        if (_preferences.value.matchAlerts) {
            val selections = userPrefs.getStringSet("followed_sports_selections", emptySet()).orEmpty().toList().sorted().take(6)
            for (selection in selections) {
                val parts = selection.split('|')
                if (parts.size != 2) continue
                try {
                    processSportsNotifications(footballApiService.fetchFixtures(parts[1], parts[0], currentSportsSeasonYear(parts[1], parts[0])))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Other selected leagues can still refresh. */ }
            }
        }
        return refreshed
    }

    private suspend fun refreshNewsData(): Boolean = newsRefreshMutex.withLock {
        try {
            // Publish a few fresh publisher RSS items first so a cold launch does not wait for
            // every slow feed or the press archive. The complete refresh follows in the same job.
            val quickStories = withTimeoutOrNull(7_000L) { rssNewsService.fetchQuickNews() }.orEmpty()
            val quickPublished = if (quickStories.isNotEmpty()) publishLiveNews(quickStories) else false
            val (rssStories, pressPage, gatewayHealth) = coroutineScope {
                val rss = async { rssNewsService.fetchRealNews() }
                val press = async { pressNewsApiService.fetchPage(page = 1) }
                val gateway = async { gatewayHealthService.check() }
                Triple(rss.await(), press.await(), gateway.await())
            }
            val reachableFeeds = rssNewsService.lastSuccessfulFeedCount
            recordSourceDiagnostic(
                "RSS haber kaynakları",
                reachableFeeds > 0,
                "$reachableFeeds/${rssNewsService.feedCount} kaynak yanıt verdi · ${rssStories.size} haber",
            )
            recordSourceDiagnostic(
                "PHA haber arşivi",
                pressPage != null,
                pressPage?.let { "${it.total} haber · ${it.page}/${it.totalPages} sayfa" }
                    ?: pressNewsApiService.lastError.ifBlank { "Haber arşivi yanıt vermedi." },
            )
            val gatewayMessage = when {
                !gatewayHealth.available && gatewayHealth.httpCode == 0 -> "Servis geçidine bağlanılamadı."
                !gatewayHealth.available -> "Servis geçidi HTTP ${gatewayHealth.httpCode} yanıtı verdi."
                !gatewayHealth.pressConfigured -> "Servis çalışıyor ancak PHA anahtarı ayarlı değil."
                else -> "Servis ve PHA bağlantısı hazır."
            }
            recordSourceDiagnostic("Haber servis geçidi", gatewayHealth.available && gatewayHealth.pressConfigured, gatewayMessage)
            if (rssStories.isEmpty() && pressPage == null) return@withLock quickPublished
            pressPage?.let { page ->
                pressArchiveTotalPages = page.totalPages.coerceAtLeast(page.page)
                if (pressArchiveNextPage > pressArchiveTotalPages && pressArchiveTotalPages > 1) {
                    pressArchiveNextPage = 2
                }
                persistPressArchiveProgress()
            }
            val liveFeed = withContext(Dispatchers.Default) {
                mergePressAndRss(pressPage?.stories.orEmpty(), rssStories)
            }
            if (liveFeed.isEmpty()) return@withLock true
            publishLiveNews(liveFeed) || quickPublished
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordSourceDiagnostic("RSS haber kaynakları", false, "Haber kaynakları yenilenemedi.")
            recordSourceDiagnostic("PHA haber arşivi", false, "Haber arşivi yenilenemedi.")
            false
        }
    }

    private suspend fun publishLiveNews(liveFeed: List<Story>): Boolean {
        if (liveFeed.isEmpty()) return false
        val currentStories = _stories.value
        val currentById = currentStories.associateBy { it.id }
        val mergedStories = withContext(Dispatchers.Default) {
            NewsStoryDeduplicator.merge(currentStories + liveFeed)
        }
        val changedStories = mergedStories.filter { currentById[it.id] != it }
        if (changedStories.isNotEmpty()) {
            newsArticleDao.upsertAll(changedStories.map(NewsArticleEntity::fromStory))
        }
        newsArticleDao.pruneOldest(MAX_ARCHIVE_ARTICLES)

        val savedIds = savedArticles.value.map { it.id }.toSet()
        withContext(Dispatchers.Default) {
            _stories.update { current ->
                val incoming = liveFeed.map { it.copy(isSaved = it.id in savedIds) }
                NewsStoryDeduplicator.merge(current + incoming)
                    .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                    .take(MAX_IN_MEMORY_STORIES)
            }
        }

        enrichMissingStoryImages(_stories.value.take(NEWS_PAGE_SIZE))
        CizgiWidgetProvider.update(context, _stories.value.firstOrNull())
        prefetchReadingContent()
        processNewsNotifications(_stories.value.take(MAX_STORIES_PER_REFRESH))
        return true
    }

    private fun mergePressAndRss(pressStories: List<Story>, rssStories: List<Story>): List<Story> {
        return NewsStoryDeduplicator.merge(pressStories + rssStories)
            .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
            .take(MAX_STORIES_PER_REFRESH)
    }

    private suspend fun loadNextPressArchivePage(background: Boolean = false): Boolean = newsRefreshMutex.withLock {
        pressArchiveMutex.withLock {
        if (pressArchiveNextPage > pressArchiveTotalPages) return@withLock false
        if (background && System.currentTimeMillis() - userPrefs.getLong("press_archive_last_backfill", 0L) < ARCHIVE_BACKFILL_INTERVAL_MS) {
            return@withLock false
        }
        val pageNumber = pressArchiveNextPage
        val page = pressNewsApiService.fetchPage(page = pageNumber)
        if (page == null) {
            recordSourceDiagnostic("PHA arşiv devamı", false, pressNewsApiService.lastError.ifBlank { "Arşiv sayfası alınamadı." })
            return@withLock false
        }
        pressArchiveTotalPages = maxOf(pressArchiveTotalPages, page.totalPages)
        val savedIds = savedArticles.value.map { it.id }.toSet()
        val incoming = page.stories.map { it.copy(isSaved = it.id in savedIds) }

        if (incoming.isNotEmpty()) {
            val current = _stories.value
            val merged = withContext(Dispatchers.Default) {
                NewsStoryDeduplicator.merge(current + incoming)
                    .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                    .take(MAX_IN_MEMORY_STORIES)
            }
            val currentById = current.associateBy { it.id }
            val changed = merged.filter { currentById[it.id] != it }
            if (changed.isNotEmpty()) {
                newsArticleDao.upsertAll(changed.map(NewsArticleEntity::fromStory))
                newsArticleDao.pruneOldest(MAX_ARCHIVE_ARTICLES)
            }
            _stories.value = merged
            enrichMissingStoryImages(changed.filterNot { it.id in currentById })
        }

        pressArchiveNextPage = pageNumber + 1
        persistPressArchiveProgress()
        if (background) userPrefs.edit().putLong("press_archive_last_backfill", System.currentTimeMillis()).apply()
        recordSourceDiagnostic("PHA arşiv devamı", true, "${page.page}/${page.totalPages} sayfa · ${page.stories.size} haber alındı")
        true
        }
    }

    private fun persistPressArchiveProgress() {
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("press_archive_next_page", pressArchiveNextPage)
            .putInt("press_archive_total_pages", pressArchiveTotalPages)
            .apply()
    }

    fun fetchLiveWeather(city: String) {
        scope.launch { refreshWeatherData(city) }
    }

    private suspend fun refreshWeatherData(city: String) {
        try {
            val live = weatherApiService.fetchWeather(city)
            if (live == null) {
                recordSourceDiagnostic("Hava durumu", false, "Hava servisi yeni veri döndürmedi; önceki bilgi korunuyor.")
                return
            }
            _weather.value = live
            if (_preferences.value.weatherAlerts) {
                val warning = when {
                    (live.rainChancePct ?: 0) >= 80 -> "Yağış olasılığı bugün %${live.rainChancePct}; dışarı çıkmadan önce hazırlıklı ol."
                    (live.currentTempC ?: 20) <= 0 -> "Sıcaklık ${live.currentTempC}°C seviyesinde; don uyarısı var."
                    (live.currentTempC ?: 20) >= 35 -> "Sıcaklık ${live.currentTempC}°C seviyesinde; sıcak hava uyarısı var."
                    else -> null
                }
                val alertKey = "${live.cityName}:${LocalDate.now(ZoneId.of("Europe/Istanbul"))}:${warning.orEmpty()}"
                if (!warning.isNullOrBlank() && userPrefs.getString("weather_alert_last", "") != alertKey) {
                    CizgiNotificationHelper.sendWeatherNotification(context, live.cityName, warning)
                    userPrefs.edit().putString("weather_alert_last", alertKey).apply()
                }
            }
            recordSourceDiagnostic("Hava durumu", true, "${live.cityName} · ${live.lastUpdated}")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordSourceDiagnostic("Hava durumu", false, "Hava durumu yenilenemedi; önceki bilgi korunuyor.")
        }
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
                    val current = _stories.value
                    val currentById = current.associateBy { it.id }
                    val merged = withContext(Dispatchers.Default) {
                        NewsStoryDeduplicator.merge(current + olderStories)
                    }
                        .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                        .take(MAX_IN_MEMORY_STORIES)
                    val changed = merged.filter { currentById[it.id] != it }
                    if (changed.isNotEmpty()) newsArticleDao.upsertAll(changed.map(NewsArticleEntity::fromStory))
                    val mergedIds = merged.mapTo(mutableSetOf()) { it.id }
                    val duplicateIds = olderStories.asSequence().map { it.id }
                        .filterNot { it in mergedIds }.distinct().toList()
                    if (duplicateIds.isNotEmpty()) newsArticleDao.deleteByIds(duplicateIds)
                    withContext(Dispatchers.Default) {
                        _stories.update { current ->
                        NewsStoryDeduplicator.merge(current + olderStories)
                            .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                            .take(MAX_IN_MEMORY_STORIES)
                        }
                    }
                    enrichMissingStoryImages(olderStories)
                }
                if (older.size < NEWS_PAGE_SIZE) loadNextPressArchivePage()
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                val metadata = rssNewsService.fetchSourceMetadata(story.canonicalUrl)
                if (metadata == null) {
                    sourceDescriptionAttempts.remove(storyId)
                    return@launch
                }
                val latestStory = _stories.value.firstOrNull { it.id == storyId } ?: story
                val sourceStory = if (latestStory.fullText.length >= story.fullText.length) latestStory else story
                val description = metadata?.description?.takeIf { it.length >= MIN_SOURCE_DESCRIPTION_CHARS }
                val imageUrl = sourceStory.imageUrl.ifBlank { metadata?.imageUrl.orEmpty() }
                val sourceText = sourceStory.fullText.takeIf { it.length >= MIN_SOURCE_DESCRIPTION_CHARS }
                    ?: description.orEmpty().ifBlank { sourceStory.summary }
                val fullText = sourceStory.fullText.takeIf { it.length >= MIN_SOURCE_DESCRIPTION_CHARS }
                    ?: description.orEmpty().ifBlank { sourceStory.fullText }
                val summary = NewsSummary.bestAvailableSummary(
                    summaryCandidate = sourceStory.summary,
                    sourceText = sourceText,
                    title = sourceStory.title,
                ).ifBlank { NewsSummary.concise(sourceText, story.title) }
                if (summary == sourceStory.summary && (description == null || description == sourceStory.fullText.trim()) && imageUrl == sourceStory.imageUrl) return@launch

                val updated = sourceStory.copy(
                    summary = summary,
                    fullText = fullText,
                    paragraphs = description?.let(rssNewsService::sourceParagraphs) ?: sourceStory.paragraphs,
                    readTimeMin = description?.let { maxOf(2, it.length / 220) } ?: sourceStory.readTimeMin,
                    imageUrl = imageUrl,
                    whatHappened = if (description != null) "" else sourceStory.whatHappened,
                    whyItMatters = if (description != null) "" else sourceStory.whyItMatters,
                    whatIsUnknown = if (description != null) "" else sourceStory.whatIsUnknown,
                )
                _stories.update { current -> current.map { if (it.id == storyId) updated else it } }
                newsArticleDao.upsertAll(listOf(NewsArticleEntity.fromStory(updated)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                sourceDescriptionAttempts.remove(storyId)
            }
        }
    }

    suspend fun loadStoryFromArchive(storyId: String): Story? {
        _stories.value.firstOrNull { it.id == storyId }?.let { return it }
        val savedArticle = savedArticleDao.getSavedArticleById(storyId)
        val story = newsArticleDao.getById(storyId)?.toStory()
            ?: savedArticle?.toStory()
            ?: readerDao.readingState(storyId)?.story()
            ?: return null
        val loaded = story.copy(isSaved = savedArticle != null)
        _stories.update { current ->
            if (current.any { it.id == storyId }) current else listOf(loaded) + current
        }
        return loaded
    }

    suspend fun loadFullArticle(storyId: String): Story? = articleLoadLocks.getOrPut(storyId) { Mutex() }.withLock {
        val story = _stories.value.firstOrNull { it.id == storyId } ?: loadStoryFromArchive(storyId) ?: return@withLock null
        val saved = savedArticleDao.getSavedArticleById(storyId)
        if (saved?.offlineReady == true && saved.fullText.length >= 180) return@withLock saved.toStory()
        if ((completeArticleUntil[storyId] ?: 0L) > System.currentTimeMillis()) return@withLock story
        val hasLicensedArticleBody = story.fullText.length >= 400 &&
            (story.id.startsWith("pha-") || story.fullText.length >= 1_200)
        val content = if (hasLicensedArticleBody) {
            com.example.data.remote.SourceArticleContent(story.fullText, story.paragraphs, story.imageUrl)
        } else {
            rssNewsService.fetchFullArticle(story.canonicalUrl) ?: return@withLock null
        }
        val updated = story.copy(
            summary = NewsSummary.bestAvailableSummary(
                summaryCandidate = story.summary,
                sourceText = content.text,
                title = story.title,
            ).ifBlank { story.summary },
            fullText = content.text,
            paragraphs = content.paragraphs,
            imageUrl = story.imageUrl.ifBlank { content.imageUrl.orEmpty() },
            readTimeMin = maxOf(1, content.text.split(Regex("\\s+")).size / 220),
        )
        completeArticleUntil[storyId] = System.currentTimeMillis() + 30 * 60_000L
        _stories.update { current -> current.map { if (it.id == storyId) updated else it } }
        withContext(Dispatchers.IO) {
            newsArticleDao.upsertAll(listOf(NewsArticleEntity.fromStory(updated)))
            savedArticleDao.updateOffline(storyId, StorySnapshot.encode(updated), updated.fullText, updated.summary, true)
        }
        updated
    }

    private fun prefetchReadingContent() {
        prepareReadingContent(_stories.value.take(8).map { it.id })
    }

    fun prepareReadingContent(storyIds: List<String>) {
        scope.launch { enqueueReadingContent(storyIds) }
    }

    private fun enqueueReadingContent(storyIds: List<String>) {
        val now = System.currentTimeMillis()
        val candidates = _stories.value.asSequence()
            .filter { it.id in storyIds && it.canonicalUrl.startsWith("https://") && it.fullText.length < 1_200 }
            .filter { now - (readingPrefetchAttempts[it.id] ?: 0L) > 15 * 60_000L }
            .take(8).toList()
        candidates.forEach { readingPrefetchAttempts[it.id] = now; pendingReadingIds.add(it.id) }
        while (pendingReadingIds.size > 20) pendingReadingIds.remove(pendingReadingIds.first())
        if (readingPrefetchJob?.isActive == true || pendingReadingIds.isEmpty()) return
        readingPrefetchJob = scope.launch {
            val semaphore = Semaphore(2)
            while (pendingReadingIds.isNotEmpty()) {
                val batch = pendingReadingIds.take(2)
                pendingReadingIds.removeAll(batch.toSet())
                coroutineScope {
                    batch.map { id -> async {
                        semaphore.withPermit {
                            try { loadFullArticle(id) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { null }
                        }
                    } }.awaitAll()
                }
            }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // RSS articles remain readable even if a publisher blocks page metadata requests.
            }
        }
    }

    fun fetchLiveSports(competition: String = "Trendyol Süper Lig", sportType: String = "FUTBOL", seasonYear: Int = 0) {
        val resolvedSeason = seasonYear.takeIf { it > 0 } ?: currentSportsSeasonYear(competition, sportType)
        val requestKey = "$sportType|$competition|$resolvedSeason"
        if (activeSportsKey != requestKey) {
            activeSportsKey = requestKey
            sportsRefreshJob?.cancel()
            sportsStatsJob?.cancel()
            _sportsSnapshot.value = SportsSnapshot(
                competition = competition,
                sportType = sportType,
                seasonYear = resolvedSeason,
                isLoading = true,
                statsLoading = true,
            )
        }
        if (sportsRefreshJob?.isActive == true) return
        sportsRefreshJob = scope.launch { refreshSportsData(competition, sportType, resolvedSeason, requestKey) }
    }

    private suspend fun refreshSportsData(competition: String, sportType: String, seasonYear: Int, requestKey: String) {
        val refreshStats = _sportsSnapshot.value.updatedAtEpochMs == 0L ||
            System.currentTimeMillis() - (sportsStatsRefreshAt[requestKey] ?: 0L) > 15 * 60_000L
        if (refreshStats && sportsStatsJob?.isActive != true) {
            sportsStatsRefreshAt[requestKey] = System.currentTimeMillis()
            sportsStatsJob = scope.launch { refreshSportsStats(competition, sportType, seasonYear, requestKey) }
        }

        var responseReceived = false
        try {
            val fixtures = footballApiService.fetchFixtures(competition, sportType, seasonYear)
            responseReceived = true
            recordSourceDiagnostic(
                "Spor · $competition",
                fixtures.isNotEmpty(),
                if (fixtures.isNotEmpty()) "${fixtures.size} maç alındı" else "Maç yok veya sağlayıcı boş yanıt verdi.",
            )
            if (activeSportsKey != requestKey) return
            _sportsSnapshot.update {
                when {
                    fixtures.isNotEmpty() -> it.copy(matches = fixtures, errorMessage = "")
                    it.matches.isNotEmpty() -> it.copy(errorMessage = "Yeni maç listesi boş geldi; son alınan maçlar korunuyor.")
                    else -> it.copy(matches = emptyList(), errorMessage = "")
                }
            }
            sportsFixtureSelectionsLoaded.add(requestKey)

            processSportsNotifications(fixtures)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordSourceDiagnostic("Spor · $competition", false, "Maç verisi alınamadı; son başarılı görünüm korunuyor.")
            if (activeSportsKey == requestKey) _sportsSnapshot.update {
                it.copy(errorMessage = "Maç verisi şu anda alınamıyor. Bağlantı gelince yeniden dene.")
            }
        }

        if (activeSportsKey == requestKey) _sportsSnapshot.update {
            it.copy(isLoading = false, updatedAtEpochMs = if (responseReceived) System.currentTimeMillis() else it.updatedAtEpochMs)
        }
    }

    private suspend fun refreshSportsStats(competition: String, sportType: String, seasonYear: Int, requestKey: String) {
        if (activeSportsKey == requestKey) _sportsSnapshot.update { it.copy(statsLoading = true) }
        try {
            if (seasonYear == currentSportsSeasonYear(competition, sportType)) {
                val cachedScorers = cachedSportStatDao.getStatsDirect(competition, scorerCacheType(sportType))
                if (cachedScorers.isNotEmpty() && activeSportsKey == requestKey) {
                    _sportsSnapshot.update { it.copy(topScorers = cachedScorers.map { row -> row.toTopScorer() }) }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { }
        try {
            val currentStandings = footballApiService.fetchStandings(competition, sportType, seasonYear)
            recordSourceDiagnostic(
                "Spor istatistikleri · $competition",
                currentStandings.isNotEmpty(),
                if (currentStandings.isNotEmpty()) "${currentStandings.size} takım sırası alındı" else "Puan tablosu yanıt vermedi veya boş.",
            )
            if (activeSportsKey != requestKey) return
            _sportsSnapshot.update {
                it.copy(
                    standings = currentStandings,
                    errorMessage = it.errorMessage,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordSourceDiagnostic("Spor istatistikleri · $competition", false, "Puan tablosu alınamadı; varsa önceki tablo korunuyor.")
        }

        try {
            val scorers = footballApiService.fetchTopScorers(competition, sportType, seasonYear)
            if (scorers.isNotEmpty()) recordSourceDiagnostic("Oyuncu istatistikleri · $competition", true, "${scorers.size} oyuncu lideri alındı")
            else recordSourceDiagnostic("Oyuncu istatistikleri · $competition", false, footballApiService.topScorersUnavailableMessage(sportType))
            if (activeSportsKey != requestKey) return
            sportsStatsRefreshAt[requestKey] = System.currentTimeMillis()
            if (scorers.isNotEmpty() && seasonYear == currentSportsSeasonYear(competition, sportType)) {
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
                    statsLoading = false,
                    scorerMessage = if (scorers.isEmpty() && it.topScorers.isEmpty()) {
                        footballApiService.topScorersUnavailableMessage(sportType)
                    } else "",
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordSourceDiagnostic("Oyuncu istatistikleri · $competition", false, "Oyuncu istatistikleri alınamadı.")
            if (activeSportsKey == requestKey) {
                _sportsSnapshot.update { it.copy(statsLoading = false) }
            }
        }
    }

    private fun scorerCacheType(sportType: String): String = when (sportType) {
        "BASKETBOL" -> "SAYI"
        else -> "GOL"
    }

    private fun currentSportsSeasonYear(competition: String, sportType: String): Int {
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul"))
        if (sportType == "BASKETBOL" && competition == "WNBA") return today.year
        return if (today.monthValue >= 7) today.year else today.year - 1
    }

    suspend fun fetchMatchLineups(match: SportsMatch): com.example.model.MatchLineups? =
        footballApiService.fetchMatchLineups(match)

    fun refreshMarkets(force: Boolean = false) {
        scope.launch { refreshMarketData(force) }
    }

    private suspend fun refreshMarketData(force: Boolean = false) {
        if (!marketRefreshMutex.tryLock()) return
        try {
            coroutineScope {
                val assetsJob = async(Dispatchers.IO) { marketApiService.fetchCompleteMarketOverview(force) }
                val stocksJob = async(Dispatchers.IO) { bistRetrofitApi.fetchBistWatchlist(force) }
                val assets = try {
                    assetsJob.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                val stocks = try {
                    stocksJob.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                assets?.takeIf { it.isNotEmpty() }?.let { _financialAssets.value = it }
                stocks?.takeIf { it.isNotEmpty() }?.let { _bistStocks.value = it }
                evaluateMarketAlerts(assets ?: _financialAssets.value, stocks ?: _bistStocks.value)
                recordSourceDiagnostic(
                    "Döviz, altın ve kripto",
                    !assets.isNullOrEmpty(),
                    assets?.firstOrNull()?.let { "${assets.size} araç · ${it.source} · ${it.updatedAtEpochMs}" }
                        ?: "Piyasa servisi veri vermedi; önceki fiyatlar korunuyor.",
                )
                recordSourceDiagnostic(
                    "BIST",
                    !stocks.isNullOrEmpty(),
                    stocks?.firstOrNull()?.let { "${stocks.size} araç · ${it.source} · ${it.updatedAtEpochMs}" }
                        ?: "BIST servisi veri vermedi; önceki fiyatlar korunuyor.",
                )
                _marketStatusMessage.value = (marketApiService.availabilityWarnings() + bistRetrofitApi.lastErrorMessage)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" ")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordSourceDiagnostic("Döviz, altın ve kripto", false, "Piyasa verisi yenilenemedi; son fiyatlar korunuyor.")
            recordSourceDiagnostic("BIST", false, "BIST verisi yenilenemedi; son fiyatlar korunuyor.")
            // Keep the last successful quotes visible and let the UI display their source timestamps.
        } finally {
            marketRefreshMutex.unlock()
        }
    }

    private fun evaluateMarketAlerts(assets: List<FinancialAsset>, stocks: List<com.example.data.remote.BISTStock>) {
        if (_marketAlerts.value.none { it.enabled }) return
        val quotes = buildMap {
            assets.forEach { put(it.code, it.currentPrice to it.name) }
            stocks.forEach { put("BIST:${it.symbol}", it.price to it.companyName) }
        }
        val now = System.currentTimeMillis()
        var changed = false
        val updated = _marketAlerts.value.map { alert ->
            val quote = quotes[alert.code] ?: return@map alert
            val hit = if (alert.direction == "below") quote.first <= alert.threshold else quote.first >= alert.threshold
            if (!hit || now - alert.lastTriggeredEpochMs < 6 * 60 * 60_000L) return@map alert
            changed = true
            val direction = if (alert.direction == "below") "altına indi" else "üzerine çıktı"
            CizgiNotificationHelper.sendMarketNotification(
                context = context,
                title = "${quote.second} alarmı",
                body = "${quote.first} değeri ${alert.threshold} seviyesinin $direction.",
            )
            alert.copy(lastTriggeredEpochMs = now)
        }
        if (changed) persistMarketAlerts(updated)
    }

    private fun persistMarketAlerts(alerts: List<MarketAlert>) {
        val normalized = alerts.distinctBy { it.code }.take(30)
        _marketAlerts.value = normalized
        userPrefs.edit().putString("market_alerts", diagnosticsCodec.encodeToString(normalized)).apply()
    }

    fun addMarketAlert(code: String, threshold: Double, direction: String = "above") {
        if (code.isBlank() || !threshold.isFinite()) return
        persistMarketAlerts(
            _marketAlerts.value.filterNot { it.code == code } + MarketAlert(
                code = code.trim(),
                threshold = threshold,
                direction = if (direction == "below") "below" else "above",
            ),
        )
    }

    fun removeMarketAlert(code: String) {
        persistMarketAlerts(_marketAlerts.value.filterNot { it.code == code })
    }

    fun setMarketAlertEnabled(code: String, enabled: Boolean) {
        persistMarketAlerts(_marketAlerts.value.map { if (it.code == code) it.copy(enabled = enabled) else it })
    }

    fun setWeatherAlerts(enabled: Boolean) {
        _preferences.update { it.copy(weatherAlerts = enabled) }
        userPrefs.edit().putBoolean("weather_alerts", enabled).apply()
    }

    fun exportBackupJson(): String = backupCodec.encodeToString(
        BackupPayload(
            preferences = _preferences.value,
            savedStories = savedArticles.value,
            marketAlerts = _marketAlerts.value,
            marketFavoriteCodes = context.getSharedPreferences("market_watchlist", Context.MODE_PRIVATE)
                .getStringSet("codes", emptySet()).orEmpty(),
        ),
    )

    fun importBackupJson(raw: String): Boolean {
        val payload = runCatching { backupCodec.decodeFromString<BackupPayload>(raw) }.getOrNull() ?: return false
        val prefs = payload.preferences
        _preferences.value = prefs
        userPrefs.edit()
            .putString("theme", prefs.themeMode)
            .putString("city", prefs.selectedCity)
            .putString("district", prefs.selectedDistrict)
            .putString("font_size", prefs.fontSize)
            .putStringSet("followed_topics", prefs.followedTopics)
            .putStringSet("followed_teams", prefs.followedTeams)
            .putBoolean("breaking_alerts", prefs.breakingAlerts)
            .putBoolean("match_alerts", prefs.matchAlerts)
            .putBoolean("topic_alerts", prefs.topicAlerts)
            .putBoolean("daily_digest", prefs.dailyDigest)
            .putBoolean("match_start_alerts", prefs.matchStartAlerts)
            .putBoolean("match_score_alerts", prefs.matchScoreAlerts)
            .putBoolean("weather_alerts", prefs.weatherAlerts)
            .putBoolean("reduce_motion", prefs.reduceMotion)
            .putBoolean("high_contrast", prefs.highContrast)
            .putBoolean("quiet_hours_enabled", prefs.quietHoursEnabled)
            .putString("quiet_hours_start", prefs.quietHoursStart)
            .putString("quiet_hours_end", prefs.quietHoursEnd)
            .putBoolean("onboarding_completed", prefs.onboardingCompleted)
            .apply()
        persistMarketAlerts(payload.marketAlerts)
        context.getSharedPreferences("market_watchlist", Context.MODE_PRIVATE).edit()
            .putStringSet("codes", payload.marketFavoriteCodes)
            .apply()
        scope.launch(Dispatchers.IO) {
            payload.savedStories.forEach { savedArticleDao.insertArticle(SavedArticleEntity.fromStory(it)) }
            refreshWeatherData(locationQuery())
        }
        return true
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
        rememberSportsSelection()
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

    fun setBreakingAlerts(enabled: Boolean) {
        if (enabled && !_preferences.value.breakingAlerts) userPrefs.edit().putLong("breaking_enabled_since", System.currentTimeMillis()).apply()
        _preferences.update { it.copy(breakingAlerts = enabled) }
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("breaking_alerts", enabled).apply()
    }

    fun setMatchAlerts(enabled: Boolean) {
        _preferences.update { it.copy(matchAlerts = enabled) }
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("match_alerts", enabled).apply()
    }

    fun toggleMatchAlert(matchId: String) {
        val updated = _matchAlertIds.value.toMutableSet().apply {
            if (!add(matchId)) remove(matchId)
        }.toSet()
        _matchAlertIds.value = updated
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("match_alert_ids", updated).apply()
        rememberSportsSelection()
    }

    private fun isMatchAlertSelected(match: SportsMatch): Boolean {
        if (match.id in _matchAlertIds.value) return true
        return _preferences.value.followedTeams.any { followed ->
            val name = followed.trim()
            name.isNotEmpty() && (
                match.homeTeam.contains(name, ignoreCase = true) ||
                    match.awayTeam.contains(name, ignoreCase = true) ||
                    name.contains(match.homeTeam, ignoreCase = true) ||
                    name.contains(match.awayTeam, ignoreCase = true)
                )
        }
    }

    fun setQuietHours(enabled: Boolean, start: String? = null, end: String? = null) {
        val current = _preferences.value
        val savedStart = start ?: current.quietHoursStart
        val savedEnd = end ?: current.quietHoursEnd
        _preferences.update {
            it.copy(quietHoursEnabled = enabled, quietHoursStart = savedStart, quietHoursEnd = savedEnd)
        }
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("quiet_hours_enabled", enabled)
            .putString("quiet_hours_start", savedStart)
            .putString("quiet_hours_end", savedEnd)
            .apply()
    }

    private fun isQuietHoursNow(): Boolean {
        val settings = _preferences.value
        return NotificationPolicy.isQuiet(LocalTime.now(ZoneId.of("Europe/Istanbul")), settings.quietHoursEnabled, settings.quietHoursStart, settings.quietHoursEnd)
    }

    fun clearSearchHistory() {
        _recentSearches.value = emptyList()
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().remove("search_history").remove("recent_searches").apply()
    }

    fun clearLocalNewsData() {
        scope.launch(Dispatchers.IO) {
            newsRefreshMutex.withLock {
              pressArchiveMutex.withLock {
                pressArchiveNextPage = 2
                pressArchiveTotalPages = 0
                userPrefs.edit()
                    .remove("press_archive_next_page")
                    .remove("press_archive_total_pages")
                    .remove("press_archive_last_backfill")
                    .apply()
                newsArticleDao.clearAll()
                database.cachedStoryDao().clearAll()
                cachedSportStatDao.clearAll()
              }
            }
        }
    }

    fun markNotificationRead(id: String) { scope.launch { notificationDao.markRead(id) } }
    fun markAllNotificationsRead() { scope.launch { notificationDao.markAllRead() } }
    fun clearNotifications() { scope.launch { notificationDao.clear() } }

    fun completeOnboarding(topics: Set<String>, teams: Set<String>) {
        _preferences.update { it.copy(followedTopics = topics, followedTeams = teams, onboardingCompleted = true) }
        userPrefs.edit().putStringSet("followed_topics", topics).putStringSet("followed_teams", teams)
            .putBoolean("onboarding_completed", true).apply()
        rememberSportsSelection()
    }

    fun setReduceMotion(enabled: Boolean) {
        _preferences.update { it.copy(reduceMotion = enabled) }
        userPrefs.edit().putBoolean("reduce_motion", enabled).apply()
    }

    fun setHighContrast(enabled: Boolean) {
        _preferences.update { it.copy(highContrast = enabled) }
        userPrefs.edit().putBoolean("high_contrast", enabled).apply()
    }

    fun setTopicAlerts(enabled: Boolean) {
        if (enabled && !_preferences.value.topicAlerts) userPrefs.edit().putLong("topic_enabled_since", System.currentTimeMillis()).apply()
        _preferences.update { it.copy(topicAlerts = enabled) }
        userPrefs.edit().putBoolean("topic_alerts", enabled).apply()
    }

    fun setDailyDigest(enabled: Boolean) {
        _preferences.update { it.copy(dailyDigest = enabled) }
        userPrefs.edit().putBoolean("daily_digest", enabled).apply()
    }

    fun setMatchStartAlerts(enabled: Boolean) {
        _preferences.update { it.copy(matchStartAlerts = enabled) }
        userPrefs.edit().putBoolean("match_start_alerts", enabled).apply()
    }

    fun setMatchScoreAlerts(enabled: Boolean) {
        _preferences.update { it.copy(matchScoreAlerts = enabled) }
        userPrefs.edit().putBoolean("match_score_alerts", enabled).apply()
    }

    fun rememberSearch(query: String) {
        val text = query.trim().take(100)
        if (text.length < 2) return
        _recentSearches.update { list -> (listOf(text) + list.filterNot { ArchiveSearch.normalize(it) == ArchiveSearch.normalize(text) }).take(10) }
        userPrefs.edit().putString("recent_searches", JSONArray(_recentSearches.value).toString()).apply()
    }

    suspend fun searchArchive(query: String, topic: String?, publisher: String?, from: Long?, until: Long?, offset: Int): ArchivePage = withContext(Dispatchers.Default) {
        val terms = ArchiveSearch.terms(query).map(ArchiveSearch::like).toMutableList()
        while (terms.size < 6) terms += "%"
        val rows = newsArticleDao.search(terms[0], terms[1], terms[2], terms[3], terms[4], terms[5], topic, publisher, from, until, 61, offset)
        val savedIds = savedArticles.value.mapTo(mutableSetOf()) { it.id }
        ArchivePage(NewsStoryDeduplicator.merge(rows.take(60).map { it.toStory().copy(isSaved = it.id in savedIds) }), offset + minOf(60, rows.size), rows.size > 60)
    }

    suspend fun readingState(id: String): ReadingHistoryEntity? = readerDao.readingState(id)

    fun saveReadingPosition(story: Story, anchor: String, offset: Int, progress: Int) {
        scope.launch(Dispatchers.IO) {
            readerDao.saveReadingState(ReadingHistoryEntity(story.id, StorySnapshot.encode(story), System.currentTimeMillis(), anchor, offset.coerceAtLeast(0), progress.coerceIn(0, 100)))
            readerDao.pruneHistory()
        }
    }

    fun clearReadingHistory() { scope.launch { readerDao.clearHistory() } }

    fun summaryFeedback(storyId: String): String? = userPrefs.getString("summary_feedback_$storyId", null)
    fun recordSummaryFeedback(storyId: String, value: String) {
        if (value !in setOf("helpful", "missing", "incorrect")) return
        userPrefs.edit().putString("summary_feedback_$storyId", value).apply()
    }

    private suspend fun addInbox(id: String, category: String, title: String, body: String, storyId: String? = null, screen: String? = null): Boolean = inboxMutex.withLock {
        val delivered = runCatching { JSONObject(userPrefs.getString("delivered_notifications", "{}") ?: "{}") }.getOrDefault(JSONObject())
        if (delivered.has(id)) return@withLock false
        val inserted = notificationDao.insert(NotificationEntity(id, category, title, body.take(500), storyId, System.currentTimeMillis()))
        if (inserted == -1L) return@withLock false
        delivered.put(id, System.currentTimeMillis())
        delivered.keys().asSequence().toList().filter { delivered.optLong(it) < System.currentTimeMillis() - 7 * 86_400_000L }.forEach { delivered.remove(it) }
        while (delivered.length() > 500) delivered.remove(delivered.keys().asSequence().minBy { delivered.optLong(it) })
        userPrefs.edit().putString("delivered_notifications", delivered.toString()).apply()
        notificationDao.prune()
        if (!isQuietHoursNow()) CizgiNotificationHelper.sendContentNotification(context, id, category, title, body, storyId, screen)
        true
    }

    private suspend fun processNewsNotifications(incoming: List<Story>) {
        val now = System.currentTimeMillis()
        val settings = _preferences.value
        val breakingSince = userPrefs.getLong("breaking_enabled_since", now)
        if (settings.breakingAlerts && now - userPrefs.getLong("last_breaking_alert", 0L) >= 20 * 60_000L) {
            val latest = BreakingNewsClassifier.latest(incoming.filter { NotificationPolicy.isNewStory(it.publishedAtEpochMs, breakingSince, now) }, now)
            if (latest != null && addNewsInbox(latest, "Son Dakika")) {
                userPrefs.edit().putLong("last_breaking_alert", now).apply()
            }
        }
        if (settings.topicAlerts && now - userPrefs.getLong("last_topic_alert", 0L) >= 30 * 60_000L) {
            val since = userPrefs.getLong("topic_enabled_since", now)
            val candidates = incoming.filter { NotificationPolicy.isNewStory(it.publishedAtEpochMs, since, now) && PersonalizedNews.matches(it, settings) }
                .sortedByDescending { it.publishedAtEpochMs }.take(2)
            for (story in candidates) {
                if (addNewsInbox(story, "Konular")) userPrefs.edit().putLong("last_topic_alert", now).apply()
            }
        }
        val zone = ZoneId.of("Europe/Istanbul")
        val today = LocalDate.now(zone)
        if (settings.dailyDigest && LocalTime.now(zone).hour >= 8) {
            val todayStories = _stories.value.filter { it.publishedAtEpochMs in 1..(now + 60_000L) && java.time.Instant.ofEpochMilli(it.publishedAtEpochMs).atZone(zone).toLocalDate() == today }
            val points = NewsDiscoveryInsights.dailyDigest(todayStories)
            if (points.isNotEmpty()) addInbox("digest-$today", "Gündem", "Günün kısa turu", points.take(5).joinToString("\n"), todayStories.firstOrNull()?.id)
        }
    }

    private suspend fun addNewsInbox(story: Story, category: String): Boolean {
        val delivered = runCatching { JSONObject(userPrefs.getString("notified_news_links", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val cutoff = System.currentTimeMillis() - 7 * 86_400_000L
        delivered.keys().asSequence().toList().filter { delivered.optLong(it) < cutoff }.forEach { delivered.remove(it) }
        val keys = NotificationPolicy.newsKeys(story)
        val alreadyCovered = keys.any { delivered.has(it) }
        val inserted = if (alreadyCovered) false else addInbox("news-${story.id}", category, story.title, story.summary, story.id)
        if (alreadyCovered || inserted) {
            keys.forEach { delivered.put(it, System.currentTimeMillis()) }
            while (delivered.length() > 1000) delivered.remove(delivered.keys().asSequence().minBy { delivered.optLong(it) })
            userPrefs.edit().putString("notified_news_links", delivered.toString()).apply()
        }
        return inserted
    }

    fun rememberSportsSelection() {
        val snapshot = _sportsSnapshot.value
        if (snapshot.competition.isBlank() || snapshot.sportType.isBlank()) return
        val selections = userPrefs.getStringSet("followed_sports_selections", emptySet()).orEmpty().toMutableSet()
        selections += "${snapshot.sportType}|${snapshot.competition}"
        userPrefs.edit().putStringSet("followed_sports_selections", selections.toList().takeLast(6).toSet()).apply()
    }

    private suspend fun processSportsNotifications(fixtures: List<SportsMatch>) = sportsNotificationMutex.withLock {
        val baselines = runCatching { JSONObject(userPrefs.getString("sports_score_baselines", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val settings = _preferences.value
        for (match in fixtures) {
            val old = baselines.optJSONObject(match.id)
            val hasScore = match.homeScore != null && match.awayScore != null
            if (settings.matchAlerts && isMatchAlertSelected(match) && old != null) {
                val changedScore = hasScore && old.has("home") && old.has("away") &&
                    (old.optInt("home") != match.homeScore || old.optInt("away") != match.awayScore)
                val started = match.status == com.example.model.MatchStatus.LIVE && old.optString("status") == "UPCOMING"
                val finished = match.status == com.example.model.MatchStatus.FINISHED && old.optString("status") == "LIVE"
                val title = "${match.homeTeam} – ${match.awayTeam}"
                if (started && settings.matchStartAlerts) addInbox("start-${match.id}", "Spor", "Maç başladı · $title", match.competition, screen = "spor")
                if (settings.matchScoreAlerts && (changedScore || finished)) {
                    val last = old.optLong("lastScoreAlert")
                    // Basketball has many scoring changes: consolidate them into at most one update per five minutes.
                    if (match.sportType != "BASKETBOL" || finished || System.currentTimeMillis() - last >= 5 * 60_000L) {
                        val score = "${match.homeScore} – ${match.awayScore}"
                        val eventId = if (finished) "final-${match.id}" else "score-${match.id}-${match.homeScore}-${match.awayScore}"
                        if (hasScore && addInbox(eventId, "Spor", "${if (finished) "Maç bitti" else "Skor değişti"} · $score", "$title · ${match.competition} · ${match.statusLabel}", screen = "spor")) old.put("lastScoreAlert", System.currentTimeMillis())
                    }
                }
            }
            val latest = JSONObject().put("status", match.status.name).put("kickoff", match.kickoffAtEpochMs).put("lastScoreAlert", old?.optLong("lastScoreAlert") ?: 0)
            if (hasScore) latest.put("home", match.homeScore).put("away", match.awayScore)
            baselines.put(match.id, latest)
        }
        val keys = baselines.keys().asSequence().toList()
        keys.filter { key -> val kickoff = baselines.optJSONObject(key)?.optLong("kickoff") ?: 0; kickoff > 0 && kickoff < System.currentTimeMillis() - 3 * 86_400_000L }.forEach { baselines.remove(it) }
        while (baselines.length() > 250) baselines.remove(baselines.keys().next())
        userPrefs.edit().putString("sports_score_baselines", baselines.toString()).apply()
    }

    companion object {
        private const val NEWS_PAGE_SIZE = 120
        private const val INITIAL_NEWS_LOAD_LIMIT = 120
        private const val MAX_STORIES_PER_REFRESH = 3_000
        private const val MAX_IN_MEMORY_STORIES = 10_000
        private const val MAX_ARCHIVE_ARTICLES = 100_000
        private const val MAX_DIAGNOSTIC_SOURCES = 20
        private const val ARCHIVE_BACKFILL_INTERVAL_MS = 15 * 60_000L
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

data class ArchivePage(val stories: List<Story>, val nextOffset: Int, val hasMore: Boolean)
