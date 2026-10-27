package az.fadayat.voicetyping.data

import az.fadayat.voicetyping.domain.TranscriptionResult
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiTranscriptionRepositoryTest {
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

            override fun startActivity(): Boolean = true

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
    fun reusesPreparedLiveConnectionForNextTranscription() = runTest {
        var connectCount = 0
        var startCount = 0
        var closeCount = 0
        val live = object : LiveTranscriptionConnection {
            override suspend fun connect(apiKey: String, languageCode: String) {
                connectCount++
            }

            override fun startActivity(): Boolean {
                startCount++
                return true
            }

            override fun sendAudio(pcm: ByteArray): Boolean = true

            override suspend fun finish(): String = "Hazır mətn"

            override fun close() {
                closeCount++
            }
        }
        val repository = GeminiTranscriptionRepository(
            OkHttpClient(),
            UnconfinedTestDispatcher(testScheduler),
        ).apply {
            liveConnectionFactory = { live }
        }
        val audio = ByteArray(9_600) { index -> if (index % 2 == 0) 0 else 16 }

        assertTrue(repository.prepare("test-key-with-safe-length", "az-AZ"))
        assertTrue(repository.prepare("test-key-with-safe-length", "az-AZ"))
        assertEquals(0, startCount)
        val result = repository.transcribe(
            "test-key-with-safe-length",
            flowOf(audio),
            "az-AZ",
        )

        assertEquals("Hazır mətn", (result as TranscriptionResult.Success).text)
        assertEquals(1, connectCount)
        assertEquals(1, startCount)
        assertEquals(1, closeCount)
    }

    @Test
    fun capturesAudioWhileInFlightPreconnectFinishes() = runTest {
        val connectStarted = CompletableDeferred<Unit>()
        val allowConnect = CompletableDeferred<Unit>()
        val captureStarted = CompletableDeferred<Unit>()
        var factoryCount = 0
        var startCount = 0
        val live = object : LiveTranscriptionConnection {
            override suspend fun connect(apiKey: String, languageCode: String) {
                connectStarted.complete(Unit)
                allowConnect.await()
            }

            override fun startActivity(): Boolean {
                startCount++
                return true
            }

            override fun sendAudio(pcm: ByteArray): Boolean = true
            override suspend fun finish(): String = "Sürətli mətn"
            override fun close() = Unit
        }
        val repository = GeminiTranscriptionRepository(
            OkHttpClient(),
            UnconfinedTestDispatcher(testScheduler),
        ).apply {
            liveConnectionFactory = {
                factoryCount++
                live
            }
        }
        val audio = ByteArray(9_600) { index -> if (index % 2 == 0) 0 else 16 }

        val preparation = async {
            repository.prepare("test-key-with-safe-length", "az-AZ")
        }
        connectStarted.await()
        val transcription = async {
            repository.transcribe(
                "test-key-with-safe-length",
                flow {
                    captureStarted.complete(Unit)
                    emit(audio)
                },
                "az-AZ",
            )
        }

        captureStarted.await()
        assertTrue(captureStarted.isCompleted)
        allowConnect.complete(Unit)

        assertTrue(preparation.await())
        assertEquals("Sürətli mətn", (transcription.await() as TranscriptionResult.Success).text)
        assertEquals(1, factoryCount)
        assertEquals(1, startCount)
    }

    @Test
    fun failedInFlightPreconnectDoesNotOpenSecondLiveConnection() = runTest {
        val connectStarted = CompletableDeferred<Unit>()
        val failConnect = CompletableDeferred<Unit>()
        val captureStarted = CompletableDeferred<Unit>()
        var factoryCount = 0
        val live = object : LiveTranscriptionConnection {
            override suspend fun connect(apiKey: String, languageCode: String) {
                connectStarted.complete(Unit)
                failConnect.await()
                throw IOException("offline")
            }

            override fun startActivity(): Boolean = true
            override fun sendAudio(pcm: ByteArray): Boolean = true
            override suspend fun finish(): String = ""
            override fun close() = Unit
        }
        val repository = GeminiTranscriptionRepository(
            OkHttpClient(),
            UnconfinedTestDispatcher(testScheduler),
        ).apply {
            liveConnectionFactory = {
                factoryCount++
                live
            }
        }

        val preparation = async {
            repository.prepare("test-key-with-safe-length", "az-AZ")
        }
        connectStarted.await()
        val transcription = async {
            repository.transcribe(
                "test-key-with-safe-length",
                flow {
                    captureStarted.complete(Unit)
                    emit(ByteArray(9_600))
                },
                "az-AZ",
            )
        }
        captureStarted.await()
        failConnect.complete(Unit)

        assertFalse(preparation.await())
        val result = transcription.await() as TranscriptionResult.Failure
        assertEquals(TranscriptionResult.FailureReason.SILENCE, result.reason)
        assertEquals(1, factoryCount)
    }

    @Test
    fun releasePreparedClosesUnusedConnection() = runTest {
        var closeCount = 0
        val live = object : LiveTranscriptionConnection {
            override suspend fun connect(apiKey: String, languageCode: String) = Unit
            override fun startActivity(): Boolean = true
            override fun sendAudio(pcm: ByteArray): Boolean = true
            override suspend fun finish(): String = ""
            override fun close() {
                closeCount++
            }
        }
        val repository = GeminiTranscriptionRepository(
            OkHttpClient(),
            UnconfinedTestDispatcher(testScheduler),
        ).apply {
            liveConnectionFactory = { live }
        }

        assertTrue(repository.prepare("test-key-with-safe-length", "az-AZ"))
        repository.releasePrepared()

        assertEquals(1, closeCount)
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
