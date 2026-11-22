package com.cityfix.safenight.map

import org.osmdroid.config.Configuration
import java.net.HttpURLConnection
import java.net.URL

/**
 * One real tile request, synchronously, before the map commits to a tile source.
 *
 * osmdroid swallows download failures: a blocked host or a captive portal just
 * leaves an empty black canvas with no message. This probe turns that into a
 * decision (fall back / tell the user) instead of a silent failure.
 */
object TileProbe {

    private const val DEFAULT_UA = "SafeNightRadar/1.0 (com.cityfix.safenight)"

    /** True when [url] answers with 2xx within [timeoutMs]. */
    fun reachable(url: String, timeoutMs: Int = 7000): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                useCaches = false
                setRequestProperty("User-Agent", userAgent())
                setRequestProperty("Accept", "image/png,image/*")
            }
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun userAgent(): String {
        val configured: String? = Configuration.getInstance().userAgentValue
        return configured?.takeIf { it.isNotBlank() } ?: DEFAULT_UA
    }
}
