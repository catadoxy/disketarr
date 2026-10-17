package io.github.catadoxy.disketarr.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TrackMediaRequest(
    @Json(name = "source") val source: String,
    @Json(name = "media_id") val mediaId: String,
    @Json(name = "title") val title: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    @Json(name = "parent_tv") val parentTv: Int? = null,
    @Json(name = "parent_season") val parentSeason: Int? = null,
    @Json(name = "library_media_type") val libraryMediaType: String? = null,
    @Json(name = "score") val score: Double? = null,
    @Json(name = "status") val status: Any? = null, // Can be Int or String
    @Json(name = "progress") val progress: Double? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "notes") val notes: String? = null,
)

@JsonClass(generateAdapter = true)
data class MediaUpdateRequest(
    @Json(name = "score") val score: Double? = null,
    @Json(name = "status") val status: Any? = null, // Can be Int or String
    @Json(name = "progress") val progress: Double? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "notes") val notes: String? = null,
)
