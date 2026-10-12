package com.example.multitimer.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * A hardware-accelerated fluid liquid wave animation view.
 * Renders dual sinusoidal flowing waves that rise with timer progress
 * and organically oscillate when the timer is in the RUNNING state.
 * Strictly adheres to zero rounded corners and the obsidian aesthetic.
 */
class FluidWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val wavePathFront = Path()
    private val wavePathBack = Path()

    private val paintFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paintBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // Wave parameters
    private var wavePhase = 0f
    private var waveAmplitude = 14f // px
    private var waveLength = 400f   // px

    // Progress: 0.0f (empty / bottom) to 1.0f (full / top)
    private var progress = 0f
    private var isRunning = false

    private var waveAnimator: ValueAnimator? = null

    // Palette colors (Obsidian Frosted Glass aesthetic)
    private var backWaveColor = Color.argb(45, 19, 32, 45)       // Deep translucent slate
    private var frontWaveColorTop = Color.argb(80, 107, 143, 163) // Muted slate-cyan crest
    private var frontWaveColorBottom = Color.argb(35, 14, 20, 27) // Obsidian base
    private var crestColor = Color.argb(120, 107, 143, 163)      // Luminous crest hairline

    init {
        // Density scaling
        val density = context.resources.displayMetrics.density
        waveAmplitude = 6f * density
        waveLength = 220f * density
        crestPaint.strokeWidth = 1.2f * density
    }

    /**
     * Set progress fraction between 0.0 (empty) and 1.0 (full).
     */
    fun setProgress(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (progress != clamped) {
            progress = clamped
            invalidate()
        }
    }

    /**
     * Start or pause the continuous fluid wave ripple animation.
     */
    fun setRunning(running: Boolean) {
        if (isRunning == running) return
        isRunning = running
        if (running) {
            startAnimation()
        } else {
            pauseAnimation()
        }
    }

    /**
     * Set accent color for YouTube Music or custom theme.
     */
    fun setAccentColor(isYouTube: Boolean) {
        if (isYouTube) {
            // Muted wine / warm berry fluid tint
            frontWaveColorTop = Color.argb(75, 143, 90, 98)
            frontWaveColorBottom = Color.argb(30, 28, 16, 20)
            crestColor = Color.argb(120, 180, 100, 110)
            backWaveColor = Color.argb(40, 42, 22, 28)
        } else {
            // Muted slate cyan fluid tint
            frontWaveColorTop = Color.argb(75, 107, 143, 163)
            frontWaveColorBottom = Color.argb(30, 14, 20, 27)
            crestColor = Color.argb(110, 107, 143, 163)
            backWaveColor = Color.argb(40, 19, 32, 45)
        }
        updateShaders(width, height)
        invalidate()
    }

    private fun startAnimation() {
        if (waveAnimator == null) {
            waveAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
                duration = 2400L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    wavePhase = anim.animatedValue as Float
                    invalidate()
                }
            }
        }
        if (!waveAnimator!!.isRunning) {
            waveAnimator!!.start()
        }
    }

    private fun pauseAnimation() {
        waveAnimator?.cancel()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        waveLength = (w * 0.85f).coerceAtLeast(180f)
        updateShaders(w, h)
    }

    private fun updateShaders(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return

        // Front wave gradient (translucent crest fading to obsidian bottom)
        paintFront.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            frontWaveColorTop,
            frontWaveColorBottom,
            Shader.TileMode.CLAMP
        )

        paintBack.color = backWaveColor
        crestPaint.color = crestColor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Don't draw if progress is 0 and not running
        if (progress <= 0.001f && !isRunning) return

        // Effective liquid surface height from the top
        // Minimum visible liquid base when running: 12% so the wave is always visible
        val effectiveProgress = if (isRunning) {
            progress.coerceAtLeast(0.08f)
        } else {
            progress
        }

        // baseY: height at which the liquid surface oscillates
        // (h - h * effectiveProgress) means 0 progress = bottom, 1.0 progress = top
        val baseY = h - (h * effectiveProgress)

        // 1. Draw Back Wave (offset phase, higher frequency)
        buildWavePath(
            wavePathBack,
            w,
            h,
            baseY,
            waveAmplitude * 0.75f,
            waveLength * 0.7f,
            wavePhase + 1.8f
        )
        canvas.drawPath(wavePathBack, paintBack)

        // 2. Draw Front Wave
        buildWavePath(
            wavePathFront,
            w,
            h,
            baseY,
            waveAmplitude,
            waveLength,
            wavePhase
        )
        canvas.drawPath(wavePathFront, paintFront)

        // 3. Draw Luminous Wave Crest Hairline
        canvas.drawPath(wavePathFront, crestPaint)
    }

    private fun buildWavePath(
        path: Path,
        w: Float,
        h: Float,
        baseY: Float,
        amplitude: Float,
        length: Float,
        phase: Float
    ) {
        path.reset()
        path.moveTo(0f, h) // Bottom left

        val step = 12f // px step for smooth curves
        var x = 0f
        var first = true

        val twoPi = (2 * PI).toFloat()

        while (x <= w + step) {
            val y = baseY + amplitude * sin((x / length) * twoPi + phase)
            if (first) {
                path.lineTo(0f, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            x += step
        }

        path.lineTo(w, h) // Bottom right
        path.close()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isRunning) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        waveAnimator?.cancel()
    }
}
