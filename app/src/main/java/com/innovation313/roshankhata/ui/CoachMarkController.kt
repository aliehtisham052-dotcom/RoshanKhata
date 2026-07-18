package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import com.innovation313.roshankhata.R

/**
 * Drives the first-run Home screen tour: a dimmed scrim with a spotlight hole
 * over one real control at a time, and a small bubble beside it explaining
 * what it does. No full-screen slides — every step points at the actual
 * button the owner will tap later.
 *
 * Shown once. [hasRun] / [markRun] persist that on-device, the same pattern
 * LanguageActivity and BackupReminder already use for their own one-shot flags.
 */
class CoachMarkController(
    private val activity: Activity,
    private val root: ViewGroup,
    private val steps: List<Step>
) {

    /** One stop on the tour: the view to spotlight, and the strings to show beside it. */
    data class Step(
        val target: View,
        val titleRes: Int,
        val descRes: Int,
        /** For a BottomNavigationView item, pass the menu item's id-bearing icon/label view. */
        val cornerRadiusDp: Float = 12f
    )

    private var index = 0
    private var overlay: CoachMarkOverlay? = null
    private var bubble: View? = null

    fun start() {
        if (steps.isEmpty()) return
        val overlayView = CoachMarkOverlay(activity)
        root.addView(
            overlayView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        overlay = overlayView
        index = 0
        showStep()
    }

    private fun dp(value: Float): Float = value * activity.resources.displayMetrics.density

    private fun showStep() {
        val step = steps.getOrNull(index) ?: return finish()
        val overlayView = overlay ?: return

        // Wait one layout pass so target bounds are current — a bottom nav
        // item's position can shift very slightly on first measure.
        overlayView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                overlayView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                positionStep(step, overlayView)
            }
        })
        overlayView.requestLayout()
    }

    private fun positionStep(step: Step, overlayView: CoachMarkOverlay) {
        val rect = CoachMarkOverlay.boundsWithin(step.target, root)
        overlayView.holePadding = dp(6f)
        overlayView.holeRadius = dp(step.cornerRadiusDp)
        overlayView.holeRect = rect

        bubble?.let { root.removeView(it) }
        val bubbleView = activity.layoutInflater.inflate(R.layout.view_coach_bubble, root, false)
        bubble = bubbleView

        bubbleView.findViewById<TextView>(R.id.tvCoachStep).text =
            activity.getString(R.string.coach_step_of, index + 1, steps.size)
        bubbleView.findViewById<TextView>(R.id.tvCoachTitle).setText(step.titleRes)
        bubbleView.findViewById<TextView>(R.id.tvCoachDesc).setText(step.descRes)

        val isLast = index == steps.size - 1
        bubbleView.findViewById<android.widget.Button>(R.id.btnCoachNext).apply {
            setText(if (isLast) R.string.coach_done else R.string.coach_next)
            setOnClickListener { advance() }
        }
        bubbleView.findViewById<TextView>(R.id.tvCoachSkip).setOnClickListener { finish() }

        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val screenHeight = root.height.takeIf { it > 0 } ?: activity.resources.displayMetrics.heightPixels
        val margin = dp(16f).toInt()
        val bubbleWidthPx = dp(280f).toInt()

        // Prefer sitting below the target; flip above it if there isn't room
        // (e.g. the bottom nav sits at the very bottom of the screen).
        val spaceBelow = screenHeight - rect.bottom
        val putBelow = spaceBelow > dp(160f)

        lp.topMargin = if (putBelow) {
            (rect.bottom + dp(10f)).toInt()
        } else {
            (rect.top - dp(10f) - dp(140f)).toInt().coerceAtLeast(margin)
        }

        val rootWidth = root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val centeredStart = (rect.centerX() - bubbleWidthPx / 2f)
            .coerceIn(margin.toFloat(), (rootWidth - bubbleWidthPx - margin).toFloat())
        lp.marginStart = centeredStart.toInt()
        lp.gravity = Gravity.TOP or Gravity.START

        root.addView(bubbleView, lp)
    }

    private fun advance() {
        index++
        if (index >= steps.size) finish() else showStep()
    }

    private fun finish() {
        bubble?.let { root.removeView(it) }
        overlay?.let { root.removeView(it) }
        bubble = null
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
