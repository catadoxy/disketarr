package io.github.catadoxy.disketarr.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import io.github.catadoxy.disketarr.R
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.model.FlexStringAdapterFactory
import io.github.catadoxy.disketarr.data.model.LibraryType
import io.github.catadoxy.disketarr.data.model.MediaStatus
import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.data.network.ApiClient
import io.github.catadoxy.disketarr.data.network.DisketarrApiService
import io.github.catadoxy.disketarr.viewmodel.AnimeLibraryMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.catadoxy.disketarr.viewmodel.asHistoryItems
import io.github.catadoxy.disketarr.viewmodel.asSeasonEpisodes
import io.github.catadoxy.disketarr.viewmodel.asSeasonSummaries
import io.github.catadoxy.disketarr.viewmodel.asUpcomingItems
import io.github.catadoxy.disketarr.viewmodel.extractRowsSafely
import io.github.catadoxy.disketarr.viewmodel.toSearchResults
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

class ShowWidgetFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private class Row(val item: SearchResult, val poster: Bitmap?)

    private val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    /**
     * The rows on screen.
     *
     * Replaced in one go rather than edited in place: the launcher reads them
     * from another thread while a refresh is running, and a half-cleared list
     * would come back as "no shows".
     */
    @Volatile
    private var rows: List<Row> = emptyList()

    override fun onCreate() = Unit

    /**
     * Fetches the rows and downloads their posters. `RemoteViewsFactory` has no
     * way to signal "data ready" later, so this blocks (as it did before); poster
     * downloads are memory-cached and time-limited to keep it short.
     *
     * A refresh that cannot reach Floppy keeps the last list that did load, so
     * the widget never blanks out because of one failed update.
     */
    override fun onDataSetChanged() {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val type = prefs.getString("type_$appWidgetId", "upcoming") ?: "upcoming"
        val sort = prefs.getString("sort_$appWidgetId", "latest") ?: "latest"
        val filter = prefs.getString("filter_$appWidgetId", "active") ?: "active"
        val source = prefs.getString("source_$appWidgetId", "both") ?: "both"
        val direction = prefs.getString("direction_$appWidgetId", "desc") ?: "desc"
        val posterSize = prefs.getString("postersize_$appWidgetId", "medium") ?: "medium"

        val items = runCatching {
            val settings = SettingsStore(context)
            if (!settings.isConfigured) {
                rows = emptyList()
                return
            }
            loadItems(settings, type, sort, filter, source, direction)
        }.getOrNull()

        if (items != null) {
            rows = toRows(items)
            if (items.isNotEmpty()) saveCache(prefs, type, items)
        } else {
            // The server was unreachable (or the settings could not be opened):
            // show the last list that loaded instead of nothing.
            val cached = cachedItems(prefs, type)
            android.util.Log.w("ShowWidget", "Floppy unreachable; showing ${cached.size} cached rows")
            rows = toRows(sortItems(cached, sort, direction))
        }
    }

    private fun toRows(items: List<SearchResult>): List<Row> = runBlocking {
        // One request per poster, in parallel: done one after another a slow
        // connection can outlast the launcher's patience and blank the widget.
        items.map { item ->
            async(Dispatchers.IO) { Row(item, WidgetImages.load(item.image)) }
        }.awaitAll()
    }

    private fun loadItems(
        settings: SettingsStore,
        type: String,
        sort: String,
        filter: String,
        source: String,
        direction: String,
    ): List<SearchResult> {
        val today = LocalDate.now()
        val fetched: List<SearchResult> = runBlocking {
            val service = ApiClient.getService(settings)
            when (type) {
                    // The flat list costs the server ~10s to aggregate (history is
                    // thousands of entries); the widget's update window aborts that
                    // request. Day buckets answer in well under a second.
                    "recent" -> service.listHistory(limit = 30)
                        .asHistoryItems(setOf("tv", "season", "episode"))
                    "shows" -> {
                        val statuses = when (filter) {
                            "inprogress" -> listOf("1")
                            "planned" -> listOf("0")
                            "all" -> null
                            else -> listOf("0", "1")
                        }
                        // Floppy decides whether the anime have a library of
                        // their own. When they are filed in the TV library
                        // there is no Anime list to read, so only the TV one is
                        // used - the same rule the app's tabs follow.
                        val mode = AnimeLibraryMode.read(service) ?: settings.animeLibraryMode
                        val libraries = when {
                            !AnimeLibraryMode.hasAnimeLibrary(mode) -> listOf(LibraryType.TV)
                            source == "tv" -> listOf(LibraryType.TV)
                            source == "anime" -> listOf(LibraryType.ANIME)
                            else -> listOf(LibraryType.TV, LibraryType.ANIME)
                        }
                        libraries
                            .flatMap { library ->
                                service.listTrackedMediaRaw(
                                    mediaType = library,
                                    statuses = statuses,
                                    limit = 60,
                                ).extractRowsSafely().toSearchResults()
                            }
                            // The same title can be filed in more than one
                            // library at once.
                            .distinctBy { "${it.source}|${it.mediaId}" }
                            // Caught up, with only unreleased episodes left:
                            // there is nothing to watch yet.
                            .filterNot { it.isCaughtUpWithUpcoming() }
                    }
                    else -> {
                        // The calendar answers one month at a time, in air-date
                        // order, and `limit` counts rows from the start of the
                        // month - so both the current and the next month are
                        // read, with a limit that covers a whole month, or the
                        // week at the end of the month would be cut off.
                        val months = listOf(today, today.plusMonths(1))
                        months.flatMap { month ->
                            service.upcomingCalendar(
                                month = month.monthValue,
                                year = month.year,
                                limit = 200,
                            ).asUpcomingItems()
                        }.filter { item ->
                            val air = item.nextEpisode?.airDate?.take(10) ?: return@filter false
                            val date = runCatching { LocalDate.parse(air) }.getOrNull() ?: return@filter false
                            !date.isBefore(today) && !date.isAfter(today.plusDays(7))
                        }
                    }
                }
        }

        // Shows need the season data before sorting: the list endpoint returns
        // no next_episode (and therefore no air date) for library shows.
        val prepared = if (type == "shows") enrichShows(settings, fetched) else fetched

        return sortItems(prepared, sort, direction).take(MAX_ROWS)
    }

    private fun sortItems(
        items: List<SearchResult>,
        sort: String,
        direction: String,
    ): List<SearchResult> {
        val order = when (sort) {
            "title" -> compareBy<SearchResult> { it.title.orEmpty().lowercase() }
            // Shows with a next episode come first; fully watched shows have no
            // episode date and would otherwise outrank in-progress shows with
            // their (older) last-watched timestamps.
            "oldest" -> compareBy<SearchResult> { it.nextEpisode == null }
                .thenBy { it.episodeOrWatchDate() }
            "lastwatched" -> compareByDescending<SearchResult> { it.lastWatchedAt.orEmpty() }
            "remaining" -> compareByDescending<SearchResult> { it.episodesLeft ?: 0 }
            else -> compareByDescending<SearchResult> { it.nextEpisode != null }
                .thenByDescending { it.episodeOrWatchDate() }
        }
        // "Descending" keeps each sort's usual order (newest first, most
        // episodes left first, A-Z for titles); "Ascending" reverses it.
        return items.sortedWith(if (direction == "asc") order.reversed() else order)
    }

    /**
     * The tracked-media list omits `next_episode` for library shows; only the
     * season rows carry it, and they leave `air_date` empty for already-aired
     * episodes. This fills both in (one seasons call plus one episodes call per
     * show, run concurrently) so date sorts and the row's episode date are real.
     */
    private fun enrichShows(settings: SettingsStore, items: List<SearchResult>): List<SearchResult> =
        runCatching {
            runBlocking {
                val service = ApiClient.getService(settings)
                coroutineScope {
                    items.map { item ->
                        async(Dispatchers.IO) { enrichShow(service, item) }
                    }.awaitAll()
                }
            }
        }.getOrDefault(items)

    private suspend fun enrichShow(service: DisketarrApiService, item: SearchResult): SearchResult {
        val known = item.nextEpisode
        // A show's seasons live in their own library - the TV Seasons library
        // for a TV show, the Anime library for an anime - and asking for the
        // wrong one hides the watched state.
        val seasonLibrary = LibraryType.seasonLibrary(item.libraryMediaType)
        val episodeLibrary = LibraryType.episodeLibrary(item.libraryMediaType)
        val next = if (known?.seasonNumber != null && known.episodeNumber != null) {
            known
        } else {
            runCatching {
                service.listSeasonsRaw(item.mediaType, item.source, item.mediaId, seasonLibrary)
                    .asSeasonSummaries()
                    .mapNotNull { it.nextEpisode }
                    .firstOrNull()
            }.getOrNull() ?: return item
        }

        val season = next.seasonNumber ?: return item.copy(nextEpisode = next)
        val number = next.episodeNumber ?: return item.copy(nextEpisode = next)
        if (next.airDate != null) return item.copy(nextEpisode = next)

        val airDate = runCatching {
            service.listSeasonEpisodesRaw(item.mediaType, item.source, item.mediaId, season, episodeLibrary)
                .asSeasonEpisodes(season)
                .firstOrNull { it.episodeNumber == number }
                ?.airDate
        }.getOrNull()

        return item.copy(nextEpisode = if (airDate == null) next else next.copy(airDate = airDate))
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        // The row can be gone if a refresh replaced the list between getCount
        // and this call; show an empty row rather than crash.
        val row = rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_list_item_small)
        val item = row.item
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val type = prefs.getString("type_$appWidgetId", "upcoming") ?: "upcoming"
        val largeFont = prefs.getBoolean("largefont_$appWidgetId", false)
        val posterSize = prefs.getString("postersize_$appWidgetId", "medium") ?: "medium"
        val isDark = isWidgetDark(context, appWidgetId)
        val bgOpacity = prefs.getInt("bg_$appWidgetId", 100)

        val layoutId = when (posterSize) {
            "small" -> R.layout.widget_list_item_small
            "medium" -> R.layout.widget_list_item_medium
            else -> R.layout.widget_list_item
        }
        val views = RemoteViews(context.packageName, layoutId)
        val rowBg = if (bgOpacity == 0) 0x00000000 else if (isDark) DARK_ROW else LIGHT_ROW
        views.setInt(R.id.item_root, "setBackgroundColor", rowBg)

        val (targetW, targetH) = when (posterSize) {
            "small" -> 32 to 45
            "large" -> 58 to 80
            else -> 46 to 64
        }
        if (row.poster != null) {
            val scaled = android.graphics.Bitmap.createScaledBitmap(row.poster, targetW, targetH, true)
            views.setImageViewBitmap(R.id.item_poster, scaled)
        } else {
            views.setImageViewResource(R.id.item_poster, android.R.drawable.ic_menu_gallery)
        }

        val primary = if (isDark) DARK_PRIMARY else LIGHT_PRIMARY
        val secondary = if (isDark) DARK_SECONDARY else LIGHT_SECONDARY

        // History rows carry the episode name in `title`; lead with the show.
        views.setTextViewText(R.id.item_title, item.seriesTitle ?: item.title ?: "Untitled")
        views.setFloat(R.id.item_title, "setTextSize", if (largeFont) 16f else 14f)
        views.setTextColor(R.id.item_title, primary)

        val moment = parseMoment(item.nextEpisode?.airDate ?: item.lastWatchedAt)
        // Library episodes report a midnight placeholder rather than a real air
        // time, so shows only show the channel; upcoming keeps its air time.
        val meta = listOfNotNull(
            item.studio?.takeIf { it.isNotBlank() },
            moment?.takeIf { type != "shows" }?.let { TIME_FORMAT.format(it) },
        ).joinToString(" · ")
        views.setTextViewText(R.id.item_meta, meta)
        views.setFloat(R.id.item_meta, "setTextSize", if (largeFont) 13f else 12f)
        views.setTextColor(R.id.item_meta, secondary)

        views.setTextViewText(R.id.item_episode, episodeLabel(item).orEmpty())
        views.setFloat(R.id.item_episode, "setTextSize", if (largeFont) 14f else 13f)
        views.setTextColor(R.id.item_episode, primary)

        views.setTextViewText(R.id.item_date, moment?.let { formatDate(it) }.orEmpty())
        views.setFloat(R.id.item_date, "setTextSize", if (largeFont) 13f else 12f)
        views.setTextColor(R.id.item_date, secondary)

        views.setInt(R.id.item_check, "setColorFilter", primary)
        val markable = item.nextEpisode?.seasonNumber != null && item.nextEpisode?.episodeNumber != null
        views.setViewVisibility(R.id.item_check, if (markable) View.VISIBLE else View.INVISIBLE)

        // Both the row content and the checkmark carry their own fill-in intent;
        // the host combines whichever view was tapped with the collection's
        // template. They must be siblings - a child inside the tappable content
        // never receives its own action.
        views.setOnClickFillInIntent(R.id.item_content, fillInIntent(item, WidgetActionReceiver.ACTION_OPEN))
        if (markable) {
            views.setOnClickFillInIntent(
                R.id.item_check,
                fillInIntent(item, WidgetActionReceiver.ACTION_MARK_WATCHED),
            )
        }
        return views
    }

    private fun fillInIntent(item: SearchResult, action: String): Intent = Intent().apply {
        putExtra(WidgetActionReceiver.EXTRA_ACTION, action)
        putExtra(WidgetActionReceiver.EXTRA_APPWIDGET_ID, appWidgetId)
        putExtra(WidgetActionReceiver.EXTRA_SOURCE, item.source)
        putExtra(WidgetActionReceiver.EXTRA_MEDIA_TYPE, item.mediaType)
        putExtra(WidgetActionReceiver.EXTRA_MEDIA_ID, item.mediaId)
        // The Floppy library the title lives in. A show can be tracked in the
        // TV and in the Anime library at once, and the watch has to land in
        // the same one the row came from.
        putExtra(WidgetActionReceiver.EXTRA_LIBRARY, item.libraryMediaType.orEmpty())
        putExtra(WidgetActionReceiver.EXTRA_SEASON, item.nextEpisode?.seasonNumber ?: -1)
        putExtra(WidgetActionReceiver.EXTRA_EPISODE, item.nextEpisode?.episodeNumber ?: -1)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = false

    override fun onDestroy() {
        rows = emptyList()
    }

    /**
     * The last list that loaded, kept in plain preferences.
     *
     * Plain on purpose: it has to be readable when the encrypted settings
     * cannot be opened (right after boot, say), which is one of the moments the
     * widget would otherwise come up empty.
     */
    private fun cachedItems(prefs: android.content.SharedPreferences, type: String): List<SearchResult> {
        val raw = prefs.getString("cache_${type}_$appWidgetId", null) ?: return emptyList()
        return runCatching { itemsAdapter.fromJson(raw) }.getOrNull().orEmpty()
    }

    private fun saveCache(
        prefs: android.content.SharedPreferences,
        type: String,
        items: List<SearchResult>,
    ) {
        runCatching {
            prefs.edit().putString("cache_${type}_$appWidgetId", itemsAdapter.toJson(items)).apply()
        }
    }

    private companion object {
        const val WIDGET_PREFS = "widget_prefs"

        /**
         * How many shows the widget loads.
         *
         * The list scrolls, so this is not a limit on what the user can see -
         * it only decides how much is fetched. The Shows tab in the app pages
         * through the same list, so the widget shows the same shows.
         */
        const val MAX_ROWS = 20

        /** Round-trips the rows through the cache. */
        private val moshi: Moshi = Moshi.Builder()
            .add(FlexStringAdapterFactory)
            .add(KotlinJsonAdapterFactory())
            .build()

        private val itemsAdapter = moshi.adapter<List<SearchResult>>(
            Types.newParameterizedType(List::class.java, SearchResult::class.java),
        )

        const val LIGHT_ROW = 0xFFF3F4F6.toInt()
        const val DARK_ROW = 0xFF374151.toInt()
        const val LIGHT_PRIMARY = 0xFF111827.toInt()
        const val DARK_PRIMARY = 0xFFF9FAFB.toInt()
        const val LIGHT_SECONDARY = 0xFF6B7280.toInt()
        const val DARK_SECONDARY = 0xFF9CA3AF.toInt()
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val DATE_WITH_YEAR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

/**
 * Disketarr reports a placeholder `0001-01-01` air date for episodes with no
 * known release date, and tracked shows often omit `next_episode` entirely.
 * Treat both as "no date" so date-based sorts fall back to the last-watched
 * timestamp instead of collapsing into the API's original order.
 */
private fun String?.validDateOrNull(): String? {
    val text = this?.take(10)?.trim().orEmpty()
    return text.takeIf { it.isNotBlank() && it != "0001-01-01" }
}

/** Episode air date when known, otherwise the last-watched date, otherwise empty. */
private fun SearchResult.episodeOrWatchDate(): String =
    nextEpisode?.airDate.validDateOrNull()
        ?: lastWatchedAt.validDateOrNull()
        ?: ""

/** "2x03 Purpose" - the episode code plus title when either is available. */
private fun episodeLabel(item: SearchResult): String? {
    val episode = item.nextEpisode ?: return null
    val season = episode.seasonNumber
    val number = episode.episodeNumber
    val code = when {
        season != null && number != null -> "${season}x${number.toString().padStart(2, '0')}"
        !episode.episodeCode.isNullOrBlank() -> episode.episodeCode
        else -> null
    }
    val title = episode.title?.takeIf { it.isNotBlank() }
    return listOfNotNull(code, title).joinToString(" ").takeIf { it.isNotBlank() }
}

private fun parseMoment(raw: String?): ZonedDateTime? {
    val text = raw?.takeIf { it.isNotBlank() && !it.startsWith("0001-01-01") } ?: return null
    runCatching { OffsetDateTime.parse(text) }.getOrNull()?.let {
        return it.atZoneSameInstant(ZoneId.systemDefault())
    }
    runCatching { LocalDateTime.parse(text) }.getOrNull()?.let {
        return it.atZone(ZoneId.systemDefault())
    }
    return null
}

private fun formatDate(moment: ZonedDateTime): String =
    if (moment.year == LocalDate.now().year) {
        DATE_FORMAT.format(moment)
    } else {
        DATE_WITH_YEAR_FORMAT.format(moment)
    }

/**
 * True when every aired episode is watched but more are still to come.
 *
 * `episodes_left` counts the released episodes and `total_episodes_left` the
 * announced ones as well, so zero released and a positive total means the user
 * is caught up with nothing to watch right now. The widget lists what can be
 * watched, so such a show is left out of it until the next episode is out.
 */
internal fun SearchResult.isCaughtUpWithUpcoming(): Boolean =
    status == MediaStatus.IN_PROGRESS &&
        episodesLeft != null && episodesLeft <= 0 &&
        (totalEpisodesLeft ?: 0) > 0
