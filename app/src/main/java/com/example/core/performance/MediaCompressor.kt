package com.example.core.performance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class PreparedMediaUpload(
    val bytes: ByteArray,
    val mimeType: String
)

/**
 * Downscales and compresses user-selected photos on Dispatchers.IO so uploads to
 * Supabase Storage are fast on slow networks and safe on 2GB RAM Android devices.
 */
object MediaCompressor {
    private const val MAX_DIMENSION_PX = 1440
    private const val JPEG_QUALITY = 82

    suspend fun prepareImageForUpload(
        context: Context,
        uri: Uri
    ): PreparedMediaUpload? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.applicationContext.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) {
                val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                val mime = resolver.getType(uri) ?: "image/jpeg"
                return@runCatching PreparedMediaUpload(raw, mime)
            }

            var sampleSize = 1
            val longest = max(width, height)
            while (longest / sampleSize > MAX_DIMENSION_PX * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@runCatching null

            val scaled = if (max(decoded.width, decoded.height) > MAX_DIMENSION_PX) {
                val ratio = MAX_DIMENSION_PX.toFloat() / max(decoded.width, decoded.height).toFloat()
                val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            scaled.recycle()

            PreparedMediaUpload(
                bytes = output.toByteArray(),
                mimeType = "image/jpeg"
            )
        }.getOrNull()
    }
}
