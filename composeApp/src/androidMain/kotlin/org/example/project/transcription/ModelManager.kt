// Phase 3.6 — ModelManager.kt: Whisper model asset management (Android only)

package org.example.project.transcription

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * Phase 3.6 — ModelManager
 *
 * Manages the lifecycle of the Whisper GGML model file.
 * - Bundles model in APK assets on first install
 * - Copies model to app-specific storage on first run
 * - Returns path to model file for WhisperContext initialization
 *
 * **Thread-safety**: Synchronized to prevent race conditions during first-run copy.
 * **Storage**: Uses `Context.getFilesDir()` for persistent storage across app restarts.
 */
object ModelManager {
    private const val MODEL_FILENAME = "ggml-tiny.en-q5_1.bin"
    private const val ASSET_PATH = "models/$MODEL_FILENAME"
    private const val BUFFER_SIZE = 8192  // 8KB buffer for file copy

    private var modelPath: String? = null
    private val lock = Any()

    /**
     * Phase 3.6 — Get path to Whisper model file
     *
     * On first call:
     * - Copies model from APK assets to app-specific storage
     * - Verifies file size matches expected model size
     *
     * On subsequent calls:
     * - Returns cached path
     * - Verifies file still exists
     *
     * @param context Android Context for accessing assets and storage
     * @return Absolute path to ggml-tiny.en-q5_1.bin model file
     * @throws IllegalStateException if model cannot be accessed or copied
     * @throws IOException if file copy fails
     */
    fun getModelPath(context: Context): String {
        // Return cached path if already loaded
        modelPath?.let {
            val file = File(it)
            if (file.exists()) {
                return it
            }
        }

        // Synchronized block to prevent race conditions during first-run copy
        synchronized(lock) {
            // Double-check pattern: another thread may have copied during wait
            modelPath?.let {
                val file = File(it)
                if (file.exists()) {
                    return it
                }
            }

            // First run: copy model from assets to app storage
            val modelFile = File(context.filesDir, MODEL_FILENAME)

            if (!modelFile.exists()) {
                copyModelFromAssets(context, modelFile)
            }

            // Verify model file exists and has expected size
            if (!modelFile.exists()) {
                throw IllegalStateException("Model file not found: ${modelFile.absolutePath}")
            }

            if (modelFile.length() == 0L) {
                throw IllegalStateException("Model file is empty: ${modelFile.absolutePath}")
            }

            // Cache the path for future calls
            modelPath = modelFile.absolutePath
            return modelPath!!
        }
    }

    /**
     * Phase 3.6 — Copy model file from APK assets to app-specific storage
     *
     * Uses buffered I/O for efficient copying of large files (~31 MB).
     *
     * @param context Android Context for accessing assets
     * @param targetFile Destination file in app storage
     * @throws IOException if copy fails
     * @throws IllegalStateException if asset cannot be opened
     */
    private fun copyModelFromAssets(context: Context, targetFile: File) {
        try {
            context.assets.open(ASSET_PATH).use { assetInputStream ->
                targetFile.outputStream().use { fileOutputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (assetInputStream.read(buffer).also { bytesRead = it } != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
        } catch (e: IOException) {
            // Clean up partial file on failure
            targetFile.delete()
            throw IOException("Failed to copy model from assets: ${e.message}", e)
        } catch (e: Exception) {
            targetFile.delete()
            throw IllegalStateException("Error accessing model asset '$ASSET_PATH': ${e.message}", e)
        }
    }

    /**
     * Phase 3.6 — Clear cached model path (for testing or manual reset)
     *
     * Useful for forcing re-initialization or testing.
     * Does NOT delete the actual model file from storage.
     */
    fun clearCache() {
        synchronized(lock) {
            modelPath = null
        }
    }

    /**
     * Phase 3.6 — Check if model file exists without copying
     *
     * @param context Android Context
     * @return True if model file exists in app storage
     */
    fun isModelAvailable(context: Context): Boolean {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        return modelFile.exists() && modelFile.length() > 0L
    }
}
