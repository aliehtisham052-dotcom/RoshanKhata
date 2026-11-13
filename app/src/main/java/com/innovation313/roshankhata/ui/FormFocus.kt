package com.innovation313.roshankhata.ui

import android.app.Dialog
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Where the cursor lands when a form opens (8 Nov 2026, the owner's rule, point 22):
 *
 *  a. a form opened TO fill something in (Add customer, Add product, a missing box) puts the cursor in the
 *     first empty box with the keyboard up;
 *  b. an Edit of something that exists puts the cursor in the first EMPTY box too, and when nothing is empty
 *     the keyboard stays down (the owner reads first);
 *  c. money boxes keep the app's calculator pad, text boxes the phone's keyboard (the boxes themselves
 *     decide that; this only says which box has the cursor).
 *
 * Call after `show()`. [boxes] are in reading order; a box that is hidden or disabled is skipped, so a
 * form whose later boxes appear only on a choice is not asked for them.
 */
object FormFocus {

    /**
     * Puts the cursor in the first empty box of [boxes] and, when [showKeyboard], raises the keyboard.
     * When none is empty the cursor goes nowhere and the keyboard stays down. A money box (one the app's pad
     * takes: `showSoftInputOnFocus` is off) gets the cursor and its pad, never the phone's keyboard.
     * Returns the box that took the cursor, or null.
     */
    fun focusFirstEmpty(
        boxes: List<EditText>,
        showKeyboard: Boolean = true,
        window: Window? = null
    ): EditText? {
        val target = boxes.firstOrNull { it.visibility == View.VISIBLE && it.isEnabled && it.text.isNullOrBlank() }
        if (target == null) {
            // Nothing to fill: read first. The window is told to keep the keyboard down.
            window?.setSoftInputMode(
                (window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            )
            return null
        }
        target.requestFocus()
        if (showKeyboard) {
            // After the window is laid out: a keyboard asked for in the same frame as show() is dropped.
            target.post {
                if (target.isAttachedToWindow && target.showSoftInputOnFocus) {
                    if (window != null) WindowInsetsControllerCompat(window, target).show(WindowInsetsCompat.Type.ime())
                    else androidx.core.view.ViewCompat.getWindowInsetsController(target)?.show(WindowInsetsCompat.Type.ime())
                }
            }
        }
        return target
    }

    /**
     * An AlertDialog whose view is a ComposeView (nothing in it yet when the dialog is built) is created with
     * FLAG_ALT_FOCUSABLE_IM: AlertController sets it unless it finds a text box in the view at that moment,
     * and a ComposeView has none until it has composed. With the flag the window never takes the keyboard:
     * no box in the dialog opened it (the stock count, 8 Nov 2026). Cleared here, after show(); the window
     * resizes for the keyboard and leaves its state alone.
     */
    fun allowKeyboard(dialog: Dialog) {
        val window = dialog.window ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        val state = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE
        window.setSoftInputMode(state or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    /**
     * The keyboard's Next walks [boxes] in the order given, the reading order (10 Nov 2026, the owner's photo of
     * Add product): left to their own search, Android went from the cash price straight down past the credit
     * price beside it. A box hidden or disabled at that moment is passed over, so a form whose boxes come and
     * go with a choice keeps the right order; after the last shown box Next does what it always did.
     */
    fun chain(boxes: List<EditText>) {
        boxes.forEachIndexed { i, box ->
            box.imeOptions = (box.imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION.inv()) or
                android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
            val nextOf: () -> EditText? = { boxes.drop(i + 1).firstOrNull { it.isShown && it.isEnabled && it.isFocusable } }
            box.setOnEditorActionListener { _, action, _ ->
                if (action != android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) return@setOnEditorActionListener false
                nextOf()?.let { go(it) } != null
            }
            // A money box has the app's pad, not the keyboard, so no Next key: the pad's "=" on a plain figure
            // asks for this (11 Nov 2026, the owner's photo of Add bill: the cursor could not move on).
            box.setTag(com.innovation313.roshankhata.R.id.tag_form_next, nextOf)
        }
    }

    private fun go(box: EditText) {
        box.requestFocus()
        box.setSelection(box.text?.length ?: 0)
    }

    /**
     * The box after [box] in its form: the one [chain] named, else the next text box Android's own Next would
     * reach that is shown, enabled and not above [box] on the screen (Android's search wraps to the form's first
     * box after its last; going back up is never "next"). Null when there is none.
     */
    @Suppress("UNCHECKED_CAST")
    fun nextBox(box: EditText): EditText? {
        (box.getTag(com.innovation313.roshankhata.R.id.tag_form_next) as? () -> EditText?)?.let { return it() }
        if (!box.isAttachedToWindow) return null
        val found = box.focusSearch(View.FOCUS_FORWARD) as? EditText ?: return null
        if (found === box || !found.isShown || !found.isEnabled) return null
        val here = IntArray(2).also { box.getLocationInWindow(it) }
        val there = IntArray(2).also { found.getLocationInWindow(it) }
        return found.takeIf { there[1] >= here[1] }
    }

    /** Moves from [box] to [nextBox]; false when there is none. */
    fun next(box: EditText): Boolean = nextBox(box)?.let { go(it); true } ?: false

    /**
     * No autofill bubble over this view or anything inside it (a Compose host's boxes included). Autofill is
     * Android 8+: on 7.x the call does not exist (it threw there, 13 Nov 2026 lint), and there is no bubble to hide.
     */
    fun noAutofill(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
    }
}
