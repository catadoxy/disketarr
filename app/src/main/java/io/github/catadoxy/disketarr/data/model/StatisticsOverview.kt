package io.github.catadoxy.disketarr.data.model

/**
 * A trimmed view of Floppy's `/api/v1/statistics/overview/` payload.
 *
 * The endpoint returns every chart Floppy can draw - close to two megabytes of
 * it - so the app keeps only what the Statistics screen renders: one block per
 * media type (Shows, Movies, Anime) plus the activity data that is shared by
 * all of them. Everything is read defensively in `asStatisticsOverview()`; a
 * section Floppy stops sending simply disappears from the screen.
 */
data class StatisticsOverview(
    /** "All Time", or whatever range Floppy was asked for. */
    val rangeName: String = "All Time",
    val types: List<TypeStats> = emptyList(),
    val statusTotals: List<StatusTotal> = emptyList(),
    val heatmap: List<HeatmapDay> = emptyList(),
    val activity: Activity = Activity(),
) {
    val isEmpty: Boolean
        get() = types.isEmpty()

    /** One media type's numbers: Shows, Movies or Anime. */
    data class TypeStats(
        val type: String,
        val label: String,
        /** Items in the library, as Floppy counts them. */
        val tracked: Int = 0,
        val completed: Int = 0,
        val hours: Double = 0.0,
        /** Episode plays for shows and anime, movie plays for films. */
        val plays: Int = 0,
        val playsLabel: String = "Plays",
        val hoursPerMonth: Double = 0.0,
        val hoursPerDay: Double = 0.0,
        val averageScore: Double? = null,
        /** Twelve values, January first. */
        val byMonth: List<Float> = emptyList(),
        /** Seven values, Sunday first. */
        val byWeekday: List<Float> = emptyList(),
        /** Twenty-four values, midnight first. */
        val byTimeOfDay: List<Float> = emptyList(),
        val topGenres: List<NamedValue> = emptyList(),
        val topDecades: List<NamedValue> = emptyList(),
        val mostWatched: List<MostWatched> = emptyList(),
        val talent: List<Talent> = emptyList(),
        /** Counts for this tab only, from `status_distribution`. */
        val statusTotals: List<StatusTotal> = emptyList(),
        /** Ratings 0..[scoreMax], or empty when nothing has been rated. */
        val scoreBins: List<Int> = emptyList(),
        val scoreMax: Int = 10,
    )

    data class NamedValue(
        val name: String,
        val minutes: Int = 0,
        val plays: Int = 0,
        /** Floppy's own "2916h 08min" text, when it sends one. */
        val formatted: String? = null,
    )

    data class MostWatched(
        val title: String,
        val image: String?,
        val plays: Int,
        val durations: String?,
        val minutes: Int,
    )

    data class Talent(
        /** actors, actresses, directors, writers or studios. */
        val category: String,
        val name: String,
        val image: String?,
        val plays: Int,
        val minutes: Int,
        val formatted: String?,
        val uniqueTitles: Int,
    )

    data class HeatmapDay(
        val date: String,
        val count: Int,
        val level: Int,
        val byType: Map<String, Int> = emptyMap(),
    )

    data class Activity(
        val mostActiveDay: String? = null,
        val mostActiveDayPercentage: Int? = null,
        val currentStreak: Int? = null,
        val longestStreak: Int? = null,
        val longestStreakRange: String? = null,
    )

    data class StatusTotal(val label: String, val total: Int)
}
