package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.DriveAuth
import kotlinx.coroutines.launch

/**
 * The one-time welcome, shown once on the first run after the language is
 * picked and never again. Its whole job is trust: a new shopkeeper meets the
 * app and is offered — not forced — to connect their own Google account, so
 * their books can be kept safe in their own Drive.
 *
 * Two things are deliberate here, both from the owner's own instructions:
 *
 *  - It is an INVITATION, not a gate. A shopkeeper who has no Google account,
 *    or does not want to connect one right now, taps "Maybe later" and uses
 *    the app fully. Forcing a login would shut such a user out — the app has
 *    always run with nobody signed in, and still does.
 *
 *  - It does not pretend that signing in is the same as being backed up.
 *    Connecting an account only makes backup POSSIBLE; the data is safe once
 *    a backup actually runs. The screen says this plainly, so no one is left
 *    with a false sense of safety. (Automatic backup, if the owner turns it
 *    on, is what then keeps them current.)
 *
 * App Lock (a PIN) is a separate matter and is not touched here — it stays the
 * owner's own choice, offered in settings, never mixed into this sign-in.
 */
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)

        findViewById<MaterialButton>(R.id.btnWelcomeConnect).setOnClickListener {
            connectDrive()
        }
        findViewById<MaterialButton>(R.id.btnWelcomeSkip).setOnClickListener {
            markSeenAndProceed()
        }
    }

    /**
     * The same two-step connect the backup screen uses: Sign in with Google to
     * learn the account (and show its email later), then authorize the Drive
     * appdata folder. Whatever the outcome, the welcome has been seen — the
     * owner is never brought back to it — so both success and decline proceed
     * to the ledger; only the connection state differs.
     */
    private fun connectDrive() {
        lifecycleScope.launch {
            val email = try {
                DriveAuth.signIn(this@WelcomeActivity)
            } catch (e: Exception) {
                // Never swallow the real reason a sign-in fails -- kept in
                // Logcat so a future failure is diagnosable, not a mystery
                // toast again.
                android.util.Log.e("DriveSignIn", "signIn failed (Welcome)", e)
                null
            }

            if (email == null) {
                // Dismissed the account sheet. Not connected — but the welcome
                // is done; let them into the app, they can connect later from
                // the backup screen.
                Toast.makeText(this@WelcomeActivity, R.string.drive_signin_failed, Toast.LENGTH_LONG).show()
                markSeenAndProceed()
                return@launch
            }

            DriveAuth.rememberAccount(this@WelcomeActivity, email)
            authorizeDrive()
        }
    }

    private fun authorizeDrive() {
        DriveAuth.authorize(this)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        driveAuthorize.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } else {
                        markSeenAndProceed()
                    }
                } else {
                    // Already granted — connected. Say so before moving on;
                    // the welcome screen hands off to the ledger too fast
                    // for the owner to otherwise notice the connection took.
                    showConnectedAndProceed()
                }
            }
            .addOnFailureListener {
                // Sign-in worked, Drive grant did not. The account is
                // remembered; they can finish connecting from the backup
                // screen. Don't trap them on the welcome.
                markSeenAndProceed()
            }
    }

    /**
     * Confirm the sign-in ON THIS SCREEN and hold a moment before moving on.
     *
     * A toast was tried first and was the wrong tool: this activity finishes
     * immediately after, and the ledger's first-run walkthrough opens a dimmed
     * card over the whole screen — the toast landed underneath it and the owner
     * never saw that the account had connected. The banner is part of this
     * layout, so nothing can cover it, and the short pause is what makes it
     * readable rather than a flash.
     */
    private fun showConnectedAndProceed() {
        val email = DriveAuth.accountName(this)
        if (email == null) {
            markSeenAndProceed()
            return
        }

        findViewById<android.widget.TextView>(R.id.tvWelcomeSignedInEmail).text = email
        val banner = findViewById<android.view.View>(R.id.welcomeSignedIn)
        banner.alpha = 0f
        banner.visibility = android.view.View.VISIBLE
        banner.animate().alpha(1f).setDuration(200).start()

        // Nothing left to decide once the account is connected, and a second
        // tap during the pause would start the ledger twice.
        findViewById<MaterialButton>(R.id.btnWelcomeConnect).isEnabled = false
        findViewById<MaterialButton>(R.id.btnWelcomeSkip).isEnabled = false

        // Long enough to read an email address, short enough not to feel stuck.
        // The guard matters: the owner can leave during the pause, and starting
        // an activity from a finished one crashes.
        banner.postDelayed({
            if (!isFinishing && !isDestroyed) markSeenAndProceed()
        }, 1800L)
    }

    private val driveAuthorize = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val connected = try {
            DriveAuth.resultFromIntent(this, activityResult.data)
            true
        } catch (_: Exception) {
            // Declined the Drive consent. Fine — account is remembered, they
            // can finish later. Proceed regardless.
            false
        }
        // showConnectedAndProceed moves on by itself once the banner has been
        // up long enough to read; a decline has nothing to confirm.
        if (connected) showConnectedAndProceed() else markSeenAndProceed()
    }

    /** Record that the welcome has been shown, then go to the ledger. */
    private fun markSeenAndProceed() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putBoolean(KEY_SEEN, true).apply()
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_UNLOCKED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    companion object {
        private const val PREFS = "welcome"
        private const val KEY_SEEN = "welcome_seen"

        /** True once the welcome has been shown — it is a first-run-only screen. */
        fun isSeen(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SEEN, false)
    }
}
