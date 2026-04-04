// Phase 1.1 — RecordingRepository: data access contract (Android + iOS via SQLDelight)
package org.example.project.data

import RecordingUiItem
import kotlinx.coroutines.flow.Flow

/**
 * Data access interface for recording persistence.
 *
 * Platform-agnostic contract:
 * - Android: implemented via SQLDelight + Room (Phase 1.3)
 * - iOS: implemented via SQLDelight + Native driver (Phase 1.5)
 *
 * All operations return Flow<List<RecordingUiItem>> (not raw DB entities).
 * This keeps Room/SQLDelight internal to data layer.
 */
interface RecordingRepository {

    /**
     * Insert a new recording with PENDING status.
     *
     * @param filePath Absolute path to the audio file (e.g., "/data/data/.../audio_20260405.mp4")
     * @param fileName Display name (e.g., "audio_20260405_143022_+0530.mp4")
     * @return Generated recording ID from database
     */
    suspend fun insertRecording(filePath: String, fileName: String): Long

    /**
     * Mark recording as IN_PROGRESS (transcription started).
     *
     * Called when TranscriptionHandler begins work.
     */
    suspend fun markInProgress(id: Long)

    /**
     * Mark recording as DONE with transcription result.
     *
     * Called when Whisper/WhisperKit completes successfully.
     *
     * @param id Recording ID
     * @param text Transcription output text
     */
    suspend fun markDone(id: Long, text: String)

    /**
     * Mark recording as ERROR with error message.
     *
     * Called when transcription fails (Whisper error, audio decode error, etc.).
     * User can then tap Retry button.
     *
     * @param id Recording ID
     * @param error Error message to display (e.g., "Model not found", "Audio decode failed")
     */
    suspend fun markError(id: Long, error: String)

    /**
     * Get all recordings in reverse chronological order (newest first).
     *
     * Returns a Flow that emits a new list whenever the database changes.
     * Used by ViewModel to reactively update UI.
     *
     * NOT a suspend function — Flow is lazy and cold.
     *
     * @return Flow<List<RecordingUiItem>> — emits whenever DB changes
     */
    fun getAllRecordings(): Flow<List<RecordingUiItem>>

    /**
     * Get a single recording by ID.
     *
     * @param id Recording ID
     * @return RecordingUiItem if found, null otherwise
     */
    suspend fun getRecordingById(id: Long): RecordingUiItem?

    /**
     * Delete a recording (and its metadata) from database.
     *
     * Note: Actual audio file deletion is handled by ViewModel/service layer.
     * This only removes the DB row.
     *
     * @param id Recording ID
     */
    suspend fun deleteRecording(id: Long)
}
