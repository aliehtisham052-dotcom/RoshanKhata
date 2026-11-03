package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * Live sync's two roads, drawn (3 Nov 2026, the owner's mockup "Live sync — dots only when data moves"): this phone
 * at the start, the computer at the end, the shop's WiFi straight between them and the relay over a cloud above.
 * The road in use is green; the other is a grey dash. Three gold dots travel a road only when a round really
 * carried changes ([carry], from LiveRunner.carried) — out from the phone, in from the computer — and the device
 * they reach gives one soft ring. When neither road answers, both roads are grey dashes and the cloud wears a red
 * cross. Nothing moves for a quiet round; with the phone's animations off nothing moves at all.
 */
class LiveTrafficView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Road { WIFI, RELAY, DOWN, IDLE }

    var road = Road.IDLE
        set(v) { if (field != v) { field = v; invalidate() } }

    private val dp = resources.displayMetrics.density
    private val green = ContextCompat.getColor(context, R.color.brand_green_text)
    private val grey = ContextCompat.getColor(context, R.color.text_faint).let { (it and 0x00FFFFFF) or (0x8C shl 24) }
    private val red = ContextCompat.getColor(context, R.color.red_gave_text)
    private val gold = ContextCompat.getColor(context, R.color.brand_gold)
    private val card = ContextCompat.getColor(context, R.color.surface)
    private val ink = ContextCompat.getColor(context, R.color.ink)

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 2.4f * dp }
    private val dash = DashPathEffect(floatArrayOf(5 * dp, 6 * dp), 0f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val wifiPath = Path()
    private val relayPath = Path()
    private val measure = PathMeasure()
    private val at = FloatArray(2)

    /** The dots' run: which road, which way, and how far in ms (0 to [RUN_MS]); null when nothing moves. */
    private var run: ValueAnimator? = null
    private var runRelay = false
    private var runOut = true
    private var t = -1f
    /** Runs waiting their turn (a round that carried both ways: out first, then in), relay to out; two at most. */
    private val queued = ArrayDeque<Pair<Boolean, Boolean>>()
    private val cloud = Path()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize((240 * dp).toInt(), widthMeasureSpec), resolveSize((72 * dp).toInt(), heightMeasureSpec))
    }

    private val rtl get() = layoutDirection == LAYOUT_DIRECTION_RTL
    private fun phoneX() = if (rtl) width - 22 * dp else 22 * dp
    private fun pcX() = if (rtl) 34 * dp else width - 34 * dp
    private val roadY get() = 48 * dp

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val a = phoneX(); val b = pcX()
        val dir = if (b > a) 1f else -1f
        wifiPath.reset()
        wifiPath.moveTo(a + dir * 16 * dp, roadY)
        wifiPath.lineTo(b - dir * 26 * dp, roadY)
        relayPath.reset()
        relayPath.moveTo(a, roadY - 20 * dp)
        relayPath.cubicTo(a + dir * (w * 0.22f), 2 * dp, b - dir * (w * 0.22f), 2 * dp, b, roadY - 16 * dp)
        val cx = w / 2f
        val cy = 12 * dp
        // One outline for the three puffs (their union), so no puff's edge shows inside another.
        val left = Path().apply { addCircle(cx - 7 * dp, cy + 2 * dp, 6 * dp, Path.Direction.CW) }
        val top = Path().apply { addCircle(cx + 1 * dp, cy - 2 * dp, 8 * dp, Path.Direction.CW) }
        val right = Path().apply { addCircle(cx + 9 * dp, cy + 3 * dp, 5 * dp, Path.Direction.CW) }
        cloud.reset()
        cloud.op(left, top, Path.Op.UNION)
        cloud.op(right, Path.Op.UNION)
    }

    /** A round carried changes: [out] this phone's went, [came] the other device's arrived, by [relay] or WiFi. */
    fun carry(out: Boolean, came: Boolean, relay: Boolean) {
        if (!Motion.enabled(context) || !isAttachedToWindow || (!out && !came)) return
        val runs = listOfNotNull(if (out) relay to true else null, if (came) relay to false else null)
        runs.forEach { queued.addLast(it) }
        while (queued.size > 2) queued.removeFirst()
        if (run?.isRunning != true) queued.removeFirstOrNull()?.let { start(it.first, it.second) }
    }

    private fun start(relay: Boolean, out: Boolean) {
        runRelay = relay
        runOut = out
        // Started only when nothing runs (carry) or from the last run's end: never over a run still going.
        run = ValueAnimator.ofFloat(0f, RUN_MS.toFloat()).apply {
            duration = RUN_MS
            interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    t = -1f
                    invalidate()
                    val next = queued.removeFirstOrNull()
                    if (next != null && isAttachedToWindow) start(next.first, next.second)
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        queued.clear()
        run?.cancel()
        super.onDetachedFromWindow()
    }

    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        val down = road == Road.DOWN
        // The roads: the one in use green and whole, the other a grey dash.
        line.strokeWidth = 2.4f * dp
        line.color = if (road == Road.RELAY) green else grey
        line.pathEffect = if (road == Road.RELAY) null else dash
        canvas.drawPath(relayPath, line)
        line.color = if (road == Road.WIFI) green else grey
        line.pathEffect = if (road == Road.WIFI) null else dash
        canvas.drawPath(wifiPath, line)
        line.pathEffect = null

        // The cloud at the top of the relay's road, with a red cross when no road answers.
        val cx = width / 2f
        val cy = 12 * dp
        line.strokeWidth = 2 * dp
        line.color = if (road == Road.RELAY) green else grey
        fill.color = card
        canvas.drawPath(cloud, fill)
        canvas.drawPath(cloud, line)
        if (down) {
            line.color = red
            line.strokeWidth = 2.4f * dp
            canvas.drawLine(cx - 4 * dp, cy - 3 * dp, cx + 4 * dp, cy + 5 * dp, line)
            canvas.drawLine(cx + 4 * dp, cy - 3 * dp, cx - 4 * dp, cy + 5 * dp, line)
        }

        // The two devices.
        val px = phoneX()
        line.strokeWidth = 2.2f * dp
        line.color = ink
        fill.color = card
        box.set(px - 11 * dp, roadY - 19 * dp, px + 11 * dp, roadY + 19 * dp)
        canvas.drawRoundRect(box, 5 * dp, 5 * dp, fill)
        canvas.drawRoundRect(box, 5 * dp, 5 * dp, line)
        canvas.drawLine(px - 4 * dp, roadY + 13 * dp, px + 4 * dp, roadY + 13 * dp, line)
        val mx = pcX()
        box.set(mx - 24 * dp, roadY - 16 * dp, mx + 24 * dp, roadY + 12 * dp)
        canvas.drawRoundRect(box, 3 * dp, 3 * dp, fill)
        canvas.drawRoundRect(box, 3 * dp, 3 * dp, line)
        canvas.drawLine(mx - 8 * dp, roadY + 19 * dp, mx + 8 * dp, roadY + 19 * dp, line)
        canvas.drawLine(mx, roadY + 12 * dp, mx, roadY + 19 * dp, line)

        if (t < 0f) return
        // Three dots, 90 ms apart, each 560 ms along the road; then the ring at the device they reach.
        measure.setPath(if (runRelay) relayPath else wifiPath, false)
        val length = measure.length
        for (k in 0 until 3) {
            val q = MotionKit.STANDARD.getInterpolation(seg(k * 90f, k * 90f + 560f))
            if (q <= 0f || q >= 1f) continue
            measure.getPosTan(length * (if (runOut) q else 1f - q), at, null)
            val o = minOf(1f, q / 0.1f, (1f - q) / 0.1f)
            fill.color = card
            fill.alpha = (255 * o).toInt()
            canvas.drawCircle(at[0], at[1], 6.5f * dp, fill)
            fill.color = gold
            fill.alpha = (255 * o).toInt()
            canvas.drawCircle(at[0], at[1], 5 * dp, fill)
            fill.alpha = 255
        }
        val pk = seg(ARRIVE_MS, ARRIVE_MS + 300f)
        if (pk > 0f && pk < 1f) {
            line.color = green
            line.strokeWidth = 2 * dp
            line.alpha = (255 * 0.35f * (1f - pk)).toInt()
            val x = if (runOut) pcX() else phoneX()
            canvas.drawCircle(x, roadY - 2 * dp, (26 + 10 * pk) * dp, line)
            line.alpha = 255
        }
    }

    private companion object {
        const val RUN_MS = 1_100L
        const val ARRIVE_MS = 740f
    }
}
