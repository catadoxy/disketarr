package io.github.catadoxy.disketarr.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.network.ApiClient

/** Marks one episode watched through Floppy, then refreshes every widget instance. */
class MarkWatchedWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val source = inputData.getString(WidgetActionReceiver.EXTRA_SOURCE) ?: return Result.failure()
        val mediaType = inputData.getString(WidgetActionReceiver.EXTRA_MEDIA_TYPE) ?: return Result.failure()
        val mediaId = inputData.getString(WidgetActionReceiver.EXTRA_MEDIA_ID) ?: return Result.failure()
        val season = inputData.getInt(WidgetActionReceiver.EXTRA_SEASON, -1)
        val episode = inputData.getInt(WidgetActionReceiver.EXTRA_EPISODE, -1)
        if (season < 0 || episode < 0) return Result.failure()

        val settings = SettingsStore(applicationContext)
        if (!settings.isConfigured) return Result.failure()

        val marked = runCatching {
            ApiClient.getService(settings).markEpisodeWatched(
                mediaType = widgetMediaType(mediaType),
                source = source,
                mediaId = mediaId,
                seasonNumber = season,
                episodeNumber = episode,
            )
        }
        if (marked.isFailure) {
            toast("Could not mark watched")
            notify("Could not mark watched")
            return Result.failure()
        }
        toast("Marked watched")
        notify("Marked watched")

        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(ComponentName(applicationContext, ShowWidgetProvider::class.java))
        // Refresh more than once: the launcher rebinds the collection view
        // lazily, and a single update right after the API call is often ignored
        // while the app is in the background.
        ids.forEach { ShowWidgetUpdater.update(applicationContext, it) }
        kotlinx.coroutines.delay(1000)
        ids.forEach { manager.notifyAppWidgetViewDataChanged(it, io.github.catadoxy.disketarr.R.id.widget_list) }
        kotlinx.coroutines.delay(1000)
        ids.forEach { ShowWidgetUpdater.update(applicationContext, it) }
        return Result.success()
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notify(message: String) {
        val manager = applicationContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        val channel = android.app.NotificationChannel(
            "disketarr_widget",
            "Widget actions",
            android.app.NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
        val notification = android.app.Notification.Builder(applicationContext, "disketarr_widget")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("Disketarr")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

/** A `season`/`episode` row is tracked against its parent `tv` endpoint. */
private fun widgetMediaType(mediaType: String): String = when (mediaType.lowercase()) {
    "season", "episode", "anime" -> "tv"
    else -> mediaType
}
