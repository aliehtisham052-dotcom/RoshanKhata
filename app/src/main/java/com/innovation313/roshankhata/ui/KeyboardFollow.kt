package com.innovation313.roshankhata.ui

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R

/**
 * Keep the field being typed in above the phone's keyboard (18 Oct).
 *
 * Why this exists: the app draws behind the status and navigation bars
 * (ScreenInsets; forced on every app from Android 15). Once an app does that,
 * Android stops shrinking the screen for the keyboard, even with
 * windowSoftInputMode="adjustResize": it only REPORTS how much of the window
 * the keyboard covers and leaves the rest to the app. ScreenInsets used to
 * pad for the bars but not the keyboard, so on the Poster's Words step (and
 * every other form) the keyboard opened over the very line being typed —
 * what the owner filmed.
 *
 * Two parts:
 *  - [imeBottom]: how much of THIS window the keyboard covers. Zero while it
 *    is hidden, and zero too when Android has already moved the window clear
 *    of it (a small dialog), so padding by it never doubles a gap.
 *  - [reveal]: after the window has made room, scroll the nearest scrolling
 *    parent until the focused field sits a little above the keyboard. Room
 *    alone is not enough: a field already low on the screen stays where it
 *    was, now under the padding instead of under the keyboard.
 */
object KeyboardFollow {

    /** Space kept between the field's bottom edge and the keyboard. */
    private const val GAP_DP = 16

    /** How much of the window the keyboard covers right now, in pixels. */
    fun imeBottom(insets: WindowInsetsCompat): Int =
        if (insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        else 0

    /**
     * Watch [root] for focus moving between fields while the keyboard is up
     * (tapping the next box down), and bring each newly focused one into view.
     * Safe to call more than once: the listener is added only the first time.
     */
    fun keepFocusedVisible(root: View) {
        if (root.getTag(R.id.tag_keyboard_follow) != null) return
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, now ->
            if (now != null && isInside(now, root)) root.post { reveal(root) }
        }
        root.setTag(R.id.tag_keyboard_follow, listener)
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
    }

    /**
     * Scroll so the focused text field in [root] ends above the keyboard.
     * Does nothing when the keyboard is hidden, nothing is focused, the field
     * is already clear, or nothing above it scrolls.
     */
    fun reveal(root: View) {
        val insets = ViewCompat.getRootWindowInsets(root) ?: return
        if (!insets.isVisible(WindowInsetsCompat.Type.ime())) return
        val field = root.findFocus() ?: return
        if (!field.onCheckIsTextEditor()) return

        // Where the window's usable area ends. A root that took the keyboard
        // as bottom padding (ScreenInsets, dialogs below) ends at its padding;
        // a window Android itself shrank ends at its own bottom. Either way
        // that is the top of the keyboard.
        val at = IntArray(2)
        root.getLocationInWindow(at)
        val usableBottom = at[1] + root.height - root.paddingBottom
        val keyboardTop = minOf(usableBottom, windowHeight(root) - imeBottom(insets))

        field.getLocationInWindow(at)
        val fieldTop = at[1]
        val fieldBottom = fieldTop + field.height
        val gap = (GAP_DP * root.resources.displayMetrics.density).toInt()
        val need = fieldBottom + gap - keyboardTop
        if (need <= 0) return

        val scroller = scrollingParent(field, root) ?: return
        scroller.getLocationInWindow(at)
        // Never push the field's top off the top of the scrolling area: for a
        // tall note box, showing where the typing starts matters more.
        val room = fieldTop - (at[1] + scroller.paddingTop) - gap
        val dy = minOf(need, maxOf(room, 0))
        if (dy > 0) scroller.scrollBy(0, dy)
    }

    /**
     * For a dialog or bottom sheet with text fields: its window reports the
     * keyboard rather than moving out of its way, so its content takes the
     * covered part as bottom padding and the focused field is scrolled up.
     * Call after show(). When Android does move the window, the covered part
     * is zero and this changes nothing.
     */
    fun dialog(dialog: Dialog) {
        val window = dialog.window ?: return
        val state = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
        window.setSoftInputMode(state or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val content = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        keepFocusedVisible(content)
        // A small floating dialog is sized to its contents and centred:
        // padding it would make it taller, which moves it, which changes how
        // much the keyboard covers, and it would twitch. Those only scroll.
        if (window.isFloating) {
            ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
                if (imeBottom(insets) > 0) v.post { reveal(v) }
                insets
            }
            return
        }
        val base = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val ime = imeBottom(insets)
            // Only the part of the keyboard that actually lies over this
            // window (a full-screen window: all of it).
            val covered = coveredBy(v, ime, v.paddingBottom - base)
            if (v.paddingBottom != base + covered) {
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, base + covered)
            }
            if (ime > 0) v.post { reveal(v) }
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    /**
     * How many pixels of [v]'s bottom the keyboard (ime px tall) covers.
     * [added] is the keyboard padding already on it, taken back out first.
     */
    private fun coveredBy(v: View, ime: Int, added: Int): Int {
        if (ime <= 0) return 0
        val at = IntArray(2)
        v.getLocationInWindow(at)
        val contentBottom = at[1] + v.height - added.coerceAtLeast(0)
        val keyboardTop = windowHeight(v) - ime
        return (contentBottom - keyboardTop).coerceAtLeast(0)
    }

    private fun windowHeight(v: View): Int = v.rootView.height

    private fun isInside(view: View, root: View): Boolean {
        var p: Any? = view
        while (p is View) {
            if (p === root) return true
            p = p.parent
        }
        return false
    }

    /** The nearest parent of [field], up to [root], that scrolls vertically. */
    private fun scrollingParent(field: View, root: View): View? {
        var p = field.parent
        while (p is View) {
            if (p is ScrollView || p is NestedScrollView || p is RecyclerView) return p
            if (p === root) return null
            p = p.parent
        }
        return null
    }
}
