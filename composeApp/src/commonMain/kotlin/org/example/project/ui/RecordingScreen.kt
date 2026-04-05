// Phase 2.3 — RecordingScreen: shared composable UI (Android + iOS)

package org.example.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.data.RecordingUiItem
import org.example.project.data.TranscriptionUiState
import org.example.project.util.getCurrentTimeMillis
import org.example.project.viewmodel.RecordingViewModel

/**
 * Shared recording screen for RecordingScreen.
 *
 * Displays:
 * - Start/Stop recording buttons
 * - List of saved recordings from database
 * - Recording status (PENDING, IN_PROGRESS, DONE, ERROR)
 *
 * Takes RecordingViewModel as parameter (injected by platform-specific entry points).
 * Platform-agnostic Compose code — works on both Android and iOS.
 */
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    onRequestRecordPermission: ((callback: (Boolean) -> Unit) -> Unit)? = null,
    onPermissionDenied: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    // Phase 5.4: State for selected transcription to show in dialog
    var selectedTranscriptionItem by remember { mutableStateOf<RecordingUiItem?>(null) }

    // Recording state indicator
    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Title
        item {
            Text(
                "Voice Recording & Transcription",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Recording Controls Section
        item {
            Column {
                RecordingControlsSection(
                    viewModel = viewModel,
                    isRecording = isRecording,
                    isPaused = isPaused,
                    onRecordingStateChange = { recording, paused ->
                        isRecording = recording
                        isPaused = paused
                    },
                    onRequestRecordPermission = onRequestRecordPermission,
                    onPermissionDenied = onPermissionDenied
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Saved Recordings Section
        item {
            Text(
                "Saved Recordings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // List of recordings from database
        if (uiState.recordings.isNotEmpty()) {
            items(uiState.recordings) { recording: RecordingUiItem ->
                SavedRecordingItem(
                    recording = recording,
                    onDelete = { viewModel.deleteRecording(recording.id) },
                    onRetry = { viewModel.retryTranscription(recording.id) },
                    onViewTranscription = {
                        // Phase 5.4: Show transcription dialog with selected recording
                        selectedTranscriptionItem = recording
                    }
                )
            }
        } else {
            item {
                Text(
                    "No recordings yet. Start recording to see them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    // Phase 5.4: Show transcription dialog when a recording is selected
    if (selectedTranscriptionItem != null) {
        val item = selectedTranscriptionItem!!
        if (item.transcription is TranscriptionUiState.Done) {
            TranscriptionDialog(
                text = item.transcription.text,
                onDismiss = { selectedTranscriptionItem = null }
            )
        }
    }
}

/**
 * Recording control buttons (start/stop/pause/resume).
 * Shows recording indicator and pause/resume buttons when recording is active.
 * Requests RECORD_AUDIO permission before starting recording on Android.
 */
@Composable
private fun RecordingControlsSection(
    viewModel: RecordingViewModel,
    isRecording: Boolean,
    isPaused: Boolean,
    onRecordingStateChange: (isRecording: Boolean, isPaused: Boolean) -> Unit,
    onRequestRecordPermission: ((callback: (Boolean) -> Unit) -> Unit)? = null,
    onPermissionDenied: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recording status indicator
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Recording indicator (red dot)
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPaused) "⏸ RECORDING PAUSED" else "● RECORDING",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (isRecording) {
                // Show pause/resume and stop buttons during recording
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isPaused) {
                                viewModel.resumeRecording()
                                onRecordingStateChange(true, false)
                            } else {
                                viewModel.pauseRecording()
                                onRecordingStateChange(true, true)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isPaused) "▶ Resume" else "⏸ Pause")
                    }

                    Button(
                        onClick = {
                            viewModel.stopRecording("recording_${getCurrentTimeMillis()}.mp4")
                            onRecordingStateChange(false, false)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⏹️ Stop")
                    }
                }
            } else {
                // Show start button when not recording
                Button(
                    onClick = {
                        if (onRequestRecordPermission != null) {
                            // Android: request permission before recording
                            onRequestRecordPermission { isGranted ->
                                if (isGranted) {
                                    viewModel.startRecording()
                                    onRecordingStateChange(true, false)
                                } else {
                                    onPermissionDenied?.invoke()
                                }
                            }
                        } else {
                            // iOS or other platforms without permission system
                            viewModel.startRecording()
                            onRecordingStateChange(true, false)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎤 Start Recording")
                }
            }
        }
    }
}

/**
 * Phase 5.2 — Single recording item displaying:
 * - File name
 * - Transcription status (via TranscriptionStatusRow component)
 * - Delete button
 *
 * @param recording The recording to display
 * @param onDelete Callback when Delete button is clicked
 * @param onRetry Callback when Retry button is clicked (Error state)
 * @param onViewTranscription Callback when View Transcription button is clicked (Done state)
 */
@Composable
private fun SavedRecordingItem(
    recording: RecordingUiItem,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onViewTranscription: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header row: file name + delete button
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // File name
                Text(
                    text = recording.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )

                // Delete button
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }

            // Phase 5.2: Transcription status component
            TranscriptionStatusRow(
                transcriptionState = recording.transcription,
                onRetry = onRetry,
                onViewTranscription = onViewTranscription,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
