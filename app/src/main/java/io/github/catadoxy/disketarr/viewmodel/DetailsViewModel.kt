package io.github.catadoxy.disketarr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.catadoxy.disketarr.data.SettingsStore
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

private fun episodeKey(mediaType: String, source: String, mediaId: String, season: Int, episode: Int): String =
    "${apiMediaType(mediaType)}|$source|$mediaId|$season|$episode"

/** Floppy's seasons/episodes/watch endpoints only accept "tv" (even for anime). */
internal fun apiMediaType(mediaType: String): String =
    if (mediaType.lowercase() == "anime") "tv" else mediaType

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
): TrackMediaRequest = TrackMediaRequest(
    source = source,
    mediaId = mediaId,
    title = title,
    image = image,
    imageUrl = image,
    libraryMediaType = mediaType,
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

    /** Total episodes watched for this show (from Floppy's max_progress). Used
     *  as a heuristic for shows whose episodes aren't tracked individually. */
    private var showMaxProgress: Int = 0

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /** Reload from the server, ignoring the cache. Used by pull-to-refresh. */
    fun reload(source: String, mediaType: String, mediaId: String) {
        appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId))
        load(source, mediaType, mediaId)
    }

    fun load(source: String, mediaType: String, mediaId: String) {
        val cacheKey = appCache.detailKey(mediaType, source, mediaId)

        // Opening a title you have already viewed costs nothing.
        appCache.details(cacheKey)?.let { cached ->
            if (appCache.isFresh(cached.at)) {
                // Apply locally tracked watched state (needed for shows whose
                // episodes aren't tracked individually, e.g. anime).
                val episodes = cached.episodes.map { ep ->
                    val key = episodeKey(mediaType, source, mediaId, ep.seasonNumber ?: cached.selectedSeason ?: 1, ep.episodeNumber)
                    if (ep.watched) ep else ep.copy(watched = watchedEpisodeKeys.contains(key))
                }
                _uiState.value = DetailsUiState.Success(
                    detail = cached.detail,
                    seasons = cached.seasons,
                    selectedSeason = cached.selectedSeason,
                    episodes = episodes,
                )

                // Fetch history in the background to mark already-watched episodes
                // for shows whose episodes aren't tracked individually.
                if (mediaType.lowercase() == "anime" || mediaType.lowercase() == "tv") {
                    viewModelScope.launch {
                        try {
                            val historyWatched = watchedEpisodesFromHistory(repository.service(), mediaId)
                            if (historyWatched.isEmpty()) return@launch
                            val current = _uiState.value as? DetailsUiState.Success ?: return@launch
                            if (current.detail.mediaId != mediaId) return@launch
                            val updated = current.episodes.map { ep ->
                                val isWatched = ep.watched || historyWatched.contains("${ep.seasonNumber}|${ep.episodeNumber}")
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
                    val detail = service.getMediaDetail(mediaType = mediaType, source = source, mediaId = mediaId)
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
        val apiType = apiMediaType(mediaType)

        val summaries = runCatching {
            service.listSeasonsRaw(mediaType = apiMediaType(mediaType), source = source, mediaId = mediaId).asSeasonSummaries()
        }.getOrElse { emptyList() }.let { seasons ->
            if (settingsStore.hideSpecialEpisodes) seasons.filter { it.seasonNumber != 0 } else seasons
        }

        val selected = pickDefaultSeason(summaries)
            ?: return Triple(summaries, null, emptyList())

        // For shows whose episodes aren't tracked individually, use max_progress
        // as a heuristic: episodes are watched in order, so the first
        // showMaxProgress episodes (across seasons) are marked watched.
        val watchedPerSeason: Map<Int, Int> = if (showMaxProgress > 0) {
            val sortedSeasons = summaries.sortedBy { it.seasonNumber }
            var cumulative = 0
            val result = mutableMapOf<Int, Int>()
            for (s in sortedSeasons) {
                val count = runCatching {
                    service.listSeasonEpisodesRaw(
                        mediaType = apiType,
                        source = source,
                        mediaId = mediaId,
                        seasonNumber = s.seasonNumber,
                        limit = 1,
                    )["pagination"]?.toStringKeyMapOrNull()?.get("total")?.asIntOrNull() ?: 0
                }.getOrDefault(0)
                val watched = (showMaxProgress - cumulative).coerceIn(0, count)
                result[s.seasonNumber] = watched
                cumulative += count
            }
            result
        } else emptyMap()

        val watchedInSelectedSeason = watchedPerSeason[selected] ?: 0

        // placeholder2 replaced



        val rawEpisodes = runCatching {
            val all = mutableListOf<SeasonEpisode>()
            var offset = 0
            while (true) {
                val page = service.listSeasonEpisodesRaw(
                    mediaType = apiType,
                    source = source,
                    mediaId = mediaId,
                    seasonNumber = selected,
                    limit = 200,
                    offset = offset,
                ).asSeasonEpisodes(fallbackSeason = selected)
                all += page
                if (page.size < 200) break
                offset += page.size
            }
            all
        }.getOrElse { emptyList() }

        // Override progress for shows where the API's progress is stale or missing.
        // For anime, the API reports a low/incorrect progress (or null) while
        // max_progress reflects the true watched count.
        val selectedSummaryProgress = summaries.find { it.seasonNumber == selected }?.progress
        val apiProgress = selectedSummaryProgress ?: 0
        val overrideProgress = showMaxProgress > 0 && showMaxProgress > apiProgress
        val updatedSummaries = if (overrideProgress) {
            summaries.map { s ->
                s.copy(progress = watchedPerSeason[s.seasonNumber] ?: s.progress, episodesLeft = 0)
            }
        } else summaries

        val episodes = rawEpisodes.mapIndexed { index, ep ->
            val key = episodeKey(mediaType, source, mediaId, ep.seasonNumber ?: selected, ep.episodeNumber)
            val isWatched = ep.watched ||
                (showMaxProgress > 0 && (index + 1) <= watchedInSelectedSeason) ||
                watchedEpisodeKeys.contains(key)
            if (isWatched == ep.watched) ep else ep.copy(watched = isWatched)
        }


        // Override progress when the API's value is missing or inconsistent
        // with the max_progress heuristic (e.g. anime shows where the API
        // reports a stale/low progress).

        return Triple(updatedSummaries, selected, episodes)
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
                val apiType = apiMediaType(mediaType)
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
                            limit = 200,
                            offset = offset,
                        ).asSeasonEpisodes(fallbackSeason = season)
                        all += page
                        if (page.size < 200) break
                        offset += page.size
                    }
                    // Compute watched count for this season using max_progress.
                    val sortedSeasons = (uiState.value as? DetailsUiState.Success)?.seasons?.sortedBy { it.seasonNumber } ?: emptyList()
                    var cumulative = 0
                    var watchedInThisSeason = 0
                    for (s in sortedSeasons) {
                        val count = if (s.seasonNumber == season) all.size else runCatching {
                            service.listSeasonEpisodesRaw(mediaType = apiType, source = source, mediaId = mediaId, seasonNumber = s.seasonNumber, limit = 1)["pagination"]?.toStringKeyMapOrNull()?.get("total")?.asIntOrNull() ?: 0
                        }.getOrDefault(0)
                        if (s.seasonNumber == season) {
                            watchedInThisSeason = (showMaxProgress - cumulative).coerceIn(0, all.size)
                            break
                        }
                        cumulative += count
                    }
                    all.mapIndexed { index, ep ->
                        val key = episodeKey(mediaType, source, mediaId, ep.seasonNumber ?: season, ep.episodeNumber)
                        val isWatched = ep.watched ||
                            (showMaxProgress > 0 && (index + 1) <= watchedInThisSeason) ||
                            watchedEpisodeKeys.contains(key)
                        if (isWatched == ep.watched) ep else ep.copy(watched = isWatched)
                    }
                }.getOrElse { emptyList() }

                val latest = _uiState.value as? DetailsUiState.Success ?: return@withContext
                _uiState.value = latest.copy(episodes = episodes, loadingEpisodes = false)
                appCache.putEpisodes(
                    appCache.detailKey(mediaType, source, mediaId),
                    episodes,
                    season,
                )
            }
        }
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
                    val apiType = apiMediaType(mediaType)
                    val response = if (episode.watched) {
                        service.unmarkEpisodeWatched(apiType, source, mediaId, season, episode.episodeNumber)
                    } else {
                        service.markEpisodeWatched(apiType, source, mediaId, season, episode.episodeNumber)
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

                    val key = episodeKey(mediaType, source, mediaId, season, episode.episodeNumber)
                    if (episode.watched) watchedEpisodeKeys.remove(key) else watchedEpisodeKeys.add(key)
                    settingsStore.setWatchedEpisodeKeys(watchedEpisodeKeys)

                    val newWatched = !episode.watched
                    val historyWatched = if (mediaType.lowercase() == "anime" || mediaType.lowercase() == "tv") {
                        watchedEpisodesFromHistory(service, mediaId)
                    } else emptySet()
                    val episodes = runCatching {
                        val all = mutableListOf<SeasonEpisode>()
                        var offset = 0
                        while (true) {
                            val page = service.listSeasonEpisodesRaw(
                                mediaType = apiType,
                                source = source,
                                mediaId = mediaId,
                                seasonNumber = season,
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
                    // Episodes for some shows (e.g. anime) are not tracked
                    // individually, so the server returns a null status and the
                    // reload would leave the checkbox unchanged. Preserve the
                    // state the API just accepted so the UI reflects the tap.
                    val merged = episodes.map { ep ->
                        val isWatched = ep.watched ||
                            historyWatched.contains("${ep.seasonNumber ?: season}|${ep.episodeNumber}") ||
                            (ep.seasonNumber == season && ep.episodeNumber == episode.episodeNumber && newWatched)
                        ep.copy(watched = isWatched)
                    }
                    _uiState.value = latest.copy(episodes = merged, selectedSeason = season)
                    // A write changes the item and the lists that contain it.
                    appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId))
                    appCache.putEpisodes(appCache.detailKey(mediaType, source, mediaId), episodes, season)
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
                        ),
                    )
                    _actionMessage.value = "Tracked${response.consumptionId?.let { " (#$it)" } ?: ""}"
                }
                // A write changes the item and every list that contains it.
                appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId))
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
                        ),
                    )
                }
                _actionMessage.value = "Rated ${score} (${response.consumptionId ?: "saved"})"
                appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId))
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
                        ),
                    )
                    _actionMessage.value = "Added to tracker${response.consumptionId?.let { " (#$it)" } ?: ""}"
                    appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId))
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
                        request = buildMediaUpdateRequest(
                            score = existing.score,
                            status = MediaStatus.PLANNING,
                            progress = 0.0,
                        ),
                    )
                    _actionMessage.value = "Unmarked watched${response.consumptionId?.let { " (#$it)" } ?: ""}"
                    appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId))
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
                    val response = service.untrackMedia(mediaType = mediaType, source = source, mediaId = mediaId)
                    if (!response.isSuccessful && response.code() != 204) {
                        _actionMessage.value = "Untrack failed: HTTP ${response.code()}"
                        return@withContext
                    }
                    _actionMessage.value = "Untracked"
                    appCache.invalidateAfterWrite(appCache.detailKey(mediaType, source, mediaId))
                    // Refresh so tracked/consumption state reflects the change.
                    load(source, mediaType, mediaId)
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


