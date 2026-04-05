package org.example.project

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.example.project.viewmodel.RecordingViewModel
import org.koin.compose.koinInject

/**
 * Android-specific app entry point with recording UI.
 * Injects RecordingViewModel from Koin and passes to common App composable.
 *
 * Handles RECORD_AUDIO permission requests before starting recording.
 */
@Composable
fun AndroidApp() {
    val viewModel: RecordingViewModel = koinInject()
    val context = LocalContext.current

    // Permission denial state
    val showPermissionDenied = remember { mutableStateOf(false) }

    // Get the activity to request permissions
    val activity = context as? BaseActivity

    App(
        viewModel = viewModel,
        onRequestRecordPermission = { callback ->
            if (activity != null) {
                activity.requestRecordAudioPermission(callback)
            } else {
                // Fallback: assume permission is granted if activity is not available
                callback(true)
            }
        },
        onPermissionDenied = { showPermissionDenied.value = true }
    )

    // Show permission denied dialog
    if (showPermissionDenied.value) {
        AlertDialog(
            onDismissRequest = { showPermissionDenied.value = false },
            title = { Text("Permission Required") },
            text = { Text("Microphone permission is required to record audio.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDenied.value = false }) {
                    Text("OK")
                }
            }
        )
    }
}
