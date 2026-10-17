package io.github.catadoxy.disketarr.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsViewModelTest {

    @Test
    fun buildTrackRequest_keepsSourceAndMediaIdAndMapsMediaType() {
        val request = buildTrackRequest(
            mediaType = "movie",
            source = "tmdb",
            mediaId = "123",
            title = "Sample Movie",
            image = "https://example.com/poster.jpg",
            score = 8.5,
            status = 2,
            progress = 100.0,
        )

        assertEquals("tmdb", request.source)
        assertEquals("123", request.mediaId)
        assertEquals("movie", request.libraryMediaType)
        assertEquals(8.5, request.score ?: 0.0, 0.0)
        assertEquals(2, (request.status as? Number)?.toInt())
    }
}
