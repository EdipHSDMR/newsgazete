package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.CizgiRepository
import com.example.ui.components.CizgiBottomBar
import com.example.ui.components.CizgiScreen
import com.example.ui.components.CizgiTopBar
import com.example.ui.screens.AkisScreen
import com.example.ui.screens.BildirimlerScreen
import com.example.ui.screens.HaberDetayScreen
import com.example.ui.screens.HavaScreen
import com.example.ui.screens.KaydedilenlerScreen
import com.example.ui.screens.KaynakKarsilastirmaScreen
import com.example.ui.screens.KesfetScreen
import com.example.ui.screens.ProfilScreen
import com.example.ui.screens.SporScreen
import com.example.ui.screens.TopicsScreen
import com.example.ui.theme.CizgiTheme

import com.example.ui.screens.PiyasaScreen

private sealed interface SubScreen {
    data object None : SubScreen
    data object Weather : SubScreen
    data object Notifications : SubScreen
    data object Topics : SubScreen
    data object Profile : SubScreen
    data class StoryDetail(val storyId: String) : SubScreen
    data class CompareSources(val storyId: String) : SubScreen
}

@Composable
fun CizgiApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { CizgiRepository.getInstance(context) }
    val preferences by repository.preferences.collectAsState()
    val weather by repository.weather.collectAsState()
    val notifications by repository.notifications.collectAsState()

    val isDark = when (preferences.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    CizgiTheme(darkTheme = isDark) {
        var currentTab by remember { mutableStateOf(CizgiScreen.AKIS) }
        var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

        // BackHandler for smooth hierarchical backstack
        BackHandler(enabled = subScreen != SubScreen.None || currentTab != CizgiScreen.AKIS) {
            when {
                subScreen is SubScreen.CompareSources -> {
                    val currentStoryId = (subScreen as SubScreen.CompareSources).storyId
                    subScreen = SubScreen.StoryDetail(currentStoryId)
                }
                subScreen != SubScreen.None -> {
                    subScreen = SubScreen.None
                }
                currentTab != CizgiScreen.AKIS -> {
                    currentTab = CizgiScreen.AKIS
                }
            }
        }

        val unreadCount = remember(notifications) {
            notifications.count { !it.isRead }
        }

        when (val currentSub = subScreen) {
            is SubScreen.StoryDetail -> {
                HaberDetayScreen(
                    storyId = currentSub.storyId,
                    repository = repository,
                    onBack = { subScreen = SubScreen.None },
                    onCompareClick = { storyId -> subScreen = SubScreen.CompareSources(storyId) },
                    onStoryClick = { newStoryId -> subScreen = SubScreen.StoryDetail(newStoryId) },
                )
            }
            is SubScreen.CompareSources -> {
                KaynakKarsilastirmaScreen(
                    storyId = currentSub.storyId,
                    repository = repository,
                    onBack = { subScreen = SubScreen.StoryDetail(currentSub.storyId) },
                )
            }
            else -> {
                Scaffold(
                    modifier = modifier.fillMaxSize(),
                    topBar = {
                        if (subScreen == SubScreen.None) {
                            CizgiTopBar(
                                weatherTemp = "${weather.currentTempC}°",
                                weatherCity = weather.cityName,
                                unreadNotificationCount = unreadCount,
                                onWeatherClick = { subScreen = SubScreen.Weather },
                                onSearchClick = { currentTab = CizgiScreen.KESFET },
                                onNotificationsClick = { subScreen = SubScreen.Notifications },
                                onProfileClick = { subScreen = SubScreen.Profile },
                            )
                        }
                    },
                    bottomBar = {
                        if (subScreen == SubScreen.None) {
                            CizgiBottomBar(
                                currentScreen = currentTab,
                                onNavigate = { screen ->
                                    currentTab = screen
                                    subScreen = SubScreen.None
                                },
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        when (currentSub) {
                            is SubScreen.Weather -> {
                                HavaScreen(
                                    repository = repository,
                                    onBack = { subScreen = SubScreen.None },
                                )
                            }
                            is SubScreen.Notifications -> {
                                BildirimlerScreen(
                                    repository = repository,
                                    onBack = { subScreen = SubScreen.None },
                                    onStoryClick = { storyId -> subScreen = SubScreen.StoryDetail(storyId) },
                                    onSportsClick = {
                                        subScreen = SubScreen.None
                                        currentTab = CizgiScreen.SPOR
                                    },
                                )
                            }
                            is SubScreen.Topics -> {
                                TopicsScreen(
                                    repository = repository,
                                    onBack = { subScreen = SubScreen.None },
                                )
                            }
                            is SubScreen.Profile -> {
                                ProfilScreen(
                                    repository = repository,
                                    onNavigateTopics = { subScreen = SubScreen.Topics },
                                    onNavigateSports = {
                                        subScreen = SubScreen.None
                                        currentTab = CizgiScreen.SPOR
                                    },
                                    onNavigateWeather = { subScreen = SubScreen.Weather },
                                    onNavigateNotifications = { subScreen = SubScreen.Notifications },
                                )
                            }
                            else -> {
                                when (currentTab) {
                                    CizgiScreen.AKIS -> {
                                        AkisScreen(
                                            repository = repository,
                                            onStoryClick = { storyId -> subScreen = SubScreen.StoryDetail(storyId) },
                                            onWeatherClick = { subScreen = SubScreen.Weather },
                                            onSportsClick = { currentTab = CizgiScreen.SPOR },
                                            onMarketClick = { currentTab = CizgiScreen.PIYASA },
                                        )
                                    }
                                    CizgiScreen.KESFET -> {
                                        KesfetScreen(
                                            repository = repository,
                                            onStoryClick = { storyId -> subScreen = SubScreen.StoryDetail(storyId) },
                                        )
                                    }
                                    CizgiScreen.SPOR -> {
                                        SporScreen(
                                            repository = repository,
                                        )
                                    }
                                    CizgiScreen.KAYDEDILENLER -> {
                                        KaydedilenlerScreen(
                                            repository = repository,
                                            onStoryClick = { storyId -> subScreen = SubScreen.StoryDetail(storyId) },
                                            onNavigateHome = { currentTab = CizgiScreen.AKIS },
                                        )
                                    }
                                    CizgiScreen.PIYASA -> {
                                        PiyasaScreen(
                                            repository = repository,
                                        )
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

