// Phase 1.5 — iOS SQLDelight driver registration
package org.example.project.di

import org.example.project.data.AppDatabase
import org.example.project.data.InMemoryDatabase
import org.example.project.voicerecorder.AudioRecorder
import org.example.project.voicerecorder.AudioRecorderIOS
import org.example.project.transcription.TranscriptionOrchestrator
import org.example.project.transcription.TranscriptionOrchestratorStub
import org.koin.dsl.module

val iosModule = module {
    // Audio Recorder
    single<AudioRecorder> { AudioRecorderIOS() }

    // SQLDelight database for iOS
    // NOTE: Using in-memory stub implementation (shared with Android) pending actual SQLDelight driver integration
    single<AppDatabase> {
        InMemoryDatabase()
    }

    // Phase 4.2 — Transcription orchestrator (stub until Phase 7)
    single<TranscriptionOrchestrator> { TranscriptionOrchestratorStub }
}
