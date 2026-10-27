package az.fadayat.voicetyping.data

import az.fadayat.voicetyping.di.IoDispatcher
import az.fadayat.voicetyping.domain.TranscriptionRepository
import az.fadayat.voicetyping.domain.TranscriptionResult
import az.fadayat.voicetyping.util.PcmAudioValidator
import az.fadayat.voicetyping.util.WavEncoder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiTranscriptionRepository @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TranscriptionRepository {
    internal var liveConnectionFactory: () -> LiveTranscriptionConnection = {
        GeminiLiveConnection(client)
    }
    private val preconnectLock = Any()
    private var preparedLive: PreparedLive? = null
    private var liveWarmup: LiveWarmup? = null

    override suspend fun prepare(apiKey: String, languageCode: String): Boolean =
        withContext(ioDispatcher) {
            when (val action = prepareAction(apiKey, languageCode)) {
                PrepareAction.Ready -> true
                is PrepareAction.Await -> action.warmup.ready.await()
                is PrepareAction.Start -> startWarmup(action)
            }
        }

    override suspend fun transcribe(
        apiKey: String,
        pcmChunks: Flow<ByteArray>,
        languageCode: String,
    ): TranscriptionResult = withContext(ioDispatcher) {
        coroutineScope {
            transcribeInScope(apiKey, pcmChunks, languageCode)
        }
    }

    private suspend fun transcribeInScope(
        apiKey: String,
        pcmChunks: Flow<ByteArray>,
        languageCode: String,
    ): TranscriptionResult = coroutineScope {
        val recording = ByteArrayOutputStream()
        val queuedAudio = Channel<ByteArray>(Channel.UNLIMITED)

        launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                pcmChunks.collect { chunk ->
                    if (recording.size() + chunk.size <= MAX_RECORDING_BYTES) {
                        recording.write(chunk)
                        queuedAudio.send(chunk)
                    }
                }
            } finally {
                queuedAudio.close()
            }
        }

        val acquisition = acquirePrepared(apiKey, languageCode)
        var live: LiveTranscriptionConnection? = when (acquisition) {
            is PreparedAcquisition.Ready -> acquisition.connection
            PreparedAcquisition.NoWarmup -> liveConnectionFactory()
            PreparedAcquisition.WarmupFailed -> null
        }
        val usedPreparedConnection = acquisition is PreparedAcquisition.Ready

        try {
            var liveAvailable = if (acquisition is PreparedAcquisition.WarmupFailed) {
                false
            } else try {
                var activeLive = checkNotNull(live)
                if (!usedPreparedConnection) {
                    activeLive.connect(apiKey, languageCode)
                }
                var activityStarted = activeLive.startActivity()
                if (!activityStarted && usedPreparedConnection) {
                    activeLive.close()
                    activeLive = liveConnectionFactory()
                    live = activeLive
                    activeLive.connect(apiKey, languageCode)
                    activityStarted = activeLive.startActivity()
                }
                check(activityStarted) {
                    "Could not start Live activity"
                }
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }

            for (chunk in queuedAudio) {
                if (liveAvailable && live?.sendAudio(chunk) != true) {
                    liveAvailable = false
                }
            }

            val pcm = recording.toByteArray()
            when (PcmAudioValidator.validate(pcm)) {
                PcmAudioValidator.Result.TOO_SHORT ->
                    return@coroutineScope TranscriptionResult.Failure(TranscriptionResult.FailureReason.EMPTY_AUDIO)
                PcmAudioValidator.Result.SILENCE ->
                    return@coroutineScope TranscriptionResult.Failure(TranscriptionResult.FailureReason.SILENCE)
                PcmAudioValidator.Result.VALID -> Unit
            }

            if (liveAvailable) {
                val liveText = try {
                    checkNotNull(live).finish().trim()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    ""
                }
                if (liveText.isNotEmpty()) {
                    return@coroutineScope TranscriptionResult.Success(
                        liveText,
                        TranscriptionResult.Route.LIVE,
                    )
                }
            }

            batchTranscribe(apiKey, WavEncoder.encode(pcm), languageCode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            TranscriptionResult.Failure(mapFailure(error))
        } finally {
            live?.close()
        }
    }

    override fun releasePrepared() {
        val (prepared, warmup) = synchronized(preconnectLock) {
            val currentPrepared = preparedLive
            val currentWarmup = liveWarmup
            preparedLive = null
            liveWarmup = null
            currentPrepared to currentWarmup
        }
        prepared?.connection?.close()
        warmup?.ready?.complete(false)
        warmup?.connection?.close()
    }

    private fun prepareAction(apiKey: String, languageCode: String): PrepareAction =
        synchronized(preconnectLock) {
            val prepared = preparedLive
            if (prepared?.matches(apiKey, languageCode) == true) {
                return@synchronized PrepareAction.Ready
            }
            val warming = liveWarmup
            if (warming?.matches(apiKey, languageCode) == true) {
                return@synchronized PrepareAction.Await(warming)
            }

            preparedLive = null
            liveWarmup = null
            val next = LiveWarmup(
                apiKey = apiKey,
                languageCode = languageCode,
                connection = liveConnectionFactory(),
            )
            liveWarmup = next
            PrepareAction.Start(next, prepared, warming)
        }

    private suspend fun startWarmup(action: PrepareAction.Start): Boolean {
        action.stalePrepared?.connection?.close()
        action.staleWarmup?.ready?.complete(false)
        action.staleWarmup?.connection?.close()

        var connected = false
        var accepted = false
        try {
            action.warmup.connection.connect(
                action.warmup.apiKey,
                action.warmup.languageCode,
            )
            connected = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Unit
        } finally {
            accepted = synchronized(preconnectLock) {
                if (liveWarmup === action.warmup) {
                    liveWarmup = null
                    if (connected) {
                        preparedLive = PreparedLive(
                            apiKey = action.warmup.apiKey,
                            languageCode = action.warmup.languageCode,
                            connection = action.warmup.connection,
                        )
                    }
                    connected
                } else {
                    false
                }
            }
            if (!accepted) action.warmup.connection.close()
            action.warmup.ready.complete(accepted)
        }
        return accepted
    }

    private suspend fun acquirePrepared(
        apiKey: String,
        languageCode: String,
    ): PreparedAcquisition {
        while (true) {
            when (val action = acquireAction(apiKey, languageCode)) {
                is AcquireAction.Take -> return PreparedAcquisition.Ready(action.connection)
                is AcquireAction.Await -> {
                    if (!action.warmup.ready.await()) return PreparedAcquisition.WarmupFailed
                }
                is AcquireAction.Miss -> {
                    action.stalePrepared?.connection?.close()
                    action.staleWarmup?.ready?.complete(false)
                    action.staleWarmup?.connection?.close()
                    return PreparedAcquisition.NoWarmup
                }
            }
        }
    }

    private fun acquireAction(apiKey: String, languageCode: String): AcquireAction =
        synchronized(preconnectLock) {
            preparedLive?.let { prepared ->
                if (prepared.matches(apiKey, languageCode)) {
                    preparedLive = null
                    return@synchronized AcquireAction.Take(prepared.connection)
                }
            }
            liveWarmup?.let { warmup ->
                if (warmup.matches(apiKey, languageCode)) {
                    return@synchronized AcquireAction.Await(warmup)
                }
            }

            val stalePrepared = preparedLive
            val staleWarmup = liveWarmup
            preparedLive = null
            liveWarmup = null
            AcquireAction.Miss(stalePrepared, staleWarmup)
        }

    private fun batchTranscribe(
        apiKey: String,
        wav: ByteArray,
        languageCode: String,
    ): TranscriptionResult {
        val request = buildBatchRequest(apiKey, wav, languageCode)
        val call = client.newCall(request).apply {
            timeout().timeout(BATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        call.execute().use { response ->
            if (!response.isSuccessful) {
                return TranscriptionResult.Failure(mapHttpCode(response.code))
            }
            val text = response.body?.string()?.let(TranscriptExtractor::fromInteraction).orEmpty()
            return if (text.isBlank()) {
                TranscriptionResult.Failure(TranscriptionResult.FailureReason.SILENCE)
            } else {
                TranscriptionResult.Success(text, TranscriptionResult.Route.BATCH)
            }
        }
    }

    internal fun buildBatchRequest(
        apiKey: String,
        wav: ByteArray,
        languageCode: String,
    ): Request {
        val body = JSONObject()
            .put("model", BATCH_MODEL)
            .put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("type", "audio")
                        .put("data", Base64.getEncoder().encodeToString(wav))
                        .put("mime_type", "audio/wav"),
                ),
            )
            .put(
                "generation_config",
                JSONObject().put(
                    "transcription_config",
                    JSONObject().put("language_codes", JSONArray().put(languageCode)),
                ),
            )
        return Request.Builder()
            .url(BATCH_ENDPOINT)
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun mapFailure(error: Throwable): TranscriptionResult.FailureReason = when (error) {
        is SocketTimeoutException -> TranscriptionResult.FailureReason.SERVICE_UNAVAILABLE
        is IOException -> TranscriptionResult.FailureReason.NETWORK
        else -> TranscriptionResult.FailureReason.UNKNOWN
    }

    private fun mapHttpCode(code: Int): TranscriptionResult.FailureReason = when (code) {
        401, 403 -> TranscriptionResult.FailureReason.INVALID_API_KEY
        429 -> TranscriptionResult.FailureReason.QUOTA
        500, 502, 503, 504 -> TranscriptionResult.FailureReason.SERVICE_UNAVAILABLE
        else -> TranscriptionResult.FailureReason.UNKNOWN
    }

    companion object {
        private const val MAX_RECORDING_BYTES = 16_000 * 2 * 60 * 7
        private const val BATCH_TIMEOUT_SECONDS = 20L
        private const val BATCH_MODEL = "gemini-3.5-transcribe"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val BATCH_ENDPOINT = HttpUrl.Builder()
            .scheme("https")
            .host("generativelanguage.googleapis.com")
            .addPathSegments("v1beta/interactions")
            .build()
    }

    private data class PreparedLive(
        val apiKey: String,
        val languageCode: String,
        val connection: LiveTranscriptionConnection,
    ) {
        fun matches(candidateKey: String, candidateLanguage: String): Boolean =
            apiKey == candidateKey && languageCode == candidateLanguage
    }

    private data class LiveWarmup(
        val apiKey: String,
        val languageCode: String,
        val connection: LiveTranscriptionConnection,
        val ready: CompletableDeferred<Boolean> = CompletableDeferred(),
    ) {
        fun matches(candidateKey: String, candidateLanguage: String): Boolean =
            apiKey == candidateKey && languageCode == candidateLanguage
    }

    private sealed interface PrepareAction {
        data object Ready : PrepareAction
        data class Await(val warmup: LiveWarmup) : PrepareAction
        data class Start(
            val warmup: LiveWarmup,
            val stalePrepared: PreparedLive?,
            val staleWarmup: LiveWarmup?,
        ) : PrepareAction
    }

    private sealed interface AcquireAction {
        data class Take(val connection: LiveTranscriptionConnection) : AcquireAction
        data class Await(val warmup: LiveWarmup) : AcquireAction
        data class Miss(
            val stalePrepared: PreparedLive?,
            val staleWarmup: LiveWarmup?,
        ) : AcquireAction
    }

    private sealed interface PreparedAcquisition {
        data class Ready(val connection: LiveTranscriptionConnection) : PreparedAcquisition
        data object NoWarmup : PreparedAcquisition
        data object WarmupFailed : PreparedAcquisition
    }
}
