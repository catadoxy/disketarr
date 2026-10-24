package io.github.catadoxy.disketarr.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import io.github.catadoxy.disketarr.R
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.viewmodel.AnimeLibraryMode

object ShowWidgetUpdater {
    fun update(context: Context, appWidgetId: Int, feedback: String? = null) {
        val manager = AppWidgetManager.getInstance(context)
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val type = prefs.getString("type_$appWidgetId", "upcoming") ?: "upcoming"
        val isDark = isWidgetDark(context, appWidgetId)
        val bgOpacity = prefs.getInt("bg_$appWidgetId", 100)
        val largeFont = prefs.getBoolean("largefont_$appWidgetId", false)

        val baseColor = if (isDark) 0xFF1F2937.toInt() else 0xFFFFFFFF.toInt()
        val alpha = (bgOpacity.coerceIn(0, 100) * 255 / 100)
        val bgColor = (alpha shl 24) or (baseColor and 0x00FFFFFF)
        val titleColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF111827.toInt()
        val titleSize = if (largeFont) 16f else 14f
        val source = prefs.getString("source_$appWidgetId", "both") ?: "both"
        // With the anime filed in the TV library there is no Anime library to
        // read, so the widget is a Shows widget whatever the stored source says.
        val animeLibraryExists = runCatching {
            AnimeLibraryMode.hasAnimeLibrary(SettingsStore(context).animeLibraryMode)
        }.getOrDefault(true)
        val effectiveSource = if (animeLibraryExists) source else "tv"
        val title = when (type) {
            "recent" -> "Disketarr recent"
            "shows" -> if (effectiveSource == "anime") "Disketarr anime" else "Disketarr shows"
            else -> "Disketarr upcoming"
        }
        val emptyText = when (type) {
            "recent" -> "No recent activity"
            "shows" -> if (effectiveSource == "anime") "No anime" else "No shows"
            else -> "No upcoming episodes"
        }

        val views = RemoteViews(context.packageName, R.layout.widget_show_list)
        views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setFloat(R.id.widget_title, "setTextSize", titleSize)
        views.setTextViewText(R.id.widget_empty, emptyText)
        if (feedback != null) {
            views.setTextViewText(R.id.widget_feedback, feedback)
            views.setViewVisibility(R.id.widget_feedback, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_feedback, android.view.View.GONE)
        }

        val serviceIntent = Intent(context, ShowWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_list, serviceIntent)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        val templateIntent = Intent(context, WidgetActionReceiver::class.java)
        val pending = android.app.PendingIntent.getBroadcast(
            context,
            0,
            templateIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
        )
        views.setPendingIntentTemplate(R.id.widget_list, pending)

        // Lets the widget be reconfigured after it has already been placed. The
        // launcher does not always offer a settings entry on long-press, so the
        // header carries its own settings button.
        val configureIntent = Intent(context, ShowWidgetConfigActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val configurePending = android.app.PendingIntent.getActivity(
            context,
            appWidgetId,
            configureIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_configure, configurePending)
        views.setInt(R.id.widget_configure, "setColorFilter", titleColor)

        manager.updateAppWidget(appWidgetId, views)
        manager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)

        // The rebind triggered above can make some launchers re-apply the
        // previously rendered RemoteViews, which would leave the title and
        // background one change behind. Re-apply ours once the rebind settles.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            manager.updateAppWidget(appWidgetId, views)
        }, 250)
    }
}

/**
 * Resolves the widget's effective light/dark mode from its saved theme preference,
 * falling back to the system dark mode for the "system" setting.
 */
internal fun isWidgetDark(context: Context, appWidgetId: Int): Boolean {
    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    return when (prefs.getString("theme_$appWidgetId", "system") ?: "system") {
        "light" -> false
        "dark" -> true
        else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
