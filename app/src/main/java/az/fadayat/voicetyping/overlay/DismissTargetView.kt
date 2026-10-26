package az.fadayat.voicetyping.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import az.fadayat.voicetyping.R
import kotlin.math.hypot

class DismissTargetView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4f * density
    }
    private var highlighted = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.orb_dismiss_target)
        elevation = 16f * density
    }

    fun setHighlighted(value: Boolean) {
        if (highlighted == value) return
        highlighted = value
        scaleX = if (value) 1.12f else 1f
        scaleY = if (value) 1.12f else 1f
        invalidate()
    }

    fun containsRawPoint(rawX: Float, rawY: Float): Boolean {
        if (width == 0 || height == 0) return false
        val location = IntArray(2)
        getLocationOnScreen(location)
        return containsPoint(
            rawX = rawX,
            rawY = rawY,
            left = location[0].toFloat(),
            top = location[1].toFloat(),
            width = width.toFloat(),
            height = height.toFloat(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) * 0.4f
        circlePaint.color = if (highlighted) {
            Color.rgb(196, 48, 56)
        } else {
            Color.argb(220, 52, 54, 62)
        }
        circlePaint.setShadowLayer(8f * density, 0f, 3f * density, 0x66000000)
        setLayerType(LAYER_TYPE_SOFTWARE, circlePaint)
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        val arm = radius * 0.38f
        canvas.drawLine(centerX - arm, centerY - arm, centerX + arm, centerY + arm, xPaint)
        canvas.drawLine(centerX + arm, centerY - arm, centerX - arm, centerY + arm, xPaint)
    }

    companion object {
        internal fun containsPoint(
            rawX: Float,
            rawY: Float,
            left: Float,
            top: Float,
            width: Float,
            height: Float,
        ): Boolean {
            val centerX = left + width / 2f
            val centerY = top + height / 2f
            val hitRadius = minOf(width, height) * 0.55f
            return hypot(rawX - centerX, rawY - centerY) <= hitRadius
        }
    }
}
