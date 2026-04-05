// Phase 3.4 — WhisperContext.kt: Kotlin wrapper for whisper.cpp JNI bridge (Android only)

package org.example.project.transcription

/**
 * Phase 3.4 — WhisperContext
 *
 * Kotlin wrapper around the native whisper.cpp JNI bridge.
 * Manages the lifecycle of a Whisper transcription context and provides a clean API
 * for initializing the model, running transcription, and cleanup.
 *
 * **Thread-safety**: Context handles are not thread-safe. Create one context per thread.
 *
 * **Resource management**: Implements [AutoCloseable] for use with try-with-resources.
 * Always call [close] (directly or via try-with-resources) to prevent native memory leaks.
 */
class WhisperContext private constructor(
    @Volatile private var contextHandle: Long
) : AutoCloseable {
    companion object {
        private val libraryLoaded = lazy {
            try {
                System.loadLibrary("whisper_jni")
                true
            } catch (e: UnsatisfiedLinkError) {
                throw RuntimeException("Failed to load whisper_jni library", e)
            }
        }

        private fun ensureLibraryLoaded() {
            libraryLoaded.value
        }

        /**
         * Initialize Whisper context from a model file
         *
         * @param modelPath Absolute path to ggml-tiny.en-q5_1.bin model file
         * @return WhisperContext instance
         * @throws IllegalArgumentException if modelPath is empty or blank
         * @throws RuntimeException if native initialization fails or library cannot be loaded
         */
        fun initFromFile(modelPath: String): WhisperContext {
            if (modelPath.isBlank()) {
                throw IllegalArgumentException("modelPath cannot be empty")
            }

            ensureLibraryLoaded()

            val handle = whisperInitFromFile(modelPath)
            if (handle == 0L) {
                throw RuntimeException("Failed to initialize Whisper context from $modelPath")
            }

            return WhisperContext(handle)
        }

        /**
         * JNI function declarations matching whisper_jni.cpp signatures:
         * - Java_org_example_project_transcription_WhisperContext_whisperInitFromFile
         * - Java_org_example_project_transcription_WhisperContext_whisperTranscribe
         * - Java_org_example_project_transcription_WhisperContext_whisperFreeContext
         */
        private external fun whisperInitFromFile(modelPath: String): Long
        private external fun whisperTranscribe(contextHandle: Long, audioSamples: FloatArray): String
        private external fun whisperFreeContext(contextHandle: Long)
    }

    /**
     * Run transcription on audio samples
     *
     * @param audioSamples Float array of PCM audio samples at 16kHz, normalized to [-1.0, 1.0]
     * @return Transcribed text
     * @throws IllegalStateException if context has been freed
     * @throws IllegalArgumentException if audioSamples is empty
     */
    fun transcribe(audioSamples: FloatArray): String {
        if (contextHandle == 0L) {
            throw IllegalStateException("Context has been freed. Cannot transcribe.")
        }

        if (audioSamples.isEmpty()) {
            throw IllegalArgumentException("audioSamples cannot be empty")
        }

        return Companion.whisperTranscribe(contextHandle, audioSamples)
    }

    /**
     * Free the Whisper context and release all associated native memory.
     *
     * Safe to call multiple times (no-op on already-freed contexts).
     * Prefer using try-with-resources instead of calling directly.
     */
    override fun close() {
        if (contextHandle != 0L) {
            try {
                Companion.whisperFreeContext(contextHandle)
            } catch (e: Exception) {
                // Suppress native cleanup errors to allow safe resource cleanup
            } finally {
                contextHandle = 0L
            }
        }
    }

    /**
     * Check if context is still valid (not freed)
     */
    fun isValid(): Boolean = contextHandle != 0L
}
