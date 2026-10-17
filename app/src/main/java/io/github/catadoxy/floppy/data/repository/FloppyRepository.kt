package io.github.catadoxy.floppy.data.repository

import io.github.catadoxy.floppy.data.SettingsStore
import io.github.catadoxy.floppy.data.network.ApiClient
import io.github.catadoxy.floppy.data.network.FloppyApiService
import javax.inject.Inject

/**
 * Single seam for Floppy network access. ViewModels depend on this instead
 * of reaching into [ApiClient] directly, which makes the service replaceable
 * in tests without touching global state.
 */
class FloppyRepository @Inject constructor(
    val settingsStore: SettingsStore,
) {
    fun service(): FloppyApiService = ApiClient.getService(settingsStore)

    fun resetApiClient() = ApiClient.reset()
}