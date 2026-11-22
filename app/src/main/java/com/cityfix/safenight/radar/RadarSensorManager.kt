package com.cityfix.safenight.radar

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * Passive ambient crowd radar.
 *
 * Listens to BLE advertisements and Wi-Fi scan results, converts RSSI into a
 * rough distance and publishes an anonymised snapshot of what is around the
 * user. Privacy guarantees:
 *
 *  - The raw MAC address / BSSID never leaves this class.
 *  - [hashKey] salts it with a random per-session value and truncates the hash,
 *    so the output cannot be reversed or tracked across launches.
 *  - Everything lives in memory only; nothing is written to disk.
 *
 * Direction is unknown with a single antenna, so each signal is assigned a
 * stable pseudo-random angle by the UI instead.
 */
class RadarSensorManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Fresh random salt per manager instance: hashes are valid for one session. */
    private val sessionSalt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)
    private val wifiManager: WifiManager? =
        appContext.getSystemService(WifiManager::class.java)

    private val tracked = ConcurrentHashMap<String, TrackedSignal>()

    private val _signals = MutableStateFlow<List<AmbientSignal>>(emptyList())
    val signals: StateFlow<List<AmbientSignal>> = _signals.asStateFlow()

    private val _status = MutableStateFlow(RadarStatus())
    val status: StateFlow<RadarStatus> = _status.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private var publishJob: Job? = null
    private var bleKeepAliveJob: Job? = null
    private var wifiJob: Job? = null

    @Volatile
    private var running = false

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    /**
     * Starts both passive scanners. Safe to call repeatedly; each radio starts
     * independently so a missing Bluetooth permission never blocks Wi-Fi.
     */
    fun start() {
        if (running) return
        running = true

        _status.value = _status.value.copy(
            bleAvailable = bluetoothManager?.adapter != null,
            wifiAvailable = wifiManager != null
        )

        startBleScan()

        publishJob = scope.launch {
            while (isActive) {
                publishSnapshot()
                delay(SNAPSHOT_INTERVAL_MS)
            }
        }

        // Android silently stops long-running scans; restart periodically.
        bleKeepAliveJob = scope.launch {
            while (isActive) {
                delay(BLE_RESTART_INTERVAL_MS)
                restartBleScan()
            }
        }

        wifiJob = scope.launch {
            while (isActive) {
                refreshWifiScan()
                delay(WIFI_POLL_INTERVAL_MS)
            }
        }
    }

    /** Stops all radios and clears every tracked signal from memory. */
    fun stop() {
        running = false
        publishJob?.cancel()
        bleKeepAliveJob?.cancel()
        wifiJob?.cancel()
        publishJob = null
        bleKeepAliveJob = null
        wifiJob = null
        stopBleScan()
        tracked.clear()
        _signals.value = emptyList()
        _status.value = _status.value.copy(bleScanning = false, wifiScanning = false)
    }

    fun release() = stop()

    // ---------------------------------------------------------------------
    // BLE
    // ---------------------------------------------------------------------

    private fun startBleScan() {
        if (!running) return
        if (!hasBleScanPermission()) return
        val adapter: BluetoothAdapter = bluetoothManager?.adapter ?: return
        if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return
        if (!adapter.isEnabled) {
            _status.value = _status.value.copy(bleScanning = false)
            return
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _status.value = _status.value.copy(bleScanning = false)
            return
        }

        if (scanCallback == null) {
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    ingestBle(result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach(::ingestBle)
                }

                override fun onScanFailed(errorCode: Int) {
                    _status.value = _status.value.copy(bleScanning = false)
                }
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0L)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            _status.value = _status.value.copy(bleScanning = true)
        } catch (_: SecurityException) {
            _status.value = _status.value.copy(bleScanning = false)
        } catch (_: IllegalStateException) {
            // Bluetooth was switched off between the check and the call.
            _status.value = _status.value.copy(bleScanning = false)
        }
    }

    private fun restartBleScan() {
        if (!running) return
        stopBleScan()
        startBleScan()
    }

    private fun stopBleScan() {
        val callback = scanCallback ?: return
        scanCallback = null
        if (!hasBleScanPermission()) return
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(callback)
        } catch (_: SecurityException) {
            // Permission revoked mid-session: nothing left to stop.
        }
        _status.value = _status.value.copy(bleScanning = false)
    }

    private fun ingestBle(result: ScanResult) {
        // Reading the address requires BLUETOOTH_CONNECT on API 31+. If the
        // permission was revoked while a scan was in flight, drop the result
        // instead of letting the exception kill the scan callback thread.
        val address = try {
            result.device?.address
        } catch (_: SecurityException) {
            null
        } ?: return
        // AD transmit power (dBm @ 1 m) lets us calibrate the path-loss model.
        val txPower = result.scanRecord?.txPowerLevel
        ingest(SignalKind.BLE, address, result.rssi, txPower)
    }

    private fun hasBleScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    // ---------------------------------------------------------------------
    // Wi-Fi
    // ---------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun refreshWifiScan() {
        val manager = wifiManager ?: return
        if (!hasFineLocationPermission()) return
        val enabled = try {
            manager.isWifiEnabled
        } catch (_: SecurityException) {
            false
        }
        if (!enabled) {
            _status.value = _status.value.copy(wifiScanning = false)
            return
        }

        val results = try {
            manager.scanResults
        } catch (_: SecurityException) {
            null
        }

        if (results.isNullOrEmpty()) {
            _status.value = _status.value.copy(wifiScanning = false)
            return
        }

        results.forEach { ap ->
            // BSSID is the stable identifier; fall back to SSID for hidden APs.
            val rawId = ap.BSSID ?: ap.SSID ?: return@forEach
            ingest(SignalKind.WIFI, rawId, ap.level, null)
        }
        _status.value = _status.value.copy(wifiScanning = true)
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------------
    // Shared ingestion + distance estimation
    // ---------------------------------------------------------------------

    private fun ingest(kind: SignalKind, rawId: String, rssi: Int, txPower: Int?) {
        if (rssi == 0) return
        val key = hashKey(rawId)
        val now = SystemClock.elapsedRealtime()
        val measured = estimateDistanceMeters(rssi, txPower)

        tracked.compute(key) { _, existing ->
            if (existing == null) {
                TrackedSignal(
                    kind = kind,
                    rssi = rssi,
                    distanceMeters = measured,
                    angleTurn = deterministicAngle(key),
                    lastSeenMs = now
                )
            } else {
                // Exponential smoothing stops dots from jumping between rings.
                existing.distanceMeters = existing.distanceMeters * 0.65f + measured * 0.35f
                existing.rssi = (existing.rssi * 2 + rssi) / 3
                existing.lastSeenMs = now
                existing
            }
        }

        if (tracked.size > MAX_TRACKED_SIGNALS) {
            // Drop the least recently seen entries to bound memory.
            tracked.entries
                .sortedBy { it.value.lastSeenMs }
                .take(tracked.size - MAX_TRACKED_SIGNALS)
                .forEach { tracked.remove(it.key) }
        }
    }

    private fun publishSnapshot() {
        val now = SystemClock.elapsedRealtime()
        val fresh = tracked.entries
            .filter { now - it.value.lastSeenMs <= STALE_AFTER_MS && it.value.distanceMeters <= MAX_RANGE_M }
            .map { entry ->
                AmbientSignal(
                    key = entry.key,
                    kind = entry.value.kind,
                    rssi = entry.value.rssi,
                    distanceMeters = entry.value.distanceMeters,
                    angleTurn = entry.value.angleTurn,
                    lastSeenAtMs = entry.value.lastSeenMs
                )
            }
            .sortedBy { it.distanceMeters }

        _signals.value = fresh
        _status.value = _status.value.copy(wifiScanning = fresh.any { it.kind == SignalKind.WIFI })
    }

    /**
     * Log-distance path-loss model. [txPower] is the calibrated RSSI at 1 m
     * (-59 dBm is a sensible default for both BLE and consumer Wi-Fi APs).
     */
    private fun estimateDistanceMeters(rssi: Int, txPower: Int?): Float {
        val reference = (txPower ?: DEFAULT_TX_POWER).coerceIn(-100, 10)
        val exponent = (reference - rssi) / (10.0 * PATH_LOSS_EXPONENT)
        return 10.0.pow(exponent).toFloat().coerceIn(MIN_DISTANCE_M, MAX_RANGE_M)
    }

    /** 6 bytes of SHA-256(salt + identifier): irreversible for this session. */
    private fun hashKey(rawId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sessionSalt)
        digest.update(rawId.toByteArray(Charsets.UTF_8))
        val bytes = digest.digest()
        val builder = StringBuilder(HASH_BYTES * 2)
        for (index in 0 until HASH_BYTES) {
            builder.append(String.format(Locale.US, "%02x", bytes[index]))
        }
        return builder.toString()
    }

    private fun deterministicAngle(key: String): Float {
        val value = (key.hashCode().toLong() and 0xFFFFFFL).toFloat()
        return value / 0xFFFFFFL.toFloat()
    }

    private class TrackedSignal(
        val kind: SignalKind,
        var rssi: Int,
        var distanceMeters: Float,
        val angleTurn: Float,
        var lastSeenMs: Long
    )

    private companion object {
        const val DEFAULT_TX_POWER = -59
        const val PATH_LOSS_EXPONENT = 2.2
        const val MIN_DISTANCE_M = 0.4f
        const val MAX_RANGE_M = 55f
        const val MAX_TRACKED_SIGNALS = 180
        const val HASH_BYTES = 6
        const val SNAPSHOT_INTERVAL_MS = 1000L
        const val WIFI_POLL_INTERVAL_MS = 4000L
        const val BLE_RESTART_INTERVAL_MS = 15_000L
        const val STALE_AFTER_MS = 20_000L
    }
}
