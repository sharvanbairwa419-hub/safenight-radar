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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** Fallback centre until the first GPS fix: central Bengaluru. */
private const val DEFAULT_LAT = 12.9716
private const val DEFAULT_LON = 77.5946
private const val DEFAULT_ZOOM = 17.0

/**
 * Full-screen OpenStreetMap without an API key.
 *
 * Tiles are recoloured with a night matrix so the standard OSM raster fits the
 * dark UI without needing a paid dark tile provider. Caching is app-private and
 * configured once in [com.cityfix.safenight.SafeNightApplication], so panning a
 * route at home keeps the map usable with no signal later.
 */
@Composable
fun NightMapView(
    userFix: UserFix?,
    followUser: Boolean,
    onMapReady: (MapView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            id = View.generateViewId()
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setTilesScaledToDpi(true)
            setUseDataConnection(true)
            setBackgroundColor(android.graphics.Color.parseColor("#05070D"))
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // MapView.setZoomLevel() is package-private in osmdroid 6.1.x;
            // the controller overload is the public entry point.
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LON))
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(nightMatrix()))
        }
    }

    var centredOnce by remember { mutableStateOf(false) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    LaunchedEffect(mapView) { onMapReady(mapView) }

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

/**
 * Desaturate + crush luminance so OSM's bright raster becomes a night basemap.
 * The blue channel is preserved slightly so water reads as water.
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
