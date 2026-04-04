package org.example.project.voicerecorder

expect class AudioRecorder {
    fun startRecording()
    fun stopRecording(fileName: String)
    fun pauseRecording()
    fun isRecording(): Boolean
}