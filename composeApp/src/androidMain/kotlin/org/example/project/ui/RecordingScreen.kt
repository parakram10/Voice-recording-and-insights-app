package org.example.project.ui

import android.media.MediaPlayer
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.example.project.BaseActivity
import org.example.project.voicerecorder.AudioRecorder
import java.io.File

/**
 * Testing screen for audio recording functionality.
 * Provides UI buttons to test record, pause, resume, and stop operations.
 * Displays saved audio files with playback and delete options.
 *
 * @param audioRecorder The AudioRecorder instance from Koin DI
 */
@Composable
fun RecordingScreen(audioRecorder: AudioRecorder) {
    val context = LocalContext.current
    val isRecording = remember { mutableStateOf(false) }
    val isPaused = remember { mutableStateOf(false) }
    val recordingStatus = remember { mutableStateOf("Permission required: Request microphone access first") }
    val savedFilePath = remember { mutableStateOf("") }
    val permissionGranted = remember { mutableStateOf(false) }
    val savedFiles = remember { mutableStateOf<List<File>>(emptyList()) }
    val mediaPlayer = remember { mutableStateOf<MediaPlayer?>(null) }
    val isPlaying = remember { mutableStateOf(false) }
    val playingFile = remember { mutableStateOf<String?>(null) }

    // Load saved files when composition starts
    LaunchedEffect(Unit) {
        loadSavedFiles(context, savedFiles)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        item {
            Text(
                "Audio Recording Test",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Status Display
        item {
            Text(
                "Status: ${recordingStatus.value}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            )
        }

        // Recording indicators
        if (isRecording.value) {
            item {
                Text(
                    "🔴 Recording in progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (isPaused.value) {
            item {
                Text(
                    "⏸️ Recording paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        // Saved file display
        if (savedFilePath.value.isNotEmpty()) {
            item {
                Text(
                    "Saved: ${savedFilePath.value.substringAfterLast("/")}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
            }
        }

        // Permission Status
        if (!permissionGranted.value) {
            item {
                Text(
                    "⚠️ Microphone permission required",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        // Request Permission Button
        item {
            Button(
                onClick = {
                    if (context is BaseActivity) {
                        context.requestRecordAudioPermission { isGranted ->
                            permissionGranted.value = isGranted
                            if (isGranted) {
                                recordingStatus.value = "Permission granted! Ready to record"
                            } else {
                                recordingStatus.value = "Permission denied. Cannot record audio."
                            }
                        }
                    } else {
                        recordingStatus.value = "Error: Invalid context"
                    }
                },
                enabled = !permissionGranted.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text("Request Microphone Permission")
            }
        }

        // Start/Record Button
        item {
            Button(
                onClick = {
                    try {
                        audioRecorder.startRecording()
                        isRecording.value = true
                        isPaused.value = false
                        recordingStatus.value = "Recording started"
                    } catch (e: Exception) {
                        recordingStatus.value = "Error: ${e.message}"
                    }
                },
                enabled = !isRecording.value && permissionGranted.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text("Start Recording")
            }
        }

        // Pause Button (API 24+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            item {
                Button(
                    onClick = {
                        try {
                            audioRecorder.pauseRecording()
                            isPaused.value = true
                            recordingStatus.value = "Recording paused"
                        } catch (e: Exception) {
                            recordingStatus.value = "Error: ${e.message}"
                        }
                    },
                    enabled = isRecording.value && !isPaused.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text("Pause Recording")
                }
            }

            // Resume Button (API 24+ only)
            item {
                Button(
                    onClick = {
                        try {
                            audioRecorder.resumeRecording()
                            isPaused.value = false
                            recordingStatus.value = "Recording resumed"
                        } catch (e: Exception) {
                            recordingStatus.value = "Error: ${e.message}"
                        }
                    },
                    enabled = isRecording.value && isPaused.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text("Resume Recording")
                }
            }
        } else {
            item {
                Text(
                    "⚠️ Pause/Resume requires API 24+",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        // Stop Button
        item {
            Button(
                onClick = {
                    try {
                        val filePath = audioRecorder.stopRecording("")
                        isRecording.value = false
                        isPaused.value = false
                        recordingStatus.value = "Recording stopped"
                        savedFilePath.value = filePath ?: "Failed to save"
                        // Refresh file list
                        loadSavedFiles(context, savedFiles)
                    } catch (e: Exception) {
                        recordingStatus.value = "Error: ${e.message}"
                    }
                },
                enabled = isRecording.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text("Stop Recording")
            }
        }

        // Reset Button
        item {
            Button(
                onClick = {
                    isRecording.value = false
                    isPaused.value = false
                    savedFilePath.value = ""
                    if (permissionGranted.value) {
                        recordingStatus.value = "Ready to record"
                    } else {
                        recordingStatus.value = "Permission required: Request microphone access first"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text("Reset")
            }
        }

        // Saved Files Section
        item {
            Text(
                "📁 Saved Audio Files (${savedFiles.value.size})",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(top = 24.dp, bottom = 12.dp)
                    .fillMaxWidth()
            )
        }

        if (savedFiles.value.isEmpty()) {
            item {
                Text(
                    "No saved recordings yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(savedFiles.value) { file ->
                SavedFileItem(
                    file = file,
                    isPlaying = playingFile.value == file.absolutePath && isPlaying.value,
                    mediaPlayer = mediaPlayer.value,
                    onPlay = {
                        try {
                            // Stop any currently playing file
                            mediaPlayer.value?.apply {
                                if (isPlaying.value) {
                                    stop()
                                }
                                release()
                            }

                            // Create new player and play
                            val player = MediaPlayer()
                            player.setDataSource(file.absolutePath)
                            player.prepare()
                            player.start()
                            mediaPlayer.value = player
                            playingFile.value = file.absolutePath
                            isPlaying.value = true
                            recordingStatus.value = "Playing: ${file.name}"

                            // Set completion listener
                            player.setOnCompletionListener {
                                isPlaying.value = false
                                playingFile.value = null
                                recordingStatus.value = "Playback completed"
                            }
                        } catch (e: Exception) {
                            recordingStatus.value = "Error: Cannot play file - ${e.message}"
                        }
                    },
                    onStop = {
                        try {
                            mediaPlayer.value?.apply {
                                stop()
                                release()
                            }
                            mediaPlayer.value = null
                            playingFile.value = null
                            isPlaying.value = false
                            recordingStatus.value = "Playback stopped"
                        } catch (e: Exception) {
                        }
                    },
                    onDelete = {
                        try {
                            // Stop playback if playing this file
                            if (playingFile.value == file.absolutePath) {
                                mediaPlayer.value?.apply {
                                    stop()
                                    release()
                                }
                                mediaPlayer.value = null
                                playingFile.value = null
                                isPlaying.value = false
                            }

                            // Delete file
                            val deleted = file.delete()
                            if (deleted) {
                                loadSavedFiles(context, savedFiles)
                                recordingStatus.value = "File deleted: ${file.name}"
                            } else {
                                recordingStatus.value = "Failed to delete file"
                            }
                        } catch (e: Exception) {
                            recordingStatus.value = "Error: ${e.message}"
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SavedFileItem(
    file: File,
    isPlaying: Boolean,
    mediaPlayer: MediaPlayer?,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Size: ${formatFileSize(file.length())} | Modified: ${formatDate(file.lastModified())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPlaying) {
                    Text(
                        text = "▶️ Now playing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Play/Stop Button
            TextButton(
                onClick = if (isPlaying) onStop else onPlay,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(if (isPlaying) "⏹️ Stop" else "▶️ Play")
            }

            // Delete Button
            TextButton(onClick = onDelete) {
                Text("🗑️ Delete")
            }
        }
    }
}

private fun loadSavedFiles(context: android.content.Context, savedFiles: androidx.compose.runtime.MutableState<List<File>>) {
    try {
        val externalFilesDir = context.getExternalFilesDir(null)
        if (externalFilesDir != null && externalFilesDir.exists()) {
            val audioFiles = externalFilesDir.listFiles { file ->
                file.isFile && (file.extension == "mp4" || file.extension == "m4a")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            savedFiles.value = audioFiles
        } else {
        }
    } catch (e: Exception) {
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.US)
    return sdf.format(java.util.Date(timestamp))
}
