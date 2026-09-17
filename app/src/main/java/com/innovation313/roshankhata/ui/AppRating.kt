package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory

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
        val packageName = activity.packageName
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
}
