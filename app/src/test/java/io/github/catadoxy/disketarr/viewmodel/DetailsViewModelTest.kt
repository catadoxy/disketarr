package io.github.catadoxy.disketarr.viewmodel

import io.github.catadoxy.disketarr.data.model.LibraryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Floppy keeps one tracked entry per *library*, so the library is part of
 * every request. These guard the mapping between the app's media types and
 * Floppy's libraries - getting it wrong files an anime in the TV library,
 * where it shows up in the Shows tab instead of the Anime one.
 */
class LibraryTypeTest {

    @Test
    fun anime_is_its_own_library_even_though_the_item_is_a_tv_show() {
        // A TMDB anime is a "tv" item that lives in the Anime library.
        assertEquals(LibraryType.ANIME, LibraryType.forMediaType("anime"))
        assertEquals(LibraryType.TV, LibraryType.forMediaType("tv"))
        assertEquals(LibraryType.MOVIE, LibraryType.forMediaType("movie"))
    }

    @Test
    fun only_floppys_libraries_are_accepted() {
        assertTrue(LibraryType.isSupported("anime"))
        assertTrue(LibraryType.isSupported("TV"))
        assertTrue(LibraryType.isSupported("season"))
        assertEquals(false, LibraryType.isSupported("manga"))
        assertEquals(false, LibraryType.isSupported(null))
        assertEquals(false, LibraryType.isSupported(""))
    }

    /**
     * Floppy's `media_type` filter is about the item, not the library, so the
     * app has to ask for the item types a library can hold and then filter by
     * the library itself.
     */
    @Test
    fun every_library_is_asked_for_with_its_item_types() {
        // A TMDB anime is a "tv" item, a MAL anime an "anime" one.
        assertEquals(listOf("tv", "anime"), itemTypesOf(LibraryType.ANIME))
        assertEquals(listOf("tv"), itemTypesOf(LibraryType.TV))
        assertEquals(listOf("movie"), itemTypesOf(LibraryType.MOVIE))
        assertEquals(listOf("season"), itemTypesOf(LibraryType.SEASON))
        assertEquals(listOf("manga"), itemTypesOf("manga"))
    }

    @Test
    fun only_the_anime_library_has_to_be_asked_for_by_name() {
        // The detail routes name the library, so every library is sent.
        assertEquals("anime", LibraryType.apiParam("Anime"))
        assertEquals("tv", LibraryType.apiParam("TV"))
        assertEquals("movie", LibraryType.apiParam("movie"))
        assertNull(LibraryType.apiParam("manga"))
        assertNull(LibraryType.apiParam(null))
    }
}

class DetailsViewModelTest {

    @Test
    fun buildTrackRequest_keepsSourceAndMediaIdAndMapsMediaType() {
        val request = buildTrackRequest(
            mediaType = "movie",
            source = "tmdb",
            mediaId = "123",
            title = "Sample Movie",
            image = "https://example.com/poster.jpg",
            score = 8.5,
            status = 2,
            progress = 100.0,
        )

        assertEquals("tmdb", request.source)
        assertEquals("123", request.mediaId)
        assertEquals("movie", request.libraryMediaType)
        assertEquals(8.5, request.score ?: 0.0, 0.0)
        assertEquals(2, (request.status as? Number)?.toInt())
    }

    @Test
    fun buildTrackRequest_files_an_anime_in_the_anime_library() {
        // The item is a TMDB "tv" show, but the user is tracking it as anime:
        // the library decides which tab it shows up in.
        val request = buildTrackRequest(
            mediaType = "tv",
            source = "tmdb",
            mediaId = "253811",
            status = 1,
            progress = 0.0,
            libraryMediaType = LibraryType.ANIME,
        )

        assertEquals("anime", request.libraryMediaType)
    }

    @Test
    fun buildTrackRequest_carries_the_season_for_season_level_entries() {
        val request = buildTrackRequest(
            mediaType = "season",
            source = "tmdb",
            mediaId = "253811",
            seasonNumber = 2,
            status = 1,
            progress = 0.0,
            libraryMediaType = LibraryType.ANIME,
        )

        assertEquals(2, request.seasonNumber)
        assertEquals("anime", request.libraryMediaType)
    }
}
