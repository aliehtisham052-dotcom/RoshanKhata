package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.innovation313.roshankhata.data.AppLock

/**
 * The app's front door.
 *
 * If App Lock is off, this steps aside immediately and the owner never knows
 * it was there. If it is on, the ledger is not started at all until the lock
 * screen has been cleared — so a locked app leaks nothing, not even in the
 * recent-apps thumbnail.
 *
 * This also carries the system splash — the single logo beat during cold
 * start — so there is no separate splash screen to show the mark a second time.
 */
class GateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The branded beat comes first, always — before the language picker on
        // a first run and before the ledger on every one after. It used to be
        // skipped entirely on the very first launch, which meant a new owner
        // met the language list before they had seen the app's own name.
        setContentView(R.layout.activity_gate)

        // Long enough to read on the first launch, brief on the rest. Someone
        // opening the app for the tenth time today wants their ledger, not the
        // logo they already know.
        val firstRun = !LanguageActivity.isChosen(this)
        val hold = if (firstRun) SPLASH_FIRST_MS else SPLASH_MS

        Handler(Looper.getMainLooper()).postDelayed({ route() }, hold)
    }

    private fun route() {
        if (isFinishing || isDestroyed) return

        // First run: the language picker, now that the mark has been shown.
        // Once chosen it never appears here again.
        if (!LanguageActivity.isChosen(this)) {
            startActivity(Intent(this, LanguageActivity::class.java))
            finish()
            return
        }

        val locked = AppLock.isEnabled(this) && AppLock.isAvailable(this)

        // If the owner turned the lock on but has since removed their screen
        // lock, we cannot honour it — and pretending otherwise (silently
        // letting them in while the setting still reads "on") would be a lie.
        // Falling through is the honest behaviour; the settings screen tells
        // them plainly that no screen lock is set.
        val next = if (locked) LockActivity::class.java else MainActivity::class.java

        startActivity(
            Intent(this, next).putExtra(MainActivity.EXTRA_UNLOCKED, !locked)
        )
        finish()
    }

    companion object {
        /** The first launch, where the name is worth a moment. */
        private const val SPLASH_FIRST_MS = 1200L

        /** Every launch after: enough to see, not enough to wait through. */
        private const val SPLASH_MS = 500L
    }
}
