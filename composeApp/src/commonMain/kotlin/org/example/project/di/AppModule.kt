package org.example.project.di

import org.example.project.voicerecorder.AudioRecorder
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    // AudioRecorder interface - will be implemented by platform-specific modules
    // This is declared here but bound in platform-specific modules
}
