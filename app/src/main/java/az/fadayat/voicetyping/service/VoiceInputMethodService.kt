package az.fadayat.voicetyping.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import az.fadayat.voicetyping.MainActivity
import az.fadayat.voicetyping.data.PcmAudioSource
import az.fadayat.voicetyping.data.SecureApiKeyStore
import az.fadayat.voicetyping.di.MainDispatcher
import az.fadayat.voicetyping.domain.TranscriptionRepository
import az.fadayat.voicetyping.domain.TranscriptionResult
import az.fadayat.voicetyping.util.EditorSecurity
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class VoiceInputMethodService : android.inputmethodservice.InputMethodService() {
    @Inject lateinit var repository: TranscriptionRepository
    @Inject lateinit var apiKeyStore: SecureApiKeyStore
    @Inject lateinit var audioSource: PcmAudioSource
    @Inject @MainDispatcher lateinit var mainDispatcher: CoroutineDispatcher

    private lateinit var serviceScope: CoroutineScope
    private var sessionJob: Job? = null
    private var imeView: VoiceImeView? = null
    private var state = VoiceImeState.IDLE
    private var passwordField = false
    private var editorGeneration = 0L

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + mainDispatcher)
    }

    override fun onCreateInputView(): View = VoiceImeView(
        context = this,
        onRecord = ::onRecordPressed,
        onSwitchBack = ::switchBack,
        onSettings = ::openSettings,
    ).also { imeView = it }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        editorGeneration++
        cancelSession()
        passwordField = attribute?.let { EditorSecurity.isPasswordInput(it.inputType) } == true
        resetView()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        passwordField = info?.let { EditorSecurity.isPasswordInput(it.inputType) } == true
        resetView()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        editorGeneration++
        cancelSession()
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        cancelSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onRecordPressed() {
        when (state) {
            VoiceImeState.CONNECTING -> cancelSession()
            VoiceImeState.RECORDING -> stopRecording()
            VoiceImeState.PROCESSING, VoiceImeState.PASSWORD_BLOCKED -> Unit
            VoiceImeState.IDLE, VoiceImeState.SUCCESS, VoiceImeState.ERROR -> startRecording()
        }
    }

    private fun startRecording() {
        if (passwordField) {
            render(VoiceImeState.PASSWORD_BLOCKED)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            render(VoiceImeState.ERROR, "Mikrofon icazəsini tətbiqin ayarlarından ver")
            return
        }
        val apiKey = apiKeyStore.read()
        if (apiKey == null) {
            render(VoiceImeState.ERROR, "Gemini API açarını tətbiqdə yadda saxla")
            return
        }

        sessionJob?.cancel()
        render(VoiceImeState.CONNECTING)
        val generation = editorGeneration
        sessionJob = serviceScope.launch {
            try {
                val result = repository.transcribe(
                    apiKey = apiKey,
                    pcmChunks = audioSource.stream(
                        onStarted = {
                            serviceScope.launch { render(VoiceImeState.RECORDING) }
                        },
                    ),
                )
                handleResult(result, generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                render(VoiceImeState.ERROR, "Mikrofon icazəsi yoxdur")
            } catch (_: IOException) {
                render(VoiceImeState.ERROR, "Şəbəkə bağlantısını yoxla")
            } catch (_: Throwable) {
                render(VoiceImeState.ERROR, "Səs yazısı başladılmadı")
            }
        }
    }

    private fun stopRecording() {
        if (state != VoiceImeState.RECORDING) return
        render(VoiceImeState.PROCESSING)
        audioSource.stop()
        serviceScope.launch {
            val finished = withTimeoutOrNull(PROCESSING_TIMEOUT_MILLIS) {
                sessionJob?.join()
                true
            } ?: false
            if (!finished && state == VoiceImeState.PROCESSING) {
                sessionJob?.cancel()
                render(VoiceImeState.ERROR, "Xidmət gecikdi. Yenidən cəhd et")
            }
        }
    }

    private suspend fun handleResult(result: TranscriptionResult, generation: Long) {
        if (generation != editorGeneration) return
        when (result) {
            is TranscriptionResult.Success -> {
                if (passwordField) {
                    render(VoiceImeState.PASSWORD_BLOCKED)
                    return
                }
                val committed = currentInputConnection?.commitText(result.text, 1) == true
                if (!committed) {
                    render(VoiceImeState.ERROR, "Mətni bu xanaya yazmaq mümkün olmadı")
                    return
                }
                render(VoiceImeState.SUCCESS)
                delay(SWITCH_BACK_DELAY_MILLIS)
                switchBack()
            }
            is TranscriptionResult.Failure -> render(
                VoiceImeState.ERROR,
                when (result.reason) {
                    TranscriptionResult.FailureReason.EMPTY_AUDIO,
                    TranscriptionResult.FailureReason.SILENCE,
                    -> "Səs eşidilmədi. Yenidən cəhd et"
                    TranscriptionResult.FailureReason.INVALID_API_KEY -> "Gemini API açarı yanlışdır"
                    TranscriptionResult.FailureReason.QUOTA -> "Gemini limiti bitib"
                    TranscriptionResult.FailureReason.NETWORK -> "Şəbəkə bağlantısını yoxla"
                    TranscriptionResult.FailureReason.SERVICE_UNAVAILABLE -> "Gemini hazırda cavab vermir"
                    TranscriptionResult.FailureReason.UNKNOWN -> "Transkripsiya alınmadı"
                },
            )
        }
    }

    private fun cancelSession() {
        audioSource.stop()
        sessionJob?.cancel()
        sessionJob = null
        if (::serviceScope.isInitialized) resetView()
    }

    private fun resetView() {
        render(if (passwordField) VoiceImeState.PASSWORD_BLOCKED else VoiceImeState.IDLE)
    }

    private fun render(newState: VoiceImeState, message: String? = null) {
        state = newState
        imeView?.render(newState, message)
    }

    @Suppress("DEPRECATION")
    private fun switchBack() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            val token = window.window?.attributes?.token
            getSystemService(InputMethodManager::class.java).switchToLastInputMethod(token)
        }
        if (!switched) {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        }
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        private const val PROCESSING_TIMEOUT_MILLIS = 30_000L
        private const val SWITCH_BACK_DELAY_MILLIS = 450L
    }
}
