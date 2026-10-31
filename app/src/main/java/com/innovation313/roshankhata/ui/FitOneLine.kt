package com.innovation313.roshankhata.ui

import android.text.TextPaint
import android.util.TypedValue
import android.widget.TextView
import com.innovation313.roshankhata.R

/**
 * A figure that must show whole on one line, in a row whose width it shares (31 Oct 2026, the review: at 130-150%
 * text Home's net balance broke after "Rs" and showed only that). A box with a width of its own takes
 * `app:autoSizeTextType="uniform"` in its layout instead; this is for a figure sized by its words beside a marker
 * (Home's balance and its eye), where autosize has no width to fit.
 */
object FitOneLine {

    /**
     * Shrinks [tv]'s text from the size it was laid out at until all of it fits in [available] pixels, never below
     * [minScale] of that size; a shorter text grows back. Call it after the text is set, once the row is measured.
     */
    fun fit(tv: TextView, available: Int, minScale: Float = 0.6f) {
        if (available <= 0) return
        val base = (tv.getTag(R.id.fit_one_line_base) as? Float) ?: tv.textSize.also { tv.setTag(R.id.fit_one_line_base, it) }
        val text = tv.text?.toString().orEmpty()
        val paint = TextPaint(tv.paint)
        val room = available - tv.compoundPaddingLeft - tv.compoundPaddingRight
        var size = base
        paint.textSize = size
        while (size > base * minScale && paint.measureText(text) > room) {
            size *= 0.94f
            paint.textSize = size
        }
        if (kotlin.math.abs(size - tv.textSize) > 0.5f) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }
}
