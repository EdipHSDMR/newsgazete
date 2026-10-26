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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    val hasMorePressArchivePages: Boolean
        get() = pressArchiveTotalPages > 1 && pressArchiveNextPage <= pressArchiveTotalPages

    // Remote Services
    private val rssNewsService = RssNewsService(httpClient)
    private val pressNewsApiService = PressNewsApiService(httpClient)
    private val weatherApiService = WeatherApiService(httpClient)
    private val footballApiService = FootballApiService(httpClient)
    private val volleyballApiService = com.example.data.remote.VolleyballApiService(httpClient)
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
    val savedArticles: StateFlow<List<Story>> = savedArticleDao.getAllSavedArticles()
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

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _financialAssets = MutableStateFlow<List<FinancialAsset>>(emptyList())
    val financialAssets: StateFlow<List<com.example.model.FinancialAsset>> = _financialAssets.asStateFlow()

    private val _bistStocks = MutableStateFlow<List<com.example.data.remote.BISTStock>>(emptyList())
    val bistStocks: StateFlow<List<com.example.data.remote.BISTStock>> = _bistStocks.asStateFlow()
    private val _marketStatusMessage = MutableStateFlow("")
    val marketStatusMessage: StateFlow<String> = _marketStatusMessage.asStateFlow()

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val bistRetrofitApi = com.example.data.remote.BistRetrofitApi(httpClient)

    private val refreshMutex = Mutex()
    private val sentBreakingStoryIds = mutableSetOf<String>()
    private val sentGoalAlertIds = mutableSetOf<String>()
    private val sentMatchStartAlertIds = mutableSetOf<String>()
    private val sportsFixtureSelectionsLoaded = mutableSetOf<String>()
    private var lastBreakingNotifTimeMs = 0L

    private var newsStreamJob: kotlinx.coroutines.Job? = null
    private var marketStreamJob: kotlinx.coroutines.Job? = null
    private var weatherStreamJob: kotlinx.coroutines.Job? = null
    private var activeSportsKey: String? = null
    private var sportsRefreshJob: Job? = null
    private val newsPageMutex = Mutex()
    private val newsRefreshMutex = Mutex()
    private val marketRefreshMutex = Mutex()
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
        val breakingAlerts = prefs.getBoolean("breaking_alerts", false)
        val matchAlerts = prefs.getBoolean("match_alerts", false)
        val quietHoursEnabled = prefs.getBoolean("quiet_hours_enabled", true)
        val quietHoursStart = prefs.getString("quiet_hours_start", "23:00") ?: "23:00"
        val quietHoursEnd = prefs.getString("quiet_hours_end", "07:00") ?: "07:00"
        val matchAlertIds = prefs.getStringSet("match_alert_ids", emptySet())?.toSet() ?: emptySet()
        val followedTopics = prefs.getStringSet("followed_topics", setOf("Türkiye", "Ekonomi", "Teknoloji", "Spor")) ?: emptySet()
        val followedTeams = prefs.getStringSet("followed_teams", setOf("Galatasaray", "Fenerbahçe")) ?: emptySet()
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
                quietHoursEnabled = quietHoursEnabled,
                quietHoursStart = quietHoursStart,
                quietHoursEnd = quietHoursEnd,
            )
        }
        _matchAlertIds.value = matchAlertIds
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
                    val latestIds = latest.mapTo(mutableSetOf()) { it.id }
                    NewsStoryDeduplicator.merge(latest + current.filterNot { it.id in latestIds })
                        .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
                        .take(MAX_IN_MEMORY_STORIES)
                }
                refreshDailyDigest(_stories.value)
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

    suspend fun refreshNewsInBackground(): Boolean = refreshNewsData()

    private suspend fun refreshNewsData(): Boolean = newsRefreshMutex.withLock {
        try {
            // Publish a few fresh publisher RSS items first so a cold launch does not wait for
            // every slow feed or the press archive. The complete refresh follows in the same job.
            val quickStories = withTimeoutOrNull(7_000L) { rssNewsService.fetchQuickNews() }.orEmpty()
            val quickPublished = if (quickStories.isNotEmpty()) publishLiveNews(quickStories) else false
            val (rssStories, pressPage) = coroutineScope {
                val rss = async { rssNewsService.fetchRealNews() }
                val press = async { pressNewsApiService.fetchPage(page = 1) }
                rss.await() to press.await()
            }
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
        val now = System.currentTimeMillis()
        val criticalBreaking = liveFeed.asSequence().filter { story ->
            _preferences.value.breakingAlerts && story.isBreaking && story.id !in sentBreakingStoryIds &&
                BreakingNewsClassifier.isBreaking(story.title, story.publishedAtEpochMs, now, story.isBreaking) &&
                HIGH_IMPACT_BREAKING_TERMS.any { term -> story.title.contains(term, ignoreCase = true) }
        }.maxByOrNull { it.publishedAtEpochMs }
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
            if (!isQuietHoursNow()) {
                CizgiNotificationHelper.sendBreakingNotification(
                    context = context,
                    title = criticalBreaking.title,
                    body = criticalBreaking.summary,
                    storyId = criticalBreaking.id,
                )
            }
        }
        return true
    }

    private fun mergePressAndRss(pressStories: List<Story>, rssStories: List<Story>): List<Story> {
        return NewsStoryDeduplicator.merge(pressStories + rssStories)
            .sortedWith(compareByDescending<Story> { it.publishedAtEpochMs }.thenByDescending { it.id })
            .take(MAX_STORIES_PER_REFRESH)
    }

    private suspend fun loadNextPressArchivePage() {
        if (pressArchiveNextPage > pressArchiveTotalPages) return
        val pageNumber = pressArchiveNextPage
        val page = pressNewsApiService.fetchPage(page = pageNumber) ?: return
        pressArchiveTotalPages = maxOf(pressArchiveTotalPages, page.totalPages)
        val savedIds = savedArticles.value.map { it.id }.toSet()
        val incoming = page.stories.map { it.copy(isSaved = it.id in savedIds) }

        if (incoming.isNotEmpty()) {
            newsRefreshMutex.withLock {
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
                val description = metadata?.description?.takeIf { it.length >= MIN_SOURCE_DESCRIPTION_CHARS }
                val imageUrl = story.imageUrl.ifBlank { metadata?.imageUrl.orEmpty() }
                val sourceText = story.fullText.takeIf { it.length >= MIN_SOURCE_DESCRIPTION_CHARS }
                    ?: description.orEmpty().ifBlank { story.summary }
                val fullText = story.fullText.takeIf { it.length >= MIN_SOURCE_DESCRIPTION_CHARS }
                    ?: description.orEmpty().ifBlank { story.fullText }
                val summary = NewsSummary.bestAvailableSummary(
                    summaryCandidate = story.summary,
                    sourceText = sourceText,
                    title = story.title,
                ).ifBlank { NewsSummary.concise(sourceText, story.title) }
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
            } catch (_: Exception) {
                sourceDescriptionAttempts.remove(storyId)
            }
        }
    }

    suspend fun loadStoryFromArchive(storyId: String): Story? {
        _stories.value.firstOrNull { it.id == storyId }?.let { return it }
        val article = withContext(Dispatchers.IO) { newsArticleDao.getById(storyId) } ?: return null
        val saved = savedArticles.value.any { it.id == storyId }
        val story = article.toStory().copy(isSaved = saved)
        _stories.update { current ->
            if (current.any { it.id == storyId }) current else listOf(story) + current
        }
        return story
    }

    suspend fun loadFullArticle(storyId: String): Story? {
        val story = _stories.value.firstOrNull { it.id == storyId } ?: return null
        val hasLicensedArticleBody = story.fullText.length >= 400 &&
            (story.id.startsWith("pha-") || story.fullText.length >= 1_200)
        val content = if (hasLicensedArticleBody) {
            com.example.data.remote.SourceArticleContent(story.fullText, story.paragraphs, story.imageUrl)
        } else {
            rssNewsService.fetchFullArticle(story.canonicalUrl) ?: return null
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
        _stories.update { current -> current.map { if (it.id == storyId) updated else it } }
        withContext(Dispatchers.IO) {
            newsArticleDao.upsertAll(listOf(NewsArticleEntity.fromStory(updated)))
        }
        return updated
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
        if (sportType == "VOLEYBOL") volleyballApiService.clearLastError()
        try {
            val cachedScorers = cachedSportStatDao.getStatsDirect(competition, scorerCacheType(sportType))
            if (cachedScorers.isNotEmpty() && activeSportsKey == requestKey) {
                _sportsSnapshot.update { it.copy(topScorers = cachedScorers.map { row -> row.toTopScorer() }) }
            }
        } catch (_: Exception) {}

        try {
            val hadPreviousFixtures = requestKey in sportsFixtureSelectionsLoaded
            val previousLiveIds = _sportsSnapshot.value.matches
                .filter { it.status == com.example.model.MatchStatus.LIVE }
                .mapTo(mutableSetOf()) { it.id }
            val fixtures = if (sportType == "VOLEYBOL") {
                volleyballApiService.fetchFixtures(competition)
            } else {
                footballApiService.fetchFixtures(competition, sportType)
            }
            if (activeSportsKey != requestKey) return
            _sportsSnapshot.update {
                it.copy(
                    matches = fixtures,
                    errorMessage = if (sportType == "VOLEYBOL") volleyballApiService.lastErrorMessage else "",
                )
            }
            sportsFixtureSelectionsLoaded.add(requestKey)

            fixtures.asSequence()
                .filter { hadPreviousFixtures && it.status == com.example.model.MatchStatus.LIVE && it.id !in previousLiveIds }
                .filter(::isMatchAlertSelected)
                .forEach { match ->
                    val alertId = "start-${match.id}"
                    if (_preferences.value.matchAlerts && sentMatchStartAlertIds.add(alertId)) {
                        val title = "Maç başladı: ${match.homeTeam} - ${match.awayTeam}"
                        val body = "${match.competition} · ${match.statusLabel}"
                        _notifications.update { list -> listOf(NotificationItem(alertId, "Spor", title, body, "Az önce")) + list.take(19) }
                        if (!isQuietHoursNow()) {
                            CizgiNotificationHelper.sendMatchStartNotification(
                                context = context,
                                matchTitle = "${match.homeTeam} - ${match.awayTeam}",
                                competition = match.competition,
                                sportType = match.sportType,
                            )
                        }
                    }
                }

            if (!hadPreviousFixtures) {
                fixtures.asSequence()
                    .filter { it.status == com.example.model.MatchStatus.LIVE }
                    .flatMap { match -> match.events.asSequence().filter { it.type == "GOL" }.map { match to it } }
                    .forEach { (match, goal) -> sentGoalAlertIds.add("goal-${match.id}-${goal.minute}-${goal.player}") }
            }

            fixtures.asSequence()
                .filter { it.status == com.example.model.MatchStatus.LIVE }
                .flatMap { match -> match.events.asSequence().filter { it.type == "GOL" }.map { match to it } }
                .forEach { (match, goal) ->
                    val alertId = "goal-${match.id}-${goal.minute}-${goal.player}"
                    if (_preferences.value.matchAlerts && isMatchAlertSelected(match) && sentGoalAlertIds.add(alertId)) {
                        val notif = NotificationItem(
                            id = alertId,
                            category = "Spor",
                            title = "⚽ GOL! ${goal.player} (${match.homeTeam} ${match.homeScore} - ${match.awayScore} ${match.awayTeam})",
                            body = "${match.competition}: ${goal.minute}'. dakikada ${goal.player} fileleri havalandırdı!",
                            timeAgo = "${goal.minute}'",
                        )
                        _notifications.update { list -> listOf(notif) + list.take(19) }
                        if (!isQuietHoursNow()) {
                            CizgiNotificationHelper.sendGoalNotification(
                                context = context,
                                matchTitle = "${match.homeTeam} vs ${match.awayTeam}",
                                minute = goal.minute,
                                player = goal.player,
                                score = "${match.homeScore ?: 0} - ${match.awayScore ?: 0}",
                            )
                        }
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {}

        try {
            val currentStandings = if (sportType == "VOLEYBOL") {
                volleyballApiService.fetchStandings(competition)
            } else {
                footballApiService.fetchStandings(competition, sportType)
            }
            if (activeSportsKey != requestKey) return
            _sportsSnapshot.update {
                it.copy(
                    standings = currentStandings,
                    errorMessage = if (sportType == "VOLEYBOL") volleyballApiService.lastErrorMessage else it.errorMessage,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {}

        try {
            val scorers = if (sportType == "VOLEYBOL") emptyList() else footballApiService.fetchTopScorers(competition, sportType)
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

    private fun scorerCacheType(sportType: String): String = when (sportType) {
        "BASKETBOL" -> "SAYI"
        "VOLEYBOL" -> "VOLEYBOL"
        else -> "GOL"
    }

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
                _marketStatusMessage.value = (marketApiService.availabilityWarnings() + bistRetrofitApi.lastErrorMessage)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" ")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep the last successful quotes visible and let the UI display their source timestamps.
        } finally {
            marketRefreshMutex.unlock()
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

    fun setBreakingAlerts(enabled: Boolean) {
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
        if (!settings.quietHoursEnabled) return false
        val start = runCatching { java.time.LocalTime.parse(settings.quietHoursStart) }.getOrNull() ?: return false
        val end = runCatching { java.time.LocalTime.parse(settings.quietHoursEnd) }.getOrNull() ?: return false
        if (start == end) return true
        val now = java.time.LocalTime.now()
        return if (start < end) now >= start && now < end else now >= start || now < end
    }

    fun clearSearchHistory() {
        context.getSharedPreferences("cizgi_user_prefs", Context.MODE_PRIVATE)
            .edit().remove("search_history").remove("recent_searches").apply()
    }

    fun clearLocalNewsData() {
        scope.launch(Dispatchers.IO) {
            newsArticleDao.clearAll()
            database.cachedStoryDao().clearAll()
            cachedSportStatDao.clearAll()
        }
    }

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
        private const val INITIAL_NEWS_LOAD_LIMIT = 3_000
        private const val MAX_STORIES_PER_REFRESH = 3_000
        private const val MAX_IN_MEMORY_STORIES = 10_000
        private const val MAX_ARCHIVE_ARTICLES = 100_000
        private const val MIN_SOURCE_DESCRIPTION_CHARS = 40
        private const val SOURCE_DESCRIPTION_RETRY_MS = 12 * 60 * 60 * 1_000L
        private const val MAX_IMAGES_PER_ENRICHMENT = 24
        private const val IMAGE_ENRICHMENT_CONCURRENCY = 4
        private const val IMAGE_ENRICHMENT_RETRY_MS = 12 * 60 * 60 * 1_000L
        private val HIGH_IMPACT_BREAKING_TERMS = listOf(
            "deprem", "tsunami", "tahliye", "can kaybı", "yaralı", "afet", "yangın",
            "saldırı", "patlama", "acil durum", "seferberlik",
        )

        @Volatile
        private var instance: CizgiRepository? = null

        fun getInstance(context: Context): CizgiRepository {
            return instance ?: synchronized(this) {
                instance ?: CizgiRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

