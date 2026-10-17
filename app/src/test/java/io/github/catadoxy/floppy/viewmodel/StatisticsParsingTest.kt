package io.github.catadoxy.floppy.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trims Floppy's `/api/v1/statistics/overview/` payload. The real response is
 * large with ~32 chart sections, so the parser must ignore everything it does not
 * use and survive sections changing shape.
 */
class StatisticsParsingTest {

    @Test
    fun reads_totals_type_counts_and_status_breakdown() {
        val overview = payload().asStatisticsOverview()

        assertEquals(1598, overview.totalItems)
        // "total" is not a media type, and zero-count types are dropped.
        assertEquals(listOf("Movies", "Seasons", "TV"), overview.countsByType.map { it.label })
        assertEquals(listOf(701, 638, 259), overview.countsByType.map { it.count })
        assertEquals(listOf("Completed", "In progress"), overview.statusTotals.map { it.label })
        assertEquals(listOf(1562, 36), overview.statusTotals.map { it.total })
    }

    @Test
    fun collects_most_watched_across_types_by_time_and_limits_to_ten() {
        val overview = payload().asStatisticsOverview()

        assertEquals("NCIS", overview.mostWatched.first().title)
        assertEquals(22007, overview.mostWatched.first().minutes)
        assertEquals("366h 47min", overview.mostWatched.first().duration)
        assertEquals(508, overview.mostWatched.first().plays)
        assertTrue(overview.mostWatched.size <= 10)
    }

    @Test
    fun reads_total_watch_hours_from_the_numeric_summary() {
        // hours_per_media_type is formatted text; total_minutes is the real number.
        assertEquals(429394 / 60.0, payload().asStatisticsOverview().totalHours!!, 0.001)
    }

    @Test
    fun falls_back_to_a_numeric_hours_map_when_the_summary_is_absent() {
        val noSummary = payload()
        (noSummary["statistics"] as MutableMap<String, Any?>).remove("summary_stats_by_type")

        assertEquals(42.5, noSummary.asStatisticsOverview().totalHours!!, 0.001)
    }

    @Test
    fun tolerates_missing_sections() {
        val bare = linkedMapOf<String, Any?>("statistics" to linkedMapOf<String, Any?>("media_count" to linkedMapOf("total" to 3)))

        val overview = bare.asStatisticsOverview()

        assertEquals(3, overview.totalItems)
        assertTrue(overview.countsByType.isEmpty())
        assertTrue(overview.statusTotals.isEmpty())
        assertTrue(overview.mostWatched.isEmpty())
        assertNull(overview.totalHours)
    }

    @Test
    fun tolerates_a_null_or_unexpected_payload() {
        assertTrue((null as Any?).asStatisticsOverview().isEmpty)
        assertTrue(("nonsense" as Any?).asStatisticsOverview().isEmpty)
    }

    private fun payload() = linkedMapOf<String, Any?>(
        "range" to linkedMapOf("range_name" to "All Time"),
        "statistics" to linkedMapOf(
            "media_count" to linkedMapOf("total" to 1598, "movie" to 701, "season" to 638, "tv" to 259, "game" to 0),
            "status_distribution" to linkedMapOf(
                "datasets" to listOf(
                    linkedMapOf("label" to "Completed", "total" to 1562),
                    linkedMapOf("label" to "In progress", "total" to 36),
                    linkedMapOf("label" to "Planning", "total" to 0),
                ),
            ),
            "hours_per_media_type" to linkedMapOf("tv" to 40.0, "movie" to 2.5),
            "summary_stats_by_type" to linkedMapOf(
                "all" to linkedMapOf("completed" to 1562, "total" to 1598, "total_minutes" to 429394),
            ),
            "top_played" to linkedMapOf(
                "tv" to listOf(
                    linkedMapOf(
                        "media" to linkedMapOf(
                            "item" to linkedMapOf(
                                "title" to "NCIS",
                                "image" to "https://img/ncis.jpg",
                                "media_type" to "tv",
                            ),
                        ),
                        "total_time_minutes" to 22007,
                        "formatted_duration" to "366h 47min",
                        "play_count" to 508,
                    ),
                ),
                "movie" to listOf(
                    linkedMapOf(
                        "media" to linkedMapOf(
                            "item" to linkedMapOf("title" to "Avatar", "media_type" to "movie"),
                        ),
                        "total_time_minutes" to 162,
                        "formatted_duration" to "2h 42min",
                        "play_count" to 1,
                    ),
                ),
            ),
        ),
    )
}
