package com.innovation313.roshankhata.ui

import android.view.Gravity
import android.widget.FrameLayout

/**
 * A child of a FrameLayout placed by a picture's own left and top.
 *
 * A FrameLayout hangs a child with no gravity from its START, and in a window that reads right to left (Urdu, Sindhi,
 * Arabic, Farsi) the start is the RIGHT edge: the left margin is then not read at all, and the child stands
 * `left` too far to the right. The posters' live 3D hero did, on the owner's Urdu phone (19 Nov 2026: the clock, the
 * mosque and the crate each stood beside their place), and the seal's stamp on a statement the same way. A picture's
 * coordinates do not turn with the language, so the place is hung from LEFT | TOP by name.
 *
 * Every left margin in the app is set here (scripts/check_repo_guards.py, BC-237); PlaceAtTest lays one out both ways.
 */
object PlaceAt {
    fun params(left: Int, top: Int, width: Int, height: Int): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(width, height, Gravity.LEFT or Gravity.TOP).apply {
            leftMargin = left
            topMargin = top
        }
}
