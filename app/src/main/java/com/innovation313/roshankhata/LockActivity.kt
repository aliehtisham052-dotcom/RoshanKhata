package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.data.AppLock

/**
 * The gate shown before the ledger is visible.
 *
 * Nothing of the user's data is drawn behind this screen — the ledger activity
 * is not started until authentication succeeds, so a locked app shows nothing
 * even in the recent-apps preview.
 */
class LockActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        // Edge-to-edge, the mechanism proven on the Home screen.
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        onBackPressedDispatcher.addCallback(this, backGuard)

        prompt()
    }

    private fun prompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Asked again on coming back: the screen underneath is the owner's again.
                if (intent.getBooleanExtra(EXTRA_RELOCK, false)) finish() else openLedger()
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                // The owner cancelled or backed out. Close the app rather than
                // leaving them stranded on a dead screen — but never let them
                // through, since that would make the lock decorative.
                when (code) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_CANCELED -> finishAffinity()

                    else -> finishAffinity()
                }
            }

            override fun onAuthenticationFailed() {
                // A wrong finger. The system prompt stays up and lets them
                // try again — nothing for us to do here.
            }
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setSubtitle(getString(R.string.lock_subtitle))
            .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
            .build()

        BiometricPrompt(this, executor, callback).authenticate(info)
    }

    /**
     * Back on the lock must not reveal the screen underneath. Through the dispatcher, not an onBackPressed
     * override: on Android 16 an app targeting API 36 never gets onBackPressed (Google's Android 16 behaviour
     * changes, checked 23 Oct 2026), so the old override let Back close the relock and show the ledger.
     */
    private val backGuard = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (intent.getBooleanExtra(EXTRA_RELOCK, false)) finishAffinity() else finish()
        }
    }

    companion object {
        /** Started over an open screen after time away: on success it only closes itself. */
        const val EXTRA_RELOCK = "relock"
    }

    private fun openLedger() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_UNLOCKED, true)
                .putExtra(MainActivity.EXTRA_OPEN, intent.getStringExtra(MainActivity.EXTRA_OPEN))
        )
        finish()
    }
}
