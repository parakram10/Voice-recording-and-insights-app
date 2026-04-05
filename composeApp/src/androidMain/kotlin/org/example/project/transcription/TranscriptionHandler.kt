// Phase 4.1 — TranscriptionHandler: Manage background transcription lifecycle (Android only)

package org.example.project.transcription

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.example.project.data.RecordingRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 4.1 — TranscriptionHandler
 *
 * Orchestrates the entire transcription lifecycle:
 * 1. Decode audio file (MP4 → 16kHz PCM)
 * 2. Load Whisper model
 * 3. Run transcription on audio samples
 * 4. Persist result or error to database
 *
 * Manages a background coroutine scope that survives ViewModel destruction.
 * Jobs are tracked by recording ID to prevent duplicate transcriptions.
 *
 * **Thread-safety**: Uses ConcurrentHashMap for thread-safe job tracking.
 * **Lifecycle**: Must call [destroy] when app exits to cancel all pending jobs.
 * **Error handling**: Marks recording as ERROR with user-facing message on failure.
 */
class TranscriptionHandler(
    private val context: Context,
    private val repository: RecordingRepository,
    private val audioDecoder: AudioDecoder,
    private val modelManager: ModelManager
) : TranscriptionOrchestrator {
    // Own scope: SupervisorJob ensures one failure doesn't cancel other transcriptions
    // IO dispatcher: suitable for file I/O (audio decode, model load) and repo operations
    // Note: Whisper.cpp may spawn its own threads, but we decode/persist on IO thread
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Track active transcription jobs by recording ID
    // Prevents duplicate transcriptions if enqueue is called multiple times
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /**
     * Phase 4.1 — Enqueue a recording for transcription
     *
     * Workflow:
     * 1. Check if transcription already running (idempotent)
     * 2. Mark recording as IN_PROGRESS
     * 3. Decode audio file to 16kHz mono PCM
     * 4. Load Whisper model
     * 5. Transcribe audio samples
     * 6. Persist transcription text
     *
     * On error: Mark recording as ERROR with message for user retry
     * On cancellation: Mark as ERROR with "Cancelled" message
     * Finally: Remove job from activeJobs to allow future transcriptions
     *
     * **Idempotent**: If transcription already running for this recordingId, returns immediately.
     * This prevents race conditions if stopRecording() triggers transcription twice.
     *
     * @param recordingId Recording ID from database
     * @param filePath Absolute path to audio file (MP4 format, 44.1kHz stereo)
     */
    override fun enqueue(recordingId: Long, filePath: String) {
        // If transcription already running for this recordingId, return early (idempotent)
        if (activeJobs.containsKey(recordingId)) {
            return
        }

        // Launch background coroutine in custom scope
        val job = scope.launch {
            try {
                // Step 1: Mark recording as IN_PROGRESS
                repository.markInProgress(recordingId)

                // Step 2: Decode audio file to 16kHz mono PCM float array
                val audioSamples = withContext(Dispatchers.Default) {
                    audioDecoder.decodeToFloatArray(filePath)
                }

                // Step 3: Get path to Whisper model file
                val modelPath = modelManager.getModelPath(context)

                // Step 4: Initialize Whisper context from model
                val whisperContext = WhisperContext.initFromFile(modelPath)

                // Step 5: Transcribe audio samples (may use Whisper's internal threading)
                val text = try {
                    whisperContext.transcribe(audioSamples)
                } finally {
                    // Always free native resources
                    whisperContext.close()
                }

                // Step 6: Persist transcription result to database
                repository.markDone(recordingId, text)

            } catch (e: CancellationException) {
                // Job was cancelled (e.g., user tapped cancel button or app was backgrounded)
                // Mark recording as ERROR with "Cancelled" message
                repository.markError(recordingId, "Cancelled")
                // Must rethrow CancellationException per Kotlin coroutine spec
                throw e

            } catch (e: Exception) {
                // Any other exception: decode error, model not found, whisper failed, etc.
                // Mark recording as ERROR with user-facing error message
                val errorMessage = e.message ?: "Unknown transcription error"
                repository.markError(recordingId, errorMessage)
                // Don't rethrow — job ends gracefully; will be logged if needed
            } finally {
                // Cleanup: remove job from activeJobs to allow future transcriptions
                // This runs regardless of success/failure/cancellation
                activeJobs.remove(recordingId)
            }
        }

        // Store job in map for tracking and potential cancellation
        activeJobs[recordingId] = job
    }

    /**
     * Phase 4.1 — Cancel a transcription in progress
     *
     * If transcription is running, cancels the coroutine and removes it from tracking.
     * Recording will be marked as ERROR with "Cancelled" message.
     *
     * @param recordingId Recording ID
     */
    override fun cancel(recordingId: Long) {
        activeJobs[recordingId]?.cancel()
        activeJobs.remove(recordingId)
    }

    /**
     * Phase 4.1 — Destroy handler and cancel all pending transcriptions
     *
     * Call this when app exits or handler is no longer needed.
     * Ensures all background coroutines are cancelled and native resources freed.
     *
     * After calling [destroy], this handler should not be reused.
     */
    override fun destroy() {
        scope.cancel()
    }

    /**
     * Phase 4.1 — Check if transcription is running for a recording
     *
     * Useful for UI to prevent user from starting duplicate transcriptions.
     *
     * @param recordingId Recording ID
     * @return True if transcription is currently running
     */
    override fun isTranscribing(recordingId: Long): Boolean = activeJobs.containsKey(recordingId)
}
