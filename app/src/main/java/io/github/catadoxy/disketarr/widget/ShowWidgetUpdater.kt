package io.github.catadoxy.disketarr.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.catadoxy.disketarr.MainActivity
import io.github.catadoxy.disketarr.R

object ShowWidgetUpdater {
    fun update(context: Context, appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val theme = prefs.getString("theme_$appWidgetId", "system") ?: "system"
        val bgOpacity = prefs.getInt("bg_$appWidgetId", 100)
        val largeFont = prefs.getBoolean("largefont_$appWidgetId", false)
        val isDark = when (theme) {
            "light" -> false
            "dark" -> true
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val baseColor = if (isDark) 0xFF1F2937.toInt() else 0xFFFFFFFF.toInt()
        val alpha = (bgOpacity.coerceIn(0, 100) * 255 / 100)
        val bgColor = (alpha shl 24) or (baseColor and 0x00FFFFFF)
        val titleColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF111827.toInt()
        val titleSize = if (largeFont) 16f else 14f

        val views = RemoteViews(context.packageName, R.layout.widget_show_list)
        views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
        views.setTextViewText(R.id.widget_title, "Disketarr upcoming")
        views.setTextColor(R.id.widget_title, titleColor)
        views.setFloat(R.id.widget_title, "setTextSize", titleSize)

        val serviceIntent = Intent(context, ShowWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)

        val templateIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
        }
        val pending = android.app.PendingIntent.getActivity(
            context,
            0,
            templateIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
        )
        views.setPendingIntentTemplate(R.id.widget_list, pending)

        views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
        manager.updateAppWidget(appWidgetId, views)
        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }
}