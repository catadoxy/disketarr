package io.github.catadoxy.floppy.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.catadoxy.floppy.data.repository.FloppyRepository
import io.github.catadoxy.floppy.viewmodel.asUpcomingItems
import java.time.LocalDate
import javax.inject.Inject

@HiltWorker
class UpcomingReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: FloppyRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = repository.settingsStore
        if (!settings.isConfigured) return Result.success()

        val today = LocalDate.now()
        val items = try {
            val service = repository.service()
            // Upcoming endpoint is month-scoped; fetch this month.
            service.upcomingCalendar(month = today.monthValue, year = today.year, limit = 60).asUpcomingItems()
        } catch (e: Exception) {
            return Result.retry()
        }

        val leadDays = (settings.reminderLeadMinutes + 1439) / 1440
        val cutoff = today.plusDays(leadDays.toLong())
        val enabled = settings.seriesReminders()
        if (enabled.isEmpty()) return Result.success()
        val soon = items.filter { item ->
            val air = item.nextEpisode?.airDate?.take(10) ?: return@filter false
            val date = runCatching { LocalDate.parse(air) }.getOrNull() ?: return@filter false
            if (date.isBefore(today) || date.isAfter(cutoff)) return@filter false
            if (item.mediaType.lowercase() !in setOf("tv", "anime", "season", "episode")) return@filter false
            val key = "${item.source}|${item.mediaType}|${item.mediaId}"
            key in enabled
        }
        if (soon.isEmpty()) return Result.success()

        showNotification(soon.size)
        return Result.success()
    }

    private fun showNotification(count: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "floppy_upcoming"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Upcoming episodes", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Floppy upcoming")
            .setContentText("$count episode(s) airing today or tomorrow")
            .setAutoCancel(true)
            .build()
        manager.notify(42, notification)
    }
}