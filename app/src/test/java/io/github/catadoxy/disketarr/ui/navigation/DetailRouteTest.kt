package io.github.catadoxy.disketarr.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailRouteTest {

    @Test
    fun season_rows_open_their_parent_show_detail_route() {
        assertEquals("tv", detailRouteMediaType("season"))
    }

    @Test
    fun episode_rows_open_their_parent_show_detail_route() {
        assertEquals("tv", detailRouteMediaType("episode"))
    }

    @Test
    fun supported_top_level_media_types_keep_their_type() {
        assertEquals("tv", detailRouteMediaType("tv"))
        assertEquals("movie", detailRouteMediaType("movie"))
    }

    /**
     * The path segment is the item type. A TMDB anime is a "tv" item, while a
     * MyAnimeList anime really is an "anime" item.
     */
    @Test
    fun an_anime_is_a_tv_item_unless_it_comes_from_myanimelist() {
        assertEquals("tv", detailRouteMediaType("anime", "tmdb"))
        assertEquals("anime", detailRouteMediaType("anime", "mal"))
    }
}
