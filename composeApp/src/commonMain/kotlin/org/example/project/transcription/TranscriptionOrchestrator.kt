// Phase 4.2 — TranscriptionOrchestrator: platform-agnostic transcription interface (commonMain)

package org.example.project.transcription

/**
 * Phase 4.2 — TranscriptionOrchestrator
 *
 * Platform-agnostic interface for managing background transcription.
 *
 * Implemented by:
 * - Android (Phase 4.1): TranscriptionHandler (Whisper.cpp via JNI)
 * - iOS (Phase 7.4): TranscriptionHandlerIOS (WhisperKit)
 *
 * Allows ViewModel and UI to trigger transcription without knowing platform details.
 */
interface TranscriptionOrchestrator {
    /**
     * Phase 4.2 — Enqueue a recording for transcription
     *
     * Asynchronously:
     * 1. Decodes audio file to 16kHz PCM
     * 2. Runs transcription (Whisper.cpp or WhisperKit)
     * 3. Persists result to database
     *
     * Idempotent: calling multiple times with same ID won't start duplicate transcriptions.
     *
     * @param recordingId ID of the recording (from database)
     * @param filePath Absolute path to audio file (platform-specific format)
     */
    fun enqueue(recordingId: Long, filePath: String)

    /**
     * Phase 4.2 — Cancel a transcription in progress
     *
     * If transcription is running for this ID, cancels it.
     * Recording will be marked as ERROR with "Cancelled" message.
     *
     * @param recordingId ID of the recording
     */
    fun cancel(recordingId: Long)

    /**
     * Phase 4.2 — Check if transcription is running
     *
     * Useful for UI to show progress spinner only while actively transcribing.
     *
     * @param recordingId ID of the recording
     * @return True if transcription is currently running
     */
    fun isTranscribing(recordingId: Long): Boolean

    /**
     * Phase 4.2 — Destroy transcription handler
     *
     * Call when app exits or handler is no longer needed.
     * Cancels all pending transcriptions and releases resources.
     */
    fun destroy()
}
