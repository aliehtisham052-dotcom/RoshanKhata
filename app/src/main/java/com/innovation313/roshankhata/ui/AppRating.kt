package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.innovation313.roshankhata.data.PdfBranding

/**
 * Asking for a Play rating, the only way Play actually allows.
 *
 * The tempting version of this feature is a home-grown card: five stars, a few
 * ready-made compliments to tap, send. It is also the version that destroys
 * the thing it is trying to build. Play strips reviews whose text was supplied
 * by the app rather than the reviewer, and treats "ask happy users for a
 * review, route unhappy ones to email" as review gating. Fifty identical
 * "Very good" reviews become zero reviews, and the account wears the strike.
 *
 * So the rating flow is Google's own card, rendered by Play inside this app,
 * in the reviewer's own words. Feedback — where canned options are perfectly
 * fine and genuinely useful — goes the other way, through ReportProblem to
 * the support inbox. Two doors, because Play requires them to be two doors.
 *
 * WHAT THIS DOES NOT DO: filter anyone. Every owner who taps Rate gets the
 * same card, whatever they think of the app.
 */
object AppRating {

    /**
     * Open the in-app review card, or the store listing if it cannot appear.
     *
     * The card is Play's to grant, not ours to demand: it does not show on a
     * sideloaded build, it does not show when Play's own quota says the user
     * has been asked recently, and when it does not show there is no error and
     * nothing on screen. That silence is indistinguishable from a dead button,
     * so anything other than a shown card falls through to the listing, where
     * the owner can rate whenever they like.
     */
    fun launch(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnCompleteListener { request ->
                if (request.isSuccessful) {
                    manager.launchReviewFlow(activity, request.result)
                        .addOnCompleteListener {
                            // Play never says whether a review was written, by
                            // design — the owner is not reported on for what
                            // they chose to say or not say.
                        }
                } else {
                    openStoreListing(activity)
                }
            }
    }

    /** The Play listing: the Play app if it is installed, the browser if not. */
    fun openStoreListing(activity: Activity) {
        // The real listing, also from the side-by-side dev build (see PdfBranding).
        val packageName = PdfBranding.STORE_PACKAGE
        val playApp = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        )
        try {
            activity.startActivity(playApp)
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            } catch (_: ActivityNotFoundException) {
                // No Play app and no browser. Nothing sensible left to open,
                // and a crash here would be over a rating button.
            }
        }
    }

    // ---- The natural moment (8 Nov 2026 review, M96) ----------------------------------------------------------------
    // Asked at most once in 120 days, only after the owner has really used the app (5 entries on 3 different days, 3 days
    // since the first), and only on Home after finishing work, never over a form or a Saved card. There is NO question
    // first ("are you enjoying the app?"), no reward, no wording of ours: Play's own card or nothing. When Play's card
    // does not appear nothing happens at all (no store listing is opened behind the owner's back; the Help button does that).
    private const val PREFS = "app_rating"
    private const val MIN_ENTRIES = 5
    private const val MIN_DAYS = 3
    private const val MIN_AGE_DAYS = 3
    private const val GAP_DAYS = 120
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** What the rule decides on; pure so a test holds it. */
    data class Use(val entries: Int, val days: Int, val firstSeen: Long, val lastAsked: Long)

    fun due(u: Use, now: Long): Boolean =
        u.entries >= MIN_ENTRIES && u.days >= MIN_DAYS && u.firstSeen > 0 && now - u.firstSeen >= MIN_AGE_DAYS * DAY_MS &&
            (u.lastAsked == 0L || now - u.lastAsked >= GAP_DAYS * DAY_MS)

    private fun prefs(c: android.content.Context) = c.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    /** One entry saved on a customer's screen: counted, with the day. */
    fun noteEntrySaved(c: android.content.Context, now: Long = System.currentTimeMillis()) {
        runCatching {
            val p = prefs(c)
            val day = (now / DAY_MS).toString()
            val days = p.getStringSet("days", emptySet()).orEmpty().toMutableSet().apply { add(day) }
            val keep = if (days.size > 30) days.sortedBy { it.toLong() }.takeLast(30).toSet() else days
            p.edit().putInt("entries", p.getInt("entries", 0) + 1).putStringSet("days", keep)
                .apply { if (!p.contains("first")) putLong("first", now) }.apply()
        }
    }

    /** On Home, after work: Play's card when the rule says so. Nothing is shown to the owner but Play's own card. */
    fun maybeAsk(activity: Activity, now: Long = System.currentTimeMillis()) {
        runCatching {
            val p = prefs(activity)
            val use = Use(p.getInt("entries", 0), p.getStringSet("days", emptySet()).orEmpty().size, p.getLong("first", 0L), p.getLong("asked", 0L))
            if (!due(use, now)) return
            p.edit().putLong("asked", now).apply()
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (request.isSuccessful && !activity.isFinishing && !activity.isDestroyed) manager.launchReviewFlow(activity, request.result)
            }
        }
    }
}
