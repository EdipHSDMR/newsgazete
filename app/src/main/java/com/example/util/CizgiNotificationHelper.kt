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

    const val CHANNEL_BREAKING = "cizgi_breaking_news"
    const val CHANNEL_SPORTS = "cizgi_sports_goals"
    const val CHANNEL_MARKETS = "cizgi_market_alerts"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Son Dakika Kanalı
            val breakingChannel = NotificationChannel(
                CHANNEL_BREAKING,
                "Son Dakika Haberleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kritik son dakika gelişmeleri ve manşet bildirimleri"
                enableVibration(true)
                setShowBadge(true)
            }

            // 2. Canlı Spor & Gol Kanalı
            val sportsChannel = NotificationChannel(
                CHANNEL_SPORTS,
                "Canlı Gol ve Skor Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Takip ettiğiniz maçlardaki goller, düdükler ve canlı sonuçlar"
                enableVibration(true)
                setShowBadge(true)
            }

            // 3. Piyasa ve Gündem Özeti Kanalı
            val marketsChannel = NotificationChannel(
                CHANNEL_MARKETS,
                "Piyasa ve Günün Özeti",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Döviz, altın, borsa kapanışları ve günün 5 maddelik özeti"
                enableVibration(false)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(breakingChannel, sportsChannel, marketsChannel))
        }
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
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_BREAKING)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚡ SON DAKİKA · $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
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
            (System.currentTimeMillis() % 10000).toInt() + 1,
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(2001, notification)
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
            (System.currentTimeMillis() % 10000).toInt() + 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MARKETS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📈 $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(3001, notification)
        } catch (_: SecurityException) {}
    }
}
