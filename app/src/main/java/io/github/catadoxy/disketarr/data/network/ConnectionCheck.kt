package io.github.catadoxy.disketarr.data.network

import retrofit2.HttpException

/**
 * Verifies that the saved base URL + token actually work.
 *
 * The setup screen used to probe `GET /api/v1/user/preferences/`. That route
 * requires Disketarr's `user:read` scope, but the App tokens Disketarr hands out
 * (Settings -> Integrations -> App tokens) default to the *tracking preset*:
 * scrobble:write, progress:read/write, watchlist:read/write, catalog:read,
 * sync:read. No `user:read`. So every correctly scoped token answered 403 and
 * the app looked like it could not pull any data at all.
 *
 * `GET /api/v1/info/` is mapped to `ANY_SCOPE` in Disketarr, so any valid token -
 * legacy account token or scoped App token - passes. Servers predating that
 * route (or a proxy that does not forward it) fall back to a one-item media
 * list, which only needs `watchlist:read`.
 */
object ConnectionCheck {

    /** Result of a connectivity probe. */
    sealed interface Outcome {
        /** The server answered as an authenticated Disketarr instance. */
        data object Ok : Outcome

        /**
         * The server was reached but rejected or failed the probe.
         *
         * @param code HTTP status, or -1 when the request never completed.
         * @param transportError exception text for a failure that never got an
         *   HTTP response (DNS, TLS, connection refused, timeout...).
         */
        data class Failed(val code: Int, val transportError: String? = null) : Outcome
    }

    suspend fun verify(service: DisketarrApiService): Outcome {
        val info = runCatching { service.getServerInfo() }
        val response = info.getOrNull()

        if (response != null && response.isSuccessful) return Outcome.Ok

        // Fall back for servers without /api/v1/info/. Any other status
        // (notably 401/403) is a real answer and is reported as-is.
        val code = response?.code()
        if (response == null || code == 404 || code == 405) {
            val mediaProbe = runCatching { service.listTrackedMediaRaw(limit = 1) }
            if (mediaProbe.isSuccess) return Outcome.Ok

            val mediaError = mediaProbe.exceptionOrNull()
            if (mediaError is HttpException) return Outcome.Failed(mediaError.code())

            // Neither probe completed: report the transport-level reason.
            if (response == null) {
                return Outcome.Failed(
                    code = -1,
                    transportError = mediaError?.message ?: info.exceptionOrNull()?.message,
                )
            }
        }

        return Outcome.Failed(code ?: -1, info.exceptionOrNull()?.message)
    }
}

/**
 * Turns a probe failure into something a user can act on.
 */
internal fun connectionErrorMessage(outcome: ConnectionCheck.Outcome.Failed): String = when {
    outcome.code == 401 || outcome.code == 403 ->
        "Disketarr rejected the token (HTTP ${outcome.code}). Create an App token under " +
            "Settings -> Integrations -> App tokens and give it at least watchlist:read " +
            "and catalog:read - the \"Tracking\" preset already includes both."

    outcome.code == 404 ->
        "Disketarr answered HTTP 404 for /api/v1/. Check that the base URL points at your " +
            "Disketarr instance (not a reverse-proxy path or the login page)."

    outcome.code == -1 ->
        "Could not reach the server" +
            (outcome.transportError?.let { ": $it" } ?: ".") +
            " Check the URL, the port, and that this device can see the host."

    outcome.code in 300..399 ->
        "Disketarr redirected (HTTP ${outcome.code}). A login page (Authelia/SSO) is probably " +
            "intercepting /api/v1 - see the README."

    else -> "Server responded with HTTP ${outcome.code}."
}
