package com.cityfix.safenight.ui.components

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.cityfix.safenight.location.UserFix
import com.cityfix.safenight.map.CartoDark
import com.cityfix.safenight.map.TileProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/** Fallback centre until the first GPS fix: central Bengaluru. */
private const val DEFAULT_LAT = 12.9716
private const val DEFAULT_LON = 77.5946
private const val DEFAULT_ZOOM = 17.0

/** Low-zoom tile over the default centre, used for the reachability probe. */
private const val PROBE_ZOOM = 10
private val PROBE_TILE: Long = MapTileIndex.getTileIndex(
    PROBE_ZOOM,
    tileX(DEFAULT_LON, PROBE_ZOOM),
    tileY(DEFAULT_LAT, PROBE_ZOOM)
)

/**
 * Full-screen OpenStreetMap without an API key.
 *
 * Tiles come from the Carto dark basemap, which is night-coloured out of the box,
 * so the map reads as a dark basemap the moment the first tile lands instead of
 * waiting for a colour filter over a daylight raster. Caching is app-private and
 * configured once in [com.cityfix.safenight.SafeNightApplication], so panning a
 * route at home keeps the map usable with no signal later.
 *
 * osmdroid reports tile failures nowhere, so the map is probed once: if the dark
 * CDN is unreachable it falls back to OSM Mapnik (with the night matrix), and if
 * both are unreachable [onTileStatus] carries a message for the HUD.
 */
@Composable
fun NightMapView(
    userFix: UserFix?,
    followUser: Boolean,
    onMapReady: (MapView) -> Unit,
    modifier: Modifier = Modifier,
    onTileStatus: (String) -> Unit = {}
) {
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            id = View.generateViewId()
            setTileSource(CartoDark)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            setUseDataConnection(true)
            setBackgroundColor(android.graphics.Color.parseColor("#05070D"))
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // MapView.setZoomLevel() is package-private in osmdroid 6.1.x;
            // the controller overload is the public entry point.
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LON))
        }
    }

    var centredOnce by remember { mutableStateOf(false) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    LaunchedEffect(mapView) {
        onMapReady(mapView)

        val cartoReachable = withContext(Dispatchers.IO) {
            TileProbe.reachable(CartoDark.getTileURLString(PROBE_TILE))
        }
        if (cartoReachable) return@LaunchedEffect

        val mapnikReachable = withContext(Dispatchers.IO) {
            TileProbe.reachable(TileSourceFactory.MAPNIK.getTileURLString(PROBE_TILE))
        }
        when {
            mapnikReachable -> {
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.overlayManager.tilesOverlay
                    .setColorFilter(ColorMatrixColorFilter(nightMatrix()))
                mapView.invalidate()
            }

            else -> onTileStatus(
                "Map tiles load nahi ho rahe. Internet ya Wi-Fi check karein."
            )
        }
    }

    LaunchedEffect(userFix?.latitude, userFix?.longitude, followUser) {
        val fix = userFix ?: return@LaunchedEffect
        if (!followUser) return@LaunchedEffect
        val point = GeoPoint(fix.latitude, fix.longitude)
        if (!centredOnce) {
            mapView.controller.animateTo(point)
            centredOnce = true
        } else {
            mapView.controller.setCenter(point)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun tileX(lon: Double, zoom: Int): Int {
    val tiles = 1 shl zoom
    return ((lon + 180.0) / 360.0 * tiles).toInt().coerceIn(0, tiles - 1)
}

private fun tileY(lat: Double, zoom: Int): Int {
    val tiles = 1 shl zoom
    val latRad = lat * PI / 180.0
    val mercator = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
    return (mercator * tiles).toInt().coerceIn(0, tiles - 1)
}

/**
 * Desaturate + crush luminance so OSM's bright raster becomes a night basemap.
 * Only used for the Mapnik fallback: the Carto dark tiles are already dark, and
 * running this matrix over them would crush them to solid black.
 */
private fun nightMatrix(): ColorMatrix {
    val matrix = ColorMatrix().apply { setSaturation(0.22f) }
    matrix.postConcat(
        ColorMatrix(
            floatArrayOf(
                0.62f, 0.12f, 0.08f, 0f, -26f,
                0.06f, 0.68f, 0.10f, 0f, -18f,
                0.06f, 0.12f, 0.82f, 0f, 2f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
    return matrix
}
