package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.example.project.ui.RecordingScreen
import org.example.project.voicerecorder.AudioRecorder
import org.koin.compose.koinInject

/**
 * Android-specific app entry point with recording testing UI.
 * Injects AudioRecorder from Koin and displays RecordingScreen for testing.
 */
@Composable
fun AndroidApp() {
    MaterialTheme {
        val audioRecorder: AudioRecorder = koinInject()
        RecordingScreen(audioRecorder)
    }
}
