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
                when (code) {
                    // The phone cannot ask at all (no screen lock any more, the sensor and its fallback not there):
                    // the lock cannot be honoured, and saying so beats tearing the app down on every return — a
                    // half-typed sale was lost each time (R98). As the front door has always done: told, let in.
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT -> {
                        // A sensor that is only busy (HW_UNAVAILABLE can pass in a moment) on a phone that still has
                        // a screen lock is no reason to open the book: only a phone with no screen lock at all is let in.
                        val secure = (getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager)
                            ?.isDeviceSecure ?: true
                        if (secure && code != BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) { finishAffinity(); return }
                        android.widget.Toast.makeText(
                            this@LockActivity,
                            if (code == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || code == BiometricPrompt.ERROR_NO_BIOMETRICS)
                                R.string.app_lock_no_screen_lock else R.string.app_lock_unavailable,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        if (intent.getBooleanExtra(EXTRA_RELOCK, false)) finish() else openLedger()
                    }
                    // The owner cancelled or backed out, or too many tries: the app closes rather than
                    // leaving them on a dead screen — never let through, or the lock would be decorative.
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
                .putExtra(MainActivity.EXTRA_OPEN_ARGS, intent.getBundleExtra(MainActivity.EXTRA_OPEN_ARGS))
        )
        finish()
    }
}
