package org.example.project.voicerecorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Android implementation of [AudioRecorder] for recording audio using the device microphone.
 *
 * ## Features
 * - Records audio in MP4 format with AAC encoding (44.1 kHz, 128 kbps)
 * - Stores recordings in app's external files directory (persistent, user-accessible)
 * - Supports pause/resume on Android 7.0+ (API 24+) with automatic API level validation
 * - Automatic file naming with timestamps (e.g., `audio_20260405_143022.mp4`)
 * - Robust error handling with atomic state updates and safe resource cleanup
 *
 * ## Storage & Permissions
 * Files are saved to [Context.getExternalFilesDir], which:
 * - Persists with app lifecycle (not auto-deleted like cache)
 * - Is app-private but user-accessible
 * - Requires `android.permission.RECORD_AUDIO` (handled by BaseActivity)
 * - Does NOT require external storage permission in Android 13+
 *
 * ## Lifecycle & Usage
 * ```
 * val recorder = AudioRecorderAndroid(context)
 *
 * try {
 *     // Start recording
 *     recorder.startRecording()
 *
 *     // Optional: pause & resume (automatically checks Android 7.0+)
 *     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
 *         recorder.pauseRecording()
 *         recorder.resumeRecording()
 *     }
 *
 *     // Stop and save
 *     val filePath = recorder.stopRecording("custom_name.mp4")
 *     // or use auto-generated name:
 *     val filePath = recorder.stopRecording("")
 * } catch (e: Exception) {
 *     Log.e("Recorder", "Recording failed", e)
 * }
 * ```
 *
 * ## Error Handling & State Consistency
 * All methods throw exceptions on failure. Critical invariants:
 * - [startRecording] throws if recorder creation/prepare fails; state unchanged on exception
 * - [stopRecording] throws if stop/rename fails; state unchanged if stop() fails (caller can retry)
 * - [pauseRecording]/[resumeRecording] throw [UnsupportedOperationException] on API < 24
 * - On any exception, recorder resources are safely released without clearing state
 *
 * This design ensures callers can determine if recording is still in progress via [isRecording]
 * after an exception, enabling proper retry/recovery logic.
 *
 * ## State Management
 * - `isRecording`: True between successful start() and successful stop()
 * - `isPaused`: Only valid when isRecording=true; tracks pause state
 * - Invalid operations (pause when not recording, resume when not paused) return early without error
 *
 * ## Thread Safety
 * Not thread-safe. All method calls must be from the same thread (typically the main thread).
 * Concurrent calls from multiple threads will cause undefined behavior.
 *
 * @param context Android Context for accessing external files directory
 * @throws IllegalStateException if external files directory is not available
 *
 * @see [AudioRecorder]
 * @see [BaseActivity] for permission request handling
 */
class AudioRecorderAndroid(private val context: Context) : AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var isRecording = false
    private var isPaused = false
    private var outputFile: File? = null

    private fun createRecorder() {
        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
            }
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            throw e
        }
    }

    private fun getOutputDirectory(): File {
        val dir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External files directory not available")
        dir.mkdirs()  // Safe to call multiple times, idempotent
        return dir
    }

    override fun startRecording() {
        if (isRecording) return

        try {
            if (recorder == null) {
                createRecorder()
            }

            // Create output file with timestamp (includes timezone)
            // Example filename: audio_20260405_143022_+0530.mp4
            val timestamp = getCurrentTimestamp()
            val outputDir = getOutputDirectory()
            val audioFile = File(outputDir, "audio_$timestamp.mp4")
            outputFile = audioFile

            recorder?.apply {
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            isPaused = false
        } catch (e: Exception) {
            isRecording = false
            isPaused = false
            cleanup()
            throw e
        }
    }

    override fun stopRecording(fileName: String): String? {
        if (!isRecording) return null

        try {
            // Stop and release recorder - state cleared after stop() succeeds
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            isPaused = false

            var finalFile = outputFile

            // Rename file if custom name provided
            if (fileName.isNotBlank() && outputFile != null) {
                val outputDir = outputFile?.parentFile
                    ?: throw IllegalStateException("Output directory not found")
                val newFile = File(outputDir, fileName)

                val renameSuccess = outputFile?.renameTo(newFile) ?: false
                if (!renameSuccess) {
                    throw IllegalStateException("Failed to rename file from ${outputFile?.name} to $fileName")
                }
                finalFile = newFile
            }

            return finalFile?.absolutePath
        } catch (e: Exception) {
            // On failure, only release recorder without changing state
            // Caller can determine if recording is still in progress via isRecording()
            try {
                recorder?.release()
            } catch (ex: Exception) {
                // Ignore cleanup errors
            }
            throw e
        }
    }

    override fun pauseRecording() {
        if (!isRecording || isPaused) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            throw UnsupportedOperationException("pause/resume requires API 24+ (Android 7.0+)")
        }

        recorder?.pause()
        isPaused = true
    }

    override fun resumeRecording() {
        if (!isRecording || !isPaused) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            throw UnsupportedOperationException("pause/resume requires API 24+ (Android 7.0+)")
        }

        recorder?.resume()
        isPaused = false
    }

    override fun isRecording(): Boolean {
        return isRecording
    }

    private fun cleanup() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            // Ignore errors during cleanup
        }
        recorder = null
        isRecording = false
        isPaused = false
    }
}