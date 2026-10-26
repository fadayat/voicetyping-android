package az.fadayat.voicetyping.util

import kotlin.math.sqrt

object PcmAudioValidator {
    private const val SAMPLE_RATE = 16_000
    private const val BYTES_PER_SAMPLE = 2
    private const val MIN_DURATION_MS = 250L
    private const val MIN_PEAK = 240
    private const val MIN_RMS = 35.0

    enum class Result {
        VALID,
        TOO_SHORT,
        SILENCE,
    }

    fun validate(pcm: ByteArray): Result {
        val durationMs = pcm.size.toLong() * 1_000L / (SAMPLE_RATE * BYTES_PER_SAMPLE)
        if (durationMs < MIN_DURATION_MS) return Result.TOO_SHORT

        var peak = 0
        var sumSquares = 0.0
        var samples = 0
        var index = 0
        while (index + 1 < pcm.size) {
            val sample = ((pcm[index + 1].toInt() shl 8) or (pcm[index].toInt() and 0xff)).toShort().toInt()
            val absolute = kotlin.math.abs(sample)
            if (absolute > peak) peak = absolute
            sumSquares += sample.toDouble() * sample.toDouble()
            samples++
            index += 2
        }
        val rms = if (samples == 0) 0.0 else sqrt(sumSquares / samples)
        return if (peak >= MIN_PEAK && rms >= MIN_RMS) Result.VALID else Result.SILENCE
    }
}
