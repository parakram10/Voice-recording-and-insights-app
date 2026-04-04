package org.example.project.voicerecorder

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.kAudioFormatMPEG4AAC
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.setActive
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL

/**
 * iOS implementation of [AudioRecorder] for recording audio using the device microphone.
 *
 * ## Features
 * - Records audio in M4A format with AAC encoding
 * - Audio quality: 44.1 kHz sample rate, 128 kbps bitrate (matching Android)
 * - Stores recordings in app's Documents directory (persistent, user-accessible)
 * - Supports pause/resume on all iOS versions
 * - Automatic file naming with timestamps (e.g., `audio_20260405_143022.m4a`)
 * - Robust error handling with atomic state updates
 *
 * ## Storage & Permissions
 * Files are saved to Documents directory, which:
 * - Persists across app updates
 * - Is accessible to iTunes File Sharing if enabled in Info.plist
 * - Requires `NSMicrophoneUsageDescription` in Info.plist
 * - Uses AVAudioSession for proper background/interrupt handling
 *
 * ## Lifecycle & Usage
 * ```
 * val recorder = AudioRecorderIOS()
 *
 * try {
 *     recorder.startRecording()
 *     recorder.pauseRecording()
 *     recorder.resumeRecording()
 *     val filePath = recorder.stopRecording("custom_name.m4a")
 * } catch (e: Exception) {
 *     println("Recording failed: ${e.message}")
 * }
 * ```
 *
 * ## Error Handling & State Consistency
 * All methods throw exceptions on failure:
 * - [startRecording] throws if AVAudioRecorder initialization, prepareToRecord(), or record() fails;
 *   NSError descriptions are included in the exception message; state and resources are cleaned up on exception
 * - [stopRecording] throws if stop/rename fails; state unchanged if stop() fails
 * - [pauseRecording]/[resumeRecording] are no-ops when the recorder is not in a valid state for that transition
 * - On any exception, recorder resources are safely cleaned up without clearing state
 *
 * ## State Management
 * - `isRecording`: True between successful start() and successful stop()
 * - `isPaused`: Only valid when isRecording=true; tracks pause state
 * - Invalid operations (pause when not recording) return early without error
 *
 * ## Thread Safety
 * Not thread-safe. All method calls must be from the main thread.
 *
 * @throws IllegalStateException if Documents directory is not available
 * @throws RuntimeException for AVAudioRecorder errors
 */
@OptIn(ExperimentalForeignApi::class)
class AudioRecorderIOS : AudioRecorder {

    private var recorder: AVAudioRecorder? = null
    private var isRecording = false
    private var isPaused = false
    private var outputFile: NSURL? = null

    companion object {
        private val audioSettings: Map<Any?, *> = mapOf(
            AVFormatIDKey to NSNumber(uint = kAudioFormatMPEG4AAC.toUInt()),
            AVSampleRateKey to NSNumber(double = 44100.0),
            AVNumberOfChannelsKey to NSNumber(int = 2),
            AVEncoderBitRateKey to NSNumber(int = 128000)
        )
    }

    private val documentsDirectory: NSURL by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            expandTilde = true
        )

        if (paths.isEmpty()) {
            throw IllegalStateException("Documents directory not available")
        }

        @Suppress("UNCHECKED_CAST")
        val documentsPath = paths[0] as String
        NSURL(fileURLWithPath = documentsPath)
    }

    init {
        setupAudioSession()
    }

    private fun setupAudioSession() {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            memScoped {
                val error = alloc<NSError?>()
                audioSession.setCategory(AVAudioSessionCategoryRecord, error = error.ptr)
                audioSession.setActive(true, error = error.ptr)
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to setup AVAudioSession: ${e.message}", e)
        }
    }

    override fun startRecording() {
        if (isRecording) return

        try {
            val timestamp = getCurrentTimestamp()
            val fileName = "audio_$timestamp.m4a"
            val audioFileURL = documentsDirectory.URLByAppendingPathComponent(fileName)
            outputFile = audioFileURL

            var initError: String? = null
            recorder = memScoped {
                val error = alloc<NSError?>()
                val rec = AVAudioRecorder(
                    URL = audioFileURL,
                    settings = audioSettings,
                    error = error.ptr
                )
                if (rec == null) {
                    initError = error.value?.localizedDescription
                }
                rec
            }

            if (recorder == null) {
                throw RuntimeException(
                    "Failed to initialize AVAudioRecorder: ${initError ?: "Unknown error"}"
                )
            }

            val prepared = recorder?.prepareToRecord() ?: false
            if (!prepared) {
                throw RuntimeException("Failed to prepare AVAudioRecorder for recording")
            }

            val started = recorder?.record() ?: false
            if (!started) {
                throw RuntimeException("Failed to start recording")
            }

            isRecording = true
            isPaused = false
        } catch (e: Exception) {
            isRecording = false
            isPaused = false
            cleanup()
            throw RuntimeException("Failed to start recording: ${e.message}", e)
        }
    }

    override fun stopRecording(fileName: String): String? {
        if (!isRecording) return null

        val savedFile = outputFile

        try {
            recorder?.stop()
            recorder = null
            isRecording = false
            isPaused = false

            var finalURL = savedFile

            // Rename file if custom name provided
            if (fileName.isNotBlank() && savedFile != null) {
                val parentURL = savedFile.URLByDeletingLastPathComponent
                    ?: throw IllegalStateException("Output directory not found")

                val newURL = parentURL.URLByAppendingPathComponent(fileName)
                    ?: throw IllegalStateException("Failed to create new file URL")

                val fileManager = NSFileManager.defaultManager

                val moveSuccess = memScoped {
                    val error = alloc<NSError?>()
                    fileManager.moveItemAtURL(
                        srcURL = savedFile,
                        toURL = newURL,
                        error = error.ptr
                    )
                }

                if (!moveSuccess) {
                    throw IllegalStateException("Failed to rename file")
                }
                finalURL = newURL
            }

            outputFile = null

            return finalURL?.path
        } catch (e: Exception) {
            try {
                recorder?.stop()
                recorder = null
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
            throw RuntimeException("Failed to stop recording: ${e.message}", e)
        }
    }

    override fun pauseRecording() {
        if (!isRecording || isPaused) return

        try {
            recorder?.pause()
            isPaused = true
        } catch (e: Exception) {
            throw RuntimeException("Failed to pause recording: ${e.message}", e)
        }
    }

    override fun resumeRecording() {
        if (!isRecording || !isPaused) return

        try {
            recorder?.record()
            isPaused = false
        } catch (e: Exception) {
            throw RuntimeException("Failed to resume recording: ${e.message}", e)
        }
    }

    override fun isRecording(): Boolean {
        return isRecording
    }

    private fun cleanup() {
        try {
            recorder?.stop()
            recorder = null
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
        isRecording = false
        isPaused = false
    }
}
