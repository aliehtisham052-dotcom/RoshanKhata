package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
 * The card is CENTRED rather than pinned beside the target. Pinning looked
 * tidier on a large screen and collided with the very control it was
 * describing on a small one; the spotlight already says which control is
 * meant, so the card does not need to crowd it.
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
        val target: View,
        val titleRes: Int,
        val descRes: Int,
        val cornerRadiusDp: Float = 12f,
        /**
         * Light a circle around the target's centre instead of its full
         * rectangle. A grid tile is mostly padding around a small icon, so
         * lighting the whole tile draws the eye to empty space.
         */
        val circular: Boolean = false,
        /** Radius of that circle in dp. Ignored unless [circular]. */
        val circleRadiusDp: Float = 34f
    )

    private var index = 0
    private var overlay: CoachMarkOverlay? = null
    private var card: View? = null
    private var container: FrameLayout? = null

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

        val cardView = activity.layoutInflater.inflate(R.layout.view_coach_bubble, host, false)
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            marginStart = dp(20f).toInt()
            marginEnd = dp(20f).toInt()
            topMargin = dp(20f).toInt()
        }
        host.addView(cardView, lp)
        card = cardView

        cardView.findViewById<TextView>(R.id.tvCoachSkip).setOnClickListener { finish() }
        cardView.findViewById<Button>(R.id.btnCoachNext).setOnClickListener { advance() }

        index = 0
        showStep()
    }

    private fun dp(value: Float): Float = value * activity.resources.displayMetrics.density

    private fun showStep() {
        val step = steps.getOrNull(index) ?: return finish()
        val overlayView = overlay ?: return
        val cardView = card ?: return
        val host = container ?: return

        cardView.findViewById<TextView>(R.id.tvCoachTitle).setText(step.titleRes)
        cardView.findViewById<TextView>(R.id.tvCoachDesc).setText(step.descRes)

        val isLast = index == steps.size - 1
        cardView.findViewById<Button>(R.id.btnCoachNext)
            .setText(if (isLast) R.string.coach_done else R.string.coach_next)

        // Skip stops offering an exit on the final step — there is nothing left
        // to skip past, and "Skip" beside "Got it" only invites a misread.
        cardView.findViewById<TextView>(R.id.tvCoachSkip).visibility =
            if (isLast) View.INVISIBLE else View.VISIBLE

        renderDots(cardView)

        // The feature grid scrolls, so a tile further down can be off-screen
        // when its turn comes. Bring it into view first — a spotlight measured
        // before the scroll would land on empty space.
        scrollIntoView(step.target) {
            // Wait for a layout pass so the target's bounds are current — a bottom
            // nav item can shift slightly on first measure.
            overlayView.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        overlayView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        val rect = CoachMarkOverlay.boundsWithin(step.target, host)
                        overlayView.holePadding = dp(6f)
                        overlayView.holeRadius = dp(step.cornerRadiusDp)
                        overlayView.circular = step.circular
                        overlayView.circleRadius = dp(step.circleRadiusDp)
                        overlayView.holeRect = rect
                        // Post rather than place inline: assigning layoutParams
                        // during a layout pass re-enters layout, which Android
                        // may treat as a loop.
                        cardView.post {
                            try {
                                positionCard(cardView, rect)
                            } catch (e: Exception) {
                                // A misplaced card is a blemish; a crash is not.
                                android.util.Log.e(TAG, "positioning failed", e)
                            }
                        }
                    }
                }
            )
            overlayView.requestLayout()
        }
    }

    /**
     * Put the card directly beneath the tile it describes, so the owner reads
     * the name and the explanation in one downward glance.
     *
     * Centring it looked neater in the abstract and buried the highlighted
     * tile behind the card in practice — the one thing the step exists to
     * show. If a tile sits so low that the card will not fit beneath it, the
     * card goes above instead rather than running off the screen.
     */
    private fun positionCard(cardView: View, target: RectF) {
        // The card lives in the tour's own FrameLayout, so these params are
        // the ones we set and a topMargin means what it says.
        val lp = cardView.layoutParams as? FrameLayout.LayoutParams ?: return
        val host = container ?: return
        val gap = dp(14f).toInt()

        val rootWidth = host.width
        val rootHeight = host.height
        if (rootWidth <= 0 || rootHeight <= 0) return

        val available = (rootWidth - dp(40f).toInt()).coerceAtLeast(1)
        cardView.measure(
            View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cardHeight = cardView.measuredHeight

        // Always below, never above: the owner should read the tile and then
        // its explanation, in that order, on every step. The grid scrolls the
        // tile high enough to leave the room, and the last rows have padding
        // beneath them for the same reason.
        val below = target.bottom.toInt() + gap
        val lowestTop = rootHeight - cardHeight - dp(12f).toInt()
        lp.topMargin = below.coerceAtMost(lowestTop.coerceAtLeast(dp(12f).toInt()))
        cardView.layoutParams = lp
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

        // Park the tile near the top of the viewport. The card always sits
        // beneath it, so anything lower leaves the card nowhere to go — and
        // "somewhere else" would mean the explanation appearing above the
        // thing it explains.
        val topWithin = CoachMarkOverlay.boundsWithin(target, scroller).top.toInt() + scroller.scrollY
        val desired = (topWithin - dp(24f).toInt()).coerceAtLeast(0)
        if (kotlin.math.abs(desired - scroller.scrollY) < dp(4f)) {
            then()
            return
        }
        scroller.smoothScrollTo(0, desired)
        // Let the scroll settle before the bounds are read.
        scroller.postDelayed({ then() }, SCROLL_SETTLE_MS)
    }

    /**
     * The progress dots. Rebuilt each step rather than animated: eight views
     * is nothing, and a rebuild cannot drift out of sync with [index].
     */
    private fun renderDots(cardView: View) {
        val holder = cardView.findViewById<LinearLayout>(R.id.coachDots)
        holder.removeAllViews()
        for (i in steps.indices) {
            val dot = View(activity)
            val size = if (i == index) dp(26f).toInt() else dp(10f).toInt()
            val params = LinearLayout.LayoutParams(size, dp(10f).toInt()).apply {
                marginEnd = dp(6f).toInt()
            }
            dot.layoutParams = params
            dot.setBackgroundResource(
                if (i == index) R.drawable.coach_dot_active else R.drawable.coach_dot_inactive
            )
            holder.addView(dot)
        }
    }

    private fun advance() {
        index++
        if (index >= steps.size) finish() else showStep()
    }

    private fun finish() {
        // Removing the host removes the scrim and the card with it.
        container?.let { root.removeView(it) }
        container = null
        card = null
        overlay = null
        markRun(activity)
    }

    companion object {
        private const val TAG = "CoachMarks"

        /** Long enough for smoothScrollTo to land before bounds are measured. */
        private const val SCROLL_SETTLE_MS = 320L

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
