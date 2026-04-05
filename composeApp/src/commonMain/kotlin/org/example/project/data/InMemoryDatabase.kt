// Phase 1.5 — In-memory stub database for development/testing
// Used by both Android and iOS until actual SQLDelight driver integration

package org.example.project.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow

/**
 * In-memory SQLDelight stub for testing/development
 * Stores recordings in a mutable list with auto-incrementing IDs
 * Emits Flow updates whenever data changes
 * Thread-safe for single-threaded use only (Kotlin/Multiplatform on main thread)
 */
class InMemoryDatabase : AppDatabase {
    private val recordings = mutableListOf<Recording>()
    private var nextId = 1L
    // Flow that emits whenever recordings change
    private val recordingsFlow = MutableStateFlow(emptyList<Recording>())
    private val queries = lazy { InMemoryRecordingQueries(recordings, recordingsFlow) { nextId++ } }

    override val recordingQueries: RecordingQueries
        get() = queries.value
}

class InMemoryRecordingQueries(
    private val recordings: MutableList<Recording>,
    private val recordingsFlow: MutableStateFlow<List<Recording>>,
    private val getNextId: () -> Long
) : RecordingQueries {

    private fun notifyChanged() {
        // Emit updated recordings whenever anything changes
        recordingsFlow.value = recordings.sortedByDescending { it.createdAt }
    }

    override fun insertRecording(filePath: String, fileName: String, createdAt: Long) {
        val id = getNextId()
        recordings.add(
            Recording(
                id = id,
                filePath = filePath,
                fileName = fileName,
                createdAt = createdAt,
                durationMs = 0,
                transcriptionText = null,
                transcriptionStatus = "PENDING",
                transcriptionError = null
            )
        )
        notifyChanged()
    }

    override fun updateTranscriptionStatus(status: String, id: Long) {
        val index = recordings.indexOfFirst { it.id == id }
        if (index >= 0) {
            val recording = recordings[index]
            recordings[index] = recording.copy(transcriptionStatus = status)
            notifyChanged()
        }
    }

    override fun updateTranscriptionResult(text: String, status: String, id: Long) {
        val index = recordings.indexOfFirst { it.id == id }
        if (index >= 0) {
            val recording = recordings[index]
            recordings[index] = recording.copy(
                transcriptionText = text,
                transcriptionStatus = status,
                transcriptionError = null
            )
            notifyChanged()
        }
    }

    override fun updateTranscriptionError(error: String, status: String, id: Long) {
        val index = recordings.indexOfFirst { it.id == id }
        if (index >= 0) {
            val recording = recordings[index]
            recordings[index] = recording.copy(
                transcriptionStatus = status,
                transcriptionError = error
            )
            notifyChanged()
        }
    }

    override fun getAllRecordings(): Query<Recording> {
        // Return newest first (sorted by createdAt DESC)
        val sorted = recordings.sortedByDescending { it.createdAt }
        return QueryWrapper(sorted, recordingsFlow)
    }

    override fun getRecordingById(id: Long): Query<Recording> {
        val recording = recordings.find { it.id == id }
        return if (recording != null) {
            QueryWrapper(listOf(recording), recordingsFlow)
        } else {
            EmptyQuery()
        }
    }

    override fun deleteRecording(id: Long) {
        recordings.removeAll { it.id == id }
        notifyChanged()
    }
}

private class QueryWrapper<T>(
    private val data: List<T>,
    private val flowSource: MutableStateFlow<List<T>>
) : Query<T> {
    override fun executeAsOne(): T = data.first()
    override fun executeAsOneOrNull(): T? = data.firstOrNull()
    override fun asFlow(): Any = flowSource as Any
}

private class EmptyQuery<T> : Query<T> {
    override fun executeAsOne(): T = throw NoSuchElementException("Query returned no results")
    override fun executeAsOneOrNull(): T? = null
    override fun asFlow(): Any = flow<T> { } // Empty flow
}
