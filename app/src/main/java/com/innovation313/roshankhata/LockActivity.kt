package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
class LockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)

        prompt()
    }

    private fun prompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                openLedger()
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

    private fun openLedger() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_UNLOCKED, true)
        )
        finish()
    }
}
