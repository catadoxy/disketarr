package io.github.catadoxy.disketarr.viewmodel

import io.github.catadoxy.disketarr.data.model.DiscoverRail
import io.github.catadoxy.disketarr.data.model.LibraryMediaTypes
import io.github.catadoxy.disketarr.data.model.SearchResult
import io.github.catadoxy.disketarr.data.model.MediaStatus
import io.github.catadoxy.disketarr.data.model.NextEpisode
import io.github.catadoxy.disketarr.data.model.SeasonEpisode
import io.github.catadoxy.disketarr.data.model.SeasonSummary
import io.github.catadoxy.disketarr.data.model.StatisticsOverview
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

internal fun Any?.extractRowsSafely(): List<Map<String, Any?>> {
    // A failed or timed-out request hands us null. ArrayDeque rejects null
    // elements, so bail out here rather than in addLast.
    if (this == null) return emptyList()

    val rows = mutableListOf<Map<String, Any?>>()
    val pending = ArrayDeque<Any?>()
    val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    pending.addLast(this)

    while (pending.isNotEmpty() && rows.size < 1_000) {
        val current = pending.removeLast() ?: continue

        when (current) {
            is List<*> -> {
                if (!visited.add(current)) continue
                val directRows = current.mapNotNull { item ->
                    val map = item.toStringKeyMapOrNull() ?: return@mapNotNull null
                    map.takeIf { isRowCandidate(it) }
                }
                if (directRows.isNotEmpty()) {
                    rows += directRows
                    continue
                }

                current.asReversed().forEach { child ->
                    if (child is Map<*, *> || child is List<*>) pending.addLast(child)
                }
            }

            is Map<*, *> -> {
                if (!visited.add(current)) continue
                val map = current.toStringKeyMapOrNull() ?: continue
                if (isRowCandidate(map)) {
                    rows += map
                    continue
                }

                val preferredKeys = listOf(
                    "results",
                    "items",
                    "data",
                    "media",
                    "history",
                    "entries",
                    "tracked",
                )
                val children = preferredKeys.mapNotNull { map[it] } +
                    map.values.filter { it is Map<*, *> || it is List<*> }

                children.asReversed().forEach { child ->
                    if (child != null) pending.addLast(child)
                }
            }
        }
    }

    return rows
}

internal fun Any?.toStringKeyMapOrNull(): Map<String, Any?>? {
    val src = this as? Map<*, *> ?: return null
    val out = LinkedHashMap<String, Any?>(src.size)
    src.forEach { (k, v) ->
        val key = k?.toString() ?: return@forEach
        out[key] = v
    }
    return out
}

/** First studio/broadcaster from a raw `item` map; the widget renders it as the channel. */
private fun studioOf(item: Map<String, Any?>?): String? =
    (item?.get("studios") as? List<*>)
        ?.firstOrNull()
        ?.toString()
        ?.takeIf { it.isNotBlank() }

private fun isRowCandidate(map: Map<String, Any?>): Boolean {
    return map["media_id"] != null ||
        map["item_id"] != null ||
        map["id"] != null ||
        map["source"] != null ||
        map["media_type"] != null ||
        map["library_media_type"] != null
}

/**
 * Moshi decodes numbers inside a raw `Map<String, Any?>` as [Double], so
 * `3` arrives as `3.0`. `"3.0".toIntOrNull()` is null, which silently blanked
 * every integer field parsed from these payloads (media status in particular).
 * These helpers accept either a [Number] or numeric text.
 */
internal fun Any?.asIntOrNull(): Int? = when (this) {
    is Number -> toInt()
    is String -> trim().toIntOrNull() ?: trim().toDoubleOrNull()?.toInt()
    else -> null
}

internal fun Any?.asDoubleOrNull(): Double? = when (this) {
    is Number -> toDouble()
    is String -> trim().toDoubleOrNull()
    else -> null
}


/**
 * Season numbers from `.../seasons/`. That endpoint returns tracked-media rows
 * whose item carries the season number - unlike the show detail payload, which
 * only exposes an aggregate season count.
 */
internal fun Any?.asSeasonNumbers(): List<Int> =
    asSeasonSummaries().map { it.seasonNumber }

internal fun Any?.asSeasonSummaries(): List<SeasonSummary> =
    extractRowsSafely()
        .mapNotNull { row ->
            val seasonNumber = (
                row["item"].toStringKeyMapOrNull()?.get("season_number")
                    ?: row["season_number"]
                ).asIntOrNull() ?: return@mapNotNull null
            val item = row["item"].toStringKeyMapOrNull()
            SeasonSummary(
                seasonNumber = seasonNumber,
                status = row["status"].asIntOrNull(),
                progress = row["progress"].asIntOrNull(),
                episodesLeft = row["episodes_left"].asIntOrNull(),
                totalEpisodesLeft = row["total_episodes_left"].asIntOrNull(),
                nextEpisode = row["next_episode"].asNextEpisodeOrNull(),
                // Floppy reports the library on a tracked season and leaves it
                // empty on an untracked one.
                libraryMediaType = (
                    item?.get("library_media_type") ?: row["library_media_type"]
                    )?.toString()?.takeIf { it.isNotBlank() },
            )
        }
        .distinctBy { it.seasonNumber }
        .sortedBy { it.seasonNumber }

/**
 * Which season to open a show on.
 *
 * `next_episode_season` on the show payload is the next *airing* season, not the
 * one being watched, so it lands on a season the user has not started. The
 * in-progress season is the right target; failing that, the first unstarted
 * season, and finally the latest (a fully watched show).
 */
internal fun pickDefaultSeason(summaries: List<SeasonSummary>): Int? {
    // The season being watched is the one with the most watched episodes among
    // the unfinished ones. The specials usually have none, so they do not win
    // over the season the user is actually on.
    val unfinished = summaries.filter {
        (it.episodesLeft ?: 0) > 0 || it.status == MediaStatus.IN_PROGRESS
    }
    unfinished.maxByOrNull { it.progress ?: 0 }?.let { return it.seasonNumber }

    // Nothing is in progress: the first season that still has something to
    // watch, and otherwise the first unstarted one.
    return summaries.firstOrNull {
        (it.episodesLeft ?: 0) > 0 || it.status == MediaStatus.IN_PROGRESS || it.status == MediaStatus.PLANNING
    }?.seasonNumber
}

/**
 * Episodes from `.../{season}/episodes/`. [fallbackSeason] is used when a row
 * omits `season_number`, which happens for episodes that are not individually
 * tracked - without it those rows render as "1x03" on season 3.
 */
internal fun Any?.asSeasonEpisodes(fallbackSeason: Int? = null): List<SeasonEpisode> =
    extractRowsSafely()
        .mapNotNull { row ->
            val item = row["item"].toStringKeyMapOrNull() ?: return@mapNotNull null
            val episodeNumber = item["episode_number"].asIntOrNull() ?: return@mapNotNull null
            SeasonEpisode(
                seasonNumber = item["season_number"].asIntOrNull() ?: fallbackSeason,
                episodeNumber = episodeNumber,
                title = item["title"]?.toString()?.takeIf { it.isNotBlank() },
                airDate = item["release_datetime"]?.toString()?.takeIf { it.isNotBlank() },
                runtimeMinutes = item["runtime_minutes"].asIntOrNull()?.takeIf { it in 1..600 },
                status = row["status"].asIntOrNull(),
                watched = row["status"].asIntOrNull() == MediaStatus.COMPLETED,
            )
        }
        .distinctBy { it.seasonNumber to it.episodeNumber }
        .sortedBy { it.episodeNumber }

/**
 * Trims Floppy's statistics payload down to what the Statistics screen shows.
 *
 * Everything is read defensively: the endpoint is large (roughly two megabytes)
 * and its chart sections change shape between Floppy releases, so a missing or
 * unexpected section degrades to "not shown" rather than failing the screen.
 */
internal fun Any?.asStatisticsOverview(): StatisticsOverview {
    val root = this.toStringKeyMapOrNull() ?: return StatisticsOverview()
    val stats = root["statistics"].toStringKeyMapOrNull() ?: return StatisticsOverview()

    val rangeName = root["range"].toStringKeyMapOrNull()
        ?.get("range_name")?.toString()?.takeIf { it.isNotBlank() }
        ?: "All Time"

    val summaries = stats["summary_stats_by_type"].toStringKeyMapOrNull().orEmpty()
    val consumption = stats["consumption_stats_by_type"].toStringKeyMapOrNull().orEmpty()
    val talentBySort = stats["top_talent"].toStringKeyMapOrNull()
        ?.get("by_sort").toStringKeyMapOrNull()
        ?.get("plays").toStringKeyMapOrNull()

    val scoreDatasets = (stats["score_distribution"].toStringKeyMapOrNull()?.get("datasets") as? List<*>)
        .orEmpty()
        .mapNotNull { it.toStringKeyMapOrNull() }

    val statusTotals = (stats["status_distribution"].toStringKeyMapOrNull()?.get("datasets") as? List<*>)
        .orEmpty()
        .mapNotNull { row ->
            val dataset = row.toStringKeyMapOrNull() ?: return@mapNotNull null
            val label = dataset["label"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            dataset["total"].asIntOrNull()?.takeIf { it > 0 }?.let { StatisticsOverview.StatusTotal(label, it) }
        }

    val statusDistribution = stats["status_distribution"].toStringKeyMapOrNull()
    val statusLabels = (statusDistribution?.get("labels") as? List<*>)
        .orEmpty()
        .map { it?.toString()?.lowercase().orEmpty() }
    val statusDatasets = (statusDistribution?.get("datasets") as? List<*>)
        .orEmpty()
        .mapNotNull { it.toStringKeyMapOrNull() }

    val types = MEDIA_TYPES.mapNotNull { (type, label, playsLabel) ->
        val summary = summaries[type].toStringKeyMapOrNull() ?: return@mapNotNull null
        val media = consumption[type].toStringKeyMapOrNull() ?: return@mapNotNull null
        val primary = media["primary"].toStringKeyMapOrNull().orEmpty()
        val secondary = media["secondary"].toStringKeyMapOrNull().orEmpty()
        val consumptionSection = stats["${type}_consumption"].toStringKeyMapOrNull().orEmpty()
        val charts = consumptionSection["charts"].toStringKeyMapOrNull().orEmpty()
        // The per-type charts count plays; the combined charts measure hours,
        // which is what the screen shows. Fall back to plays when Floppy does
        // not send the combined section.
        val combined = stats["combined_plays_charts"].toStringKeyMapOrNull().orEmpty()
        fun hoursChart(key: String): List<Float> =
            combined[key].toStringKeyMapOrNull()?.get(type).asChartPoints()

        // The status chart is split by media type: labels carry the types and
        // every dataset is one status with a count per type.
        val statusIndex = statusLabels.indexOfFirst { it == SCORE_LABELS[type] }
        val typeStatus = if (statusIndex >= 0) {
            statusDatasets.mapNotNull { dataset ->
                val statusLabel = dataset["label"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val value = (dataset["data"] as? List<*>)?.getOrNull(statusIndex).asIntOrNull()
                    ?: return@mapNotNull null
                value.takeIf { it > 0 }?.let { StatisticsOverview.StatusTotal(statusLabel, it) }
            }
        } else {
            emptyList()
        }

        val tracked = summary["total"].asIntOrNull() ?: 0
        val completed = summary["completed"].asIntOrNull() ?: 0
        val hours = primary["total"].asDoubleOrNull() ?: 0.0
        if (tracked == 0 && completed == 0 && hours <= 0.0) return@mapNotNull null

        val listedScores = (scoreDatasets.firstOrNull {
            it["label"]?.toString()?.lowercase() == SCORE_LABELS[type]
        }?.get("data") as? List<*>).orEmpty().mapNotNull { it.asIntOrNull() }

        StatisticsOverview.TypeStats(
            type = type,
            label = label,
            tracked = tracked,
            completed = completed,
            hours = hours,
            plays = secondary["total"].asIntOrNull() ?: 0,
            playsLabel = playsLabel,
            hoursPerMonth = primary["per_month"].asDoubleOrNull() ?: 0.0,
            hoursPerDay = primary["per_day"].asDoubleOrNull() ?: 0.0,
            // `has_score` is false when nothing in the library has a score, even
            // though an average may still be serialised.
            averageScore = summary["average_score"].asDoubleOrNull()
                ?.takeIf { summary["has_score"] != false },
            byMonth = hoursChart("by_month").ifEmpty { charts["by_month"].asChartPoints() },
            byWeekday = hoursChart("by_weekday").ifEmpty { charts["by_weekday"].asChartPoints() },
            byTimeOfDay = hoursChart("by_time_of_day").ifEmpty { charts["by_time_of_day"].asChartPoints() },
            topGenres = (consumptionSection["top_genres"] as? List<*>).orEmpty()
                .mapNotNull { it.asNamedValue() }
                .sortedByDescending { it.minutes }
                .take(6),
            topDecades = (consumptionSection["top_decades"] as? List<*>).orEmpty()
                .mapNotNull { it.asNamedValue() }
                .sortedByDescending { it.minutes }
                .take(5),
            mostWatched = (stats["top_played"].toStringKeyMapOrNull()?.get(type) as? List<*>).orEmpty()
                .mapNotNull { it.asMostWatched() }
                .sortedByDescending { it.minutes }
                .take(5),
            talent = TALENT_CATEGORIES.flatMap { (category, key) ->
                (talentBySort?.get(key) as? List<*>).orEmpty()
                    .mapNotNull { it.asTalent(category) }
                    .take(5)
            },
            statusTotals = typeStatus,
            scoreBins = listedScores,
            scoreMax = stats["score_distribution"].toStringKeyMapOrNull()?.get("scale_max").asIntOrNull() ?: 10,
        )
    }

    val activityStats = stats["activity_data"].toStringKeyMapOrNull()
        ?.get("stats").toStringKeyMapOrNull()
        ?: summaries["all"].toStringKeyMapOrNull()

    val streakStart = activityStats?.get("longest_streak_start")?.toString()?.takeIf { it.isNotBlank() }
    val streakEnd = activityStats?.get("longest_streak_end")?.toString()?.takeIf { it.isNotBlank() }

    val today = LocalDate.now()
    // The calendar starts at the oldest play in the library - forty years for
    // someone who has imported their history - so keep only the last half year.
    val heatmap = (stats["activity_data"].toStringKeyMapOrNull()?.get("calendar_weeks") as? List<*>)
        .orEmpty()
        .mapNotNull { row ->
            val day = row.toStringKeyMapOrNull() ?: return@mapNotNull null
            val date = day["date"]?.toString()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            if (date.isBefore(today.minusDays(HEATMAP_DAYS))) return@mapNotNull null
            StatisticsOverview.HeatmapDay(
                date = date.toString(),
                count = day["count"].asIntOrNull() ?: 0,
                level = day["level"].asIntOrNull() ?: 0,
                byType = (day["by_type"] as? Map<*, *>).orEmpty()
                    .mapNotNull { (type, value) ->
                        val name = type?.toString() ?: return@mapNotNull null
                        val amount = value.asIntOrNull() ?: return@mapNotNull null
                        name to amount
                    }
                    .toMap(),
            )
        }

    return StatisticsOverview(
        rangeName = rangeName,
        types = types,
        statusTotals = statusTotals,
        heatmap = heatmap,
        activity = StatisticsOverview.Activity(
            mostActiveDay = activityStats?.get("most_active_day")?.toString()?.takeIf { it.isNotBlank() },
            mostActiveDayPercentage = activityStats?.get("most_active_day_percentage").asIntOrNull(),
            currentStreak = activityStats?.get("current_streak").asIntOrNull(),
            longestStreak = activityStats?.get("longest_streak").asIntOrNull(),
            longestStreakRange = if (streakStart != null && streakEnd != null) "$streakStart → $streakEnd" else null,
        ),
    )
}

/** The media types the app knows how to display, in tab order. */
private val MEDIA_TYPES = listOf(
    Triple("tv", "Shows", "episodes"),
    Triple("movie", "Movies", "plays"),
    Triple("anime", "Anime", "episodes"),
)

/** Dataset labels inside `score_distribution`, lowercased. */
private val SCORE_LABELS = mapOf(
    "tv" to "tv show",
    "movie" to "movie",
    "anime" to "anime",
)

/** Talent keys inside `top_talent.by_sort.plays`. */
private val TALENT_CATEGORIES = listOf(
    "actors" to "top_actors",
    "actresses" to "top_actresses",
    "directors" to "top_directors",
    "writers" to "top_writers",
    "studios" to "top_studios",
)

private const val HEATMAP_DAYS = 182L

/**
 * A chart dataset as numbers. Floppy sends the per-type charts as a list of
 * numbers and the combined charts as one space-separated string, so both shapes
 * are accepted.
 */
private fun Any?.asChartPoints(): List<Float> {
    val dataset = (toStringKeyMapOrNull()?.get("datasets") as? List<*>)
        ?.firstOrNull()
        .toStringKeyMapOrNull()
        ?: return emptyList()
    return when (val data = dataset["data"]) {
        is List<*> -> data.mapNotNull { it.asDoubleOrNull()?.toFloat() }
        is String -> data.trim().split(' ').mapNotNull { it.toFloatOrNull() }
        else -> emptyList()
    }
}

private fun Any?.asNamedValue(): StatisticsOverview.NamedValue? {
    val row = toStringKeyMapOrNull() ?: return null
    // Genres carry `name`; decades carry `label`.
    val name = (row["name"] ?: row["label"])?.toString()?.takeIf { it.isNotBlank() } ?: return null
    return StatisticsOverview.NamedValue(
        name = name,
        minutes = row["minutes"].asIntOrNull() ?: 0,
        plays = row["plays"].asIntOrNull() ?: 0,
        formatted = row["formatted_duration"]?.toString()?.takeIf { it.isNotBlank() },
    )
}

private fun Any?.asMostWatched(): StatisticsOverview.MostWatched? {
    val entry = toStringKeyMapOrNull() ?: return null
    val item = entry["media"].toStringKeyMapOrNull()?.get("item").toStringKeyMapOrNull() ?: return null
    val title = item["title"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
    return StatisticsOverview.MostWatched(
        title = title,
        image = item["image"]?.toString(),
        plays = entry["play_count"].asIntOrNull() ?: 0,
        durations = entry["formatted_duration"]?.toString()?.takeIf { it.isNotBlank() },
        minutes = entry["total_time_minutes"].asIntOrNull() ?: 0,
    )
}

private fun Any?.asTalent(category: String): StatisticsOverview.Talent? {
    val row = toStringKeyMapOrNull() ?: return null
    val name = row["name"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
    return StatisticsOverview.Talent(
        category = category,
        name = name,
        // Studios carry a `logo`; people carry an `image`.
        image = (row["image"] ?: row["logo"])?.toString()?.takeIf { it.isNotBlank() },
        plays = row["plays"].asIntOrNull() ?: 0,
        minutes = row["watched_minutes"].asIntOrNull() ?: 0,
        formatted = row["watched_time"]?.toString()?.takeIf { it.isNotBlank() },
        uniqueTitles = row["unique_titles"].asIntOrNull() ?: 0,
    )
}

/**
 * Discover rows: `{rows: [{title, why, items: [...]}]}`. Items carry the same
 * fields as a search hit, so they map straight onto [SearchResult].
 */
internal fun Any?.asDiscoverRails(): List<DiscoverRail> {
    val root = this.toStringKeyMapOrNull() ?: return emptyList()
    return (root["rows"] as? List<*>).orEmpty().mapNotNull { rowAny ->
        val row = rowAny.toStringKeyMapOrNull() ?: return@mapNotNull null
        val title = row["title"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val items = (row["items"] as? List<*>).orEmpty().mapNotNull { itemAny ->
            val item = itemAny.toStringKeyMapOrNull() ?: return@mapNotNull null
            val mediaId = item["media_id"]?.toString()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            SearchResult(
                mediaId = mediaId,
                source = item["source"]?.toString().orEmpty(),
                mediaType = item["media_type"]?.toString().orEmpty(),
                title = item["title"]?.toString()?.takeIf { it.isNotBlank() },
                image = item["image"]?.toString()?.takeIf { it.isNotBlank() },
            )
        }
        if (items.isEmpty()) return@mapNotNull null
        DiscoverRail(
            title = title,
            summary = row["why"]?.toString()?.takeIf { it.isNotBlank() },
            items = items,
        )
    }
}

/**
 * Upcoming rows. The row nests the episode under `item` and carries the air
 * moment in `datetime` / `content_number`.
 */
internal fun Any?.asUpcomingItems(): List<SearchResult> {
    val root = this.toStringKeyMapOrNull() ?: return emptyList()
    return (root["results"] as? List<*>).orEmpty().mapNotNull { rowAny ->
        val row = rowAny.toStringKeyMapOrNull() ?: return@mapNotNull null
        val item = row["item"].toStringKeyMapOrNull() ?: return@mapNotNull null
        val mediaId = item["media_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val airDate = row["datetime"]?.toString()?.takeIf { it.isNotBlank() }
        val episodeNumber = item["episode_number"].asIntOrNull()
            ?: row["content_number"].asIntOrNull()
        val seasonNumber = item["season_number"].asIntOrNull()
            ?: seasonFromUrl(item["url"]?.toString())

        SearchResult(
            mediaId = mediaId,
            source = item["source"]?.toString().orEmpty(),
            mediaType = item["media_type"]?.toString().orEmpty(),
            title = item["title"]?.toString()?.takeIf { it.isNotBlank() },
            image = item["image"]?.toString()?.takeIf { it.isNotBlank() },
            lastWatchedAt = airDate,
            studio = studioOf(item),
            // The calendar is not media-type specific, so the library is what
            // separates an anime episode from a TV one.
            libraryMediaType = item["library_media_type"]?.toString()?.takeIf { it.isNotBlank() },
            nextEpisode = NextEpisode(
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                airDate = airDate,
            ).takeIf { seasonNumber != null || episodeNumber != null },
        )
    }
}

/** `.../tv/253811/tougen-anki/season/2` -> 2 */
private fun seasonFromUrl(url: String?): Int? {
    val parts = url.orEmpty().split('/')
    val index = parts.indexOf("season")
    return if (index >= 0) parts.getOrNull(index + 1).asIntOrNull() else null
}

/**
 * True when an episode airs on [today] or later. Disketarr's calendar covers a whole
 * month, so without this the Upcoming tab lists episodes that already aired.
 * An unparseable or missing date is kept rather than silently dropped.
 */
internal fun airsOnOrAfter(rawAirDate: String?, today: LocalDate): Boolean {
    if (rawAirDate.isNullOrBlank()) return true
    val date = runCatching { OffsetDateTime.parse(rawAirDate).toLocalDate() }.getOrNull()
        ?: return true
    return !date.isBefore(today)
}

/**
 * Flat history rows (`?flat=1`). One entry per play, so the list reads like a
 * diary. Only the kinds a Shows/Movies screen cares about are kept.
 */
internal fun Any?.asHistoryItems(mediaTypes: Set<String>): List<SearchResult> {
    val root = this.toStringKeyMapOrNull() ?: return emptyList()

    // Floppy can return either a flat list (`flat=1`) or day-grouped buckets.
    // Accept both so a missed/ignored `flat` parameter does not empty the tab.
    val topRows: List<*> = (root["results"] as? List<*>)
        ?: root.values.filterIsInstance<List<*>>().flatten()

    // Day-grouped history wraps entries as {date, weekday, entries:[...]}.
    val rows: List<*> = topRows.flatMap { candidate ->
        val bucket = candidate.toStringKeyMapOrNull()
        (bucket?.get("entries") as? List<*>) ?: listOf(candidate)
    }

    return rows.mapNotNull { rowAny ->
        val row = rowAny.toStringKeyMapOrNull() ?: return@mapNotNull null
        val item = row["item"].toStringKeyMapOrNull() ?: return@mapNotNull null

        // Episodes belong to their show, so a Shows history keeps episode and
        // season entries too - they are the show's activity.
        val rawType = (row["media_type"] ?: item["media_type"])?.toString()?.lowercase()
        if (rawType == null || rawType !in mediaTypes) return@mapNotNull null

        val mediaId = item["media_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val season = row["season_number"].asIntOrNull() ?: item["season_number"].asIntOrNull()
        val episode = row["episode_number"].asIntOrNull() ?: item["episode_number"].asIntOrNull()

        SearchResult(
            mediaId = mediaId,
            source = item["source"]?.toString().orEmpty(),
            mediaType = rawType,
            title = (
                row["display_title"]
                    ?: item["series_name"]
                    ?: row["show"].toStringKeyMapOrNull()?.get("title")
                    ?: item["title"]
                )?.toString()?.takeIf { it.isNotBlank() },
            image = (
                row["poster"]?.toString()
                    ?: item["image"]?.toString()
                    ?: row["show"].toStringKeyMapOrNull()?.get("image")?.toString()
                )?.takeIf { !it.isNullOrBlank() },
            lastWatchedAt = row["played_at_local"]?.toString(),
            showId = row["show"].toStringKeyMapOrNull()?.get("id")?.toString(),
            seriesTitle = (
                row["show"].toStringKeyMapOrNull()?.get("title")
                    ?: item["series_name"]
                )?.toString()?.takeIf { it.isNotBlank() },
            studio = studioOf(item),
            nextEpisode = NextEpisode(
                seasonNumber = season,
                episodeNumber = episode,
                title = row["title"]?.toString()?.takeIf { it.isNotBlank() },
                episodeCode = row["episode_code"]?.toString(),
            ).takeIf { season != null || episode != null },
        )
    }
}

/**
 * Reads `pagination.total` out of a raw list envelope. The API returns the full
 * library size here, which is much larger than the single page of rows.
 */internal fun Any?.asPaginationTotalOrNull(): Int? {
    val map = this as? Map<*, *> ?: return null
    val pagination = map.entries
        .firstOrNull { it.key?.toString() == "pagination" }
        ?.value as? Map<*, *> ?: return null
    return pagination.entries
        .firstOrNull { it.key?.toString() == "total" }
        ?.value
        .asIntOrNull()
}

/**
 * Reads a `next_episode` object out of a raw map. Disketarr nests it inside the
 * tracked row both in the media list and in history entries.
 */
internal fun Any?.asNextEpisodeOrNull(): NextEpisode? {
    val map = this as? Map<*, *> ?: return null
    fun value(key: String): Any? = map.entries.firstOrNull { it.key?.toString() == key }?.value
    return NextEpisode(
        seasonNumber = value("season_number").asIntOrNull(),
        episodeNumber = value("episode_number").asIntOrNull(),
        airDate = value("air_date")?.toString(),
        title = value("title")?.toString(),
        episodeCode = value("episode_code")?.toString(),
    ).takeIf { it.seasonNumber != null || it.episodeNumber != null || !it.airDate.isNullOrBlank() }
}


internal fun List<Map<String, Any?>>.toSearchResults(): List<SearchResult> {
    return mapNotNull { tracked ->
        val item = tracked["item"].toStringKeyMapOrNull()

        val mediaId = (
            item?.get("media_id") ?: item?.get("id")
                ?: tracked["media_id"] ?: tracked["id"] ?: tracked["item_id"]
            )?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

        val source = (
            item?.get("source") ?: tracked["source"]
            )?.toString()?.takeIf { it.isNotBlank() } ?: "unknown"

        // Label rows with media_type ("tv"). library_media_type can be a
        // tracking granularity - The Vermilion Mask is recorded as "season" -
        // which printed "Season" on a Shows row. Routing still maps
        // season/episode back to the tv endpoint via detailRouteMediaType().
        val rawMediaType = (
            item?.get("media_type") ?: item?.get("library_media_type")
                ?: tracked["media_type"] ?: tracked["library_media_type"]
            )?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

        SearchResult(
            mediaId = mediaId,
            source = source,
            mediaType = rawMediaType.lowercase(),
            title = (item?.get("title") ?: tracked["title"])?.toString()?.takeIf { it.isNotBlank() },
            image = (item?.get("image") ?: tracked["image"])?.toString()?.takeIf { it.isNotBlank() },
            lastWatchedAt = (tracked["progressed_at"] ?: item?.get("progressed_at"))?.toString(),
            status = (tracked["status"] ?: item?.get("status")).asIntOrNull(),
            progress = (tracked["progress"] ?: item?.get("progress")).asDoubleOrNull(),
            score = (tracked["score"] ?: item?.get("score")).asDoubleOrNull(),
            nextEpisode = (tracked["next_episode"] ?: item?.get("next_episode")).asNextEpisodeOrNull(),
            studio = studioOf(item),
            episodesLeft = (tracked["episodes_left"] ?: item?.get("episodes_left")).asIntOrNull(),
            totalEpisodesLeft = (tracked["total_episodes_left"] ?: item?.get("total_episodes_left")).asIntOrNull(),
            // Which library this entry lives in. The same title can be tracked
            // in the TV and the Anime library at once, and only this tells the
            // two copies apart.
            libraryMediaType = (
                item?.get("library_media_type") ?: tracked["library_media_type"]
                )?.toString()?.takeIf { it.isNotBlank() },
            // The id of the tracked entry, which a history row points at.
            trackedId = tracked["id"]?.toString(),
        )
    }.distinctBy { "${it.source}|${it.mediaType}|${it.mediaId}|${it.libraryMediaType.orEmpty()}" }
}

