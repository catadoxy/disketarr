package io.github.catadoxy.floppy.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * `GET /api/v1/media/{media_type}/{source}/{media_id}/`.
 *
 * Every property carries a default on purpose. Moshi treats a nullable property
 * **without** a default as required, so a single field the server renames or
 * omits makes the whole response undecodable and the details screen shows
 * nothing at all. Floppy's payloads also differ per media type (a movie has no
 * `season_number`, an episode has no `budget`), so absent means null here.
 */
@JsonClass(generateAdapter = true)
data class MediaDetail(
    @Json(name = "id") val id: Int? = null,
    @FlexString @Json(name = "media_id") val mediaId: String? = null,
    @Json(name = "source") val source: String? = null,
    @Json(name = "source_url") val sourceUrl: String? = null,
    @Json(name = "media_type") val mediaType: String = "",
    @Json(name = "title") val title: String? = null,
    @Json(name = "max_progress") val maxProgress: Int? = null,
    @Json(name = "episodes_left") val episodesLeft: Int? = null,
    @Json(name = "total_episodes_left") val totalEpisodesLeft: Int? = null,
    @Json(name = "image") val image: String? = null,
    /** Wide artwork, used as the series page background. */
    @Json(name = "backdrop") val backdrop: String? = null,
    @Json(name = "synopsis") val synopsis: String? = null,
    @Json(name = "genres") val genres: List<String>? = null,
    @Json(name = "score") val score: Double? = null,
    @Json(name = "score_count") val scoreCount: Int? = null,
    @Json(name = "imdb_rating") val imdbRating: Double? = null,
    @Json(name = "imdb_rating_count") val imdbRatingCount: Int? = null,
    @Json(name = "cast") val cast: List<CastMember>? = null,
    @Json(name = "crew") val crew: List<CrewMember>? = null,
    @Json(name = "details") val details: Details? = null,
    /**
     * Floppy returns a map of related-group name to items, e.g.
     * `{"Zootopia Collection": [...]}` or `{"seasons": [...]}` - not the flat
     * media object this used to be modelled as.
     */
    @Json(name = "related") val related: Map<String, Any?>? = null,
    @Json(name = "item_id") val itemId: String? = null,
    @Json(name = "parent_id") val parentId: String? = null,
    @Json(name = "tracked") val tracked: Boolean? = null,
    @Json(name = "consumptions_number") val consumptionsNumber: Int? = null,
    @Json(name = "consumptions") val consumptions: List<Consumption>? = null,
    @Json(name = "lists") val lists: List<ListItem>? = null,
)

/**
 * Cast entries as Floppy actually sends them: `person_id`, `role` and
 * `image` - there is no `id`, `character` or `job` field.
 */
@JsonClass(generateAdapter = true)
data class CastMember(
    @Json(name = "person_id") val personId: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "role") val character: String? = null,
    @Json(name = "department") val department: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "order") val order: Int? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null,
)

/** Crew entries: `person_id` and `role`, with no `job` field. */
@JsonClass(generateAdapter = true)
data class CrewMember(
    @Json(name = "person_id") val personId: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "department") val department: String? = null,
    @Json(name = "role") val job: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "order") val order: Int? = null,
)

/**
 * The `details` blob. It is free-form and type-specific: a movie sends
 * `format`/`release_date`/`budget`/`revenue`, a show sends
 * `first_air_date`/`seasons`/`episodes`, an episode sends
 * `season_number`/`episode_number`/`air_date`.
 */
@JsonClass(generateAdapter = true)
data class Details(
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "runtime") val runtime: Any? = null,
    @Json(name = "episode_type") val episodeType: String? = null,
    @Json(name = "crew") val crew: List<CrewMember>? = null,
    @Json(name = "guest_stars") val guestStars: List<CastMember>? = null,
    /** Airing status, e.g. "Returning Series". */
    @Json(name = "status") val status: String? = null,
    /** Studios/broadcasters; the closest thing Floppy has to a network. */
    @Json(name = "studios") val studios: List<String>? = null,
    /** Number of seasons, as reported for shows. */
    @Json(name = "seasons") val seasons: Int? = null,
    /** Total episodes. Often null; [maxProgress] is not a substitute for it. */
    @Json(name = "episodes") val episodes: Int? = null,
) {
    /** Whichever date this media type actually carries. */
    val displayDate: String?
        get() = airDate ?: firstAirDate ?: releaseDate
}

@JsonClass(generateAdapter = true)
data class Consumption(
    @Json(name = "consumption_id") val consumptionId: Int = 0,
    @Json(name = "created") val created: String? = null,
    @Json(name = "score") val score: Double? = null,
    @Json(name = "scored_at") val scoredAt: String? = null,
    @Json(name = "progress") val progress: Double? = null,
    @Json(name = "progressed_at") val progressedAt: String? = null,
    @Json(name = "status") val status: Int? = null,
    @Json(name = "start_date") val startDate: String? = null,
    @Json(name = "end_date") val endDate: String? = null,
    @Json(name = "notes") val notes: String? = null,
)

@JsonClass(generateAdapter = true)
data class ListItem(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "owner") val owner: Owner? = null,
    @Json(name = "collaborators") val collaborators: List<Collaborator>? = null,
    @Json(name = "items_count") val itemsCount: Int? = null,
    @Json(name = "latest_update") val latestUpdate: String? = null,
    @Json(name = "is_public") val isPublic: Boolean? = null,
    @Json(name = "public_slug") val publicSlug: String? = null,
    @Json(name = "allow_recommendations") val allowRecommendations: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class Owner(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
)

@JsonClass(generateAdapter = true)
data class Collaborator(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "username") val username: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
)
