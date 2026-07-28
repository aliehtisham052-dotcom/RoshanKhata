package com.innovation313.roshankhata.ui

import android.app.Activity
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.ScreenSecurity

/**
 * The one switch for [ScreenSecurity], written once.
 *
 * App Lock's dialog is duplicated in Home and Khata, and the two have to be
 * kept in step by hand. This one is not: both screens call here, so the wording
 * a shopkeeper reads about their own privacy cannot say two different things
 * depending on which door they came through.
 *
 * The dialog says which of the two layers is already doing its work, because
 * that answer depends on the phone, not on the app: the switcher thumbnail is
 * hidden for free from Android 13 onward, and on anything older this switch is
 * the only thing that hides it. An owner deciding whether to give up their own
 * screenshots deserves to know which of those two they are actually buying.
 *
 * The change applies immediately — [ScreenSecurity.applyTo] runs again on the
 * next resume, which is the moment this dialog closes.
 */
object ScreenPrivacyDialog {

    fun show(activity: Activity) {
        val blocked = ScreenSecurity.isBlocked(activity)

        val status = activity.getString(
            if (blocked) R.string.screen_privacy_on else R.string.screen_privacy_off
        )
        val recents = activity.getString(
            if (ScreenSecurity.recentsCanBeHidden) R.string.screen_privacy_recents_hidden
            else R.string.screen_privacy_recents_visible
        )
        val message = status + "\n\n" + recents + "\n\n" +
            activity.getString(R.string.screen_privacy_explain)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.screen_privacy)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(
                if (blocked) R.string.screen_privacy_turn_off else R.string.screen_privacy_turn_on
            ) { _, _ ->
                ScreenSecurity.setBlocked(activity, !blocked)
                ScreenSecurity.applyTo(activity)
                Toast.makeText(
                    activity,
                    if (!blocked) R.string.screen_privacy_on else R.string.screen_privacy_off,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .show()
    }
}
