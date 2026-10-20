package io.github.catadoxy.disketarr.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Brush
import io.github.catadoxy.disketarr.data.model.SeasonSummary
import io.github.catadoxy.disketarr.ui.components.AppScreenHeader
import io.github.catadoxy.disketarr.ui.components.UnderlineTabRow
import io.github.catadoxy.disketarr.ui.components.mediaTypeLabel
import io.github.catadoxy.disketarr.viewmodel.isShowMediaType
import io.github.catadoxy.disketarr.viewmodel.nextEpisode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.github.catadoxy.disketarr.data.model.CastMember
import io.github.catadoxy.disketarr.data.model.MediaDetail
import io.github.catadoxy.disketarr.data.model.MediaStatus
import io.github.catadoxy.disketarr.data.model.SeasonEpisode
import io.github.catadoxy.disketarr.viewmodel.DetailsUiState
import io.github.catadoxy.disketarr.viewmodel.DetailsViewModel

import java.util.Locale

private fun formatRatingValue(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

private fun sourceLabel(source: String?): String {
    return when (source?.lowercase()) {
        "tmdb" -> "TMDB"
        "mal", "myanimelist" -> "MAL"
        "igdb" -> "IGDB"
        "jellyfin" -> "Jellyfin"
        "trakt" -> "Trakt"
        "imdb" -> "IMDb"
        "anilist" -> "AniList"
        else -> source?.uppercase()?.take(4) ?: "SCORE"
    }
}

@Composable
private fun sourceAccent(source: String?): Color {
    return when (source?.lowercase()) {
        "tmdb" -> Color(0xFF22D3EE) // cyan
        "mal", "myanimelist" -> Color(0xFF818CF8) // indigo
        "igdb" -> Color(0xFFF472B6) // pink
        "trakt" -> Color(0xFFFB7185) // rose
        "imdb" -> Color(0xFFFBBF24) // amber
        "anilist" -> Color(0xFF60A5FA) // blue
        else -> MaterialTheme.colorScheme.primary
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    source: String,
    mediaType: String,
    mediaId: String,
    /**
     * The Floppy library this title is shown from. A title can be tracked in
     * the TV and in the Anime library at once, each with its own status and
     * progress, so the screen has to say which copy it means.
     */
    library: String = "",
) {
    val viewModel: DetailsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val lastActionMessage = actionMessage

    LaunchedEffect(source, mediaType, mediaId, library) {
        viewModel.load(source, mediaType, mediaId, library)
    }

    when (val state = uiState) {
        is DetailsUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading\u2026", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is DetailsUiState.Error -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is DetailsUiState.Success -> {
            val detail = state.detail
            var selectedTab by remember(mediaType, source, mediaId) { mutableIntStateOf(0) }
            val hasEpisodes = state.seasons.isNotEmpty()
            val nextEpisode = state.nextEpisode
            val tabs = buildList {
                add("Overview")
                if (hasEpisodes) add("Episodes")
            }
            val activeTab = tabs[selectedTab.coerceIn(0, tabs.lastIndex)]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                AppScreenHeader(title = detail.title ?: "Details")
                UnderlineTabRow(
                    tabs = tabs,
                    selectedIndex = tabs.indexOf(activeTab),
                    onSelect = { selectedTab = it },
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (activeTab == "Overview") {
                        AsyncImage(
                            model = detail.backdrop ?: detail.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.TopCenter),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                )
                    }

                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {

                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { viewModel.reload(source, mediaType, mediaId) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        when (activeTab) {
                            "Overview" -> {
                                OverviewTab(
                                    detail = detail,
                                    nextEpisode = nextEpisode,
                                    remaining = state.seasons
                                        .firstOrNull { it.seasonNumber == state.selectedSeason }
                                        ?.episodesLeft,
                                    actionMessage = lastActionMessage,
                                    onSetWatched = {
                                        viewModel.markNextEpisodeWatched(mediaType, source, mediaId)
                                    },
                                )
                                // Everything this screen used to show stays here.
                                DetailsTab(
                                    detail = detail,
                                    viewModel = viewModel,
                                    actionMessage = lastActionMessage,
                                )
                            }

                            else -> state.seasons.forEach { summary ->
                                SeasonSection(
                                    summary = summary,
                                    selectedSeason = state.selectedSeason,
                                    episodes = if (summary.seasonNumber == state.selectedSeason) {
                                        state.episodes
                                    } else {
                                        emptyList()
                                    },
                                    loading = state.loadingEpisodes && summary.seasonNumber == state.selectedSeason,
                                    onSelect = {
                                        viewModel.toggleSeason(mediaType, source, mediaId, summary.seasonNumber)
                                    },
                                    onToggleEpisode = { episode ->
                                        viewModel.toggleEpisodeWatched(mediaType, source, mediaId, episode)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
}

/**
 * What the show is doing right now: the next episode to watch and how it is
 * rated. Modelled on SeriesGuide's "Overview" tab, minus the sharing, streaming
 * and reminder actions Disketarr has no data for.
 */
@Composable
private fun OverviewTab(
    detail: MediaDetail,
    nextEpisode: SeasonEpisode?,
    remaining: Int?,
    actionMessage: String?,
    onSetWatched: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StatusLine(detail)

        if (nextEpisode != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = buildString {
                        append("Next to watch")
                        remaining?.takeIf { it > 0 }?.let { append(" \u00b7 $it remaining") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                NextEpisodeCard(episode = nextEpisode, fallbackImage = detail.image)
                Button(
                    onClick = onSetWatched,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Set watched")
                }
            }
        }

        actionMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        RatingsRow(detail)

        if (!detail.lists.isNullOrEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Lists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                detail.lists.forEach { list ->
                    Text(
                        text = list.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Wide backdrop artwork, faded into the page background. */
@Composable
private fun BackdropHeader(detail: MediaDetail) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        AsyncImage(
            model = detail.backdrop ?: detail.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    ),
                ),
        )
    }
}

/** "Returning Series" then "100studio · 24m". */
@Composable
private fun StatusLine(detail: MediaDetail) {
    val studios = detail.details?.studios.orEmpty().take(2).joinToString(", ")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        detail.details?.status?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        val meta = listOfNotNull(
            studios.takeIf { it.isNotBlank() },
            detail.details?.runtime?.toString()?.takeIf { it.isNotBlank() },
        ).joinToString(" \u00b7 ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextEpisodeCard(episode: SeasonEpisode, fallbackImage: String?) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            AsyncImage(
                model = fallbackImage,
                contentDescription = episode.title ?: episode.code,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = episode.title ?: episode.code,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        "Season ${episode.seasonNumber ?: 1} Episode ${episode.episodeNumber}",
                        formatIsoDate(episode.airDate),
                    ).joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RatingsRow(detail: MediaDetail) {
    val ratings = listOfNotNull(
        detail.score?.let { "Floppy" to it to detail.scoreCount },
        detail.imdbRating?.let { "IMDb" to it to detail.imdbRatingCount },
    )
    if (ratings.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        ratings.forEach { (entry, votes) ->
            val (label, score) = entry
            Column {
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f", score)}/10",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(label, votes?.let { "$it votes" }).joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Everything our old single-tab screen showed: actions, synopsis, genres, cast. */
@Composable
private fun DetailsTab(
    detail: MediaDetail,
    viewModel: DetailsViewModel,
    actionMessage: String?,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Poster(detail)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TitleBlock(detail)
                ScoreChips(detail)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isShowMediaType(detail.mediaType)) {
                        IconButton(onClick = { viewModel.toggleSeriesReminder(detail) }) {
                            Icon(
                                imageVector = if (viewModel.isSeriesReminderEnabled(detail)) Icons.Default.Notifications else Icons.Outlined.NotificationsNone,
                                contentDescription = "Series reminders",
                                tint = if (viewModel.isSeriesReminderEnabled(detail)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        RatingControls(detail = detail, viewModel = viewModel)
                    }
                }
            }
        }

        ActionButtons(
            detail = detail,
            viewModel = viewModel,
            actionMessage = actionMessage,
        )

        SynopsisBlock(detail.synopsis)

        if (!detail.genres.isNullOrEmpty()) {
            GenreSection(detail.genres)
        }

        DetailsPanel(detail)

        if (!detail.cast.isNullOrEmpty()) {
            CastSection(detail.cast)
        }
    }
}

@Composable
private fun Poster(detail: MediaDetail) {
    Box(
        modifier = Modifier
            .width(132.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp)),
    ) {
        AsyncImage(
            model = detail.image,
            contentDescription = detail.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(132.dp)
                .height(198.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun TitleBlock(detail: MediaDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            detail.title ?: "Unknown Title",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        val meta = listOfNotNull(
            mediaTypeLabel(detail.mediaType),
            detail.details?.seasonNumber?.let { "Season $it" },
            detail.details?.episodeNumber?.let { "Ep $it" },
            formatIsoDate(detail.details?.displayDate),
        ).joinToString(" \u00b7 ")

        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScoreChips(detail: MediaDetail) {
    val score = detail.score
    val scoreCount = detail.scoreCount
    if (score == null && scoreCount == null) return

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScoreChip(
                label = sourceLabel(detail.source),
                accent = sourceAccent(detail.source),
                score = score,
                count = scoreCount,
            )
        }
        // User's own rating, if tracked
        val userScore = detail.consumptions?.firstOrNull()?.score
        if (userScore != null) {
            item {
                ScoreChip(
                    label = "You",
                    accent = MaterialTheme.colorScheme.primary,
                    score = userScore,
                    count = null,
                )
            }
        }
    }
}

@Composable
private fun ScoreChip(
    label: String,
    accent: Color,
    score: Double?,
    count: Int?,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.take(2),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                score?.let { formatRatingValue(it) } ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (count != null) {
                Text(
                    "  |  $count",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    detail: MediaDetail,
    viewModel: DetailsViewModel,
    actionMessage: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Only offer tracking for titles that are not tracked yet. Tracked
            // titles expose "Untrack" through the compact controls under the
            // TMDB pill instead.
            val tracked = !detail.consumptions.isNullOrEmpty()
            if (!tracked) {
                val completed = detail.consumptions?.firstOrNull()?.status == MediaStatus.COMPLETED
                var showTrackDialog by remember(detail.id, detail.mediaId) { mutableStateOf(false) }

                Button(
                    onClick = { showTrackDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Track")
                }

                if (showTrackDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showTrackDialog = false },
                        title = { Text("Track this title") },
                        text = { Text("How do you want to track it?") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.markWatched(detail)
                                showTrackDialog = false
                            }) { Text("Mark watched") }
                        },
                        dismissButton = {
                            Column {
                                TextButton(onClick = {
                                    viewModel.addToTracker(detail)
                                    showTrackDialog = false
                                }) { Text("Add as planned") }
                                if (completed) {
                                    TextButton(onClick = {
                                        viewModel.unmarkWatched(detail)
                                        showTrackDialog = false
                                    }) { Text("Unmark watched") }
                                }
                            }
                        },
                    )
                }
            }

        }

        val firstListId = detail.lists?.firstOrNull()?.id?.toString()
        if (!firstListId.isNullOrBlank()) {
            val listName = detail.lists?.firstOrNull()?.name ?: "List"
            OutlinedButton(
                onClick = { viewModel.addToList(detail, firstListId) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add to $listName")
            }
        }

        if (!actionMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    actionMessage,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun RatingControls(detail: MediaDetail, viewModel: DetailsViewModel) {
    val initialScore = detail.consumptions?.firstOrNull()?.score ?: detail.score ?: 0.0
    var selectedScore by remember(detail.id, detail.mediaId, initialScore) {
        mutableFloatStateOf(initialScore.coerceIn(0.0, 10.0).toFloat())
    }
    var ratingExpanded by remember { mutableStateOf(false) }
    val tracked = !detail.consumptions.isNullOrEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(
            onClick = { ratingExpanded = !ratingExpanded },
            modifier = Modifier.fillMaxWidth().height(32.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("${formatRatingValue(selectedScore.toDouble())}/10", style = MaterialTheme.typography.labelSmall)
        }

        if (tracked) {
            OutlinedButton(
                onClick = { viewModel.untrack(detail) },
                modifier = Modifier.fillMaxWidth().height(32.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Untrack", style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (ratingExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${formatRatingValue(selectedScore.toDouble())} / 10",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = selectedScore,
                onValueChange = { selectedScore = it },
                valueRange = 0f..10f,
                steps = 19,
                onValueChangeFinished = {
                    viewModel.rate(detail, selectedScore.toDouble())
                    ratingExpanded = false
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SynopsisBlock(synopsis: String?) {
    if (synopsis.isNullOrBlank()) return

    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            synopsis,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (synopsis.length > 180) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (expanded) "Show less" else "Read more")
            }
        }
    }
}

@Composable
private fun GenreSection(genres: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Genres",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.take(6).forEach { genre ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        genre,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsPanel(detail: MediaDetail) {
    val details = detail.details
    val rows = listOfNotNull(
        "Status" to detail.consumptions?.firstOrNull()?.let { statusLabel(it.status) },
        "Format" to mediaTypeLabel(detail.mediaType),
        // max_progress is a progress unit (1 for a show), not an episode count.
        "Seasons" to details?.seasons?.toString(),
        "Episodes" to details?.episodes?.toString(),
        "Runtime" to details?.runtime?.toString(),
        "Season" to details?.seasonNumber?.toString(),
        "Episode" to details?.episodeNumber?.toString(),
        "Air date" to formatIsoDate(details?.displayDate),
        "Source" to sourceLabel(detail.source),
    ).filter { it.second != null }

    if (rows.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rows.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        rowItems.forEach { (label, value) ->
                            DetailCell(label = label, value = value.orEmpty())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DetailCell(label: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SeasonSection(
    summary: SeasonSummary,
    selectedSeason: Int?,
    episodes: List<SeasonEpisode>,
    loading: Boolean,
    onSelect: () -> Unit,
    onToggleEpisode: (SeasonEpisode) -> Unit,
) {
    val expanded = summary.seasonNumber == selectedSeason
    // The season rows do not always carry a total, so fall back to the episode
    // list once it is loaded - otherwise a 2-of-8 season reads "2/2".
    val total = summary.totalEpisodes ?: episodes.size.takeIf { it > 0 && expanded }
    val watched = summary.progress ?: episodes.count { it.watched }.takeIf { expanded }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (summary.seasonNumber == 0) "Specials" else "Season ${summary.seasonNumber}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (total != null) {
                Text(
                    text = "${watched ?: 0}/$total",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Progress bar plus the remaining/released breakdown, like a season card.
        if (total != null && total > 0) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LinearProgressIndicator(
                    progress = { ((watched ?: 0).toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val breakdown = listOfNotNull(
                    summary.episodesLeft?.takeIf { it > 0 }?.let { "$it remaining" },
                    summary.unreleased?.let { "$it to be released" },
                ).joinToString(" \u00b7 ")
                if (breakdown.isNotBlank()) {
                    Text(
                        text = breakdown,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!expanded) {
            Text(
                text = "Tap to show episodes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (loading) {
                    Text(
                        text = "Loading episodes\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (episodes.isEmpty()) {
                    Text(
                        text = "No episodes returned for this season.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    episodes.forEach { episode ->
                        EpisodeRow(episode = episode, onClick = { onToggleEpisode(episode) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: SeasonEpisode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (episode.watched) Icons.Default.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (episode.watched) "Watched" else "Not watched",
            tint = if (episode.watched) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title?.let { "${episode.code}  $it" } ?: episode.code,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                episode.runtimeMinutes?.let { "${it}m" },
                formatEpisodeAirDate(episode.airDate),
            ).joinToString(" \u00b7 ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatEpisodeAirDate(raw: String?): String? = formatIsoDate(raw)

/**
 * Disketarr sends full ISO date-times (e.g. `2016-02-11T00:00:00+02:00`). Showing
 * that verbatim looks broken, so render just the date.
 */
private fun formatIsoDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val parsed = runCatching { java.time.OffsetDateTime.parse(raw) }.getOrNull()
        ?: runCatching { java.time.Instant.parse(raw).atOffset(java.time.ZoneOffset.UTC) }.getOrNull()
        ?: return raw
    return java.time.format.DateTimeFormatter
        .ofPattern("d MMM yyyy", java.util.Locale.getDefault())
        .format(parsed.toLocalDate())
}

@Composable
private fun CastSection(cast: List<CastMember>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Cast",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cast.take(12)) { member ->
                CastCard(member)
            }
        }
    }
}

@Composable
private fun CastCard(member: CastMember) {
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                member.name?.take(1)?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!member.image.isNullOrBlank()) {
                AsyncImage(
                    model = member.image,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape),
                )
            }
        }
        Text(
            member.name ?: "Unknown",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (!member.character.isNullOrBlank()) {
            Text(
                member.character,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun statusLabel(status: Int?): String? {
    // Disketarr codes: 0 Planning, 1 In progress, 2 Paused, 3 Completed, 4 Dropped.
    return MediaStatus.options.firstOrNull { it.first == status }?.second
}
