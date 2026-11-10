package com.innovation313.roshankhata.ui

import android.app.Activity
import android.os.Bundle
import com.innovation313.roshankhata.R

/**
 * "Did the message go?" (8 Nov 2026, the owner's phone test): Android never tells an app whether WhatsApp (or the SMS
 * app) really sent what it was handed — a number that is not on WhatsApp opens "Invite", nothing is sent, and the
 * reminder was counted all the same, the ladder climbing. So opening the app counts nothing: the reminder waits as a
 * [Pending] until the owner is back on the screen, who is asked once. Only "Sent" counts it (the ladder's step, the
 * "Reminded …" line, the Radar's "reminder that worked", the pill's check); "Not sent" offers the same words by SMS;
 * closing the question records nothing.
 *
 * The screen keeps the [Pending] across a rotation ([save] / [restore]) and asks in onResume, so the question comes
 * when the owner is looking at the app again, never over WhatsApp.
 */
object ReminderConfirm {

    /** A reminder handed to another app and not yet confirmed. [step] is the ladder step sent, or 0 for "as the ladder says". */
    data class Pending(
        val partyId: Long,
        val partyName: String,
        val balance: Double,
        val phone: String?,
        val message: String,
        val step: Int,
        val viaSms: Boolean
    )

    private const val KEY = "reminder_pending"

    fun save(out: Bundle, pending: Pending?) {
        if (pending == null) { out.remove(KEY); return }
        out.putBundle(KEY, Bundle().apply {
            putLong("id", pending.partyId)
            putString("name", pending.partyName)
            putDouble("balance", pending.balance)
            putString("phone", pending.phone)
            putString("message", pending.message)
            putInt("step", pending.step)
            putBoolean("sms", pending.viaSms)
        })
    }

    fun restore(state: Bundle?): Pending? {
        val b = state?.getBundle(KEY) ?: return null
        return Pending(
            partyId = b.getLong("id"),
            partyName = b.getString("name").orEmpty(),
            balance = b.getDouble("balance"),
            phone = b.getString("phone"),
            message = b.getString("message").orEmpty(),
            step = b.getInt("step"),
            viaSms = b.getBoolean("sms")
        )
    }

    /**
     * Asks once. [onSent] counts the reminder; [onNotSent] runs for "Not sent" (a WhatsApp reminder offers SMS here: the
     * caller opens it with [Reminder.sendViaSms] and keeps a new SMS [Pending]); [onClosed] when the question is closed
     * without an answer (back, a tap outside) — nothing is recorded. Each of the three runs after the dialog has gone.
     */
    fun ask(
        activity: Activity,
        pending: Pending,
        onSent: () -> Unit,
        onNotSent: () -> Unit,
        onClosed: () -> Unit
    ): android.app.Dialog {
        // The app's sheet of cards (10 Nov 2026, the owner's photo: a plain dialog with "Not sent — send by SMS" as
        // a long text button, and no way to say "not sent" without opening SMS). Sent in green; after WhatsApp,
        // Send by SMS; and Not sent, which records nothing. Back or a swipe also records nothing.
        val sheet = ChoiceSheet
        val options = buildList {
            add(sheet.done(activity.getString(R.string.reminder_sent_yes)))
            if (!pending.viaSms) add(
                ChoiceSheet.Option(
                    activity.getString(R.string.reminder_sent_no_sms),
                    icon = R.drawable.ic_action_sms, iconTint = R.color.tile_insights_fg, tile = R.color.tile_insights_bg
                )
            )
            add(sheet.stop(activity.getString(R.string.reminder_sent_no)))
        }
        var answered = false
        return sheet.show(
            activity,
            title = activity.getString(R.string.reminder_sent_title),
            message = activity.getString(R.string.reminder_sent_body, Format.isolate(pending.partyName)),
            options = options,
            onCancel = { if (!answered) { answered = true; onClosed() } }
        ) { which ->
            answered = true
            when {
                which == 0 -> onSent()
                !pending.viaSms && which == 1 -> onNotSent()
                // "Not sent" after WhatsApp is not a request for SMS: nothing is recorded, as for a closed question.
                !pending.viaSms -> onClosed()
                else -> onNotSent()
            }
        }
    }
}
