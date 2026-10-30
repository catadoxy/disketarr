package io.github.catadoxy.disketarr.viewmodel

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trims Floppy's `/api/v1/statistics/overview/` payload. The real response is
 * large - roughly two megabytes of chart sections - so the parser must ignore
 * everything it does not use and survive sections changing shape.
 */
class StatisticsParsingTest {

    @Test
    fun reads_each_media_type_with_its_numbers() {
        val overview = payload().asStatisticsOverview()

        assertEquals("All Time", overview.rangeName)
        assertEquals(listOf("Shows", "Movies", "Anime"), overview.types.map { it.label })
        assertEquals(listOf("tv", "movie", "anime"), overview.types.map { it.type })

        val shows = overview.types.first { it.type == "tv" }
        assertEquals(10, shows.tracked)
        assertEquals(9, shows.completed)
        assertEquals(100.0, shows.hours, 0.001)
        assertEquals(50, shows.plays)
        assertEquals("episodes", shows.playsLabel)
        assertEquals(5.0, shows.hoursPerMonth, 0.001)
        assertEquals(0.2, shows.hoursPerDay, 0.001)
        assertEquals(8.5, shows.averageScore!!, 0.001)

        // has_score=false hides an average even when one is serialised.
        val movies = overview.types.first { it.type == "movie" }
        assertNull(movies.averageScore)
    }

    @Test
    fun month_chart_uses_hours_not_plays() {
        val shows = payload().asStatisticsOverview().types.first { it.type == "tv" }

        // The per-type chart counts plays (1..12); the combined one is hours.
        assertEquals(listOf(10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f, 90f, 100f, 110f, 120f), shows.byMonth)
    }

    @Test
    fun reads_genres_decades_status_and_scores() {
        val shows = payload().asStatisticsOverview().types.first { it.type == "tv" }

        assertEquals("Drama", shows.topGenres.first().name)
        // Decades carry `label` instead of `name`.
        assertEquals("2010s", shows.topDecades.first().name)
        assertEquals(120, shows.topDecades.first().minutes)

        assertEquals(listOf("Completed", "In progress"), shows.statusTotals.map { it.label })
        assertEquals(listOf(9, 1), shows.statusTotals.map { it.total })

        val movies = payload().asStatisticsOverview().types.first { it.type == "movie" }
        assertEquals(listOf("Completed"), movies.statusTotals.map { it.label })
        assertEquals(listOf(40), movies.statusTotals.map { it.total })

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), shows.scoreBins)
        assertEquals(10, shows.scoreMax)
    }

    @Test
    fun drops_history_older_than_half_a_year_and_keeps_per_type_counts() {
        val heatmap = payload().asStatisticsOverview().heatmap

        assertEquals(1, heatmap.size)
        assertEquals(2, heatmap.first().count)
        assertEquals(1, heatmap.first().byType["tv"])
        assertEquals(1, heatmap.first().byType["anime"])
    }

    @Test
    fun reads_talent_and_activity() {
        val overview = payload().asStatisticsOverview()
        val shows = overview.types.first { it.type == "tv" }

        assertEquals(listOf("actors", "studios"), shows.talent.map { it.category })
        assertEquals("Toshio Furukawa", shows.talent.first().name)
        assertEquals(822, shows.talent.first().plays)
        // Studios carry a logo rather than an image.
        assertEquals("logo", shows.talent.first { it.category == "studios" }.image)

        assertEquals("Tuesday", overview.activity.mostActiveDay)
        assertEquals(27, overview.activity.mostActiveDayPercentage)
        assertEquals(2, overview.activity.currentStreak)
        assertEquals(47, overview.activity.longestStreak)
        assertEquals("2010-09-17 → 2010-11-02", overview.activity.longestStreakRange)
    }

    @Test
    fun reads_most_played_per_type() {
        val shows = payload().asStatisticsOverview().types.first { it.type == "tv" }

        assertEquals("NCIS", shows.mostWatched.first().title)
        assertEquals(508, shows.mostWatched.first().plays)
        assertEquals(22007, shows.mostWatched.first().minutes)
        assertEquals("366h 47min", shows.mostWatched.first().durations)
    }

    @Test
    fun tolerates_missing_sections_and_bad_payloads() {
        // A type needs its summary and consumption; anything else degrades to
        // "not shown" rather than failing.
        val onlyConsumption = linkedMapOf<String, Any?>(
            "statistics" to linkedMapOf(
                "consumption_stats_by_type" to linkedMapOf(
                    "tv" to consumption(hours = 10.0, plays = 5),
                ),
            ),
        )
        assertTrue(onlyConsumption.asStatisticsOverview().isEmpty)

        assertTrue((null as Any?).asStatisticsOverview().isEmpty)
        assertTrue(("nonsense" as Any?).asStatisticsOverview().isEmpty)
    }

    private fun payload(): Map<String, Any?> = linkedMapOf(
        "range" to linkedMapOf("range_name" to "All Time"),
        "statistics" to linkedMapOf(
            "summary_stats_by_type" to linkedMapOf(
                "all" to linkedMapOf("total" to 55, "completed" to 53),
                "tv" to linkedMapOf(
                    "total" to 10,
                    "completed" to 9,
                    "has_score" to true,
                    "average_score" to 8.5,
                ),
                "movie" to linkedMapOf(
                    "total" to 40,
                    "completed" to 40,
                    "has_score" to false,
                    "average_score" to 7.0,
                ),
                "anime" to linkedMapOf("total" to 5, "completed" to 4),
            ),
            "consumption_stats_by_type" to linkedMapOf(
                "tv" to consumption(hours = 100.0, plays = 50, perMonth = 5.0, perDay = 0.2),
                "movie" to consumption(hours = 80.0, plays = 40, perMonth = 4.0, perDay = 0.1),
                "anime" to consumption(hours = 20.0, plays = 10, perMonth = 1.0, perDay = 0.05),
            ),
            "tv_consumption" to linkedMapOf(
                "charts" to linkedMapOf(
                    "by_month" to chart("Episode Plays", (1..12).toList()),
                ),
                "top_genres" to listOf(
                    linkedMapOf("name" to "Drama", "minutes" to 60, "plays" to 3, "formatted_duration" to "1h"),
                ),
                "top_decades" to listOf(
                    linkedMapOf("label" to "2010s", "minutes" to 120, "plays" to 9, "formatted_duration" to "2h"),
                ),
            ),
            "combined_plays_charts" to linkedMapOf(
                "by_month" to linkedMapOf(
                    "tv" to chart("Hours", listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)),
                ),
            ),
            "status_distribution" to linkedMapOf(
                "labels" to listOf("TV Show", "Movie", "Anime"),
                "datasets" to listOf(
                    linkedMapOf("label" to "Completed", "total" to 53, "data" to listOf(9, 40, 4)),
                    linkedMapOf("label" to "In progress", "total" to 1, "data" to listOf(1, 0, 0)),
                ),
            ),
            "score_distribution" to linkedMapOf(
                "datasets" to listOf(
                    linkedMapOf("label" to "TV Show", "data" to (0..10).toList()),
                    linkedMapOf("label" to "Movie", "data" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3)),
                ),
                "scale_max" to 10,
            ),
            "top_played" to linkedMapOf(
                "tv" to listOf(
                    linkedMapOf(
                        "media" to linkedMapOf(
                            "item" to linkedMapOf("title" to "NCIS", "image" to "https://img/ncis.jpg"),
                        ),
                        "total_time_minutes" to 22007,
                        "formatted_duration" to "366h 47min",
                        "play_count" to 508,
                    ),
                ),
                "movie" to listOf(
                    linkedMapOf(
                        "media" to linkedMapOf(
                            "item" to linkedMapOf("title" to "Avatar"),
                        ),
                        "total_time_minutes" to 162,
                        "formatted_duration" to "2h 42min",
                        "play_count" to 1,
                    ),
                ),
                "anime" to emptyList<Any?>(),
            ),
            "top_talent" to linkedMapOf(
                "by_sort" to linkedMapOf(
                    "plays" to linkedMapOf(
                        "top_actors" to listOf(
                            linkedMapOf(
                                "name" to "Toshio Furukawa",
                                "image" to "https://img/actor.jpg",
                                "plays" to 822,
                                "watched_minutes" to 20726,
                                "watched_time" to "345h 26min",
                                "unique_titles" to 9,
                            ),
                        ),
                        "top_studios" to listOf(
                            linkedMapOf(
                                "name" to "Warner Bros. Television",
                                "logo" to "logo",
                                "plays" to 1966,
                                "watched_minutes" to 68941,
                                "watched_time" to "1149h 1min",
                                "unique_titles" to 18,
                            ),
                        ),
                    ),
                ),
            ),
            "activity_data" to linkedMapOf(
                "stats" to linkedMapOf(
                    "most_active_day" to "Tuesday",
                    "most_active_day_percentage" to 27,
                    "current_streak" to 2,
                    "longest_streak" to 47,
                    "longest_streak_start" to "2010-09-17",
                    "longest_streak_end" to "2010-11-02",
                ),
                "calendar_weeks" to listOf(
                    linkedMapOf(
                        "date" to LocalDate.now().minusDays(400).toString(),
                        "count" to 5,
                        "level" to 3,
                        "by_type" to linkedMapOf("tv" to 5),
                    ),
                    linkedMapOf(
                        "date" to LocalDate.now().minusDays(3).toString(),
                        "count" to 2,
                        "level" to 1,
                        "by_type" to linkedMapOf("tv" to 1, "anime" to 1),
                    ),
                ),
            ),
        ),
    )

    private fun consumption(
        hours: Double,
        plays: Int,
        perMonth: Double = 0.0,
        perDay: Double = 0.0,
    ): Map<String, Any?> = linkedMapOf(
        "primary" to linkedMapOf("total" to hours, "per_month" to perMonth, "per_day" to perDay),
        "secondary" to linkedMapOf("total" to plays),
        "has_data" to true,
    )

    private fun chart(label: String, data: List<Int>): Map<String, Any?> = linkedMapOf(
        "labels" to listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        "datasets" to listOf(linkedMapOf("label" to label, "data" to data)),
    )
}
