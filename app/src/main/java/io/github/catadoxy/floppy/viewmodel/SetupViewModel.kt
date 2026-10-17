package io.github.catadoxy.floppy.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.catadoxy.floppy.data.SettingsStore
import io.github.catadoxy.floppy.data.repository.FloppyRepository
import javax.inject.Inject
import io.github.catadoxy.floppy.data.network.BaseUrlNormalizer
import io.github.catadoxy.floppy.data.network.ConnectionCheck
import io.github.catadoxy.floppy.data.network.connectionErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SetupUiState {
    data object Idle : SetupUiState
    data object Testing : SetupUiState
    data object Success : SetupUiState
    data class Error(val message: String) : SetupUiState
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val repository: FloppyRepository = FloppyRepository(settingsStore),
) : ViewModel() {

    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    var baseUrl: String = settingsStore.baseUrl.orEmpty()
    var apiToken: String = settingsStore.apiToken.orEmpty()

    /**
     * Saves settings, then runs ConnectionCheck.verify(service) to
     * confirm the token + base URL actually work end-to-end - including
     * that Authelia isn't intercepting the request before it reaches
     * Floppy. A redirect-to-login HTML page instead of JSON is exactly
     * what you'd see if the Authelia bypass rule isn't in place yet.
     */
    fun saveAndTest() {
        val normalizedBaseUrl = try {
            BaseUrlNormalizer.normalize(baseUrl)
        } catch (e: IllegalArgumentException) {
            // Keep what the user typed on screen so they can correct it.
            baseUrl = baseUrl.trim()
            _uiState.value = SetupUiState.Error(e.message ?: "That server URL isn't valid.")
            return
        }

        baseUrl = normalizedBaseUrl
        settingsStore.baseUrl = normalizedBaseUrl
        settingsStore.apiToken = apiToken.trim()

        _uiState.value = SetupUiState.Testing
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = repository.service()
                    _uiState.value = when (val outcome = ConnectionCheck.verify(service)) {
                        is ConnectionCheck.Outcome.Ok -> SetupUiState.Success
                        is ConnectionCheck.Outcome.Failed ->
                            SetupUiState.Error(connectionErrorMessage(outcome))
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SetupUiState.Error(e.message ?: "Connection failed")
            }
        }
    }
}

