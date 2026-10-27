package az.fadayat.voicetyping.data

import java.io.Closeable
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject

internal interface LiveTranscriptionConnection : Closeable {
    suspend fun connect(apiKey: String, languageCode: String)
    fun startActivity(): Boolean
    fun sendAudio(pcm: ByteArray): Boolean
    suspend fun finish(): String
}

internal class GeminiLiveConnection(
    private val client: OkHttpClient,
    private val endpoint: HttpUrl = DEFAULT_ENDPOINT,
    private val connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
    private val finalizeTimeoutMs: Long = FINALIZE_TIMEOUT_MS,
    private val transcriptSettleMs: Long = TRANSCRIPT_SETTLE_MS,
) : LiveTranscriptionConnection {
    private val opened = CompletableDeferred<Unit>()
    private val configured = CompletableDeferred<Unit>()
    private val completed = CompletableDeferred<String>()
    private val failed = CompletableDeferred<Throwable>()
    private val postEndTranscriptUpdates = Channel<Unit>(Channel.CONFLATED)
    private val transcript = StringBuilder()
    private val activityEnded = AtomicBoolean(false)
    private var webSocket: WebSocket? = null

    override suspend fun connect(apiKey: String, languageCode: String) {
        val request = Request.Builder()
            .url(endpoint)
            .header(API_KEY_HEADER, apiKey)
            .build()
        webSocket = client.newWebSocket(request, Listener())
        withTimeoutOrNull(connectTimeoutMs) { opened.await() }
            ?: throw SocketTimeoutException("Live socket open timed out")
        check(send(setupMessage(languageCode))) { "Could not send Live setup" }
        withTimeoutOrNull(connectTimeoutMs) {
            select<Unit> {
                configured.onAwait { }
                failed.onAwait { throw it }
            }
        } ?: throw SocketTimeoutException("Live setup timed out")
    }

    override fun startActivity(): Boolean {
        activityEnded.set(false)
        return send(realtimeMessage("activityStart", JSONObject()))
    }

    override fun sendAudio(pcm: ByteArray): Boolean {
        val audio = JSONObject()
            .put("data", Base64.getEncoder().encodeToString(pcm))
            .put("mimeType", PCM_MIME_TYPE)
        return send(JSONObject().put("realtime_input", JSONObject().put("audio", audio)))
    }

    override suspend fun finish(): String {
        activityEnded.set(true)
        check(send(realtimeMessage("activityEnd", JSONObject()))) {
            "Could not finish Live activity"
        }
        val finalized = awaitFinalTranscript()
        if (finalized != null) return finalized

        val committedTranscript = synchronized(transcript) { transcript.toString().trim() }
        if (committedTranscript.isNotEmpty()) return committedTranscript
        throw SocketTimeoutException("Live transcription finalization timed out")
    }

    override fun close() {
        webSocket?.close(1000, "done")
        webSocket = null
    }

    private fun send(payload: JSONObject): Boolean = webSocket?.send(payload.toString()) == true

    private suspend fun awaitFinalTranscript(): String? {
        val deadlineNanos = System.nanoTime() + finalizeTimeoutMs * NANOS_PER_MILLISECOND
        var receivedPostEndTranscript = false
        while (true) {
            val remainingMs = ((deadlineNanos - System.nanoTime()) / NANOS_PER_MILLISECOND)
                .coerceAtLeast(0L)
            if (remainingMs == 0L) return null
            val waitMs = if (receivedPostEndTranscript) {
                minOf(remainingMs, transcriptSettleMs)
            } else {
                remainingMs
            }
            val event = withTimeoutOrNull(waitMs.coerceAtLeast(1L)) {
                select<FinalizationEvent> {
                    completed.onAwait { FinalizationEvent.Completed(it.trim()) }
                    failed.onAwait { throw it }
                    postEndTranscriptUpdates.onReceive { FinalizationEvent.TranscriptUpdate }
                }
            }
            when (event) {
                is FinalizationEvent.Completed -> return event.text
                FinalizationEvent.TranscriptUpdate -> receivedPostEndTranscript = true
                null -> {
                    if (receivedPostEndTranscript) {
                        return synchronized(transcript) { transcript.toString().trim() }
                    }
                    return null
                }
            }
        }
    }

    private fun setupMessage(languageCode: String): JSONObject {
        val languageCodes = JSONArray().put(languageCode)
        val setup = JSONObject()
            .put("model", "models/$LIVE_MODEL")
            .put(
                "generationConfig",
                JSONObject().put("responseModalities", JSONArray().put("TEXT")),
            )
            .put(
                "inputAudioTranscription",
                JSONObject().put("language_codes", languageCodes),
            )
            .put(
                "realtimeInputConfig",
                JSONObject().put(
                    "automatic_activity_detection",
                    JSONObject().put("disabled", true),
                ),
            )
        return JSONObject().put("setup", setup)
    }

    private fun realtimeMessage(name: String, value: JSONObject): JSONObject =
        JSONObject().put("realtime_input", JSONObject().put(name, value))

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleMessage(bytes.utf8())
        }

        private fun handleMessage(message: String) {
            runCatching { LiveMessageParser.parse(message) }
                .onFailure(::fail)
                .getOrNull()
                ?.forEach(::handle)
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            fail(throwable)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!completed.isCompleted) fail(IllegalStateException("Live connection closed: $code"))
        }
    }

    private fun handle(event: LiveMessageParser.Event) {
        when (event) {
            LiveMessageParser.Event.SetupComplete -> configured.complete(Unit)
            is LiveMessageParser.Event.Transcript -> synchronized(transcript) {
                transcript.append(event.text)
                if (activityEnded.get()) postEndTranscriptUpdates.trySend(Unit)
            }
            LiveMessageParser.Event.TurnComplete -> {
                if (!activityEnded.get()) {
                    fail(IllegalStateException("Live turn completed before recording stopped"))
                } else {
                    completed.complete(synchronized(transcript) { transcript.toString() })
                }
            }
            is LiveMessageParser.Event.Error -> fail(IllegalStateException(event.message))
            LiveMessageParser.Event.Ignore -> Unit
        }
    }

    private fun fail(throwable: Throwable) {
        if (!opened.isCompleted) opened.completeExceptionally(throwable)
        if (!configured.isCompleted) configured.completeExceptionally(throwable)
        if (!failed.isCompleted) failed.complete(throwable)
    }

    companion object {
        private const val LIVE_MODEL = "gemini-3.5-transcribe-live"
        private const val PCM_MIME_TYPE = "audio/pcm;rate=16000"
        private const val API_KEY_HEADER = "x-goog-api-key"
        private const val CONNECT_TIMEOUT_MS = 3_000L
        private const val FINALIZE_TIMEOUT_MS = 3_000L
        private const val TRANSCRIPT_SETTLE_MS = 250L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private val DEFAULT_ENDPOINT = HttpUrl.Builder()
            .scheme("https")
            .host("generativelanguage.googleapis.com")
            .addPathSegments("ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent")
            .build()
    }

    private sealed interface FinalizationEvent {
        data class Completed(val text: String) : FinalizationEvent
        data object TranscriptUpdate : FinalizationEvent
    }
}
