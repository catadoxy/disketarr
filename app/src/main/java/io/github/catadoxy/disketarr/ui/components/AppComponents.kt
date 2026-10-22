package io.github.catadoxy.disketarr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.catadoxy.disketarr.data.model.MediaStatus
import io.github.catadoxy.disketarr.data.model.NextEpisode
import io.github.catadoxy.disketarr.data.model.SearchResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A large primary-coloured screen title with trailing actions.
 */
@Composable
fun AppScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/**
 * Text tabs with a primary underline under the active one, plus the hairline the
 * strip sits on.
 */
@Composable
fun UnderlineTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        val listState = rememberLazyListState()
        // With five tabs the selected one can sit half off-screen, so bring it
        // fully into view whenever the selection changes.
        LaunchedEffect(selectedIndex, tabs.size) {
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
            val fullyVisible = item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!fullyVisible) {
                listState.animateScrollToItem(selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0)))
            }
        }
        LazyRow(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(tabs) { index, label ->
                val selected = index == selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        // IntrinsicSize.Max lets the underline match the label width
                        // inside a horizontally scrolling row, where fillMaxWidth has
                        // no bounded constraint to fill.
                        .width(IntrinsicSize.Max)
                        .clickable { onSelect(index) }
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                            ),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * Poster + meta line + title + next episode + date, with the watch state
 * trailing. Everything is taken from the tracked-media row Disketarr already
 * returns, so rendering a row costs no extra request.
 */
@Composable
fun MediaRow(
    item: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = item.image,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(68.dp)
                .height(102.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metaLine(item),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.title ?: "Untitled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            item.nextEpisode?.let { episode ->
                Text(
                    text = episodeLine(episode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            dateLine(item)?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        WatchState(item)
    }
}

@Composable
private fun WatchState(item: SearchResult) {
    when {
        item.status == MediaStatus.COMPLETED -> Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Completed",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp),
        )

        item.episodesLeft != null && item.episodesLeft > 0 -> Text(
            text = "${item.episodesLeft} left",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )

        else -> Unit
    }
}

/** "2 remaining · 8.5 ★ · TV" - the muted first line of a row. */
internal fun metaLine(item: SearchResult): String {
    val parts = buildList {
        when {
            item.episodesLeft != null && item.episodesLeft > 0 -> add("${item.episodesLeft} remaining")
            item.status != null -> add(statusLabel(item.status))
            // No status is normal for a search hit, an upcoming episode or a
            // history entry - saying "Not tracked" there is simply wrong.
            else -> Unit
        }
        item.score?.takeIf { it > 0.0 }?.let { add(String.format(Locale.US, "%.1f ★", it)) }
        // The library is what the user is looking at: an anime from TMDB is a
        // "tv" item, but it belongs to the Anime library.
        add(mediaTypeLabel(item.libraryMediaType ?: item.mediaType))
    }
    return parts.joinToString(" · ")
}

/** "3x03 Reunion" - the episode line, including the episode name when known. */
internal fun episodeLine(episode: NextEpisode): String {
    val code = when {
        episode.seasonNumber != null && episode.episodeNumber != null ->
            "${episode.seasonNumber}x${episode.episodeNumber.toString().padStart(2, '0')}"

        episode.seasonNumber != null -> "Season ${episode.seasonNumber}"
        episode.episodeNumber != null -> "Episode ${episode.episodeNumber}"
        !episode.episodeCode.isNullOrBlank() -> episode.episodeCode
        else -> null
    }
    return listOfNotNull(code, episode.title?.takeIf { it.isNotBlank() })
        .joinToString(" ")
        .ifBlank { "Next episode" }
}

/**
 * Recent dates read as "6 days ago"; older ones as "20 Dec 2019 (Fri)".
 */
internal fun dateLine(item: SearchResult): String? =
    formatRowDate(item.nextEpisode?.airDate) ?: formatRowDate(item.lastWatchedAt)

private fun formatRowDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return null
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(date, LocalDate.now(ZoneId.systemDefault()))

    return when {
        daysAgo == 0L -> "Today"
        daysAgo == 1L -> "Yesterday"
        daysAgo in 2L..7L -> "$daysAgo days ago"
        else -> ROW_DATE_FORMAT.format(date)
    }
}

private val ROW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy (EEE)")

internal fun statusLabel(status: Int?): String =
    MediaStatus.options.firstOrNull { it.first == status }?.second ?: "Not tracked"

internal fun mediaTypeLabel(type: String): String = when (type.lowercase()) {
    "all" -> "All"
    // The TV Seasons library holds per-season tracking of TV shows, so a row
    // from it is still a TV show.
    "tv", "season" -> "TV"
    "boardgame", "board_game" -> "Board Game"
    "comicissue" -> "Comic Issue"
    else -> type.replaceFirstChar { it.uppercase() }
}

/** Centred placeholder text on the flat surface. */
@Composable
fun AppEmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Centred error message with an explicit retry action. */
@Composable
fun AppRetryableError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/**
 * The API token field.
 *
 * The token is masked, and the eye button reveals it only for as long as it is
 * held down - long enough to check what was pasted, without leaving a secret
 * on screen afterwards.
 *
 * The eye is a sibling of the text field, drawn over the space reserved for it,
 * rather than the field's `trailingIcon`. A long press anywhere inside the
 * field starts a text selection - the field's gesture is on its own container,
 * so an icon inside it would get "select all" on top of the reveal.
 */
@Composable
fun ApiTokenField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("API token") },
            singleLine = true,
            visualTransformation = if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            trailingIcon = { Spacer(Modifier.size(40.dp)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(40.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // Take the whole gesture, so no long press of the field
                        // underneath can turn into a text selection.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        revealed = true
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                        revealed = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (revealed) {
                    Icons.Default.VisibilityOff
                } else {
                    Icons.Default.Visibility
                },
                contentDescription = if (revealed) {
                    "Hide the token"
                } else {
                    "Hold to show the token"
                },
            )
        }
    }
}
