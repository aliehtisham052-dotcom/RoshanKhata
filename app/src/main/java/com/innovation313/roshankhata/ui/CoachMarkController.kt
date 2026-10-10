package com.innovation313.roshankhata.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.innovation313.roshankhata.R

/**
 * The Home walkthrough (rebuilt 9 Oct): a sheet that stays put, and a
 * spotlight that travels.
 *
 * The first version put the card under each lit item, or above it when it
 * would not fit, with a notch pointing at it. Over six steps the card
 * landed in a different place every time - under the header, over the
 * Business row, above the Bills tile - and the owner said he could not
 * tell where to look. This is the pattern feature tours in Google's own
 * apps use: one card pinned to the foot of the screen, every step's words
 * in the same place, and only the spotlight moves. Each step scrolls its
 * item into the clear stage above the card, centred, then the hole glides
 * from the last item to this one while the words cross-fade.
 */
class CoachMarkController(
    private val activity: Activity,
    private val root: ViewGroup,
    private val steps: List<Step>,
    private val onFinished: (() -> Unit)? = null
) {

    data class Step(
        val target: View,
        val titleRes: Int,
        val descRes: Int,
        val cornerRadiusDp: Float = 999f,
        val paddingDp: Float = 10f,
        /** Kept for callers; the sheet no longer needs a clearance view. */
        val clearance: View? = null
    )

    private var index = 0
    private var overlay: CoachMarkOverlay? = null
    private var tip: View? = null
    private var container: FrameLayout? = null
    private var scrollAnimator: ValueAnimator? = null
    private var holeAnimator: ValueAnimator? = null
    private var sheetBottomInset = 0

    /** The item the spotlight is on now; the hole rides with it while the screen scrolls. */
    private var lit: View? = null

    fun start() {
        if (steps.isEmpty()) return

        val host = FrameLayout(activity)
        root.addView(
            host,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        container = host

        val overlayView = CoachMarkOverlay(activity)
        host.addView(
            overlayView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        overlayView.isClickable = true
        overlayView.isFocusable = true
        overlayView.ringColor = activity.getColor(R.color.gold_on_dark)
        overlay = overlayView

        val tipView = activity.layoutInflater.inflate(R.layout.view_coach_tip, host, false)
        TextFit.relax(tipView)
        host.addView(
            tipView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
                .apply { gravity = Gravity.BOTTOM }
        )
        tip = tipView
        // The sheet sits on the gesture bar, not under it: its own bottom
        // padding plus the bar's height, the way every screen's root does.
        val basePad = tipView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(tipView) { v, insets ->
            sheetBottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPaddingRelative(v.paddingStart, v.paddingTop, v.paddingEnd, basePad + sheetBottomInset)
            insets
        }
        ViewCompat.requestApplyInsets(tipView)

        tipView.findViewById<TextView>(R.id.tvTipSkip).setOnClickListener { finish() }
        tipView.findViewById<Button>(R.id.btnTipNext).setOnClickListener { advance() }

        // The sheet rises from the foot of the screen as the dim fades in.
        overlayView.alpha = 0f
        overlayView.animate().alpha(1f).setDuration(220).start()
        tipView.alpha = 0f
        tipView.translationY = dp(40f)
        tipView.animate().alpha(1f).translationY(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()

        index = 0
        tipView.post { showStep(first = true) }
    }

    private var roomScroller: ScrollView? = null
    private var roomBasePadding = 0

    /**
     * The sheet covers the foot of the screen, so the last rows of a
     * scrolling screen could never rise above it: Tools sat under the sheet
     * on its own step (the owner's report, 10 Oct). While the tour runs the
     * scroller gets the sheet's height as extra bottom padding - room to
     * scroll that far - and gives it back when the tour ends.
     */
    private fun makeRoom(sheetHeight: Int) {
        val scroller = roomScroller ?: (steps.firstNotNullOfOrNull { scrollerOf(it.target) } ?: return).also {
            // Remembered once, so a later step never mistakes the room it
            // added for the screen's own padding.
            roomScroller = it
            roomBasePadding = it.paddingBottom
            it.clipToPadding = false
        }
        val wanted = roomBasePadding + sheetHeight
        if (scroller.paddingBottom != wanted) {
            scroller.setPadding(scroller.paddingLeft, scroller.paddingTop, scroller.paddingRight, wanted)
        }
    }

    private fun scrollerOf(target: View): ScrollView? {
        var parent = target.parent
        while (parent != null && parent !is ScrollView) parent = (parent as? View)?.parent
        return parent as? ScrollView
    }

    private fun dp(value: Float): Float = value * activity.resources.displayMetrics.density

    /** The screen above the sheet: where every lit item is brought to. */
    private fun stageHeight(): Int {
        val host = container ?: return 0
        val sheet = tip ?: return host.height
        return (host.height - sheet.height).coerceAtLeast(host.height / 2)
    }

    private fun showStep(first: Boolean = false) {
        val step = steps.getOrNull(index) ?: return finish()
        val overlayView = overlay ?: return
        val tipView = tip ?: return
        val host = container ?: return

        val words = listOf<View>(
            tipView.findViewById(R.id.tvTipTitle),
            tipView.findViewById(R.id.tvTipDesc)
        )
        fun fill() {
            tipView.findViewById<TextView>(R.id.tvTipCount).text = "${index + 1} / ${steps.size}"
            tipView.findViewById<TextView>(R.id.tvTipTitle).setText(step.titleRes)
            tipView.findViewById<TextView>(R.id.tvTipDesc).setText(step.descRes)
            fun weigh(id: Int, w: Float) {
                val v = tipView.findViewById<View>(id)
                v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).apply { weight = w }
            }
            weigh(R.id.coachTipDone, (index + 1).toFloat())
            weigh(R.id.coachTipRest, (steps.size - index - 1).toFloat())
            val isLast = index == steps.size - 1
            tipView.findViewById<Button>(R.id.btnTipNext)
                .setText(if (isLast) R.string.coach_done else R.string.coach_next)
            tipView.findViewById<TextView>(R.id.tvTipSkip).visibility =
                if (isLast) View.INVISIBLE else View.VISIBLE
        }

        val mine = index
        // Placed only once this step's words are in and the sheet has been
        // laid out with them (10 Oct). The sheet's height changes from step
        // to step - Tools has the longest words, and the gesture-bar inset
        // arrives after the first measure - and placing against the first
        // step's height left Tools under a sheet that had since grown.
        fun place() {
            if (mine != index || container == null) return
            makeRoom(tipView.height)
            scrollIntoStage(step.target) {
                // A scroll the next step cut short still ends; its placement is
                // not wanted any more.
                if (mine != index || container == null) return@scrollIntoStage
                val rect = CoachMarkOverlay.boundsWithin(step.target, host)
                overlayView.holePadding = dp(step.paddingDp)
                overlayView.holeRadius = dp(step.cornerRadiusDp)
                lit = step.target
                glideHole(overlayView, rect)
            }
        }

        if (first) {
            fill()
            tipView.post { place() }
        } else {
            // The words cross-fade in place; the card itself never moves.
            words.forEach { it.animate().alpha(0f).setDuration(110).start() }
            tipView.postDelayed({
                if (mine != index || container == null) return@postDelayed
                fill()
                words.forEach { it.animate().alpha(1f).setDuration(160).start() }
                tipView.post { place() }
            }, 110)
        }
    }

    /** The spotlight slides from where it was to the new item. */
    private fun glideHole(overlayView: CoachMarkOverlay, to: RectF) {
        val from = overlayView.holeRect
        holeAnimator?.cancel()
        if (from == null) {
            overlayView.holeRect = to
            return
        }
        holeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = HOLE_MS
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener {
                val t = it.animatedValue as Float
                overlayView.holeRect = RectF(
                    from.left + (to.left - from.left) * t,
                    from.top + (to.top - from.top) * t,
                    from.right + (to.right - from.right) * t,
                    from.bottom + (to.bottom - from.bottom) * t
                )
            }
            start()
        }
    }

    /**
     * Scroll the nearest ScrollView so [target] sits in the middle of the
     * stage above the sheet, then run [then]. An item outside any scroller
     * (the header) runs [then] at once.
     */
    private fun scrollIntoStage(target: View, then: () -> Unit) {
        val scroller = scrollerOf(target)
        val host = container
        if (scroller == null || host == null) {
            then()
            return
        }

        // Where the item is on the host, and where the stage is: below the
        // scroller's top (the header stays put) and above the sheet.
        val stageTop = CoachMarkOverlay.boundsWithin(scroller, host).top
        val stageBottom = stageHeight().toFloat()
        val item = CoachMarkOverlay.boundsWithin(target, host)
        val margin = dp(16f)
        // Centred when it fits; a section taller than the stage (Tools on a
        // short phone) starts at the top of the stage instead, so its first
        // rows show rather than its middle with both ends hidden.
        val shift = if (item.height() + 2 * margin <= stageBottom - stageTop) {
            item.centerY() - (stageTop + (stageBottom - stageTop) / 2f)
        } else {
            item.top - (stageTop + margin)
        }
        // Already in the clear: no scroll at all (10 Oct). Each tile used to be
        // re-centred, so going from Cheques (end of the first row) to Bills
        // (start of the second) scrolled a row while the spotlight waited -
        // and the waiting spotlight then sat on Expiry, under Cheques, before
        // gliding back to Bills: the owner saw it twice. Both rows fit on the
        // stage together, so the spotlight now simply moves from the end of
        // one row to the start of the next.
        if (item.top >= stageTop + margin && item.bottom <= stageBottom - margin) {
            then()
            return
        }
        val maxScroll = (scroller.getChildAt(0)?.height ?: 0) + scroller.paddingTop + scroller.paddingBottom - scroller.height
        val desired = (scroller.scrollY + shift).toInt()
            .coerceIn(0, maxScroll.coerceAtLeast(0))
        if (kotlin.math.abs(desired - scroller.scrollY) < dp(2f)) {
            then()
            return
        }

        scrollAnimator?.let { it.removeAllListeners(); it.cancel() }
        scrollAnimator = ValueAnimator.ofInt(scroller.scrollY, desired).apply {
            duration = SCROLL_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                scroller.scrollTo(0, it.animatedValue as Int)
                // The hole stays on the item it lights while the screen moves
                // under it, instead of standing still over whatever scrolls in.
                val holding = lit
                val overlayView = overlay
                if (holding != null && overlayView != null && holeAnimator?.isRunning != true) {
                    overlayView.holeRect = CoachMarkOverlay.boundsWithin(holding, host)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { then() }
            })
            start()
        }
    }

    private var lastAdvanceAt = 0L

    /**
     * One step per tap. A second tap inside the step change - the words
     * cross-fade and the spotlight glides for about 300ms - used to count
     * as the next step too, so a quick double tap on Next went from
     * Cashbook straight past Cheques (the owner's report, 10 Oct).
     */
    private fun advance() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAdvanceAt < ADVANCE_GAP_MS) return
        lastAdvanceAt = now
        index++
        if (index >= steps.size) finish() else showStep()
    }

    private fun finish() {
        scrollAnimator?.let { it.removeAllListeners(); it.cancel() }
        scrollAnimator = null
        roomScroller?.let { sc ->
            sc.setPadding(sc.paddingLeft, sc.paddingTop, sc.paddingRight, roomBasePadding)
        }
        roomScroller = null
        holeAnimator?.cancel()
        holeAnimator = null
        val host = container
        // Removing the host removes the scrim and the sheet with it.
        container = null
        tip = null
        overlay = null
        if (host != null) {
            host.animate().alpha(0f).setDuration(180).withEndAction { root.removeView(host) }.start()
        }
        markRun(activity)
        onFinished?.invoke()
    }

    companion object {
        /** How long a row takes to travel, whatever the distance. */
        private const val SCROLL_MS = 260L

        /** How long the spotlight takes to glide between items. */
        private const val HOLE_MS = 300L

        /** Taps on Next closer together than this are one tap. */
        private const val ADVANCE_GAP_MS = 400L

        private const val PREFS = "coach_marks"
        private const val KEY_RUN = "home_tour_done"

        fun hasRun(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUN, false)

        /** Help -> "Show the Home tour again": the next Home runs it once more. */
        fun reset(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RUN, false).apply()
        }

        fun markRun(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RUN, true).apply()
        }
    }
}
