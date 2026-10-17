package io.github.catadoxy.floppy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.github.catadoxy.floppy.data.model.Pagination
import io.github.catadoxy.floppy.data.model.SearchResult
import io.github.catadoxy.floppy.ui.components.UnderlineTabRow
import io.github.catadoxy.floppy.viewmodel.SearchUiState
import io.github.catadoxy.floppy.viewmodel.SearchViewModel
import io.github.catadoxy.floppy.viewmodel.normalizeMediaType
import io.github.catadoxy.floppy.viewmodel.pageNumber
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private data class MediaTypeOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun SearchScreen(
    onResultClick: (SearchResult) -> Unit,
) {
    val viewModel: SearchViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val mediaTypes = listOf(
        MediaTypeOption("movie", "Movie", Icons.Default.Movie),
        MediaTypeOption("tv", "TV", Icons.Default.LiveTv),
        MediaTypeOption("anime", "Anime", Icons.Default.AutoAwesome),
        MediaTypeOption("manga", "Manga", Icons.AutoMirrored.Filled.MenuBook),
        MediaTypeOption("game", "Game", Icons.Default.SportsEsports),
        MediaTypeOption("boardgame", "Board Game", Icons.Default.SportsEsports),
        MediaTypeOption("music", "Music", Icons.Default.MusicNote),
        MediaTypeOption("podcast", "Podcast", Icons.Default.MusicNote),
        MediaTypeOption("book", "Book", Icons.AutoMirrored.Filled.MenuBook),
        MediaTypeOption("comic", "Comic", Icons.AutoMirrored.Filled.MenuBook),
    )
    var selectedType by remember { mutableStateOf("movie") }
    var gridLayout by remember { mutableStateOf(true) }

    fun submitSearch() {
        viewModel.searchMedia(query.trim(), mediaType = selectedType)
    }

    LaunchedEffect(query, selectedType) {
        if (query.trim().length >= 3) {
            delay(350)
            viewModel.searchMedia(query.trim(), mediaType = selectedType)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (query.isBlank()) {
                        viewModel.searchMedia("", mediaType = selectedType)
                    }
                },
                label = { Text("Search your media catalog") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { submitSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Run search")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UnderlineTabRow(
                tabs = mediaTypes.map { it.label },
                selectedIndex = mediaTypes
                    .indexOfFirst { normalizeMediaType(it.key) == selectedType }
                    .coerceAtLeast(0),
                onSelect = { index ->
                    val normalized = normalizeMediaType(mediaTypes[index].key)
                    selectedType = normalized
                    if (query.isNotBlank()) {
                        viewModel.searchMedia(query.trim(), mediaType = normalized)
                    }
                },
                modifier = Modifier.weight(1f),
            )

            Row(
                modifier = Modifier.padding(start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LayoutToggleButton(
                    selected = gridLayout,
                    icon = Icons.Default.GridView,
                    contentDescription = "Grid layout",
                    onClick = { gridLayout = true },
                )
                LayoutToggleButton(
                    selected = !gridLayout,
                    icon = Icons.AutoMirrored.Filled.List,
                    contentDescription = "List layout",
                    onClick = { gridLayout = false },
                )
            }
        }

        when (val state = uiState) {
            is SearchUiState.Idle -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (recentSearches.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Recent searches",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { viewModel.clearRecents() },
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentSearches) { recent ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        query = recent
                                        viewModel.searchMedia(recent, mediaType = selectedType)
                                    },
                                    label = { Text(recent) },
                                )
                            }
                        }
                    }
                    EmptySearchState("Search movies, tv, anime, manga, comics, games, board games, music, podcasts, and books")
                }
            }
            is SearchUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is SearchUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    io.github.catadoxy.floppy.ui.components.AppRetryableError(
                        message = state.message,
                        onRetry = { if (query.isNotBlank()) viewModel.searchMedia(query.trim(), mediaType = selectedType) },
                    )
                }
            }
            is SearchUiState.Success -> {
                val response = state.response
                if (response.results.isEmpty()) {
                    EmptySearchState("No results found. Try different keywords.")
                } else if (gridLayout) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "${response.pagination.total} total results",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        items(response.results) { result ->
                            SearchResultGridCard(result, onClick = { onResultClick(result) })
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SearchPagination(
                                pagination = response.pagination,
                                onLoadPage = { page, limit ->
                                    viewModel.loadPage(page, limit)
                                },
                            )
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text(
                                text = "${response.pagination.total} total results",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        items(response.results) { result ->
                            SearchResultRow(result, onClick = { onResultClick(result) })
                        }

                        item {
                            SearchPagination(
                                pagination = response.pagination,
                                onLoadPage = { page, limit ->
                                    viewModel.loadPage(page, limit)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPagination(
    pagination: Pagination,
    onLoadPage: (page: Int, limit: Int) -> Unit,
) {
    if (pagination.total <= pagination.limit || pagination.limit <= 0) return

    val currentPage = pageNumber(pagination.offset, pagination.limit)
    val totalPages = max(1, ceil(pagination.total.toDouble() / pagination.limit.toDouble()).toInt())
    val windowStart = max(1, currentPage - 2)
    val windowEnd = min(totalPages, currentPage + 2)
    val pages = (windowStart..windowEnd).toList()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onLoadPage(currentPage - 1, pagination.limit) },
                enabled = currentPage > 1,
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page")
            }

            pages.forEach { page ->
                val selected = page == currentPage
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(horizontal = 2.dp),
                ) {
                    Text(
                        text = page.toString(),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clickable { onLoadPage(page, pagination.limit) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            IconButton(
                onClick = { onLoadPage(currentPage + 1, pagination.limit) },
                enabled = currentPage < totalPages,
            ) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next page")
            }
        }
    }
}

@Composable
private fun EmptySearchState(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LayoutToggleButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultGridCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = result.image,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    text = result.title ?: "Unknown Title",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    text = result.mediaType.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = result.image,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    result.title ?: "Unknown Title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val subtitle = listOfNotNull(
                    result.mediaType.replaceFirstChar { it.uppercase() },
                    result.source.replaceFirstChar { it.uppercase() },
                ).joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        ),
                        shape = RoundedCornerShape(50),
                    ),
            )
        }
    }
}

