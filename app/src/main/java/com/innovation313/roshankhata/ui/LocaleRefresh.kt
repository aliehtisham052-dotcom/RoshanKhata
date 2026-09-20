package com.innovation313.roshankhata.ui

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate

/**
 * Keep a screen's words in step with the language that was chosen after it
 * was built.
 *
 * AppCompat's per-app locales rebuild the screen that is on top when the
 * choice is made — and the language picker finishes itself at that same
 * moment, so the rebuild lands on a screen already on its way out. Whatever
 * was underneath it in the back stack keeps the wording it was created with:
 * Home would still read "Cashbook" after the owner asked for Urdu, and would
 * only correct itself much later, whenever something else happened to
 * recreate it. That delay is what this removes.
 *
 * Each screen notes the language it was built in, and checks again as it
 * comes back to the front. If the answer has changed, it rebuilds itself —
 * so the owner stays exactly where they were, in the language they just
 * picked, instead of being thrown back to Home.
 *
 * Usage, in any screen that can be underneath the language picker:
 *
 *     private var builtIn: String? = null            // field
 *     builtIn = LocaleRefresh.tag(this)              // end of onCreate
 *     builtIn = LocaleRefresh.refresh(this, builtIn) // start of onStart
 */
object LocaleRefresh {

    /**
     * The language this screen is showing, as a BCP-47 tag — the app's own
     * choice where there is one ("ur", "ur-Latn", "en", …), otherwise
     * whatever the phone resolved the resources to.
     */
    fun tag(activity: Activity): String {
        val chosen = AppCompatDelegate.getApplicationLocales()
        if (!chosen.isEmpty) chosen[0]?.let { return it.toLanguageTag() }
        return activity.resources.configuration.locales[0].toLanguageTag()
    }

    /**
     * Rebuild the screen if the language moved on while it was in the back
     * stack. Returns the tag to remember — unchanged when nothing happened,
     * and the new one when a rebuild has been asked for, so a second call
     * before the rebuild arrives cannot ask for it twice.
     */
    fun refresh(activity: Activity, builtIn: String?): String {
        val now = tag(activity)
        // Nothing noted yet (a screen restored by the system before onCreate
        // ran its note) is not evidence of a change; treat it as current.
        if (builtIn != null && builtIn != now && !activity.isFinishing) {
            activity.recreate()
        }
        return now
    }
}
