package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.example.project.ui.RecordingScreen

/**
 * Android-specific app entry point with recording UI.
 * Displays RecordingScreen which injects RecordingViewModel from Koin.
 */
@Composable
fun AndroidApp() {
    MaterialTheme {
        RecordingScreen()
    }
}
