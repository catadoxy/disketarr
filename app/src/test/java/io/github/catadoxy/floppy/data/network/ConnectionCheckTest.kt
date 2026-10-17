package io.github.catadoxy.floppy.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.UnknownHostException

class ConnectionCheckTest {

    @Test
    fun ok_when_info_answers_200() = runBlocking {
        val outcome = ConnectionCheck.verify(
            FakeService(infoResponse = Response.success(emptyMap<String, Any?>())),
        )

        assertEquals(ConnectionCheck.Outcome.Ok, outcome)
    }

    @Test
    fun reports_403_from_info_without_falling_back() = runBlocking {
        val outcome = ConnectionCheck.verify(
            FakeService(infoResponse = Response.error(403, "".toResponseBody(null))),
        )

        assertEquals(ConnectionCheck.Outcome.Failed(403), outcome)
    }

    @Test
    fun falls_back_to_media_list_when_info_is_missing() = runBlocking {
        val outcome = ConnectionCheck.verify(
            FakeService(infoResponse = Response.error(404, "".toResponseBody(null))),
        )

        assertEquals(ConnectionCheck.Outcome.Ok, outcome)
    }

    @Test
    fun reports_media_probe_status_when_info_is_missing() = runBlocking {
        val outcome = ConnectionCheck.verify(
            FakeService(
                infoResponse = Response.error(404, "".toResponseBody(null)),
                mediaThrows = HttpException(Response.error<Map<String, Any?>>(403, "".toResponseBody(null))),
            ),
        )

        assertEquals(ConnectionCheck.Outcome.Failed(403), outcome)
    }

    @Test
    fun reports_transport_failure_when_nothing_answers() = runBlocking {
        val failure = UnknownHostException("no dns")
        val outcome = ConnectionCheck.verify(FakeService(infoThrows = failure, mediaThrows = failure))

        assertEquals(ConnectionCheck.Outcome.Failed(-1, "no dns"), outcome)
    }

    @Test
    fun message_for_403_points_at_token_scopes() {
        val message = connectionErrorMessage(ConnectionCheck.Outcome.Failed(403))

        assertTrue(message, message.contains("watchlist:read"))
        assertTrue(message, message.contains("Integrations"))
    }

    @Test
    fun message_for_transport_failure_includes_the_reason() {
        val message = connectionErrorMessage(ConnectionCheck.Outcome.Failed(-1, "Connection refused"))

        assertTrue(message, message.contains("Connection refused"))
    }

    /**
     * Minimal [FloppyApiService] stand-in. Only the two probe routes are
     * exercised; everything else is unreachable in these tests.
     */
    private class FakeService(
        private val infoResponse: Response<Map<String, Any?>>? = null,
        private val infoThrows: Throwable? = null,
        private val mediaThrows: Throwable? = null,
    ) : FloppyApiService {

        override suspend fun getServerInfo(): Response<Map<String, Any?>> {
            infoThrows?.let { throw it }
            return requireNotNull(infoResponse)
        }

        override suspend fun listTrackedMediaRaw(
            mediaType: String?,
            statuses: List<String>?,
            rating: String?,
            ratingMin: Double?,
            genre: String?,
            year: String?,
            progress: String?,
            collection: String?,
            search: String?,
            sort: String?,
            direction: String?,
            limit: Int?,
            offset: Int?,
        ): Map<String, Any?> {
            mediaThrows?.let { throw it }
            return emptyMap()
        }

        override suspend fun discover(mediaType: String?) = unsupported()
        override suspend fun upcomingCalendar(month: Int?, year: Int?, limit: Int?, offset: Int?) = unsupported()

        override suspend fun getUserPreferences() = unsupported()
        override suspend fun getStatisticsOverview() = unsupported()
        override suspend fun searchMedia(
            mediaType: String,
            query: String,
            limit: Int?,
            offset: Int?,
            source: String?,
        ) = unsupported()

        override suspend fun listTrackedMedia(mediaType: String?, statuses: List<String>?, limit: Int?, offset: Int?) = unsupported()
        override suspend fun listTrackedMediaByType(mediaType: String, statuses: List<String>?, limit: Int?, offset: Int?) = unsupported()
        override suspend fun listTrackedMediaByTypeRaw(mediaType: String, statuses: List<String>?, limit: Int?, offset: Int?) = unsupported()
        override suspend fun listHistory(limit: Int?, offset: Int?) = unsupported()
        override suspend fun listHistoryRaw(limit: Int?, offset: Int?, flat: String?) = unsupported()
        override suspend fun getLists() = unsupported()
        override suspend fun getMediaDetail(mediaType: String, source: String, mediaId: String) = unsupported()
        override suspend fun listSeasonsRaw(mediaType: String, source: String, mediaId: String, limit: Int?) = unsupported()
        override suspend fun listSeasonEpisodesRaw(mediaType: String, source: String, mediaId: String, seasonNumber: Int, limit: Int?) = unsupported()
        override suspend fun markEpisodeWatched(mediaType: String, source: String, mediaId: String, seasonNumber: Int, episodeNumber: Int) = unsupported()
        override suspend fun unmarkEpisodeWatched(mediaType: String, source: String, mediaId: String, seasonNumber: Int, episodeNumber: Int) = unsupported()
        override suspend fun trackMedia(mediaType: String, request: io.github.catadoxy.floppy.data.model.TrackMediaRequest) = unsupported()
        override suspend fun updateMediaConsumption(
            mediaType: String,
            source: String,
            mediaId: String,
            consumptionId: String,
            request: io.github.catadoxy.floppy.data.model.MediaUpdateRequest,
        ) = unsupported()

        override suspend fun addMediaToList(mediaType: String, source: String, mediaId: String, listId: String, request: Map<String, Any>) = unsupported()
        override suspend fun removeMediaFromList(mediaType: String, source: String, mediaId: String, listId: String) = unsupported()
        override suspend fun untrackMedia(mediaType: String, source: String, mediaId: String) = unsupported()

        private fun unsupported(): Nothing = throw NotImplementedError("not used in these tests")
    }
}
