package com.innovation313.roshankhata.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewTreeObserver

/**
 * A sheet that fits its content grows and shrinks to a new answer smoothly instead of in one frame (13 Nov 2026, the
 * owner's video of Hudhud's sheet: "I didn't catch that" was shorter than the greeting before it, and the whole sheet
 * dropped about 50 dp at once — Hudhud, in the air at that moment, then landed where his stage had been).
 *
 * Only a change of [content]'s own height is eased: the keyboard and the window's insets change the space around the
 * sheet, not what is in it, and keep their own motion. When [content] has changed, the sheet's [root] is held at the
 * height it was drawn at and led to its new height on the standard curve; the part of the sheet that scrolls takes
 * up the difference meanwhile, so the box and the chips at the foot stay where they are under the finger. With the
 * phone's animations off the sheet changes at once, as before.
 */
class SheetEase private constructor(private val root: View, private val content: View) {

    /** The root's height in the last frame drawn. */
    private var drawn = 0
    /** [content] changed its height since the last frame: the next change of the root's height is eased. */
    private var armed = false
    private var leading: ValueAnimator? = null

    private val contentChanged = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        val was = oldBottom - oldTop
        if (was > 0 && bottom - top != was) armed = true
    }

    private val beforeDraw = ViewTreeObserver.OnPreDrawListener {
        val now = root.height
        if (leading != null || now <= 0) {
            // Being led (or not laid out yet): only remember the height drawn; a change of content meanwhile stays
            // armed and is eased once this one ends.
            if (now > 0) drawn = now
            return@OnPreDrawListener true
        }
        val was = drawn
        drawn = now
        val ease = armed && was > 0 && now != was && Motion.enabled(root.context)
        armed = false
        if (!ease) return@OnPreDrawListener true
        lead(was, now)
        // This frame is not drawn at the new height: the next one is drawn at the old height, then led to the new.
        false
    }

    private var observer: ViewTreeObserver? = null

    private fun watch() {
        content.addOnLayoutChangeListener(contentChanged)
        val o = root.viewTreeObserver
        o.addOnPreDrawListener(beforeDraw)
        observer = o
    }

    /** Ends what is being led (the sheet takes its own height again) and stops watching. */
    fun stop() {
        leading?.cancel()
        leading = null
        content.removeOnLayoutChangeListener(contentChanged)
        val o = observer
        if (o != null && o.isAlive) o.removeOnPreDrawListener(beforeDraw) else root.viewTreeObserver.removeOnPreDrawListener(beforeDraw)
        observer = null
    }

    private fun lead(from: Int, to: Int) {
        val lp = root.layoutParams ?: return
        val own = lp.height
        lp.height = from
        root.layoutParams = lp
        leading = ValueAnimator.ofInt(from, to).apply {
            duration = DURATION_MS
            interpolator = MotionKit.STANDARD
            addUpdateListener {
                lp.height = it.animatedValue as Int
                root.layoutParams = lp
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    leading = null
                    // The sheet fits its content again; if the content changed meanwhile, that change is led next.
                    lp.height = own
                    root.layoutParams = lp
                }
            })
            start()
        }
    }

    companion object {
        /** Material's own length for a container that changes size on screen (medium 1 to 2: 250-300 ms). */
        private const val DURATION_MS = 260L

        /**
         * Eases [root] (the sheet's whole content view) whenever [content] (what it shows, inside its scrolling part)
         * changes height. Call [stop] when the sheet closes.
         */
        fun attach(root: View, content: View): SheetEase = SheetEase(root, content).also { it.watch() }
    }
}
