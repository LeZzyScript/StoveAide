package com.example.stoveaide.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class StoveGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#E5E2DC")
        strokeWidth = 36f
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#2C8290")
        strokeWidth = 36f
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val knobBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1C1C1C")
        strokeWidth = 5f
    }

    private val rectF = RectF()

    // 0 to 100 percentage or minutes
    var progress: Float = 65f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    var maxProgress: Float = 100f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = 40f
        val diameter = min(width, height) - padding * 2
        val cx = width / 2f
        val cy = height / 2f
        val radius = diameter / 2f

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Arc starts from left-bottom (-200 degrees) and sweeps around 280 degrees
        val startAngle = 135f
        val sweepAngle = 270f

        // Draw track
        canvas.drawArc(rectF, startAngle, sweepAngle, false, trackPaint)

        // Draw progress arc
        val currentSweep = (progress / maxProgress) * sweepAngle
        canvas.drawArc(rectF, startAngle, currentSweep, false, progressPaint)

        // Calculate knob position at end of progress arc
        val knobAngleRad = Math.toRadians((startAngle + currentSweep).toDouble())
        val knobX = (cx + radius * cos(knobAngleRad)).toFloat()
        val knobY = (cy + radius * sin(knobAngleRad)).toFloat()

        // Draw knob circle
        canvas.drawCircle(knobX, knobY, 18f, knobPaint)
        canvas.drawCircle(knobX, knobY, 18f, knobBorderPaint)
    }
}
