package com.example

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.CizgiApp
import com.example.util.CizgiNotificationHelper
import com.example.util.CizgiMessagingBridge
import com.example.data.NewsRefreshScheduler

data class NotificationNavigation(
    val storyId: String? = null,
    val screen: String? = null,
    val requestToken: Long = System.nanoTime(),
)

class MainActivity : ComponentActivity() {
    private var notificationNavigation by mutableStateOf<NotificationNavigation?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationNavigation = notificationNavigationFrom(intent)
        NewsRefreshScheduler.schedule(this)
        CizgiNotificationHelper.createNotificationChannels(this)
        CizgiMessagingBridge.configure(this)
        enableEdgeToEdge()
        setContent {
            CizgiApp(
                notificationNavigation = notificationNavigation,
                onNotificationNavigationHandled = { token ->
                    if (notificationNavigation?.requestToken == token) notificationNavigation = null
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationNavigation = notificationNavigationFrom(intent)
    }

    private fun notificationNavigationFrom(intent: Intent?): NotificationNavigation? {
        val storyId = intent?.getStringExtra("extra_story_id")?.takeIf(String::isNotBlank)
        val screen = intent?.getStringExtra("extra_screen")
            ?.takeIf { it == "spor" || it == "piyasa" || it == "hava" }
        return if (storyId != null || screen != null) NotificationNavigation(storyId, screen) else null
    }
}

