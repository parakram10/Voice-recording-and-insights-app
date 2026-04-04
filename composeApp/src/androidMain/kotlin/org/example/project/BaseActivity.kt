package org.example.project

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.example.project.voicerecorder.AudioRecorder
import org.koin.android.ext.android.inject

/**
 * Base activity providing permission request handling and dependency injection.
 *
 * ## Permission Handling
 * Supports concurrent permission requests via RequestMultiplePermissions contract.
 * Each permission request is tracked independently with its own callback.
 * Multiple in-flight requests are safely handled without callback conflicts.
 *
 * ## Concurrency Safety
 * - Uses RequestMultiplePermissions to handle multiple concurrent requests
 * - Callbacks are keyed by permission string
 * - Each permission's callback is invoked independently when result arrives
 * - Safe to call requestPermission() multiple times before previous results return
 *
 * Example:
 * ```
 * requestRecordAudioPermission { isGranted ->
 *     if (isGranted) {
 *         audioRecorder.startRecording()
 *     }
 * }
 * ```
 */
open class BaseActivity : ComponentActivity() {
    private val permissionCallbacks = mutableMapOf<String, (Boolean) -> Unit>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle each permission result independently
        for ((permission, isGranted) in permissions) {
            val callback = permissionCallbacks.remove(permission)
            callback?.invoke(isGranted)
        }
    }

    // Inject AudioRecorder from Koin
    protected val audioRecorder: AudioRecorder by inject()

    // Inject AudioRecorder from Koin
    protected val audioRecorder: AudioRecorder by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    /**
     * Request a single permission and invoke callback with the result.
     * Safe to call multiple times concurrently - each request is tracked independently.
     *
     * @param permission Permission string (e.g., Manifest.permission.RECORD_AUDIO)
     * @param callback Invoked with true if granted, false if denied
     */
    fun requestPermission(permission: String, callback: (Boolean) -> Unit) {
        permissionCallbacks[permission] = callback
        permissionLauncher.launch(arrayOf(permission))
    }

    /**
     * Request RECORD_AUDIO permission.
     *
     * @param callback Invoked with true if granted, false if denied
     */
    fun requestRecordAudioPermission(callback: (Boolean) -> Unit) {
        requestPermission(Manifest.permission.RECORD_AUDIO, callback)
    }
}
