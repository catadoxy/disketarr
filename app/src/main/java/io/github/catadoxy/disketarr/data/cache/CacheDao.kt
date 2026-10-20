package io.github.catadoxy.disketarr.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CacheEntry)

    @Query("SELECT * FROM cache_entries WHERE key = :key")
    suspend fun get(key: String): CacheEntry?

    @Query("DELETE FROM cache_entries WHERE key = :key")
    suspend fun remove(key: String)

    /** Drops every entry whose key starts with the given prefix. */
    @Query("DELETE FROM cache_entries WHERE key LIKE :prefix || '%'")
    suspend fun removeByPrefix(prefix: String)

    @Query("DELETE FROM cache_entries")
    suspend fun clear()
}