package com.cityfix.safenight.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Always-on 10 second audio ring buffer for panic moments.
 *
 * MediaRecorder can only start writing when it is told to, which would lose the
 * seconds *before* the user hits SOS (the part that matters). Instead we capture
 * raw 16 kHz mono PCM with [AudioRecord] into a fixed-size in-memory ring and
 * flush the last 10 seconds to a WAV file the instant SOS fires.
 *
 * Audio is written only to app-private storage, is never uploaded, and the ring
 * buffer is discarded when the radar is switched off.
 */
class PanicAudioBuffer(
    private val capacitySeconds: Int = 10,
    private val sampleRate: Int = 16_000
) {

    private val running = AtomicBoolean(false)
    private val lock = Any()
    private val frames = ArrayDeque<ShortArray>()
    private var bufferedSamples = 0

    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null

    /** True once capture is live and pre-roll audio is available. */
    val isCapturing: Boolean get() = running.get()

    /**
     * Starts the rolling buffer. Returns false when RECORD_AUDIO is missing or
     * the device refuses another active capture (e.g. a call in progress).
     */
    fun start(context: Context): Boolean {
        if (running.get()) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) return false

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            return false
        }

        recorder = record
        synchronized(lock) {
            frames.clear()
            bufferedSamples = 0
        }
        running.set(true)

        try {
            record.startRecording()
        } catch (_: IllegalStateException) {
            running.set(false)
            record.release()
            recorder = null
            return false
        }

        captureThread = thread(name = "safenight-audio-ring", isDaemon = true) {
            val buffer = ShortArray(minBufferSize)
            while (running.get()) {
                val read = try {
                    record.read(buffer, 0, buffer.size)
                } catch (_: IllegalStateException) {
                    -1
                }
                if (read > 0) {
                    push(buffer.copyOf(read))
                }
            }
        }
        return true
    }

    /** Stops capture and releases the microphone. */
    fun stop() {
        if (!running.getAndSet(false)) {
            releaseRecorder()
            return
        }
        captureThread?.join(500)
        captureThread = null
        releaseRecorder()
    }

    /**
     * Stops capture, snapshots the ring and writes a playable WAV into
     * `filesDir/sos_audio`. Returns null when nothing was buffered.
     */
    suspend fun stopAndExport(context: Context): File? = withContext(Dispatchers.IO) {
        val snapshot = synchronized(lock) {
            val copy = frames.toList()
            frames.clear()
            bufferedSamples = 0
            copy
        }
        stop()
        if (snapshot.isEmpty()) return@withContext null

        runCatching {
            val directory = File(context.filesDir, "sos_audio").apply { mkdirs() }
            val file = File(directory, "sos_${System.currentTimeMillis()}.wav")
            writeWav(file, snapshot)
            file
        }.onFailure { Log.w(TAG, "Unable to write SOS audio", it) }
            .getOrNull()
    }

    private fun push(chunk: ShortArray) {
        synchronized(lock) {
            frames.addLast(chunk)
            bufferedSamples += chunk.size
            val maxSamples = sampleRate * capacitySeconds
            while (bufferedSamples > maxSamples && frames.isNotEmpty()) {
                bufferedSamples -= frames.removeFirst().size
            }
        }
    }

    private fun releaseRecorder() {
        val record = recorder ?: return
        recorder = null
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        }
        runCatching { record.release() }
    }

    private fun writeWav(file: File, chunks: List<ShortArray>) {
        val totalSamples = chunks.sumOf { it.size }
        val dataBytes = totalSamples * 2
        FileOutputStream(file).use { output ->
            val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)                           // PCM chunk size
            header.putShort(1)                          // PCM format
            header.putShort(1)                          // mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)               // byte rate
            header.putShort(2)                          // block align
            header.putShort(16)                         // bits per sample
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes)
            output.write(header.array())

            val chunkBytes = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN)
            chunks.forEach { chunk ->
                var index = 0
                while (index < chunk.size) {
                    chunkBytes.clear()
                    val end = minOf(chunk.size, index + 2048)
                    while (index < end) {
                        chunkBytes.putShort(chunk[index])
                        index++
                    }
                    output.write(chunkBytes.array(), 0, chunkBytes.position())
                }
            }
        }
    }

    private companion object {
        const val TAG = "PanicAudioBuffer"
        const val WAV_HEADER_BYTES = 44
    }
}
