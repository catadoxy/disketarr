package io.github.catadoxy.disketarr.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.viewmodel.AnimeViewModel
import io.github.catadoxy.disketarr.viewmodel.MoviesViewModel
import io.github.catadoxy.disketarr.viewmodel.ShowsViewModel


@Composable
fun ShowsScreen(
    onOpenDetail: (SearchResult) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: ShowsViewModel = hiltViewModel()
    BrowseScreen(
        viewModel = viewModel,
        title = "Shows",
        onOpenDetail = onOpenDetail,
        onOpenSearch = onOpenSearch,
    )
}

@Composable
fun MoviesScreen(
    onOpenDetail: (SearchResult) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: MoviesViewModel = hiltViewModel()
    BrowseScreen(
        viewModel = viewModel,
        title = "Movies",
        onOpenDetail = onOpenDetail,
        onOpenSearch = onOpenSearch,
    )
}

@Composable
fun AnimeScreen(
    onOpenDetail: (SearchResult) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: AnimeViewModel = hiltViewModel()
    BrowseScreen(
        viewModel = viewModel,
        title = "Anime",
        onOpenDetail = onOpenDetail,
        onOpenSearch = onOpenSearch,
    )
}
