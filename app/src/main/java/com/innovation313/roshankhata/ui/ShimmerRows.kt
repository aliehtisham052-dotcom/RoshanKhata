package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * Placeholder customer rows with a light sweeping across them, shown while the
 * ledger loads (5 Oct) instead of a blank page. Same shape as a real row —
 * avatar, name, phone line, amount — mirrored in right-to-left languages, in
 * colours that have night versions. With phone animations off the rows stay
 * still; nothing animates while the view is hidden.
 */
class ShimmerRows @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.shimmer_base) }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlight = ContextCompat.getColor(context, R.color.shimmer_highlight)
    private var phase = 0f
    private val r = RectF()

    private val sweep = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    private fun run(on: Boolean) {
        if (on && Motion.enabled(context) && isAttachedToWindow) { if (!sweep.isStarted) sweep.start() } else sweep.cancel()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); run(visibility == VISIBLE) }
    override fun onDetachedFromWindow() { sweep.cancel(); super.onDetachedFromWindow() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); run(isVisible) }

    private fun shapes(c: Canvas, p: Paint) {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val w = width.toFloat()
        fun x(start: Float, len: Float) = if (rtl) w - start - len else start
        val rowH = 74 * dp
        val pad = 16 * dp
        var top = 8 * dp
        while (top + rowH <= height + rowH) {
            val cy = top + rowH / 2
            val av = 22 * dp
            c.drawCircle(x(pad, 2 * av) + av, cy, av, p)
            val textStart = pad + 2 * av + 14 * dp
            r.set(x(textStart, w * 0.42f), cy - 14 * dp, x(textStart, w * 0.42f) + w * 0.42f, cy - 2 * dp)
            c.drawRoundRect(r, 6 * dp, 6 * dp, p)
            r.set(x(textStart, w * 0.26f), cy + 6 * dp, x(textStart, w * 0.26f) + w * 0.26f, cy + 16 * dp)
            c.drawRoundRect(r, 5 * dp, 5 * dp, p)
            val amtW = w * 0.2f
            r.set(x(w - pad - amtW, amtW), cy - 8 * dp, x(w - pad - amtW, amtW) + amtW, cy + 8 * dp)
            c.drawRoundRect(r, 6 * dp, 6 * dp, p)
            top += rowH
        }
    }

    override fun onDraw(canvas: Canvas) {
        shapes(canvas, base)
        if (!sweep.isRunning) return
        val band = width * 0.45f
        val at = -band + phase * (width + 2 * band)
        val clear = highlight and 0x00FFFFFF
        shine.shader = LinearGradient(at, 0f, at + band, 0f, intArrayOf(clear, highlight, clear), null, Shader.TileMode.CLAMP)
        shapes(canvas, shine)
    }
}
