package io.github.catadoxy.floppy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.github.catadoxy.floppy.data.model.StatisticsOverview
import io.github.catadoxy.floppy.ui.components.AppEmptyState
import io.github.catadoxy.floppy.ui.components.AppScreenHeader
import io.github.catadoxy.floppy.viewmodel.StatisticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val viewModel: StatisticsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val overview = uiState.overview

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppScreenHeader(title = "Statistics") {
            IconButton(onClick = { viewModel.forceRefresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        PullToRefreshBox(
            isRefreshing = uiState.loading,
            onRefresh = { viewModel.forceRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (overview.isEmpty) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        AppEmptyState(
                            text = if (uiState.loading) "Loading statistics…" else uiState.error ?: "No statistics available.",
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                }
            } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { SummaryRow(overview) }

                val showCounts = overview.countsByType.filter { !it.label.contains("movie", ignoreCase = true) }
                val movieCounts = overview.countsByType.filter { it.label.contains("movie", ignoreCase = true) }
                val showWatchTime = overview.watchTimeByType.filter { !it.label.contains("movie", ignoreCase = true) }
                val movieWatchTime = overview.watchTimeByType.filter { it.label.contains("movie", ignoreCase = true) }

                if (showCounts.isNotEmpty()) {
                    item {
                        StatSection("Shows & series") {
                            showCounts.forEach { TypeRow(it.label, it.count) }
                        }
                    }
                }

                if (overview.statusTotals.isNotEmpty()) {
                    item {
                        StatSection("By status") {
                            overview.statusTotals.forEach { StatusRow(it.label, it.total) }
                        }
                    }
                }

                if (showWatchTime.isNotEmpty()) {
                    item {
                        StatSection("Shows & series watch time") {
                            showWatchTime.forEach { TimeRow(it.label, it.value) }
                        }
                    }
                }

                if (movieCounts.isNotEmpty()) {
                    item {
                        StatSection("Movies") {
                            movieCounts.forEach { TypeRow(it.label, it.count) }
                        }
                    }
                }

                if (movieWatchTime.isNotEmpty()) {
                    item {
                        StatSection("Movies watch time") {
                            movieWatchTime.forEach { TimeRow(it.label, it.value) }
                        }
                    }
                }

                if (overview.currentStreak != null || overview.longestStreak != null || overview.mostActiveDay != null) {
                    item {
                        StatSection("Activity") {
                            overview.mostActiveDay?.let { ValueRow("Most active day", it) }
                            overview.currentStreak?.let { ValueRow("Current streak", dayCount(it)) }
                            overview.longestStreak?.let { ValueRow("Longest streak", dayCount(it)) }
                            overview.longestStreakRange?.let { ValueRow("", it) }
                        }
                    }
                }

                if (overview.averageScore != null || (overview.scoredCount ?: 0) > 0) {
                    item {
                        StatSection("Scores") {
                            overview.averageScore?.let {
                                ValueRow("Average score", String.format(java.util.Locale.US, "%.2f", it))
                            }
                            overview.scoredCount?.let { ValueRow("Rated items", it.toString()) }
                        }
                    }
                }

                val showMostWatched = overview.mostWatched.filter { it.mediaType.lowercase() != "movie" }
                val movieMostWatched = overview.mostWatched.filter { it.mediaType.lowercase() == "movie" }
                if (showMostWatched.isNotEmpty()) {
                    item {
                        Text(
                            text = "Most watched shows",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        )
                    }
                    items(
                        items = showMostWatched,
                        key = { "${it.mediaType}|${it.title}" },
                    ) { entry ->
                        MostWatchedRow(entry)
                    }
                }
                if (movieMostWatched.isNotEmpty()) {
                    item {
                        Text(
                            text = "Most watched movies",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                        )
                    }
                    items(
                        items = movieMostWatched,
                        key = { "${it.mediaType}|${it.title}" },
                    ) { entry ->
                        MostWatchedRow(entry)
                    }
                }

                uiState.error?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun SummaryRow(overview: StatisticsOverview) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryTile("Items", overview.totalItems.toString(), Modifier.weight(1f))
        SummaryTile(
            "Completed",
            (overview.statusTotals.firstOrNull { it.label == "Completed" }?.total ?: 0).toString(),
            Modifier.weight(1f),
        )
        SummaryTile(
            "Hours",
            overview.totalHours?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "—",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
    }
}

@Composable
private fun TypeRow(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Same layout as [TypeRow] but with a preformatted value ("5764h 40min"). */
@Composable
private fun TimeRow(label: String, value: String) = ValueRow(label, value)

private fun dayCount(days: Int): String = if (days == 1) "1 day" else "$days days"

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusRow(label: String, count: Int) = TypeRow(label, count)

@Composable
private fun MostWatchedRow(entry: StatisticsOverview.MostWatched) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = entry.image,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(44.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    entry.plays.takeIf { it > 0 }?.let { "$it episodes" },
                    entry.duration,
                ).joinToString(" \u00b7 "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

