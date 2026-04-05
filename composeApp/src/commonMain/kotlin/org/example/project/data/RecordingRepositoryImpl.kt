// Phase 1.3 — RecordingRepositoryImpl: SQLDelight-backed repository (shared for Android + iOS)

package org.example.project.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import org.example.project.util.getCurrentTimeMillis

/**
 * Implementation wrapping SQLDelight database.
 *
 * Converts database entities (Recording) to UI types (RecordingUiItem).
 * All database operations run on IO dispatcher to avoid blocking the main thread.
 */
class RecordingRepositoryImpl(private val database: AppDatabase) : RecordingRepository {
    private val queries = database.recordingQueries

    override suspend fun insertRecording(filePath: String, fileName: String): Long =
        withContext(Dispatchers.IO) {
            val timestamp = getCurrentTimeMillis()
            queries.insertRecording(filePath, fileName, timestamp)
            // Get the most recently inserted recording (by createdAt timestamp)
            // This works because we just inserted with current timestamp
            val allRecordings = queries.getAllRecordings().executeAsOne()
            // getAllRecordings returns in DESC order by createdAt, so first = most recent
            allRecordings.id
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
        // NOTE: Currently returns empty list (stub implementation).
        // Will be connected to actual SQLDelight Flow once database driver is wired:
        // return database.recordingQueries.getAllRecordings()
        //     .asFlow()
        //     .mapToList(Dispatchers.IO)
        //     .map { records -> records.map { it.toUiItem() } }
        return flowOf(emptyList())
    }

    override suspend fun getRecordingById(id: Long): RecordingUiItem? =
        withContext(Dispatchers.IO) {
            // NOTE: Currently returns null (stub implementation).
            // Will be connected to actual SQLDelight query once database driver is wired:
            // return database.recordingQueries.getRecordingById(id)
            //     .executeAsOneOrNull()?.toUiItem()
            null
        }

    override suspend fun deleteRecording(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteRecording(id)
    }

    /**
     * Convert SQLDelight Recording entity to UI-friendly RecordingUiItem.
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
