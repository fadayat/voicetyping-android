package az.fadayat.voicetyping.data

import az.fadayat.voicetyping.domain.TranscriptionResult
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTranscriptionRepositoryTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startsAudioCaptureWhileLiveConnectionIsStillOpening() = runTest {
        val connectStarted = CompletableDeferred<Unit>()
        val allowConnect = CompletableDeferred<Unit>()
        val captureStarted = CompletableDeferred<Unit>()
        val allowCaptureToFinish = CompletableDeferred<Unit>()
        val sentAudio = mutableListOf<ByteArray>()
        val live = object : LiveTranscriptionConnection {
            override suspend fun connect(apiKey: String, languageCode: String) {
                connectStarted.complete(Unit)
                allowConnect.await()
            }

            override fun sendAudio(pcm: ByteArray): Boolean {
                sentAudio += pcm
                return true
            }

            override suspend fun finish(): String = "Salam dünya"

            override fun close() = Unit
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = GeminiTranscriptionRepository(OkHttpClient(), dispatcher).apply {
            liveConnectionFactory = { live }
        }
        val audio = ByteArray(9_600) { index -> if (index % 2 == 0) 0 else 16 }
        val audioFlow = flow {
            captureStarted.complete(Unit)
            emit(audio)
            allowCaptureToFinish.await()
        }

        val result = async {
            repository.transcribe("test-key-with-safe-length", audioFlow, "az-AZ")
        }

        connectStarted.await()
        assertTrue("Audio capture must start before Live connection completes", captureStarted.isCompleted)
        allowConnect.complete(Unit)
        allowCaptureToFinish.complete(Unit)

        assertEquals("Salam dünya", (result.await() as TranscriptionResult.Success).text)
        assertEquals(1, sentAudio.size)
        assertTrue(sentAudio.single().contentEquals(audio))
    }

    @Test
    fun batchFallbackUsesInteractionsApiWithoutPuttingKeyInUrl() {
        val repository = GeminiTranscriptionRepository(OkHttpClient(), Dispatchers.Unconfined)
        val wav = byteArrayOf(1, 2, 3, 4)

        val request = repository.buildBatchRequest(
            apiKey = "test-key-with-safe-length",
            wav = wav,
            languageCode = "az-AZ",
        )

        assertEquals("/v1beta/interactions", request.url.encodedPath)
        assertNull(request.url.queryParameter("key"))
        assertEquals("test-key-with-safe-length", request.header("x-goog-api-key"))

        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val rawBody = buffer.readUtf8()
        val body = JSONObject(rawBody)
        assertEquals("gemini-3.5-transcribe", body.getString("model"))
        val audio = body.getJSONArray("input").getJSONObject(0)
        assertEquals("audio", audio.getString("type"))
        assertEquals("audio/wav", audio.getString("mime_type"))
        assertEquals(Base64.getEncoder().encodeToString(wav), audio.getString("data"))
        val languages = body.getJSONObject("generation_config")
            .getJSONObject("transcription_config")
            .getJSONArray("language_codes")
        assertEquals("az-AZ", languages.getString(0))
        assertFalse(rawBody.contains("test-key-with-safe-length"))
    }
}
