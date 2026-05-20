package com.myra.assistant.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barCount = 20
    private val barHeights = FloatArray(barCount) { 0f }
    private val targetHeights = FloatArray(barCount) { 0f }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var isAnimating = false
    private var currentAmplitude = 0f

    fun setAmplitude(amplitude: Float) {
        currentAmplitude = amplitude.coerceIn(0f, 1f)
        updateTargetHeights()
    }

    private fun updateTargetHeights() {
        for (i in 0 until barCount) {
            val centerIndex = barCount / 2
            val distance = kotlin.math.abs(i - centerIndex)
            val falloff = 1f - (distance.toFloat() / centerIndex) * 0.6f
            targetHeights[i] = currentAmplitude * (0.3f + Math.random().toFloat() * 0.7f) * falloff
        }
    }

    fun startAnimation() {
        isAnimating = true
        invalidate()
    }

    fun stopAnimation() {
        isAnimating = false
        for (i in 0 until barCount) {
            targetHeights[i] = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isAnimating) {
            for (i in 0 until barCount) {
                barHeights[i] = barHeights[i] + (0f - barHeights[i]) * 0.3f
            }
        } else {
            for (i in 0 until barCount) {
                barHeights[i] = barHeights[i] + (targetHeights[i] - barHeights[i]) * 0.3f
            }
        }

        val barWidth = width.toFloat() / barCount * 0.7f
        val spacing = width.toFloat() / barCount

        for (i in 0 until barCount) {
            val x = i * spacing + (spacing - barWidth) / 2
            val barHeight = barHeights[i] * height
            val y = (height - barHeight) / 2

            val alpha = (150 + (barHeights[i] * 105)).toInt().coerceIn(150, 255)
            paint.color = (0xFF000000 or 0xFF1744) and 0x00FFFFFF or (alpha shl 24)
            paint.alpha = alpha

            canvas.drawRoundRect(
                x, y,
                x + barWidth, y + barHeight,
                barWidth / 2, barWidth / 2,
                paint
            )
        }

        if (isAnimating) {
            postInvalidateOnAnimation()
        }
    }
}