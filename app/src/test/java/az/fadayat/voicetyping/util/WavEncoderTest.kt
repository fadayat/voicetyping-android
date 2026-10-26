package az.fadayat.voicetyping.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WavEncoderTest {
    @Test
    fun writesCanonicalMono16kHeaderAndPayload() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = WavEncoder.encode(pcm)
        val littleEndian = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(40, littleEndian.getInt(4))
        assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals(1, littleEndian.getShort(22).toInt())
        assertEquals(16_000, littleEndian.getInt(24))
        assertEquals(16, littleEndian.getShort(34).toInt())
        assertEquals(4, littleEndian.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }
}
