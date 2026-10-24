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
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.network.ApiClient
import io.github.catadoxy.disketarr.viewmodel.AnimeLibraryMode

class ShowWidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Floppy decides whether the anime have a library of their own, and the
        // widget's title and source follow that. Ask the server, so a change
        // there shows up without opening the app.
        val store = SettingsStore(applicationContext)
        runCatching {
            AnimeLibraryMode.read(ApiClient.getService(store))?.let { store.animeLibraryMode = it }
        }

        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(ComponentName(applicationContext, ShowWidgetProvider::class.java))
        if (ids.isEmpty()) return Result.success()

        for (id in ids) {
            ShowWidgetUpdater.update(applicationContext, id)
        }
        return Result.success()
    }
}