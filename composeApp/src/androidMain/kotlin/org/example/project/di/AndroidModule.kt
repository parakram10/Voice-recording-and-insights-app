// Phase 1.5 — Android SQLDelight driver registration
package org.example.project.di

import org.example.project.data.AppDatabase
import org.example.project.data.InMemoryDatabase
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
    // NOTE: Using in-memory implementation with reactive Flow updates
    // TODO: Replace with AndroidSqliteDriver once SQLDelight code generation is working with KMP
    single<AppDatabase> {
        InMemoryDatabase()
    }

    // Phase 4.1 — Transcription components (Android-specific)
    single { AudioDecoder }
    single { ModelManager }
    single<TranscriptionOrchestrator> { TranscriptionHandler(androidContext(), get(), get(), get()) }
}
