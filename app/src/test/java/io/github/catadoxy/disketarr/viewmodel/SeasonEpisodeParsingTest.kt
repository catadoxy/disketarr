package io.github.catadoxy.disketarr.viewmodel

import io.github.catadoxy.disketarr.data.model.MediaStatus
import io.github.catadoxy.disketarr.data.model.SeasonSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses the real shapes returned by
 * `.../seasons/` and `.../{season}/episodes/`: a paginated envelope whose
 * `results` are tracked-media rows, with the episode's details nested under
 * `item` and its watched state in the row's own `status`.
 */
class SeasonEpisodeParsingTest {

    @Test
    fun season_numbers_come_from_the_rows_and_are_sorted() {
        val payload = linkedMapOf<String, Any?>(
            "pagination" to linkedMapOf("total" to 3),
            "results" to listOf(
                seasonRow(3),
                // Moshi hands raw-map numbers back as Double.
                seasonRow(1.0),
                seasonRow(2),
            ),
        )

        assertEquals(listOf(1, 2, 3), payload.asSeasonNumbers())
    }

    @Test
    fun seasons_carry_progress_and_the_derived_totals() {
        val payload = linkedMapOf<String, Any?>(
            "results" to listOf(
                linkedMapOf(
                    // A row is only recognised by its id/item_id, as in the real payload.
                    "id" to 13599,
                    "status" to 1,
                    "progress" to 1,
                    "episodes_left" to 0,
                    "total_episodes_left" to 11,
                    "item" to linkedMapOf("season_number" to 1),
                ),
            ),
        )

        val season = payload.asSeasonSummaries().single()

        assertEquals(1, season.seasonNumber)
        assertEquals(1, season.progress)
        // watched + everything left = the season's episode count
        assertEquals(12, season.totalEpisodes)
        // 11 left, none of them available yet -> all still to be released
        assertEquals(11, season.unreleased)
    }

    @Test
    fun unreleased_is_null_when_everything_left_has_aired() {
        val season = SeasonSummary(seasonNumber = 1, progress = 8, episodesLeft = 0, totalEpisodesLeft = 0)

        assertEquals(8, season.totalEpisodes)
        assertNull(season.unreleased)
    }

    @Test
    fun a_season_without_total_episodes_left_uses_the_aired_count() {
        // Disketarr leaves total_episodes_left null on The Witcher's seasons, which
        // previously made 2-of-8 read as "2/2".
        val season = SeasonSummary(seasonNumber = 3, progress = 2, episodesLeft = 6, totalEpisodesLeft = null)

        assertEquals(8, season.airedEpisodes)
        assertEquals(8, season.totalEpisodes)
        assertNull(season.unreleased)
    }

    @Test
    fun an_untracked_season_reports_no_total() {
        val season = SeasonSummary(seasonNumber = 0)

        assertNull(season.airedEpisodes)
        assertNull(season.totalEpisodes)
        assertNull(season.unreleased)
    }

    @Test
    fun episode_rows_carry_title_runtime_air_date_and_watched_state() {
        val payload = linkedMapOf<String, Any?>(
            "results" to listOf(
                episodeRow(number = 2, title = "Second", status = 1),
                episodeRow(number = 1, title = "Peru the Mask Maker", status = 3),
            ),
        )

        val episodes = payload.asSeasonEpisodes()

        assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
        assertEquals("Peru the Mask Maker", episodes[0].title)
        assertEquals("1x01", episodes[0].code)
        assertTrue(episodes[0].watched)
        assertFalse(episodes[1].watched)
        assertEquals(24, episodes[0].runtimeMinutes)
    }

    @Test
    fun episodes_without_a_number_are_skipped() {
        val payload = linkedMapOf<String, Any?>(
            "results" to listOf(
                linkedMapOf("item" to linkedMapOf("season_number" to 1)),
                episodeRow(number = 1, title = "Only", status = 1),
            ),
        )

        assertEquals(listOf(1), payload.asSeasonEpisodes().map { it.episodeNumber })
    }

    @Test
    fun empty_and_null_payloads_yield_empty_lists() {
        assertEquals(emptyList<Int>(), (null as Any?).asSeasonNumbers())
        assertEquals(emptyList<Int>(), (null as Any?).asSeasonEpisodes())
    }

    /**
     * Mirrors The Witcher: seasons 1-2 completed, 3 in progress, 4 unstarted.
     * Opening on season 4 (the newest) would be wrong.
     */
    @Test
    fun default_season_is_the_one_in_progress() {
        val summaries = listOf(
            SeasonSummary(1, MediaStatus.COMPLETED),
            SeasonSummary(2, MediaStatus.COMPLETED),
            SeasonSummary(3, MediaStatus.IN_PROGRESS),
            SeasonSummary(4, MediaStatus.PLANNING),
        )

        assertEquals(3, pickDefaultSeason(summaries))
    }

    @Test
    fun default_season_is_the_first_unstarted_when_nothing_is_in_progress() {
        val summaries = listOf(
            SeasonSummary(1, MediaStatus.COMPLETED),
            SeasonSummary(2, MediaStatus.PLANNING),
            SeasonSummary(3, MediaStatus.PLANNING),
        )

        assertEquals(2, pickDefaultSeason(summaries))
    }

    @Test
    fun default_season_is_the_last_one_when_the_show_is_finished() {
        val summaries = listOf(
            SeasonSummary(1, MediaStatus.COMPLETED),
            SeasonSummary(2, MediaStatus.COMPLETED),
        )

        assertEquals(null, pickDefaultSeason(summaries))
    }

    @Test
    fun default_season_is_null_without_seasons() {
        assertEquals(null, pickDefaultSeason(emptyList()))
    }

    private fun seasonRow(season: Any) = linkedMapOf<String, Any?>(
        "id" to 1,
        "item" to linkedMapOf(
            "media_id" to "295420",
            "media_type" to "season",
            "season_number" to season,
        ),
    )

    private fun episodeRow(number: Int, title: String, status: Int) = linkedMapOf<String, Any?>(
        "id" to (100 + number),
        "status" to status,
        "item" to linkedMapOf(
            "media_id" to "295420",
            "media_type" to "episode",
            "season_number" to 1,
            "episode_number" to number,
            "title" to title,
            "runtime_minutes" to 24,
            "release_datetime" to "2026-10-10T11:30:00+03:00",
        ),
    )
}
