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
