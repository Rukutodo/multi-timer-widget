package com.example.multitimer.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, anti-aliased Canvas renderer for circular progress rings
 * with a prominent glowing circle ending bead for Home Screen Widgets.
 * Compatible with Android RemoteViews via setImageViewBitmap.
 */
object WidgetRingRenderer {

    fun drawProgressRing(
        context: Context,
        progress: Float,
        isRunning: Boolean,
        isYouTube: Boolean = false,
        sizeDp: Int = 160,
        strokeWidthDp: Float = 6f,
        beadRadiusDp: Float = 6f
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(40)
        val strokeWidthPx = strokeWidthDp * density
        val beadRadiusPx = beadRadiusDp * density

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f
        // Inset radius so stroke and bead do not clip at bitmap edges
        val margin = (strokeWidthPx / 2f).coerceAtLeast(beadRadiusPx * 1.5f) + (2f * density)
        val radius = center - margin

        val rectF = RectF(
            center - radius,
            center - radius,
            center + radius,
            center + radius
        )

        // Palette colors
        val trackColor = Color.argb(60, 255, 255, 255) // Translucent obsidian track
        val activeAccent = if (isYouTube) {
            Color.rgb(181, 100, 112) // Muted warm berry
        } else {
            Color.rgb(107, 143, 163) // Muted slate cyan
        }

        // 1. Draw Background Track Ring
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = trackColor
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawCircle(center, center, radius, trackPaint)

        // 2. Draw Active Progress Arc
        val clampedProgress = progress.coerceIn(0f, 1f)
        val sweepAngle = clampedProgress * 360f

        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = activeAccent
            strokeCap = Paint.Cap.ROUND
        }

        // Start from 12 o'clock (-90 degrees)
        if (sweepAngle > 0.5f) {
            canvas.drawArc(rectF, -90f, sweepAngle, false, arcPaint)
        }

        // 3. Draw The "Circle Ending" Bead at the Tip of the Progress Arc
        // Angle in radians
        val tipAngleDeg = -90f + sweepAngle
        val tipAngleRad = Math.toRadians(tipAngleDeg.toDouble())

        val beadX = (center + radius * cos(tipAngleRad)).toFloat()
        val beadY = (center + radius * sin(tipAngleRad)).toFloat()

        if (clampedProgress > 0.01f || isRunning) {
            // A. Outer Glow / Pulse Halo (when running)
            if (isRunning) {
                val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(50, Color.red(activeAccent), Color.green(activeAccent), Color.blue(activeAccent))
                }
                canvas.drawCircle(beadX, beadY, beadRadiusPx * 1.9f, pulsePaint)
            }

            // B. Intermediate Halo
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(120, Color.red(activeAccent), Color.green(activeAccent), Color.blue(activeAccent))
            }
            canvas.drawCircle(beadX, beadY, beadRadiusPx * 1.35f, glowPaint)

            // C. Solid White Core Circle Bead
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }
            canvas.drawCircle(beadX, beadY, beadRadiusPx, corePaint)

            // D. Subtle Hairline Rim on Core Bead
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * density
                color = activeAccent
            }
            canvas.drawCircle(beadX, beadY, beadRadiusPx, rimPaint)
        } else {
            // Resting subtle dot at 12 o'clock when IDLE
            val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(90, 255, 255, 255)
            }
            canvas.drawCircle(beadX, beadY, beadRadiusPx * 0.7f, idlePaint)
        }

        return bitmap
    }
}
