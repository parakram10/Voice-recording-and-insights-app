package org.example.project.voicerecorder

/**
 * Cross-platform audio recording interface.
 *
 * Provides a unified API for recording audio on Android and iOS.
 * Each platform implements this interface according to its capabilities and constraints.
 *
 * ## Implementations
 * - [AudioRecorderAndroid] - Android implementation using MediaRecorder
 * - [AudioRecorderIOS] - iOS implementation using AVAudioRecorder
 */
interface AudioRecorder {
    /**
     * Starts audio recording from the device microphone.
     *
     * If recording is already active, this method does nothing.
     *
     * @throws IllegalStateException if recording fails to start (e.g., permission denied, device error)
     * @throws Exception for platform-specific errors
     *
     * @see [isRecording]
     * @see [stopRecording]
     */
    fun startRecording()

    /**
     * Stops recording and saves the audio file.
     *
     * If not currently recording, returns null without error.
     *
     * @param fileName Custom filename for the recording (without directory path).
     *                 If empty/blank, uses auto-generated name with timestamp.
     *                 Example: "interview.mp4" or "my_voice_note"
     *
     * @return Absolute file path of the saved recording, or null if not recording or save failed
     *
     * @throws IllegalStateException if file rename or directory operations fail
     * @throws Exception for platform-specific errors
     *
     * @see [startRecording]
     * @see [isRecording]
     */
    fun stopRecording(fileName: String): String?

    /**
     * Pauses the current recording (if supported by the platform).
     *
     * If not recording or already paused, this method does nothing.
     * Use [resumeRecording] to continue recording.
     *
     * Android: Requires API 24+ (Android 7.0+)
     * iOS: Requires iOS implementation
     *
     * @throws UnsupportedOperationException if platform doesn't support pause
     * @throws Exception for platform-specific errors
     *
     * @see [resumeRecording]
     * @see [isRecording]
     */
    fun pauseRecording()

    /**
     * Resumes a paused recording.
     *
     * If not recording or not paused, this method does nothing.
     *
     * Android: Requires API 24+ (Android 7.0+)
     * iOS: Requires iOS implementation
     *
     * @throws UnsupportedOperationException if platform doesn't support resume
     * @throws Exception for platform-specific errors
     *
     * @see [pauseRecording]
     * @see [isRecording]
     */
    fun resumeRecording()

    /**
     * Checks if audio recording is currently active.
     *
     * Returns true if [startRecording] was called and [stopRecording] has not been called yet.
     * Paused recordings still return true.
     *
     * @return true if recording is active, false otherwise
     *
     * @see [startRecording]
     * @see [stopRecording]
     * @see [pauseRecording]
     */
    fun isRecording(): Boolean
}