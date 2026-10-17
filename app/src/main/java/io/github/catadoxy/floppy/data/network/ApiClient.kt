package io.github.catadoxy.floppy.data.network

import io.github.catadoxy.floppy.BuildConfig
import io.github.catadoxy.floppy.data.SettingsStore
import io.github.catadoxy.floppy.data.model.FlexStringAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Lazily (re)builds the Retrofit service whenever settings change,
 * since baseUrl isn't known until the user completes setup.
 */
object ApiClient {

    private var cachedBaseUrl: String? = null
    private var cachedService: FloppyApiService? = null

    @Synchronized
    fun reset() {
        cachedBaseUrl = null
        cachedService = null
    }

    @Synchronized
    fun getService(settingsStore: SettingsStore): FloppyApiService {
        val configured = settingsStore.baseUrl?.takeIf { it.isNotBlank() }
            ?: error("Floppy base URL not configured yet")

        // Accepts plain http:// hosts as well as https://, and tolerates a
        // missing scheme. Retrofit needs the trailing slash so relative paths
        // resolve against the deployment root (or sub-path).
        val normalizedBaseUrl = "${BaseUrlNormalizer.normalize(configured)}/"

        if (cachedService != null && cachedBaseUrl == normalizedBaseUrl) {
            return cachedService!!
        }

        val logging = HttpLoggingInterceptor().apply {
            // Only emit request/response summaries in debug builds; release builds
            // must not leak URLs or token-bearing headers to logcat.
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { settingsStore.apiToken })
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(FlexStringAdapterFactory)
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val service = retrofit.create(FloppyApiService::class.java)
        cachedBaseUrl = normalizedBaseUrl
        cachedService = service
        return service
    }
}
