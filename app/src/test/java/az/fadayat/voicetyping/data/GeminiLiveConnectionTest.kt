package az.fadayat.voicetyping.data

import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.encodeUtf8
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GeminiLiveConnectionTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamsPcmAndReturnsOnlyFinalTranscript() = runBlocking {
        val received = LinkedBlockingQueue<String>()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        received += text
                        val root = JSONObject(text)
                        when {
                            root.has("setup") -> webSocket.send("{\"setupComplete\":{}}")
                            root.optJSONObject("realtime_input")?.has("activityEnd") == true -> {
                                webSocket.send(
                                    """{"serverContent":{"inputTranscription":{"text":"Salam dünya"},"turnComplete":true}}""",
                                )
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = Unit

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
        server.start()
        val client = OkHttpClient()
        val connection = GeminiLiveConnection(
            client = client,
            endpoint = server.url("/live"),
        )

        connection.connect("test-key-with-safe-length", "az-AZ")
        assertTrue(connection.startActivity())
        assertTrue(connection.sendAudio(byteArrayOf(1, 2, 3, 4)))
        assertEquals("Salam dünya", connection.finish())
        connection.close()

        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("test-key-with-safe-length", request?.getHeader("x-goog-api-key"))
        assertEquals(null, request?.requestUrl?.queryParameter("key"))
        val messages = buildList {
            while (true) add(received.poll() ?: break)
        }
        val setup = messages.map(::JSONObject).first { it.has("setup") }.getJSONObject("setup")
        assertEquals("models/gemini-3.5-transcribe-live", setup.getString("model"))
        assertEquals(
            "az-AZ",
            setup.getJSONObject("inputAudioTranscription")
                .getJSONArray("language_codes")
                .getString(0),
        )
        assertTrue(
            setup.getJSONObject("realtimeInputConfig")
                .getJSONObject("automatic_activity_detection")
                .getBoolean("disabled"),
        )
        assertTrue(messages.any { it.contains("audio/pcm;rate=16000") })
        assertEquals(
            1,
            messages.count {
                JSONObject(it).optJSONObject("realtime_input")?.has("activityStart") == true
            },
        )
        assertTrue(messages.any { JSONObject(it).has("realtime_input") })
        assertFalse(messages.any { it.contains("test-key-with-safe-length") })
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test
    fun returnsCommittedTranscriptWhenTurnCompleteIsMissing() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val root = JSONObject(text)
                        when {
                            root.has("setup") -> webSocket.send("{\"setupComplete\":{}}".encodeUtf8())
                            root.optJSONObject("realtime_input")?.has("activityEnd") == true -> {
                                webSocket.send(
                                    """{"serverContent":{"inputTranscription":{"text":"Hazır "}}}""".encodeUtf8(),
                                )
                                Thread.sleep(10L)
                                webSocket.send(
                                    """{"serverContent":{"inputTranscription":{"text":"mətn"}}}""".encodeUtf8(),
                                )
                            }
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
        server.start()
        val client = OkHttpClient()
        val connection = GeminiLiveConnection(
            client = client,
            endpoint = server.url("/live"),
            finalizeTimeoutMs = 2_000L,
            transcriptSettleMs = 25L,
        )

        connection.connect("test-key-with-safe-length", "az-AZ")
        assertTrue(connection.startActivity())
        connection.sendAudio(byteArrayOf(1, 2, 3, 4))

        val startedAtNanos = System.nanoTime()
        assertEquals("Hazır mətn", connection.finish())
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        assertTrue("Final transcript should not wait for turnComplete timeout", elapsedMillis < 500L)
        connection.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    @Test
    fun setupTimeoutIsARecoverableNetworkFailureNotCoroutineCancellation() {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
        server.start()
        val client = OkHttpClient()
        val connection = GeminiLiveConnection(
            client = client,
            endpoint = server.url("/live"),
            connectTimeoutMs = 100L,
        )

        assertThrows(SocketTimeoutException::class.java) {
            runBlocking { connection.connect("test-key-with-safe-length", "az-AZ") }
        }

        connection.close()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
