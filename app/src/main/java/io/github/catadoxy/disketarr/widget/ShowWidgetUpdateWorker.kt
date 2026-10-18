package io.github.catadoxy.disketarr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.catadoxy.disketarr.MainActivity
import io.github.catadoxy.disketarr.R

class ShowWidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(ComponentName(applicationContext, ShowWidgetProvider::class.java))
        if (ids.isEmpty()) return Result.success()

        for (id in ids) {
            val prefs = applicationContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val theme = prefs.getString("theme_$id", "system") ?: "system"
            val bgOpacity = prefs.getInt("bg_$id", 100)
            val largeFont = prefs.getBoolean("largefont_$id", false)
            val isDark = when (theme) {
                "light" -> false
                "dark" -> true
                else -> android.content.res.Configuration.UI_MODE_NIGHT_MASK and
                    applicationContext.resources.configuration.uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            val baseColor = if (isDark) 0xFF1F2937.toInt() else 0xFFFFFFFF.toInt()
            val alpha = (bgOpacity.coerceIn(0, 100) * 255 / 100)
            val bgColor = (alpha shl 24) or (baseColor and 0x00FFFFFF)
            val titleColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF111827.toInt()
            val titleSize = if (largeFont) 16f else 14f

            val views = RemoteViews(applicationContext.packageName, R.layout.widget_show_list)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)
            views.setTextViewText(R.id.widget_title, "Disketarr upcoming")
            views.setTextColor(R.id.widget_title, titleColor)
            views.setFloat(R.id.widget_title, "setTextSize", titleSize)

            val serviceIntent = Intent(applicationContext, ShowWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)

            val templateIntent = Intent(applicationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
            }
            val pending = PendingIntent.getActivity(
                applicationContext,
                0,
                templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.widget_list, pending)

            // Configure button opens the config activity for this widget.
            val configIntent = Intent(applicationContext, ShowWidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            val configPending = PendingIntent.getActivity(
                applicationContext,
                id,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_configure, configPending)

            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
            manager.updateAppWidget(id, views)
        }
        return Result.success()
    }
}