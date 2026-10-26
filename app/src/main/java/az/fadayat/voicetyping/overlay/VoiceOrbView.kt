package az.fadayat.voicetyping.overlay

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import az.fadayat.voicetyping.R
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
class VoiceOrbView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val onTap: () -> Unit,
    private val onDragStarted: () -> Unit,
    private val onDragged: (rawX: Float, rawY: Float) -> Unit,
    private val onDragEnded: (rawX: Float, rawY: Float, cancelled: Boolean) -> Unit,
) : View(context) {
    enum class State {
        IDLE,
        CONNECTING,
        RECORDING,
        PROCESSING,
        ERROR,
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = Color.WHITE
        alpha = 210
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    private var orbState = State.IDLE
    private var pulse = 0f
    private var downRawX = 0f
    private var downRawY = 0f
    private var initialX = 0
    private var initialY = 0
    private var dragging = false

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        elevation = 12f * resources.displayMetrics.density
        setState(State.IDLE)
    }

    fun setState(state: State) {
        orbState = state
        contentDescription = when (state) {
            State.IDLE, State.ERROR -> context.getString(R.string.orb_start)
            State.CONNECTING, State.PROCESSING -> context.getString(R.string.orb_processing)
            State.RECORDING -> context.getString(R.string.orb_stop)
        }
        if (state == State.RECORDING || state == State.PROCESSING || state == State.CONNECTING) {
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
            pulse = 0f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = minOf(width, height) * 0.36f
        val radius = baseRadius * (1f + pulse * 0.08f)
        fillPaint.color = when (orbState) {
            State.IDLE -> Color.rgb(53, 92, 168)
            State.CONNECTING, State.PROCESSING -> Color.rgb(190, 119, 25)
            State.RECORDING -> Color.rgb(190, 50, 55)
            State.ERROR -> Color.rgb(151, 40, 45)
        }
        fillPaint.setShadowLayer(8f * resources.displayMetrics.density, 0f, 3f, 0x55000000)
        setLayerType(LAYER_TYPE_SOFTWARE, fillPaint)
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        canvas.drawCircle(centerX, centerY, radius - ringPaint.strokeWidth, ringPaint)
        drawMicrophone(canvas, centerX, centerY, radius)
    }

    private fun drawMicrophone(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val micWidth = radius * 0.36f
        val micHeight = radius * 0.72f
        val body = RectF(x - micWidth, y - micHeight * 0.65f, x + micWidth, y + micHeight * 0.35f)
        canvas.drawRoundRect(body, micWidth, micWidth, iconPaint)
        val arc = RectF(x - radius * 0.56f, y - radius * 0.1f, x + radius * 0.56f, y + radius * 0.62f)
        canvas.drawArc(arc, 0f, 180f, false, iconPaint)
        canvas.drawLine(x, y + radius * 0.62f, x, y + radius * 0.88f, iconPaint)
        canvas.drawLine(x - radius * 0.25f, y + radius * 0.88f, x + radius * 0.25f, y + radius * 0.88f, iconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                initialX = layoutParams.x
                initialY = layoutParams.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = hypot(event.rawX - downRawX, event.rawY - downRawY)
                if (!dragging && distance >= dragThresholdPx) {
                    dragging = true
                    onDragStarted()
                }
                layoutParams.x = initialX + (event.rawX - downRawX).toInt()
                layoutParams.y = initialY + (event.rawY - downRawY).toInt()
                windowManager.updateViewLayout(this, layoutParams)
                if (dragging) onDragged(event.rawX, event.rawY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    onDragEnded(event.rawX, event.rawY, false)
                } else {
                    performClick()
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnded(event.rawX, event.rawY, true)
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private val dragThresholdPx: Float
        get() = 12f * resources.displayMetrics.density
}
