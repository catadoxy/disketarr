package io.github.catadoxy.disketarr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.catadoxy.disketarr.data.model.StatisticsOverview
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * A compact column chart: one bar per value, labels under the bars that ask for
 * one. Built from plain boxes rather than a chart library - the app only needs
 * bars, and this keeps the APK free of a dependency for three charts.
 */
@Composable
fun StatsBarChart(
    values: List<Float>,
    labels: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    labelEvery: Int = 1,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightIndex: Int? = null,
) {
    if (values.isEmpty()) return
    val max = values.maxOrNull()?.takeIf { it > 0f } ?: 1f

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction = (value / max).coerceIn(0.015f, 1f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(
                                if (highlightIndex != null && highlightIndex == index) {
                                    barColor
                                } else {
                                    barColor.copy(alpha = 0.45f)
                                },
                            ),
                    )
                }
                val label = labels.getOrNull(index)
                if (!label.isNullOrBlank() && index % labelEvery == 0) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A GitHub-style calendar of watch activity: one square per day, columns are
 * weeks starting on Monday, the shade follows Floppy's own `level` (0-4).
 */
@Composable
fun ActivityHeatmap(
    days: List<StatisticsOverview.HeatmapDay>,
    modifier: Modifier = Modifier,
    weeks: Int = 24,
    typeFilter: String? = null,
) {
    if (days.isEmpty()) return

    val today = LocalDate.now()
    val start = today
        .minusWeeks((weeks - 1).toLong())
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val byDate = days.associateBy { it.date }
    val quiet = MaterialTheme.colorScheme.surfaceVariant
    val shades = listOf(
        quiet,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
        MaterialTheme.colorScheme.primary,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (weekday in 0..6) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (week in 0 until weeks) {
                    val date = start.plusDays((week * 7 + weekday).toLong())
                    val day = if (date.isAfter(today)) null else byDate[date.toString()]
                    val count = when {
                        day == null -> 0
                        typeFilter == null -> day.count
                        else -> day.byType[typeFilter] ?: 0
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (date.isAfter(today)) {
                                    Color.Transparent
                                } else {
                                    shades[if (count <= 0) 0 else day!!.level.coerceIn(1, 4)]
                                },
                            ),
                    )
                }
            }
        }
    }
}
