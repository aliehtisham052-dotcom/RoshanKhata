package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R
import kotlin.math.atan2
import kotlin.math.max

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
 * The golden paper plane (5 Oct): from where the owner tapped, along a curve
 * up to the top corner on the reading side's far end, turning with the
 * curve, leaving a dotted golden trail that fades behind it.
 */
internal class PlaneFlight(
    context: Context,
    startX: Float, startY: Float, endX: Float, endY: Float
) : View(context) {
    private val plane: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_paper_plane_gold)!!.mutate()
    private val size = (34 * resources.displayMetrics.density).toInt()
    private val path = Path().apply {
        moveTo(startX, startY)
        // Rise a little first, then sweep across: a throw, not a slide.
        quadTo(startX + (endX - startX) * 0.15f, endY + (startY - endY) * 0.15f, endX, endY)
    }
    private val measure = PathMeasure(path, false)
    private val trail = Path()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)
    private val gold = ContextCompat.getColor(context, R.color.brand_gold)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        val d = 6 * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(d * 0.4f, d), 0f)
    }
    var t = 0f
        set(v) { field = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val len = measure.length
        val head = len * t
        trail.reset()
        measure.getSegment(max(0f, head - len * 0.45f), head, trail, true)
        val fade = if (t < 0.7f) 1f else (1f - (t - 0.7f) / 0.3f)
        trailPaint.color = (gold and 0x00FFFFFF) or (((0.55f * fade) * 255).toInt().coerceIn(0, 255) shl 24)
        canvas.drawPath(trail, trailPaint)

        measure.getPosTan(head, pos, tan)
        val angle = Math.toDegrees(atan2(tan[1], tan[0]).toDouble()).toFloat() + 45f
        val scale = 1f - 0.35f * t
        canvas.save()
        canvas.translate(pos[0], pos[1])
        canvas.rotate(angle)
        canvas.scale(scale, scale)
        plane.setBounds(-size / 2, -size / 2, size / 2, size / 2)
        plane.alpha = (255 * fade).toInt().coerceIn(0, 255)
        plane.draw(canvas)
        canvas.restore()
    }
}
