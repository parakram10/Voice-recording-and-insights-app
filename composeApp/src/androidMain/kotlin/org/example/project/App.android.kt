package org.example.project

import android.app.Application
import org.example.project.di.appModule
import org.example.project.di.androidModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class VoiceApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@VoiceApp)
            modules(appModule, androidModule)
        }
    }
}
