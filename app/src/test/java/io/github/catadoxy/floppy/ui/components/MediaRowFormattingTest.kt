package io.github.catadoxy.floppy.ui.components

import io.github.catadoxy.floppy.data.model.MediaStatus
import io.github.catadoxy.floppy.data.model.NextEpisode
import io.github.catadoxy.floppy.data.model.SearchResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * A media row is assembled from Floppy's tracked-media fields, so the line
 * formatting is the part worth pinning down.
 */
class MediaRowFormattingTest {

    private lateinit var previousLocale: Locale

    /**
     * Month and weekday abbreviations follow the device locale - this JVM is
     * en-GB, where September is "Sept", not "Sep". Pin the locale so the
     * expectations below describe the format rather than the build machine.
     */
    @Before
    fun pinLocale() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun meta_line_leads_with_remaining_episodes() {
        val item = result(mediaType = "tv", status = MediaStatus.IN_PROGRESS, episodesLeft = 2, score = 8.5)

        assertEquals("2 remaining · 8.5 ★ · TV", metaLine(item))
    }

    @Test
    fun meta_line_falls_back_to_status_then_just_the_type() {
        assertEquals(
            "Completed · Movie",
            metaLine(result(mediaType = "movie", status = MediaStatus.COMPLETED)),
        )
        // A search hit or an upcoming episode has no status; claiming
        // "Not tracked" there would be wrong.
        assertEquals("Book", metaLine(result(mediaType = "book")))
    }

    @Test
    fun meta_line_omits_a_zero_or_missing_score() {
        assertEquals("In progress · TV", metaLine(result(mediaType = "tv", status = 1, score = 0.0)))
        assertEquals("In progress · TV", metaLine(result(mediaType = "tv", status = 1, score = null)))
    }

    @Test
    fun episode_line_is_season_x_episode_plus_the_episode_name() {
        assertEquals("3x03 Reunion", episodeLine(NextEpisode(3, 3, null, title = "Reunion")))
        assertEquals("1x01 The End's Beginning", episodeLine(NextEpisode(1, 1, null, title = "The End's Beginning")))
    }

    @Test
    fun episode_line_falls_back_when_the_name_is_missing() {
        assertEquals("2x12", episodeLine(NextEpisode(2, 12, null)))
        assertEquals("Season 2", episodeLine(NextEpisode(2, null, null)))
        assertEquals("Episode 5", episodeLine(NextEpisode(null, 5, null)))
        assertEquals("S03E03", episodeLine(NextEpisode(null, null, null, episodeCode = "S03E03")))
    }

    @Test
    fun date_line_prefers_the_next_air_date_over_last_watched() {
        val item = result(lastWatchedAt = "2026-09-13T16:09:24.228323Z")
            .copy(nextEpisode = NextEpisode(3, 3, "2023-06-29T11:59:59.999999Z"))

        assertEquals("29 Jun 2023 (Thu)", dateLine(item))
    }

    @Test
    fun date_line_formats_older_dates_with_year_and_weekday() {
        assertEquals("13 Sep 2026 (Sun)", dateLine(result(lastWatchedAt = "2026-09-13T16:09:24.228323Z")))
    }

    @Test
    fun date_line_is_relative_for_the_last_week() {
        val threeDaysAgo = LocalDate.now()
            .minusDays(3)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toString()

        assertEquals("3 days ago", dateLine(result(lastWatchedAt = threeDaysAgo)))
    }

    @Test
    fun date_line_ignores_unparseable_or_missing_values() {
        assertNull(dateLine(result(lastWatchedAt = "not-a-date")))
        assertNull(dateLine(result()))
        assertNull(dateLine(result(lastWatchedAt = "")))
    }

    @Test
    fun media_type_labels_use_readable_wording() {
        assertEquals("TV", mediaTypeLabel("tv"))
        assertEquals("Board Game", mediaTypeLabel("boardgame"))
        assertEquals("Movie", mediaTypeLabel("movie"))
        assertEquals("All", mediaTypeLabel("all"))
    }

    @Test
    fun status_labels_follow_floppys_codes() {
        assertEquals("Planning", statusLabel(MediaStatus.PLANNING))
        assertEquals("Paused", statusLabel(MediaStatus.PAUSED))
        assertEquals("Completed", statusLabel(MediaStatus.COMPLETED))
        assertEquals("Not tracked", statusLabel(null))
    }

    private fun result(
        mediaType: String = "movie",
        status: Int? = null,
        episodesLeft: Int? = null,
        score: Double? = null,
        lastWatchedAt: String? = null,
    ) = SearchResult(
        mediaId = "1",
        source = "tmdb",
        mediaType = mediaType,
        title = "Title",
        image = null,
        lastWatchedAt = lastWatchedAt,
        status = status,
        score = score,
        episodesLeft = episodesLeft,
    )
}
