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

        // First run: the setup's first unfinished step, now that the mark has been shown (5 Nov
        // 2026: a setup left at the shop type or the details used to open Home next time, its
        // name and country never asked). Once the setup is done it never appears here again.
        val setupStep = when (com.innovation313.roshankhata.data.SetupProgress.next(this)) {
            com.innovation313.roshankhata.data.SetupProgress.Step.LANGUAGE -> LanguageActivity::class.java
            com.innovation313.roshankhata.data.SetupProgress.Step.SHOP_TYPE -> ShopTypeActivity::class.java
            com.innovation313.roshankhata.data.SetupProgress.Step.SHOP_DETAILS -> ShopNameActivity::class.java
            com.innovation313.roshankhata.data.SetupProgress.Step.DONE -> null
        }
        if (setupStep != null) {
            startActivity(Intent(this, setupStep))
            finish()
            return
        }

        val locked = AppLock.required(this)

        // If the owner turned the lock on but has since removed their screen
        // lock, we cannot honour it — and pretending otherwise (silently
        // letting them in while the setting still reads "on") would be a lie.
        // Falling through is the honest behaviour; the settings screen tells
        // them plainly that no screen lock is set.
        val next = if (locked) LockActivity::class.java else MainActivity::class.java

        startActivity(
            Intent(this, next)
                .putExtra(MainActivity.EXTRA_UNLOCKED, !locked)
                // A notification's screen, carried through the lock unopened. Read with care (3 Nov 2026 audit,
                // L24): this door is exported, and on Android 7-12 a stranger's unreadable extra threw here.
                .putExtra(MainActivity.EXTRA_OPEN, runCatching { intent.getStringExtra(MainActivity.EXTRA_OPEN) }.getOrNull())
                .putExtra(MainActivity.EXTRA_OPEN_ARGS, runCatching { intent.getBundleExtra(MainActivity.EXTRA_OPEN_ARGS) }.getOrNull())
                .putExtra(MainActivity.EXTRA_OPEN_BUSINESS, runCatching { intent.getLongExtra(MainActivity.EXTRA_OPEN_BUSINESS, -1L) }.getOrDefault(-1L))
        )
        finish()
    }
}
