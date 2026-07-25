package com.innovation313.roshankhata.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * Give a dialog's own view the height its window already has.
 *
 * A dialog window can be told to fill the screen, and that alone does nothing
 * for what is inside it. AlertDialog stacks its title, its content and its
 * buttons in panels that each wrap their contents, so a view handed to
 * setView() is measured against what it asks for and never against what the
 * window has. The window ends up full-screen, the form sits at the top of it,
 * and the rest is white.
 *
 * That is worth spelling out because it is invisible from the layout file. The
 * entry form's calculator was given a weight so it would take the space left
 * over, and there was never any space left over to take — the layout was
 * correct and the panels above it were handing down nothing.
 *
 * So the chain is walked from the view up to the window's content, and each
 * link is told to fill. Inside a vertical stack that means taking a share of
 * what is left rather than a fixed height, which is what leaves the title and
 * the buttons their own room and gives the form everything between.
 *
 * Best effort. If a future version of the dialog is built differently this
 * quietly changes nothing, which is today's behaviour and not a crash.
 */
fun View.fillDialogHeight() {
    try {
        var child: View = this
        var parent = this.parent
        while (parent is ViewGroup) {
            val lp = child.layoutParams
            if (lp is LinearLayout.LayoutParams) {
                // A share of what the stack has left, so the title and the
                // buttons keep theirs.
                lp.height = 0
                lp.weight = 1f
                child.layoutParams = lp
            } else if (lp != null) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                child.layoutParams = lp
            }
            if (parent.id == android.R.id.content) return
            child = parent
            parent = parent.parent
        }
    } catch (e: Exception) {
        // Leave the dialog exactly as it was.
    }
}
