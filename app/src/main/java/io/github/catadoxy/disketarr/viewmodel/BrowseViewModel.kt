package io.github.catadoxy.disketarr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.catadoxy.disketarr.data.AppCacheRepository
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.repository.DisketarrRepository
import io.github.catadoxy.disketarr.data.model.DiscoverRail
import io.github.catadoxy.disketarr.data.model.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catadoxy.disketarr.data.network.DisketarrApiService
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.LocalDate

/** A tab on the Shows or Movies screen. */
enum class BrowseTab(val label: String) {
    DISCOVER("Discover"),
    IN_PROGRESS("In progress"),
    HISTORY("History"),
    UPCOMING("Upcoming"),
    WATCHED("Watched"),
    PLANNED("Planned"),
}

/** Shows: what you are watching, what is next, and what you have watched. */
val SHOW_TABS = listOf(
    BrowseTab.DISCOVER,
    BrowseTab.IN_PROGRESS,
    BrowseTab.HISTORY,
    BrowseTab.UPCOMING,
    BrowseTab.WATCHED,
)

/** Movies: discovery, watch history, planned and everything already watched. */
val MOVIE_TABS = listOf(
    BrowseTab.DISCOVER,
    BrowseTab.PLANNED,
    BrowseTab.HISTORY,
    BrowseTab.WATCHED,
)

/**
 * Tabs backed by the filtered media list. Discover is curated and History reads
 * the history endpoint, neither of which accepts the filter parameters, so the
 * filter control is only offered where it actually does something.
 */
internal val FILTERABLE_TABS = setOf(BrowseTab.IN_PROGRESS, BrowseTab.WATCHED, BrowseTab.PLANNED)

/**
 * A finished show is only "watched" when nothing is left, aired or announced.
 * Disketarr keeps [SearchResult.totalEpisodesLeft] for the announced remainder.
 */
internal fun SearchResult.hasNothingLeft(): Boolean = (totalEpisodesLeft ?: 0) <= 0

/**
 * Server-side filters, mapped to the query parameters the media list accepts.
 * Filtering on the server matters here because a page of media rows is large -
 * filtering a downloaded page would hide anything further down the library.
 *
 * Deliberately absent: the tracking status (each tab pins one) and an "any
 * rating" style filter, since `rating_min`/`rating_max` match your own scores and
 * `rating` only accepts `not_rated`.
 */
data class BrowseFilter(
    val rating: String? = null,
    val ratingMin: Double? = null,
    val genre: String? = null,
    val year: String? = null,
    val progress: String? = null,
    val collection: String? = null,
    val sort: String? = null,
    val direction: String? = null,
    val query: String = "",
) {
    val isActive: Boolean
        get() = rating != null || ratingMin != null || genre != null ||
            year != null || progress != null || collection != null ||
            sort != null || query.isNotBlank()

    fun cacheKey(tab: BrowseTab): String = "$tab|$rating|$ratingMin|$genre|$year|$progress|$collection|$sort|$direction|$query"
}

data class BrowseUiState(
    val tab: BrowseTab,
    val items: List<SearchResult> = emptyList(),
    val rails: List<DiscoverRail> = emptyList(),
    val filter: BrowseFilter = BrowseFilter(),
    val loading: Boolean = false,
    val error: String? = null,
    val canLoadMore: Boolean = false,
)

/**
 * Backs both the Shows and Movies screens. The only difference is the media type
 * and the tab set, so the fetch logic lives here once.
 */
open class BrowseViewModel(
    private val settingsStore: SettingsStore,
    /** Centralised network access. */
    protected val repository: DisketarrRepository = DisketarrRepository(settingsStore),
    /** Offline cache. */
    protected val appCache: AppCacheRepository = AppCacheRepository(),
    /** "tv" or "movie" - also decides whether TV-only filters are offered. */
    val mediaType: String,
    /** The tabs this screen shows. */
    val tabs: List<BrowseTab>,
) : ViewModel() {

    private val pageSize = 60

    private val _uiState = MutableStateFlow(BrowseUiState(tab = tabs.first()))
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    /** History rows are episodic for shows, so keep the show's parts together. */
    private val historyTypes: Set<String> =
        if (mediaType == "tv") setOf("tv", "season", "episode") else setOf(mediaType)

    init {
        _uiState.value = BrowseUiState(tab = tabs.first(), filter = loadSavedFilter())
        load(tabs.first(), force = false)
    }

    fun selectTab(tab: BrowseTab) {
        if (tab != _uiState.value.tab) load(tab, force = false)
    }

    fun applyFilter(filter: BrowseFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        settingsStore.saveBrowseFilter(mediaType, filterToJson(filter))
        load(_uiState.value.tab, force = true)
    }

    fun forceRefresh() = load(_uiState.value.tab, force = true)

    private fun loadSavedFilter(): BrowseFilter {
        val raw = settingsStore.loadBrowseFilter(mediaType) ?: return BrowseFilter()
        return runCatching {
            val o = JSONObject(raw)
            BrowseFilter(
                rating = o.optString("rating").ifBlank { null },
                ratingMin = if (o.has("ratingMin")) o.optDouble("ratingMin") else null,
                genre = o.optString("genre").ifBlank { null },
                year = o.optString("year").ifBlank { null },
                progress = o.optString("progress").ifBlank { null },
                collection = o.optString("collection").ifBlank { null },
                sort = o.optString("sort").ifBlank { null },
                direction = o.optString("direction").ifBlank { null },
                query = o.optString("query", ""),
            )
        }.getOrDefault(BrowseFilter())
    }

    private fun filterToJson(filter: BrowseFilter): String {
        val o = JSONObject()
        filter.rating?.let { o.put("rating", it) }
        filter.ratingMin?.let { o.put("ratingMin", it) }
        filter.genre?.let { o.put("genre", it) }
        filter.year?.let { o.put("year", it) }
        filter.progress?.let { o.put("progress", it) }
        filter.collection?.let { o.put("collection", it) }
        filter.sort?.let { o.put("sort", it) }
        filter.direction?.let { o.put("direction", it) }
        o.put("query", filter.query)
        return o.toString()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || !state.canLoadMore) return
        load(state.tab, force = true, offset = state.items.size)
    }

    private fun load(tab: BrowseTab, force: Boolean, offset: Int = 0, ) {
        _uiState.value = _uiState.value.copy(tab = tab, loading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { fetch(tab, force, offset) }
            if (_uiState.value.tab != tab) return@launch
            result.fold(
                onSuccess = { page ->
                    val items = if (offset == 0) page.items else _uiState.value.items + page.items
                    _uiState.value = BrowseUiState(
                        tab = tab,
                        items = items,
                        rails = page.rails,
                        filter = _uiState.value.filter,
                        loading = false,
                        canLoadMore = page.hasMore,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = error.message ?: "Could not load",
                    )
                },
            )
        }
    }

    private data class Page(
        val items: List<SearchResult> = emptyList(),
        val rails: List<DiscoverRail> = emptyList(),
        val hasMore: Boolean = false,
    )

    private suspend fun fetch(tab: BrowseTab, force: Boolean, offset: Int): Result<Page> = runCatching {
        val service = repository.service()
        val filter = _uiState.value.filter
        val cacheKey = "${mediaType}|${filter.cacheKey(tab)}"

        if (!force && offset == 0) {
            appCache.browse(cacheKey)?.let { cached ->
                if (appCache.isFresh(cached.at)) {
                    return@runCatching Page(cached.items, cached.rails, false)
                }
            }
        }

        val page = when (tab) {
            BrowseTab.DISCOVER -> {
                val rails = withTimeoutOrNull(20000) {
                    service.discover(mediaType = mediaType).asDiscoverRails()
                }.orEmpty()
                Page(rails = rails)
            }

            BrowseTab.UPCOMING -> {
                // The calendar returns one whole month, including days that have
                // already aired, so keep today onwards and pull next month in as
                // well - otherwise the tab empties out at the end of every month.
                val today = LocalDate.now()
                val items = listOf(today, today.plusMonths(1)).flatMap { month ->
                    withTimeoutOrNull(20000) {
                        service.upcomingCalendar(
                            month = month.monthValue,
                            year = month.year,
                            limit = pageSize,
                        ).asUpcomingItems()
                    }.orEmpty()
                }
                // The calendar is not media-type specific; keep shows only.
                Page(
                    items = items
                        .filter { it.mediaType == "episode" || it.mediaType == "tv" }
                        .filter { airsOnOrAfter(it.nextEpisode?.airDate, today) }
                        .distinctBy { "${it.source}|${it.mediaId}|${it.nextEpisode?.seasonNumber}|${it.nextEpisode?.episodeNumber}" }
                        .sortedBy { it.nextEpisode?.airDate.orEmpty() },
                )
            }

            BrowseTab.HISTORY -> {
                if (mediaType == "movie") {
                    // Movie history is sparse in the mixed history feed, so use the
                    // tracked-media list (completed, newest first) instead.
                    val payload = withTimeoutOrNull(25000) {
                        service.listTrackedMediaRaw(
                            mediaType = mediaType,
                            statuses = listOf("3"),
                            sort = "date_added",
                            direction = "desc",
                            limit = pageSize,
                            offset = offset,
                        )
                    }
                    val fetched = payload.extractRowsSafely().toSearchResults()
                    val total = payload.asPaginationTotalOrNull() ?: fetched.size
                    Page(items = fetched, hasMore = fetched.isNotEmpty() && offset + fetched.size < total)
                } else {
                    val items = withTimeoutOrNull(25000) {
                        service.listHistoryRaw(limit = pageSize, offset = offset, flat = "1")
                            .asHistoryItems(historyTypes)
                    }.orEmpty()
                    Page(items = items, hasMore = items.size >= pageSize)
                }
            }

            BrowseTab.IN_PROGRESS, BrowseTab.WATCHED, BrowseTab.PLANNED -> {
                val statuses = when (tab) {
                    BrowseTab.WATCHED -> listOf("3")
                    BrowseTab.PLANNED -> listOf("0")
                    else -> listOf("1")
                }
                val payload = withTimeoutOrNull(25000) {
                    service.listTrackedMediaRaw(
                        mediaType = mediaType,
                        // The tab fixes the status, so the filter never sets one.
                        statuses = statuses,
                        rating = filter.rating,
                        ratingMin = filter.ratingMin,
                        genre = filter.genre,
                        year = filter.year,
                        // Documented as meaningful for TV/anime only; sending it
                        // for movies silently returns everything.
                        progress = filter.progress?.takeIf { mediaType == "tv" },
                        collection = filter.collection,
                        search = filter.query.takeIf { it.isNotBlank() },
                        sort = filter.sort ?: defaultSort(tab, mediaType),
                        direction = filter.direction ?: if (filter.sort == null && defaultSort(tab, mediaType) != null) "desc" else null,
                        limit = pageSize,
                        offset = offset,
                    )
                }
                val fetched = payload.extractRowsSafely().toSearchResults()
                // Completed shows that still have announced episodes belong with
                // what is coming up, not in the watched list.
                val items =
                    if (tab == BrowseTab.WATCHED) fetched.filter { it.hasNothingLeft() } else fetched
                val total = payload.asPaginationTotalOrNull() ?: fetched.size
                Page(items = items, hasMore = fetched.isNotEmpty() && offset + fetched.size < total)
            }
        }

        if (offset == 0) {
            appCache.putBrowse(cacheKey, page.items, page.rails)
        }
        page
    }

    private fun List<Map<String, Any?>>.toSearchResults(): List<SearchResult> {
        return mapNotNull { tracked ->
            val item = tracked["item"].toStringKeyMapOrNull()

            val mediaId = (
                item?.get("media_id") ?: item?.get("id")
                    ?: tracked["media_id"] ?: tracked["id"] ?: tracked["item_id"]
                )?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            val source = (
                item?.get("source") ?: tracked["source"]
                )?.toString()?.takeIf { it.isNotBlank() } ?: "unknown"

            // Label rows with media_type ("tv"). library_media_type can be a
            // tracking granularity - The Vermilion Mask is recorded as "season" -
            // which printed "Season" on a Shows row. Routing still maps
            // season/episode back to the tv endpoint via detailRouteMediaType().
            val rawMediaType = (
                item?.get("media_type") ?: item?.get("library_media_type")
                    ?: tracked["media_type"] ?: tracked["library_media_type"]
                )?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            SearchResult(
                mediaId = mediaId,
                source = source,
                mediaType = rawMediaType.lowercase(),
                title = (item?.get("title") ?: tracked["title"])?.toString()?.takeIf { it.isNotBlank() },
                image = (item?.get("image") ?: tracked["image"])?.toString()?.takeIf { it.isNotBlank() },
                lastWatchedAt = (tracked["progressed_at"] ?: item?.get("progressed_at"))?.toString(),
                status = (tracked["status"] ?: item?.get("status")).asIntOrNull(),
                progress = (tracked["progress"] ?: item?.get("progress")).asDoubleOrNull(),
                score = (tracked["score"] ?: item?.get("score")).asDoubleOrNull(),
                nextEpisode = (tracked["next_episode"] ?: item?.get("next_episode")).asNextEpisodeOrNull(),
                episodesLeft = (tracked["episodes_left"] ?: item?.get("episodes_left")).asIntOrNull(),
                totalEpisodesLeft = (tracked["total_episodes_left"] ?: item?.get("total_episodes_left")).asIntOrNull(),
            )
        }.distinctBy { "${it.source}|${it.mediaType}|${it.mediaId}" }
    }
}

@HiltViewModel
class ShowsViewModel @Inject constructor(
    settingsStore: SettingsStore,
    repository: DisketarrRepository = DisketarrRepository(settingsStore),
    appCache: AppCacheRepository = AppCacheRepository(),
) : BrowseViewModel(settingsStore, repository, appCache, mediaType = "tv", tabs = SHOW_TABS)

/** Default sort order for tabs that benefit from a sensible first view. */
private fun defaultSort(tab: BrowseTab, mediaType: String): String? =
    if (tab == BrowseTab.WATCHED && mediaType == "movie") "date_added" else null

@HiltViewModel
class MoviesViewModel @Inject constructor(
    settingsStore: SettingsStore,
    repository: DisketarrRepository = DisketarrRepository(settingsStore),
    appCache: AppCacheRepository = AppCacheRepository(),
) : BrowseViewModel(settingsStore, repository, appCache, mediaType = "movie", tabs = MOVIE_TABS)

