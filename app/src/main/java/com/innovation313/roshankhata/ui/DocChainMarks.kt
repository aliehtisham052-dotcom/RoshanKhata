package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * A document chain's marks (3 Nov 2026, the owner's mockup "Documents — the chain"): one disc per step, evenly
 * across the width (the step's words sit under it in a row of the same weights), a line between each two. Steps
 * before [current] are done (a disc with a check), [current] is the document just made (a disc with a dot), the
 * ones after are still to come (a ring). [stepOn] plays the convert: the line into [current] fills, the step it
 * came from takes its check, and [current] arrives. Mirrored in a right-to-left language.
 */
class DocChainMarks @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val green = ContextCompat.getColor(context, R.color.brand_green_text)
    private val onGreen = ContextCompat.getColor(context, R.color.surface)
    private val waiting = ContextCompat.getColor(context, R.color.text_faint).let { (it and 0x00FFFFFF) or (0x8C shl 24) }
    private val lineBase = ContextCompat.getColor(context, R.color.page_line)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * dp }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2.6f * dp
    }

    private var count = 0
    private var current = 0
    /** The convert's progress in ms (0 to [STEP_MS]); [STEP_MS] when nothing moves. */
    private var t = STEP_MS.toFloat()
    private var run: ValueAnimator? = null

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    /**
     * [count] steps, [current] the one just made: drawn at rest, or — [playing] — as it stood before the step, for
     * [stepOn] to play from there (never the end first and then the start).
     */
    fun set(count: Int, current: Int, playing: Boolean = false) {
        this.count = count
        this.current = current.coerceIn(0, (count - 1).coerceAtLeast(0))
        run?.cancel()
        t = if (playing && this.current > 0 && Motion.enabled(context)) 0f else STEP_MS.toFloat()
        invalidate()
    }

    /** The convert into [current]: line, check, arrival — about 0.4 s; at once with the phone's animations off. */
    fun stepOn() {
        run?.cancel()
        if (current == 0 || !Motion.enabled(context)) { t = STEP_MS.toFloat(); invalidate(); return }
        run = ValueAnimator.ofFloat(t.coerceAtMost(0f), STEP_MS.toFloat()).apply {
            duration = STEP_MS
            interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        run?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((count * 56 * dp).toInt(), widthMeasureSpec),
            resolveSize((32 * dp).toInt(), heightMeasureSpec)
        )
    }

    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    /** Step [i]'s centre, from the reading side. */
    private fun x(i: Int): Float {
        val slot = width / count.toFloat()
        val fromStart = slot * (i + 0.5f)
        return if (layoutDirection == LAYOUT_DIRECTION_RTL) width - fromStart else fromStart
    }

    override fun onDraw(canvas: Canvas) {
        if (count == 0) return
        val cy = height / 2f
        val r = 12 * dp

        // The lines first, under the discs.
        val half = 1.5f * dp
        for (i in 0 until count - 1) {
            val a = x(i); val b = x(i + 1)
            val start = a + (if (b > a) r else -r)
            val end = b - (if (b > a) r else -r)
            fill.color = lineBase
            canvas.drawRect(minOf(start, end), cy - half, maxOf(start, end), cy + half, fill)
            val k = when {
                i < current - 1 -> 1f
                i == current - 1 -> MotionKit.STANDARD.getInterpolation(seg(0f, 280f))
                else -> 0f
            }
            if (k > 0f) {
                fill.color = green
                val to = start + (end - start) * k
                canvas.drawRect(minOf(start, to), cy - half, maxOf(start, to), cy + half, fill)
            }
        }

        for (i in 0 until count) {
            val cx = x(i)
            when {
                i < current -> {
                    fill.color = green
                    canvas.drawCircle(cx, cy, r, fill)
                    if (i == current - 1) {
                        // The step it came from: its dot goes, its check is drawn.
                        val dot = 1f - MotionKit.STANDARD.getInterpolation(seg(100f, 220f))
                        if (dot > 0f) { fill.color = onGreen; fill.alpha = (255 * dot).toInt(); canvas.drawCircle(cx, cy, 4.5f * dp, fill); fill.alpha = 255 }
                        check(canvas, cx, cy, MotionKit.STANDARD.getInterpolation(seg(140f, 320f)))
                    } else check(canvas, cx, cy, 1f)
                }
                i == current -> {
                    // Waiting until the line reaches it, then it arrives.
                    val pop = if (current == 0) 1f else MotionKit.EMPHASIZED_DECELERATE.getInterpolation(seg(220f, STEP_MS.toFloat()))
                    ring.color = waiting
                    if (pop < 1f) canvas.drawCircle(cx, cy, r - dp, ring)
                    fill.color = green
                    fill.alpha = (255 * pop).toInt()
                    canvas.drawCircle(cx, cy, r * (0.85f + 0.15f * pop), fill)
                    fill.color = onGreen
                    fill.alpha = (255 * pop).toInt()
                    canvas.drawCircle(cx, cy, 4.5f * dp, fill)
                    fill.alpha = 255
                }
                else -> {
                    ring.color = waiting
                    canvas.drawCircle(cx, cy, r - dp, ring)
                }
            }
        }
    }

    private fun check(canvas: Canvas, cx: Float, cy: Float, k: Float) {
        if (k <= 0f) return
        pen.color = onGreen
        val ax = cx - 5 * dp; val ay = cy + 0.4f * dp
        val bx = cx - 1.8f * dp; val by = cy + 3.6f * dp
        val tx = cx + 5 * dp; val ty = cy - 3.2f * dp
        val ab = Math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
        val bt = Math.hypot((tx - bx).toDouble(), (ty - by).toDouble()).toFloat()
        val d = (ab + bt) * k
        if (d <= ab) canvas.drawLine(ax, ay, ax + (bx - ax) * (d / ab), ay + (by - ay) * (d / ab), pen)
        else {
            canvas.drawLine(ax, ay, bx, by, pen)
            val q = (d - ab) / bt
            canvas.drawLine(bx, by, bx + (tx - bx) * q, by + (ty - by) * q, pen)
        }
    }

    companion object {
        const val STEP_MS = 440L
    }
}
