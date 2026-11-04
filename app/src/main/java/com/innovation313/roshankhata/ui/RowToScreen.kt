package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.Window
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback

/**
 * A customer row growing into that customer's ledger, and folding back into
 * the row on Back (5 Oct) — Material's container transform, between two
 * activities. Both halves are called before super.onCreate, as the platform
 * requires. Where there is no row on screen, or animations are off, the
 * screen opens the usual way (the app's ScreenChange fade).
 */
object RowToScreen {

    private const val NAME = "party_row"

    /** In the list's onCreate, before super.onCreate. */
    fun send(activity: Activity) {
        activity.window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        activity.setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        activity.window.sharedElementsUseOverlay = false
    }

    /** In the ledger's onCreate, before super.onCreate. */
    fun receive(activity: Activity) {
        activity.window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        activity.findViewById<View>(android.R.id.content).transitionName = NAME
        activity.setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        activity.window.sharedElementEnterTransition = MaterialContainerTransform().apply {
            addTarget(android.R.id.content)
            duration = 340
            scrimColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
        }
        activity.window.sharedElementReturnTransition = MaterialContainerTransform().apply {
            addTarget(android.R.id.content)
            duration = 280
            scrimColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
        }
    }

    /**
     * Back on the list, once the fold-back has had its time: every row on screen plain again
     * (1 Nov 2026, the owner's photo of a blank card-sized gap in Khata). The framework hides the
     * row that grew into the ledger and shows it again only at the end of the return transition;
     * when that return was cut short (the list changed while the ledger was open - a new entry
     * moves the customer - or the ledger closed another way), the row stayed hidden, and the
     * RecyclerView then gave that hidden row to another customer. Its transition name went with
     * it, so a later return could hide the wrong row again.
     */
    fun settle(list: androidx.recyclerview.widget.RecyclerView) {
        list.postDelayed({
            for (i in 0 until list.childCount) plain(list.getChildAt(i))
            // And every row in its own place (4 Nov 2026, the owner's photo: a wide gap above one card, two cards
            // touching below it). At the end of the fold-back the framework puts the shared row back by measuring
            // and laying it out at the bounds it had when the ledger opened (ActivityTransitionCoordinator's
            // setOriginalSharedElementState). When the list changed meanwhile (entries deleted, the customer moved),
            // that view already stood for another customer at another place: it was laid over its neighbours at the
            // old place and size, and a scroll only moves rows by an offset, so it stayed there. A layout pass
            // puts every row back where the list's own order says.
            list.requestLayout()
        }, SETTLE_MS)
    }

    /** One row: visible, opaque, no transition name (also every bind - a recycled row starts plain). */
    fun plain(row: View) {
        row.transitionName = null
        if (row.visibility != View.VISIBLE) row.visibility = View.VISIBLE
        if (row.alpha != 1f) row.alpha = 1f
    }

    /** Longer than the 280 ms fold-back, so a return that runs is never cut. */
    private const val SETTLE_MS = 600L

    fun start(activity: Activity, intent: Intent, row: View?) {
        if (row == null || !Motion.enabled(activity) || !row.isAttachedToWindow) {
            activity.startActivity(intent)
            return
        }
        row.transitionName = NAME
        val options = ActivityOptions.makeSceneTransitionAnimation(activity, row, NAME)
        activity.startActivity(intent, options.toBundle())
    }
}
