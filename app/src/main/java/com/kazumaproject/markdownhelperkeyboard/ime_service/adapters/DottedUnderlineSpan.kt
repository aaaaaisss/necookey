package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

/**
 * Dotted underline for a low-confidence bunsetsu ("迷い印") in the two-row candidate bar.
 * Draws the text unchanged and a row of dots in the text color just below the baseline.
 */
class DottedUnderlineSpan : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (text == null) return 0
        if (fm != null) paint.getFontMetricsInt(fm)
        return paint.measureText(text, start, end).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        if (text == null) return
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
        val width = paint.measureText(text, start, end)
        val radius = (paint.textSize / 22f).coerceAtLeast(1f)
        val step = radius * 3.5f
        val dotY = (y + paint.descent() * 0.55f).coerceAtMost(bottom - radius)
        val oldStyle = paint.style
        paint.style = Paint.Style.FILL
        var cx = x + radius
        while (cx <= x + width - radius) {
            canvas.drawCircle(cx, dotY, radius, paint)
            cx += step
        }
        paint.style = oldStyle
    }
}
