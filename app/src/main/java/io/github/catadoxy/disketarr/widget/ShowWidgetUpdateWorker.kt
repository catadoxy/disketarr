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
            val views = RemoteViews(applicationContext.packageName, R.layout.widget_show_list)
            views.setTextViewText(R.id.widget_title, "Disketarr upcoming")

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

            views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
            manager.updateAppWidget(id, views)
        }
        return Result.success()
    }
}