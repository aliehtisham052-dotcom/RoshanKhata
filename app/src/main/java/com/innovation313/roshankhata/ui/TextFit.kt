package com.innovation313.roshankhata.ui

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

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
 * Call it on anything freshly inflated: [com.innovation313.roshankhata.BaseActivity]
 * does the screen itself; dialogs and rows inflated in code call it directly.
 */
object TextFit {

    fun relax(root: View?) {
        if (root == null) return
        if (root.resources.configuration.fontScale <= 1.0f) return
        walk(root)
    }

    private fun walk(view: View) {
        if (view is TextView) {
            val lp = view.layoutParams
            // > 0: a real fixed height. 0 (match-constraint / weight) and the
            // negative MATCH_PARENT / WRAP_CONTENT are left as designed.
            if (lp != null && lp.height > 0) {
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
