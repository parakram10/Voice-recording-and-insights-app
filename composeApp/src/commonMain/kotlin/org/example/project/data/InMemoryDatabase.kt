// Phase 1.5 — In-memory stub database for development/testing
// Used by both Android and iOS until actual SQLDelight driver integration

package org.example.project.data

/**
 * In-memory SQLDelight stub for testing/development
 * Stores recordings in a mutable list with auto-incrementing IDs
 * Thread-safe for single-threaded use only (Kotlin/Multiplatform on main thread)
 */
class InMemoryDatabase : AppDatabase {
    private val recordings = mutableListOf<Recording>()
    private var nextId = 1L
    private val queries = lazy { InMemoryRecordingQueries(recordings) { nextId++ } }

    override val recordingQueries: RecordingQueries
        get() = queries.value
}

class InMemoryRecordingQueries(
    private val recordings: MutableList<Recording>,
    private val getNextId: () -> Long
) : RecordingQueries {

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
    }

    override fun updateTranscriptionStatus(status: String, id: Long) {
        val index = recordings.indexOfFirst { it.id == id }
        if (index >= 0) {
            val recording = recordings[index]
            recordings[index] = recording.copy(transcriptionStatus = status)
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
        }
    }

    override fun getAllRecordings(): Query<Recording> {
        // Return newest first (sorted by createdAt DESC)
        val sorted = recordings.sortedByDescending { it.createdAt }
        return QueryWrapper(sorted)
    }

    override fun getRecordingById(id: Long): Query<Recording> {
        val recording = recordings.find { it.id == id }
        return if (recording != null) {
            QueryWrapper(listOf(recording))
        } else {
            EmptyQuery()
        }
    }

    override fun deleteRecording(id: Long) {
        recordings.removeAll { it.id == id }
    }
}

private class QueryWrapper<T>(private val data: List<T>) : Query<T> {
    override fun executeAsOne(): T = data.first()
    override fun executeAsOneOrNull(): T? = data.firstOrNull()
    override fun asFlow(): Any = throw NotImplementedError("Flow not yet implemented")
}

private class EmptyQuery<T> : Query<T> {
    override fun executeAsOne(): T = throw NoSuchElementException("Query returned no results")
    override fun executeAsOneOrNull(): T? = null
    override fun asFlow(): Any = throw NotImplementedError("Flow not yet implemented")
}
