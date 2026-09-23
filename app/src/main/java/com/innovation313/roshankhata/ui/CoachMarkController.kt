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
import com.innovation313.roshankhata.R

/**
 * Drives the first-run Home screen walkthrough: a dimmed scrim with a
 * spotlight hole over one real control at a time, and a card describing it.
 * No full-screen slides — every step points at the actual button the owner
 * will tap later.
 *
 * ONE card for every step (view_coach_tip), header and tiles alike, counted
 * straight through ("4 / 18") with a thin progress bar. It used to be two:
 * a pointer card counting the three header steps and, for the tiles, a
 * generic "Walk Through / Home Screen" card with bare dots — two counts that
 * disagreed and two looks for one tour. The owner asked for one.
 *
 * The card sits directly under the lit item with a notch aimed at it, or
 * above with the notch turned down when there is no room beneath.
 *
 * Shown once. [hasRun] / [markRun] persist that on-device, the same one-shot
 * pattern LanguageActivity already uses.
 */
class CoachMarkController(
    private val activity: Activity,
    private val root: ViewGroup,
    private val steps: List<Step>
) {

    /** One stop on the tour: the view to spotlight, and the strings beside it. */
    data class Step(
        /** The view the spotlight is centred on. */
        val target: View,
        val titleRes: Int,
        val descRes: Int,
        /**
         * Corner radius in dp. The overlay clamps this to half the shorter
         * side, so a generous value rounds a small square target fully while
         * a wide one keeps sensible ends.
         */
        val cornerRadiusDp: Float = 999f,
        /**
         * Breathing room drawn around the target, in dp. Small where
         * something sits close above or below the target — the balance row
         * has its own caption directly overhead, and a wide ring lights that
         * too, which reads as pointing at the wrong thing.
         */
        val paddingDp: Float = 10f,
        /**
         * What the card must clear, if that is larger than [target]. A tile's
         * circle is drawn round its icon alone, but the card still has to sit
         * below the label underneath — otherwise it lands on top of it.
         */
        val clearance: View? = null
    )

    private var index = 0
    private var overlay: CoachMarkOverlay? = null
    private var tip: View? = null
    private var container: FrameLayout? = null
    private var scrollAnimator: ValueAnimator? = null

    fun start() {
        if (steps.isEmpty()) return

        // Everything the tour draws goes in a FrameLayout of our own rather
        // than straight into the screen's root. Home's root is a
        // ConstraintLayout, where a plain topMargin positions nothing without
        // constraints to hang it from — the card would sit at the top corner
        // whatever it was told. A FrameLayout honours margins as offsets,
        // which is exactly what placing a card beneath a tile needs.
        val host = FrameLayout(activity)
        root.addView(
            host,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        container = host

        val overlayView = CoachMarkOverlay(activity)
        host.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        // Swallow taps on the scrim: during the tour the only ways forward are
        // NEXT and Skip, so a stray tap cannot fire the control underneath.
        overlayView.isClickable = true
        overlayView.isFocusable = true
        overlay = overlayView

        // Placed by absolute left/top because it follows a measured spot on
        // screen, not the reading direction.
        val tipView = activity.layoutInflater.inflate(R.layout.view_coach_tip, host, false)
        host.addView(
            tipView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                leftMargin = dp(TIP_SIDE_DP).toInt()
                rightMargin = dp(TIP_SIDE_DP).toInt()
            }
        )
        tip = tipView
        tipView.findViewById<TextView>(R.id.tvTipSkip).setOnClickListener { finish() }
        tipView.findViewById<Button>(R.id.btnTipNext).setOnClickListener { advance() }
        overlayView.ringColor = activity.getColor(R.color.gold_on_dark)

        index = 0
        showStep()
    }

    private fun dp(value: Float): Float = value * activity.resources.displayMetrics.density

    private fun showStep() {
        val step = steps.getOrNull(index) ?: return finish()
        val overlayView = overlay ?: return
        val tipView = tip ?: return
        val host = container ?: return

        tipView.findViewById<TextView>(R.id.tvTipCount).text = "${index + 1} / ${steps.size}"
        tipView.findViewById<TextView>(R.id.tvTipTitle).setText(step.titleRes)
        tipView.findViewById<TextView>(R.id.tvTipDesc).setText(step.descRes)

        // Progress: the seen part and the rest, as two weights of one bar.
        fun weigh(id: Int, w: Float) {
            val v = tipView.findViewById<View>(id)
            v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).apply { weight = w }
        }
        weigh(R.id.coachTipDone, (index + 1).toFloat())
        weigh(R.id.coachTipRest, (steps.size - index - 1).toFloat())

        val isLast = index == steps.size - 1
        tipView.findViewById<Button>(R.id.btnTipNext)
            .setText(if (isLast) R.string.coach_done else R.string.coach_next)
        // Skip stops offering an exit on the final step — there is nothing left
        // to skip past, and "Skip" beside "Done" only invites a misread.
        tipView.findViewById<TextView>(R.id.tvTipSkip).visibility =
            if (isLast) View.INVISIBLE else View.VISIBLE

        // Out of sight while the grid moves, so the new words never sit
        // under the old tile.
        tipView.visibility = View.INVISIBLE

        // The feature grid scrolls, so a tile further down can be off-screen
        // when its turn comes. Bring it into view first — a spotlight measured
        // before the scroll would land on empty space.
        scrollIntoView(step.clearance ?: step.target) {
            // Measured here, straight after the scroll reports it has landed.
            // The overlay covers the whole screen, so scrolling beneath it does
            // not re-lay it out; waiting on its layout pass left the hole on the
            // previous tile.
            val rect = CoachMarkOverlay.boundsWithin(step.target, host)
            overlayView.holePadding = dp(step.paddingDp)
            overlayView.holeRadius = dp(step.cornerRadiusDp)
            overlayView.holeRect = rect

            val anchor = step.clearance
                ?.let { CoachMarkOverlay.boundsWithin(it, host) }
                ?: rect
            // Post rather than place inline: assigning layoutParams during a
            // layout pass re-enters layout, which Android may treat as a loop.
            tipView.post {
                try {
                    positionTip(tipView, rect, anchor, dp(step.paddingDp))
                } catch (e: Exception) {
                    // A misplaced card is a blemish; a crash is not.
                    android.util.Log.e(TAG, "positioning failed", e)
                }
                tipView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Put the card directly beneath the lit item, notch pointing up at its
     * centre, so the name and the explanation are read in one downward
     * glance. If that would run into the bottom bar, the card goes above the
     * item instead with the notch turned down — never over the item itself,
     * the one thing the step exists to show.
     */
    private fun positionTip(tipView: View, target: RectF, anchor: RectF, pad: Float) {
        val lp = tipView.layoutParams as? FrameLayout.LayoutParams ?: return
        val host = container ?: return
        if (host.width <= 0 || host.height <= 0) return

        val side = dp(TIP_SIDE_DP)
        val gap = dp(6f)
        val cardWidth = (host.width - 2 * side).toInt().coerceAtLeast(1)
        tipView.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cardHeight = tipView.measuredHeight
        val floor = host.height - bottomBarHeight() - dp(8f).toInt()

        val below = (anchor.bottom + pad + gap).toInt()
        val fitsBelow = below + cardHeight <= floor
        lp.topMargin = if (fitsBelow) {
            below
        } else {
            (target.top - pad - gap - cardHeight).toInt().coerceAtLeast(dp(8f).toInt())
        }
        tipView.layoutParams = lp

        val up = tipView.findViewById<View>(R.id.coachTipPointer)
        val down = tipView.findViewById<View>(R.id.coachTipPointerDown)
        up.visibility = if (fitsBelow) View.VISIBLE else View.INVISIBLE
        down.visibility = if (fitsBelow) View.INVISIBLE else View.VISIBLE

        // Aim the notch at the lit item's centre, clear of the card's corners.
        val want = target.centerX() - side - dp(8f)
        val x = want.coerceIn(dp(14f), (cardWidth - dp(30f)).coerceAtLeast(dp(14f)))
        up.translationX = x
        down.translationX = x
    }

    /**
     * Height of whatever sits pinned to the bottom of the screen behind the
     * scrim — the navigation bar on both screens that run this tour. Zero if
     * there is nothing there.
     */
    private fun bottomBarHeight(): Int {
        val bar = activity.findViewById<View>(R.id.bottomNav) ?: return 0
        return if (bar.isShown) bar.height else 0
    }

    /**
     * Scroll the nearest scrolling ancestor so [target] sits in view, then run
     * [then]. Targets outside any scroller run [then] straight away.
     */
    private fun scrollIntoView(target: View, then: () -> Unit) {
        var parent = target.parent
        while (parent != null && parent !is ScrollView) {
            parent = (parent as? View)?.parent
        }
        val scroller = parent as? ScrollView
        if (scroller == null) {
            then()
            return
        }

        // Park every row at the same height in the viewport, so the tour
        // advances by the same visual step each time rather than barely moving
        // on one row and swinging a screenful on the next.
        val topWithin = CoachMarkOverlay.boundsWithin(target, scroller).top.toInt() + scroller.scrollY
        val desired = (topWithin - dp(16f).toInt()).coerceAtLeast(0)
        if (kotlin.math.abs(desired - scroller.scrollY) < dp(4f)) {
            then()
            return
        }

        // Darken while the grid moves. The hole is still sitting over the
        // previous step's tile, and as the rows slide past it another tile
        // takes that spot — so the wrong feature stood lit for the length of
        // the scroll before the right one arrived. Nothing is lit in transit;
        // the hole reopens on the new target when the scroll lands.
        overlay?.holeRect = null

        // An animator rather than smoothScrollTo. smoothScrollTo takes as long
        // as it likes depending on the distance, so pairing it with a fixed
        // wait meant the spotlight was measured mid-flight on the long jumps
        // and sat idle after the short ones — the lurch between steps. This
        // runs to a known duration and reports the moment it lands.
        scrollAnimator?.cancel()
        scrollAnimator = ValueAnimator.ofInt(scroller.scrollY, desired).apply {
            duration = SCROLL_MS
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { scroller.scrollTo(0, it.animatedValue as Int) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    then()
                }
            })
            start()
        }
    }

    private fun advance() {
        index++
        if (index >= steps.size) finish() else showStep()
    }

    private fun finish() {
        scrollAnimator?.cancel()
        scrollAnimator = null
        // Removing the host removes the scrim and the card with it.
        container?.let { root.removeView(it) }
        container = null
        tip = null
        overlay = null
        markRun(activity)
    }

    companion object {
        private const val TAG = "CoachMarks"

        /** How long a row takes to travel, whatever the distance. */
        private const val SCROLL_MS = 200L

        private const val PREFS = "coach_marks"
        private const val KEY_RUN = "home_tour_done"

        fun hasRun(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RUN, false)

        fun markRun(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_RUN, true).apply()
        }
    }
}
