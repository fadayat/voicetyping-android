package az.fadayat.voicetyping.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import az.fadayat.voicetyping.R

internal class VoiceImeView(
    context: Context,
    private val onRecord: () -> Unit,
    onSwitchBack: () -> Unit,
    onSettings: () -> Unit,
) : LinearLayout(context) {
    private val status = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(12))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val recordButton = Button(context).apply {
        minHeight = dp(56)
        setOnClickListener { onRecord() }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.rgb(27, 29, 33))
            cornerRadii = floatArrayOf(dp(20f), dp(20f), dp(20f), dp(20f), 0f, 0f, 0f, 0f)
        }

        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(recordButton, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        actions.addView(
            Button(context).apply {
                text = context.getString(R.string.ime_switch_back)
                minHeight = dp(48)
                setOnClickListener { onSwitchBack() }
            },
            LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(4) },
        )
        actions.addView(
            Button(context).apply {
                text = context.getString(R.string.ime_settings)
                minHeight = dp(48)
                setOnClickListener { onSettings() }
            },
            LayoutParams(0, dp(52), 1f).apply { marginStart = dp(4) },
        )
        addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        render(VoiceImeState.IDLE)
    }

    fun render(state: VoiceImeState, message: String? = null) {
        status.text = message ?: when (state) {
            VoiceImeState.IDLE -> context.getString(R.string.ime_ready)
            VoiceImeState.CONNECTING -> context.getString(R.string.ime_connecting)
            VoiceImeState.RECORDING -> context.getString(R.string.ime_recording)
            VoiceImeState.PROCESSING -> context.getString(R.string.ime_processing)
            VoiceImeState.SUCCESS -> "Mətn yazıldı"
            VoiceImeState.ERROR -> "Yenidən cəhd et"
            VoiceImeState.PASSWORD_BLOCKED -> "Parol xanasında səsli yazı bloklanır"
        }
        recordButton.text = when (state) {
            VoiceImeState.RECORDING -> "Dayandır"
            VoiceImeState.CONNECTING -> "Ləğv et"
            else -> "Danış"
        }
        recordButton.contentDescription = when (state) {
            VoiceImeState.RECORDING -> "Səs yazısını dayandır"
            VoiceImeState.CONNECTING -> "Gemini bağlantısını ləğv et"
            else -> "Səs yazısını başlat"
        }
        recordButton.isEnabled = state !in setOf(
            VoiceImeState.PROCESSING,
            VoiceImeState.PASSWORD_BLOCKED,
        )
        status.announceForAccessibility(status.text)
    }

    private fun dp(value: Int): Int = dp(value.toFloat()).toInt()

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}

internal enum class VoiceImeState {
    IDLE,
    CONNECTING,
    RECORDING,
    PROCESSING,
    SUCCESS,
    ERROR,
    PASSWORD_BLOCKED,
}
