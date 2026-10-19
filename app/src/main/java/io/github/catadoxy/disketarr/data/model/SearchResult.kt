package io.github.catadoxy.disketarr.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Provider search hit.
 *
 * `media_id` is annotated [FlexString] because Disketarr emits a bare integer from
 * the search serializers (`"media_id": 603`) while every other endpoint sends it
 * as a string. Declaring it `String` made all search decoding throw.
 *
 * The other properties are optional so a provider-specific payload (a game result
 * carries `year`/`platforms`, a movie result does not) can never fail the whole
 * response.
 */
@JsonClass(generateAdapter = true)
data class SearchResult(
    @FlexString @Json(name = "media_id") val mediaId: String = "",
    @Json(name = "source") val source: String = "",
    @Json(name = "media_type") val mediaType: String = "",
    @Json(name = "title") val title: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "last_watched_at") val lastWatchedAt: String? = null,
    @Json(name = "status") val status: Int? = null,
    @Json(name = "progress") val progress: Double? = null,
    @Json(name = "score") val score: Double? = null,
    @Json(name = "next_episode") val nextEpisode: NextEpisode? = null,
    /** First studio/broadcaster; the widget shows it as the channel next to the air time. */
    @Json(name = "studio") val studio: String? = null,
    /** Released episodes not yet watched - drives the "N remaining" row label. */
    @Json(name = "episodes_left") val episodesLeft: Int? = null,
    @Json(name = "total_episodes_left") val totalEpisodesLeft: Int? = null,
    /**
     * Parent show title for history/episode rows, whose [title] is the episode
     * name. The widget shows this on the first line, like SeriesGuide.
     */
    @Json(ignore = true) val seriesTitle: String? = null,
    /** Tracked show id for history rows, used to split history between tabs. */
    @Json(ignore = true) val showId: String? = null,
)

@JsonClass(generateAdapter = true)
data class SearchResponse(
    @Json(name = "pagination") val pagination: Pagination = Pagination(),
    @Json(name = "results") val results: List<SearchResult> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class Pagination(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "limit") val limit: Int = 0,
    @Json(name = "offset") val offset: Int = 0,
    @Json(name = "next") val next: String? = null,
    @Json(name = "previous") val previous: String? = null,
)
