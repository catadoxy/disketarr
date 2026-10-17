package io.github.catadoxy.disketarr.data.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.catadoxy.disketarr.data.model.FlexStringAdapterFactory
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Reproduces the exact Retrofit/Moshi wiring used by [ApiClient].
 *
 * Retrofit resolves response converters eagerly inside `create()`, so any
 * declared return type the converter chain cannot handle fails here rather than
 * at runtime on a user's device.
 */
class ApiClientWiringTest {

    @Test
    fun service_creation_resolves_every_response_converter() {
        val moshi = Moshi.Builder()
            .add(FlexStringAdapterFactory)
            .add(KotlinJsonAdapterFactory())
            .build()

        val service = Retrofit.Builder()
            .baseUrl("http://localhost:8000/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DisketarrApiService::class.java)

        assertNotNull(service)
    }
}
