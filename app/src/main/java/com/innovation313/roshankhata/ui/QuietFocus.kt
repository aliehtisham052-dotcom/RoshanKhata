package com.innovation313.roshankhata.ui

import android.view.View

/**
 * A dialog whose only focusable view is a text box gives that box the focus the moment it opens, and Android
 * then draws the cursor's round handle over the text with nobody having touched it (the Send Reminder
 * message, 8 Nov 2026). [park] hands the focus to the dialog's own body instead, so the box takes it —
 * and shows its cursor — only when the owner taps it.
 */
object QuietFocus {

    /** Call after the dialog is shown. [body] is the view the dialog was built from. */
    fun park(body: View) {
        body.isFocusableInTouchMode = true
        // A ViewGroup asks itself before its children (FOCUS_BEFORE_DESCENDANTS), so this takes the focus away from the box.
        body.post { body.requestFocus() }
    }
}
