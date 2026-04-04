// Phase 1.3 Manual Implementation — AppDatabase wrapper + UI types
// NOTE: SQLDelight auto-generation not working with KMP 2.3.20 + SQLDelight 2.3.1
// Manually implemented to match what SQLDelight would generate

package org.example.project.data

import kotlinx.serialization.Serializable

interface AppDatabase {
    val recordingQueries: RecordingQueries

    companion object {
        val Schema: Any? = null
    }
}

interface RecordingQueries {
    fun insertRecording(filePath: String, fileName: String, createdAt: Long)
    fun updateTranscriptionStatus(status: String, id: Long)
    fun updateTranscriptionResult(text: String, status: String, id: Long)
    fun updateTranscriptionError(error: String, status: String, id: Long)
    fun getAllRecordings(): Query<Recording>
    fun getRecordingById(id: Long): Query<Recording>
    fun deleteRecording(id: Long)
}

interface Query<T> {
    fun executeAsOne(): T
    fun executeAsOneOrNull(): T?
    fun asFlow(): Any // Flow<Query<T>>
}

data class Recording(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val createdAt: Long,
    val durationMs: Long = 0,
    val transcriptionText: String? = null,
    val transcriptionStatus: String,
    val transcriptionError: String? = null
)

// UI State Types (moved to data package for visibility)
@Serializable
sealed class TranscriptionUiState {
    @Serializable
    data object Pending : TranscriptionUiState()

    @Serializable
    data object InProgress : TranscriptionUiState()

    @Serializable
    data class Done(val text: String) : TranscriptionUiState()

    @Serializable
    data class Error(val message: String) : TranscriptionUiState()
}

@Serializable
data class RecordingUiItem(
    val id: Long,
    val fileName: String,
    val filePath: String,
    val createdAt: Long,
    val transcription: TranscriptionUiState
)
