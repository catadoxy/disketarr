package io.github.catadoxy.floppy.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.catadoxy.floppy.data.model.SearchResult
import io.github.catadoxy.floppy.data.network.BaseUrlNormalizer
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Holds the two things this app needs to talk to Floppy:
 *   - baseUrl: e.g. "https://floppy.example.com" or "http://192.168.1.10:8000"
 *   - apiToken: from Floppy's Settings -> Advanced -> API Token
 *
 * Stored in EncryptedSharedPreferences so the token isn't sitting in
 * plaintext prefs if the phone is ever compromised.
 */
class SettingsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "floppy_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var baseUrl: String?
        get() = prefs.getString(KEY_BASE_URL, null)
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiToken: String?
        get() = prefs.getString(KEY_API_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_API_TOKEN, value).apply()

    var selectedThemeId: String
        get() = prefs.getString(KEY_SELECTED_THEME, io.github.catadoxy.floppy.ui.theme.ThemeCatalog.DEFAULT_ID) ?: io.github.catadoxy.floppy.ui.theme.ThemeCatalog.DEFAULT_ID
        set(value) = prefs.edit().putString(KEY_SELECTED_THEME, value).apply()

    var hideSpecialEpisodes: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SPECIAL_EPISODES, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_SPECIAL_EPISODES, value).apply()

    var episodeRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_EPISODE_REMINDERS, false)
        set(value) = prefs.edit().putBoolean(KEY_EPISODE_REMINDERS, value).apply()

    var reminderLeadMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_LEAD_MINUTES, 1440)
        set(value) = prefs.edit().putInt(KEY_REMINDER_LEAD_MINUTES, value.coerceAtLeast(0)).apply()

    fun selectedCategories(): Set<String> {
        val raw = prefs.getStringSet(KEY_SELECTED_CATEGORIES, null)
        return raw?.toSet() ?: io.github.catadoxy.floppy.data.model.LibraryMediaTypes.ALL.toSet()
    }

    fun saveSelectedCategories(selection: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED_CATEGORIES, selection).apply()
    }

    fun seriesReminders(): Set<String> =
        prefs.getStringSet(KEY_SERIES_REMINDERS, emptySet()) ?: emptySet()

    fun toggleSeriesReminder(key: String, enabled: Boolean) {
        val updated = seriesReminders().toMutableSet()
        if (enabled) updated += key else updated -= key
        prefs.edit().putStringSet(KEY_SERIES_REMINDERS, updated).apply()
    }

    fun isSeriesReminderEnabled(key: String): Boolean = key in seriesReminders()

    val isConfigured: Boolean
        get() = !baseUrl.isNullOrBlank() && !apiToken.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun recentSearches(limit: Int = 8): List<String> {
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }.distinct().take(limit)
        }.getOrDefault(emptyList())
    }

    fun pushRecentSearch(query: String, limit: Int = 8) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val updated = (listOf(trimmed) + recentSearches(limit = limit))
            .distinct()
            .take(limit)
        val array = JSONArray()
        updated.forEach { array.put(it) }
        prefs.edit().putString(KEY_RECENT_SEARCHES, array.toString()).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    fun saveBrowseFilter(mediaType: String, json: String) {
        prefs.edit().putString(KEY_BROWSE_FILTER_PREFIX + mediaType, json).apply()
    }

    fun loadBrowseFilter(mediaType: String): String? =
        prefs.getString(KEY_BROWSE_FILTER_PREFIX + mediaType, null)

    data class SavedServer(
        val id: String,
        val name: String,
        val baseUrl: String,
        val apiToken: String,
    )

    fun savedServers(): List<SavedServer> {
        val raw = prefs.getString(KEY_SAVED_SERVERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").ifBlank { return@mapNotNull null }
                val name = item.optString("name").ifBlank { "Server" }
                val baseUrl = item.optString("baseUrl")
                val apiToken = item.optString("apiToken")
                if (baseUrl.isBlank()) return@mapNotNull null
                SavedServer(id, name, baseUrl, apiToken)
            }
        }.getOrDefault(emptyList())
    }

    fun saveServer(name: String, baseUrl: String, apiToken: String): SavedServer {
        val normalized = BaseUrlNormalizer.normalize(baseUrl)
        val updated = savedServers().toMutableList()
        val existingIndex = updated.indexOfFirst { it.baseUrl == normalized }
        val server = SavedServer(
            id = if (existingIndex >= 0) updated[existingIndex].id else UUID.randomUUID().toString(),
            name = name.ifBlank { "Server ${updated.size + 1}" },
            baseUrl = normalized,
            apiToken = apiToken.trim(),
        )
        if (existingIndex >= 0) updated[existingIndex] = server else updated += server
        val array = JSONArray()
        updated.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("baseUrl", s.baseUrl)
                    .put("apiToken", s.apiToken),
            )
        }
        prefs.edit().putString(KEY_SAVED_SERVERS, array.toString()).apply()
        return server
    }

    fun removeServer(id: String) {
        val updated = savedServers().filterNot { it.id == id }
        val array = JSONArray()
        updated.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("baseUrl", s.baseUrl)
                    .put("apiToken", s.apiToken),
            )
        }
        prefs.edit().putString(KEY_SAVED_SERVERS, array.toString()).apply()
    }

    fun getRecentMedia(limit: Int = 60): List<SearchResult> {
        val raw = prefs.getString(KEY_RECENT_MEDIA, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val items = mutableListOf<SearchResult>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val mediaId = item.optString("mediaId")
                val source = item.optString("source")
                val mediaType = item.optString("mediaType")
                if (mediaId.isBlank() || source.isBlank() || mediaType.isBlank()) continue
                items += SearchResult(
                    mediaId = mediaId,
                    source = source,
                    mediaType = mediaType,
                    title = item.optString("title").ifBlank { null },
                    image = item.optString("image").ifBlank { null },
                    lastWatchedAt = item.optString("lastWatchedAt").ifBlank { null },
                )
            }
            items.take(limit)
        }.getOrDefault(emptyList())
    }

    fun pushRecentMedia(item: SearchResult, maxItems: Int = 60) {
        val existing = getRecentMedia(limit = maxItems)
            .filterNot { it.mediaId == item.mediaId && it.source == item.source && it.mediaType == item.mediaType }
        val updated = listOf(item) + existing
        val array = JSONArray()
        updated.take(maxItems).forEach { media ->
            val payload = JSONObject()
                .put("mediaId", media.mediaId)
                .put("source", media.source)
                .put("mediaType", media.mediaType)
                .put("title", media.title)
                .put("image", media.image)
                .put("lastWatchedAt", media.lastWatchedAt)
            array.put(payload)
        }
        prefs.edit().putString(KEY_RECENT_MEDIA, array.toString()).apply()
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_RECENT_MEDIA = "recent_media"
        private const val KEY_SELECTED_THEME = "selected_theme"
        private const val KEY_HIDE_SPECIAL_EPISODES = "hide_special_episodes"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val KEY_BROWSE_FILTER_PREFIX = "browse_filter_"
        private const val KEY_EPISODE_REMINDERS = "episode_reminders"
        private const val KEY_REMINDER_LEAD_MINUTES = "reminder_lead_minutes"
        private const val KEY_SELECTED_CATEGORIES = "selected_categories"
        private const val KEY_SERIES_REMINDERS = "series_reminders"
        private const val KEY_SAVED_SERVERS = "saved_servers"
    }
}
