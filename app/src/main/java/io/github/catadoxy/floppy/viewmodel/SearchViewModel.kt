package io.github.catadoxy.floppy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catadoxy.floppy.data.SettingsStore
import io.github.catadoxy.floppy.data.repository.FloppyRepository
import javax.inject.Inject
import io.github.catadoxy.floppy.data.model.SearchResponse
import io.github.catadoxy.floppy.data.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val response: SearchResponse) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

fun nextPageOffset(response: SearchResponse): Int =
    response.pagination.offset + response.pagination.limit

fun pageNumber(offset: Int, limit: Int): Int = if (limit <= 0) 1 else (offset / limit) + 1

fun normalizeMediaType(mediaType: String?): String {
    val normalized = mediaType?.trim()?.lowercase()?.replace(" ", "_") ?: "movie"
    return when (normalized) {
        "tv", "series", "show", "shows" -> "tv"
        "anime", "animation" -> "anime"
        "manga" -> "manga"
        "comic", "comics" -> "comic"
        "game", "games", "gaming" -> "game"
        "board_game", "boardgame", "boardgames" -> "boardgame"
        "music", "album", "artist" -> "music"
        "podcast", "podcasts" -> "podcast"
        "season", "seasons" -> "season"
        "book", "books" -> "book"
        "movie", "movies" -> "movie"
        else -> "movie"
    }
}

fun mergeSearchResults(
    current: List<SearchResult>,
    incoming: List<SearchResult>,
): List<SearchResult> {
    val seen = current
        .asSequence()
        .map { result -> "${result.source}|${result.mediaId}" }
        .toMutableSet()
    val newResults = incoming.filter { result ->
        seen.add("${result.source}|${result.mediaId}")
    }
    return current + newResults
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val repository: FloppyRepository = FloppyRepository(settingsStore),
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _recentSearches = MutableStateFlow(settingsStore.recentSearches())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private var currentQuery: String = ""
    private var currentMediaType: String = "movie"
    private var currentSource: String? = null
    private var isLoadingMore = false
    private var searchJob: Job? = null

    private val sourceFallbacks = mapOf(
        "movie" to listOf("tmdb"),
        "tv" to listOf("tmdb", "tvdb"),
        "anime" to listOf("mal", "tmdb"),
        "manga" to listOf("mal", "mangaupdates"),
        "game" to listOf("igdb"),
        "book" to listOf("openlibrary", "hardcover"),
        "comic" to listOf("comicvine"),
        "boardgame" to listOf("bgg"),
        "music" to listOf("musicbrainz"),
        "podcast" to listOf("pocketcasts", "gpodder", "audiobookshelf"),
    )

    fun searchMedia(
        query: String,
        mediaType: String = currentMediaType,
        limit: Int = 20,
        offset: Int = 0,
        source: String? = null,
        append: Boolean = false,
    ) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            searchJob?.cancel()
            isLoadingMore = false
            _uiState.value = SearchUiState.Idle
            return
        }

        val resolvedMediaType = normalizeMediaType(mediaType)
        currentQuery = trimmedQuery
        currentMediaType = resolvedMediaType
        currentSource = source

        if (!append) {
            settingsStore.pushRecentSearch(trimmedQuery)
            _recentSearches.value = settingsStore.recentSearches()
        }

        if (!append) {
            isLoadingMore = false
            searchJob?.cancel()
        }

        if (append && isLoadingMore) return
        if (append) isLoadingMore = true

        val existing = (uiState.value as? SearchUiState.Success)?.response?.results ?: emptyList()
        _uiState.value = SearchUiState.Loading
        searchJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                val service = repository.service()
                val attempts = if (source.isNullOrBlank()) {
                    sourceFallbacks[resolvedMediaType].orEmpty()
                } else {
                    listOf(source)
                }

                val attemptSources = if (attempts.isEmpty()) listOf<String?>(null) else attempts
                val (usedSource, response) = coroutineScope {
                    val completions = Channel<Pair<String?, Result<SearchResponse>>>(attemptSources.size)
                    val jobs = attemptSources.map { attemptSource ->
                        async {
                            val result = runCatching {
                                service.searchMedia(
                                    mediaType = resolvedMediaType,
                                    query = trimmedQuery,
                                    limit = limit,
                                    offset = offset,
                                    source = attemptSource,
                                )
                            }
                            if (result.exceptionOrNull() is CancellationException) throw result.exceptionOrNull()!!
                            completions.send(attemptSource to result)
                        }
                    }
                    try {
                        var lastError: Throwable? = null
                        repeat(attemptSources.size) {
                            val (attemptSource, result) = completions.receive()
                            if (result.isSuccess) {
                                jobs.forEach { it.cancel() }
                                return@coroutineScope attemptSource to result.getOrThrow()
                            }
                            val error = result.exceptionOrNull()
                            if (error is Exception && !isRetryableSearchError(error)) throw error
                            lastError = error
                        }
                        throw (lastError ?: IllegalStateException("Search failed"))
                    } finally {
                        jobs.forEach { it.cancel() }
                        completions.close()
                    }
                }

                currentSource = usedSource

                val merged = if (append) mergeSearchResults(existing, response.results) else response.results
                val combinedResponse = response.copy(results = merged)
                _uiState.value = SearchUiState.Success(combinedResponse)
                isLoadingMore = false
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.value = SearchUiState.Error("Search timed out across provider fallbacks. Try a different query.")
                isLoadingMore = false
            } catch (_: CancellationException) {
                // User typed again or changed filters; ignore cancelled request.
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error(e.message ?: "Search failed")
                isLoadingMore = false
            }
        }
    }

    private fun isRetryableSearchError(error: Exception): Boolean {
        return error is SocketTimeoutException ||
            error is InterruptedIOException ||
            error is TimeoutCancellationException
    }

    fun refreshSearch() {
        searchMedia(
            query = currentQuery,
            mediaType = currentMediaType,
            source = currentSource,
            offset = 0,
            append = false,
        )
    }

    fun loadNextPage(limit: Int = 20) {
        val state = uiState.value as? SearchUiState.Success ?: return
        val currentResponse = state.response
        if (currentResponse.pagination.next == null) return

        searchMedia(
            query = currentQuery,
            mediaType = currentMediaType,
            limit = limit,
            offset = nextPageOffset(currentResponse),
            source = currentSource,
            append = true,
        )
    }

    fun clearRecents() {
        settingsStore.clearRecentSearches()
        _recentSearches.value = emptyList()
    }

    fun loadPage(page: Int, limit: Int) {
        if (currentQuery.isBlank()) return
        val safeLimit = if (limit <= 0) 20 else limit
        val safePage = if (page < 1) 1 else page
        val offset = (safePage - 1) * safeLimit
        searchMedia(
            query = currentQuery,
            mediaType = currentMediaType,
            limit = safeLimit,
            offset = offset,
            source = currentSource,
            append = false,
        )
    }
}

