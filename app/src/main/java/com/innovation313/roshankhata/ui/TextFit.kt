package com.innovation313.roshankhata.ui

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.Locale

/**
 * Lets a fixed-height button or label grow when the text in it has been made
 * larger, instead of cutting the words off.
 *
 * About seventy buttons and labels in the layouts have a fixed height in dp
 * (44dp, 52dp …). Their text is in sp, so it grows with the text size while
 * the box does not, and at 130% the bottom of the words is sliced away.
 *
 * Why this is done at runtime and not by editing the layouts: changing them
 * to wrap_content would also change them at NORMAL size — Urdu script runs
 * far taller than Latin for the same sp, so buttons that look right today in
 * Urdu would change height for every owner, whether they touched the setting
 * or not. Here nothing happens unless the text is actually larger than
 * normal (by this app's setting or the phone's own). Then each such view
 * keeps its designed height as a MINIMUM and may only grow beyond it.
 *
 * Only TextView and its subclasses (Button, MaterialButton, EditText):
 * a fixed-height container is left alone, because turning a container to
 * wrap_content changes how its match_parent children measure, which is a
 * layout change of its own.
 *
 * Android 15 and later, in Urdu, Sindhi, Arabic and Farsi (23 Oct review,
 * R04): the dp heights were drawn against Android's COMPACT Arabic-script
 * font. For an app targeting API 35+, `elegantTextHeight` is on by default
 * on an Android 15+ phone, which swaps in the taller font with larger
 * vertical bounds; for an app targeting 36 (this one) Android 16 ignores the
 * attribute altogether, so there is no theme switch to turn the old metrics
 * back (developer.android.com, "Android 15 features and changes list" and
 * "Behavior changes: apps targeting Android 16"). So on those phones, in an
 * Arabic-script language, the heights are relaxed at the normal size too.
 * Roman Urdu (`ur-Latn`) is Latin and stays as designed, as do the other
 * languages and every phone below Android 15 (still the compact font).
 *
 * It also lets a box's hint wrap instead of being cut ([HintFit]), at every size.
 *
 * Call it on anything freshly inflated: [com.innovation313.roshankhata.BaseActivity]
 * does the screen itself; dialogs and rows inflated in code call it directly.
 */
object TextFit {

    fun relax(root: View?) {
        if (root == null) return
        // Hints are read whole at every text size, so this runs before the size check below.
        HintFit.apply(root)
        val config = root.resources.configuration
        val locale = if (config.locales.isEmpty) null else config.locales[0]
        if (!needed(config.fontScale, Build.VERSION.SDK_INT, locale)) return
        walk(root)
    }

    /** Arabic-script languages of this app; Roman Urdu is excluded by its script. */
    private val ARABIC_SCRIPT = setOf("ur", "sd", "ar", "fa")

    /**
     * Whether fixed heights must be allowed to grow: text larger than normal,
     * or an Arabic-script language on Android 15+ (the elegant font's taller
     * lines). Public for the unit test.
     */
    fun needed(fontScale: Float, sdk: Int, locale: Locale?): Boolean {
        if (fontScale > 1.0f) return true
        if (sdk < 35 || locale == null) return false
        return locale.language in ARABIC_SCRIPT && locale.script != "Latn"
    }

    /** At or above this text scale a row's figure column goes under the name (see [stackWhenLarge]). */
    const val STACK_SCALE = 1.2f

    /**
     * A row of name + figure (a weighted name column beside a wrap_content figure column) on a
     * 360 dp phone at 120-150% text: the figure takes what it needs first and the name is left
     * a few letters wide, so it breaks a letter a line (31 Oct 2026, the screen proofs: "Test Wh /
     * olesale / Custom / er"). Above [STACK_SCALE] the figure column moves under the name, at the
     * reading start, and the name has the whole width. At normal size nothing changes. Call once,
     * on a freshly inflated row.
     */
    fun stackWhenLarge(nameColumn: ViewGroup, figureColumn: View) {
        if (nameColumn.resources.configuration.fontScale < STACK_SCALE) return
        val row = figureColumn.parent as? ViewGroup ?: return
        if (row === nameColumn) return
        row.removeView(figureColumn)
        val lp = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = (4 * nameColumn.resources.displayMetrics.density).toInt()
        nameColumn.addView(figureColumn, android.widget.LinearLayout.LayoutParams(lp))
        if (figureColumn is android.widget.LinearLayout) figureColumn.gravity = android.view.Gravity.START
        if (figureColumn is TextView) figureColumn.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    private fun walk(view: View) {
        if (view is TextView) {
            val lp = view.layoutParams
            // > 0: a real fixed height. 0 (match-constraint / weight) and the
            // negative MATCH_PARENT / WRAP_CONTENT are left as designed.
            // A view that shrinks its own text to its box (autoSize) keeps the box: Android documents autoSize
            // with wrap_content as unpredictable (30 Oct 2026, the phone audit: btnGave/btnGot, btnDriveConnect).
            val shrinks = androidx.core.widget.TextViewCompat.getAutoSizeTextType(view) !=
                androidx.core.widget.TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
            if (lp != null && lp.height > 0 && !shrinks) {
                view.minHeight = maxOf(view.minHeight, lp.height)
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                view.layoutParams = lp
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
    }
}
