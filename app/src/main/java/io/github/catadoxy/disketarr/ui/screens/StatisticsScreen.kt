package io.github.catadoxy.disketarr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.catadoxy.disketarr.data.model.StatisticsOverview
import io.github.catadoxy.disketarr.ui.components.ActivityHeatmap
import io.github.catadoxy.disketarr.ui.components.AppEmptyState
import io.github.catadoxy.disketarr.ui.components.AppScreenHeader
import io.github.catadoxy.disketarr.ui.components.StatsBarChart
import io.github.catadoxy.disketarr.ui.components.SwipeableTabPager
import io.github.catadoxy.disketarr.ui.components.UnderlineTabRow
import io.github.catadoxy.disketarr.viewmodel.StatisticsViewModel
import java.util.Locale

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

        if (overview.isEmpty) {
            PullToRefreshBox(
                isRefreshing = uiState.loading,
                onRefresh = { viewModel.forceRefresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        AppEmptyState(
                            text = if (uiState.loading) "Loading statistics…" else uiState.error ?: "No statistics available.",
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                }
            }
            return@Column
        }

        var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
        val types = overview.types
        val selectedIndex = types.indexOfFirst { it.type == selectedType }.coerceAtLeast(0)

        Text(
            text = overview.rangeName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
        )

        UnderlineTabRow(
            tabs = types.map { it.label },
            selectedIndex = selectedIndex,
            onSelect = { selectedType = types[it].type },
        )

        PullToRefreshBox(
            isRefreshing = uiState.loading,
            onRefresh = { viewModel.forceRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            SwipeableTabPager(
                pageCount = types.size,
                selectedIndex = selectedIndex,
                onPageSelected = { page -> selectedType = types[page].type },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                StatsPage(
                    stats = types[page],
                    overview = overview,
                    error = uiState.error,
                )
            }
        }
    }
}

@Composable
private fun StatsPage(
    stats: StatisticsOverview.TypeStats,
    overview: StatisticsOverview,
    error: String?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { KpiGrid(stats) }

        item { MonthChart(stats) }

        item { WhenYouWatch(stats) }

        item { ActivityCard(overview, stats.type) }

        if (stats.scoreBins.isNotEmpty() && stats.scoreBins.sum() > 0) {
            item { RatingsCard(stats) }
        }

        if (stats.topGenres.isNotEmpty()) {
            item { GenresCard(stats) }
        }

        if (stats.topDecades.isNotEmpty()) {
            item { DecadesCard(stats) }
        }

        if (stats.talent.isNotEmpty()) {
            item { TalentCard(stats) }
        }

        if (stats.mostWatched.isNotEmpty()) {
            item { MostWatchedCard(stats) }
        }

        if (stats.statusTotals.isNotEmpty()) {
            item { StatusCard(stats.statusTotals) }
        }

        error?.let { message ->
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

@Composable
private fun KpiGrid(stats: StatisticsOverview.TypeStats) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile(
                label = "Completed",
                value = formatCount(stats.completed),
                // Floppy counts completed seasons as well as shows, so the
                // number can exceed the tracked titles; only compare them when
                // it does not.
                hint = when {
                    stats.completed <= stats.tracked -> "of ${formatCount(stats.tracked)} tracked"
                    stats.type == "movie" -> "${formatCount(stats.tracked)} films tracked"
                    else -> "${formatCount(stats.tracked)} shows tracked"
                },
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Watch time",
                value = "${hoursText(stats.hours)} h",
                hint = "${oneDecimal(stats.hoursPerMonth)} h / month",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiTile(
                label = stats.playsLabel.replaceFirstChar { it.uppercase() },
                value = formatCount(stats.plays),
                hint = "${oneDecimal(stats.hoursPerDay)} h / day",
                modifier = Modifier.weight(1f),
            )
            KpiTile(
                label = "Average rating",
                value = stats.averageScore?.let { oneDecimal(it) } ?: "—",
                hint = if (stats.averageScore == null) "nothing rated yet" else "across the library",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthChart(stats: StatisticsOverview.TypeStats) {
    val values = stats.byMonth
    val peak = values.indices.maxByOrNull { values[it] }
    val peakValue = peak?.let { values[it] }
    SectionCard(
        title = "Watch time by month",
        subtitle = if (peak != null && (peakValue ?: 0f) > 0f) {
            "Peak: ${MONTHS.getOrNull(peak) ?: "?"} · ${hoursText(peakValue!!.toDouble())} h"
        } else {
            null
        },
    ) {
        if (values.isEmpty()) {
            EmptyHint("No plays recorded yet.")
        } else {
            StatsBarChart(
                values = values,
                labels = MONTHS,
                height = 108.dp,
                highlightIndex = peak,
            )
        }
    }
}

@Composable
private fun WhenYouWatch(stats: StatisticsOverview.TypeStats) {
    SectionCard(title = "When you watch") {
        ChartBlock(
            title = "By weekday",
            values = stats.byWeekday,
            labels = WEEKDAYS,
        )
        Spacer(Modifier.height(16.dp))
        ChartBlock(
            title = "By time of day",
            values = stats.byTimeOfDay,
            labels = HOURS,
            labelEvery = 3,
        )
    }
}

@Composable
private fun ChartBlock(
    title: String,
    values: List<Float>,
    labels: List<String>,
    labelEvery: Int = 1,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    if (values.isEmpty() || values.all { it <= 0f }) {
        EmptyHint("No plays recorded yet.")
    } else {
        StatsBarChart(
            values = values,
            labels = labels,
            height = 64.dp,
            labelEvery = labelEvery,
            highlightIndex = values.indices.maxByOrNull { values[it] },
        )
    }
}

@Composable
private fun ActivityCard(overview: StatisticsOverview, type: String) {
    val activity = overview.activity
    SectionCard(
        title = "Activity",
        subtitle = "Last 24 weeks · darker means more watched",
    ) {
        if (overview.heatmap.isNotEmpty()) {
            ActivityHeatmap(
                days = overview.heatmap,
                typeFilter = type,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
        }
        activity.currentStreak?.let { InfoRow("Current streak", dayCount(it)) }
        activity.longestStreak?.let { InfoRow("Longest streak", dayCount(it)) }
        activity.longestStreakRange?.let { InfoRow("", it) }
        activity.mostActiveDay?.let { day ->
            val percentage = activity.mostActiveDayPercentage?.let { " · $it%" }.orEmpty()
            InfoRow("Most active day", "$day$percentage")
        }
        if (overview.heatmap.isEmpty() && activity.currentStreak == null) {
            EmptyHint("No activity recorded yet.")
        }
    }
}

@Composable
private fun RatingsCard(stats: StatisticsOverview.TypeStats) {
    val bins = stats.scoreBins
    val rated = bins.sum()
    SectionCard(
        title = "Ratings",
        subtitle = buildString {
            stats.averageScore?.let { append("Average ${oneDecimal(it)} · ") }
            append("$rated rated")
        },
    ) {
        StatsBarChart(
            values = bins.map { it.toFloat() },
            labels = bins.indices.map { it.toString() },
            height = 84.dp,
            highlightIndex = bins.indices.maxByOrNull { bins[it] },
        )
    }
}

@Composable
private fun GenresCard(stats: StatisticsOverview.TypeStats) {
    val max = stats.topGenres.maxOf { it.minutes }.coerceAtLeast(1)
    SectionCard(title = "Top genres") {
        stats.topGenres.forEach { genre ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = genre.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = genre.formatted ?: "${formatCount(genre.plays)} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { (genre.minutes.toFloat() / max).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun DecadesCard(stats: StatisticsOverview.TypeStats) {
    SectionCard(title = "By decade") {
        stats.topDecades.forEach { decade ->
            InfoRow(
                label = decade.name,
                value = decade.formatted ?: "${formatCount(decade.plays)} plays",
            )
        }
    }
}

@Composable
private fun TalentCard(stats: StatisticsOverview.TypeStats) {
    val categories = stats.talent.map { it.category }.distinct()
    var selected by rememberSaveable(stats.type) { mutableStateOf(categories.first()) }
    val entries = stats.talent.filter { it.category == selected }

    SectionCard(title = "Top talent", subtitle = "Across every library") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                FilterPill(
                    text = talentLabel(category),
                    selected = category == selected,
                    onClick = { selected = category },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        entries.forEach { talent ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = talent.image,
                    contentDescription = talent.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = talent.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            talent.uniqueTitles.takeIf { it > 0 }?.let { "${formatCount(it)} titles" },
                            "${formatCount(talent.plays)} plays",
                            talent.formatted,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MostWatchedCard(stats: StatisticsOverview.TypeStats) {
    SectionCard(title = "Most watched") {
        stats.mostWatched.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            entry.plays.takeIf { it > 0 }
                                ?.let { "${formatCount(it)} ${if (stats.type == "movie") "plays" else "episodes"}" },
                            entry.durations,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(statusTotals: List<StatisticsOverview.StatusTotal>) {
    SectionCard(title = "By status") {
        statusTotals.forEach { status ->
            InfoRow(status.label, formatCount(status.total))
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), content = content)
        }
    }
}

@Composable
private fun KpiTile(
    label: String,
    value: String,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val HOURS = List(24) { hour ->
    when {
        hour == 0 -> "12a"
        hour < 12 -> "${hour}a"
        hour == 12 -> "12p"
        else -> "${hour - 12}p"
    }
}

private fun formatCount(value: Int): String = String.format(Locale.US, "%,d", value)

private fun oneDecimal(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun hoursText(value: Double): String =
    if (value >= 100.0) String.format(Locale.US, "%,.0f", value) else oneDecimal(value)

private fun dayCount(days: Int): String = if (days == 1) "1 day" else "$days days"

private fun talentLabel(category: String): String = when (category) {
    "actors" -> "Actors"
    "actresses" -> "Actresses"
    "directors" -> "Directors"
    "writers" -> "Writers"
    "studios" -> "Studios"
    else -> category.replaceFirstChar { it.uppercase() }
}
