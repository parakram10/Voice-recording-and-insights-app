// Phase 1.5 — Common data layer registration
package org.example.project.di

import org.example.project.data.RecordingRepository
import org.example.project.data.RecordingRepositoryImpl
import org.koin.dsl.module

val appModule = module {
    // Data layer: RecordingRepositoryImpl depends on AppDatabase (provided by platform modules)
    single<RecordingRepository> { RecordingRepositoryImpl(get()) }
}
