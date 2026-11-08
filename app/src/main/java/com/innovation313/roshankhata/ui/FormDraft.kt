package com.innovation313.roshankhata.ui

import android.app.Activity
import android.os.Bundle
import android.widget.EditText

/**
 * What is typed in a money form while it is open, kept across a rotation, dark mode or a process killed behind the
 * camera (8 Nov 2026 review, M90 — the Cashbook form's pattern, shared). A screen owns one [FormDraft] per form kind:
 * [open] when the form is shown, [closed] from its dismiss listener, [save] from onSaveInstanceState, and [saved] in
 * onCreate to reopen the form with [fill]ed boxes. Which form (and its row, if any) is a number the screen chooses.
 * Chips, days and pictures are not kept: only the text boxes, as the add-entry form's first step.
 */
class FormDraft(private val tag: String) {
    /** A form that was open when the screen was saved. */
    class Saved(val which: Int, val arg: Long, val texts: List<String>?)

    private var which = -1
    private var arg: () -> Long = { 0L }
    private var reader: (() -> List<String>)? = null

    /** [arg] is read when the screen is saved (a choice made in the form, or its row); [boxes] are its text boxes. */
    fun open(which: Int, arg: () -> Long = { 0L }, boxes: () -> List<EditText>) {
        this.which = which
        this.arg = arg
        reader = { boxes().map { it.text.toString() } }
    }

    /** From the form's dismiss listener: a rotation is not a close, so the draft survives it. */
    fun closed(activity: Activity) {
        if (!activity.isChangingConfigurations) { which = -1; reader = null }
    }

    fun save(out: Bundle) {
        if (which < 0) return
        out.putInt("${tag}_which", which)
        out.putLong("${tag}_arg", arg())
        reader?.let { out.putStringArrayList("${tag}_texts", ArrayList(it())) }
    }

    fun saved(state: Bundle?): Saved? {
        if (state == null || !state.containsKey("${tag}_which")) return null
        return Saved(state.getInt("${tag}_which"), state.getLong("${tag}_arg"), state.getStringArrayList("${tag}_texts"))
    }

    companion object {
        /** The kept text put back into the form's boxes, in the order they were given to [open]. */
        fun fill(boxes: List<EditText>, texts: List<String>?) {
            texts?.let { t -> boxes.forEachIndexed { i, box -> t.getOrNull(i)?.let { box.setText(it) } } }
        }
    }
}
