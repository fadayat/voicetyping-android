package az.fadayat.voicetyping.domain

data class VoiceStatus(
    val phase: Phase = Phase.IDLE,
    val message: String? = null,
) {
    enum class Phase {
        IDLE,
        CONNECTING,
        RECORDING,
        PROCESSING,
        COPIED,
        ERROR,
    }
}
