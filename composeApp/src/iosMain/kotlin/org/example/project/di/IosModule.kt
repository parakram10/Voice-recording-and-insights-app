package org.example.project.di

import org.example.project.voicerecorder.AudioRecorder
import org.example.project.voicerecorder.AudioRecorderIOS
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val iosModule = module {
    // Audio Recorder
    single<AudioRecorder> { AudioRecorderIOS() }
}
