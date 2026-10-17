package io.github.catadoxy.disketarr.data.repository

import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.network.ApiClient
import io.github.catadoxy.disketarr.data.network.DisketarrApiService
import javax.inject.Inject

/**
 * Single seam for Disketarr network access. ViewModels depend on this instead
 * of reaching into [ApiClient] directly, which makes the service replaceable
 * in tests without touching global state.
 */
class DisketarrRepository @Inject constructor(
    val settingsStore: SettingsStore,
) {
    fun service(): DisketarrApiService = ApiClient.getService(settingsStore)

    fun resetApiClient() = ApiClient.reset()
}