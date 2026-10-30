package com.innovation313.roshankhata.ui

import android.view.View
import android.view.ViewGroup
import android.widget.EditText

/**
 * A box's hint is read whole, never cut at the box's edge (30 Oct 2026, the owner's photo of a
 * product form: "Barcode (optional", "Cost price (op", "How many in a").
 *
 * A one-line box draws its hint on one line and cuts what does not fit, and which hints do not
 * fit depends on the shop's trade (the examples in "Unit — cylinder, litre, kg (optional)"), the
 * language (Bengali and Indonesian run longest), the text size and the phone. No layout can be
 * checked against all of them, so the box measures its own hint once it knows its width: one
 * that does not fit wraps its lines instead (horizontal scrolling off, up to [MAX_LINES] lines).
 * The keyboard is unchanged: the input type keeps no multi-line flag, so Enter still moves on
 * (Next or Done) rather than writing a new line. A box whose hint fits is left exactly as designed.
 *
 * The forms whose hints are long by design ask them as labels above the box instead (the
 * product form, a bill's line, an entry's line); this is the floor under every other box.
 * [TextFit.relax] runs it, so every screen and every dialog that calls TextFit has it.
 */
object HintFit {

    const val MAX_LINES = 4

    /** Watch every one-line box under [root]; each is checked whenever it is laid out. */
    fun apply(root: View?) {
        if (root == null) return
        if (root is EditText) watch(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) apply(root.getChildAt(i))
    }

    private fun watch(box: EditText) {
        if (box.getTag(TAG) != null) return
        // A box that is already several lines tall wraps its hint by itself.
        if (box.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return
        box.setTag(TAG, true)
        box.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> fit(v as EditText) }
    }

    /** Whether [hintWidth] (px) overruns the room a box of [width] px leaves inside its padding. Public for the test. */
    fun overruns(hintWidth: Float, width: Int, paddingStart: Int, paddingEnd: Int): Boolean =
        width > 0 && hintWidth > (width - paddingStart - paddingEnd).toFloat()

    private fun fit(box: EditText) {
        val hint = box.hint?.toString().orEmpty()
        if (hint.isEmpty() || box.maxLines > 1) return
        val room = box.width
        if (!overruns(box.paint.measureText(hint), room, box.totalPaddingLeft, box.totalPaddingRight)) return
        // Changed after this layout pass: the box measures again with its new lines.
        box.post {
            box.setHorizontallyScrolling(false)
            box.maxLines = MAX_LINES
        }
    }

    private val TAG = com.innovation313.roshankhata.R.id.hint_fit_watched
}
