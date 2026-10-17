package io.github.catadoxy.disketarr.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.catadoxy.disketarr.data.model.DiscoverRail
import io.github.catadoxy.disketarr.data.model.MediaDetail
import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.data.model.SeasonEpisode
import io.github.catadoxy.disketarr.data.model.SeasonSummary
import io.github.catadoxy.disketarr.data.model.StatisticsOverview

/**
 * In-memory cache so moving around the app does not re-hit the API.
 *
 * Why a count probe rather than Disketarr's change feed: `GET /api/v1/sync/changes/`
 * is a client-to-client sync log. It only records changes for clients that have
 * registered a connection, and making a watched-state change through the API
 * leaves it empty (`newest_sequence: null`), so it cannot answer "did something
 * change in the Disketarr web UI".
 *
 * Instead, staleness is answered by the cheapest request available - a one-row
 * media list, whose `pagination.total` moves when items are added or removed.
 * That is ~60x smaller than a full library fetch. It cannot see an edit that
 * keeps the count (a rating change, say); the Refresh action covers that.
 */
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCacheRepository @Inject constructor(
    private val diskCache: DiskCache?,
) {

    constructor() : this(null)

    private val moshi = Moshi.Builder()
        .add(io.github.catadoxy.disketarr.data.model.FlexStringAdapterFactory)
        .add(KotlinJsonAdapterFactory())
        .build()
    private val pageAdapter = moshi.adapter(Page::class.java)
    private val detailsAdapter = moshi.adapter(Details::class.java)
    private val browseAdapter = moshi.adapter(Browse::class.java)
    private val statsAdapter = moshi.adapter(Stats::class.java)

    /** A cached page is trusted with no check at all for this long. */
    val FRESH_MS = 5 * 60 * 1000L

    data class Page(
        val items: List<SearchResult>,
        val total: Int,
        val at: Long,
    )

    data class Details(
        val detail: MediaDetail,
        val seasons: List<SeasonSummary>,
        val selectedSeason: Int?,
        val episodes: List<SeasonEpisode>,
        val at: Long,
    )

    private var baseUrl: String? = null
    private var libraryPage: Page? = null
    private val homePages = mutableMapOf<String, Page>()
    private val detailPages = mutableMapOf<String, Details>()
    private var statisticsPage: Stats? = null

    data class Stats(val value: StatisticsOverview, val at: Long)

    @Synchronized
    fun isFresh(at: Long, now: Long = System.currentTimeMillis()): Boolean = now - at < FRESH_MS

    // ---- library ----------------------------------------------------------

    @Synchronized
    fun library(baseUrlNow: String): Page? {
        val mem = libraryPage?.takeIf { baseUrl == baseUrlNow && it.items.isNotEmpty() }
        if (mem != null) return mem
        val payload = diskCache?.get("library|$baseUrlNow") ?: return null
        val parsed = runCatching { pageAdapter.fromJson(payload) }.getOrNull() ?: return null
        if (parsed.items.isEmpty()) return null
        if (baseUrl != baseUrlNow) {
            homePages.clear()
            detailPages.clear()
            baseUrl = baseUrlNow
        }
        libraryPage = parsed
        return parsed
    }

    @Synchronized
    fun putLibrary(
        baseUrlNow: String,
        items: List<SearchResult>,
        total: Int,
        now: Long = System.currentTimeMillis(),
    ) {
        if (baseUrl != baseUrlNow) {
            // A different server: nothing cached here is usable.
            homePages.clear()
            detailPages.clear()
            baseUrl = baseUrlNow
            diskCache?.clear()
        }
        val page = Page(items, total, now)
        libraryPage = page
        diskCache?.put("library|$baseUrlNow", baseUrlNow, pageAdapter.toJson(page))
    }

    /** Marks the cached library as just-checked, without refetching it. */
    @Synchronized
    fun touchLibrary(now: Long = System.currentTimeMillis()) {
        libraryPage = libraryPage?.copy(at = now)
    }

    // ---- home (per filter) ------------------------------------------------

    @Synchronized
    fun home(key: String): Page? {
        val mem = homePages[key]?.takeIf { it.items.isNotEmpty() }
        if (mem != null) return mem
        val scoped = "home|${baseUrl ?: "default"}|$key"
        val payload = diskCache?.get(scoped) ?: return null
        val parsed = runCatching { pageAdapter.fromJson(payload) }.getOrNull() ?: return null
        if (parsed.items.isEmpty()) return null
        homePages[key] = parsed
        return parsed
    }

    @Synchronized
    fun putHome(key: String, items: List<SearchResult>, total: Int, now: Long = System.currentTimeMillis()) {
        val page = Page(items, total, now)
        homePages[key] = page
        diskCache?.put("home|${baseUrl ?: "default"}|$key", baseUrl, pageAdapter.toJson(page))
    }

    @Synchronized
    fun touchHome(key: String, now: Long = System.currentTimeMillis()) {
        homePages[key]?.let { homePages[key] = it.copy(at = now) }
    }

    // ---- media details ----------------------------------------------------

    @Synchronized
    fun detailKey(mediaType: String, source: String, mediaId: String): String =
        "$mediaType|$source|$mediaId"

    @Synchronized
    fun details(key: String): Details? {
        val mem = detailPages[key]
        if (mem != null) return mem
        val scoped = "details|${baseUrl ?: "default"}|$key"
        val payload = diskCache?.get(scoped) ?: return null
        val parsed = runCatching { detailsAdapter.fromJson(payload) }.getOrNull() ?: return null
        detailPages[key] = parsed
        return parsed
    }

    @Synchronized
    fun putDetails(key: String, details: Details) {
        detailPages[key] = details
        diskCache?.put("details|${baseUrl ?: "default"}|$key", baseUrl, detailsAdapter.toJson(details))
    }

    @Synchronized
    fun putEpisodes(key: String, episodes: List<SeasonEpisode>, selectedSeason: Int?) {
        detailPages[key]?.let {
            detailPages[key] = it.copy(episodes = episodes, selectedSeason = selectedSeason)
        }
    }

    /** After a write, that item's page and the lists that show it are stale. */
    @Synchronized
    fun invalidateAfterWrite(detailKey: String? = null) {
        detailKey?.let { detailPages.remove(it) }
        libraryPage = null
        homePages.clear()
        browsePages.clear()
        statisticsPage = null
        diskCache?.clear()
    }

    // ---- statistics -------------------------------------------------------

    @Synchronized
    fun statistics(): Stats? {
        val mem = statisticsPage
        if (mem != null) return mem
        val payload = diskCache?.get("stats|${baseUrl ?: "default"}") ?: return null
        val parsed = runCatching { statsAdapter.fromJson(payload) }.getOrNull() ?: return null
        statisticsPage = parsed
        return parsed
    }

    @Synchronized
    fun putStatistics(value: StatisticsOverview, now: Long = System.currentTimeMillis()) {
        val stats = Stats(value, now)
        statisticsPage = stats
        diskCache?.put("stats|${baseUrl ?: "default"}", baseUrl, statsAdapter.toJson(stats))
    }

    // ---- browse pages (Shows / Movies tabs) -------------------------------

    data class Browse(
        val items: List<SearchResult>,
        val rails: List<DiscoverRail>,
        val at: Long,
    )

    private val browsePages = mutableMapOf<String, Browse>()

    @Synchronized
    fun browse(key: String): Browse? {
        val mem = browsePages[key]?.takeIf { it.items.isNotEmpty() || it.rails.isNotEmpty() }
        if (mem != null) return mem
        val scoped = "browse|${baseUrl ?: "default"}|$key"
        val payload = diskCache?.get(scoped) ?: return null
        val parsed = runCatching { browseAdapter.fromJson(payload) }.getOrNull() ?: return null
        if (parsed.items.isEmpty() && parsed.rails.isEmpty()) return null
        browsePages[key] = parsed
        return parsed
    }

    @Synchronized
    fun putBrowse(
        key: String,
        items: List<SearchResult>,
        rails: List<DiscoverRail>,
        now: Long = System.currentTimeMillis(),
    ) {
        val browse = Browse(items, rails, now)
        browsePages[key] = browse
        diskCache?.put("browse|${baseUrl ?: "default"}|$key", baseUrl, browseAdapter.toJson(browse))
    }

    @Synchronized
    fun clear() {
        baseUrl = null
        libraryPage = null
        homePages.clear()
        browsePages.clear()
        detailPages.clear()
        statisticsPage = null
        diskCache?.clear()
    }
}
