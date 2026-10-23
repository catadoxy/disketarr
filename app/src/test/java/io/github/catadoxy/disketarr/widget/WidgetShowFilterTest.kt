package io.github.catadoxy.disketarr.widget

import io.github.catadoxy.disketarr.data.model.MediaStatus
import io.github.catadoxy.disketarr.data.model.SearchResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Shows widget lists what can be watched now. A show whose aired episodes
 * are all watched but which has more still to come has nothing to offer, so it
 * is left out until the next episode is out.
 */
class WidgetShowFilterTest {

    @Test
    fun caught_up_shows_with_announced_episodes_are_left_out() {
        assertTrue(result(status = MediaStatus.IN_PROGRESS, episodesLeft = 0, totalLeft = 12).isCaughtUpWithUpcoming())
    }

    @Test
    fun a_show_with_something_to_watch_stays() {
        assertFalse(result(status = MediaStatus.IN_PROGRESS, episodesLeft = 2, totalLeft = 12).isCaughtUpWithUpcoming())
    }

    @Test
    fun a_finished_show_is_not_caught_up_with_upcoming() {
        // Nothing aired and nothing announced: the widget does not hide it,
        // because a completed show is not "waiting for the next episode".
        assertFalse(result(status = MediaStatus.IN_PROGRESS, episodesLeft = 0, totalLeft = 0).isCaughtUpWithUpcoming())
    }

    @Test
    fun planned_shows_are_not_affected() {
        // A planned show has watched nothing, so it is not caught up - and the
        // "Planned only" filter is explicit about wanting it.
        assertFalse(result(status = MediaStatus.PLANNING, episodesLeft = 0, totalLeft = 12).isCaughtUpWithUpcoming())
    }

    @Test
    fun a_row_without_episode_counts_is_kept() {
        assertFalse(result(status = MediaStatus.IN_PROGRESS, episodesLeft = null, totalLeft = null).isCaughtUpWithUpcoming())
    }

    private fun result(status: Int, episodesLeft: Int?, totalLeft: Int?) = SearchResult(
        mediaId = "1",
        source = "tmdb",
        mediaType = "tv",
        title = "Title",
        status = status,
        episodesLeft = episodesLeft,
        totalEpisodesLeft = totalLeft,
    )
}
