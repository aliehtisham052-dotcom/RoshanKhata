package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.DialogInterface
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.AppFailures
import com.innovation313.roshankhata.data.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.WeakHashMap

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
        val leave: () -> Unit = leave@{
            // A form whose Save is being written waits for the write (17 Nov 2026, L003): it closes by itself once
            // the row is in the book, or stays with what was typed when the write failed.
            if (writing.contains(dialog)) return@leave
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

    /**
     * A write a form's Save hands on: [write] runs on [AppScope] (a screen left at once cannot cancel it); [onWritten]
     * runs on the main thread once it is in the book, before the form closes (let go of a staged picture, say "Saved").
     */
    class Pending(val write: suspend () -> Unit, val onWritten: () -> Unit = {})

    /** Dialogs whose Save is being written now (main thread only; weak, so a closed form is forgotten). */
    private val writing: MutableSet<AlertDialog> = Collections.newSetFromMap(WeakHashMap())

    /** True while [dialog]'s Save is being written (tests, and a form's own buttons that must wait too). */
    fun isWriting(dialog: AlertDialog): Boolean = writing.contains(dialog)

    /**
     * Save that closes the form only once the write is in the book (17 Nov 2026, audit L003). [onSave] closed the
     * form the moment Save was let through and wrote behind it; a write that then failed (storage full, a refusal in
     * the DAO) said "not saved" — but everything typed was gone with the form. Now:
     *
     * - [check] runs on each tap, as [onSave]'s did: a refusal says why and returns without calling `commit`, and the
     *   form stays as it was. Let through, it calls `commit` with the write — at once, or later from a question it
     *   asks first (a manager's PIN, "proceed anyway?"); a `commit` after the form was closed does nothing.
     * - While the write runs, Save takes no second tap and Back, Cancel and [guard]'s question wait (a flag, not a
     *   disabled button: lesson 10). [draft] (and [busy]) are told, so a screen turned in that moment does not
     *   reopen a form whose row is about to land: the kept text is not saved while it is written.
     * - Written: [Pending.onWritten], then the form closes. Failed: [onFailed] says "not saved" (the app's record and
     *   toast) and the form stays open with everything that was typed, to try again or to discard.
     *
     * Call after show(): the Save button exists only then. What is NOT kept: a form whose screen was turned during
     * the write itself and whose write then failed (a few milliseconds' window): "not saved" is said, the typing is
     * gone, as before this.
     */
    fun onSaveWrite(
        dialog: AlertDialog,
        draft: FormDraft? = null,
        busy: (Boolean) -> Unit = {},
        onFailed: (Throwable) -> Unit = { AppFailures.report(it) },
        check: (commit: (Pending) -> Unit) -> Unit
    ) {
        val commit: (Pending) -> Unit = commit@{ pending ->
            if (writing.contains(dialog) || !dialog.isShowing) return@commit
            writing.add(dialog)
            draft?.writing = true
            busy(true)
            AppScope.launch {
                val failure = try {
                    pending.write(); null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    e
                }
                withContext(Dispatchers.Main) {
                    writing.remove(dialog)
                    draft?.writing = false
                    busy(false)
                    if (failure == null) {
                        pending.onWritten()
                        if (dialog.isShowing) dialog.dismiss()
                    } else {
                        onFailed(failure)
                    }
                }
            }
        }
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
            if (writing.contains(dialog)) return@setOnClickListener
            check(commit)
        }
    }
}
