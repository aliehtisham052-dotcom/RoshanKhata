package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * One step's mark in a list of steps (3 Nov 2026, the owner's mockup "Restore steps"): a grey ring while it
 * waits, a green arc turning on a pale track while it runs, a green disc with a check that draws itself when
 * it is done, a red disc with a cross when it failed, a ring with a dash when there was nothing to do. Under
 * the mark, a line to the next step that fills green once this one is done. The mark sits at the top of the
 * view; the line runs to its foot, whatever height the step's words take (150% text, a long language).
 *
 * Colours are the night-aware marks: the arc and the done disc brand_green_text, the cross's disc
 * red_gave_text, the check and cross in the card's own colour (white by day, the dark card by night), so they
 * read on both. With the phone's animations off every state is drawn at once and nothing turns.
 */
class StepMark(context: Context) : View(context) {

    enum class State { WAIT, RUN, DONE, FAILED, SKIPPED }

    var state = State.WAIT
        private set

    /** Whether a line runs to the next step (not under the last one). */
    var hasLine = true
        set(v) { field = v; invalidate() }

    private val dp = resources.displayMetrics.density
    private val markSize = 24 * dp
    private val waitColor = ContextCompat.getColor(context, R.color.text_faint).let { (it and 0x00FFFFFF) or (0x8C shl 24) }
    private val track = ContextCompat.getColor(context, R.color.wash_got)
    private val green = ContextCompat.getColor(context, R.color.brand_green_text)
    private val red = ContextCompat.getColor(context, R.color.red_gave_text)
    private val onDisc = ContextCompat.getColor(context, R.color.surface)
    private val lineBase = ContextCompat.getColor(context, R.color.page_line)
    private val faint = ContextCompat.getColor(context, R.color.text_faint)

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2.6f * dp
    }
    private val box = RectF()

    /** The arrival of a done/failed disc (0-1), its check or cross (0-1), the line below (0-1), the arc's angle. */
    private var pop = 0f
    private var trace = 0f
    private var fillLine = 0f
    private var turn = 0f
    private var turning: ValueAnimator? = null
    private var arriving: ValueAnimator? = null

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize((28 * dp).toInt(), widthMeasureSpec)
        val h = resolveSize((markSize + 4 * dp).toInt(), heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    /**
     * Moves to [to]. Returns how long its arrival takes in ms (0 when it is drawn at once), so a list of steps
     * can show each in turn and never two ticks on top of one another.
     */
    fun show(to: State): Long {
        val animate = Motion.enabled(context)
        arriving?.cancel()
        if (to != State.RUN) { turning?.cancel(); turning = null }
        state = to
        when (to) {
            State.WAIT -> { pop = 0f; trace = 0f; fillLine = 0f }
            State.RUN -> {
                pop = 0f; trace = 0f; fillLine = 0f
                if (animate && turning == null) turning = ValueAnimator.ofFloat(0f, 360f).apply {
                    duration = 900
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { turn = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
            State.DONE, State.FAILED, State.SKIPPED -> {
                val total = if (to == State.DONE) ARRIVE_MS else 300L
                if (!animate) { pop = 1f; trace = 1f; fillLine = if (to == State.FAILED) 0f else 1f; invalidate(); return 0 }
                arriving = ValueAnimator.ofFloat(0f, total.toFloat()).apply {
                    duration = total
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        val ms = it.animatedValue as Float
                        // Material's tokens: the disc on the emphasized curve in 200 ms, the check traced from
                        // 60 to 280 ms, the line filled from 120 ms to the end.
                        pop = MotionKit.EMPHASIZED_DECELERATE.getInterpolation(seg(ms, 0f, 200f))
                        trace = MotionKit.STANDARD.getInterpolation(seg(ms, 60f, 280f))
                        fillLine = if (to == State.FAILED) 0f else MotionKit.STANDARD.getInterpolation(seg(ms, 120f, total.toFloat()))
                        invalidate()
                    }
                    start()
                }
                return total
            }
        }
        invalidate()
        return 0
    }

    /** Stops whatever turns or arrives; the state stays as drawn. */
    fun stop() {
        turning?.cancel(); turning = null
        arriving?.cancel(); arriving = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = markSize / 2f
        val r = 10 * dp

        if (hasLine) {
            val top = markSize + 4 * dp
            val bottom = height.toFloat()
            if (bottom > top) {
                fill.color = lineBase
                canvas.drawRect(cx - dp, top, cx + dp, bottom, fill)
                if (fillLine > 0f) {
                    fill.color = green
                    canvas.drawRect(cx - dp, top, cx + dp, top + (bottom - top) * fillLine, fill)
                }
            }
        }

        box.set(cx - r, cy - r, cx + r, cy + r)
        when (state) {
            State.WAIT -> {
                ring.color = waitColor; ring.strokeWidth = 2 * dp
                canvas.drawOval(box, ring)
            }
            State.RUN -> {
                ring.strokeWidth = 2.6f * dp
                ring.color = track
                canvas.drawOval(box, ring)
                ring.color = green
                canvas.drawArc(box, -90f + turn, 90f, false, ring)
            }
            State.SKIPPED -> {
                ring.color = waitColor; ring.strokeWidth = 2 * dp
                canvas.drawOval(box, ring)
                pen.color = faint
                pen.alpha = (255 * trace).toInt()
                canvas.drawLine(cx - 4.5f * dp, cy, cx + 4.5f * dp, cy, pen)
                pen.alpha = 255
            }
            State.DONE, State.FAILED -> {
                fill.color = if (state == State.DONE) green else red
                fill.alpha = (255 * pop).toInt()
                canvas.drawCircle(cx, cy, (markSize / 2f) * (0.7f + 0.3f * pop), fill)
                fill.alpha = 255
                pen.color = onDisc
                if (trace > 0f) {
                    if (state == State.DONE) drawCheck(canvas, cx, cy) else drawCross(canvas, cx, cy)
                }
            }
        }
    }

    /** The check, from its foot's start to the corner to its tip, traced by [trace]. */
    private fun drawCheck(canvas: Canvas, cx: Float, cy: Float) {
        val ax = cx - 5 * dp; val ay = cy + 0.4f * dp
        val bx = cx - 1.8f * dp; val by = cy + 3.6f * dp
        val tx = cx + 5 * dp; val ty = cy - 3.2f * dp
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

    /** The cross: one stroke, then the other. */
    private fun drawCross(canvas: Canvas, cx: Float, cy: Float) {
        val s = 4 * dp
        val first = (trace * 2f).coerceAtMost(1f)
        canvas.drawLine(cx - s, cy - s, cx - s + 2 * s * first, cy - s + 2 * s * first, pen)
        if (trace > 0.5f) {
            val k = (trace - 0.5f) * 2f
            canvas.drawLine(cx + s, cy - s, cx + s - 2 * s * k, cy - s + 2 * s * k, pen)
        }
    }

    companion object {
        /** A done step's arrival: the disc, the check, the line. */
        const val ARRIVE_MS = 380L
    }
}
