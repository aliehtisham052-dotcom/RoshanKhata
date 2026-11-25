package com.innovation313.roshankhata.ui

import android.app.Activity
import android.app.Dialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R

/**
 * The one question about where speech is understood (25 Nov 2026, D10), asked in plain words, two choices:
 * "Use Google's online recogniser: your voice goes to Google" or the other — "Type instead" when asked at the
 * microphone, "Keep on this phone only" when asked by the voice settings' switch being turned off. Used by the assistant's
 * ears, Khata's voice entry and the voice settings, so the words and the answers are the same everywhere.
 *
 * Nothing is remembered here: [onYes] and [onNo] are told, and each caller keeps the answer ([ListenChoiceStore]). A
 * question closed any other way (Back, a tap outside, the screen going) is [onCancel]: nothing is chosen and it is asked
 * again next time. A caller that dismisses the returned dialog itself is told [onCancel] too and ignores it by its own token.
 */
object ListenAsk {

    /** [typeWords]: the second button's words. */
    fun show(
        activity: Activity,
        typeWords: Int = R.string.hudhud_listen_ask_type,
        onYes: () -> Unit,
        onNo: () -> Unit,
        onCancel: () -> Unit
    ): Dialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        var answered = false
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.hudhud_listen_ask_title)
            .setMessage(R.string.hudhud_listen_ask_body)
            .setPositiveButton(R.string.hudhud_listen_ask_online) { _, _ ->
                answered = true
                onYes()
            }
            .setNegativeButton(typeWords) { _, _ ->
                answered = true
                onNo()
            }
            .create()
        DialogLife.bind(activity, dialog, onDismiss = { if (!answered) onCancel() })
        return try {
            dialog.show()
            dialog
        } catch (e: Exception) {
            null
        }
    }
}
