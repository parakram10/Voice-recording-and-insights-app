package org.example.project.voicerecorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

private const val TAG = "Hello"

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
        Log.d(TAG, "createRecorder: Starting recorder initialization")
        // Ensure any previous recorder is cleaned up
        cleanup()

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.d(TAG, "createRecorder: Creating MediaRecorder with context (API 31+)")
                MediaRecorder(context)
            } else {
                Log.d(TAG, "createRecorder: Creating MediaRecorder without context (API < 31)")
                MediaRecorder()
            }

            Log.d(TAG, "createRecorder: MediaRecorder created, configuring audio settings")
            recorder?.apply {
                // setAudioSource must be called first on a newly created MediaRecorder
                Log.d(TAG, "createRecorder: Setting audio source to MIC")
                setAudioSource(MediaRecorder.AudioSource.MIC)
                Log.d(TAG, "createRecorder: Setting output format to MPEG_4")
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                Log.d(TAG, "createRecorder: Setting audio encoder to AAC")
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                Log.d(TAG, "createRecorder: Setting sampling rate to 44100 Hz")
                setAudioSamplingRate(44100)
                Log.d(TAG, "createRecorder: Setting encoding bit rate to 128000 bps")
                setAudioEncodingBitRate(128000)
                Log.d(TAG, "createRecorder: Recorder configuration completed successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "createRecorder: Failed to initialize MediaRecorder", e)
            recorder?.release()
            recorder = null
            throw IllegalStateException("Failed to initialize MediaRecorder: ${e.message}", e)
        }
    }

    private fun getOutputDirectory(): File {
        val dir = context.getExternalFilesDir(null)
            ?: throw IllegalStateException("External files directory not available")
        dir.mkdirs()  // Safe to call multiple times, idempotent
        return dir
    }

    override fun startRecording() {
        Log.d(TAG, "startRecording: Called")
        if (isRecording) {
            Log.d(TAG, "startRecording: Already recording, returning")
            return
        }

        try {
            Log.d(TAG, "startRecording: Creating fresh recorder instance")
            // Always create a fresh recorder - MediaRecorder must be reinitialized for each session
            createRecorder()

            // Create output file with timestamp (includes timezone)
            // Example filename: audio_20260405_143022_+0530.mp4
            val timestamp = getCurrentTimestamp()
            Log.d(TAG, "startRecording: Generated timestamp: $timestamp")
            val outputDir = getOutputDirectory()
            val audioFile = File(outputDir, "audio_$timestamp.mp4")
            outputFile = audioFile
            Log.d(TAG, "startRecording: Output file path: ${audioFile.absolutePath}")

            recorder?.apply {
                Log.d(TAG, "startRecording: Setting output file")
                setOutputFile(audioFile.absolutePath)
                Log.d(TAG, "startRecording: Preparing recorder")
                prepare()
                Log.d(TAG, "startRecording: Starting recording")
                start()
            }

            isRecording = true
            isPaused = false
            Log.d(TAG, "startRecording: Recording started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording: Error occurred", e)
            isRecording = false
            isPaused = false
            cleanup()
            throw e
        }
    }

    override fun stopRecording(fileName: String): String? {
        Log.d(TAG, "stopRecording: Called with fileName='$fileName'")
        if (!isRecording) {
            Log.d(TAG, "stopRecording: Not recording, returning null")
            return null
        }

        try {
            // Stop and release recorder - state cleared after stop() succeeds
            Log.d(TAG, "stopRecording: Stopping recorder")
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            isPaused = false
            Log.d(TAG, "stopRecording: Recorder stopped and released")

            var finalFile = outputFile

            // Rename file if custom name provided
            if (fileName.isNotBlank() && outputFile != null) {
                Log.d(TAG, "stopRecording: Renaming file from ${outputFile?.name} to $fileName")
                val outputDir = outputFile?.parentFile
                    ?: throw IllegalStateException("Output directory not found")
                val newFile = File(outputDir, fileName)

                val renameSuccess = outputFile?.renameTo(newFile) ?: false
                if (!renameSuccess) {
                    throw IllegalStateException("Failed to rename file from ${outputFile?.name} to $fileName")
                }
                finalFile = newFile
                Log.d(TAG, "stopRecording: File renamed successfully")
            } else if (fileName.isBlank()) {
                Log.d(TAG, "stopRecording: No custom filename provided, using auto-generated name")
            }

            val filePath = finalFile?.absolutePath
            Log.d(TAG, "stopRecording: Recording saved to $filePath")
            return filePath
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording: Error occurred", e)
            // On failure, only release recorder without changing state
            // Caller can determine if recording is still in progress via isRecording()
            try {
                recorder?.release()
            } catch (ex: Exception) {
                Log.e(TAG, "stopRecording: Error during cleanup", ex)
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
        Log.d(TAG, "cleanup: Starting cleanup")
        try {
            if (recorder != null) {
                Log.d(TAG, "cleanup: Releasing recorder")
                recorder?.release()
            } else {
                Log.d(TAG, "cleanup: Recorder is already null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "cleanup: Error releasing recorder", e)
            // Ignore errors during cleanup
        }
        recorder = null
        isRecording = false
        isPaused = false
        Log.d(TAG, "cleanup: Cleanup completed")
    }
}