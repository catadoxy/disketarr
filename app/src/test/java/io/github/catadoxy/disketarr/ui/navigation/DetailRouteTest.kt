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
        assertEquals("anime", detailRouteMediaType("anime"))
    }
}
