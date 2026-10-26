package az.fadayat.voicetyping.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.fadayat.voicetyping.data.SecureApiKeyStore
import az.fadayat.voicetyping.di.IoDispatcher
import az.fadayat.voicetyping.domain.VoiceStatusStore
import az.fadayat.voicetyping.service.VoiceAccessibilityService
import az.fadayat.voicetyping.service.VoiceOverlayService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyStore: SecureApiKeyStore,
    private val statusStore: VoiceStatusStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MainUiEvent> = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            statusStore.status.collect { status ->
                mutableUiState.update {
                    it.copy(
                        voiceStatus = status,
                        isServiceRunning = VoiceOverlayService.running.get(),
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        mutableUiState.update {
            it.copy(
                hasApiKey = apiKeyStore.hasKey(),
                hasMicrophonePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED,
                hasOverlayPermission = Settings.canDrawOverlays(context),
                hasAccessibilityService = isAccessibilityEnabled(),
                isServiceRunning = VoiceOverlayService.running.get(),
            )
        }
    }

    fun saveApiKey(value: String) {
        viewModelScope.launch {
            val saved = withContext(ioDispatcher) {
                runCatching { apiKeyStore.save(value) }.isSuccess
            }
            refresh()
            mutableEvents.tryEmit(
                MainUiEvent.Message(
                    if (saved) "API açarı təhlükəsiz yadda saxlandı" else "API açarının formatı düzgün deyil",
                ),
            )
        }
    }

    fun clearApiKey() {
        viewModelScope.launch {
            withContext(ioDispatcher) { apiKeyStore.clear() }
            refresh()
            mutableEvents.tryEmit(MainUiEvent.Message("API açarı silindi"))
        }
    }

    fun toggleService() {
        refresh()
        val state = mutableUiState.value
        if (state.isServiceRunning) {
            context.stopService(Intent(context, VoiceOverlayService::class.java))
            mutableUiState.update { it.copy(isServiceRunning = false) }
            return
        }
        if (!state.isReady) {
            mutableEvents.tryEmit(MainUiEvent.Message("Əvvəl bütün hazırlıq addımlarını tamamlayın"))
            return
        }
        ContextCompat.startForegroundService(context, Intent(context, VoiceOverlayService::class.java))
        mutableUiState.update { it.copy(isServiceRunning = true) }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(context, VoiceAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
