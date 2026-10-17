package io.github.catadoxy.disketarr.data.model

/**
 * A trimmed view of Disketarr's `/api/v1/statistics/overview/` payload.
 *
 * The real response carries ~32 chart sections and is far too large to hold or
 * re-fetch casually, so only the summary figures this app displays are kept.
 */
data class StatisticsOverview(
    val totalItems: Int = 0,
    val countsByType: List<TypeCount> = emptyList(),
    val statusTotals: List<StatusTotal> = emptyList(),
    val mostWatched: List<MostWatched> = emptyList(),
    val totalHours: Double? = null,
    val averageScore: Double? = null,
    val scoredCount: Int? = null,
    val currentStreak: Int? = null,
    val longestStreak: Int? = null,
    val longestStreakRange: String? = null,
    val mostActiveDay: String? = null,
    /** Disketarr formats these as text, e.g. "5764h 40min". */
    val watchTimeByType: List<TypeValue> = emptyList(),
) {
    data class TypeCount(val type: String, val label: String, val count: Int)

    data class TypeValue(val label: String, val value: String)

    data class StatusTotal(val label: String, val total: Int)

    data class MostWatched(
        val title: String,
        val mediaType: String,
        val image: String?,
        val plays: Int,
        val duration: String?,
        val minutes: Int,
    )

    val isEmpty: Boolean
        get() = totalItems == 0 && countsByType.isEmpty() && mostWatched.isEmpty()
}
