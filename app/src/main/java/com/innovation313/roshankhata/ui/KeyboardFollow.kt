package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import kotlin.math.abs

/**
 * The box being typed in stays in the middle of what can be seen (18 Oct; 6 Nov 2026).
 *
 * Why this exists: the app draws behind the status and navigation bars
 * (ScreenInsets; forced on every app from Android 15). Once an app does that,
 * Android stops shrinking the screen for the keyboard, even with
 * windowSoftInputMode="adjustResize": it only REPORTS how much of the window
 * the keyboard covers and leaves the rest to the app.
 *
 * Three parts:
 *  - Room: a window makes room for the keyboard ONCE. A screen does it in
 *    ScreenInsets; a dialog here ([dialog], or by itself through [watch]).
 *    Whatever takes the keyboard as padding hands its children insets without
 *    it ([withoutKeyboard]). Until 6 Nov it handed them on whole, and a bottom
 *    sheet's own container (fitsSystemWindows, adjustResize from Material's
 *    theme) took the keyboard a second time: on Android 15 the invoice's
 *    "Discount, tax & payment" sheet stood a keyboard's height above the
 *    keyboard, its fields squeezed to nothing between the title and Apply, an
 *    empty band under it (the owner's photo; the API 35 keyboard run measured
 *    a field 867 px above the keyboard).
 *  - The middle ([reveal], [centre]): the focused box is scrolled to the middle
 *    of the part of its scrolling parent that can be seen — above the keyboard
 *    (or the app's own pad), below the title, inside any pinned footer — never
 *    only to the keyboard's edge (the owner, 6 Nov: "bilkul mid mein, taake
 *    likhai nazar aaye"). A box near the top of a form stays where it is: the
 *    scroll cannot go above the form's start. A note box taller than half the
 *    space puts the line being typed on there instead.
 *  - Every box, by itself ([watchAll], [watch]): every layout made in code goes
 *    through TextFit.relax and every box made in code through FormBox.style;
 *    both hand their boxes here. Once a box is in a window, that window gets
 *    the room and the middle, unless its own code already gives them (a screen
 *    through ScreenInsets; the setup pages, the shop-type and country pages).
 */
object KeyboardFollow {

    /** Space kept between the box and the edge of what can be seen. */
    private const val GAP_DP = 16

    /** A box in sight this close to the middle is left still: no scroll for a few pixels. */
    private const val STILL_DP = 24

    /** A visible part smaller than this is not a place to centre anything in. */
    private const val MIN_SEEN_DP = 48

    /** How much of the window the keyboard covers right now, in pixels. */
    fun imeBottom(insets: WindowInsetsCompat): Int =
        if (insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        else 0

    // ------------------------------------------------------------------ every box, by itself

    /** Every text box under [root] ([watch]). TextFit.relax runs this on every layout made in code. */
    fun watchAll(root: View?) {
        when (root) {
            null -> return
            is EditText -> watch(root)
            is ViewGroup -> for (i in 0 until root.childCount) watchAll(root.getChildAt(i))
        }
    }

    /**
     * Once [box] is in a window, that window keeps the box being typed in in the middle of what can
     * be seen and makes room for the keyboard — unless its own code already does. FormBox.style runs
     * this on every box made in code. Safe to call more than once.
     */
    fun watch(box: View) {
        if (box.getTag(R.id.tag_keyboard_watch) != null) return
        box.setTag(R.id.tag_keyboard_watch, true)
        box.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = follow(v)
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        if (box.isAttachedToWindow) follow(box)
    }

    /** The window [box] was just put in. */
    private fun follow(box: View) {
        val decor = box.rootView
        if (decor.getTag(R.id.tag_keyboard_window) != null) return
        val content = decor.findViewById<View>(android.R.id.content) as? ViewGroup ?: return
        val activity = activityOf(box.context)
        if (activity == null || activity.window?.decorView === decor) {
            // A screen: its room is its own (ScreenInsets on every screen); only the middle here.
            keepFocusedVisible(content)
            return
        }
        // A dialog or a sheet (an application window of the screen's), not a drop-down's panel.
        val params = decor.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.type < WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW ||
            params.type > WindowManager.LayoutParams.LAST_APPLICATION_WINDOW
        ) return
        install(content, floating = isFloating(decor))
    }

    // ------------------------------------------------------------------ a window

    /**
     * Watch [root] for focus moving between fields while the keyboard is up
     * (tapping the next box down), and bring each newly focused one to the middle.
     * Marks [root]'s window as looked after, so [watch] leaves it to its own code.
     * Safe to call more than once: the listener is added only the first time.
     */
    fun keepFocusedVisible(root: View) {
        root.rootView.setTag(R.id.tag_keyboard_window, true)
        if (root.getTag(R.id.tag_keyboard_follow) != null) return
        val listener = ViewTreeObserver.OnGlobalFocusChangeListener { _, now ->
            if (now != null && isInside(now, root)) root.post { reveal(root) }
        }
        root.setTag(R.id.tag_keyboard_follow, listener)
        root.viewTreeObserver.addOnGlobalFocusChangeListener(listener)
    }

    /**
     * For a dialog or bottom sheet with text fields: the window is set to adjustResize, room is made
     * for the keyboard once (a floating dialog is moved clear of it by the phone itself; a full-screen
     * one or a sheet takes the covered part as its content's bottom padding) and the focused box is
     * brought to the middle. Call after show(). Every dialog whose boxes went through TextFit.relax or
     * FormBox.style gets the same by itself; this stays for the ones that say it outright.
     */
    fun dialog(dialog: Dialog) {
        val window = dialog.window ?: return
        val state = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
        window.setSoftInputMode(state or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val content = window.decorView.findViewById<View>(android.R.id.content) as? ViewGroup ?: return
        install(content, window.isFloating)
    }

    /** Room and the middle for a dialog's window, once. */
    private fun install(content: ViewGroup, floating: Boolean) {
        if (content.rootView.getTag(R.id.tag_keyboard_window) != null) return
        keepFocusedVisible(content)
        val base = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            var out = insets
            // A small floating dialog is sized to its contents and centred: padding it would make it
            // taller, which moves it, which changes how much the keyboard covers, and it would
            // twitch. adjustResize moves those clear of the keyboard; they only need the middle.
            if (!floating) {
                val ime = imeBottom(insets)
                val covered = coveredBy(v, ime)
                if (v.paddingBottom != base + covered) {
                    v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, base + covered)
                }
                // Taken here, so taken once: a sheet's container (fitsSystemWindows) must not take it again.
                if (ime > 0) out = withoutKeyboard(insets)
            }
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) v.post { reveal(v) }
            out
        }
        // What still lies under the keyboard once the window is laid out (6 Nov 2026, the API 30 keyboard
        // run): below Android 15 the window's own frame (AppCompat's) took part of the keyboard and handed
        // the rest on spent, and a sheet stood a navigation bar's height under the keyboard, Apply hidden.
        // Measured after the layout, against the window's own keyboard, whoever took the rest.
        if (!floating) content.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.post {
                val ime = ViewCompat.getRootWindowInsets(v)?.let { imeBottom(it) } ?: 0
                val want = base + coveredBy(v, ime)
                if (v.paddingBottom != want) {
                    v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, want)
                    if (ime > 0) v.post { reveal(v) }
                }
            }
        }
        // A window the phone made smaller for the keyboard (a floating dialog, adjustResize): its box
        // goes to the middle of what is left. Below Android 11 such a window is not told the keyboard
        // is up at all, so this is the only signal there.
        content.addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (oldBottom != oldTop && bottom - top < oldBottom - oldTop) v.post { revealFocused(v) }
        }
        ViewCompat.requestApplyInsets(content)
    }

    /** How many pixels of [v]'s bottom the keyboard ([ime] px tall) covers. Padding never moves [v]'s edges. */
    private fun coveredBy(v: View, ime: Int): Int {
        if (ime <= 0) return 0
        val at = IntArray(2)
        v.getLocationInWindow(at)
        val keyboardTop = windowHeight(v) - ime
        return (at[1] + v.height - keyboardTop).coerceIn(0, ime)
    }

    /**
     * [insets] with the keyboard taken out, for the children of a view that already took it as
     * padding. The navigation bar's foot goes with it: the keyboard sits over the gesture bar, and
     * the padding already lifted everything above both.
     */
    private fun withoutKeyboard(insets: WindowInsetsCompat): WindowInsetsCompat {
        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        return WindowInsetsCompat.Builder(insets)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE)
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(nav.left, nav.top, nav.right, 0))
            .build()
    }

    // ------------------------------------------------------------------ the middle

    /**
     * While the keyboard is up: the focused text box in [root] to the middle of what can be seen.
     * Does nothing when the keyboard is hidden, nothing is focused, or nothing above the box scrolls.
     */
    fun reveal(root: View) {
        val insets = ViewCompat.getRootWindowInsets(root) ?: return
        if (!insets.isVisible(WindowInsetsCompat.Type.ime())) return
        revealFocused(root)
    }

    /** The focused text box in [root] to the middle, whatever is up below it. */
    private fun revealFocused(root: View) {
        val group = root as? ViewGroup ?: return
        // A Compose screen's own view says it edits text; its fields bring themselves into view.
        val field = root.findFocus() as? TextView ?: return
        if (!field.onCheckIsTextEditor() || !isInside(field, root)) return
        bringToMiddle(field, group)
    }

    /**
     * [field] to the middle of what can be seen in its window: for a box typed in with the app's own
     * calculator pad (CalcPad), which takes the keyboard's place without the keyboard's signals. The
     * pad is laid out under the form, so the form's own box already ends above it; a keyboard still
     * on its way down is not counted (the window's padding for it is, until it is gone).
     */
    fun centre(field: View) {
        if (field !is TextView || !field.isAttachedToWindow || !field.isFocused) return
        val content = field.rootView.findViewById<View>(android.R.id.content) as? ViewGroup ?: return
        if (!isInside(field, content)) return
        bringToMiddle(field, content, withKeyboard = false, smooth = false)
    }

    /**
     * [centre] once laid out, and again once a keyboard that was up for another box has gone (the pad
     * takes its place, and the form grows by the keyboard's height when it leaves). Nothing waits longer
     * than [SETTLE_MS]: a keyboard that stays up is the pad's business, not this.
     */
    fun centreWhenSettled(field: View) {
        field.post { centre(field) }
        // Every layout until it settles, not only the first: a scroll that grows as the keyboard leaves
        // scrolls its focused box back into sight itself (onSizeChanged) and stops a scroll under way.
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val insets = ViewCompat.getRootWindowInsets(field) ?: return
                if (insets.isVisible(WindowInsetsCompat.Type.ime())) return
                field.post { centre(field) }
            }
        }
        field.viewTreeObserver.addOnGlobalLayoutListener(listener)
        field.postDelayed({
            if (field.viewTreeObserver.isAlive) field.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }, SETTLE_MS)
    }

    /** How long [centreWhenSettled] waits for a keyboard to go. */
    private const val SETTLE_MS = 1500L

    private fun bringToMiddle(field: TextView, root: ViewGroup, withKeyboard: Boolean = true, smooth: Boolean = true) {
        // Best effort, run a moment after the keyboard or the focus moved: a box that left its window
        // in between is let go, never a crash.
        try {
            middle(field, root, withKeyboard, smooth)
        } catch (e: IllegalArgumentException) {
            // offsetDescendantRectToMyCoords: no longer under root.
        }
    }

    private fun middle(field: TextView, root: ViewGroup, withKeyboard: Boolean, smooth: Boolean) {
        val d = root.resources.displayMetrics.density
        val gap = (GAP_DP * d).toInt()
        val still = (STILL_DP * d).toInt()
        val ime = if (!withKeyboard) 0 else ViewCompat.getRootWindowInsets(root)?.let { imeBottom(it) } ?: 0
        // What can be seen ends at the root's usable bottom (a window that took the keyboard as
        // padding ends at its padding) or the keyboard's top, whichever is higher. Root coordinates
        // throughout, from the layout itself: a sheet sliding with the keyboard (Material's insets
        // animation moves it by translation) is measured where it will stop, not where it is.
        val at = IntArray(2)
        root.getLocationInWindow(at)
        var seenBottom = root.height - root.paddingBottom
        if (ime > 0) seenBottom = minOf(seenBottom, windowHeight(root) - ime - at[1])

        var scroller = scrollingParent(field, root)
        while (scroller != null) {
            val frame = frameIn(root, scroller)
            val top = frame.top + scroller.paddingTop
            val bottom = minOf(frame.bottom - scroller.paddingBottom, seenBottom)
            if (bottom - top >= (MIN_SEEN_DP * d).toInt()) {
                val (fieldTop, fieldBottom) = span(field, root, bottom - top)
                val dy = shift(fieldTop, fieldBottom, top, bottom, gap, still)
                if (dy == 0) return
                if (canMove(scroller, dy)) {
                    if (smooth) smoothBy(scroller, dy) else scroller.scrollBy(0, dy)
                    return
                }
            }
            // This one cannot move it (its end, or too little of it in sight): the one around it may.
            scroller = scrollingParent(scroller, root)
        }
    }

    /**
     * How far to scroll (positive: the content goes up) so a box spanning [fieldTop]..[fieldBottom]
     * sits in the middle of [top]..[bottom], [gap] clear of both edges; a box taller than that space
     * is put at its top. Zero when the box is in sight within [still] of where it would go.
     * Public for the unit test.
     */
    fun shift(fieldTop: Int, fieldBottom: Int, top: Int, bottom: Int, gap: Int, still: Int): Int {
        val seen = bottom - top
        val height = fieldBottom - fieldTop
        val wantTop = if (height >= seen - 2 * gap) top + gap else top + (seen - height) / 2
        val dy = fieldTop - wantTop
        val inSight = fieldTop >= top + gap && fieldBottom <= bottom - gap
        return if (inSight && abs(dy) <= still) 0 else dy
    }

    /** The part of [field] that must be seen, in [root]'s coordinates: the box, or in a tall box the line being typed on. */
    private fun span(field: TextView, root: ViewGroup, seen: Int): Pair<Int, Int> {
        val box = frameIn(root, field)
        val layout = field.layout
        if (field.height <= seen / 2 || layout == null) return box.top to box.bottom
        val line = layout.getLineForOffset(maxOf(0, field.selectionEnd))
        val y = box.top + field.totalPaddingTop - field.scrollY
        return (y + layout.getLineTop(line)) to (y + layout.getLineBottom(line))
    }

    /**
     * [v]'s frame in [root]'s own box (0 at its top edge, as it sits on the screen), by the layout: scrolls
     * counted, translations not. A root that scrolls itself (a screen whose root is its ScrollView, the
     * business card) has its own scroll taken off too: offsetDescendantRectToMyCoords answers in the
     * root's content, and the card's lower boxes were measured a page too low and scrolled a page too far
     * (6 Nov 2026, the keyboard run: "field bottom -1255").
     */
    private fun frameIn(root: ViewGroup, v: View): Rect {
        if (v === root) return Rect(0, 0, v.width, v.height)
        val r = Rect(v.scrollX, v.scrollY, v.scrollX + v.width, v.scrollY + v.height)
        root.offsetDescendantRectToMyCoords(v, r)
        r.offset(-root.scrollX, -root.scrollY)
        return r
    }

    /**
     * Whether [scroller] can move [dy] at all. A ScrollView's own canScrollVertically leaves its padding out
     * (its extent is its whole height), so a screen whose root is its ScrollView, padded by the keyboard, read
     * "cannot scroll" with its last boxes under the keyboard (6 Nov 2026, the business card's Website and QR
     * message); the room is measured as the ScrollView itself clamps a scroll.
     */
    private fun canMove(scroller: View, dy: Int): Boolean {
        if (scroller is ScrollView || scroller is NestedScrollView) {
            val child = (scroller as ViewGroup).getChildAt(0) ?: return false
            val seen = scroller.height - scroller.paddingTop - scroller.paddingBottom
            val max = maxOf(0, child.height - seen)
            return if (dy > 0) scroller.scrollY < max else scroller.scrollY > 0
        }
        return scroller.canScrollVertically(if (dy > 0) 1 else -1)
    }

    private fun smoothBy(scroller: View, dy: Int) {
        when (scroller) {
            is NestedScrollView -> scroller.smoothScrollBy(0, dy)
            is ScrollView -> scroller.smoothScrollBy(0, dy)
            is RecyclerView -> scroller.smoothScrollBy(0, dy)
            else -> scroller.scrollBy(0, dy)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun windowHeight(v: View): Int = v.rootView.height

    private fun isInside(view: View, root: View): Boolean {
        var p: Any? = view
        while (p is View) {
            if (p === root) return true
            p = p.parent
        }
        return false
    }

    /** The nearest parent of [view], up to [root], that scrolls up and down. */
    private fun scrollingParent(view: View, root: View): View? {
        if (view === root) return null
        var p = view.parent
        while (p is View) {
            if (p is ScrollView || p is NestedScrollView) return p
            if (p is RecyclerView && p.layoutManager?.canScrollVertically() == true) return p
            if (p === root) return null
            p = p.parent
        }
        return null
    }

    /** Whether the window whose root view is [decor] floats (a dialog card) — its theme's word. */
    private fun isFloating(decor: View): Boolean {
        val a = decor.context.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating))
        return try { a.getBoolean(0, false) } finally { a.recycle() }
    }

    private fun activityOf(context: Context): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
