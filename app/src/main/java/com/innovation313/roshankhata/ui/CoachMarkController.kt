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
        val cornerRadiusDp: Float = 12f
    )

    private var index = 0
    private var overlay: CoachMarkOverlay? = null
    private var card: View? = null

    fun start() {
        if (steps.isEmpty()) return
        val overlayView = CoachMarkOverlay(activity)
        root.addView(
            overlayView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // Swallow taps on the scrim: during the tour the only ways forward are
        // NEXT and Skip, so a stray tap cannot fire the control underneath.
        overlayView.isClickable = true
        overlayView.isFocusable = true
        overlay = overlayView

        val cardView = activity.layoutInflater.inflate(R.layout.view_coach_bubble, root, false)
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            // Placed per step by positionCard; these are only the side insets.
            gravity = Gravity.TOP or Gravity.START
            marginStart = dp(20f).toInt()
            marginEnd = dp(20f).toInt()
        }
        root.addView(cardView, lp)
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
                        val rect = CoachMarkOverlay.boundsWithin(step.target, root)
                        overlayView.holePadding = dp(6f)
                        overlayView.holeRadius = dp(step.cornerRadiusDp)
                        overlayView.holeRect = rect
                        // Post rather than place inline: assigning layoutParams
                        // during a layout pass re-enters layout, which Android
                        // may treat as a loop.
                        cardView.post { positionCard(cardView, rect) }
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
        val lp = cardView.layoutParams as FrameLayout.LayoutParams
        val gap = dp(14f).toInt()

        // Measure at the width the card will actually occupy.
        val available = root.width - dp(40f).toInt()
        cardView.measure(
            View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cardHeight = cardView.measuredHeight

        val below = target.bottom.toInt() + gap
        val fitsBelow = below + cardHeight <= root.height - dp(12f)

        lp.gravity = Gravity.TOP or Gravity.START
        lp.topMargin = if (fitsBelow) {
            below
        } else {
            (target.top.toInt() - gap - cardHeight).coerceAtLeast(dp(12f).toInt())
        }
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

        // Aim to land the tile in the upper third. The card sits beneath it,
        // so a tile parked mid-screen would leave the card nowhere to go.
        val topWithin = CoachMarkOverlay.boundsWithin(target, scroller).top.toInt() + scroller.scrollY
        val desired = (topWithin - scroller.height / 5).coerceAtLeast(0)
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
        card?.let { root.removeView(it) }
        overlay?.let { root.removeView(it) }
        card = null
        overlay = null
        markRun(activity)
    }

    companion object {
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
