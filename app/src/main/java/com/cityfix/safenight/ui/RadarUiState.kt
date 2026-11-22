package com.cityfix.safenight.ui

import com.cityfix.safenight.location.UserFix
import com.cityfix.safenight.radar.AmbientSignal
import com.cityfix.safenight.radar.ZoneLevel
import com.cityfix.safenight.sos.SosStep

/**
 * Immutable snapshot rendered by the Compose tree. Everything the UI needs is
 * derived here so the composables stay pure and previewable.
 */
data class RadarUiState(
    val permissionGranted: Boolean = false,
    val isRadarActive: Boolean = false,
    val userFix: UserFix? = null,
    val signals: List<AmbientSignal> = emptyList(),
    val zone: ZoneLevel = ZoneLevel.ISOLATED,
    val bleScanning: Boolean = false,
    val wifiScanning: Boolean = false,
    val audioBufferReady: Boolean = false,
    val testMode: Boolean = false,
    val testNumber: String = "",
    val sosRunning: Boolean = false,
    val sosSteps: List<SosStep> = emptyList(),
    val sosSummary: String? = null,
    val notice: String? = null
) {
    /** Signals inside the 50 m outer ring, the number shown in the pill. */
    val nearbyCount: Int get() = signals.count { it.distanceMeters <= OUTER_RING_M }

    companion object {
        const val OUTER_RING_M = 50f
        const val INNER_RING_M = 15f
    }
}

/** One-shot actions that need an Activity-level capability (share sheet). */
sealed interface RadarEvent {
    data class ShareRadar(val text: String) : RadarEvent
}
