package com.innovation313.roshankhata

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
class WelcomeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        // Step 2 of 3, drawn like step 1 (9 Oct): the splash painting under
        // the status bar, the panel under the navigation bar, dark icons on
        // both because both are light.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        com.innovation313.roshankhata.ui.SplashArt.anchor(findViewById(R.id.ivWelcomeArt), TAGLINE_GAP_DP)
        com.innovation313.roshankhata.ui.StepDots.show(
            findViewById(R.id.welcomeStepDots), findViewById(R.id.tvWelcomeStep), 2)
        val panel = findViewById<android.view.View>(R.id.welcomePanel)
        val basePad = panel.paddingBottom
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(panel) { v, insets ->
            val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPaddingRelative(v.paddingStart, v.paddingTop, v.paddingEnd, basePad + nav)
            insets
        }

        findViewById<MaterialButton>(R.id.btnWelcomeConnect).apply {
            // Google's branding rules forbid a monochrome G. MaterialButton
            // tints its icon with the text colour by default, which would turn
            // the four-colour G dark grey; clearing the tint keeps it standard.
            iconTint = null
            setOnClickListener { connectDrive() }
        }
        findViewById<MaterialButton>(R.id.btnWelcomeSkip).setOnClickListener {
            markSeenAndProceed()
        }
    }

    /**
     * The same two-step connect the backup screen uses: Sign in with Google to
     * learn the account (and show its email later), then authorize the Drive
     * appdata folder. A connection, or the owner declining the Drive
     * permission, moves on; a sign-in that did not happen keeps him on this
     * step to try again or choose Maybe later.
     */
    private fun connectDrive() {
        lifecycleScope.launch {
            var reason: String? = null
            var ownerClosed = false
            val email = try {
                DriveAuth.signIn(this@WelcomeActivity)
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                android.util.Log.e("DriveSignIn", "signIn cancelled (Welcome)", e)
                ownerClosed = com.innovation313.roshankhata.ui.SignInProblem.isOwnerDismissal(e.errorMessage?.toString())
                reason = "${e.type.substringAfterLast('.')}: ${e.errorMessage}"
                null
            } catch (e: Exception) {
                android.util.Log.e("DriveSignIn", "signIn failed (Welcome)", e)
                reason = (e as? androidx.credentials.exceptions.GetCredentialException)
                    ?.let { "${it.type.substringAfterLast('.')}: ${it.errorMessage}" }
                    ?: "${e.javaClass.simpleName}: ${e.message}"
                null
            }

            // Not connected: stay on this step (9 Oct). It used to toast and
            // move on, so an owner who closed the account sheet by mistake
            // lost the screen for good. Now he can try again or tap Maybe
            // later; a real fault shows its reason with the package and
            // SHA-1, the same dialog the backup screen uses.
            if (email == null) {
                if (ownerClosed || reason == null) {
                    Toast.makeText(this@WelcomeActivity, R.string.drive_signin_failed, Toast.LENGTH_LONG).show()
                } else {
                    com.innovation313.roshankhata.ui.SignInProblem.show(this@WelcomeActivity, reason)
                }
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

        // HIDDEN, not merely disabled.
        //
        // Disabling was enough to stop a second tap starting the ledger
        // twice, and that part worked. What it did not do was LOOK disabled:
        // the connect button paints itself from app:backgroundTint, a flat
        // colour rather than a state list, so a dead button went on showing
        // as solid brand green. For the moment before this screen hands off,
        // the page therefore read "Successfully signed in" with a full-colour
        // "Connect Google account" sitting directly beneath it — two
        // statements that contradict each other, on the one screen whose
        // entire job is trust. The owner photographed exactly that.
        //
        // Gone removes the contradiction instead of explaining it away, and
        // keeps the guard: a hidden button cannot be tapped either.
        findViewById<MaterialButton>(R.id.btnWelcomeConnect).visibility =
            android.view.View.GONE
        findViewById<MaterialButton>(R.id.btnWelcomeSkip).visibility =
            android.view.View.GONE

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

    /**
     * Record that the welcome has been shown, then go to the ledger - after
     * asking the shop's trade, on a book that has never had one (9 Oct).
     * The welcome is a first-run-only screen, so an existing owner is never
     * asked; a backup restored later brings its own trade with it.
     */
    private fun markSeenAndProceed() {
        if (com.innovation313.roshankhata.data.BusinessProfile.trade(this) == null) {
            com.innovation313.roshankhata.ui.TradePicker.show(this, null, required = true, step = 3) {
                com.innovation313.roshankhata.data.BusinessProfile.setTrade(this, it)
                proceed()
            }
            return
        }
        proceed()
    }

    private fun proceed() {
        if (isFinishing || isDestroyed) return
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
        /** The tagline's clear space above the panel, as on the language screen. */
        private const val TAGLINE_GAP_DP = 52f
        private const val PREFS = "welcome"
        private const val KEY_SEEN = "welcome_seen"

        /** True once the welcome has been shown — it is a first-run-only screen. */
        fun isSeen(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SEEN, false)
    }
}
