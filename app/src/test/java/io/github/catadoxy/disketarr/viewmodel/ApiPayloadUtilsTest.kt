package io.github.catadoxy.disketarr.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Moshi decodes numbers inside a raw `Map<String, Any?>` as [Double], so a
 * status of `3` arrives as `3.0`. These helpers are what keep integer fields
 * from silently becoming null.
 */
class ApiPayloadUtilsTest {

    @Test
    fun asIntOrNull_accepts_the_double_moshi_produces() {
        assertEquals(3, (3.0 as Any?).asIntOrNull())
    }

    @Test
    fun asIntOrNull_accepts_int_and_text_forms() {
        assertEquals(3, (3 as Any?).asIntOrNull())
        assertEquals(3, ("3" as Any?).asIntOrNull())
        assertEquals(3, ("3.0" as Any?).asIntOrNull())
    }

    @Test
    fun asIntOrNull_rejects_missing_and_non_numeric_values() {
        assertNull((null as Any?).asIntOrNull())
        assertNull(("abc" as Any?).asIntOrNull())
        assertNull((true as Any?).asIntOrNull())
    }

    @Test
    fun asDoubleOrNull_handles_numbers_and_text() {
        assertEquals(42.0, (42.0 as Any?).asDoubleOrNull()!!, 0.0001)
        assertEquals(42.0, (42 as Any?).asDoubleOrNull()!!, 0.0001)
        assertEquals(42.5, ("42.5" as Any?).asDoubleOrNull()!!, 0.0001)
        assertNull((null as Any?).asDoubleOrNull())
        assertNull(("nope" as Any?).asDoubleOrNull())
    }

    /**
     * A failed or timed-out request yields null. This used to reach
     * `ArrayDeque.addLast`, which rejects null and crashed the app.
     */
    @Test
    fun extractRowsSafely_tolerates_a_null_payload() {
        assertEquals(emptyList<Map<String, Any?>>(), (null as Any?).extractRowsSafely())
    }

    @Test
    fun extractRowsSafely_tolerates_null_entries_inside_collections() {
        val payload = linkedMapOf<String, Any?>(
            "results" to listOf(null, linkedMapOf("media_id" to "1", "source" to "tmdb")),
            "pagination" to null,
        )

        assertEquals(1, payload.extractRowsSafely().size)
    }
}
