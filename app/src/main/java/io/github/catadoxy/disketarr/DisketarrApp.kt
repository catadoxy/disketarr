package io.github.catadoxy.disketarr

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import io.github.catadoxy.disketarr.data.DiskCache

@HiltAndroidApp
class DisketarrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // DiskCache is initialised lazily by AppCacheRepository on first use.
    }
}