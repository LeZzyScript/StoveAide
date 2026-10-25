package com.example.stoveaide.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class TelemetryGaugeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var value: Float = 5.0f // Scale 1.0 to 10.0
    private var maxValue: Float = 10.0f
    private var minValue: Float = 1.0f

    private var fillColor: Int = 0xFF38BDF8.toInt() // Default Cyan
    private var trackColor: Int = 0xFF2C2C2E.toInt() // Dark charcoal
    private var thumbColor: Int = 0xFFD1D5DB.toInt() // Silver knob
    private var thumbStrokeColor: Int = 0xFF6B7280.toInt()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val trackRect = RectF()
    private val fillRect = RectF()

    fun setProgress(value: Float, colorHex: Int? = null) {
        this.value = value.coerceIn(minValue, maxValue)
        if (colorHex != null) {
            this.fillColor = colorHex
        }
        invalidate()
    }

    fun setBarColor(color: Int) {
        this.fillColor = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (28 * resources.displayMetrics.density).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize)
            else -> desiredHeight
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val thumbRadius = h * 0.45f
        val trackHeight = h * 0.55f
        val trackTop = (h - trackHeight) / 2f
        val trackBottom = trackTop + trackHeight
        val cornerRadius = trackHeight / 2f

        val paddingLeftRight = thumbRadius

        // 1. Draw dark background track
        trackRect.set(paddingLeftRight, trackTop, w - paddingLeftRight, trackBottom)
        trackPaint.color = trackColor
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        // 2. Calculate progress fraction (0.0 to 1.0)
        val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        val activeWidth = (w - 2 * paddingLeftRight) * fraction

        // 3. Draw filled portion
        if (activeWidth > 0f) {
            fillRect.set(paddingLeftRight, trackTop, paddingLeftRight + activeWidth, trackBottom)
            fillPaint.color = fillColor
            canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, fillPaint)
        }

        // 4. Draw Thumb Knob
        val thumbCenterX = paddingLeftRight + activeWidth
        val thumbCenterY = h / 2f

        thumbPaint.color = thumbColor
        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadius, thumbPaint)

        thumbStrokePaint.color = thumbStrokeColor
        canvas.drawCircle(thumbCenterX, thumbCenterY, thumbRadius, thumbStrokePaint)
    }
}
