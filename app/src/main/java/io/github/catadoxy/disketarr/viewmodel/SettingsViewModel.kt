package io.github.catadoxy.disketarr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catadoxy.disketarr.data.AppCacheRepository
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.model.LibraryMediaTypes
import javax.inject.Inject
import io.github.catadoxy.disketarr.data.repository.DisketarrRepository
import io.github.catadoxy.disketarr.data.network.BaseUrlNormalizer
import io.github.catadoxy.disketarr.data.network.ConnectionCheck
import io.github.catadoxy.disketarr.data.network.connectionErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsUiState {
    data object Idle : SettingsUiState
    data object Testing : SettingsUiState
    data object DiagnosticsLoading : SettingsUiState
    data class Success(val message: String) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
    data class Diagnostics(val output: String) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settingsStore: SettingsStore,
    val repository: DisketarrRepository = DisketarrRepository(settingsStore),
    val appCache: AppCacheRepository = AppCacheRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Idle)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _availableCategories = MutableStateFlow(settingsStore.availableCategories())
    val availableCategories: StateFlow<Set<String>> = _availableCategories.asStateFlow()

    var baseUrl: String = settingsStore.baseUrl.orEmpty()
    var apiToken: String = settingsStore.apiToken.orEmpty()

    fun saveAndTest() {
        val normalizedBaseUrl = try {
            BaseUrlNormalizer.normalize(baseUrl)
        } catch (e: IllegalArgumentException) {
            // Keep what the user typed on screen so they can correct it.
            baseUrl = baseUrl.trim()
            _uiState.value = SettingsUiState.Error(e.message ?: "That server URL isn't valid.")
            return
        }

        baseUrl = normalizedBaseUrl
        settingsStore.baseUrl = normalizedBaseUrl
        settingsStore.apiToken = apiToken.trim()

        _uiState.value = SettingsUiState.Testing
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = repository.service()
                    _uiState.value = when (val outcome = ConnectionCheck.verify(service)) {
                        is ConnectionCheck.Outcome.Ok ->
                            SettingsUiState.Success("Connection verified.")
                        is ConnectionCheck.Outcome.Failed ->
                            SettingsUiState.Error(connectionErrorMessage(outcome))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SettingsUiState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun clearSettings() {
        settingsStore.clear()
        appCache.clear()
        repository.resetApiClient()
        baseUrl = ""
        apiToken = ""
        _uiState.value = SettingsUiState.Success("Saved credentials cleared.")
    }
    fun clearCache() = appCache.clear()

    fun checkAvailableCategories() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!settingsStore.isConfigured) return@withContext
                val service = repository.service()
                val available = mutableSetOf<String>()
                for (type in LibraryMediaTypes.ALL) {
                    val ok = runCatching {
                        service.searchMedia(mediaType = type, query = "a", limit = 1)
                    }.isSuccess
                    if (ok) available += type
                }
                val finalAvailable = if (available.isEmpty()) LibraryMediaTypes.ALL.toSet() else available
                settingsStore.saveAvailableCategories(finalAvailable)
                _availableCategories.value = finalAvailable
                // Keep selection within available.
                val selected = settingsStore.selectedCategories().intersect(available)
                settingsStore.saveSelectedCategories(if (selected.isEmpty()) available else selected)
            }
        }
    }


    fun runDiagnostics() {
        _uiState.value = SettingsUiState.DiagnosticsLoading
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                val service = repository.service()
                val output = buildString {
                    val token = settingsStore.apiToken.orEmpty()
                    append("\n=== DIAGNOSTICS ===")
                    append("\nServer: ")
                    append(settingsStore.baseUrl.orEmpty().ifBlank { "(not set)" })
                    append("\nToken: ")
                    append(
                        when {
                            token.isBlank() -> "(not set)"
                            token.length <= 8 -> "****"
                            else -> "${token.take(4)}…${token.takeLast(3)} (${token.length} chars)"
                        },
                    )
                    append("\n\nEach line lists the scope Floppy requires. A 403 means the")
                    append("\ntoken is missing that scope, not that the server is broken.")
                    append("\n")

                    // Label -> the scope the route needs. `/user/preferences/`
                    // is listed even though the app no longer calls it, because a
                    // 403 there is expected with an app token and used to look
                    // like a failure.
                    val probes: List<Triple<String, String, suspend () -> Int>> = listOf(
                        Triple("GET /api/v1/info/", "any scope", suspend { service.getServerInfo().code() }),
                        Triple("GET /api/v1/media/ ?limit=1", "watchlist:read", suspend { service.listTrackedMediaRaw(limit = 1).let { 200 } }),
                        Triple("GET /api/v1/media/tv/ ?limit=1", "watchlist:read", suspend { service.listTrackedMediaByTypeRaw(mediaType = "tv", limit = 1).let { 200 } }),
                        Triple("GET /api/v1/history/ ?limit=1", "watchlist:read", suspend { service.listHistoryRaw(limit = 1).let { 200 } }),
                        Triple("GET /api/v1/lists/", "lists:read", suspend { service.getLists().let { 200 } }),
                        Triple("GET /api/v1/search/movie/", "catalog:read", suspend { service.searchMedia(mediaType = "movie", query = "test", limit = 1, offset = 0).let { 200 } }),
                        Triple("GET /api/v1/statistics/overview/", "statistics:read", suspend { service.getStatisticsOverview().let { 200 } }),
                        Triple("GET /api/v1/user/preferences/", "user:read (unused by this app)", suspend { service.getUserPreferences().code() }),
                    )

                    probes.forEach { (label, scope, call) ->
                        append("\n")
                        append(label)
                        append("  [")
                        append(scope)
                        append("]\n    ")
                        val result = runCatching { call() }
                        val code = result.getOrNull()
                        if (code != null) {
                            append(if (code in 200..299) "OK" else "HTTP $code")
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is retrofit2.HttpException) {
                                append("HTTP ${error.code()}")
                            } else {
                                append(error?.javaClass?.simpleName ?: "failed")
                                append(": ")
                                append(error?.message)
                            }
                        }
                    }
                }

                _uiState.value = SettingsUiState.Diagnostics(output)
                }
            } catch (e: Exception) {
                _uiState.value = SettingsUiState.Error("Diagnostics failed: ${e.message}")
            }
        }
    }
}



