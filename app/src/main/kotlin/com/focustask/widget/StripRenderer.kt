package com.focustask.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.focustask.core.TimerBar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Draws the sun strip into a bitmap, because widgets can't draw custom shapes. */
object StripRenderer {
    fun render(bar: TimerBar, widthPx: Int, heightPx: Int, bubble: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val markerR = heightPx / 2f - density
        val left = markerR
        val right = widthPx - markerR
        val cy = heightPx / 2f
        val sunX = left + (right - left) * bar.sun.toFloat().coerceIn(0f, 1f)
        val bigR = 5.5f * density
        val smallR = 2.8f * density
        for (i in 0..12) {
            val x = left + (right - left) * i / 12f
            val large = i in bar.largeBubbles
            val r = if (large) bigR else smallR
            val color = if (large) bubble else withAlpha(bubble, 0.65f)
            if (x <= sunX) {
                fill.color = color
                c.drawCircle(x, cy, r, fill)
            } else {
                val sw = (if (large) 2f else 1.4f) * density
                ring.strokeWidth = sw
                ring.color = color
                c.drawCircle(x, cy, r - sw / 2, ring)
            }
        }
        // the sun
        fill.color = Color.parseColor("#FFF4DB")
        c.drawCircle(sunX, cy, markerR, fill)
        ring.color = Color.parseColor("#D9930A")
        ring.strokeWidth = 2f * density
        c.drawCircle(sunX, cy, markerR - density, ring)
        ring.strokeWidth = 1.5f * density
        c.drawCircle(sunX, cy, markerR * 0.28f, ring)
        ring.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) {
            val a = i * PI / 4
            val from = markerR * 0.5f
            val to = markerR * 0.7f
            c.drawLine(
                sunX + cos(a).toFloat() * from, cy + sin(a).toFloat() * from,
                sunX + cos(a).toFloat() * to, cy + sin(a).toFloat() * to, ring,
            )
        }
        return bmp
    }

    private fun withAlpha(color: Int, a: Float): Int =
        Color.argb((a * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
