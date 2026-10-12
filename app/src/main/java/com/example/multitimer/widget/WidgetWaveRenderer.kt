package com.example.multitimer.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-performance fluid liquid wave Canvas renderer for Home Screen Widgets.
 * Generates an optimized, anti-aliased bitmap featuring dual sinusoidal waves
 * that rise with countdown progress and ripple when the timer is RUNNING.
 * Strictly adheres to RemoteViews constraints and the obsidian aesthetic.
 */
object WidgetWaveRenderer {

    fun drawWave(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        progress: Float,
        isRunning: Boolean,
        isYouTube: Boolean = false,
        phase: Float = 0f
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        // Keep dimensions optimized for RemoteViews Binder IPC payload (< 300KB)
        val w = (widthDp * density).toInt().coerceIn(120, 360)
        val h = (heightDp * density).toInt().coerceIn(40, 260)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // If not running and zero progress, leave transparent
        if (progress <= 0.001f && !isRunning) {
            return bitmap
        }

        val clampedProgress = progress.coerceIn(0f, 1f)
        val effectiveProgress = if (isRunning) {
            clampedProgress.coerceAtLeast(0.10f)
        } else {
            clampedProgress
        }

        val baseY = h.toFloat() - (h.toFloat() * effectiveProgress)

        val waveAmplitude = 5.5f * density
        val waveLength = (w * 0.85f).coerceAtLeast(140f * density)

        // Color definitions
        val activeAccentTop = if (isYouTube) {
            Color.argb(80, 181, 100, 112) // Muted warm berry crest
        } else {
            Color.argb(80, 107, 143, 163) // Muted slate-cyan crest
        }

        val activeAccentBottom = if (isYouTube) {
            Color.argb(32, 28, 16, 20)
        } else {
            Color.argb(32, 14, 20, 27)
        }

        val backWaveColor = if (isYouTube) {
            Color.argb(45, 42, 22, 28)
        } else {
            Color.argb(45, 19, 32, 45)
        }

        val crestColor = if (isYouTube) {
            Color.argb(130, 190, 110, 120)
        } else {
            Color.argb(130, 107, 143, 163)
        }

        // 1. Draw Back Wave
        val backPath = Path()
        buildWavePath(
            backPath,
            w.toFloat(),
            h.toFloat(),
            baseY,
            waveAmplitude * 0.75f,
            waveLength * 0.7f,
            phase + 1.8f
        )
        val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = backWaveColor
        }
        canvas.drawPath(backPath, backPaint)

        // 2. Draw Front Wave with Vertical Gradient
        val frontPath = Path()
        buildWavePath(
            frontPath,
            w.toFloat(),
            h.toFloat(),
            baseY,
            waveAmplitude,
            waveLength,
            phase
        )
        val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                0f, baseY,
                0f, h.toFloat(),
                activeAccentTop,
                activeAccentBottom,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(frontPath, frontPaint)

        // 3. Draw Luminous Wave Crest Hairline
        val crestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = crestColor
        }
        canvas.drawPath(frontPath, crestPaint)

        return bitmap
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

        val step = 14f // px step for smooth curves
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
}
