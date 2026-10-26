package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.NotificationNavigation
import com.example.data.CizgiRepository
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.CizgiTheme
import com.example.ui.theme.LocalReducedMotion
import com.example.util.ReaderSettings

private sealed interface SubScreen {
    data object None : SubScreen
    data object Weather : SubScreen
    data object Notifications : SubScreen
    data object Topics : SubScreen
    data object Profile : SubScreen
    data object Personalize : SubScreen
    data class StoryDetail(val storyId: String) : SubScreen
    data class CompareSources(val storyId: String) : SubScreen
}

@Composable
fun CizgiApp(modifier: Modifier = Modifier, notificationNavigation: NotificationNavigation? = null, onNotificationNavigationHandled: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember { CizgiRepository.getInstance(context) }
    val preferences by repository.preferences.collectAsState()
    val weather by repository.weather.collectAsState()
    val notifications by repository.notifications.collectAsState()
    val dark = when (preferences.themeMode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val density = LocalDensity.current
    val holder = rememberSaveableStateHolder()
    var currentTab by rememberSaveable { mutableStateOf(CizgiScreen.AKIS) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    fun close() { subScreen = SubScreen.None }

    LaunchedEffect(notificationNavigation?.requestToken) {
        val request = notificationNavigation ?: return@LaunchedEffect
        when {
            request.storyId != null -> subScreen = SubScreen.StoryDetail(request.storyId)
            request.screen == "spor" -> { currentTab = CizgiScreen.SPOR; close() }
            request.screen == "piyasa" -> { currentTab = CizgiScreen.PIYASA; close() }
        }
        onNotificationNavigationHandled(request.requestToken)
    }
    BackHandler(subScreen != SubScreen.None || currentTab != CizgiScreen.AKIS) {
        when (val screen = subScreen) {
            is SubScreen.CompareSources -> subScreen = SubScreen.StoryDetail(screen.storyId)
            SubScreen.None -> currentTab = CizgiScreen.AKIS
            else -> close()
        }
    }
    CompositionLocalProvider(LocalReducedMotion provides preferences.reduceMotion, LocalDensity provides Density(density.density, density.fontScale * ReaderSettings.fontScale(preferences.fontSize))) {
        CizgiTheme(darkTheme = dark, highContrast = preferences.highContrast) {
            if (!preferences.onboardingCompleted || subScreen == SubScreen.Personalize) {
                OnboardingScreen(repository) { close() }
                return@CizgiTheme
            }
            AnimatedContent(
                targetState = subScreen, modifier = modifier.fillMaxSize(), label = "page",
                transitionSpec = {
                    if (preferences.reduceMotion) EnterTransition.None togetherWith ExitTransition.None else (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 }) togetherWith fadeOut(tween(120))
                },
            ) { screen ->
                when (screen) {
                    is SubScreen.StoryDetail -> HaberDetayScreen(
                        storyId = screen.storyId, repository = repository, onBack = ::close,
                        onCompareClick = { subScreen = SubScreen.CompareSources(it) },
                        onStoryClick = { subScreen = SubScreen.StoryDetail(it) },
                    )
                    is SubScreen.CompareSources -> KaynakKarsilastirmaScreen(
                        storyId = screen.storyId, repository = repository,
                        onBack = { subScreen = SubScreen.StoryDetail(screen.storyId) },
                    )
                    else -> Scaffold(
                        topBar = {
                            if (screen == SubScreen.None) CizgiTopBar(
                                weatherTemp = weather.currentTempC?.let { "$it°" } ?: "–",
                                weatherCity = weather.cityName, unreadNotificationCount = notifications.count { !it.isRead },
                                onWeatherClick = { subScreen = SubScreen.Weather }, onSearchClick = { currentTab = CizgiScreen.KESFET },
                                onNotificationsClick = { subScreen = SubScreen.Notifications }, onProfileClick = { subScreen = SubScreen.Profile },
                            )
                        },
                        bottomBar = {
                            if (screen == SubScreen.None) CizgiBottomBar(currentTab, { currentTab = it; close() })
                        },
                        containerColor = MaterialTheme.colorScheme.background,
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            when (screen) {
                                SubScreen.Weather -> HavaScreen(repository, ::close)
                                SubScreen.Notifications -> BildirimlerScreen(repository, ::close, { subScreen = SubScreen.StoryDetail(it) }, { close(); currentTab = CizgiScreen.SPOR })
                                SubScreen.Topics -> TopicsScreen(repository, ::close)
                                SubScreen.Profile -> ProfilScreen(
                                    repository = repository, onBack = ::close,
                                    onNavigatePersonalize = { subScreen = SubScreen.Personalize },
                                    onNavigateTopics = { subScreen = SubScreen.Topics },
                                    onNavigateSports = { close(); currentTab = CizgiScreen.SPOR },
                                    onNavigateWeather = { subScreen = SubScreen.Weather }, onNavigateNotifications = { subScreen = SubScreen.Notifications },
                                )
                                else -> AnimatedContent(
                                    targetState = currentTab, label = "tab", transitionSpec = {
                                        if (preferences.reduceMotion) EnterTransition.None togetherWith ExitTransition.None else (fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 24 }) togetherWith fadeOut(tween(100))
                                    },
                                ) { tab ->
                                    holder.SaveableStateProvider(tab.route) {
                                        when (tab) {
                                            CizgiScreen.AKIS -> AkisScreen(repository, { subScreen = SubScreen.StoryDetail(it) }, { currentTab = CizgiScreen.SPOR }, { currentTab = CizgiScreen.PIYASA }, onTopicsClick = { subScreen = SubScreen.Topics })
                                            CizgiScreen.KESFET -> KesfetScreen(repository, { subScreen = SubScreen.StoryDetail(it) })
                                            CizgiScreen.SPOR -> SporScreen(repository)
                                            CizgiScreen.KAYDEDILENLER -> KaydedilenlerScreen(repository, { subScreen = SubScreen.StoryDetail(it) }, { currentTab = CizgiScreen.AKIS })
                                            CizgiScreen.PIYASA -> PiyasaScreen(repository)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
