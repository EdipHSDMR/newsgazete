package com.example.util

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging

/** Subscribes to stable topics when Firebase is configured; failures are intentionally local. */
object CizgiMessagingBridge {
    fun configure(context: Context) {
        runCatching {
            FirebaseMessaging.getInstance().subscribeToTopic("cizgi-breaking")
            FirebaseMessaging.getInstance().subscribeToTopic("cizgi-sports")
            FirebaseMessaging.getInstance().subscribeToTopic("cizgi-markets")
            context.getSharedPreferences("cizgi_fcm", Context.MODE_PRIVATE).edit()
                .putBoolean("topics_configured", true)
                .apply()
        }
    }
}
