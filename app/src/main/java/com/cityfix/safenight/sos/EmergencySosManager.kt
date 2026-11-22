package com.cityfix.safenight.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.cityfix.safenight.location.mapsLinkGoogle
import com.cityfix.safenight.location.mapsLinkOsm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-tap panic pipeline.
 *
 * 1. Place a direct [Intent.ACTION_CALL] with a DTMF pause sequence so the
 *    emergency line is reached without navigating an IVR.
 * 2. Flush the last 10 seconds of ambient audio to a private WAV file.
 * 3. Send a background SMS carrying a live Google Maps / OpenStreetMap pin.
 *
 * The 10 second buffer must be primed by the UI while the radar is active
 * ([primeAudioBuffer]); otherwise step 2 is skipped gracefully.
 */
class EmergencySosManager(private val context: Context) {

    private val audioBuffer = PanicAudioBuffer()

    /** Starts the rolling pre-connect audio buffer. Returns false if mic denied. */
    fun primeAudioBuffer(): Boolean = audioBuffer.start(context)

    fun isAudioPrimed(): Boolean = audioBuffer.isCapturing

    /** Releases the microphone (e.g. radar paused or ViewModel cleared). */
    fun stopAudioBuffer() = audioBuffer.stop()

    fun release() {
        audioBuffer.stop()
    }

    /**
     * Runs the full SOS sequence on the main thread (call intents must be
     * launched from a UI-capable context) with blocking work moved to IO.
     *
     * @param onProgress receives an immutable snapshot after every step so the
     *        Compose overlay can animate progress live.
     */
    @SuppressLint("MissingPermission")
    suspend fun trigger(
        latitude: Double?,
        longitude: Double?,
        target: SosTarget,
        onProgress: (List<SosStep>) -> Unit
    ): SosReport = withContext(Dispatchers.Main) {
        val steps = mutableListOf(
            SosStep(
                label = if (target.isTest) "Calling test line" else "Connecting live emergency call",
                detail = target.callNumber + target.dtmfSuffix.replace(",", " · pause")
            ),
            SosStep(label = "Securing last 10 s of ambient audio"),
            SosStep(label = "Sending GPS alert to ${target.smsNumber}")
        )
        fun publish() = onProgress(steps.toList())
        publish()

        // --- 1. Direct call, no IVR navigation ---------------------------
        steps[0] = steps[0].copy(status = StepStatus.RUNNING)
        publish()
        val callPlaced = placeCall(target)
        steps[0] = steps[0].copy(
            status = if (callPlaced) StepStatus.DONE else StepStatus.FAILED,
            detail = if (callPlaced) {
                if (target.isTest) "Test call placed" else "Live call placed"
            } else {
                "Call failed — check CALL_PHONE permission"
            }
        )
        publish()

        // --- 2. 10 s pre-connect audio buffer ----------------------------
        steps[1] = steps[1].copy(status = StepStatus.RUNNING)
        publish()
        val audioFile: File? = audioBuffer.stopAndExport(context)
        steps[1] = steps[1].copy(
            status = if (audioFile != null) StepStatus.DONE else StepStatus.FAILED,
            detail = audioFile?.let { "Saved ${it.name} (${it.length() / 1024} KB)" }
                ?: "No buffered audio — arm the radar before using SOS"
        )
        publish()

        // --- 3. Background SMS with live location ------------------------
        steps[2] = steps[2].copy(status = StepStatus.RUNNING)
        publish()
        val smsSent = sendSosSms(target.smsNumber, buildSosMessage(latitude, longitude, audioFile))
        steps[2] = steps[2].copy(
            status = if (smsSent) StepStatus.DONE else StepStatus.FAILED,
            detail = when {
                smsSent -> "Location alert delivered"
                ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
                    PackageManager.PERMISSION_GRANTED -> "SMS permission denied"
                else -> "SMS could not be sent"
            }
        )
        publish()

        val succeeded = steps.count { it.status == StepStatus.DONE }
        SosReport(
            steps = steps.toList(),
            summary = when {
                succeeded == steps.size -> "SOS complete — call live and location shared."
                succeeded > 0 -> "SOS partially delivered ($succeeded/${steps.size} steps)."
                else -> "SOS could not be delivered. Call 112 manually."
            },
            audioFilePath = audioFile?.absolutePath
        )
    }

    // ---------------------------------------------------------------------
    // Call
    // ---------------------------------------------------------------------

    private fun placeCall(target: SosTarget): Boolean {
        val number = target.callNumber
        if (number.isBlank()) return false

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            // Raw commas in a tel: URI are interpreted as dial pauses by the
            // telecom stack. They are what bypasses the automated IVR menu.
            val uri = Uri.parse("tel:$number${target.dtmfSuffix}")
            val intent = Intent(Intent.ACTION_CALL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("android.telecom.extra.START_CALL_WITH_SPEAKERPHONE", true)
            }
            try {
                context.startActivity(intent)
                return true
            } catch (_: SecurityException) {
                Log.w(TAG, "CALL_PHONE was revoked; falling back to dialer")
            } catch (_: ActivityNotFoundException) {
                Log.w(TAG, "No dialer available; falling back to plain number")
            }
            // Some OEM dialers reject the pause sequence: retry without DTMF.
            try {
                context.startActivity(
                    Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (_: SecurityException) {
                Log.w(TAG, "Direct call blocked")
            } catch (_: ActivityNotFoundException) {
                Log.w(TAG, "No telephony app")
            }
        }

        // Last resort: open the dialer pre-filled so one tap still connects.
        return try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            false
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    // ---------------------------------------------------------------------
    // SMS
    // ---------------------------------------------------------------------

    private fun sendSosSms(number: String, message: String): Boolean {
        if (number.isBlank()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            val manager = smsManager()
            // Sending parts one by one avoids the ArrayList/List overload
            // ambiguity in SmsManager and works identically on all API levels.
            manager.divideMessage(message).forEach { part ->
                manager.sendTextMessage(number, null, part, null, null)
            }
            true
        } catch (_: SecurityException) {
            Log.w(TAG, "SEND_SMS denied at runtime")
            false
        } catch (error: Exception) {
            Log.w(TAG, "SMS send failed", error)
            false
        }
    }

    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    private fun buildSosMessage(latitude: Double?, longitude: Double?, audio: File?): String {
        if (latitude == null || longitude == null) {
            return "SOS from SafeNight Radar. I need help now. " +
                "Live location is unavailable — please call me immediately."
        }
        return buildString {
            append("SOS from SafeNight Radar. I need help. ")
            append("Live location: ")
            append(mapsLinkGoogle(latitude, longitude))
            append(" | OpenStreetMap: ")
            append(mapsLinkOsm(latitude, longitude))
            if (audio != null) {
                append(" (10 s of ambient audio saved on my phone.)")
            }
        }
    }

    private companion object {
        const val TAG = "EmergencySosManager"
    }
}
