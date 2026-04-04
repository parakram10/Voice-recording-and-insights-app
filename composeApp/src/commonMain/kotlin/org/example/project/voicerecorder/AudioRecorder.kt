package org.example.project.voicerecorder

interface AudioRecorder {
    fun startRecording()
    fun stopRecording(fileName: String)
    fun pauseRecording()
    fun isRecording(): Boolean
}