package az.fadayat.voicetyping.ui

import az.fadayat.voicetyping.domain.VoiceStatus

data class MainUiState(
    val hasApiKey: Boolean = false,
    val hasMicrophonePermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityService: Boolean = false,
    val isServiceRunning: Boolean = false,
    val voiceStatus: VoiceStatus = VoiceStatus(),
) {
    val isReady: Boolean
        get() = hasApiKey && hasMicrophonePermission && hasOverlayPermission && hasAccessibilityService
}

sealed interface MainUiEvent {
    data class Message(val value: String) : MainUiEvent
}
