package io.github.catadoxy.disketarr.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CacheEntry::class], version = 1, exportSchema = false)
abstract class DisketarrDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}