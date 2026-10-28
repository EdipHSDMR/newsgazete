package com.example.util

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Optional FCM bridge. It is inert until a google-services.json is supplied to the build. */
@Suppress("DEPRECATION")
class CizgiFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()
        val category = data["category"] ?: "Gündem"
        val storyId = data["storyId"]?.takeIf(String::isNotBlank)
        val screen = data["screen"]?.takeIf { it == "spor" || it == "piyasa" || it == "hava" }
        val id = data["id"] ?: "fcm:${title.hashCode()}:${body.hashCode()}"
        CizgiNotificationHelper.sendContentNotification(this, id, category, title, body, storyId, screen)
    }

    @Deprecated("Firebase callback is deprecated by the SDK but remains the compatibility entry point.")
    override fun onNewToken(token: String) {
        getSharedPreferences("cizgi_fcm", MODE_PRIVATE).edit().putString("token", token).apply()
    }
}
