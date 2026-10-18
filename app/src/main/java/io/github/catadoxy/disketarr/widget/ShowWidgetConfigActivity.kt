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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ShowWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigScreen(
                        onSave = { type, sort, theme, bg, largeFont ->
                            val prefs = getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("type_$appWidgetId", type)
                                .putString("sort_$appWidgetId", sort)
                                .putString("theme_$appWidgetId", theme)
                                .putInt("bg_$appWidgetId", bg)
                                .putBoolean("largefont_$appWidgetId", largeFont)
                                .apply()

                            val updateIntent = Intent(this@ShowWidgetConfigActivity, ShowWidgetProvider::class.java).apply {
                                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            sendBroadcast(updateIntent)

                            val request = OneTimeWorkRequestBuilder<ShowWidgetUpdateWorker>().build()
                            WorkManager.getInstance(this@ShowWidgetConfigActivity).enqueue(request)

                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
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
private fun ConfigScreen(onSave: (String, String, String, Int, Boolean) -> Unit) {
    var type by remember { mutableStateOf("upcoming") }
    var sort by remember { mutableStateOf("latest") }
    var theme by remember { mutableStateOf("system") }
    var bg by remember { mutableStateOf(100) }
    var largeFont by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
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
    val themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
    val backgrounds = listOf(0 to "Transparent", 25 to "25%", 50 to "50%", 75 to "75%", 100 to "Opaque")

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        Button(onClick = { onSave(type, sort, theme, bg, largeFont) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}