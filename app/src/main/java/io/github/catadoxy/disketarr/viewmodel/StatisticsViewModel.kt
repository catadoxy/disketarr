package io.github.catadoxy.disketarr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.catadoxy.disketarr.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catadoxy.disketarr.data.AppCacheRepository
import io.github.catadoxy.disketarr.data.model.StatisticsOverview
import javax.inject.Inject
import io.github.catadoxy.disketarr.data.network.DisketarrApiService
import io.github.catadoxy.disketarr.data.repository.DisketarrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import kotlinx.coroutines.withTimeoutOrNull

data class StatisticsUiState(
    val overview: StatisticsOverview = StatisticsOverview(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val repository: DisketarrRepository = DisketarrRepository(settingsStore),
    private val appCache: AppCacheRepository = AppCacheRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun forceRefresh() = load(force = true)

    fun load(force: Boolean = false) {
        if (!force) {
            appCache.statistics()?.let { cached ->
                _uiState.value = StatisticsUiState(overview = cached.value, loading = false)
                if (appCache.isFresh(cached.at)) return
            }
        }

        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { fetch() }
            result.fold(
                onSuccess = { overview ->
                    appCache.putStatistics(overview)
                    _uiState.value = StatisticsUiState(overview = overview, loading = false)
                },
                onFailure = { error ->
                    val message = if (error is HttpException && error.code() == 403) {
                        "Missing statistics:read permission for this API token."
                    } else {
                        error.message ?: "Could not load statistics"
                    }
                    // Keep whatever we already had on screen.
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = message,
                    )
                },
            )
        }
    }

    private suspend fun fetch(): Result<StatisticsOverview> = runCatching {
        val service: DisketarrApiService = repository.service()
        val payload = withTimeoutOrNull(30000) { service.getStatisticsOverview() }
            ?: error("Statistics request timed out")
        payload.asStatisticsOverview()
    }
}


