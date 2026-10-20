package io.github.catadoxy.disketarr.data

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.catadoxy.disketarr.data.cache.CacheDao
import io.github.catadoxy.disketarr.data.cache.CacheEntry
import io.github.catadoxy.disketarr.data.cache.DisketarrDatabase
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed key/value store used by [AppCacheRepository] so cached pages
 * survive process death.
 */
@Singleton
class DiskCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dao: CacheDao = Room.databaseBuilder(
        context.applicationContext,
        DisketarrDatabase::class.java,
        "Disketarr_cache.db",
    ).fallbackToDestructiveMigration().build().cacheDao()

    fun get(key: String): String? = runCatching {
        runBlocking { dao.get(key)?.payload }
    }.onFailure { android.util.Log.w("DiskCache", "get failed for $key", it) }.getOrNull()

    fun put(key: String, baseUrl: String?, payload: String) {
        runCatching {
            runBlocking {
                dao.put(CacheEntry(key = key, baseUrl = baseUrl, payload = payload, createdAt = System.currentTimeMillis()))
            }
        }.onFailure { android.util.Log.w("DiskCache", "put failed for $key", it) }
    }

    fun remove(key: String) {
        runCatching { runBlocking { dao.remove(key) } }
            .onFailure { android.util.Log.w("DiskCache", "remove failed for $key", it) }
    }

    /** Drops every entry whose key starts with [prefix]. */
    fun removeByPrefix(prefix: String) {
        runCatching { runBlocking { dao.removeByPrefix(prefix) } }
            .onFailure { android.util.Log.w("DiskCache", "removeByPrefix failed for $prefix", it) }
    }

    fun clear() {
        runCatching { runBlocking { dao.clear() } }
            .onFailure { android.util.Log.w("DiskCache", "clear failed", it) }
    }
}