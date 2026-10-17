package io.github.catadoxy.disketarr.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three payload shapes behind the Shows/Movies tabs. Field names were taken
 * from live responses, so these guard against a silently empty tab if Disketarr
 * renames something.
 */
class BrowseParsingTest {

    @Test
    fun discover_rows_map_to_rails_with_items() {
        val rails = discoverPayload().asDiscoverRails()

        assertEquals(2, rails.size)
        assertEquals("TV Shows: Trending Right Now", rails[0].title)
        assertEquals("What everyone has been watching this week.", rails[0].summary)
        assertEquals("22980", rails[0].items[0].mediaId)
        assertEquals("Watch What Happens Live with Andy Cohen", rails[0].items[0].title)
        assertEquals("tv", rails[0].items[0].mediaType)
    }

    @Test
    fun discover_rows_without_items_are_dropped() {
        val payload = linkedMapOf<String, Any?>(
            "rows" to listOf(
                linkedMapOf("title" to "Empty rail", "items" to emptyList<Any?>()),
                linkedMapOf("title" to "No items key"),
            ),
        )

        assertTrue(payload.asDiscoverRails().isEmpty())
    }

    @Test
    fun upcoming_rows_carry_the_air_moment_and_episode() {
        val items = upcomingPayload().asUpcomingItems()

        assertEquals(1, items.size)
        assertEquals("253811", items[0].mediaId)
        assertEquals("Tougen Anki", items[0].title)
        // `datetime` is the air moment; it drives the date line.
        assertEquals("2026-10-18T11:30:00+03:00", items[0].lastWatchedAt)
        // season comes from the item URL, episode from content_number.
        assertEquals(2, items[0].nextEpisode?.seasonNumber)
        assertEquals(5, items[0].nextEpisode?.episodeNumber)
    }

    @Test
    fun history_rows_keep_episodes_for_a_shows_history() {
        val rows = historyPayload().asHistoryItems(setOf("tv", "season", "episode"))

        assertEquals(1, rows.size)
        assertEquals("2316", rows[0].mediaId)
        assertEquals("The Office", rows[0].title)
        assertEquals(4, rows[0].nextEpisode?.seasonNumber)
        assertEquals(12, rows[0].nextEpisode?.episodeNumber)
        assertEquals("S04E12", rows[0].nextEpisode?.episodeCode)
        assertEquals("Did I Stutter?", rows[0].nextEpisode?.title)
    }

    @Test
    fun history_rows_for_movies_exclude_episodes() {
        // The same episode entry must not leak into the Movies history.
        assertTrue(historyPayload().asHistoryItems(setOf("movie")).isEmpty())
    }

    @Test
    fun parsers_tolerate_null_and_junk() {
        assertTrue((null as Any?).asDiscoverRails().isEmpty())
        assertTrue((null as Any?).asUpcomingItems().isEmpty())
        assertTrue((null as Any?).asHistoryItems(setOf("tv")).isEmpty())
        assertTrue(("junk" as Any?).asDiscoverRails().isEmpty())
        assertNull((null as Any?).asUpcomingItems().firstOrNull())
    }

    private fun discoverPayload() = linkedMapOf<String, Any?>(
        "media_type" to "all",
        "rows" to listOf(
            linkedMapOf(
                "key" to "trending_right_now",
                "title" to "TV Shows: Trending Right Now",
                "why" to "What everyone has been watching this week.",
                "items" to listOf(
                    linkedMapOf(
                        "media_type" to "tv",
                        "source" to "tmdb",
                        "media_id" to "22980",
                        "title" to "Watch What Happens Live with Andy Cohen",
                        "image" to "https://img/wwhl.jpg",
                    ),
                    // a malformed item with no id should be skipped
                    linkedMapOf("media_type" to "tv", "title" to "Broken"),
                ),
            ),
            linkedMapOf(
                "title" to "Movies: Trending Right Now",
                "items" to listOf(
                    linkedMapOf(
                        "media_type" to "movie",
                        "source" to "tmdb",
                        "media_id" to "550",
                        "title" to "Fight Club",
                    ),
                ),
            ),
        ),
    )

    private fun upcomingPayload() = linkedMapOf<String, Any?>(
        "results" to listOf(
            linkedMapOf(
                "id" to 12557,
                "content_number" to 5,
                "datetime" to "2026-10-18T11:30:00+03:00",
                "item" to linkedMapOf(
                    "media_id" to "253811",
                    "source" to "tmdb",
                    "media_type" to "episode",
                    "title" to "Tougen Anki",
                    "image" to "https://img/tougen.jpg",
                    "url" to "/details/tmdb/tv/253811/tougen-anki/season/2",
                ),
            ),
        ),
    )

    private fun historyPayload() = linkedMapOf<String, Any?>(
        "results" to listOf(
            linkedMapOf(
                "media_type" to "episode",
                "title" to "Did I Stutter?",
                "show" to linkedMapOf("title" to "The Office", "image" to "https://img/office.jpg"),
                "episode_code" to "S04E12",
                "season_number" to 4,
                "episode_number" to 12,
                "played_at_local" to "2026-10-16T15:14:34.650580+03:00",
                "status" to "Completed",
                "item" to linkedMapOf(
                    "media_id" to "2316",
                    "source" to "tmdb",
                    "media_type" to "episode",
                    "title" to "Did I Stutter?",
                    "image" to "https://img/office.jpg",
                ),
            ),
        ),
    )
}
