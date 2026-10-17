package io.github.catadoxy.floppy.viewmodel

import io.github.catadoxy.floppy.data.model.Pagination
import io.github.catadoxy.floppy.data.model.SearchResponse
import io.github.catadoxy.floppy.data.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchViewModelTest {

    @Test
    fun normalizeMediaType_preserves_supported_types() {
        assertEquals("tv", normalizeMediaType("TV"))
        assertEquals("anime", normalizeMediaType("anime"))
        assertEquals("movie", normalizeMediaType("unknown"))
    }

    @Test
    fun nextPageOffset_uses_current_offset_plus_limit() {
        val response = SearchResponse(
            pagination = Pagination(total = 60, limit = 20, offset = 20, next = "next", previous = "prev"),
            results = listOf(
                SearchResult("1", "tmdb", "movie", "One", null),
                SearchResult("2", "tmdb", "movie", "Two", null),
            ),
        )

        assertEquals(40, nextPageOffset(response))
    }

    @Test
    fun mergeSearchResults_appends_new_pages_without_dropping_existing_items() {
        val existing = listOf(
            SearchResult("1", "tmdb", "movie", "One", null),
            SearchResult("2", "tmdb", "movie", "Two", null),
        )
        val incoming = listOf(
            SearchResult("3", "tmdb", "movie", "Three", null),
        )

        assertEquals(3, mergeSearchResults(existing, incoming).size)
        assertEquals("Three", mergeSearchResults(existing, incoming).last().title)
    }
}
