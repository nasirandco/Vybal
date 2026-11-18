package com.example.core.performance

import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient

/**
 * Builds a low-RAM Coil ImageLoader tuned for 2GB RAM Android devices and slow networks:
 * - Caps in-memory bitmap cache at 15% of available heap.
 * - Uses RGB_565 by default (50% lower bitmap memory footprint vs ARGB_8888).
 * - Maintains a 25MB disk cache to avoid redundant downloads on slow connections.
 */
object VybalImageLoader {
    fun create(context: Context, okHttpClient: OkHttpClient): ImageLoader {
        val appContext = context.applicationContext
        return ImageLoader.Builder(appContext)
            .okHttpClient(okHttpClient)
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(150)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("vybal_image_cache"))
                    .maxSizeBytes(25L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(true)
            .build()
    }
}
