package com.cityfix.safenight.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime permissions required for the full SafeNight experience.
 * Each capability degrades gracefully when denied.
 */
object PermissionUtils {

    /** Everything requested on first activation, in a single system dialog chain. */
    fun requiredRuntimePermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }
        return permissions.toTypedArray()
    }

    fun hasLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasCall(context: Context): Boolean = isGranted(context, Manifest.permission.CALL_PHONE)

    fun hasSms(context: Context): Boolean = isGranted(context, Manifest.permission.SEND_SMS)

    fun hasRecordAudio(context: Context): Boolean = isGranted(context, Manifest.permission.RECORD_AUDIO)

    fun hasBluetoothScan(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isGranted(context, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            hasLocation(context)
        }

    /** Call/SMS cannot run at all without these, so the SOS dock warns early. */
    fun missingSosPermissions(context: Context): List<String> = buildList {
        if (!hasCall(context)) add("phone")
        if (!hasSms(context)) add("SMS")
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
