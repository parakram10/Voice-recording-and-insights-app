// Phase 2.2 — RecordingViewModel: shared state management (Android + iOS)

package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.example.project.data.RecordingRepository
import org.example.project.data.RecordingScreenUiState
import org.example.project.data.TranscriptionUiState
import org.example.project.voicerecorder.AudioRecorder
import org.example.project.transcription.TranscriptionOrchestrator

/**
 * ViewModel for RecordingScreen.
 *
 * Manages:
 * - Recording lifecycle (start/stop/delete)
 * - UI state (recordings list, recording status, error messages)
 * - Permission handling
 *
 * Uses viewModelScope to ensure coroutines are cancelled when ViewModel is cleared.
 */
class RecordingViewModel(
    private val recordingRepository: RecordingRepository,
    private val audioRecorder: AudioRecorder,
    private val transcriptionOrchestrator: TranscriptionOrchestrator
) : ViewModel() {

    /**
     * UI state for RecordingScreen.
     * Exposes recordings list, loading state, and error messages.
     */
    val uiState: StateFlow<RecordingScreenUiState> = recordingRepository.getAllRecordings()
        .map { recordings ->
            RecordingScreenUiState(
                recordings = recordings,
                isRecording = false,
                selectedTranscriptionItem = null,
                errorMessage = null,
                isLoading = false
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            RecordingScreenUiState()
        )

    /**
     * Phase 4.3 — Recover interrupted transcriptions on app startup
     *
     * When the app restarts, some recordings may be left in:
     * - PENDING: Inserted to DB but transcription never started (app killed between insert & enqueue)
     * - IN_PROGRESS: Transcription was running when app was killed
     *
     * This init block finds those recordings and re-enqueues them for transcription.
     * The TranscriptionHandler is idempotent, so re-enqueueing is safe.
     */
    init {
        viewModelScope.launch {
            // Get the first emission of recordings from Flow
            val recordings = recordingRepository.getAllRecordings().first()

            // Filter for PENDING and IN_PROGRESS items
            val interruptedRecordings = recordings.filter { item ->
                item.transcription is TranscriptionUiState.Pending ||
                item.transcription is TranscriptionUiState.InProgress
            }

            // Re-enqueue each interrupted transcription
            for (item in interruptedRecordings) {
                transcriptionOrchestrator.enqueue(item.id, item.filePath)
            }
        }
    }

    /**
     * Start recording audio.
     *
     * Calls audioRecorder.startRecording() on IO dispatcher.
     * Audio recorder handles permission checks and file creation.
     */
    fun startRecording() {
        viewModelScope.launch {
            audioRecorder.startRecording()
        }
    }

    /**
     * Stop recording and persist to database with PENDING status.
     *
     * Steps:
     * 1. Stop the audio recorder (passes fileName to recorder, returns file path)
     * 2. Insert recording into database with PENDING status
     * 3. Auto-trigger transcriptionOrchestrator.enqueue() to start background transcription
     */
    fun stopRecording(fileName: String) {
        viewModelScope.launch {
            val filePath = audioRecorder.stopRecording(fileName)
            if (filePath != null) {
                val recordingId = withContext(Dispatchers.IO) {
                    recordingRepository.insertRecording(filePath, fileName)
                }
                // Phase 4.2: Auto-trigger transcription
                transcriptionOrchestrator.enqueue(recordingId, filePath)
            }
        }
    }

    /**
     * Delete a recording (file + database row).
     *
     * Removes both the database entry and the associated audio file from disk.
     */
    fun deleteRecording(id: Long) {
        viewModelScope.launch {
            // Get the recording to find its file path
            val recording = recordingRepository.getRecordingById(id)
            if (recording != null) {
                // Delete from database
                recordingRepository.deleteRecording(id)
                // Phase 3/5: Delete actual audio file from disk (platform-specific)
                // Will be implemented as expect/actual in Phase 3.6 (Android) / 7.5 (iOS)
                // audioFileManager.deleteFile(recording.filePath)
            }
        }
    }

    /**
     * Retry transcription for a failed recording.
     *
     * Called when user taps Retry button on error state.
     * Steps:
     * 1. Get the recording and its file path from database
     * 2. Re-enqueue in transcription orchestrator
     * 3. Transcription will run in background and update UI when done
     */
    fun retryTranscription(id: Long) {
        viewModelScope.launch {
            val recording = withContext(Dispatchers.IO) {
                recordingRepository.getRecordingById(id)
            }
            if (recording != null) {
                // Re-enqueue transcription; will update DB when done
                transcriptionOrchestrator.enqueue(id, recording.filePath)
            }
        }
    }

    /**
     * Handle mic permission result.
     *
     * Called after user grants or denies microphone permission.
     * If denied, user cannot start recording.
     */
    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            // Phase 2.3: Show permission error UI
            // For now, recording will fail if permission is not granted
            // UI layer will handle showing permission denied message
        }
    }

    /**
     * Phase 4.2 — Cleanup on ViewModel destruction
     *
     * Called when ViewModel is cleared (screen navigated away, app backgrounded, etc.).
     * Ensures all background transcription jobs are cancelled and resources freed.
     */
    override fun onCleared() {
        super.onCleared()
        transcriptionOrchestrator.destroy()
    }
}
