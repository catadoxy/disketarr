package io.github.catadoxy.disketarr.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes verbatim responses captured from a live Disketarr instance (v26.10.8).
 *
 * These payloads are why the app stopped showing anything:
 *
 *  - the search serializers emit `media_id` as a bare integer (`603`) while the
 *    item serializers emit a string, and the app declared `String`;
 *  - cast/crew use `person_id`/`role`, not `id`/`character`/`job`;
 *  - a movie's `details` has no `air_date`/`episode_number`/`episode_type`;
 *  - `related` is a map of collection name to items, not a flat media object.
 *
 * Moshi (via `KotlinJsonAdapterFactory`) treats a nullable property with no
 * default as REQUIRED, so each of those mismatches failed the whole response.
 *
 * The `budget`/`revenue` fields were trimmed from the fixture only because they
 * contain `$`, which is not literal-safe inside a Kotlin raw string.
 */
class DisketarrPayloadParsingTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(FlexStringAdapterFactory)
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun decodes_search_response_whose_media_id_is_an_integer() {
        val response = moshi.adapter(SearchResponse::class.java).fromJson(SEARCH_JSON)

        assertNotNull(response)
        assertEquals(2, response!!.results.size)
        assertEquals("603", response.results[0].mediaId)
        assertEquals("The Matrix", response.results[0].title)
        assertEquals("tmdb", response.results[0].source)
        assertEquals(92, response.pagination.total)
        assertEquals("604", response.results[1].mediaId)
    }

    @Test
    fun decodes_media_detail_with_the_real_nested_shapes() {
        val detail = moshi.adapter(MediaDetail::class.java).fromJson(DETAIL_JSON)

        assertNotNull(detail)
        assertEquals("269149", detail!!.mediaId)
        assertEquals("movie", detail.mediaType)
        assertEquals(1, detail.maxProgress)
        assertEquals(7.8, detail.score!!, 0.001)
        assertEquals(3, detail.consumptions?.firstOrNull()?.status)

        // cast entries carry person_id/role, never id/character/job
        assertEquals("Ginnifer Goodwin", detail.cast?.firstOrNull()?.name)
        assertEquals("Judy Hopps (voice)", detail.cast?.firstOrNull()?.character)
        assertEquals("417", detail.cast?.firstOrNull()?.personId)

        // crew entries carry role, not job
        assertEquals("Director", detail.crew?.firstOrNull()?.job)

        // a movie's details has format/release_date, and no episode keys at all
        assertEquals("1h 49m", detail.details?.runtime)
        assertEquals("2016-02-11T00:00:00+02:00", detail.details?.displayDate)
        assertNull(detail.details?.seasonNumber)

        // related is a map of collection name to items
        assertTrue(detail.related?.containsKey("Zootopia Collection") == true)
    }

    @Test
    fun flex_string_adapter_accepts_numbers_strings_and_null() {
        val adapter = moshi.adapter(FlexHolder::class.java)

        assertEquals("603", adapter.fromJson("""{"media_id":603}""")!!.mediaId)
        assertEquals("603", adapter.fromJson("""{"media_id":"603"}""")!!.mediaId)
        assertEquals("tt0133093", adapter.fromJson("""{"media_id":"tt0133093"}""")!!.mediaId)
        assertEquals("", adapter.fromJson("""{"media_id":null}""")!!.mediaId)
    }

    /** Mirrors how the real models consume the qualifier. */
    private data class FlexHolder(
        @FlexString @Json(name = "media_id") val mediaId: String = "",
    )

    @Test
    fun search_result_tolerates_absent_optional_fields() {
        // A minimal payload must still decode rather than throw.
        val minimal = """{"pagination":{"total":0,"limit":1,"offset":0,"next":null,"previous":null},"results":[{"media_id":7}]}"""

        val response = moshi.adapter(SearchResponse::class.java).fromJson(minimal)

        assertEquals("7", response!!.results[0].mediaId)
        assertNull(response.results[0].title)
    }

    private companion object {
        const val SEARCH_JSON = """{"pagination":{"total":92,"limit":2,"offset":0,"next":"http://Disketarr.local:8220/api/v1/search/movie/?search=matrix&limit=2&offset=2&source=tmdb","previous":null},"results":[{"media_id":603,"source":"tmdb","media_type":"movie","title":"The Matrix","original_title":"The Matrix","localized_title":"The Matrix","image":"https://image.tmdb.org/t/p/w500/dXNAPwY7VrqMAo51EKhhCJfaGb5.jpg","year":1999},{"media_id":604,"source":"tmdb","media_type":"movie","title":"The Matrix Reloaded","original_title":"The Matrix Reloaded","localized_title":"The Matrix Reloaded","image":"https://image.tmdb.org/t/p/w500/8xEVAe84zlL9rkfYT6dZXero4KK.jpg","year":2003}]}"""

        const val DETAIL_JSON = """{"id":9738,"media_id":"269149","source":"tmdb","source_url":"https://www.themoviedb.org/movie/269149","media_type":"movie","title":"Zootopia","max_progress":1,"episodes_left":null,"total_episodes_left":null,"image":"https://image.tmdb.org/t/p/w500/hlK0e0wAQ3VLuJcsfIYPvb4JVud.jpg","backdrop":"https://image.tmdb.org/t/p/w1280/9tOkjBEiiGcaClgJFtwocStZvIT.jpg","synopsis":"Determined to prove herself.","genres":["Animation","Adventure","Family","Comedy"],"score":7.8,"score_count":18621,"imdb_rating":8,"imdb_rating_count":651583,"cast":[{"person_id":"417","name":"Ginnifer Goodwin","image":"https://image.tmdb.org/t/p/w185/eByMpalFhQxUYuzA0srCN8rnfOZ.jpg","known_for_department":"Acting","gender":"female","department":"Acting","role":"Judy Hopps (voice)","order":0,"episode_count":null},{"person_id":"23532","name":"Jason Bateman","image":"https://image.tmdb.org/t/p/w185/8e6mt0vGjPo6eW52gqRuXy5YnfN.jpg","known_for_department":"Acting","gender":"male","department":"Acting","role":"Nick Wilde (voice)","order":1,"episode_count":null}],"crew":[{"person_id":"76595","name":"Byron Howard","image":"https://image.tmdb.org/t/p/w185/ePJXkxrD44nM0VB7Xx9Q4ityzfT.jpg","known_for_department":"Directing","gender":"male","department":"Directing","role":"Director","order":null}],"details":{"format":"Movie","release_date":"2016-02-11T00:00:00+02:00","status":"Released","runtime":"1h 49m","studios":["Walt Disney Animation Studios"],"country":"United States of America","languages":["English"],"certification":"PG"},"related":{"Zootopia Collection":[{"id":1,"media_id":"269149","source":"tmdb","media_type":"movie","title":"Zootopia"}]},"item_id":"movie/tmdb/269149","parent_id":null,"tracked":true,"consumptions_number":1,"consumptions":[{"consumption_id":82,"created":"2026-09-13T16:18:29.319548Z","score":null,"scored_at":null,"progress":1,"progressed_at":"2026-09-13T16:09:24.228323Z","status":3,"start_date":null,"end_date":"2023-12-05T00:00:00Z","notes":"","source":""}],"lists":[],"media_type_status":{"media_type":"movie","enabled":true,"message":null}}"""
    }
}
