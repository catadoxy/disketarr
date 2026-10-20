package io.github.catadoxy.disketarr.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.catadoxy.disketarr.MainActivity
import io.github.catadoxy.disketarr.data.model.LibraryType

/**
 * Handles taps coming from the widget's collection.
 *
 * The rows share a single [android.app.PendingIntent] template; each row's
 * fill-in intent carries [EXTRA_ACTION] plus the media identity, and this
 * receiver dispatches on the action:
 *  - [ACTION_OPEN] opens the show in the app;
 *  - [ACTION_MARK_WATCHED] queues [MarkWatchedWorker] to mark the episode watched.
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Immediate feedback: the toast is suppressed on some devices, so vibrate.
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
        vibrator?.vibrate(
            android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE),
        )
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_OPEN -> openDetails(context, intent)
            ACTION_MARK_WATCHED -> {
                val appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)
                if (appWidgetId != -1) {
                    ShowWidgetUpdater.update(context, appWidgetId, "Marking watched…")
                }
                markWatched(context, intent)
            }
        }
    }

    private fun openDetails(context: Context, intent: Intent) {
        val source = intent.getStringExtra(EXTRA_SOURCE)?.takeIf { it.isNotBlank() } ?: return
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)?.takeIf { it.isNotBlank() } ?: return
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)?.takeIf { it.isNotBlank() } ?: return
        val library = intent.getStringExtra(EXTRA_LIBRARY)?.takeIf { LibraryType.isSupported(it) }

        val launch = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            val base = "disketarr://details/$source/$mediaType/$mediaId"
            data = Uri.parse(if (library == null) base else "$base?library=$library")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(launch) }
    }

    private fun markWatched(context: Context, intent: Intent) {
        val source = intent.getStringExtra(EXTRA_SOURCE)?.takeIf { it.isNotBlank() } ?: return
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)?.takeIf { it.isNotBlank() } ?: return
        val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)?.takeIf { it.isNotBlank() } ?: return
        val library = intent.getStringExtra(EXTRA_LIBRARY).orEmpty()
        val season = intent.getIntExtra(EXTRA_SEASON, -1)
        val episode = intent.getIntExtra(EXTRA_EPISODE, -1)
        if (season < 0 || episode < 0) return

        val request = OneTimeWorkRequestBuilder<MarkWatchedWorker>()
            .setInputData(
                workDataOf(
                    EXTRA_SOURCE to source,
                    EXTRA_MEDIA_TYPE to mediaType,
                    EXTRA_MEDIA_ID to mediaId,
                    EXTRA_LIBRARY to library,
                    EXTRA_SEASON to season,
                    EXTRA_EPISODE to episode,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        const val EXTRA_ACTION = "widget_action"
        const val ACTION_OPEN = "open"
        const val ACTION_MARK_WATCHED = "mark_watched"

        const val EXTRA_SOURCE = "source"
        const val EXTRA_MEDIA_TYPE = "media_type"
        const val EXTRA_MEDIA_ID = "media_id"
        /** The Floppy library the title lives in ("tv", "anime", ...). */
        const val EXTRA_LIBRARY = "library"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_APPWIDGET_ID = "appwidget_id"
    }
}
