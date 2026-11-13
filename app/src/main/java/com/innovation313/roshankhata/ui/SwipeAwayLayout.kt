package com.innovation313.roshankhata.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.sign

/**
 * A card that goes when it is swiped sideways (13 Nov 2026, the owner: the Saved card would not swipe away). The
 * Saved card is a Snackbar shown over the screen's own frame, not a CoordinatorLayout, so Snackbar's own swipe never
 * worked there. This frame holds the card's content: a sideways drag past the touch slop moves [mover] (the card's
 * shell) with the finger and fades it; let go past a third of its width, or flung, it leaves that way and [onGone]
 * runs; else it springs back. A tap on the card's buttons is still a tap: the drag is taken only once it is clearly
 * sideways.
 */
class SwipeAwayLayout(context: Context) : FrameLayout(context) {

    /** What moves with the finger: the card's shell (its background is there). */
    var mover: View? = null

    /** Runs once the card has left. */
    var onGone: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val fling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * FLING_TIMES
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> start(ev)
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                if (!dragging && sideways(ev)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) stopTracking()
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val m = mover ?: this
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (tracker == null) start(ev)
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                if (!dragging && sideways(ev)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    val dx = ev.rawX - downX
                    m.translationX = dx
                    m.alpha = (1f - abs(dx) / m.width.coerceAtLeast(1)).coerceIn(MIN_ALPHA, 1f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                stopTracking()
                if (dragging) letGo(m, ev.rawX - downX, vx, ev.actionMasked == MotionEvent.ACTION_UP)
                dragging = false
            }
        }
        return true
    }

    private fun start(ev: MotionEvent) {
        downX = ev.rawX
        downY = ev.rawY
        dragging = false
        tracker?.recycle()
        tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
    }

    private fun stopTracking() {
        tracker?.recycle()
        tracker = null
    }

    private fun sideways(ev: MotionEvent): Boolean {
        val dx = abs(ev.rawX - downX)
        val dy = abs(ev.rawY - downY)
        return dx > slop && dx > dy * SIDEWAYS
    }

    private fun letGo(m: View, dx: Float, vx: Float, lifted: Boolean) {
        val width = m.width.toFloat().coerceAtLeast(1f)
        val flung = abs(vx) > fling && sign(vx) == sign(dx)
        if (lifted && (abs(dx) > width * AWAY_SHARE || flung)) {
            val to = if (dx >= 0) width * OUT else -width * OUT
            m.animate().translationX(to).alpha(0f).setDuration(OUT_MS).withEndAction { onGone?.invoke() }.start()
        } else {
            m.animate().translationX(0f).alpha(1f).setDuration(BACK_MS).start()
        }
    }

    private companion object {
        const val SIDEWAYS = 1.5f
        const val AWAY_SHARE = 0.35f
        const val FLING_TIMES = 4
        const val MIN_ALPHA = 0.2f
        const val OUT = 1.2f
        const val OUT_MS = 160L
        const val BACK_MS = 180L
    }
}
