package org.example.project

import androidx.compose.ui.window.ComposeUIViewController
import org.example.project.viewmodel.RecordingViewModel
import org.koin.compose.koinInject

fun MainViewController() = ComposeUIViewController {
    // Initialize Koin for iOS
    initializeKoin()

    // Inject ViewModel and render app (no permission handling needed for iOS)
    val viewModel: RecordingViewModel = koinInject()
    App(viewModel)
}