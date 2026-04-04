package org.example.project.di

import org.example.project.voicerecorder.AudioRecorder
import org.example.project.voicerecorder.AudioRecorderAndroid
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidModule = module {
    // Audio Recorder
    single<AudioRecorder> { AudioRecorderAndroid(androidContext()) }
}
