// Phase 1.5 — Common data layer registration
package org.example.project.di

import org.example.project.data.RecordingRepository
import org.example.project.data.RecordingRepositoryImpl
import org.example.project.transcription.TranscriptionOrchestrator
import org.example.project.viewmodel.RecordingViewModel
import org.koin.dsl.module
import org.koin.androidx.viewmodel.dsl.viewModel

val appModule = module {
    // Data layer: RecordingRepositoryImpl depends on AppDatabase (provided by platform modules)
    single<RecordingRepository> { RecordingRepositoryImpl(get()) }

    // Phase 4.2 — TranscriptionOrchestrator is provided by platform modules (Android/iOS)
    // Declared here as interface so ViewModel can depend on it
    // Actual registration (e.g., binding to TranscriptionHandler) happens in AndroidModule/IosModule

    // Phase 4.2 — RecordingViewModel with TranscriptionOrchestrator injection
    viewModel { RecordingViewModel(get(), get(), get()) }
}
