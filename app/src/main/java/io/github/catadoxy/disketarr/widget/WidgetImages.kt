package io.github.catadoxy.disketarr.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Small synchronous poster loader for the widget.
 *
 * `RemoteViews` cannot load a remote URL, so the factory downloads and downscales
 * a bitmap before handing it to `RemoteViews.setImageViewBitmap`. Bitmaps are kept
 * small (RGB_565, 138x192) so the whole row set stays well under the Binder
 * transaction limit, and cached in memory so a rebind does not re-download them.
 */
internal object WidgetImages {

    private const val TARGET_WIDTH = 138
    private const val TARGET_HEIGHT = 192

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(url: String?): Bitmap? {
        val key = url?.takeIf { it.isNotBlank() } ?: return null
        cache.get(key)?.let { return it }

        val bitmap = runCatching {
            val request = Request.Builder().url(key).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    response.body?.bytes()?.let { decodeDownscaled(it) }
                }
            }
        }.getOrNull() ?: return null

        cache.put(key, bitmap)
        return bitmap
    }

    private fun decodeDownscaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_WIDTH &&
            bounds.outHeight / (sample * 2) >= TARGET_HEIGHT
        ) {
            sample *= 2
        }

        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        ) ?: return null

        val scaled = Bitmap.createScaledBitmap(decoded, TARGET_WIDTH, TARGET_HEIGHT, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
