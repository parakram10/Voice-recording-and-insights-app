// Phase 2.2 — RecordingViewModel: shared state management (Android + iOS)

package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.data.RecordingRepository
import org.example.project.voicerecorder.AudioRecorder

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
    private val audioRecorder: AudioRecorder
) : ViewModel() {

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
     * 3. (Phase 4.2: Will auto-trigger transcriptionHandler.enqueue())
     */
    fun stopRecording(fileName: String) {
        viewModelScope.launch {
            val filePath = audioRecorder.stopRecording(fileName)
            if (filePath != null) {
                val recordingId = recordingRepository.insertRecording(filePath, fileName)
                // Phase 4.2: Will call transcriptionHandler.enqueue(recordingId)
                // For now, recording is in PENDING status and will be processed when
                // TranscriptionHandler is implemented in Phase 4
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
     * 1. Mark recording as PENDING
     * 2. Phase 4.2: Re-enqueue in TranscriptionHandler
     */
    fun retryTranscription(id: Long) {
        viewModelScope.launch {
            recordingRepository.markInProgress(id)
            // Phase 4.2: Will call transcriptionHandler.enqueue(id)
            // Once TranscriptionHandler is implemented, it will pick up
            // recordings marked as IN_PROGRESS on startup
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
}
