package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.DialogInterface
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R

/**
 * A form that asks before it throws away what was typed (4 Nov 2026, the owner's phone: Back, a tap beside the
 * form or Cancel threw a typed supplier bill away in silence; 5 Nov 2026, the same guard on every form that had
 * the same flaw — cashbook, close the day, plans, cheques, staff, schemes, the entry editors).
 *
 * Back and the form's Cancel close an untouched form at once, as before; with anything typed ([typed]: every
 * field and choice held against the values the form opened with) they ask "Discard?" — Keep editing / Discard —
 * and only Discard closes it, after [onDiscard] has let go of what the form was holding (a staged photo,
 * waiting lines). A tap beside the form no longer closes it at all.
 *
 * Back is a callback on the dialog's own dispatcher, not onBackPressed or KEYCODE_BACK, so it also works on
 * Android 16+. Callbacks run newest first: a form whose own step answers Back first (the add-entry form's
 * details) adds its callback after calling this, and this one answers once that one is off.
 *
 * Call after show(): the dialog's Cancel button exists only then. The returned function is the same way out,
 * for a form that is left by a button of its own rather than the dialog's Cancel (the add-entry form's arrow).
 */
object FormGuard {

    fun guard(
        activity: Activity,
        dialog: AlertDialog,
        typed: () -> Boolean,
        onDiscard: () -> Unit = {},
        title: Int = R.string.form_discard_title,
        message: Int = R.string.form_discard_msg
    ): () -> Unit {
        dialog.setCanceledOnTouchOutside(false)
        val leave: () -> Unit = {
            if (!typed()) {
                dialog.dismiss()
            } else {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setNegativeButton(R.string.bp_leave_keep, null)
                    .setPositiveButton(R.string.bp_leave_discard) { _, _ ->
                        onDiscard()
                        dialog.dismiss()
                    }
                    .show()
            }
        }
        dialog.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setOnClickListener { leave() }
        return leave
    }

    /**
     * Save wired after show(), as Close the day's is (R155; every money form since 5 Nov 2026): a builder's Save closes
     * the dialog whatever its listener decides, so a save the form's own checks refused threw away everything typed
     * with it. [save] runs on each tap. Refused, it says why as the form always has and returns false: the dialog stays
     * open as it was. Let through, it starts the write where the form always started it and returns true: the dialog
     * closes, as Close the day's does. A save let through takes no second tap (a flag, not a disabled button: lesson 10).
     * Call after show(): the Save button exists only then.
     */
    fun onSave(dialog: AlertDialog, save: () -> Boolean) {
        var handedOn = false
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
            if (handedOn) return@setOnClickListener
            if (save()) {
                handedOn = true
                dialog.dismiss()
            }
        }
    }
}
