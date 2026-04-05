package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.example.project.ui.RecordingScreen
import org.example.project.viewmodel.RecordingViewModel

@Composable
fun App(
    viewModel: RecordingViewModel,
    onRequestRecordPermission: ((callback: (Boolean) -> Unit) -> Unit)? = null,
    onPermissionDenied: (() -> Unit)? = null
) {
    MaterialTheme {
        RecordingScreen(
            viewModel = viewModel,
            onRequestRecordPermission = onRequestRecordPermission,
            onPermissionDenied = onPermissionDenied
        )
    }
}