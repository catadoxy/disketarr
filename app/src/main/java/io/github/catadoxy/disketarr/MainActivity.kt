package io.github.catadoxy.disketarr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import io.github.catadoxy.disketarr.data.AppCacheRepository
import io.github.catadoxy.disketarr.data.SettingsStore
import io.github.catadoxy.disketarr.data.repository.DisketarrRepository
import io.github.catadoxy.disketarr.ui.navigation.DisketarrNavHost
import io.github.catadoxy.disketarr.ui.theme.DisketarrClientTheme
import io.github.catadoxy.disketarr.viewmodel.AnimeLibraryMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore

    /**
     * The shared cache.
     *
     * The ViewModels read their pages from it, so dropping the cached ones
     * here is what makes a changed Floppy setting show up on screen.
     */
    @Inject lateinit var appCache: AppCacheRepository

    private val _themeState = MutableStateFlow("")
    val themeState: StateFlow<String> = _themeState.asStateFlow()

    private var pendingDeepLink by mutableStateOf<Uri?>(null)

    fun updateTheme(themeId: String) {
        settingsStore.selectedThemeId = themeId
        _themeState.value = themeId
    }

    private fun detailUriFromIntent(intent: Intent?): Uri? {
        val source = intent?.getStringExtra("source") ?: return null
        val type = intent.getStringExtra("media_type") ?: return null
        val id = intent.getStringExtra("media_id") ?: return null
        if (source.isBlank() || type.isBlank() || id.isBlank()) return null
        return Uri.parse("disketarr://details/$source/$type/$id")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _themeState.value = settingsStore.selectedThemeId
        pendingDeepLink = intent?.data ?: detailUriFromIntent(intent)

        setContent {
            val currentThemeId by themeState.collectAsState()
            DisketarrClientTheme(themeId = currentThemeId) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DisketarrNavHost(
                        settingsStore = settingsStore,
                        deepLink = pendingDeepLink,
                    )
                }
            }
        }

        // Floppy decides whether the anime live in their own library, and with
        // it whether the app has an Anime tab. Ask the server, so a change
        // there is picked up here.
        lifecycleScope.launch {
            if (!settingsStore.isConfigured) return@launch
            val mode = runCatching {
                AnimeLibraryMode.read(DisketarrRepository(settingsStore).service())
            }.getOrNull() ?: return@launch
            if (mode == settingsStore.animeLibraryMode) return@launch
            settingsStore.animeLibraryMode = mode
            // The library lists follow the mode, so the cached ones are stale.
            appCache.invalidateBrowse()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.data ?: detailUriFromIntent(intent)
    }
}