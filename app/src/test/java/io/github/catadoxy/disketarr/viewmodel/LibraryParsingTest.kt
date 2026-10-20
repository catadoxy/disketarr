package io.github.catadoxy.disketarr.viewmodel

import io.github.catadoxy.disketarr.data.model.LibraryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Floppy library of a tracked entry is what tells the app which copy of a
 * title it is looking at: the same show can sit in the TV and in the Anime
 * library, each with its own status and progress.
 */
class LibraryParsingTest {

    @Test
    fun tracked_media_rows_carry_their_library() {
        val rows = trackedMediaPayload().extractRowsSafely().toSearchResults()

        assertEquals(2, rows.size)
        // A show tracked per season lives in the TV Seasons library...
        assertEquals(LibraryType.SEASON, rows[0].libraryMediaType)
        // ...and the same show in the Anime library is a separate entry.
        assertEquals(LibraryType.ANIME, rows[1].libraryMediaType)
    }

    @Test
    fun a_row_without_a_library_still_parses() {
        val payload = linkedMapOf<String, Any?>(
            "results" to listOf(
                linkedMapOf(
                    "id" to 7,
                    "item" to linkedMapOf("media_id" to "253811", "media_type" to "tv", "source" to "tmdb"),
                ),
            ),
        )

        val rows = payload.extractRowsSafely().toSearchResults()

        assertEquals(1, rows.size)
        assertNull(rows[0].libraryMediaType)
    }

    @Test
    fun calendar_items_carry_the_library_the_episode_belongs_to() {
        val items = upcomingPayload().asUpcomingItems()

        assertEquals(1, items.size)
        assertEquals(LibraryType.ANIME, items[0].libraryMediaType)
    }

    @Test
    fun season_summaries_report_the_library_they_are_tracked_in() {
        val summaries = seasonsPayload().asSeasonSummaries()

        assertEquals(2, summaries.size)
        // Tracked in the Anime library.
        assertEquals(LibraryType.ANIME, summaries[0].libraryMediaType)
        // Not tracked at all - Floppy leaves the library empty.
        assertNull(summaries[1].libraryMediaType)
    }

    @Test
    fun the_calendar_split_follows_the_library() {
        // An anime episode belongs in the Anime tab...
        assertTrue(belongsInLibrary(LibraryType.ANIME, LibraryType.ANIME))
        // ...and a TV episode in the Shows tab.
        assertTrue(belongsInLibrary(LibraryType.TV, LibraryType.TV))
        assertTrue(belongsInLibrary(LibraryType.SEASON, LibraryType.TV))
        assertFalse(belongsInLibrary(LibraryType.ANIME, LibraryType.TV))
        assertFalse(belongsInLibrary(LibraryType.TV, LibraryType.ANIME))
        assertFalse(belongsInLibrary(LibraryType.SEASON, LibraryType.ANIME))
    }

    /**
     * With the anime in the TV library there is no Anime tab, and Floppy shows
     * their episodes with the TV ones.
     */
    @Test
    fun with_the_anime_in_the_tv_library_everything_goes_to_the_tv_tab() {
        val mode = AnimeLibraryMode.TV

        assertTrue(belongsInLibrary(LibraryType.ANIME, LibraryType.TV, mode))
        assertTrue(belongsInLibrary(LibraryType.SEASON, LibraryType.TV, mode))
        assertFalse(belongsInLibrary(LibraryType.ANIME, LibraryType.ANIME, mode))
        assertTrue(belongsInLibrary(null, LibraryType.TV, mode))
    }

    @Test
    fun a_calendar_entry_without_a_library_stays_in_both_tabs() {
        assertTrue(belongsInLibrary(null, LibraryType.ANIME))
        assertTrue(belongsInLibrary(null, LibraryType.TV))
    }

    /**
     * The anime library mode decides what is in the TV library, so a page
     * cached under one mode must not be served under the other.
     */
    @Test
    fun the_cache_key_follows_the_anime_library_mode() {
        val filter = BrowseFilter()
        val tab = BrowseTab.IN_PROGRESS

        val inAnime = browseCacheKey("tv", AnimeLibraryMode.ANIME, filter, tab)
        val inTv = browseCacheKey("tv", AnimeLibraryMode.TV, filter, tab)

        assertNotEquals(inAnime, inTv)
        // The movies do not care about the anime, so their pages are shared.
        assertEquals(
            browseCacheKey("movie", AnimeLibraryMode.ANIME, filter, tab),
            browseCacheKey("movie", AnimeLibraryMode.TV, filter, tab),
        )
    }

    private fun trackedMediaPayload() = linkedMapOf<String, Any?>(
        "results" to listOf(
            linkedMapOf(
                "id" to 13281,
                "item_id" to "tv/tmdb/253811",
                "status" to 1,
                "progress" to 0,
                "episodes_left" to 27,
                "item" to linkedMapOf(
                    "media_id" to "253811",
                    "media_type" to "tv",
                    "source" to "tmdb",
                    "title" to "Tougen Anki",
                    "library_media_type" to "season",
                ),
            ),
            linkedMapOf(
                "id" to 11764,
                "item_id" to "tv/tmdb/253811",
                "status" to 1,
                "progress" to 26,
                "episodes_left" to 1,
                "item" to linkedMapOf(
                    "media_id" to "253811",
                    "media_type" to "tv",
                    "source" to "tmdb",
                    "title" to "Tougen Anki",
                    "library_media_type" to "anime",
                ),
            ),
        ),
    )

    private fun upcomingPayload() = linkedMapOf<String, Any?>(
        "results" to listOf(
            linkedMapOf(
                "id" to 12557,
                "content_number" to 5,
                "datetime" to "2026-10-18T11:30:00+03:00",
                "item" to linkedMapOf(
                    "media_id" to "253811",
                    "source" to "tmdb",
                    "media_type" to "episode",
                    "library_media_type" to "anime",
                    "title" to "Tougen Anki",
                    "image" to "https://img/tougen.jpg",
                    "url" to "/details/tmdb/tv/253811/tougen-anki/season/2",
                ),
            ),
        ),
    )

    private fun seasonsPayload() = linkedMapOf<String, Any?>(
        "results" to listOf(
            linkedMapOf(
                "season_number" to 1,
                "status" to 3,
                "progress" to 24,
                "item" to linkedMapOf(
                    "media_id" to "253811",
                    "media_type" to "season",
                    "season_number" to 1,
                    "library_media_type" to "anime",
                ),
            ),
            linkedMapOf(
                "season_number" to 3,
                "status" to null,
                "progress" to null,
                "item" to linkedMapOf(
                    "media_id" to "253811",
                    "media_type" to "season",
                    "season_number" to 3,
                ),
            ),
        ),
    )
}
