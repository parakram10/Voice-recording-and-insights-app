// Phase 4.2 — TranscriptionOrchestratorStub: Stub implementation for iOS (temporary until Phase 7)

package org.example.project.transcription

/**
 * Phase 4.2 — TranscriptionOrchestratorStub
 *
 * Temporary stub implementation for iOS transcription.
 *
 * This is a no-op implementation used until Phase 7 when WhisperKit integration is added.
 * In Phase 7, this will be replaced with TranscriptionHandlerIOS which uses WhisperKit.
 *
 * For now, recordings on iOS will be inserted into the database with PENDING status,
 * but will not automatically transcribe until Phase 7 is implemented.
 */
object TranscriptionOrchestratorStub : TranscriptionOrchestrator {
    override fun enqueue(recordingId: Long, filePath: String) {
        // Phase 4.2 (iOS stub): No-op until Phase 7 (WhisperKit integration)
        // Recordings will remain in PENDING status
    }

    override fun cancel(recordingId: Long) {
        // Phase 4.2 (iOS stub): No-op
    }

    override fun isTranscribing(recordingId: Long): Boolean = false

    override fun destroy() {
        // Phase 4.2 (iOS stub): No-op
    }
}
