// Phase 2.3 — RecordingScreen: shared composable UI (Android + iOS)

package org.example.project.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun RecordingScreen(viewModel: RecordingViewModel) {
    val uiState by viewModel.uiState.collectAsState()

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
                RecordingControlsSection(viewModel)
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
                    onRetry = { viewModel.retryTranscription(recording.id) }
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
}

/**
 * Recording control buttons (start/stop).
 */
@Composable
private fun RecordingControlsSection(viewModel: RecordingViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { viewModel.startRecording() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("🎤 Start Recording")
            }

            Button(
                onClick = { viewModel.stopRecording("recording_${getCurrentTimeMillis()}.mp4") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⏹️ Stop Recording")
            }
        }
    }
}

/**
 * Single recording item displaying:
 * - File name
 * - Transcription status (Pending, In Progress, Done, Error)
 * - Delete button
 * - Retry button (if error)
 * - View transcription button (if done)
 */
@Composable
private fun SavedRecordingItem(
    recording: RecordingUiItem,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                // File name
                Text(
                    text = recording.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Transcription status
                when (val state = recording.transcription) {
                    is TranscriptionUiState.Pending -> {
                        Text(
                            text = "⏳ Pending transcription",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    is TranscriptionUiState.InProgress -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .height(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Transcribing...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is TranscriptionUiState.Done -> {
                        Text(
                            text = "✅ Transcribed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    is TranscriptionUiState.Error -> {
                        Text(
                            text = "❌ Error: ${state.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Action buttons
            if (recording.transcription is TranscriptionUiState.Error) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(end = 4.dp)) {
                    Text("Retry")
                }
            }

            if (recording.transcription is TranscriptionUiState.Done) {
                TextButton(onClick = { /* Phase 5.4: Show transcription dialog */ }, modifier = Modifier.padding(end = 4.dp)) {
                    Text("View")
                }
            }

            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        }
    }
}
