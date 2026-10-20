package io.github.catadoxy.disketarr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.model.LibraryType
import io.github.catadoxy.disketarr.data.model.MediaDetail
import io.github.catadoxy.disketarr.data.model.MediaStatus
import io.github.catadoxy.disketarr.data.model.MediaUpdateRequest
import io.github.catadoxy.disketarr.data.model.SeasonEpisode
import io.github.catadoxy.disketarr.data.model.SeasonSummary
import io.github.catadoxy.disketarr.data.model.TrackMediaRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catadoxy.disketarr.data.AppCacheRepository
import io.github.catadoxy.disketarr.data.network.DisketarrApiService
import io.github.catadoxy.disketarr.data.repository.DisketarrRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(
        val detail: MediaDetail,
        val seasons: List<SeasonSummary> = emptyList(),
        val selectedSeason: Int? = null,
        val episodes: List<SeasonEpisode> = emptyList(),
        val loadingEpisodes: Boolean = false,
    ) : DetailsUiState

    data class Error(val message: String) : DetailsUiState
}

/** The next episode to watch: the first unwatched one in the loaded season. */
val DetailsUiState.Success?.nextEpisode: SeasonEpisode?
    get() = this?.episodes?.firstOrNull { !it.watched }

/** Only these carry seasons/episodes worth listing. */
internal fun isShowMediaType(mediaType: String): Boolean =
    mediaType.lowercase() in setOf("tv", "anime")

/** Cached history entries (show id -> set of "season|episode") for determining
 *  watched state of shows whose episodes aren't tracked individually. */
private val historyWatchedCache = mutableMapOf<String, MutableSet<String>>()
private var historyFetched = false
private val historyMutex = kotlinx.coroutines.sync.Mutex()

private suspend fun fetchHistoryWatched(service: DisketarrApiService) {
    historyMutex.withLock {
        if (historyFetched) return
        historyFetched = true
        var offset = 0
        while (true) {
            val raw = runCatching {
                service.listHistoryRaw(limit = 200, offset = offset, flat = "1")
            }.getOrNull() ?: break
            val rows = raw.extractRowsSafely()
            if (rows.isEmpty()) break
            for (rowAny in rows) {
                val row = rowAny.toStringKeyMapOrNull() ?: continue
                val item = row["item"].toStringKeyMapOrNull() ?: continue
                val mediaId = item["media_id"]?.toString() ?: continue
                val season = (row["season_number"] ?: item["season_number"])?.toString()?.toIntOrNull()
                val episode = (row["episode_number"] ?: item["episode_number"])?.toString()?.toIntOrNull()
                if (season != null && episode != null) {
                    historyWatchedCache.getOrPut(mediaId) { mutableSetOf() }.add("$season|$episode")
                }
            }
            val rawCount = rows.size
            if (rawCount < 200) break
            offset += rawCount
        }
    }
}

private suspend fun watchedEpisodesFromHistory(
    service: DisketarrApiService,
    mediaId: String,
): Set<String> {
    fetchHistoryWatched(service)
    return historyWatchedCache[mediaId] ?: emptySet()
}

/**
 * Key of a locally recorded watched episode.
 *
 * The library is part of it: the same episode can be watched in the TV and in
 * the Anime library, and the two are different entries.
 */
private fun episodeKey(
    mediaType: String,
    source: String,
    mediaId: String,
    season: Int,
    episode: Int,
    library: String = "",
): String =
    "${apiMediaType(mediaType, source)}|$source|$mediaId|$season|$episode|$library"

/**
 * The media type in Floppy's path.
 *
 * The path segment is the *item* type, and the library is a separate
 * parameter. A TMDB anime is a "tv" item, but the search reports it as
 * "anime" - so the source decides: only MyAnimeList has "anime" items. The
 * seasons/episodes/watch routes only accept "tv" (even for anime).
 */
internal fun apiMediaType(mediaType: String, source: String? = null): String =
    when {
        mediaType.lowercase() in setOf("season", "episode") -> "tv"
        mediaType.lowercase() == "anime" && source?.lowercase() != "mal" -> "tv"
        else -> mediaType
    }

fun buildTrackRequest(
    mediaType: String,
    source: String,
    mediaId: String,
    title: String? = null,
    image: String? = null,
    score: Double? = null,
    status: Any? = null,
    progress: Double? = null,
    startDate: String? = null,
    endDate: String? = null,
    notes: String? = null,
    seasonNumber: Int? = null,
    /**
     * The Floppy library to file the entry in.
     *
     * This is what decides the library, not [mediaType]: a TMDB anime is a
     * "tv" item, so passing the item's media type would file it in the TV
     * library and it would show up in the Shows tab instead of the Anime one.
     */
    libraryMediaType: String = mediaType,
): TrackMediaRequest = TrackMediaRequest(
    source = source,
    mediaId = mediaId,
    title = title,
    image = image,
    imageUrl = image,
    seasonNumber = seasonNumber,
    libraryMediaType = libraryMediaType,
    score = score,
    status = status,
    progress = progress,
    startDate = startDate,
    endDate = endDate,
    notes = notes,
)

fun buildMediaUpdateRequest(
    score: Double? = null,
    status: Any? = null,
    progress: Double? = null,
    startDate: String? = null,
    endDate: String? = null,
    notes: String? = null,
): MediaUpdateRequest = MediaUpdateRequest(
    score = score,
    status = status,
    progress = progress,
    startDate = startDate,
    endDate = endDate,
    notes = notes,
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val repository: DisketarrRepository = DisketarrRepository(settingsStore),
    private val appCache: AppCacheRepository = AppCacheRepository(),
) : ViewModel() {
    /** Local record of watched episodes for shows whose episodes aren't tracked
     *  individually (e.g. anime), so the checkbox survives navigation/reload. */
    private val watchedEpisodeKeys: MutableSet<String> =
        settingsStore.watchedEpisodeKeys().toMutableSet()

    /** Total episodes of this show (from Floppy's max_progress). Only used for
     *  the debug dump below - the watched state comes from Floppy itself. */
    private var showMaxProgress: Int = 0

    /**
     * The Floppy library this screen is showing.
     *
     * A title can be tracked in more than one library at once - the TV library
     * and the Anime library keep separate status and progress for it - so every
     * read and write has to say which copy it means. Without it Floppy answers
     * with the TV library's copy and a write lands in the wrong place.
     */
    private var library: String = LibraryType.TV

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /** Reload from the server, ignoring the cache. Used by pull-to-refresh. */
    fun reload(source: String, mediaType: String, mediaId: String, library: String = this.library) {
        appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId, library))
        load(source, mediaType, mediaId, library)
    }

    fun load(source: String, mediaType: String, mediaId: String, library: String = LibraryType.TV) {
        this.library = library.lowercase().takeIf { LibraryType.isSupported(it) } ?: LibraryType.TV
        val cacheKey = appCache.detailKey(mediaType, source, mediaId, this.library)

        // Opening a title you have already viewed costs nothing.
        appCache.details(cacheKey)?.let { cached ->
            if (appCache.isFresh(cached.at)) {
                // Floppy reports the watched state of every episode, so the local
                // record only fills in what it does not track at all - a season
                // that is not in the library yet.
                val episodes = cached.episodes.map { ep ->
                    val season = ep.seasonNumber ?: cached.selectedSeason ?: 1
                    val key = episodeKey(mediaType, source, mediaId, season, ep.episodeNumber, this.library)
                    val tracked = cached.seasons.any { it.seasonNumber == season && !it.libraryMediaType.isNullOrBlank() }
                    val isWatched = ep.watched || (!tracked && watchedEpisodeKeys.contains(key))
                    if (isWatched == ep.watched) ep else ep.copy(watched = isWatched)
                }
                _uiState.value = DetailsUiState.Success(
                    detail = cached.detail,
                    seasons = cached.seasons,
                    selectedSeason = cached.selectedSeason,
                    episodes = episodes,
                )

                // Fetch history in the background to mark already-watched episodes
                // for seasons Floppy does not track in this library. That is
                // rare, and the history is thousands of entries - so it is only
                // fetched when the season on screen needs it.
                val selected = cached.selectedSeason
                val needsHistory = selected != null && cached.seasons.any {
                    it.seasonNumber == selected && it.libraryMediaType.isNullOrBlank()
                }
                if (needsHistory && (mediaType.lowercase() == "anime" || mediaType.lowercase() == "tv")) {
                    viewModelScope.launch {
                        try {
                            val historyWatched = watchedEpisodesFromHistory(repository.service(), mediaId)
                            if (historyWatched.isEmpty()) return@launch
                            val current = _uiState.value as? DetailsUiState.Success ?: return@launch
                            if (current.detail.mediaId != mediaId) return@launch
                            val updated = current.episodes.map { ep ->
                                val season = ep.seasonNumber ?: current.selectedSeason ?: 1
                                val tracked = current.seasons.any { it.seasonNumber == season && !it.libraryMediaType.isNullOrBlank() }
                                val isWatched = ep.watched ||
                                    (!tracked && historyWatched.contains("${ep.seasonNumber ?: season}|${ep.episodeNumber}"))
                                if (isWatched == ep.watched) ep else ep.copy(watched = isWatched)
                            }
                            _uiState.value = current.copy(episodes = updated)
                        } catch (e: Exception) { /* ignore */ }
                    }
                }

                return
            }
        }

        _uiState.value = DetailsUiState.Loading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = repository.service()
                    val (detail, resolvedLibrary) = resolveDetail(
                        service = service,
                        mediaType = mediaType,
                        source = source,
                        mediaId = mediaId,
                    )
                    // The title may live in the other library than the one the
                    // screen was opened for, so everything from here on works
                    // with the library Floppy actually has it in.
                    this@DetailsViewModel.library = resolvedLibrary
                    showMaxProgress = detail.maxProgress ?: 0
                    try { java.io.File("/sdcard/showMaxProgress.txt").writeText("showMaxProgress=$showMaxProgress mediaId=$mediaId") } catch (e: Exception) {}
                    

                    // A show's detail payload only carries an aggregate season
                    // count, so the real seasons and episodes come from their own
                    // endpoints. Without them the screen could only guess a
                    // single "Season 1" from max_progress.
                    val (seasons, selected, episodes) =
                        if (isShowMediaType(mediaType)) {
                            loadSeasons(service, mediaType, source, mediaId)
                        } else {
                            Triple(emptyList<SeasonSummary>(), null, emptyList<SeasonEpisode>())
                        }

                    _uiState.value = DetailsUiState.Success(
                        detail = detail,
                        seasons = seasons,
                        selectedSeason = selected,
                        episodes = episodes,
                    )

                    // Watched state for anime episodes is derived from max_progress
                    // (see loadSeasons) plus locally tracked marks; no background
                    // history fetch needed.

                    appCache.putDetails(
                        cacheKey,
                        AppCacheRepository.Details(
                            detail = detail,
                            seasons = seasons,
                            selectedSeason = selected,
                            episodes = episodes,
                            at = System.currentTimeMillis(),
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.value = DetailsUiState.Error(e.message ?: "Failed to load")
            }
        }
    }

    private suspend fun loadSeasons(
        service: DisketarrApiService,
        mediaType: String,
        source: String,
        mediaId: String,
    ): Triple<List<SeasonSummary>, Int?, List<SeasonEpisode>> {
        val apiType = apiMediaType(mediaType, source)

        val summaries = runCatching {
            service.listSeasonsRaw(
                mediaType = apiMediaType(mediaType, source),
                source = source,
                mediaId = mediaId,
                // The seasons of a show live in their own library, not in the
                // one of the show: a TV show's seasons are in the TV Seasons
                // library, an anime's in the Anime library.
                libraryMediaType = LibraryType.seasonLibrary(library),
            ).asSeasonSummaries()
        }.getOrElse { emptyList() }.let { seasons ->
            if (settingsStore.hideSpecialEpisodes) seasons.filter { it.seasonNumber != 0 } else seasons
        }

        val selected = pickDefaultSeason(summaries)
            ?: return Triple(summaries, null, emptyList())

        val rawEpisodes = runCatching {
            val all = mutableListOf<SeasonEpisode>()
            var offset = 0
            while (true) {
                val page = service.listSeasonEpisodesRaw(
                    mediaType = apiType,
                    source = source,
                    mediaId = mediaId,
                    seasonNumber = selected,
                    libraryMediaType = LibraryType.episodeLibrary(library),
                    limit = 200,
                    offset = offset,
                ).asSeasonEpisodes(fallbackSeason = selected)
                all += page
                if (page.size < 200) break
                offset += page.size
            }
            all
        }.getOrElse { emptyList() }

        // Floppy reports the real state now that the library is known: the
        // season summaries carry what is watched, and the episode rows carry
        // their own status. The local record only fills in what Floppy does
        // not track at all - a season that is not in the library yet.
        val seasonTracked = summaries.any { it.seasonNumber == selected && !it.libraryMediaType.isNullOrBlank() }
        val episodes = rawEpisodes.map { ep ->
            val key = episodeKey(mediaType, source, mediaId, ep.seasonNumber ?: selected, ep.episodeNumber, library)
            val isWatched = ep.watched ||
                (!seasonTracked && watchedEpisodeKeys.contains(key))
            if (isWatched == ep.watched) ep else ep.copy(watched = isWatched)
        }

        return Triple(summaries, selected, episodes)
    }

    /** Tap a season header to expand it; tap again to collapse it. */
    fun toggleSeason(mediaType: String, source: String, mediaId: String, season: Int) {
        val current = _uiState.value as? DetailsUiState.Success ?: return
        if (current.selectedSeason == season) {
            _uiState.value = current.copy(selectedSeason = null, episodes = emptyList(), loadingEpisodes = false)
            return
        }
        selectSeason(mediaType, source, mediaId, season)
    }

    /** Switching seasons re-fetches that season's episodes. */
    fun selectSeason(mediaType: String, source: String, mediaId: String, season: Int) {
        val current = _uiState.value as? DetailsUiState.Success ?: return
        if (current.selectedSeason == season) return

        _uiState.value = current.copy(selectedSeason = season, episodes = emptyList(), loadingEpisodes = true)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val apiType = apiMediaType(mediaType, source)
                val episodes = runCatching {
                    val service = repository.service()
                    val all = mutableListOf<SeasonEpisode>()
                    var offset = 0
                    while (true) {
                        val page = service.listSeasonEpisodesRaw(
                            mediaType = apiType,
                            source = source,
                            mediaId = mediaId,
                            seasonNumber = season,
                            libraryMediaType = LibraryType.episodeLibrary(library),
                            limit = 200,
                            offset = offset,
                        ).asSeasonEpisodes(fallbackSeason = season)
                        all += page
                        if (page.size < 200) break
                        offset += page.size
                    }
                    // Floppy reports the watched state of every episode. The
                    // local record only fills in what Floppy does not track at
                    // all - a season that is not in the library yet.
                    val knownSeasons = (uiState.value as? DetailsUiState.Success)?.seasons.orEmpty()
                    val seasonTracked = knownSeasons.any {
                        it.seasonNumber == season && !it.libraryMediaType.isNullOrBlank()
                    }
                    all.map { ep ->
                        val key = episodeKey(mediaType, source, mediaId, ep.seasonNumber ?: season, ep.episodeNumber, library)
                        val isWatched = ep.watched ||
                            (!seasonTracked && watchedEpisodeKeys.contains(key))
                        if (isWatched == ep.watched) ep else ep.copy(watched = isWatched)
                    }
                }.getOrElse { emptyList() }

                val latest = _uiState.value as? DetailsUiState.Success ?: return@withContext
                _uiState.value = latest.copy(episodes = episodes, loadingEpisodes = false)
                appCache.putEpisodes(
                    appCache.detailKey(mediaType, source, mediaId, library),
                    episodes,
                    season,
                )
            }
        }
    }

    /**
     * Tracks the season in the library the user is browsing, when it is not
     * tracked there yet.
     *
     * Floppy files an episode watch in the library that already tracks the
     * season. When no library tracks it, Floppy creates the season in the TV
     * Seasons library - and a copy of the show with it, which then shows up in
     * the Shows tab as a duplicate of the anime the user is watching.
     */
    private suspend fun ensureSeasonTracked(
        service: DisketarrApiService,
        source: String,
        mediaId: String,
        season: Int,
    ) {
        // Only the Anime library needs this. For TV, Floppy's TV Seasons
        // library is where a season-level watch belongs anyway.
        if (library != LibraryType.ANIME) return

        val state = _uiState.value as? DetailsUiState.Success ?: return
        val trackedInAnime = state.seasons
            .firstOrNull { it.seasonNumber == season }
            ?.libraryMediaType
            ?.equals(LibraryType.ANIME, ignoreCase = true) == true
        if (trackedInAnime) return

        val status = state.detail.consumptions?.firstOrNull()?.status ?: MediaStatus.IN_PROGRESS
        runCatching {
            service.trackMedia(
                mediaType = "season",
                request = buildTrackRequest(
                    mediaType = "season",
                    source = source,
                    mediaId = mediaId,
                    seasonNumber = season,
                    status = status,
                    progress = 0.0,
                    libraryMediaType = LibraryType.ANIME,
                ),
            )
        }
    }

    /**
     * The libraries a title can be tracked in, in the order they are checked.
     *
     * Floppy files a tracked entry in exactly one library, and the same title
     * can be in the TV library in one account and in the Anime library in
     * another - the Anime library exists for exactly that. The screen is opened
     * with a guess (the tab it was opened from, or the type that was searched
     * for), so the other libraries are checked when the title is not tracked in
     * the guessed one.
     */
    private fun libraryCandidates(mediaType: String, preferred: String): List<String> {
        val others = when (mediaType.lowercase()) {
            // A movie only ever lives in the Movies library.
            "movie" -> emptyList()
            else -> listOf(LibraryType.TV, LibraryType.ANIME)
        }
        return (listOf(preferred) + others).distinct()
    }

    /**
     * Reads the title and the library it is tracked in.
     *
     * The detail payload says whether the title is tracked in the library that
     * was asked for, so the other libraries only cost a request when the guess
     * was wrong.
     */
    private suspend fun resolveDetail(
        service: DisketarrApiService,
        mediaType: String,
        source: String,
        mediaId: String,
    ): Pair<MediaDetail, String> {
        val preferred = LibraryType.apiParam(library) ?: LibraryType.forMediaType(mediaType)
        val candidates = libraryCandidates(mediaType, preferred)
        var first: MediaDetail? = null
        for (candidate in candidates) {
            val detail = runCatching {
                service.getMediaDetail(
                    mediaType = mediaType,
                    source = source,
                    mediaId = mediaId,
                    libraryMediaType = LibraryType.apiParam(candidate),
                )
            }.getOrNull() ?: continue
            if (first == null) first = detail
            if (detail.tracked != false) return detail to candidate
        }
        // Not tracked in any of them: show the first answer, so the screen can
        // offer to track the title.
        val detail = first ?: service.getMediaDetail(
            mediaType = mediaType,
            source = source,
            mediaId = mediaId,
            libraryMediaType = LibraryType.apiParam(preferred),
        )
        return detail to preferred
    }

    /** Marks the next unwatched episode watched, straight from the Overview tab. */
    fun markNextEpisodeWatched(mediaType: String, source: String, mediaId: String) {
        val next = (_uiState.value as? DetailsUiState.Success)?.nextEpisode ?: return
        toggleEpisodeWatched(mediaType, source, mediaId, next)
    }

    /** Tapping an episode flips its watched state, then reloads the season. */
    fun toggleEpisodeWatched(
        mediaType: String,
        source: String,
        mediaId: String,
        episode: SeasonEpisode,
    ) {

        val season = episode.seasonNumber ?: (uiState.value as? DetailsUiState.Success)?.selectedSeason
        if (season == null) {
            _actionMessage.value = "Could not tell which season this episode belongs to."
            return
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = repository.service()
                    val apiType = apiMediaType(mediaType, source)
                    // Floppy files a watch in the library that already tracks the
                    // season, so an untracked season has to be created in the
                    // library the user is browsing first.
                    ensureSeasonTracked(service, source, mediaId, season)
                    val response = if (episode.watched) {
                        service.unmarkEpisodeWatched(
                            mediaType = apiType,
                            source = source,
                            mediaId = mediaId,
                            seasonNumber = season,
                            episodeNumber = episode.episodeNumber,
                            libraryMediaType = LibraryType.episodeLibrary(library),
                        )
                    } else {
                        service.markEpisodeWatched(
                            mediaType = apiType,
                            source = source,
                            mediaId = mediaId,
                            seasonNumber = season,
                            episodeNumber = episode.episodeNumber,
                            libraryMediaType = LibraryType.episodeLibrary(library),
                        )
                    }

                    if (!response.isSuccessful) {
                        _actionMessage.value = if (response.code() == 403) {
                            "This token cannot change watched state (needs watchlist:write)."
                        } else {
                            "Episode update failed: HTTP ${response.code()}"
                        }
                        return@withContext
                    }

                    _actionMessage.value = if (episode.watched) {
                        "Unwatched ${episode.code}"
                    } else {
                        "Watched ${episode.code}"
                    }

                    val key = episodeKey(mediaType, source, mediaId, season, episode.episodeNumber, library)
                    if (episode.watched) watchedEpisodeKeys.remove(key) else watchedEpisodeKeys.add(key)
                    settingsStore.setWatchedEpisodeKeys(watchedEpisodeKeys)

                    val newWatched = !episode.watched
                    val episodes = runCatching {
                        val all = mutableListOf<SeasonEpisode>()
                        var offset = 0
                        while (true) {
                            val page = service.listSeasonEpisodesRaw(
                                mediaType = apiType,
                                source = source,
                                mediaId = mediaId,
                                seasonNumber = season,
                                libraryMediaType = LibraryType.episodeLibrary(library),
                                limit = 200,
                                offset = offset,
                            ).asSeasonEpisodes(fallbackSeason = season)
                            all += page
                            if (page.size < 200) break
                            offset += page.size
                        }
                        all
                    }.getOrElse { emptyList() }
                    val latest = _uiState.value as? DetailsUiState.Success ?: return@withContext
                    // Floppy reports the state it just accepted. Keep the state
                    // of the episode that was tapped, in case the reload lags
                    // behind the write.
                    val merged = episodes.map { ep ->
                        val isWatched = ep.watched ||
                            (ep.seasonNumber == season && ep.episodeNumber == episode.episodeNumber && newWatched)
                        ep.copy(watched = isWatched)
                    }
                    _uiState.value = latest.copy(episodes = merged, selectedSeason = season)
                    // A write changes the item and the lists that contain it.
                    appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId, library))
                    appCache.putEpisodes(appCache.detailKey(mediaType, source, mediaId, library), episodes, season)
                }
            } catch (e: Exception) {
                _actionMessage.value = "Episode update failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun markWatched(detail: MediaDetail) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                val service = repository.service()
                val source = detail.source ?: run {
                    _actionMessage.value = "Could not identify this media."
                    return@withContext
                }
                val mediaId = detail.mediaId ?: detail.id?.toString() ?: run {
                    _actionMessage.value = "Could not identify this media."
                    return@withContext
                }
                val mediaType = detail.mediaType
                val progress = (detail.maxProgress?.toDouble() ?: 100.0).coerceIn(0.0, 100.0)
                val existing = detail.consumptions?.firstOrNull()

                if (existing != null) {
                    val response = service.updateMediaConsumption(
                        mediaType = mediaType,
                        source = source,
                        mediaId = mediaId,
                        consumptionId = existing.consumptionId.toString(),
                        libraryMediaType = LibraryType.episodeLibrary(library),
                        request = buildMediaUpdateRequest(
                            score = existing.score,
                            status = MediaStatus.COMPLETED,
                            progress = progress,
                        ),
                    )
                    _actionMessage.value = "Marked watched${response.consumptionId?.let { " (#$it)" } ?: ""}"
                } else {
                    val response = service.trackMedia(
                        mediaType = mediaType,
                        request = buildTrackRequest(
                            mediaType = mediaType,
                            source = source,
                            mediaId = mediaId,
                            title = detail.title,
                            image = detail.image,
                            score = detail.score,
                            status = MediaStatus.COMPLETED,
                            progress = progress,
                            libraryMediaType = library,
                        ),
                    )
                    _actionMessage.value = "Tracked${response.consumptionId?.let { " (#$it)" } ?: ""}"
                }
                // A write changes the item and every list that contains it.
                appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId, library))
                }
            } catch (e: Exception) {
                _actionMessage.value = "Mark watched failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun rate(detail: MediaDetail, score: Double) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                val service = repository.service()
                val source = detail.source ?: run {
                    _actionMessage.value = "Could not identify this media."
                    return@withContext
                }
                val mediaId = detail.mediaId ?: detail.id?.toString() ?: run {
                    _actionMessage.value = "Could not identify this media."
                    return@withContext
                }
                val mediaType = detail.mediaType
                val existing = detail.consumptions?.firstOrNull()

                val response = if (existing != null) {
                    service.updateMediaConsumption(
                        mediaType = mediaType,
                        source = source,
                        mediaId = mediaId,
                        consumptionId = existing.consumptionId.toString(),
                        libraryMediaType = LibraryType.episodeLibrary(library),
                        request = buildMediaUpdateRequest(
                            score = score,
                            status = existing.status,
                            progress = existing.progress,
                        ),
                    )
                } else {
                    service.trackMedia(
                        mediaType = mediaType,
                        request = buildTrackRequest(
                            mediaType = mediaType,
                            source = source,
                            mediaId = mediaId,
                            title = detail.title,
                            image = detail.image,
                            score = score,
                            status = MediaStatus.IN_PROGRESS,
                            // Rating is not watching: track it on the watchlist with
                            // nothing consumed rather than at max progress.
                            progress = 0.0,
                            libraryMediaType = library,
                        ),
                    )
                }
                _actionMessage.value = "Rated ${score} (${response.consumptionId ?: "saved"})"
                appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId, library))
                }
            } catch (e: Exception) {
                _actionMessage.value = "Rate failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun addToTracker(detail: MediaDetail) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = repository.service()
                    val source = detail.source ?: run {
                        _actionMessage.value = "Could not identify this media."
                        return@withContext
                    }
                    val mediaId = detail.mediaId ?: detail.id?.toString() ?: run {
                        _actionMessage.value = "Could not identify this media."
                        return@withContext
                    }
                    val mediaType = detail.mediaType
                    val existing = detail.consumptions?.firstOrNull()
                    if (existing != null) {
                        _actionMessage.value = "Already tracked"
                        return@withContext
                    }
                    val response = service.trackMedia(
                        mediaType = mediaType,
                        request = buildTrackRequest(
                            mediaType = mediaType,
                            source = source,
                            mediaId = mediaId,
                            title = detail.title,
                            image = detail.image,
                            score = detail.score,
                            status = MediaStatus.PLANNING,
                            progress = 0.0,
                            libraryMediaType = library,
                        ),
                    )
                    _actionMessage.value = "Added to tracker${response.consumptionId?.let { " (#$it)" } ?: ""}"
                    appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId, library))
                }
            } catch (e: Exception) {
                _actionMessage.value = "Add to tracker failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun unmarkWatched(detail: MediaDetail) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = repository.service()
                    val source = detail.source ?: run {
                        _actionMessage.value = "Could not identify this media."
                        return@withContext
                    }
                    val mediaId = detail.mediaId ?: detail.id?.toString() ?: run {
                        _actionMessage.value = "Could not identify this media."
                        return@withContext
                    }
                    val mediaType = detail.mediaType
                    val existing = detail.consumptions?.firstOrNull() ?: run {
                        _actionMessage.value = "Not tracked yet"
                        return@withContext
                    }
                    val response = service.updateMediaConsumption(
                        mediaType = mediaType,
                        source = source,
                        mediaId = mediaId,
                        consumptionId = existing.consumptionId.toString(),
                        libraryMediaType = LibraryType.episodeLibrary(library),
                        request = buildMediaUpdateRequest(
                            score = existing.score,
                            status = MediaStatus.PLANNING,
                            progress = 0.0,
                        ),
                    )
                    _actionMessage.value = "Unmarked watched${response.consumptionId?.let { " (#$it)" } ?: ""}"
                    appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId, library))
                }
            } catch (e: Exception) {
                _actionMessage.value = "Unmark failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun untrack(detail: MediaDetail) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = repository.service()
                    val source = detail.source ?: run {
                        _actionMessage.value = "Could not identify this media."
                        return@withContext
                    }
                    val mediaId = detail.mediaId ?: detail.id?.toString() ?: run {
                        _actionMessage.value = "Could not identify this media."
                        return@withContext
                    }
                    val mediaType = detail.mediaType
                    val response = service.untrackMedia(
                        mediaType = mediaType,
                        source = source,
                        mediaId = mediaId,
                        libraryMediaType = LibraryType.episodeLibrary(library),
                    )
                    if (!response.isSuccessful && response.code() != 204) {
                        _actionMessage.value = "Untrack failed: HTTP ${response.code()}"
                        return@withContext
                    }
                    _actionMessage.value = "Untracked"
                    appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId, library))
                    // Refresh so tracked/consumption state reflects the change.
                    load(source, mediaType, mediaId, library)
                }
            } catch (e: Exception) {
                _actionMessage.value = "Untrack failed: ${e.message ?: "unknown error"}"
            }
        }
    }

    fun seriesReminderKey(detail: MediaDetail): String? {
        val source = detail.source ?: return null
        val mediaId = detail.mediaId ?: detail.id?.toString() ?: return null
        return "$source|${detail.mediaType}|$mediaId"
    }

    fun isSeriesReminderEnabled(detail: MediaDetail): Boolean {
        val key = seriesReminderKey(detail) ?: return false
        return settingsStore.isSeriesReminderEnabled(key)
    }

    fun toggleSeriesReminder(detail: MediaDetail) {
        val key = seriesReminderKey(detail) ?: return
        val enabled = !settingsStore.isSeriesReminderEnabled(key)
        settingsStore.toggleSeriesReminder(key, enabled)
        _actionMessage.value = if (enabled) "Reminders on for this title" else "Reminders off for this title"
    }

    fun addToList(detail: MediaDetail, listId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                val service = repository.service()
                val source = detail.source ?: run {
                    _actionMessage.value = "Could not identify this media."
                    return@withContext
                }
                val mediaId = detail.mediaId ?: detail.id?.toString() ?: run {
                    _actionMessage.value = "Could not identify this media."
                    return@withContext
                }
                val mediaType = detail.mediaType
                service.addMediaToList(
                    mediaType = mediaType,
                    source = source,
                    mediaId = mediaId,
                    listId = listId,
                )
                _actionMessage.value = "Added to list #$listId"
                }
            } catch (e: Exception) {
                _actionMessage.value = "Add to list failed: ${e.message ?: "unknown error"}"
            }
        }
    }
}


