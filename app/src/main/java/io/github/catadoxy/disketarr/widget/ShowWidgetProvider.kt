package io.github.catadoxy.disketarr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.catadoxy.disketarr.MainActivity

class ShowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val request = OneTimeWorkRequestBuilder<ShowWidgetUpdateWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "disketarr_widget_update",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val request = OneTimeWorkRequestBuilder<ShowWidgetUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}