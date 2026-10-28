package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

object CizgiNotificationHelper {

    const val CHANNEL_TOPICS = "cizgi_followed_topics"
    const val CHANNEL_DIGEST = "cizgi_daily_digest"

    const val CHANNEL_BREAKING = "cizgi_breaking_news_v2"
    const val CHANNEL_SPORTS = "cizgi_sports_goals_v2"
    const val CHANNEL_MARKETS = "cizgi_market_alerts_v2"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Son Dakika Kanalı
            val breakingChannel = NotificationChannel(
                CHANNEL_BREAKING,
                "Son Dakika Haberleri",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Açtığında önemli son dakika haberlerini gösterir"
                enableVibration(false)
                setShowBadge(false)
            }

            // 2. Canlı Spor & Gol Kanalı
            val sportsChannel = NotificationChannel(
                CHANNEL_SPORTS,
                "Canlı Gol ve Skor Bildirimleri",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Takip ettiğin maçların başlangıç ve gol bildirimleri"
                enableVibration(false)
                setShowBadge(false)
            }

            // 3. Piyasa ve Gündem Özeti Kanalı
            val marketsChannel = NotificationChannel(
                CHANNEL_MARKETS,
                "Piyasa ve Günün Özeti",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Piyasa ve gündem bildirimleri"
                enableVibration(false)
                setShowBadge(false)
            }

            val topicsChannel = NotificationChannel(CHANNEL_TOPICS, "Takip ettiğim konular", NotificationManager.IMPORTANCE_DEFAULT)
            val digestChannel = NotificationChannel(CHANNEL_DIGEST, "Günün kısa turu", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannels(listOf(breakingChannel, sportsChannel, marketsChannel, topicsChannel, digestChannel))
        }
    }

    fun sendContentNotification(context: Context, id: String, category: String, title: String, body: String, storyId: String?, screen: String?) {
        if (!hasNotificationPermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createNotificationChannels(context)
        val channel = when (category) { "Spor" -> CHANNEL_SPORTS; "Son Dakika" -> CHANNEL_BREAKING; "Konular" -> CHANNEL_TOPICS; else -> CHANNEL_DIGEST }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_story_id", storyId)
            putExtra("extra_screen", screen)
        }
        val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).build()
        try { NotificationManagerCompat.from(context).notify(id.hashCode(), notification) } catch (_: SecurityException) { }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun sendBreakingNotification(
        context: Context,
        title: String,
        body: String,
        storyId: String? = null
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_story_id", storyId)
            putExtra("extra_screen", "detail")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            storyId?.hashCode() ?: 1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BREAKING)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚡ SON DAKİKA · $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
        } catch (_: SecurityException) {}
    }

    fun sendGoalNotification(
        context: Context,
        matchTitle: String,
        minute: Int,
        player: String,
        score: String
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_screen", "spor")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            ("goal:$matchTitle:$minute:$player".hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = "⚽ GOL! $player ($score)"
        val notifBody = "$matchTitle karşılaşmasının $minute'. dakikasında $player ağları havalandırdı!"

        val notification = NotificationCompat.Builder(context, CHANNEL_SPORTS)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(notifTitle)
            .setContentText(notifBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifBody))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(2001, notification)
        } catch (_: SecurityException) {}
    }

    fun sendMatchStartNotification(context: Context, matchTitle: String, competition: String, sportType: String) {
        if (!hasNotificationPermission(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_screen", "spor")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("start:$matchTitle".hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = when (sportType) {
            "BASKETBOL" -> "🏀"
            else -> "⚽"
        }
        val title = "$icon Maç başladı"
        val body = "$competition · $matchTitle"
        val notification = NotificationCompat.Builder(context, CHANNEL_SPORTS)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(2100 + (matchTitle.hashCode() and 0x7fff), notification)
        } catch (_: SecurityException) {}
    }

    fun sendMarketNotification(
        context: Context,
        title: String,
        body: String
    ) {
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_screen", "piyasa")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            3001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MARKETS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📈 $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(3001, notification)
        } catch (_: SecurityException) {}
    }

    fun sendWeatherNotification(context: Context, city: String, body: String) {
        if (!hasNotificationPermission(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_screen", "hava")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            ("weather:$city".hashCode()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MARKETS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🌦️ $city hava uyarısı")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try { NotificationManagerCompat.from(context).notify(3101, notification) } catch (_: SecurityException) { }
    }
}

