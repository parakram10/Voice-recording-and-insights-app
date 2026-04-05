// Phase 3.2 — whisper_jni.cpp: Full JNI bridge for whisper.cpp transcription

#include <jni.h>
#include "whisper.h"
#include <android/log.h>
#include <cstring>
#include <vector>
#include <sstream>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
    /**
     * Phase 3.2 — Function 1: Initialize Whisper context from model file
     *
     * Loads the GGML model file and creates a whisper_context for transcription.
     *
     * @param modelPath: Absolute path to ggml-tiny.en-q5_1.bin model file
     * @return: Opaque handle (cast from whisper_context*) to be used in transcribe calls
     *          Returns 0 on failure
     */
    JNIEXPORT jlong JNICALL
    Java_org_example_project_transcription_WhisperContext_whisperInitFromFile(
        JNIEnv *env,
        jobject obj,
        jstring modelPath) {

        if (!modelPath) {
            LOGE("modelPath is null");
            return 0;
        }

        // Convert JNI string to C string
        const char *model_path_cstr = env->GetStringUTFChars(modelPath, nullptr);
        if (!model_path_cstr) {
            LOGE("Failed to get C string from modelPath");
            return 0;
        }

        LOGI("Loading model from: %s", model_path_cstr);

        // Create context parameters (CPU only, no GPU)
        struct whisper_context_params cparams = whisper_context_default_params();
        cparams.use_gpu = false; // CPU transcription only

        // Initialize context from file
        struct whisper_context *ctx = whisper_init_from_file_with_params(model_path_cstr, cparams);

        env->ReleaseStringUTFChars(modelPath, model_path_cstr);

        if (!ctx) {
            LOGE("Failed to initialize whisper context from model file");
            return 0;
        }

        LOGI("Whisper context initialized successfully");
        // Cast pointer to jlong (opaque handle)
        return reinterpret_cast<jlong>(ctx);
    }

    /**
     * Phase 3.2 — Function 2: Transcribe audio samples
     *
     * Runs the Whisper transcription pipeline on PCM audio samples.
     *
     * @param contextHandle: Opaque handle from whisperInitFromFile
     * @param audioSamples: Float array of audio samples (16kHz mono PCM, normalized to [-1.0, 1.0])
     * @return: Transcribed text as Java string
     *          Returns empty string on failure
     */
    JNIEXPORT jstring JNICALL
    Java_org_example_project_transcription_WhisperContext_whisperTranscribe(
        JNIEnv *env,
        jobject obj,
        jlong contextHandle,
        jfloatArray audioSamples) {

        if (!audioSamples) {
            LOGE("audioSamples is null");
            return env->NewStringUTF("");
        }

        if (contextHandle == 0) {
            LOGE("Invalid context handle (0)");
            return env->NewStringUTF("");
        }

        // Cast opaque handle back to whisper_context*
        struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(contextHandle);

        // Get audio array length and extract samples
        jsize audio_len = env->GetArrayLength(audioSamples);
        if (audio_len == 0) {
            LOGW("Audio samples array is empty");
            return env->NewStringUTF("");
        }

        LOGI("Transcribing %d audio samples", audio_len);

        // Copy JNI float array to C++ vector
        jfloat *audio_ptr = env->GetFloatArrayElements(audioSamples, nullptr);
        if (!audio_ptr) {
            LOGE("Failed to get float array elements");
            return env->NewStringUTF("");
        }

        std::vector<float> pcm_data(audio_ptr, audio_ptr + audio_len);
        env->ReleaseFloatArrayElements(audioSamples, audio_ptr, JNI_ABORT);

        // Set up transcription parameters (defaults)
        struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        wparams.print_progress = false;
        wparams.print_realtime = false;
        wparams.print_timestamps = false;

        // Run transcription
        int ret = whisper_full(ctx, wparams, pcm_data.data(), pcm_data.size());

        if (ret != 0) {
            LOGE("whisper_full failed with return code: %d", ret);
            return env->NewStringUTF("");
        }

        // Extract and concatenate all transcribed segments
        std::stringstream result;
        int n_segments = whisper_full_n_segments(ctx);

        LOGI("Transcription complete, %d segments", n_segments);

        for (int i = 0; i < n_segments; ++i) {
            const char *text = whisper_full_get_segment_text(ctx, i);
            if (text) {
                result << text;
            }
        }

        std::string transcribed_text = result.str();
        LOGI("Final transcription length: %zu characters", transcribed_text.length());

        return env->NewStringUTF(transcribed_text.c_str());
    }

    /**
     * Phase 3.2 — Function 3: Free Whisper context and release memory
     *
     * Deallocates the whisper_context and all associated memory.
     *
     * @param contextHandle: Opaque handle from whisperInitFromFile
     */
    JNIEXPORT void JNICALL
    Java_org_example_project_transcription_WhisperContext_whisperFreeContext(
        JNIEnv *env,
        jobject obj,
        jlong contextHandle) {

        if (contextHandle == 0) {
            LOGW("Attempt to free null context handle");
            return;
        }

        // Cast opaque handle back to whisper_context*
        struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(contextHandle);

        LOGI("Freeing whisper context");
        whisper_free(ctx);
        LOGI("Whisper context freed successfully");
    }
}

