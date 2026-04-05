// Phase 1.3 — RecordingRepositoryImpl: Database-backed repository (shared for Android + iOS)

package org.example.project.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.example.project.util.getCurrentTimeMillis

/**
 * Implementation wrapping the database (currently in-memory with Flow support).
 *
 * Converts database entities (Recording) to UI types (RecordingUiItem).
 * All database operations run on IO dispatcher to avoid blocking the main thread.
 *
 * NOTE: Currently uses in-memory database with reactive Flow updates.
 * TODO: Replace with actual SQLDelight driver once code generation works with KMP.
 */
class RecordingRepositoryImpl(private val database: AppDatabase) : RecordingRepository {
    private val queries = database.recordingQueries

    override suspend fun insertRecording(filePath: String, fileName: String): Long =
        withContext(Dispatchers.IO) {
            val timestamp = getCurrentTimeMillis()
            queries.insertRecording(filePath, fileName, timestamp)
            // Get the most recently inserted recording ID
            queries.getAllRecordings().executeAsOne().id
        }

    override suspend fun markInProgress(id: Long) = withContext(Dispatchers.IO) {
        queries.updateTranscriptionStatus("IN_PROGRESS", id)
    }

    override suspend fun markDone(id: Long, text: String) = withContext(Dispatchers.IO) {
        queries.updateTranscriptionResult(text, "DONE", id)
    }

    override suspend fun markError(id: Long, error: String) = withContext(Dispatchers.IO) {
        queries.updateTranscriptionError(error, "ERROR", id)
    }

    override fun getAllRecordings(): Flow<List<RecordingUiItem>> {
        // Get flow from database and map to UI items
        @Suppress("UNCHECKED_CAST")
        val recordingsFlow = queries.getAllRecordings().asFlow() as Flow<List<Recording>>
        return recordingsFlow.map { records -> records.map { it.toUiItem() } }
    }

    override suspend fun getRecordingById(id: Long): RecordingUiItem? =
        withContext(Dispatchers.IO) {
            queries.getRecordingById(id).executeAsOneOrNull()?.toUiItem()
        }

    override suspend fun deleteRecording(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteRecording(id)
    }

    /**
     * Convert database Recording entity to UI-friendly RecordingUiItem.
     */
    private fun Recording.toUiItem(): RecordingUiItem = RecordingUiItem(
        id = id,
        fileName = fileName,
        filePath = filePath,
        createdAt = createdAt,
        transcription = when (transcriptionStatus) {
            "IN_PROGRESS" -> TranscriptionUiState.InProgress
            "DONE" -> TranscriptionUiState.Done(transcriptionText ?: "")
            "ERROR" -> TranscriptionUiState.Error(transcriptionError ?: "Unknown error")
            else -> TranscriptionUiState.Pending
        }
    )
}
