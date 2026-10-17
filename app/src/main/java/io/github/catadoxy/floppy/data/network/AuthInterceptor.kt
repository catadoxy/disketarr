package io.github.catadoxy.floppy.data.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Floppy API token to every outgoing request.
 *
 * Floppy accepts either header - using Authorization: Bearer here since
 * it's the more conventional of the two. Swap to X-API-Key if you'd
 * rather match the curl examples in Floppy's own docs.
 */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val original = chain.request()

        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("X-API-Key", token)
                .build()
        }

        return chain.proceed(request)
    }
}
