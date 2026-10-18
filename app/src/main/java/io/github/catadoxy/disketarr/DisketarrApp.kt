package io.github.catadoxy.disketarr

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.catadoxy.disketarr.data.DiskCache
import io.github.catadoxy.disketarr.widget.ShowWidgetUpdateWorker
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class DisketarrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<ShowWidgetUpdateWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "disketarr_widget_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}