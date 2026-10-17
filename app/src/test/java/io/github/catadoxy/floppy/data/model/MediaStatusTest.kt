package io.github.catadoxy.floppy.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the numeric codes against Floppy's own mapping. Getting these wrong is
 * silent: the API accepts any valid code, it just stores a different status.
 */
class MediaStatusTest {

    @Test
    fun codes_match_floppys_media_status_map() {
        // src/api/helpers.py: MEDIA_STATUS_MAP
        // src/app/media_list_filters.py: MEDIA_LIST_STATUS_BY_CODE
        assertEquals(0, MediaStatus.PLANNING)
        assertEquals(1, MediaStatus.IN_PROGRESS)
        assertEquals(2, MediaStatus.PAUSED)
        assertEquals(3, MediaStatus.COMPLETED)
        assertEquals(4, MediaStatus.DROPPED)
    }

    @Test
    fun completed_is_three_and_paused_is_two() {
        // The pair that is easy to swap.
        assertTrue(MediaStatus.COMPLETED != MediaStatus.PAUSED)
        assertEquals("Completed", MediaStatus.options.first { it.first == 3 }.second)
        assertEquals("Paused", MediaStatus.options.first { it.first == 2 }.second)
    }
}
