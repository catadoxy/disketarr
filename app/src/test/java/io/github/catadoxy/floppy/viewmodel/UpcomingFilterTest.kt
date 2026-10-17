package io.github.catadoxy.floppy.viewmodel

import io.github.catadoxy.floppy.data.model.SearchResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Floppy's calendar returns a whole month, so the Upcoming tab has to drop the
 * days that have already aired, and the Watched tab has to drop finished shows
 * that still have announced episodes.
 */
class UpcomingFilterTest {

    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun episodes_that_already_aired_are_dropped() {
        assertFalse(airsOnOrAfter("2026-10-02T17:00:00+03:00", today))
        assertFalse(airsOnOrAfter("2026-10-15T23:59:00+03:00", today))
    }

    @Test
    fun episodes_today_or_later_are_kept() {
        assertTrue(airsOnOrAfter("2026-10-16T17:00:00+03:00", today))
        assertTrue(airsOnOrAfter("2026-11-06T16:00:00+02:00", today))
    }

    @Test
    fun a_missing_or_unparseable_date_is_kept() {
        // Better to show an undated row than to silently hide a show.
        assertTrue(airsOnOrAfter(null, today))
        assertTrue(airsOnOrAfter("", today))
        assertTrue(airsOnOrAfter("not a date", today))
    }

    @Test
    fun watched_keeps_only_shows_with_nothing_left() {
        assertTrue(SearchResult(totalEpisodesLeft = null).hasNothingLeft())
        assertTrue(SearchResult(totalEpisodesLeft = 0).hasNothingLeft())
        assertFalse(SearchResult(totalEpisodesLeft = 11).hasNothingLeft())
    }
}
