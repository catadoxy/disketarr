package io.github.catadoxy.disketarr.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ShowWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    /**
     * True when the launcher started this screen to place a widget.
     *
     * The launcher runs the config activity in its own task and expects the
     * widget id back as the activity's result. The widget's own configure
     * button starts the same activity in the app's task instead - there is no
     * result to deliver, and the home screen is where the user wants to end
     * up.
     */
    private val startedByLauncher: Boolean
        get() = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        setContent {
            // Follow the phone's light/dark setting; the widget's own theme
            // preference only affects the widget, not this screen.
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigScreen(
                        initialType = prefs.getString("type_$appWidgetId", "upcoming") ?: "upcoming",
                        initialSort = prefs.getString("sort_$appWidgetId", "latest") ?: "latest",
initialFilter = prefs.getString("filter_$appWidgetId", "active") ?: "active",
initialPosterSize = prefs.getString("postersize_$appWidgetId", "medium") ?: "medium",
                        initialTheme = prefs.getString("theme_$appWidgetId", "system") ?: "system",
                        initialBg = prefs.getInt("bg_$appWidgetId", 100),
                        initialLargeFont = prefs.getBoolean("largefont_$appWidgetId", false),
                        onSave = { type, sort, filter, posterSize, theme, bg, largeFont ->
                            val committed = prefs.edit()
                                .putString("type_$appWidgetId", type)
                                .putString("sort_$appWidgetId", sort)
                                 .putString("filter_$appWidgetId", filter)
                                  .putString("postersize_$appWidgetId", posterSize)
                                .putString("theme_$appWidgetId", theme)
                                .putInt("bg_$appWidgetId", bg)
                                .putBoolean("largefont_$appWidgetId", largeFont)
                                .commit()

                            android.widget.Toast.makeText(
                                this@ShowWidgetConfigActivity,
                                if (committed) "Saved" else "Save failed",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()

                            // Apply the new settings immediately. This also re-binds the
                            // collection so the list reloads with the new type/sort.
                            ShowWidgetUpdater.update(applicationContext, appWidgetId)

                            // The launcher started this screen for the widget's
                            // result, so the result has to go back with finish().
                            // finishAffinity() cannot deliver a result - it
                            // throws, and the launcher then drops the widget
                            // because it never received an id.
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                            )
                            if (!startedByLauncher) {
                                // The widget's own configure button: go back to
                                // the home screen instead of the app's main
                                // screen, which is the base of this task.
                                moveTaskToBack(true)
                            }
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    initialType: String,
    initialSort: String,
    initialFilter: String,
    initialPosterSize: String,
    initialTheme: String,
    initialBg: Int,
    initialLargeFont: Boolean,
    onSave: (String, String, String, String, String, Int, Boolean) -> Unit,
) {
    var type by remember { mutableStateOf(initialType) }
    var sort by remember { mutableStateOf(initialSort) }
    var filter by remember { mutableStateOf(initialFilter) }
    var posterSize by remember { mutableStateOf(initialPosterSize) }
    var theme by remember { mutableStateOf(initialTheme) }
    var bg by remember { mutableStateOf(initialBg) }
    var largeFont by remember { mutableStateOf(initialLargeFont) }
    var typeExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var filterExpanded by remember { mutableStateOf(false) }
    var posterSizeExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var bgExpanded by remember { mutableStateOf(false) }

    val types = listOf("upcoming" to "Upcoming", "recent" to "Recent", "shows" to "Shows")
    val sorts = listOf(
        "latest" to "Latest episode",
        "oldest" to "Oldest episode",
        "title" to "Title",
        "lastwatched" to "Last watched",
        "remaining" to "Remaining episodes",
    )
    val filters = listOf(
        "active" to "In progress + planned",
        "inprogress" to "In progress only",
        "planned" to "Planned only",
        "all" to "All (incl. completed)",
    )
    val posterSizes = listOf("small" to "Small", "medium" to "Medium", "large" to "Large")
    val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    val backgrounds = listOf(0 to "Transparent", 25 to "25%", 50 to "50%", 75 to "75%", 100 to "Opaque")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Widget configuration", style = MaterialTheme.typography.titleLarge)

        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
            OutlinedTextField(
                value = types.first { it.first == type }.second,
                onValueChange = {},
                readOnly = true,
                label = { Text("Widget type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                types.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { type = value; typeExpanded = false },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(expanded = sortExpanded, onExpandedChange = { sortExpanded = it }) {
            OutlinedTextField(
                value = sorts.first { it.first == sort }.second,
                onValueChange = {},
                readOnly = true,
                label = { Text("Sort by") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                sorts.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { sort = value; sortExpanded = false },
                    )
                }
            }
        }

        if (type == "shows") {
            ExposedDropdownMenuBox(expanded = filterExpanded, onExpandedChange = { filterExpanded = it }) {
                OutlinedTextField(
                    value = filters.first { it.first == filter }.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Show") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    filters.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { filter = value; filterExpanded = false },
                        )
                    }
                }
            }
        }

        ExposedDropdownMenuBox(expanded = posterSizeExpanded, onExpandedChange = { posterSizeExpanded = it }) {
            OutlinedTextField(
                value = posterSizes.first { it.first == posterSize }.second,
                onValueChange = {},
                readOnly = true,
                label = { Text("Poster size") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = posterSizeExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(expanded = posterSizeExpanded, onDismissRequest = { posterSizeExpanded = false }) {
                posterSizes.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { posterSize = value; posterSizeExpanded = false },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(expanded = themeExpanded, onExpandedChange = { themeExpanded = it }) {
            OutlinedTextField(
                value = themes.first { it.first == theme }.second,
                onValueChange = {},
                readOnly = true,
                label = { Text("Theme") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                themes.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { theme = value; themeExpanded = false },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(expanded = bgExpanded, onExpandedChange = { bgExpanded = it }) {
            OutlinedTextField(
                value = backgrounds.first { it.first == bg }.second,
                onValueChange = {},
                readOnly = true,
                label = { Text("Background opacity") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bgExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(expanded = bgExpanded, onDismissRequest = { bgExpanded = false }) {
                backgrounds.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { bg = value; bgExpanded = false },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Large font")
            androidx.compose.material3.Switch(
                checked = largeFont,
                onCheckedChange = { largeFont = it },
            )
        }

        Button(onClick = { onSave(type, sort, filter, posterSize, theme, bg, largeFont) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}