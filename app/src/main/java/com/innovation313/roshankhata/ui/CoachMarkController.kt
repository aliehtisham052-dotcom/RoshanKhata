package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
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
            gravity = Gravity.CENTER
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

        // Wait for a layout pass so the target's bounds are current — a bottom
        // nav item can shift slightly on first measure.
        overlayView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    overlayView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    overlayView.holePadding = dp(6f)
                    overlayView.holeRadius = dp(step.cornerRadiusDp)
                    overlayView.holeRect = CoachMarkOverlay.boundsWithin(step.target, root)
                }
            }
        )
        overlayView.requestLayout()
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
