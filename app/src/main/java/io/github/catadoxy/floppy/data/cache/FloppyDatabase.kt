package io.github.catadoxy.floppy.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CacheEntry::class], version = 1, exportSchema = false)
abstract class FloppyDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}