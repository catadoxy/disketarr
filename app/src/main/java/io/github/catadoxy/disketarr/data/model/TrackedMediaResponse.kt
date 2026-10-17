package io.github.catadoxy.disketarr.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response of `POST /api/v1/media/{media_type}/` and the history PATCH.
 *
 * All properties are optional: Moshi requires any nullable property that has no
 * default, so one missing field would otherwise fail the whole tracking call.
 */
@JsonClass(generateAdapter = true)
data class TrackedMediaResponse(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "consumption_id") val consumptionId: Int? = null,
    @Json(name = "item") val item: Map<String, Any?>? = null,
    @Json(name = "media_id") val mediaId: Any? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "media_type") val mediaType: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "item_id") val itemId: String? = null,
    @Json(name = "parent_id") val parentId: String? = null,
    @Json(name = "tracked") val tracked: Boolean? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "score") val score: Double? = null,
    @Json(name = "scored_at") val scoredAt: String? = null,
    @Json(name = "status") val status: Int? = null,
    @Json(name = "progress") val progress: Double? = null,
    @Json(name = "episodes_left") val episodesLeft: Int? = null,
    @Json(name = "total_episodes_left") val totalEpisodesLeft: Int? = null,
    @Json(name = "progress_scope") val progressScope: String? = null,
    @Json(name = "progress_unit") val progressUnit: String? = null,
    @Json(name = "progressed_at") val progressedAt: String? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "lists") val lists: List<Any>? = null,
    @Json(name = "next_episode") val nextEpisode: NextEpisode? = null,
)

@JsonClass(generateAdapter = true)
data class TrackedMediaEnvelope(
    @Json(name = "pagination") val pagination: Map<String, Any?>? = null,
    @Json(name = "results") val results: List<Map<String, Any?>> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class NextEpisode(
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    /** Present on Disketarr's `next_episode`, so rows can name the episode. */
    @Json(name = "title") val title: String? = null,
    /** Disketarr's own label, e.g. "S03E03". */
    @Json(name = "episode_code") val episodeCode: String? = null,
)
