package io.github.catadoxy.floppy

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

import io.github.catadoxy.floppy.data.DiskCache

@HiltAndroidApp
class FloppyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // DiskCache is initialised lazily by AppCacheRepository on first use.
    }
}