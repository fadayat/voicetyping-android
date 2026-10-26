package az.fadayat.voicetyping.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import az.fadayat.voicetyping.MainActivity
import az.fadayat.voicetyping.R
import az.fadayat.voicetyping.data.PcmAudioSource
import az.fadayat.voicetyping.data.SecureApiKeyStore
import az.fadayat.voicetyping.domain.TranscriptionRepository
import az.fadayat.voicetyping.domain.TranscriptionResult
import az.fadayat.voicetyping.domain.VoiceStatus
import az.fadayat.voicetyping.domain.VoiceStatusStore
import az.fadayat.voicetyping.overlay.DismissTargetView
import az.fadayat.voicetyping.overlay.VoiceOrbView
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VoiceOverlayService : Service() {
    @Inject lateinit var repository: TranscriptionRepository
    @Inject lateinit var apiKeyStore: SecureApiKeyStore
    @Inject lateinit var audioSource: PcmAudioSource
    @Inject lateinit var statusStore: VoiceStatusStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var orb: VoiceOrbView? = null
    private var dismissTarget: DismissTargetView? = null
    private var sessionJob: Job? = null
    private var processingWatchdogJob: Job? = null
    private var phase = VoiceStatus.Phase.IDLE

    override fun onCreate() {
        super.onCreate()
        running.set(true)
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        startForegroundSafely(buildNotification(getString(R.string.notification_idle)))
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)
        showOrb()
        setPhase(VoiceStatus.Phase.IDLE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running.set(false)
        audioSource.stop()
        sessionJob?.cancel()
        processingWatchdogJob?.cancel()
        hideDismissTarget()
        orb?.let { runCatching { windowManager.removeView(it) } }
        orb = null
        serviceScope.cancel()
        statusStore.set(VoiceStatus.Phase.IDLE)
        super.onDestroy()
    }

    private fun showOrb() {
        val size = (72 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (12 * resources.displayMetrics.density).toInt()
            y = resources.displayMetrics.heightPixels / 3
        }
        orb = VoiceOrbView(
            context = this,
            windowManager = windowManager,
            layoutParams = params,
            onTap = ::onOrbTapped,
            onDragStarted = ::showDismissTarget,
            onDragged = ::updateDismissTarget,
            onDragEnded = ::finishOrbDrag,
        ).also {
            windowManager.addView(it, params)
        }
    }

    private fun showDismissTarget() {
        if (dismissTarget != null) return
        val size = (96 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (28 * resources.displayMetrics.density).toInt()
        }
        dismissTarget = DismissTargetView(this).also {
            windowManager.addView(it, params)
        }
    }

    private fun updateDismissTarget(rawX: Float, rawY: Float) {
        dismissTarget?.let { it.setHighlighted(it.containsRawPoint(rawX, rawY)) }
    }

    private fun finishOrbDrag(rawX: Float, rawY: Float, cancelled: Boolean) {
        val shouldStop = !cancelled && dismissTarget?.containsRawPoint(rawX, rawY) == true
        hideDismissTarget()
        if (shouldStop) stopSelf()
    }

    private fun hideDismissTarget() {
        dismissTarget?.let { runCatching { windowManager.removeView(it) } }
        dismissTarget = null
    }

    private fun onOrbTapped() {
        when (phase) {
            VoiceStatus.Phase.IDLE,
            VoiceStatus.Phase.COPIED,
            VoiceStatus.Phase.ERROR,
            -> startTranscription()
            VoiceStatus.Phase.CONNECTING -> cancelConnecting()
            VoiceStatus.Phase.RECORDING -> stopRecording()
            VoiceStatus.Phase.PROCESSING -> Unit
        }
    }

    private fun startTranscription() {
        processingWatchdogJob?.cancel()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showError("Mikrofon icazəsi verilməyib")
            return
        }
        val apiKey = apiKeyStore.read()
        if (apiKey.isNullOrBlank()) {
            showError("Gemini API açarını tətbiqdən yadda saxlayın")
            return
        }
        setPhase(VoiceStatus.Phase.CONNECTING, "Gemini-yə qoşulur")
        sessionJob = serviceScope.launch {
            try {
                val result = repository.transcribe(
                    apiKey = apiKey,
                    pcmChunks = audioSource.stream(
                        onStarted = {
                            serviceScope.launch {
                                if (phase == VoiceStatus.Phase.CONNECTING) {
                                    setPhase(VoiceStatus.Phase.RECORDING, "Səs yazılır")
                                }
                            }
                        },
                        onStopped = {
                            serviceScope.launch {
                                if (phase == VoiceStatus.Phase.RECORDING) {
                                    setPhase(VoiceStatus.Phase.PROCESSING, "Mətn hazırlanır")
                                }
                            }
                        },
                    ),
                )
                handleResult(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                showError("Mikrofon icazəsi işləmir")
            } catch (_: Throwable) {
                showError("Səsli yazma tamamlanmadı")
            }
        }
    }

    private fun cancelConnecting() {
        processingWatchdogJob?.cancel()
        sessionJob?.cancel()
        sessionJob = null
        audioSource.stop()
        setPhase(VoiceStatus.Phase.IDLE)
    }

    private fun stopRecording() {
        setPhase(VoiceStatus.Phase.PROCESSING, "Mətn hazırlanır")
        audioSource.stop()
        processingWatchdogJob?.cancel()
        processingWatchdogJob = serviceScope.launch {
            delay(PROCESSING_WATCHDOG_MS)
            if (phase == VoiceStatus.Phase.PROCESSING) {
                sessionJob?.cancel()
                processingWatchdogJob = null
                showError("Mətnin hazırlanma vaxtı bitdi; yenidən cəhd edin")
            }
        }
    }

    private fun handleResult(result: TranscriptionResult) {
        processingWatchdogJob?.cancel()
        processingWatchdogJob = null
        when (result) {
            is TranscriptionResult.Success -> deliverText(result.text)
            is TranscriptionResult.Failure -> showError(failureMessage(result.reason))
        }
    }

    private fun deliverText(text: String) {
        processingWatchdogJob?.cancel()
        processingWatchdogJob = null
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceTyping", text))
        val paste = VoiceAccessibilityService.pasteFromClipboard()
        val message = when (paste) {
            VoiceAccessibilityService.PasteResult.SUCCESS -> "Mətn aktiv xanaya yazıldı"
            VoiceAccessibilityService.PasteResult.PASSWORD_FIELD ->
                "Parol xanasına avtomatik yazılmadı; mətn clipboard-dadır"
            VoiceAccessibilityService.PasteResult.NO_FIELD,
            VoiceAccessibilityService.PasteResult.NOT_SUPPORTED,
            VoiceAccessibilityService.PasteResult.SERVICE_DISABLED,
            -> "Mətn clipboard-a kopyalandı"
        }
        setPhase(VoiceStatus.Phase.COPIED, message)
        serviceScope.launch {
            delay(1_400)
            if (phase == VoiceStatus.Phase.COPIED) setPhase(VoiceStatus.Phase.IDLE)
        }
    }

    private fun showError(message: String) {
        processingWatchdogJob?.cancel()
        processingWatchdogJob = null
        setPhase(VoiceStatus.Phase.ERROR, message)
        serviceScope.launch {
            delay(2_500)
            if (phase == VoiceStatus.Phase.ERROR) setPhase(VoiceStatus.Phase.IDLE)
        }
    }

    private fun failureMessage(reason: TranscriptionResult.FailureReason): String = when (reason) {
        TranscriptionResult.FailureReason.EMPTY_AUDIO -> "Yazı çox qısa oldu"
        TranscriptionResult.FailureReason.SILENCE -> "Danışıq eşidilmədi"
        TranscriptionResult.FailureReason.INVALID_API_KEY -> "Gemini API açarı etibarsızdır"
        TranscriptionResult.FailureReason.QUOTA -> "Gemini limiti bitib"
        TranscriptionResult.FailureReason.NETWORK -> "İnternet bağlantısı yoxdur"
        TranscriptionResult.FailureReason.SERVICE_UNAVAILABLE -> "Gemini xidməti müvəqqəti əlçatan deyil"
        TranscriptionResult.FailureReason.UNKNOWN -> "Transkripsiya tamamlanmadı"
    }

    private fun setPhase(newPhase: VoiceStatus.Phase, message: String? = null) {
        phase = newPhase
        statusStore.set(newPhase, message)
        orb?.setState(
            when (newPhase) {
                VoiceStatus.Phase.IDLE, VoiceStatus.Phase.COPIED -> VoiceOrbView.State.IDLE
                VoiceStatus.Phase.CONNECTING -> VoiceOrbView.State.CONNECTING
                VoiceStatus.Phase.RECORDING -> VoiceOrbView.State.RECORDING
                VoiceStatus.Phase.PROCESSING -> VoiceOrbView.State.PROCESSING
                VoiceStatus.Phase.ERROR -> VoiceOrbView.State.ERROR
            },
        )
        val notificationText = message ?: when (newPhase) {
            VoiceStatus.Phase.IDLE -> getString(R.string.notification_idle)
            VoiceStatus.Phase.CONNECTING -> "Gemini-yə qoşulur"
            VoiceStatus.Phase.RECORDING -> getString(R.string.notification_recording)
            VoiceStatus.Phase.PROCESSING -> getString(R.string.notification_processing)
            VoiceStatus.Phase.COPIED -> getString(R.string.notification_copied)
            VoiceStatus.Phase.ERROR -> getString(R.string.notification_error)
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(notificationText))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VoiceOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_voice)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Dayandır", stopIntent)
            .build()
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "voice_typing_service"
        private const val NOTIFICATION_ID = 4001
        private const val PROCESSING_WATCHDOG_MS = 30_000L
        const val ACTION_STOP = "az.fadayat.voicetyping.action.STOP"
        val running = AtomicBoolean(false)
    }
}
