package com.cityfix.safenight.radar

/**
 * Signal source for one ambient hit. The app never needs more than this.
 */
enum class SignalKind { BLE, WIFI }

/**
 * A single anonymised ambient radio signal.
 *
 * [key] is a per-session salted hash of the MAC/BSSID, so nothing that could
 * identify a person or a device is ever stored, logged or persisted. A new salt
 * is generated every time [RadarSensorManager] is constructed, which means the
 * keys cannot be correlated across sessions either.
 */
data class AmbientSignal(
    val key: String,
    val kind: SignalKind,
    val rssi: Int,
    /** Estimated distance in metres, smoothed with an exponential moving average. */
    val distanceMeters: Float,
    /** Deterministic 0f..1f angle seed so dots keep a stable spot on the ring. */
    val angleTurn: Float,
    val lastSeenAtMs: Long
)

/**
 * Crowd-density verdict derived from the number of live signals inside the
 * 50 m outer ring.
 */
enum class ZoneLevel {
    SAFE,
    MODERATE,
    ISOLATED
}

/**
 * Health of the two passive radios, surfaced in the UI when a sensor is off.
 */
data class RadarStatus(
    val bleAvailable: Boolean = false,
    val bleScanning: Boolean = false,
    val wifiAvailable: Boolean = false,
    val wifiScanning: Boolean = false
)
