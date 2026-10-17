package io.github.catadoxy.disketarr.data.model

/**
 * One curated row from `GET /api/v1/discover/` - e.g. "TV Shows: Trending Right
 * Now". The rows are rendered as horizontally scrolling poster rails.
 */
data class DiscoverRail(
    val title: String,
    val summary: String?,
    val items: List<SearchResult>,
)
