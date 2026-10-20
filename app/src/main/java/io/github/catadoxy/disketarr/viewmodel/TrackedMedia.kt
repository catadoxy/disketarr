package io.github.catadoxy.disketarr.viewmodel

import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.data.network.DisketarrApiService

/**
 * The tracked entries of one Floppy library.
 *
 * The paging follows the server's rows, which are the ones on screen.
 */
internal data class TrackedLibrary(
    val rows: List<SearchResult>,
    val total: Int,
)

/**
 * Reads the tracked entries of a library.
 *
 * Floppy's list endpoint is filtered by library: `media_type=tv` is the TV
 * library, `media_type=anime` the Anime library, `media_type=movie` the Movies
 * library. The anime move between the TV and the Anime library when the user
 * changes Floppy's *anime library mode*, so the library - not the item type -
 * is what says where a title belongs.
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
    val payload = service.listTrackedMediaRaw(
        mediaType = library,
        statuses = statuses,
        rating = rating,
        ratingMin = ratingMin,
        genre = genre,
        year = year,
        // Documented as meaningful for TV/anime only; sending it for
        // movies silently returns everything.
        progress = progress?.takeIf { library == "tv" || library == "anime" },
        collection = collection,
        search = search,
        sort = sort,
        direction = direction,
        limit = limit,
        offset = offset,
    )
    val rows = payload.extractRowsSafely().toSearchResults()
    return TrackedLibrary(rows, payload.asPaginationTotalOrNull() ?: rows.size)
}
