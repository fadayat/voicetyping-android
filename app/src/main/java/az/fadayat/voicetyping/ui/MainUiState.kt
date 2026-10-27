package az.fadayat.voicetyping.ui

data class MainUiState(
    val hasApiKey: Boolean = false,
    val hasMicrophonePermission: Boolean = false,
    val isImeEnabled: Boolean = false,
    val isImeSelected: Boolean = false,
) {
    val canChooseKeyboard: Boolean
        get() = hasApiKey && hasMicrophonePermission && isImeEnabled

    val isReady: Boolean
        get() = canChooseKeyboard && isImeSelected
}

sealed interface MainUiEvent {
    data class Message(val value: String) : MainUiEvent
}
