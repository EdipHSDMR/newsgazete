package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.model.Story

/** Lightweight home-screen summary. It reads a tiny local snapshot and never blocks on network. */
class CizgiWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateViews(context, manager, ids)
    }

    override fun onEnabled(context: Context) {
        update(context, null)
    }

    companion object {
        private const val PREFS = "cizgi_widget"
        private const val TITLE = "title"
        private const val META = "meta"

        fun update(context: Context, story: Story?) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (story != null) {
                prefs.edit()
                    .putString(TITLE, story.title)
                    .putString(META, "${story.publisher} · ${story.publishedAt}")
                    .apply()
            }
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CizgiWidgetProvider::class.java)
            updateViews(context, manager, manager.getAppWidgetIds(component))
        }

        private fun updateViews(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString(TITLE, "Çizgi gündemini aç") ?: "Çizgi gündemini aç"
            val meta = prefs.getString(META, "Haberler ve son dakika gelişmeleri") ?: "Haberler ve son dakika gelişmeleri"
            val intent = PendingIntent.getActivity(
                context,
                4101,
                Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_news).apply {
                    setTextViewText(R.id.widget_title, title)
                    setTextViewText(R.id.widget_meta, meta)
                    setOnClickPendingIntent(R.id.widget_root, intent)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}
