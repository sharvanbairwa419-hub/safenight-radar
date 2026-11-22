package com.cityfix.safenight.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Immutable location fix handed to the ViewModel and Compose UI.
 */
data class UserFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float,
    val bearingDegrees: Float,
    val timestampMs: Long
)

/**
 * Thin wrapper around [FusedLocationProviderClient] exposing a cold Flow of fixes.
 * The flow closes itself when location permission is missing.
 */
class LocationTracker(private val context: Context) {

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 2000L): Flow<UserFix> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(0.5f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toUserFix()) }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }

        awaitClose { client.removeLocationUpdates(callback) }
    }
}

private fun Location.toUserFix(): UserFix = UserFix(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    speedMps = speed,
    bearingDegrees = bearing,
    timestampMs = time
)

/** Opens a Google Maps pin, the most universally supported link in an SMS. */
fun mapsLinkGoogle(latitude: Double, longitude: Double): String =
    "https://maps.google.com/?q=%.6f,%.6f".format(java.util.Locale.US, latitude, longitude)

/** OpenStreetMap link used by the in-app share sheet and the SOS message. */
fun mapsLinkOsm(latitude: Double, longitude: Double): String =
    "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=19/%.6f/%.6f"
        .format(java.util.Locale.US, latitude, longitude, latitude, longitude)
