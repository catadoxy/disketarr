package io.github.catadoxy.disketarr.data.network

import io.github.catadoxy.disketarr.data.model.MediaDetail
import io.github.catadoxy.disketarr.data.model.MediaUpdateRequest
import io.github.catadoxy.disketarr.data.model.SearchResponse
import io.github.catadoxy.disketarr.data.model.TrackMediaRequest
import io.github.catadoxy.disketarr.data.model.TrackedMediaEnvelope
import io.github.catadoxy.disketarr.data.model.TrackedMediaResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Updated to match Disketarr API schema from openapi.yaml.
 * Verified endpoints:
 * - GET /api/v1/search/{media_type}/
 * - GET /api/v1/media/{media_type}/{source}/{media_id}/
 * - POST /api/v1/media/{media_type}/
 * - PATCH /api/v1/media/{media_type}/{source}/{media_id}/history/{consumption_id}/
 * - PUT /api/v1/media/{media_type}/{source}/{media_id}/lists/{list_id}/
 */
interface DisketarrApiService {

    /**
     * Lightweight authenticated probe. Disketarr maps this to ANY_SCOPE, so it
     * answers for any valid credential - including scoped App tokens that do
     * not carry `user:read`. See [ConnectionCheck].
     */
    @GET("api/v1/info/")
    suspend fun getServerInfo(): retrofit2.Response<Map<String, Any?>>

    @GET("api/v1/lists/")
    suspend fun getLists(): Map<String, Any?>

    @GET("api/v1/user/preferences/")
    suspend fun getUserPreferences(): retrofit2.Response<Unit>

    /**
     * Summary figures for the Statistics tab. The full payload is large (every
     * chart section Disketarr renders), so it is fetched once and cached.
     * Requires `statistics:read`.
     */
    @GET("api/v1/statistics/overview/")
    suspend fun getStatisticsOverview(): Map<String, Any?>

    @GET("api/v1/search/{media_type}/")
    suspend fun searchMedia(
        @Path("media_type") mediaType: String,
        @Query("search") query: String,
        @Query("limit") limit: Int? = 20,
        @Query("offset") offset: Int? = 0,
        @Query("source") source: String? = null,
    ): SearchResponse

    @GET("api/v1/media/")
    suspend fun listTrackedMedia(
        @Query("media_type") mediaType: String? = null,
        @Query("status") statuses: List<String>? = null,
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0,
    ): TrackedMediaEnvelope

    /**
     * Curated rows ("TV Shows: Trending Right Now"). `media_type` narrows it to
     * `tv` or `movie`. Needs `catalog:read`.
     */
    @GET("api/v1/discover/")
    suspend fun discover(
        @Query("media_type") mediaType: String? = null,
    ): Map<String, Any?>

    /**
     * Upcoming releases ("Upcoming" tab). Needs `watchlist:read`. The response is
     * a single calendar month, which includes days that have already aired.
     */
    @GET("api/v1/calendar/")
    suspend fun upcomingCalendar(
        @Query("month") month: Int? = null,
        @Query("year") year: Int? = null,
        @Query("limit") limit: Int? = 60,
        @Query("offset") offset: Int? = 0,
    ): Map<String, Any?>

    @GET("api/v1/media/")
    suspend fun listTrackedMediaRaw(
        @Query("media_type") mediaType: String? = null,
        @Query("status") statuses: List<String>? = null,
        @Query("rating") rating: String? = null,
        @Query("rating_min") ratingMin: Double? = null,
        @Query("genre") genre: String? = null,
        @Query("year") year: String? = null,
        @Query("progress") progress: String? = null,
        @Query("collection") collection: String? = null,
        @Query("search") search: String? = null,
        @Query("sort") sort: String? = null,
        @Query("direction") direction: String? = null,
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0,
    ): Map<String, Any?>

    @GET("api/v1/media/{media_type}/")
    suspend fun listTrackedMediaByType(
        @Path("media_type") mediaType: String,
        @Query("status") statuses: List<String>? = null,
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0,
    ): TrackedMediaEnvelope

    @GET("api/v1/media/{media_type}/")
    suspend fun listTrackedMediaByTypeRaw(
        @Path("media_type") mediaType: String,
        @Query("status") statuses: List<String>? = null,
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0,
    ): Map<String, Any?>

    @GET("api/v1/history/")
    suspend fun listHistory(
        @Query("limit") limit: Int? = 200,
        @Query("offset") offset: Int? = 0,
    ): Map<String, Any?>

    @GET("api/v1/history/")
    suspend fun listHistoryRaw(
        @Query("limit") limit: Int? = 200,
        @Query("offset") offset: Int? = 0,
        /** `1` returns a flat per-entry list instead of day-grouped buckets. */
        @Query("flat") flat: String? = null,
    ): Map<String, Any?>

    @DELETE("api/v1/media/{media_type}/{source}/{media_id}/")
    suspend fun untrackMedia(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
    ): retrofit2.Response<Unit>

    @GET("api/v1/media/{media_type}/{source}/{media_id}/")
    suspend fun getMediaDetail(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
    ): MediaDetail

    /**
     * Seasons of a show. Unlike the show detail payload - which only carries an
     * aggregate season count - this lists the real season numbers. Scoped via
     * `watchlist:read`.
     */
    @GET("api/v1/media/{media_type}/{source}/{media_id}/seasons/")
    suspend fun listSeasonsRaw(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Query("limit") limit: Int? = 100,
    ): Map<String, Any?>

    /**
     * Episodes of one season, each with its own watched state. This is the only
     * place the per-episode status is exposed, so the show screen relies on it
     * rather than guessing from `max_progress`.
     */
    @GET("api/v1/media/{media_type}/{source}/{media_id}/{season_number}/episodes/")
    suspend fun listSeasonEpisodesRaw(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("season_number") seasonNumber: Int,
        @Query("limit") limit: Int? = 100,
        @Query("offset") offset: Int? = 0,
    ): Map<String, Any?>

    /** Marks one episode watched. Needs `watchlist:write`. */
    @POST("api/v1/media/{media_type}/{source}/{media_id}/{season_number}/episodes/{episode_number}/watch/")
    suspend fun markEpisodeWatched(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int,
    ): retrofit2.Response<Unit>

    /** Clears one episode's watched state. Needs `watchlist:write`. */
    @DELETE("api/v1/media/{media_type}/{source}/{media_id}/{season_number}/episodes/{episode_number}/watch/")
    suspend fun unmarkEpisodeWatched(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("season_number") seasonNumber: Int,
        @Path("episode_number") episodeNumber: Int,
    ): retrofit2.Response<Unit>

    @POST("api/v1/media/{media_type}/")
    suspend fun trackMedia(
        @Path("media_type") mediaType: String,
        @Body request: TrackMediaRequest,
    ): TrackedMediaResponse

    @PATCH("api/v1/media/{media_type}/{source}/{media_id}/history/{consumption_id}/")
    suspend fun updateMediaConsumption(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("consumption_id") consumptionId: String,
        @Body request: MediaUpdateRequest,
    ): TrackedMediaResponse

    @PUT("api/v1/media/{media_type}/{source}/{media_id}/lists/{list_id}/")
    suspend fun addMediaToList(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("list_id") listId: String,
        @Body request: Map<String, Any> = emptyMap(),
    ): List<Any>

    @DELETE("api/v1/media/{media_type}/{source}/{media_id}/lists/{list_id}/")
    suspend fun removeMediaFromList(
        @Path("media_type") mediaType: String,
        @Path("source") source: String,
        @Path("media_id") mediaId: String,
        @Path("list_id") listId: String,
    )
}
