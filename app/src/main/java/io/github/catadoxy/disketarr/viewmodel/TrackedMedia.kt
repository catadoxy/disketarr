package io.github.catadoxy.disketarr.viewmodel

import io.github.catadoxy.disketarr.data.model.LibraryType
import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.data.network.DisketarrApiService

/**
 * The item types a Floppy library's entries can have.
 *
 * Floppy's `media_type` filter is about the *item*, not the library: a TMDB
 * anime is a "tv" item, so asking for "tv" also returns the Anime library's
 * entries. A MAL anime is an "anime" item, so the Anime library needs both.
 */
internal fun itemTypesOf(library: String): List<String> = when (library) {
    LibraryType.ANIME -> listOf("tv", "anime")
    LibraryType.MOVIE -> listOf("movie")
    LibraryType.SEASON -> listOf("season")
    LibraryType.TV -> listOf("tv")
    // The other libraries (manga, game, book, ...) have their own item type.
    else -> listOf(library)
}

/**
 * The tracked entries of one Floppy library.
 *
 * The rows are filtered by the library they are filed in, because Floppy's own
 * type filter cannot tell the TV library's shows from the Anime library's anime.
 *
 * [rawRows] is how many rows the server returned before the filter - the
 * paging follows that, since the filtered rows are scattered over the pages.
 */
internal data class TrackedLibrary(
    val rows: List<SearchResult>,
    val rawRows: Int,
    val total: Int,
)

/**
 * Reads the tracked entries of a library.
 *
 * @param statuses the tracking statuses to include, or null for all of them.
 */
internal suspend fun trackedMediaOfLibrary(
    service: DisketarrApiService,
    library: String,
    statuses: List<String>? = null,
    limit: Int = 60,
    offset: Int = 0,
    rating: String? = null,
    ratingMin: Double? = null,
    genre: String? = null,
    year: String? = null,
    progress: String? = null,
    collection: String? = null,
    search: String? = null,
    sort: String? = null,
    direction: String? = null,
): TrackedLibrary {
    val rows = mutableListOf<SearchResult>()
    var raw = 0
    var total = 0
    for (itemType in itemTypesOf(library)) {
        val payload = service.listTrackedMediaRaw(
            mediaType = itemType,
            statuses = statuses,
            rating = rating,
            ratingMin = ratingMin,
            genre = genre,
            year = year,
            // Documented as meaningful for TV/anime only; sending it for
            // movies silently returns everything.
            progress = progress?.takeIf { itemType == "tv" || itemType == "anime" },
            collection = collection,
            search = search,
            sort = sort,
            direction = direction,
            limit = limit,
            offset = offset,
        )
        val fetched = payload.extractRowsSafely().toSearchResults()
        rows += fetched.filter { it.libraryMediaType == library }
        raw += fetched.size
        total += payload.asPaginationTotalOrNull() ?: fetched.size
    }
    return TrackedLibrary(rows, raw, total)
}
