package io.github.catadoxy.floppy.data.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type

/**
 * Accepts either a JSON string or a JSON number and exposes it as a [String].
 *
 * Floppy stringifies `media_id` in the item serializers (library, detail) but
 * emits a bare integer from the provider search serializers, and its OpenAPI
 * contract documents the field as `string | integer`. Declaring it as a plain
 * `String` made every search fail to decode.
 */
@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class FlexString

object FlexStringAdapterFactory : JsonAdapter.Factory {

    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type != String::class.java) return null
        if (annotations.none { it is FlexString }) return null
        return FlexStringAdapter
    }

    private object FlexStringAdapter : JsonAdapter<String>() {

        override fun fromJson(reader: JsonReader): String = when (reader.peek()) {
            JsonReader.Token.NULL -> {
                reader.skipValue()
                ""
            }

            JsonReader.Token.NUMBER -> numberToId(reader.nextDouble())
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            else -> reader.nextString()
        }

        override fun toJson(writer: JsonWriter, value: String?) {
            writer.value(value)
        }

        /** 603.0 must round-trip as "603", not "603.0". */
        private fun numberToId(value: Double): String =
            if (value.isFinite() && value == Math.floor(value)) {
                value.toLong().toString()
            } else {
                value.toString()
            }
    }
}
