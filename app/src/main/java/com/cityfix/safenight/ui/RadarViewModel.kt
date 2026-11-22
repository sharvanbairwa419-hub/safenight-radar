package com.cityfix.safenight.ui

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cityfix.safenight.location.LocationTracker
import com.cityfix.safenight.location.mapsLinkGoogle
import com.cityfix.safenight.location.mapsLinkOsm
import com.cityfix.safenight.radar.RadarSensorManager
import com.cityfix.safenight.radar.ZoneLevel
import com.cityfix.safenight.sos.EmergencySosManager
import com.cityfix.safenight.sos.SosTarget
import com.cityfix.safenight.util.PermissionUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MVVM brain of SafeNight Radar.
 *
 * Owns the passive sensor manager, the fused location flow and the SOS pipeline,
 * and melts everything down into a single [RadarUiState] stream for Compose.
 * No location or signal data is ever persisted.
 */
class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val sensorManager = RadarSensorManager(application)
    private val sosManager = EmergencySosManager(application)
    private val locationTracker = LocationTracker(application)

    private val _state = MutableStateFlow(
        RadarUiState(
            permissionGranted = PermissionUtils.hasLocation(application),
            testMode = prefs.getBoolean(KEY_TEST_MODE, false),
            testNumber = prefs.getString(KEY_TEST_NUMBER, "").orEmpty()
        )
    )
    val state: StateFlow<RadarUiState> = _state.asStateFlow()

    private val _events = Channel<RadarEvent>(Channel.BUFFERED)
    val events: Flow<RadarEvent> = _events.receiveAsFlow()

    private var locationJob: Job? = null

    init {
        // Ambient signals -> zone verdict.
        viewModelScope.launch {
            sensorManager.signals.collect { signals ->
                _state.update { current ->
                    current.copy(
                        signals = signals,
                        zone = zoneFor(signals.count { it.distanceMeters <= OUTER_RING_M })
                    )
                }
            }
        }
        // Radio health -> sensor strip + permission hints.
        viewModelScope.launch {
            sensorManager.status.collect { status ->
                _state.update {
                    it.copy(
                        bleScanning = status.bleScanning,
                        wifiScanning = status.wifiScanning
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Permissions
    // ---------------------------------------------------------------------

    fun syncPermissions(locationGranted: Boolean) {
        _state.update { it.copy(permissionGranted = locationGranted) }
    }

    /** Called with the launcher result; starts the radar when location is in. */
    fun onPermissionResult(result: Map<String, Boolean>) {
        val locationGranted =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        _state.update { it.copy(permissionGranted = locationGranted) }
        if (locationGranted) {
            startRadar()
        } else {
            showNotice("Location is required for the radar. Enable it in system settings.")
        }
    }

    // ---------------------------------------------------------------------
    // Radar
    // ---------------------------------------------------------------------

    fun startRadar() {
        if (_state.value.isRadarActive) return
        val app = getApplication<Application>()

        if (!PermissionUtils.hasLocation(app)) {
            showNotice("Location permission is required to activate the radar.")
            return
        }

        _state.update { it.copy(isRadarActive = true, notice = null) }
        sensorManager.start()

        // Prime the 10 s pre-roll immediately so SOS never starts cold.
        val audioReady = sosManager.primeAudioBuffer()
        _state.update { it.copy(audioBufferReady = audioReady) }

        if (!PermissionUtils.hasBluetoothScan(app)) {
            showNotice("Bluetooth scanning is off — the radar is using Wi-Fi signals only.")
        }

        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationTracker.locationUpdates()
                .catch { /* Location may be disabled; radar keeps working. */ }
                .collect { fix -> _state.update { it.copy(userFix = fix) } }
        }
    }

    fun stopRadar() {
        locationJob?.cancel()
        locationJob = null
        sensorManager.stop()
        sosManager.stopAudioBuffer()
        _state.update {
            it.copy(
                isRadarActive = false,
                signals = emptyList(),
                zone = ZoneLevel.ISOLATED,
                audioBufferReady = false
            )
        }
    }

    // ---------------------------------------------------------------------
    // SOS
    // ---------------------------------------------------------------------

    fun triggerSos() {
        val snapshot = _state.value
        if (snapshot.sosRunning) return

        val target = if (snapshot.testMode) {
            if (snapshot.testNumber.count { it.isDigit() } < 3) {
                showNotice("Add a test phone number before using SOS in test mode.")
                return
            }
            SosTarget.test(snapshot.testNumber)
        } else {
            SosTarget.emergency()
        }

        // Guarantees the 10 s buffer exists even if the radar was not running.
        if (!sosManager.isAudioPrimed()) {
            sosManager.primeAudioBuffer()
        }

        _state.update {
            it.copy(sosRunning = true, sosSummary = null, sosSteps = emptyList(), notice = null)
        }

        viewModelScope.launch {
            val fix = _state.value.userFix
            val report = sosManager.trigger(
                latitude = fix?.latitude,
                longitude = fix?.longitude,
                target = target
            ) { steps ->
                _state.update { current -> current.copy(sosSteps = steps) }
            }
            // The buffer was consumed: re-arm it if the radar is still live so
            // a second panic press still has 10 s of pre-roll audio.
            val rearmed = if (_state.value.isRadarActive) sosManager.primeAudioBuffer() else false
            _state.update {
                it.copy(
                    sosRunning = false,
                    sosSteps = report.steps,
                    sosSummary = report.summary,
                    audioBufferReady = rearmed
                )
            }
        }
    }

    fun dismissSos() {
        if (_state.value.sosRunning) return
        _state.update { it.copy(sosSteps = emptyList(), sosSummary = null) }
    }

    // ---------------------------------------------------------------------
    // Sharing + hackathon settings
    // ---------------------------------------------------------------------

    fun shareRadar() {
        val snapshot = _state.value
        val fix = snapshot.userFix
        val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val text = buildString {
            appendLine("SafeNight Radar — live safety radar")
            if (fix != null) {
                appendLine("Live location: ${mapsLinkOsm(fix.latitude, fix.longitude)}")
                appendLine("Google Maps: ${mapsLinkGoogle(fix.latitude, fix.longitude)}")
            } else {
                appendLine("Live location: unavailable (radar not active)")
            }
            appendLine(
                "Zone: ${snapshot.zone.name.lowercase(Locale.US)} • " +
                    "${snapshot.nearbyCount} active signals within 50 m"
            )
            append("Shared at $stamp")
        }

        viewModelScope.launch { _events.send(RadarEvent.ShareRadar(text)) }
    }

    fun setTestMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TEST_MODE, enabled).apply()
        _state.update { it.copy(testMode = enabled) }
        if (enabled) {
            showNotice("Test mode: SOS calls and texts your test number instead of 112.")
        }
    }

    fun setTestNumber(number: String) {
        val filtered = number.filter { it.isDigit() || it == '+' || it == ' ' }.take(18)
        prefs.edit().putString(KEY_TEST_NUMBER, filtered).apply()
        _state.update { it.copy(testNumber = filtered) }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun zoneFor(nearby: Int): ZoneLevel = when {
        nearby >= SAFE_SIGNAL_THRESHOLD -> ZoneLevel.SAFE
        nearby >= 1 -> ZoneLevel.MODERATE
        else -> ZoneLevel.ISOLATED
    }

    private fun showNotice(message: String) {
        _state.update { it.copy(notice = message) }
        viewModelScope.launch {
            delay(NOTICE_TIMEOUT_MS)
            if (_state.value.notice == message) {
                _state.update { it.copy(notice = null) }
            }
        }
    }

    override fun onCleared() {
        locationJob?.cancel()
        sensorManager.release()
        sosManager.release()
        super.onCleared()
    }

    private companion object {
        const val PREFS_NAME = "safenight.prefs"
        const val KEY_TEST_MODE = "test_mode"
        const val KEY_TEST_NUMBER = "test_number"
        const val NOTICE_TIMEOUT_MS = 6000L
        const val SAFE_SIGNAL_THRESHOLD = 3
        const val OUTER_RING_M = RadarUiState.OUTER_RING_M
    }
}
