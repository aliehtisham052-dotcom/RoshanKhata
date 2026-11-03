package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * A result's mark (3 Nov 2026, the owner's mockup "Close the day — the difference"): a disc in the result's own
 * colour with a glyph that draws itself — a check when the drawer matches the books, a minus when it is short,
 * a plus when it holds more. [show] pops the disc and traces the glyph again only when the result changes kind,
 * so typing figure by figure does not flicker it. With the phone's animations off it is drawn at once.
 */
class ResultMark @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Glyph { CHECK, MINUS, PLUS }

    private val dp = resources.displayMetrics.density
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2.6f * dp
    }
    private var glyph: Glyph? = null
    private var pop = 1f
    private var trace = 1f
    private var run: ValueAnimator? = null

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    /** [ink] the disc, [onInk] the glyph on it. */
    fun show(to: Glyph, ink: Int, onInk: Int) {
        val changed = to != glyph
        glyph = to
        disc.color = ink
        pen.color = onInk
        if (!changed) { invalidate(); return }
        run?.cancel()
        if (!Motion.enabled(context)) { pop = 1f; trace = 1f; invalidate(); return }
        run = ValueAnimator.ofFloat(0f, 380f).apply {
            duration = 380
            interpolator = LinearInterpolator()
            addUpdateListener {
                val ms = it.animatedValue as Float
                // The mockup's times: the disc from 0.8 of its size on the emphasized curve (0-180 ms), the glyph
                // traced from 80 to 320 ms.
                pop = MotionKit.EMPHASIZED_DECELERATE.getInterpolation(seg(ms, 0f, 180f))
                trace = MotionKit.STANDARD.getInterpolation(seg(ms, 80f, 320f))
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        run?.cancel()
        super.onDetachedFromWindow()
    }

    private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        val g = glyph ?: return
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        val a = disc.alpha
        disc.alpha = (255 * pop).toInt()
        canvas.drawCircle(cx, cy, r * (0.8f + 0.2f * pop), disc)
        disc.alpha = a
        if (trace <= 0f) return
        val s = r * 0.38f
        when (g) {
            Glyph.CHECK -> {
                val ax = cx - s; val ay = cy + 0.05f * r
                val bx = cx - 0.3f * s; val by = cy + 0.65f * s
                val tx = cx + s; val ty = cy - 0.55f * s
                val ab = Math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
                val bt = Math.hypot((tx - bx).toDouble(), (ty - by).toDouble()).toFloat()
                val d = (ab + bt) * trace
                if (d <= ab) canvas.drawLine(ax, ay, ax + (bx - ax) * (d / ab), ay + (by - ay) * (d / ab), pen)
                else {
                    canvas.drawLine(ax, ay, bx, by, pen)
                    val k = (d - ab) / bt
                    canvas.drawLine(bx, by, bx + (tx - bx) * k, by + (ty - by) * k, pen)
                }
            }
            Glyph.MINUS -> canvas.drawLine(cx - s, cy, cx - s + 2 * s * trace, cy, pen)
            Glyph.PLUS -> {
                val first = (trace * 2f).coerceAtMost(1f)
                canvas.drawLine(cx - s, cy, cx - s + 2 * s * first, cy, pen)
                if (trace > 0.5f) {
                    val k = (trace - 0.5f) * 2f
                    canvas.drawLine(cx, cy - s, cx, cy - s + 2 * s * k, pen)
                }
            }
        }
    }
}
