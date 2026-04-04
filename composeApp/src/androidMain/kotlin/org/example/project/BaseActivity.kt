package org.example.project

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

open class BaseActivity : ComponentActivity() {
    private val permissionCallbacks = mutableMapOf<String, (Boolean) -> Unit>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val callback = permissionCallbacks.remove(currentPermission)
        callback?.invoke(isGranted)
    }

    private var currentPermission: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun requestPermission(permission: String, callback: (Boolean) -> Unit) {
        currentPermission = permission
        permissionCallbacks[permission] = callback
        permissionLauncher.launch(permission)
    }

    fun requestRecordAudioPermission(callback: (Boolean) -> Unit) {
        requestPermission(Manifest.permission.RECORD_AUDIO, callback)
    }

    fun createAudioRecorder(): org.example.project.voicerecorder.AudioRecorderAndroid {
        return org.example.project.voicerecorder.AudioRecorderAndroid(context = this)
    }
}
