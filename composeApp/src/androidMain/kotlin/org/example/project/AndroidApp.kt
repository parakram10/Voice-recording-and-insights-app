package org.example.project

import androidx.compose.runtime.Composable
import org.example.project.viewmodel.RecordingViewModel
import org.koin.compose.koinInject

/**
 * Android-specific app entry point with recording UI.
 * Injects RecordingViewModel from Koin and passes to common App composable.
 */
@Composable
fun AndroidApp() {
    val viewModel: RecordingViewModel = koinInject()
    App(viewModel)
}
