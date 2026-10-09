package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
class GateActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // The one splash (9 Oct): Android's own, in the app's green with the
        // logo (Theme.RoshanKhata.Splash), held while this screen decides
        // where to go and handed straight to the next. This screen used to
        // draw a painting of its own after it and hold it 0.5-1.2 s, so every
        // launch showed two splashes - and on a first run the language and
        // welcome screens showed the same painting again.
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { true }
        super.onCreate(savedInstanceState)
        route()
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
            Intent(this, next)
                .putExtra(MainActivity.EXTRA_UNLOCKED, !locked)
                // A notification's screen, carried through the lock unopened.
                .putExtra(MainActivity.EXTRA_OPEN, intent.getStringExtra(MainActivity.EXTRA_OPEN))
        )
        finish()
    }
}
