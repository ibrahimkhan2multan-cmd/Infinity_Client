package com.infinity.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/** Draws the main-menu logo for a custom client name (same size as the bundled default logo). */
object LogoRenderer {
    const val WIDTH = 1768
    const val HEIGHT = 333

    fun render(name: String): ByteArray {
        val text = name.uppercase()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 200f
            }
            val maxWidth = WIDTH * 0.92f
            val measured = paint.measureText(text)
            if (measured > maxWidth) paint.textSize = paint.textSize * maxWidth / measured
            val x = (WIDTH - paint.measureText(text)) / 2f
            val metrics = paint.fontMetrics
            val y = HEIGHT / 2f - (metrics.ascent + metrics.descent) / 2f

            val outline = Paint(paint).apply {
                style = Paint.Style.STROKE
                strokeWidth = 16f
                strokeJoin = Paint.Join.ROUND
                color = 0xFF140828.toInt()
            }
            val glow = Paint(outline).apply {
                strokeWidth = 30f
                color = 0x66B04BFF
            }
            canvas.drawText(text, x, y, glow)
            canvas.drawText(text, x, y, outline)
            paint.shader = LinearGradient(
                0f, y + metrics.ascent, 0f, y + metrics.descent,
                0xFFF4E8FF.toInt(), 0xFFB04BFF.toInt(), Shader.TileMode.CLAMP
            )
            canvas.drawText(text, x, y, paint)

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
