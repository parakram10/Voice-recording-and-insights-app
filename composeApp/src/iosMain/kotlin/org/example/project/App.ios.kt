package org.example.project

import org.example.project.di.appModule
import org.example.project.di.iosModule
import org.koin.core.context.startKoin

/**
 * Initialize Koin for iOS platform
 * Call this early in the app lifecycle (e.g., in SceneDelegate or app startup)
 */
fun initializeKoin() {
    startKoin {
        modules(appModule, iosModule)
    }
}
