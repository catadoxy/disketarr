package io.github.catadoxy.floppy.data.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Turns whatever the user typed on the setup screen into a base URL that
 * Retrofit accepts.
 *
 * Floppy is self-hosted, so the value can arrive in several shapes:
 *   - "https://floppy.example.com"   public domain behind TLS
 *   - "http://192.168.1.10:8000"     plain HTTP on the LAN
 *   - "http://100.101.102.103:8000"  Tailscale / CGNAT address
 *   - "floppy.local:8000"            no scheme at all
 *
 * Rules:
 *   - An explicit http:// or https:// scheme is kept as entered.
 *   - With no scheme, LAN/loopback/CGNAT/.local style hosts default to http://
 *     (that's how they're almost always served) and everything else to
 *     https://.
 *   - Anything that still doesn't parse fails with an [IllegalArgumentException]
 *     whose message can be shown to the user as-is.
 *
 * The returned URL never has a trailing slash; see [ApiClient], which adds it
 * back because Retrofit requires it.
 */
object BaseUrlNormalizer {

    fun normalize(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        require(trimmed.isNotEmpty()) {
            "Enter your Floppy server URL, for example " +
                "http://192.168.1.10:8000 or https://floppy.example.com"
        }
        require(!trimmed.contains("://") || hasHttpScheme(trimmed)) {
            "Only http:// and https:// URLs are supported."
        }

        val candidate = if (hasHttpScheme(trimmed)) trimmed else "${defaultScheme(trimmed)}://$trimmed"

        val parsed = candidate.toHttpUrlOrNull()
            ?: throw IllegalArgumentException(
                "\"$trimmed\" doesn't look like a valid server URL. Try something like " +
                    "http://192.168.1.10:8000 or https://floppy.example.com",
            )

        // HttpUrl always renders a path, so this drops the slash it adds.
        return parsed.toString().trimEnd('/')
    }

    private fun hasHttpScheme(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    private fun defaultScheme(value: String): String =
        if (isLocalHost(extractHost(value))) "http" else "https"

    /** Pulls the host out of a scheme-less "host:port/path" style value. */
    private fun extractHost(value: String): String {
        val authority = value
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')

        // Bracketed IPv6 literal, optionally with a port: [::1]:8000
        if (authority.startsWith("[")) {
            return authority.substringAfter('[').substringBefore(']')
        }

        // A bare IPv6 literal has several colons and no port separator.
        return if (authority.count { it == ':' } <= 1) {
            authority.substringBefore(':')
        } else {
            authority
        }
    }

    private fun isLocalHost(host: String): Boolean {
        val normalized = host.lowercase().removeSurrounding("[", "]")
        if (normalized.isEmpty()) return false

        if (normalized == "localhost" || normalized == "::1" || normalized == "0.0.0.0") return true
        if (LOCAL_SUFFIXES.any { normalized.endsWith(it) }) return true

        // A single-label host ("floppy", "nas") can only resolve on the local
        // network, never on the public internet.
        if (!normalized.contains('.')) return true

        val octets = normalized.split('.')
        if (octets.size == 4 && octets.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }) {
            val first = octets[0].toInt()
            val second = octets[1].toInt()
            return first == 10 ||
                first == 127 ||
                (first == 192 && second == 168) ||
                (first == 172 && second in 16..31) ||
                (first == 169 && second == 254) ||
                (first == 100 && second in 64..127) // Tailscale / CGNAT
        }

        return false
    }

    private val LOCAL_SUFFIXES = listOf(
        ".local",
        ".lan",
        ".internal",
        ".home.arpa",
        ".localhost",
        ".home",
    )
}
