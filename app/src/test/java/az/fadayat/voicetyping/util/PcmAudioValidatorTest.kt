package az.fadayat.voicetyping.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmAudioValidatorTest {
    @Test
    fun rejectsAudioShorterThanQuarterSecond() {
        assertEquals(
            PcmAudioValidator.Result.TOO_SHORT,
            PcmAudioValidator.validate(ByteArray(7_998)),
        )
    }

    @Test
    fun rejectsDigitalSilence() {
        assertEquals(
            PcmAudioValidator.Result.SILENCE,
            PcmAudioValidator.validate(ByteArray(16_000)),
        )
    }

    @Test
    fun acceptsVoicedPcm() {
        val samples = ShortArray(8_000) { index ->
            (sin(index / 10.0) * 2_000).toInt().toShort()
        }
        val pcm = ByteBuffer.allocate(samples.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { samples.forEach(::putShort) }
            .array()

        assertEquals(PcmAudioValidator.Result.VALID, PcmAudioValidator.validate(pcm))
    }
}
