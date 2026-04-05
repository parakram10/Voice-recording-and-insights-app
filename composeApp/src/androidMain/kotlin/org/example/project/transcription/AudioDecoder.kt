// Phase 3.5 — AudioDecoder.kt: MP4 audio extraction and PCM conversion (Android only)

package org.example.project.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import kotlin.math.min

/**
 * Phase 3.5 — AudioDecoder
 *
 * Extracts and decodes MP4 audio to 16kHz mono PCM format suitable for Whisper.cpp.
 *
 * Pipeline:
 * 1. Extract audio track from MP4 via MediaExtractor
 * 2. Decode AAC → PCM via MediaCodec
 * 3. Downsample stereo → mono (if needed)
 * 4. Resample to 16kHz (linear interpolation)
 * 5. Normalize to [-1.0, 1.0] range
 *
 * **Thread-safety**: Not thread-safe. Use one instance per thread.
 * **Resource management**: Call [close] to release MediaCodec and MediaExtractor.
 */
object AudioDecoder {
    private const val SAMPLE_RATE_16KHZ = 16000
    private const val TIMEOUT_US = 10000L  // 10ms timeout for dequeue operations

    /**
     * Phase 3.5 — Decode MP4 audio to 16kHz mono PCM
     *
     * @param filePath Absolute path to MP4 file
     * @return FloatArray of PCM samples normalized to [-1.0, 1.0]
     * @throws IllegalArgumentException if file doesn't exist or has no audio track
     * @throws RuntimeException if extraction or decoding fails
     */
    fun decodeToFloatArray(filePath: String): FloatArray {
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(filePath)

            // Find audio track
            val audioTrackIndex = findAudioTrack(extractor)
                ?: throw IllegalArgumentException("No audio track found in $filePath")

            extractor.selectTrack(audioTrackIndex)
            val audioFormat = extractor.getTrackFormat(audioTrackIndex)

            // Initialize codec
            val mimeType = audioFormat.getString(MediaFormat.KEY_MIME)
                ?: throw RuntimeException("No MIME type in audio format")
            val codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            // Decode audio to PCM short samples
            val shortSamples = decodeToPcm(extractor, codec)
            val sourceSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Convert short samples to mono float array
            val monoSamples = if (channelCount > 1) {
                shortSamples.downSampleToMono(channelCount)
            } else {
                shortSamples
            }

            // Resample to 16kHz if needed
            val resampledSamples = if (sourceSampleRate != SAMPLE_RATE_16KHZ) {
                resampleLinear(monoSamples, sourceSampleRate, SAMPLE_RATE_16KHZ)
            } else {
                monoSamples
            }

            // Normalize to [-1.0, 1.0]
            val floatSamples = normalizeToFloat(resampledSamples)

            codec.release()
            return floatSamples

        } finally {
            extractor.release()
        }
    }

    /**
     * Find audio track index in MediaExtractor
     *
     * @return Track index of first audio track, or null if not found
     */
    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mimeType = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mimeType.startsWith("audio/")) {
                return i
            }
        }
        return null
    }

    /**
     * Decode audio samples using MediaCodec
     *
     * @return ShortArray of decoded PCM samples (signed 16-bit)
     */
    private fun decodeToPcm(extractor: MediaExtractor, codec: MediaCodec): ShortArray {
        val decodedSamples = mutableListOf<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            // Feed data to decoder
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                        ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)

                    if (sampleSize < 0) {
                        // End of stream
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            // Retrieve decoded output
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                        ?: continue

                    val samples = ShortArray(bufferInfo.size / 2)
                    outputBuffer.asShortBuffer().get(samples)
                    decodedSamples.addAll(samples.asIterable())

                    codec.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Format changed, continue decoding
                }
                inputDone && outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No more output
                    break
                }
            }
        }

        return decodedSamples.toShortArray()
    }

    /**
     * Downsample stereo/multichannel audio to mono
     *
     * Average samples from all channels into single mono channel
     *
     * @param samples Short array from decoder
     * @param channelCount Number of audio channels
     * @return Mono short array
     */
    private fun ShortArray.downSampleToMono(channelCount: Int): ShortArray {
        val monoSamples = mutableListOf<Short>()

        for (i in indices step channelCount) {
            var sum = 0
            for (j in 0 until min(channelCount, size - i)) {
                sum += this[i + j].toInt()
            }
            val avgSample = (sum / channelCount).toShort()
            monoSamples.add(avgSample)
        }

        return monoSamples.toShortArray()
    }

    /**
     * Resample audio using linear interpolation
     *
     * @param samples Input samples at source sample rate
     * @param sourceSampleRate Input sample rate (e.g., 44100)
     * @param targetSampleRate Output sample rate (e.g., 16000)
     * @return Resampled short array
     */
    private fun resampleLinear(
        samples: ShortArray,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): ShortArray {
        if (sourceSampleRate == targetSampleRate) {
            return samples
        }

        val ratio = sourceSampleRate.toDouble() / targetSampleRate.toDouble()
        val targetLength = (samples.size / ratio).toInt()
        val resampled = ShortArray(targetLength)

        for (i in 0 until targetLength) {
            val sourceIdx = i * ratio
            val sourceIdxInt = sourceIdx.toInt()
            val fraction = sourceIdx - sourceIdxInt

            if (sourceIdxInt >= samples.size - 1) {
                resampled[i] = samples[samples.size - 1]
            } else {
                // Linear interpolation between two samples
                val sample1 = samples[sourceIdxInt].toInt()
                val sample2 = samples[sourceIdxInt + 1].toInt()
                val interpolated = (sample1 + (sample2 - sample1) * fraction).toInt()
                resampled[i] = interpolated.toShort()
            }
        }

        return resampled
    }

    /**
     * Normalize 16-bit signed samples to float [-1.0, 1.0]
     *
     * @param samples Short array of signed 16-bit PCM samples
     * @return Float array normalized to [-1.0, 1.0]
     */
    private fun normalizeToFloat(samples: ShortArray): FloatArray {
        val floatSamples = FloatArray(samples.size)
        val maxShortValue = Short.MAX_VALUE.toFloat()

        for (i in samples.indices) {
            floatSamples[i] = samples[i].toFloat() / maxShortValue
        }

        return floatSamples
    }
}
