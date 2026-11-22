package com.cityfix.safenight

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Application entry point.
 *
 * OSMDroid must be configured once, before the first MapView is inflated:
 * a shared user agent (as required by the OSM tile policy), an app-private tile
 * cache so the map works offline after first load, and a bounded cache size so
 * the directory never grows without limit.
 */
class SafeNightApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val config = Configuration.getInstance()
        config.load(this, getSharedPreferences(OSMDROID_PREFS, Context.MODE_PRIVATE))
        config.userAgentValue = packageName

        val basePath = File(filesDir, "osmdroid")
        val tileCache = File(basePath, "tiles")
        if (!tileCache.exists()) tileCache.mkdirs()

        config.osmdroidBasePath = basePath
        config.osmdroidTileCache = tileCache
        config.tileFileSystemCacheMaxBytes = CACHE_MAX_BYTES
        config.tileFileSystemCacheTrimBytes = CACHE_TRIM_BYTES
    }

    private companion object {
        const val OSMDROID_PREFS = "safenight.osmdroid"
        const val CACHE_MAX_BYTES = 600L * 1024 * 1024
        const val CACHE_TRIM_BYTES = 500L * 1024 * 1024
    }
}
