// Phase 2.1 — RecordingUiState: shared UI state data classes (Android + iOS)

import kotlinx.serialization.Serializable

/**
 * Transcription status for a single recording.
 * Represents the lifecycle of transcription from pending to completion or error.
 */
@Serializable
sealed class TranscriptionUiState {
    /** Transcription queued, waiting to start */
    @Serializable
    data object Pending : TranscriptionUiState()

    /** Transcription actively running */
    @Serializable
    data object InProgress : TranscriptionUiState()

    /** Transcription completed with text result */
    @Serializable
    data class Done(val text: String) : TranscriptionUiState()

    /** Transcription failed with error message; retry available */
    @Serializable
    data class Error(val message: String) : TranscriptionUiState()
}

/**
 * UI representation of a single recording.
 * Derived from database entity, suitable for display in RecordingScreen.
 */
@Serializable
data class RecordingUiItem(
    val id: Long,                           // Database primary key
    val fileName: String,                   // Display name (e.g., "audio_20260405_143022_+0530.mp4")
    val filePath: String,                   // Absolute file path for deletion
    val createdAt: Long,                    // Epoch millis (for sorting)
    val transcription: TranscriptionUiState // Transcription status (Pending, InProgress, Done, Error)
)

/**
 * Root UI state for RecordingScreen.
 * Combines the list of recordings and UI interaction state.
 */
@Serializable
data class RecordingScreenUiState(
    val recordings: List<RecordingUiItem> = emptyList(),           // All recordings (newest first)
    val isRecording: Boolean = false,                               // Mic is actively recording
    val selectedTranscriptionItem: RecordingUiItem? = null,         // Recording to show transcription for
    val errorMessage: String? = null,                               // Top-level error (e.g., permission denied)
    val isLoading: Boolean = false                                  // Loading initial data from DB
)
