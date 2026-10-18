package io.github.catadoxy.disketarr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import java.util.concurrent.TimeUnit
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.catadoxy.disketarr.MainActivity

import io.github.catadoxy.disketarr.data.model.LibraryMediaTypes
import io.github.catadoxy.disketarr.data.network.BaseUrlNormalizer
import io.github.catadoxy.disketarr.ui.theme.ThemeCatalog
import io.github.catadoxy.disketarr.viewmodel.SettingsUiState
import io.github.catadoxy.disketarr.viewmodel.SettingsViewModel
import io.github.catadoxy.disketarr.workers.UpcomingReminderWorker

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onResetToSetup: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()

    var selectedCategories by remember { mutableStateOf(viewModel.settingsStore.selectedCategories()) }

    LaunchedEffect(Unit) { viewModel.checkAvailableCategories() }
    LaunchedEffect(availableCategories) {
        selectedCategories = selectedCategories.intersect(availableCategories)
        if (selectedCategories.isEmpty()) selectedCategories = availableCategories
        viewModel.settingsStore.saveSelectedCategories(selectedCategories)
    }

    var baseUrl by remember { mutableStateOf(viewModel.baseUrl) }
    var apiToken by remember { mutableStateOf(viewModel.apiToken) }
    var hideSpecials by remember { mutableStateOf(viewModel.settingsStore.hideSpecialEpisodes) }
    var episodeReminders by remember { mutableStateOf(viewModel.settingsStore.episodeRemindersEnabled) }
    var leadDays by remember { mutableStateOf((viewModel.settingsStore.reminderLeadMinutes / 1440).toString()) }
    var leadHours by remember { mutableStateOf(((viewModel.settingsStore.reminderLeadMinutes % 1440) / 60).toString()) }
    var leadMinutes by remember { mutableStateOf((viewModel.settingsStore.reminderLeadMinutes % 60).toString()) }
    val updateLeadTime = {
        val d = leadDays.toIntOrNull() ?: 0
        val h = leadHours.toIntOrNull() ?: 0
        val m = leadMinutes.toIntOrNull() ?: 0
        viewModel.settingsStore.reminderLeadMinutes = d * 1440 + h * 60 + m
    }
    var savedServers by remember { mutableStateOf(viewModel.settingsStore.savedServers()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showLeadTimeDialog by remember { mutableStateOf(false) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var selectedThemeId by remember { mutableStateOf(viewModel.settingsStore.selectedThemeId) }

    LaunchedEffect(uiState) {
        // Reflect the normalized URL (scheme filled in, trailing slash trimmed)
        // back into the field so it's clear what was actually saved.
        if (uiState !is SettingsUiState.Testing) {
            baseUrl = viewModel.baseUrl
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { showLoginDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Login")
                }

                if (showLoginDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showLoginDialog = false },
                        title = { Text("Login") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = baseUrl,
                                    onValueChange = {
                                        baseUrl = it
                                        viewModel.baseUrl = it
                                    },
                                    label = { Text("Floppy base URL") },
                                    placeholder = { Text("192.168.1.10:8000 or https://floppy.example.com") },
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = apiToken,
                                    onValueChange = {
                                        apiToken = it
                                        viewModel.apiToken = it
                                    },
                                    label = { Text("API token") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.saveAndTest()
                                showLoginDialog = false
                            }) { Text("Save & Test") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLoginDialog = false }) { Text("Cancel") }
                        },
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.runDiagnostics() },
                    enabled = uiState !is SettingsUiState.DiagnosticsLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState is SettingsUiState.DiagnosticsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondary,
                        )
                    }
                    Text(if (uiState is SettingsUiState.DiagnosticsLoading) "Loading..." else "Permissions & Diagnostics")
                }

            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Hide special episodes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = hideSpecials,
                        onCheckedChange = {
                            hideSpecials = it
                            viewModel.settingsStore.hideSpecialEpisodes = it
                            viewModel.clearCache()
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Episode reminders (daily)",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = episodeReminders,
                        onCheckedChange = {
                            episodeReminders = it
                            viewModel.settingsStore.episodeRemindersEnabled = it
                            val wm = WorkManager.getInstance(context)
                            if (it) {
                                val req = PeriodicWorkRequestBuilder<UpcomingReminderWorker>(1, TimeUnit.DAYS).build()
                                wm.enqueueUniquePeriodicWork("Disketarr_upcoming", ExistingPeriodicWorkPolicy.UPDATE, req)
                                showLeadTimeDialog = true
                            } else {
                                wm.cancelUniqueWork("Disketarr_upcoming")
                            }
                        },
                    )
                }

                if (episodeReminders) {
                    Text(
                        text = "Lead time: ${leadDays.ifBlank { "0" }}d ${leadHours.ifBlank { "0" }}h ${leadMinutes.ifBlank { "0" }}m (tap to edit)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { showLeadTimeDialog = true },
                    )
                }

                if (showLeadTimeDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showLeadTimeDialog = false },
                        title = { Text("Reminder lead time") },
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = leadDays,
                                    onValueChange = { leadDays = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Days") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = leadHours,
                                    onValueChange = { leadHours = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Hours") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = leadMinutes,
                                    onValueChange = { leadMinutes = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Minutes") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                updateLeadTime()
                                showLeadTimeDialog = false
                            }) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLeadTimeDialog = false }) { Text("Cancel") }
                        },
                    )
                }

            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Button(
                    onClick = { showCategoriesDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Categories")
                }

                if (showCategoriesDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showCategoriesDialog = false },
                        title = { Text("Categories") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LibraryMediaTypes.options.filter { it.first in setOf("movie", "tv", "anime") }.forEach { (key, label) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = key in selectedCategories,
                                            onCheckedChange = { checked ->
                                                val updated = selectedCategories.toMutableSet()
                                                if (checked) updated += key else updated -= key
                                                selectedCategories = updated
                                                viewModel.settingsStore.saveSelectedCategories(updated)
                                            },
                                        )
                                        Text(label, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showCategoriesDialog = false }) { Text("Done") }
                        },
                    )
                }

            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Saved servers",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    savedServers.forEach { server ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                baseUrl = server.baseUrl
                                apiToken = server.apiToken
                                viewModel.baseUrl = server.baseUrl
                                viewModel.apiToken = server.apiToken
                            },
                        ) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable {
                            try {
                                val normalized = BaseUrlNormalizer.normalize(baseUrl)
                                viewModel.settingsStore.saveServer(
                                    name = baseUrl,
                                    baseUrl = normalized,
                                    apiToken = apiToken,
                                )
                                savedServers = viewModel.settingsStore.savedServers()
                            } catch (e: IllegalArgumentException) {
                                // ignore; Login dialog reports URL errors
                            }
                        },
                    ) {
                        Text(
                            text = "Save current server",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }

            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                // One row per theme with its name and a colour swatch, rather than
                // a cramped grid of initials.
                var themeMenuOpen by remember { mutableStateOf(false) }
                val currentTheme = remember(selectedThemeId) {
                    ThemeCatalog.options.firstOrNull { it.id == selectedThemeId }
                        ?: ThemeCatalog.options.first()
                }

                OutlinedButton(
                    onClick = { themeMenuOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(currentTheme.previewColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Text(
                        text = currentTheme.label,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    Text("\u25BE")
                }

                DropdownMenu(
                    expanded = themeMenuOpen,
                    onDismissRequest = { themeMenuOpen = false },
                ) {
                    ThemeCatalog.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(option.previewColor)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        ),
                                )
                            },
                            trailingIcon = {
                                if (option.id == selectedThemeId) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                            onClick = {
                                themeMenuOpen = false
                                selectedThemeId = option.id
                                (context as MainActivity).updateTheme(option.id)
                            },
                        )
                    }
                }
            }
        }

        when (val state = uiState) {
            is SettingsUiState.Success -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            is SettingsUiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            is SettingsUiState.Diagnostics -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = state.output,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            else -> Unit
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { showResetDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Text("Reset configuration", color = MaterialTheme.colorScheme.onError)
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset configuration?") },
                text = { Text("This clears your saved server, token, and cached data. You will need to set up again.") },
                confirmButton = {
                    TextButton(onClick = {
                        showResetDialog = false
                        viewModel.clearSettings()
                        onResetToSetup()
                    }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
                },
            )
        }
    }
}


