package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R

/**
 * A ring that fills with the real progress and ends in a disc with a check (3 Nov 2026, Back up now): drawn as
 * a button's icon. [progress] is how much is written (0-1), [done] the ending (0-1: the disc grows, the check is
 * traced). Colours are for a dark filled button: a pale track, the bright gold ring and disc, a dark check.
 */
class ProgressRing(context: Context) : Drawable() {
    private val dp = context.resources.displayMetrics.density
    private val side = (20 * dp).toInt()
    private val track = 0x47FFFFFF
    private val gold = ContextCompat.getColor(context, R.color.gold_on_dark)
    private val check = ContextCompat.getColor(context, R.color.brand_green)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.6f * dp
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidateSelf() }
    var done = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidateSelf() }

    override fun getIntrinsicWidth() = side
    override fun getIntrinsicHeight() = side

    private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val sw = line.strokeWidth
        box.set(b.left + sw / 2f, b.top + sw / 2f, b.right - sw / 2f, b.bottom - sw / 2f)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        if (done < 1f) {
            line.color = track
            canvas.drawOval(box, line)
            line.color = gold
            if (progress > 0f) canvas.drawArc(box, -90f, 360f * progress, false, line)
        }
        if (done > 0f) {
            val pop = MotionKit.EMPHASIZED_DECELERATE.getInterpolation(seg(done, 0f, 0.5f))
            fill.color = gold
            fill.alpha = (255 * pop).toInt()
            canvas.drawCircle(cx, cy, b.width() / 2f * (0.6f + 0.4f * pop), fill)
            // The check, A to B to C, traced from the ending's third.
            val q = seg(done, 0.3f, 1f)
            if (q > 0f) {
                val w = b.width().toFloat()
                val ax = b.left + w * 0.28f; val ay = b.top + w * 0.52f
                val bx = b.left + w * 0.44f; val by = b.top + w * 0.68f
                val qx = b.left + w * 0.74f; val qy = b.top + w * 0.36f
                val ab = Math.hypot((bx - ax).toDouble(), (by - ay).toDouble()).toFloat()
                val bc = Math.hypot((qx - bx).toDouble(), (qy - by).toDouble()).toFloat()
                val d = (ab + bc) * q
                line.color = check
                if (d < ab) canvas.drawLine(ax, ay, ax + (bx - ax) * (d / ab), ay + (by - ay) * (d / ab), line)
                else {
                    canvas.drawLine(ax, ay, bx, by, line)
                    val k = (d - ab) / bc
                    canvas.drawLine(bx, by, bx + (qx - bx) * k, by + (qy - by) * k, line)
                }
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Back up now while it works (3 Nov 2026, the owner's mockup "Backup — real progress, then done"): the button
 * keeps its place and colour, a gold ring at its words fills with how much of the book is written —
 * "Creating backup… 42%" — and at the end the ring becomes a gold disc with a check and the words "Backed up ✓".
 * Taps are not taken while it runs (not disabled: a disabled Material button fades its own colours, lesson 10).
 * With the phone's animations off the percent still counts, and the ending is shown at once.
 */
class BackupButtonMotion(private val button: MaterialButton) {
    private val idleText = button.text
    private val ring = ProgressRing(button.context)
    private var shown = 0f
    private var ease: ValueAnimator? = null

    fun start() {
        button.isClickable = false
        button.iconTint = null
        button.icon = ring
        ring.progress = 0f
        ring.done = 0f
        shown = 0f
        say(0)
    }

    /** A whole percent from the writer (BackupCodec.exportTo), on the main thread. */
    fun progress(pct: Int) {
        say(pct)
        val to = pct / 100f
        ease?.cancel()
        if (!Motion.enabled(button.context)) { shown = to; ring.progress = to; return }
        ease = ValueAnimator.ofFloat(shown, to).apply {
            duration = 120
            interpolator = MotionKit.STANDARD
            addUpdateListener { shown = it.animatedValue as Float; ring.progress = shown }
            start()
        }
    }

    private fun say(pct: Int) {
        button.text = button.context.getString(R.string.creating_backup) + " " + Format.ltr("$pct%")
    }

    /** Written: the ring closes, the disc and check come, the button says so, then [then] after a breath. */
    fun done(then: () -> Unit) {
        ease?.cancel()
        ring.progress = 1f
        button.text = button.context.getString(R.string.backup_saved_flash)
        Motion.confirm(button)
        if (!Motion.enabled(button.context)) {
            ring.done = 1f
            then()
            reset(after = 1200)
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            interpolator = MotionKit.STANDARD
            addUpdateListener { ring.done = it.animatedValue as Float }
            start()
        }
        button.postDelayed({ then() }, 520)
        reset(after = 1600)
    }

    /** Not written: the button as it was. */
    fun failed() {
        ease?.cancel()
        reset(after = 0)
    }

    private fun reset(after: Long) {
        button.postDelayed({
            button.icon = null
            button.text = idleText
            button.isClickable = true
            if (Motion.enabled(button.context)) MotionKit.fadeIn(button, duration = 160)
        }, after)
    }
}
