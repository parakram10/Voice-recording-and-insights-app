// Phase 1.5 — Android SQLDelight driver registration
package org.example.project.di

import org.example.project.data.AppDatabase
import org.example.project.data.Query
import org.example.project.data.Recording
import org.example.project.data.RecordingQueries
import org.example.project.voicerecorder.AudioRecorder
import org.example.project.voicerecorder.AudioRecorderAndroid
import org.example.project.transcription.TranscriptionHandler
import org.example.project.transcription.TranscriptionOrchestrator
import org.example.project.transcription.AudioDecoder
import org.example.project.transcription.ModelManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidModule = module {
    // Audio Recorder
    single<AudioRecorder> { AudioRecorderAndroid(androidContext()) }

    // SQLDelight database for Android
    // NOTE: Using stub implementation pending actual SQLDelight driver integration
    single<AppDatabase> {
        object : AppDatabase {
            override val recordingQueries: RecordingQueries
                get() = object : RecordingQueries {
                    override fun insertRecording(filePath: String, fileName: String, createdAt: Long) {}
                    override fun updateTranscriptionStatus(status: String, id: Long) {}
                    override fun updateTranscriptionResult(text: String, status: String, id: Long) {}
                    override fun updateTranscriptionError(error: String, status: String, id: Long) {}
                    override fun getAllRecordings(): Query<Recording> = EmptyQuery()
                    override fun getRecordingById(id: Long): Query<Recording> = EmptyQuery()
                    override fun deleteRecording(id: Long) {}
                }
        }
    }

    // Phase 4.1 — Transcription components (Android-specific)
    single { AudioDecoder }
    single { ModelManager }
    single<TranscriptionOrchestrator> { TranscriptionHandler(androidContext(), get(), get(), get()) }
}

private class EmptyQuery<T> : Query<T> {
    override fun executeAsOne(): T = throw NoSuchElementException("Query returned no results")
    override fun executeAsOneOrNull(): T? = null
    override fun asFlow(): Any = throw NotImplementedError("Flow not yet implemented")
}
