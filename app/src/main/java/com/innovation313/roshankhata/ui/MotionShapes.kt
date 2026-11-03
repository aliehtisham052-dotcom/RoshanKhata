package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R
import kotlin.math.atan2

/**
 * Rings spreading out from a mic button while the phone listens (5 Oct).
 * Three rings, a third of a beat apart, each growing from the button's edge
 * and fading as it goes — in the button's own colour. Runs until [stop].
 */
internal class PulseRings(context: Context, private val color: Int, private val inner: Float) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var t = 0f
    private val beat = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    fun go() = beat.start()
    fun stop() = beat.cancel()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = minOf(cx, cy)
        for (k in 0 until 3) {
            val p = (t + k / 3f) % 1f
            val r = inner + (outer - inner) * p
            val a = ((1f - p) * 0.38f * 255).toInt().coerceIn(0, 255)
            ring.color = (color and 0x00FFFFFF) or (a shl 24)
            canvas.drawCircle(cx, cy, r, ring)
        }
    }
}

/**
 * The golden paper plane (3 Nov 2026, the owner: "professional karo"; the first flight was 5 Oct).
 *
 * It lifts out of the button that sent it: a crouch of [CROUCH_MS] (nose up, a touch down, half again
 * as big, a soft shadow under it), then a throw of [THROW_MS] straight up beside the row's amount and
 * leaning away to the top corner on the reading side's far end, out past the edge. A short solid trail
 * thins and fades behind it, gone [FADE_MS] after the plane. The owner's goal is WhatsApp, so it opens at
 * [OPEN_AT] — sooner than the 488 ms of the 5 Oct flight.
 *
 * Every point is worked out left to right and mirrored when drawn in a right-to-left language, so in
 * Urdu, Sindhi, Arabic and Farsi the plane flies to the top left the right way up. [startX] is already
 * in that left-to-right space.
 */
internal class PlaneFlight(
    context: Context,
    private val startX: Float,
    private val startY: Float,
    private val rtl: Boolean
) : View(context) {
    private val dp = resources.displayMetrics.density
    private val plane: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_paper_plane_gold)!!.mutate()
    private val base = 18 * dp
    private val gold = ContextCompat.getColor(context, R.color.brand_gold)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = android.graphics.RectF()

    /** Milliseconds since the tap. */
    var ms = 0f
        set(v) { field = v; invalidate() }

    private fun cub(a: Float, b: Float, c: Float, d: Float, u: Float): Float {
        val v = 1 - u
        return v * v * v * a + 3 * v * v * u * b + 3 * v * u * u * c + u * u * u * d
    }

    private fun tan(a: Float, b: Float, c: Float, d: Float, u: Float): Float {
        val v = 1 - u
        return 3 * v * v * (b - a) + 6 * v * u * (c - b) + 3 * u * u * (d - c)
    }

    private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        if (rtl) {
            canvas.save()
            canvas.scale(-1f, 1f, w / 2f, 0f)
        }
        // The throw's four points: straight up, then leaning to the far top corner, out past the edge.
        val x0 = startX
        val y0 = startY + 3 * dp
        val x1 = x0 + 6 * dp
        val y1 = y0 - 90 * dp
        val x3 = w * 0.88f
        val y3 = -56 * dp
        val x2 = x0 + (x3 - x0) * 0.55f
        val y2 = y0 * 0.28f

        var x = startX
        var y = startY
        var rot: Float
        var scale: Float
        var alpha = 1f
        var shade: Float
        if (ms < CROUCH_MS) {
            val a = EMPHASIZED_DECELERATE.getInterpolation(ms / CROUCH_MS)
            y += 3 * dp * a
            rot = -40f * a
            scale = 1f + 0.5f * a
            shade = 0.16f * a
        } else {
            val f = seg(ms, CROUCH_MS, CROUCH_MS + THROW_MS)
            val u = THROW.getInterpolation(f)
            x = cub(x0, x1, x2, x3, u)
            y = cub(y0, y1, y2, y3, u)
            val heading = Math.toDegrees(atan2(tan(y0, y1, y2, y3, u), tan(x0, x1, x2, x3, u)).toDouble()).toFloat()
            rot = -40f + (heading + NOSE + 40f) * STANDARD.getInterpolation(seg(f, 0f, 0.2f))
            scale = 1.5f + (0.8f - 1.5f) * u
            alpha = if (ms >= CROUCH_MS + THROW_MS) 0f else 1f - seg(u, 0.8f, 1f)
            shade = 0.16f * (1f - seg(ms, CROUCH_MS, CROUCH_MS + 110f))
            // The trail: ten pieces of the path behind the plane, each thinner and fainter than the last.
            val fade = if (ms < CROUCH_MS + THROW_MS) 1f
                else 1f - STANDARD.getInterpolation(seg(ms, CROUCH_MS + THROW_MS, CROUCH_MS + THROW_MS + FADE_MS))
            if (f > 0f && fade > 0f) {
                for (k in 0 until 10) {
                    val ub = u - k * 0.03f
                    val ua = ub - 0.03f
                    if (ua < 0f) break
                    val left = 1f - k / 10f
                    trailPaint.strokeWidth = 3.6f * dp * left
                    trailPaint.color = (gold and 0x00FFFFFF) or (((0.6f * left * fade) * 255).toInt().coerceIn(0, 255) shl 24)
                    canvas.drawLine(
                        cub(x0, x1, x2, x3, ua), cub(y0, y1, y2, y3, ua),
                        cub(x0, x1, x2, x3, ub), cub(y0, y1, y2, y3, ub), trailPaint
                    )
                }
            }
        }

        if (shade > 0.005f) {
            shadowPaint.color = ((shade * 255).toInt().coerceIn(0, 255) shl 24)
            shadow.set(x - 9 * dp, startY + 14 * dp - 2.5f * dp, x + 9 * dp, startY + 14 * dp + 2.5f * dp)
            canvas.drawOval(shadow, shadowPaint)
        }
        if (alpha > 0.005f) {
            val half = (base * scale / 2f).toInt()
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(rot)
            plane.setBounds(-half, -half, half, half)
            plane.alpha = (255 * alpha).toInt().coerceIn(0, 255)
            plane.draw(canvas)
            canvas.restore()
        }
        if (rtl) canvas.restore()
    }

    companion object {
        const val CROUCH_MS = 110f
        const val THROW_MS = 280f
        const val FADE_MS = 150f
        const val TOTAL_MS = CROUCH_MS + THROW_MS + FADE_MS
        /** WhatsApp opens here: the plane is high, leaning away, and the eye has followed it. */
        const val OPEN_AT = 280f
        /** The drawing's nose points 43.5 degrees above +x (ic_paper_plane_gold: (44,5) seen from (24,24)). */
        private const val NOSE = 43.5f
        // Material's own curves (material-components-android, docs/theming/Motion.md).
        private val EMPHASIZED_DECELERATE = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        private val STANDARD = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        /** The throw after the crouch: quick away from the button, easing only once it is past the top edge. */
        private val THROW = android.view.animation.PathInterpolator(0.25f, 0.1f, 0.55f, 0.95f)
    }
}
