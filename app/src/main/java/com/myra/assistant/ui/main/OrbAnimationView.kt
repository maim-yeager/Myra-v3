package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class OrbAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State {
        IDLE, LISTENING, SPEAKING, THINKING, ACTIVE, OFFLINE
    }

    var state: State = State.IDLE
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var rotationAngle = 0f
    private var waveOffset = 0f
    private var pulseScale = 1f
    private var pulseAlpha = 180f

    private var rotationAnimator: ValueAnimator? = null
    private var waveAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    private val colors = mapOf(
        State.IDLE to intArrayOf(0xFFB71C1C.toInt(), 0xFF880E4F.toInt()),
        State.LISTENING to intArrayOf(0xFFFF1744.toInt(), 0xFFD500F9.toInt()),
        State.SPEAKING to intArrayOf(0xFFE040FB.toInt(), 0xFFFF1744.toInt()),
        State.THINKING to intArrayOf(0xFF40C4FF.toInt(), 0xFF00B0FF.toInt()),
        State.ACTIVE to intArrayOf(0xFFFF1744.toInt(), 0xFFD500F9.toInt()),
        State.OFFLINE to intArrayOf(0xFF2196F3.toInt(), 0xFF1565C0.toInt())
    )

    private val particles = mutableListOf<Particle>()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startAnimations()
    }

    private fun startAnimations() {
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        waveAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                waveOffset = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        pulseAnimator = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                pulseScale = it.animatedValue as Float
            }
            start()
        }

        for (i in 0 until 12) {
            particles.add(Particle(i * 30f))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = minOf(width, height) / 2f * 0.6f

        val currentColors = colors[state] ?: colors[State.IDLE]!!

        drawGlow(canvas, centerX, centerY, baseRadius, currentColors)
        drawCore(canvas, centerX, centerY, baseRadius, currentColors)

        when (state) {
            State.LISTENING, State.ACTIVE -> {
                drawRings(canvas, centerX, centerY, baseRadius)
                drawWaveRings(canvas, centerX, centerY, baseRadius, currentColors)
                if (state == State.ACTIVE) drawParticles(canvas, centerX, centerY, baseRadius)
            }
            State.SPEAKING -> {
                drawWaveRings(canvas, centerX, centerY, baseRadius, currentColors)
                drawParticles(canvas, centerX, centerY, baseRadius)
            }
            State.THINKING -> {
                drawThinkingArcs(canvas, centerX, centerY, baseRadius, currentColors)
            }
            else -> {}
        }

        drawInnerHighlight(canvas, centerX, centerY, baseRadius)
    }

    private fun drawGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, colors: IntArray) {
        glowPaint.shader = RadialGradient(cx, cy, radius * 1.6f, colors[0], Color.TRANSPARENT, Shader.TileMode.CLAMP)
        glowPaint.alpha = pulseAlpha.toInt()
        canvas.drawCircle(cx, cy, radius * 1.6f * pulseScale, glowPaint)
    }

    private fun drawCore(canvas: Canvas, cx: Float, cy: Float, radius: Float, colors: IntArray) {
        paint.shader = RadialGradient(
            cx, cy, radius * pulseScale,
            colors[0], colors[1],
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * pulseScale, paint)
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.shader = null
        paint.color = 0x80FFFFFF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f

        for (i in 0 until 3) {
            val angle = rotationAngle + i * 120f
            val rect = RectF(
                cx - radius * 1.3f,
                cy - radius * 1.3f,
                cx + radius * 1.3f,
                cy + radius * 1.3f
            )
            canvas.drawArc(rect, angle, 60f, false, paint)
        }
    }

    private fun drawWaveRings(canvas: Canvas, cx: Float, cy: Float, radius: Float, colors: IntArray) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f

        for (ring in 1..3) {
            val ringRadius = radius * (1 + ring * 0.25f)
            paint.color = Color.argb(200 - ring * 50, Color.red(colors[0]), Color.green(colors[0]), Color.blue(colors[0]))

            val path = Path()
            for (i in 0..360 step 5) {
                val angle = Math.toRadians((i + waveOffset * ring).toDouble())
                val waveAmplitude = if (state == State.SPEAKING) 10f else 5f
                val r = ringRadius + sin(angle * 2) * waveAmplitude
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawThinkingArcs(canvas: Canvas, cx: Float, cy: Float, radius: Float, colors: IntArray) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = colors[0]

        val rect1 = RectF(cx - radius * 0.8f, cy - radius * 0.8f, cx + radius * 0.8f, cy + radius * 0.8f)
        canvas.drawArc(rect1, rotationAngle, 90f, false, paint)

        val rect2 = RectF(cx - radius * 0.5f, cy - radius * 0.5f, cx + radius * 0.5f, cy + radius * 0.5f)
        canvas.drawArc(rect2, -rotationAngle + 45, 90f, false, paint)
    }

    private fun drawParticles(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE

        for (particle in particles) {
            val angle = Math.toRadians((rotationAngle + particle.angle).toDouble())
            val distance = radius * 1.4f
            val x = cx + (distance * cos(angle)).toFloat()
            val y = cy + (distance * sin(angle)).toFloat()
            canvas.drawCircle(x, y, 4f, paint)
        }
    }

    private fun drawInnerHighlight(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        paint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 0.4f,
            Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        paint.alpha = 100
        canvas.drawCircle(cx - radius * 0.3f, cy - radius * 0.3f, radius * 0.3f, paint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotationAnimator?.cancel()
        waveAnimator?.cancel()
        pulseAnimator?.cancel()
    }

    data class Particle(var angle: Float)
}