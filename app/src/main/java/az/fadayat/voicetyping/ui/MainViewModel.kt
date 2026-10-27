package az.fadayat.voicetyping.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.fadayat.voicetyping.data.SecureApiKeyStore
import az.fadayat.voicetyping.di.IoDispatcher
import az.fadayat.voicetyping.service.VoiceInputMethodService
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MainUiEvent> = mutableEvents.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        val expected = ComponentName(context, VoiceInputMethodService::class.java)
        val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
        val selected = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )?.let(ComponentName::unflattenFromString)

        mutableUiState.update {
            it.copy(
                hasApiKey = apiKeyStore.hasKey(),
                hasMicrophonePermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED,
                isImeEnabled = inputMethodManager.enabledInputMethodList.any { info ->
                    ComponentName(info.packageName, info.serviceName) == expected
                },
                isImeSelected = selected == expected,
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

}
