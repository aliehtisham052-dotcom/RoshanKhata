package com.innovation313.roshankhata.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.innovation313.roshankhata.R

/**
 * Keep a screen's contents clear of the status and navigation bars.
 *
 * From Android 15 an app is drawn behind those bars whether it asks to be or
 * not, and from Android 16 there is no opting out. This app had no handling
 * for it at all: its coloured header would have slid under the clock and its
 * bottom bar under the gesture line. The theme's statusBarColor and
 * navigationBarColor, which used to do this job, are ignored from 35 onward.
 *
 * Done in one place because there are two dozen screens and touching each by
 * hand is two dozen chances to miss one. [applyToAllScreens] hangs this off
 * the application, so a screen added next year is covered without anyone
 * remembering to cover it.
 *
 * The rule follows the shape the layouts already have:
 *
 * - A screen with a coloured header pads the header, not the page. The colour
 *   then runs up behind the status bar as it did before, instead of leaving a
 *   pale band above it.
 * - A screen with a bottom bar pads the bar.
 * - Anything else pads the page itself.
 *
 * Sides are always padded on the page, for the notch in landscape.
 */
object EdgeToEdge {

    /**
     * Apply to every screen in the app, now and later.
     *
     * Registered against the application rather than written into each
     * Activity: onActivityCreated runs after the screen has set its layout, so
     * the views are there to pad by then.
     */
    fun applyToAllScreens(app: android.app.Application) {
        app.registerActivityLifecycleCallbacks(
            object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: android.os.Bundle?) = apply(a)
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityResumed(a: Activity) = Unit
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, o: android.os.Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) = Unit
            }
        )
    }

    /** Wire up one screen. Safe to call twice. */
    fun apply(activity: Activity) {
        val window = activity.window ?: return
        // Ask for the behaviour now rather than inheriting it later, so what is
        // on screen at targetSdk 34 is what will be on screen at 36. Otherwise
        // this work could only be checked by the same change that ships it.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val page = (window.decorView.findViewById<ViewGroup>(android.R.id.content))
            ?.getChildAt(0) ?: return

        val header = activity.findViewById<View>(R.id.header)
        val bottomBar = activity.findViewById<View>(R.id.bottomNav)

        // Whatever is padded keeps the padding it was designed with, so this
        // can run more than once without the gaps growing each time.
        val pageStart = page.paddingTop
        val pageEnd = page.paddingBottom
        val headerStart = header?.paddingTop ?: 0
        val barEnd = bottomBar?.paddingBottom ?: 0

        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            if (header != null) {
                header.updatePadding(top = headerStart + bars.top)
                view.updatePadding(top = pageStart)
            } else {
                view.updatePadding(top = pageStart + bars.top)
            }

            if (bottomBar != null) {
                bottomBar.updatePadding(bottom = barEnd + bars.bottom)
                view.updatePadding(bottom = pageEnd)
            } else {
                view.updatePadding(bottom = pageEnd + bars.bottom)
            }

            view.updatePadding(left = bars.left, right = bars.right)
            insets
        }

        ViewCompat.requestApplyInsets(page)
    }

    /**
     * The same for a dialog that fills the window.
     *
     * A dialog has a window of its own, so the screen's handling does not reach
     * it, and the entry form covers the display end to end — its Save button
     * would sit under the gesture line.
     */
    fun applyToDialogView(root: View) {
        val start = root.paddingTop
        val end = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = start + bars.top, bottom = end + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
