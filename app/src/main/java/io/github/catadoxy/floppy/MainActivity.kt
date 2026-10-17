package io.github.catadoxy.floppy

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
import io.github.catadoxy.floppy.data.SettingsStore
import io.github.catadoxy.floppy.ui.navigation.FloppyNavHost
import io.github.catadoxy.floppy.ui.theme.FloppyClientTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore

    private val _themeState = MutableStateFlow("")
    val themeState: StateFlow<String> = _themeState.asStateFlow()

    private var pendingDeepLink by mutableStateOf<Uri?>(null)

    fun updateTheme(themeId: String) {
        settingsStore.selectedThemeId = themeId
        _themeState.value = themeId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _themeState.value = settingsStore.selectedThemeId
        pendingDeepLink = intent?.data

        setContent {
            val currentThemeId by themeState.collectAsState()
            FloppyClientTheme(themeId = currentThemeId) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FloppyNavHost(
                        settingsStore = settingsStore,
                        deepLink = pendingDeepLink,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = intent.data
    }
}