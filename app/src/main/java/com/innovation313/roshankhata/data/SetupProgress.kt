package com.innovation313.roshankhata.data

import android.content.Context
import com.innovation313.roshankhata.LanguageActivity

/**
 * Where the first-run setup stands (5 Nov 2026, the owner's report).
 *
 * The setup is three steps: the language, the shop type, the shop's details (name, country,
 * phone). Until now the app wrote "language chosen" at step 1 and the front door looked at
 * nothing else, so a setup left at step 2 or 3 — the app closed, or Back on the country list —
 * opened straight on Home and the walkthrough next time: the shop's name and country were
 * never asked, and an unsaved country reads as Pakistan (+92, Rs) wherever the shop is.
 *
 * Now the setup is done only when its last step is ([markDone]: "Start my khata" or "Maybe
 * later" on the details page). Until then the front door opens the first step not yet
 * finished, with what was chosen before still chosen; Back walks to the step before.
 *
 * An install that chose its language before this rule ([markStarted] never written) finished
 * its setup under the old one: it counts as done and is never sent through the steps again.
 */
object SetupProgress {

    enum class Step { LANGUAGE, SHOP_TYPE, SHOP_DETAILS, DONE }

    private const val PREFS = "setup"
    private const val KEY_STARTED = "started"
    private const val KEY_DONE = "done"
    private const val KEY_COUNTRY_ASKED = "country_asked"

    /** The rule alone, for tests: what each saved fact says the next step is. */
    fun next(languageChosen: Boolean, started: Boolean, done: Boolean, hasTrade: Boolean): Step = when {
        done -> Step.DONE
        !languageChosen -> Step.LANGUAGE
        // Chosen before 5 Nov 2026: that setup ended under the old rule.
        !started -> Step.DONE
        !hasTrade -> Step.SHOP_TYPE
        else -> Step.SHOP_DETAILS
    }

    /** The first step not yet finished on this phone; an old install is marked done on the way. */
    fun next(context: Context): Step {
        val p = prefs(context)
        val done = p.getBoolean(KEY_DONE, false)
        val step = next(
            languageChosen = LanguageActivity.isChosen(context),
            started = p.getBoolean(KEY_STARTED, false),
            done = done,
            hasTrade = BusinessProfile.trade(context) != null
        )
        if (step == Step.DONE && !done) markDone(context)
        return step
    }

    fun isDone(context: Context): Boolean = next(context) == Step.DONE

    /** Written (synchronously) just before the first language is saved, so an update never mistakes this setup for an old one. */
    fun markStarted(context: Context) {
        prefs(context).edit().putBoolean(KEY_STARTED, true).commit()
    }

    fun markDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_DONE, true).commit()
    }

    /** Whether Home has asked an older book's country once already (MainActivity.askCountryOnce). */
    fun countryAsked(context: Context): Boolean = prefs(context).getBoolean(KEY_COUNTRY_ASKED, false)

    fun markCountryAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY_COUNTRY_ASKED, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
