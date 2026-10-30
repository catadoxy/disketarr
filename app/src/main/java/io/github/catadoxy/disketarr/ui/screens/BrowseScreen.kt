package io.github.catadoxy.disketarr.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.catadoxy.disketarr.data.model.DiscoverRail
import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.ui.components.AppEmptyState
import io.github.catadoxy.disketarr.ui.components.AppRetryableError
import io.github.catadoxy.disketarr.ui.components.AppScreenHeader
import io.github.catadoxy.disketarr.ui.components.MediaRow
import io.github.catadoxy.disketarr.ui.components.SwipeableTabPager
import io.github.catadoxy.disketarr.ui.components.UnderlineTabRow
import io.github.catadoxy.disketarr.viewmodel.BrowseFilter
import io.github.catadoxy.disketarr.viewmodel.BrowseTab
import io.github.catadoxy.disketarr.viewmodel.BrowseUiState
import io.github.catadoxy.disketarr.viewmodel.BrowseViewModel
import io.github.catadoxy.disketarr.viewmodel.FILTERABLE_TABS

/** Sort options Disketarr's media list accepts. */
private val SORT_OPTIONS = listOf(
    "date_added" to "Recently added",
    "title" to "Title",
    "score" to "My score",
    "progress" to "Progress",
    // Disketarr has no "episodes left" sort (sort=episodes_left returns 400), but
    // time_left is the remaining watch time, so shows order by how much is left.
    "time_left" to "Time left",
    "next_episode_air_date" to "Next episode",
    "release_date" to "Release date",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    title: String,
    onOpenDetail: (SearchResult, String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter = uiState.filter

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppScreenHeader(title = title) {
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search for something to add")
            }
            // Discover is curated and History has its own endpoint, so neither
            // takes the media-list filters.
            if (uiState.tab in FILTERABLE_TABS) {
                FilterMenu(
                    filter = filter,
                    showProgress = viewModel.mediaType == "tv" || viewModel.mediaType == "anime",
                    onApply = viewModel::applyFilter,
                )
            }
        }

        UnderlineTabRow(
            tabs = viewModel.tabs.map { it.label },
            selectedIndex = viewModel.tabs.indexOf(uiState.tab).coerceAtLeast(0),
            onSelect = { viewModel.selectTab(viewModel.tabs[it]) },
        )

        PullToRefreshBox(
            isRefreshing = uiState.loading,
            onRefresh = { viewModel.forceRefresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            SwipeableTabPager(
                pageCount = viewModel.tabs.size,
                selectedIndex = viewModel.tabs.indexOf(uiState.tab).coerceAtLeast(0),
                onPageSelected = { page -> viewModel.selectTab(viewModel.tabs[page]) },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tab = viewModel.tabs[page]
                BrowsePage(
                    tab = tab,
                    // A page that is not the selected tab has not been loaded
                    // yet - it shows a loading placeholder until the swipe
                    // settles and the view model fetches it.
                    state = if (tab == uiState.tab) uiState else null,
                    mediaType = viewModel.mediaType,
                    onOpenDetail = onOpenDetail,
                    onRetry = viewModel::forceRefresh,
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }
}

@Composable
private fun BrowsePage(
    tab: BrowseTab,
    state: BrowseUiState?,
    mediaType: String,
    onOpenDetail: (SearchResult, String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    if (state == null ||
        (state.loading && state.items.orEmpty().isEmpty() && state.rails.orEmpty().isEmpty())
    ) {
        RefreshableEmpty("Loading…")
        return
    }

    when {
        state.error != null && state.items.orEmpty().isEmpty() && state.rails.orEmpty().isEmpty() ->
            RefreshableError(state.error.orEmpty()) { onRetry() }

        tab == BrowseTab.DISCOVER -> DiscoverRails(state.rails, onOpenDetail, mediaType)

        state.items.isEmpty() ->
            RefreshableEmpty(emptyMessage(tab))

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // History is one row per play, so the same show can appear many
            // times. Keying by source/type/id alone collides and crashes the
            // list, so include the position.
            itemsIndexed(state.items) { _, item ->
                MediaRow(item = item, onClick = { onOpenDetail(item, item.libraryMediaType ?: mediaType) })
            }
            if (state.canLoadMore) {
                item {
                    Button(
                        onClick = onLoadMore,
                        enabled = !state.loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(if (state.loading) "Loading…" else "See more")
                    }
                }
            }
        }
    }
}

/**
 * An empty or loading state that can still be pulled. A plain Column has nothing
 * to scroll, so [PullToRefreshBox] never sees a drag.
 */
@Composable
private fun RefreshableEmpty(text: String) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            AppEmptyState(text = text, modifier = Modifier.padding(top = 48.dp))
        }
    }
}

@Composable
private fun RefreshableError(message: String, onRetry: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            AppRetryableError(message = message, onRetry = onRetry, modifier = Modifier.padding(top = 48.dp))
        }
    }
}

private fun emptyMessage(tab: BrowseTab): String = when (tab) {
    BrowseTab.IN_PROGRESS -> "Nothing in progress right now."
    BrowseTab.HISTORY -> "No history yet."
    BrowseTab.UPCOMING -> "Nothing upcoming."
    BrowseTab.WATCHED -> "Nothing watched yet."
    BrowseTab.PLANNED -> "Nothing planned yet."
    BrowseTab.DISCOVER -> "Nothing to discover right now."
}

@Composable
private fun DiscoverRails(
    rails: List<DiscoverRail>,
    onOpenDetail: (SearchResult, String) -> Unit,
    /** The library a catalog hit is filed in when it is tracked. */
    fallbackLibrary: String,
) {
    if (rails.orEmpty().isEmpty()) {
        AppEmptyState("Nothing to discover right now.", modifier = Modifier.fillMaxWidth())
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(rails) { rail ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = rail.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                rail.summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(rail.items, key = { "${it.source}|${it.mediaId}" }) { item ->
                        PosterCard(item = item, onClick = { onOpenDetail(item, item.libraryMediaType ?: fallbackLibrary) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterCard(item: SearchResult, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.image,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(112.dp)
                .height(168.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = item.title ?: "Untitled",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The two panes of the filter dropdown. */
private enum class FilterPane { GENERAL, SORT }

@Composable
private fun FilterMenu(
    filter: BrowseFilter,
    /** Progress is documented as TV/anime only, so it is hidden for movies. */
    showProgress: Boolean,
    onApply: (BrowseFilter) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var pane by remember { mutableStateOf(FilterPane.GENERAL) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filter",
                tint = if (filter.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(300.dp),
        ) {
            // Split over two panes so the dropdown stays short, and every option
            // applies as soon as it is tapped - there is no Apply button to reach.
            Row(modifier = Modifier.fillMaxWidth()) {
                PaneTab("General", pane == FilterPane.GENERAL) { pane = FilterPane.GENERAL }
                PaneTab("Sort by", pane == FilterPane.SORT) { pane = FilterPane.SORT }
            }
            HorizontalDivider()

            if (pane == FilterPane.GENERAL) {
                // No status filter: each tab pins its own status, and offering a
                // different one here would contradict the list you are looking at.
                MenuLabel("My rating")
                MenuOption("Any", filter.rating == null && filter.ratingMin == null) {
                    onApply(filter.copy(rating = null, ratingMin = null))
                }
                MenuOption("Not rated", filter.rating == "not_rated") {
                    onApply(filter.copy(rating = "not_rated", ratingMin = null))
                }
                MenuOption("8 or higher", filter.ratingMin == 8.0) {
                    onApply(filter.copy(rating = null, ratingMin = 8.0))
                }
                MenuOption("5 or higher", filter.ratingMin == 5.0) {
                    onApply(filter.copy(rating = null, ratingMin = 5.0))
                }

                if (showProgress) {
                    HorizontalDivider()
                    MenuLabel("Progress")
                    MenuOption("Any", filter.progress == null) {
                        onApply(filter.copy(progress = null))
                    }
                    MenuOption("Caught up", filter.progress == "caught_up") {
                        onApply(filter.copy(progress = "caught_up"))
                    }
                    MenuOption("Not caught up", filter.progress == "not_caught_up") {
                        onApply(filter.copy(progress = "not_caught_up"))
                    }
                }

                HorizontalDivider()
                MenuLabel("Collection")
                MenuOption("Any", filter.collection == null) {
                    onApply(filter.copy(collection = null))
                }
                MenuOption("Collected", filter.collection == "collected") {
                    onApply(filter.copy(collection = "collected"))
                }
                MenuOption("Not collected", filter.collection == "not_collected") {
                    onApply(filter.copy(collection = "not_collected"))
                }
            } else {
                MenuLabel("Sort by")
                MenuOption("Default", filter.sort == null) {
                    onApply(filter.copy(sort = null))
                }
                SORT_OPTIONS.forEach { (value, label) ->
                    MenuOption(label, filter.sort == value) {
                        onApply(filter.copy(sort = value))
                    }
                }
                MenuOption(
                    // Unset means the server's own default (ascending for most
                    // sorts), so the label must not claim a direction it is not.
                    text = when (filter.direction) {
                        "asc" -> "Ascending"
                        "desc" -> "Descending"
                        else -> "Direction (default)"
                    },
                    selected = false,
                ) {
                    onApply(filter.copy(direction = if (filter.direction == "desc") "asc" else "desc"))
                }
            }

            if (filter.isActive) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Clear filters") },
                    onClick = { open = false; onApply(BrowseFilter()) },
                )
            }
        }
    }
}

/** One pane switch inside the filter dropdown. */
@Composable
private fun RowScope.PaneTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

@Composable
private fun MenuLabel(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuOption(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onClick,
    )
}
