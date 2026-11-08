package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
 *   the gap. While the keyboard is up it takes the keyboard instead, and the
 *   field being typed in is scrolled above it (KeyboardFollow).
 * - A screen with no header takes the status bar on the root instead.
 */
object ScreenInsets {

    fun on(activity: Activity) {
        val window = activity.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
            ?.getChildAt(0) ?: return
        val header: View? = activity.findViewById(R.id.header)
        // No screen has a bottom bar of its own since 7 Oct (the Khata screen's
        // Home/More bar went into the header's ⋮), so the root always takes
        // the navigation-bar inset as bottom padding.
        val ownBottomBar: View? = null

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
                // The sides are the root's now (below), so they are not added twice here.
                v.setPaddingRelative(base[0], base[1] + bars.top, base[2], base[3])
                insets
            }
        }

        // Android 15+ ignores the theme's status-bar colour (23 Oct 2026, review R34): the bar is
        // see-through, and on a screen with no header what shows behind the clock is the root's own
        // page colour, not the dark bar the theme's light icons were chosen for. So the icons follow
        // that colour: dark on the day's pale page, light on the night's. A screen with a header keeps
        // the theme's light icons over its dark header; a screen that sets its own after this call wins.
        if (header == null && Build.VERSION.SDK_INT >= 35) {
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = isLight(root)
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
            // The keyboard too (18 Oct): drawn behind the bars, the screen is
            // no longer shrunk for the keyboard (adjustResize is ignored), so
            // without this every form's lower fields typed out of sight.
            // The larger of the two, never the sum: the keyboard sits over
            // the gesture bar, not above it.
            val ime = KeyboardFollow.imeBottom(insets)
            val top = if (header == null) rootBase[1] + bars.top else rootBase[1]
            val bottom = rootBase[3] + if (ownBottomBar == null) maxOf(bars.bottom, ime) else ime
            // The side bars always (21 Oct 2026, the critical review): with a header the sides were
            // left to it, and a phone turned on its side put the list under the navigation bar.
            val start = rootBase[0] + bars.startOf(v)
            val end = rootBase[2] + bars.endOf(v)
            v.setPaddingRelative(start, top, end, bottom)
            // Room alone leaves a low field under the new padding; scroll it
            // up once the smaller screen has been laid out.
            if (ime > 0) v.post { KeyboardFollow.reveal(v) }
            insets
        }
        KeyboardFollow.keepFocusedVisible(root)

        ViewCompat.requestApplyInsets(root)
    }

    /** Whether [root]'s own background is pale (dark icons read on it); by the night mode when it is not one colour. */
    private fun isLight(root: View): Boolean {
        // A see-through root shows the window's page colour, which follows the night mode as well.
        val colour = (root.background as? ColorDrawable)?.color?.takeIf { android.graphics.Color.alpha(it) > 0 }
        if (colour != null) return androidx.core.graphics.ColorUtils.calculateLuminance(colour) > 0.5
        val night = (root.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return !night
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
