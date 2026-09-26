package com.innovation313.roshankhata.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.innovation313.roshankhata.R

/**
 * Keep one screen's contents clear of the status and navigation bars.
 *
 * From Android 15 an app is drawn behind those bars whether it asks or not,
 * and from 16 there is no opting out, so every screen has to look right that
 * way before targetSdk moves to 36.
 *
 * This is the mechanism that survived two rounds on a real phone, and only
 * that mechanism. Two attempts taught it what not to do:
 *
 * - The first switched the whole app over at once and worked the padding out
 *   itself; one mistake cut two dozen screens at both ends. Hence: called
 *   explicitly, one screen at a time, from each Activity's onCreate.
 * - The second used fitsSystemWindows, which looked right until the sides
 *   were checked — it writes ALL of a view's padding, so the header's 20dp
 *   ends went to zero and the title sat on the screen's edge. Hence: the
 *   designed padding is read once and the bars are only ever ADDED to it.
 *   Run twice, the sum is the same; the design is never overwritten.
 *
 * What it does:
 * - A header (R.id.header) takes the status bar and cutout on top of its own
 *   padding, so its colour runs up behind the clock as it always did.
 * - The root takes the gesture bar at the bottom — except when the screen has
 *   a BottomNavigationView, which insets itself, and helping it would double
 *   the gap.
 * - A screen with no header takes the status bar on the root instead.
 */
object ScreenInsets {

    fun on(activity: Activity) {
        val window = activity.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
            ?.getChildAt(0) ?: return
        val header: View? = activity.findViewById(R.id.header)
        val ownBottomBar: View? = activity.findViewById(R.id.bottomNav)

        // Start/end, never left/right. A layout's padding is written as start
        // and end so it mirrors in Arabic, Farsi, Sindhi and Urdu. Reading it
        // back as left/right and writing it with setPadding() froze it in its
        // left-to-right form: the ledger header (16dp start, 4dp end) came out
        // 16dp on the LEFT and 4dp on the right in those languages, so its
        // Call button and avatar sat hard against the right-hand edge while
        // an empty band opened up on the left. paddingStart/End read the
        // start and end values whichever way the view has resolved, and
        // setPaddingRelative() lets the view mirror them itself.
        if (header != null) {
            val base = intArrayOf(
                header.paddingStart, header.paddingTop,
                header.paddingEnd, header.paddingBottom
            )
            ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                v.setPaddingRelative(
                    base[0] + bars.startOf(v), base[1] + bars.top,
                    base[2] + bars.endOf(v), base[3]
                )
                insets
            }
        }

        val rootBase = intArrayOf(
            root.paddingStart, root.paddingTop,
            root.paddingEnd, root.paddingBottom
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            val top = if (header == null) rootBase[1] + bars.top else rootBase[1]
            val bottom = if (ownBottomBar == null) rootBase[3] + bars.bottom else rootBase[3]
            val start = if (header == null) rootBase[0] + bars.startOf(v) else rootBase[0]
            val end = if (header == null) rootBase[2] + bars.endOf(v) else rootBase[2]
            v.setPaddingRelative(start, top, end, bottom)
            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    /*
     * System-bar and cutout insets are physical: left and right. The screen's
     * direction is taken from the configuration, which is settled before the
     * first inset pass, rather than from the view, which may not have
     * resolved its own direction yet at that moment.
     */
    private fun rtl(v: View) =
        v.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    private fun Insets.startOf(v: View) = if (rtl(v)) right else left
    private fun Insets.endOf(v: View) = if (rtl(v)) left else right
}
