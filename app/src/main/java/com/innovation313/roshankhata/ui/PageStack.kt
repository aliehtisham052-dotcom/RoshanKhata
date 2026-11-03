package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * A statement being made, as its pages (3 Nov 2026, the owner's mockup "Statement PDF — pages being made"): each
 * page joins the stack when the writer has really finished it ([pages], from PdfExport's own page count) —
 * dropping a little into place — and when the file is written a check arrives on the top page ([finish]). Five
 * pages at most are drawn; a longer statement keeps its count in the words beside it. With the phone's
 * animations off each state is drawn at once.
 */
class PageStack @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val dp = resources.displayMetrics.density
    private val paper = ContextCompat.getColor(context, R.color.surface)
    private val edge = ContextCompat.getColor(context, R.color.page_line)
    private val head = ContextCompat.getColor(context, R.color.brand_green_text)
    private val shade = 0x1A000000
    private val badge = ContextCompat.getColor(context, R.color.green_got_text)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 2.6f * dp
    }
    private val box = RectF()

    /** Each page slot: x, y offset in dp and a small turn, the mockup's. */
    private val slots = arrayOf(floatArrayOf(-6f, 6f, -2f), floatArrayOf(4f, 4f, 1.5f), floatArrayOf(-3f, 2f, -1f), floatArrayOf(5f, 1f, 2f), floatArrayOf(0f, 0f, 0f))
    /** How far each page has arrived (0-1). */
    private val arrived = FloatArray(slots.size)
    private val runs = arrayOfNulls<ValueAnimator>(slots.size)
    private var shown = 0
    private var done = 0f
    private var doneRun: ValueAnimator? = null

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((120 * dp).toInt(), widthMeasureSpec), resolveSize((132 * dp).toInt(), heightMeasureSpec))
    }

    /** [made] pages of the statement are finished: the ones not yet on the stack join it. */
    fun pages(made: Int) {
        val target = made.coerceIn(0, slots.size)
        val firstNew = shown
        while (shown < target) {
            val i = shown++
            if (!Motion.enabled(context)) { arrived[i] = 1f; continue }
            runs[i] = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200
                interpolator = MotionKit.EMPHASIZED_DECELERATE
                // Pages finished together still arrive one after another, 90 ms apart.
                startDelay = (i - firstNew) * 90L
                addUpdateListener { arrived[i] = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        invalidate()
    }

    /** The file is written: a check arrives on the top page. */
    fun finish() {
        if (shown == 0) pages(1)
        if (!Motion.enabled(context)) { done = 1f; invalidate(); return }
        doneRun?.cancel()
        doneRun = ValueAnimator.ofFloat(0f, 320f).apply {
            duration = 320
            interpolator = LinearInterpolator()
            addUpdateListener { done = (it.animatedValue as Float) / 320f; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        runs.forEach { it?.cancel() }
        doneRun?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = 70 * dp
        val h = 96 * dp
        val left = (width - w) / 2f
        val top = 20 * dp
        for (i in 0 until shown) {
            val k = arrived[i]
            if (k <= 0f) continue
            val s = slots[i]
            canvas.save()
            val dy = (s[1] + 16f * (1f - k)) * dp
            canvas.translate(left + s[0] * dp, top + dy)
            canvas.rotate(s[2], w / 2f, h / 2f)
            val a = (255 * k).toInt()
            // A soft edge under the page, the page, its rim.
            fill.color = shade; fill.alpha = (0x1A * k).toInt()
            box.set(0f, dp, w, h + dp)
            canvas.drawRoundRect(box, 4 * dp, 4 * dp, fill)
            fill.color = paper; fill.alpha = a
            box.set(0f, 0f, w, h)
            canvas.drawRoundRect(box, 4 * dp, 4 * dp, fill)
            stroke.color = edge; stroke.alpha = a
            canvas.drawRoundRect(box, 4 * dp, 4 * dp, stroke)
            // The page's head and its lines.
            fill.color = head; fill.alpha = a
            box.set(8 * dp, 10 * dp, 42 * dp, 16 * dp)
            canvas.drawRoundRect(box, 3 * dp, 3 * dp, fill)
            fill.color = edge; fill.alpha = a
            val lines = floatArrayOf(52f, 46f, 52f, 40f, 50f)
            for ((j, len) in lines.withIndex()) {
                box.set(8 * dp, (26 + j * 10) * dp, (8 + len) * dp, (30 + j * 10) * dp)
                canvas.drawRoundRect(box, 2 * dp, 2 * dp, fill)
            }
            canvas.restore()
        }
        if (done <= 0f) return
        // The check, at the top page's corner: the disc on the emphasized curve, the check traced after it.
        val pop = MotionKit.EMPHASIZED_DECELERATE.getInterpolation((done / 0.7f).coerceIn(0f, 1f))
        val cx = left + w - 2 * dp
        val cy = top + 4 * dp
        val r = 16 * dp * (0.6f + 0.4f * pop)
        fill.color = paper; fill.alpha = (255 * pop).toInt()
        canvas.drawCircle(cx, cy, r + 2 * dp, fill)
        fill.color = badge; fill.alpha = (255 * pop).toInt()
        canvas.drawCircle(cx, cy, r, fill)
        fill.alpha = 255
        val q = MotionKit.STANDARD.getInterpolation(((done - 0.25f) / 0.75f).coerceIn(0f, 1f))
        if (q <= 0f) return
        pen.color = paper
        val ax = cx - 6.5f * dp; val ay = cy + 0.4f * dp
        val bx = cx - 2.3f * dp; val by = cy + 4.6f * dp
        val tx = cx + 6.5f * dp; val ty = cy - 4f * dp
        val ab = Math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
        val bt = Math.hypot((tx - bx).toDouble(), (ty - by).toDouble()).toFloat()
        val d = (ab + bt) * q
        if (d <= ab) canvas.drawLine(ax, ay, ax + (bx - ax) * (d / ab), ay + (by - ay) * (d / ab), pen)
        else {
            canvas.drawLine(ax, ay, bx, by, pen)
            val k = (d - ab) / bt
            canvas.drawLine(bx, by, bx + (tx - bx) * k, by + (ty - by) * k, pen)
        }
    }
}
