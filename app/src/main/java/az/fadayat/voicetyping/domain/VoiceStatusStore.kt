package az.fadayat.voicetyping.domain

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class VoiceStatusStore @Inject constructor() {
    private val mutableStatus = MutableStateFlow(VoiceStatus())
    val status: StateFlow<VoiceStatus> = mutableStatus.asStateFlow()

    fun set(phase: VoiceStatus.Phase, message: String? = null) {
        mutableStatus.update { VoiceStatus(phase, message) }
    }
}
