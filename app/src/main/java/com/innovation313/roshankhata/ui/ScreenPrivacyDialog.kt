package com.innovation313.roshankhata.ui

import android.app.Activity
import android.widget.Toast
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

        val recents = activity.getString(
            if (ScreenSecurity.recentsCanBeHidden) R.string.screen_privacy_recents_hidden
            else R.string.screen_privacy_recents_visible
        )
        // A sheet like Automations' and Theme's (8 Nov 2026): the state as a pill, what it does for this phone
        // in one line, the full explanation behind Learn more, one button naming the result.
        SettingSheet.show(
            activity,
            icon = R.drawable.ic_menu_privacy,
            tile = R.color.tile_cheques_bg,
            title = activity.getString(R.string.screen_privacy),
            on = blocked,
            summary = recents,
            more = activity.getString(R.string.screen_privacy_explain),
            actionLabel = activity.getString(if (blocked) R.string.screen_privacy_turn_off else R.string.screen_privacy_turn_on),
            danger = blocked
        ) {
            ScreenSecurity.setBlocked(activity, !blocked)
            ScreenSecurity.applyTo(activity)
            Toast.makeText(
                activity,
                if (!blocked) R.string.screen_privacy_on else R.string.screen_privacy_off,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
