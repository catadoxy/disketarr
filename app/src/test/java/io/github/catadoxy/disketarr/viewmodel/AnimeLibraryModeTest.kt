package io.github.catadoxy.disketarr.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Floppy decides whether the anime live in their own library, and the app's
 * tabs follow that setting.
 */
class AnimeLibraryModeTest {

    @Test
    fun the_anime_library_exists_unless_the_anime_are_in_the_tv_library() {
        assertTrue(AnimeLibraryMode.hasAnimeLibrary(AnimeLibraryMode.ANIME))
        assertTrue(AnimeLibraryMode.hasAnimeLibrary(AnimeLibraryMode.BOTH))
        assertFalse(AnimeLibraryMode.hasAnimeLibrary(AnimeLibraryMode.TV))
        // An unknown mode keeps the Anime tab - better an empty tab than a
        // missing one.
        assertTrue(AnimeLibraryMode.hasAnimeLibrary(null))
    }

    @Test
    fun the_anime_tab_follows_the_setting() {
        val selected = setOf("movie", "tv", "anime")

        // Anime in the Anime library: all three tabs.
        assertEquals(
            selected,
            AnimeLibraryMode.effectiveCategories(selected, AnimeLibraryMode.ANIME),
        )
        // Anime in the TV library: the Anime tab has nothing to show.
        assertEquals(
            setOf("movie", "tv"),
            AnimeLibraryMode.effectiveCategories(selected, AnimeLibraryMode.TV),
        )
    }

    @Test
    fun the_anime_tab_comes_back_when_the_setting_changes_again() {
        val selected = setOf("movie", "tv", "anime")

        val inTv = AnimeLibraryMode.effectiveCategories(selected, AnimeLibraryMode.TV)
        val inAnime = AnimeLibraryMode.effectiveCategories(selected, AnimeLibraryMode.ANIME)

        assertFalse("anime" in inTv)
        assertTrue("anime" in inAnime)
    }
}
