package io.github.catadoxy.disketarr.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cache_entries")
data class CacheEntry(
    @PrimaryKey val key: String,
    val baseUrl: String?,
    val payload: String,
    val createdAt: Long,
)