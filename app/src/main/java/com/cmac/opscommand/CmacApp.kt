package com.cmac.opscommand

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

/**
 * Registers Coil's OkHttp network stack and gives the static map imagery a real
 * disk cache — so a TV that loses its network, or reboots, still paints the last
 * known dispatch map instead of an empty panel.
 */
class CmacApp : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("map_cache").toOkioPath())
                    .maxSizeBytes(32L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
