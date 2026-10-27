package az.fadayat.voicetyping.domain

import kotlinx.coroutines.flow.Flow

interface TranscriptionRepository {
    suspend fun prepare(
        apiKey: String,
        languageCode: String = "az-AZ",
    ): Boolean

    suspend fun transcribe(
        apiKey: String,
        pcmChunks: Flow<ByteArray>,
        languageCode: String = "az-AZ",
    ): TranscriptionResult

    fun releasePrepared()
}

sealed interface TranscriptionResult {
    data class Success(
        val text: String,
        val route: Route,
    ) : TranscriptionResult

    data class Failure(val reason: FailureReason) : TranscriptionResult

    enum class Route {
        LIVE,
        BATCH,
    }

    enum class FailureReason {
        EMPTY_AUDIO,
        SILENCE,
        INVALID_API_KEY,
        QUOTA,
        NETWORK,
        SERVICE_UNAVAILABLE,
        UNKNOWN,
    }
}
