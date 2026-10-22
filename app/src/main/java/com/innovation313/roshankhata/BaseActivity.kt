package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.refresh

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.innovation313.roshankhata.data.TextSize
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.ViewerMode
import com.innovation313.roshankhata.ui.TextFit

/**
 * What every screen shares: the owner's text size.
 *
 * Every screen in the app extends this rather than AppCompatActivity, so the
 * size chosen once reaches all of them — there is no screen that forgot.
 *
 * The wrapped context is handed to AppCompat's own attachBaseContext, which
 * then applies the per-app language on top of it. The on-device TextSizeTest
 * checks both survive together — size AND language — because that pairing is
 * the part most likely to break in some future AppCompat.
 */
abstract class BaseActivity : AppCompatActivity() {

    /** The last finger-down, for the paper plane's starting point (ui/Motion, 5 Oct). */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            // A tap that finds a counter session ended goes no further: the sign-in sheet comes instead.
            if (sessionEnded()) return true
            com.innovation313.roshankhata.ui.Motion.lastTap = ev.rawX to ev.rawY
            com.innovation313.roshankhata.ui.Motion.lastTapAt = System.currentTimeMillis()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && sessionEnded()) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * Something was done on this screen (a tap, a key). On a counter, a
     * manager's or the owner's session idle for five minutes ends here and
     * the sign-in sheet comes back; true when it did, so the tap that found it
     * ended does nothing else (22 Oct 2026, the review: one sign-in to change
     * a price left the cashier with the owner's rights all day).
     */
    private fun sessionEnded(): Boolean {
        val access = com.innovation313.roshankhata.data.Access
        val wasIn = access.ownerIn || access.current != null
        access.touch()
        if (!wasIn || !access.locked || javaClass.simpleName in BEFORE_THE_BOOK) return false
        com.innovation313.roshankhata.ui.AccessUi.lockGate(this)
        return true
    }

    /** The text size this screen was built at; see onResume. */
    private var builtAtLevel = -1

    /**
     * On a read-only phone (see [ViewerMode]) a screen that exists only to
     * change the book or its backups does not open at all. Finishing here
     * means it is never shown; the DAO would refuse its writes anyway.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.innovation313.roshankhata.data.PrintDigits.load(this)
        if (ViewerMode.isOn(this) && ViewerMode.ownerOnly(javaClass)) {
            Toast.makeText(this, R.string.viewer_refused, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        builtAtLevel = TextSize.level(newBase)
        super.attachBaseContext(TextSize.wrap(newBase))
    }

    /**
     * The size can now be changed from any screen's More list (2 Oct), not
     * only Home's. A screen left in the back stack rebuilds itself when it
     * comes forward at a size it was not built at.
     */
    override fun onResume() {
        super.onResume()
        // The shop may have been switched, restored or loaded since this
        // screen last showed; figures must wear its sign (see Currency).
        Currency.refresh(this)
        com.innovation313.roshankhata.data.ShopCalendar.refresh(this)
        if (builtAtLevel >= 0 && builtAtLevel != TextSize.level(this)) {
            builtAtLevel = TextSize.level(this)
            recreate()
            return
        }
        // A counter phone with nobody signed in shows nothing but the sign-in
        // sheet (21 Oct 2026). The doors before the book are left alone.
        // A manager's or the owner's session idle too long has ended first (22 Oct 2026, the review).
        com.innovation313.roshankhata.data.Access.expireIfIdle()
        if (javaClass.simpleName !in BEFORE_THE_BOOK) com.innovation313.roshankhata.ui.AccessUi.lockGate(this)
    }

    /**
     * A screen a cashier may not open alone (backups, settings, staff, the
     * bin, the reports) stays hidden until a manager or the owner allows it,
     * and closes if nobody does — checked here so there is no way in that
     * forgot to ask.
     */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!isFinishing) com.innovation313.roshankhata.ui.AccessUi.gateScreen(this)
    }

    override fun onDestroy() {
        com.innovation313.roshankhata.ui.AccessUi.screenGone(this)
        super.onDestroy()
    }

    /**
     * App Lock asks again when the app comes back after [RELOCK_MS] away
     * (21 Oct 2026, the critical review): it was asked once when the app
     * started, and a phone left unlocked on the counter showed the book to
     * whoever picked it up hours later. Counted across every screen, so
     * moving between the app's own screens is not "away".
     */
    override fun onStart() {
        super.onStart()
        if (startedScreens++ == 0) {
            val away = stoppedAt
            stoppedAt = 0L
            if (away > 0L && System.currentTimeMillis() - away > RELOCK_MS &&
                javaClass.simpleName !in BEFORE_THE_BOOK &&
                com.innovation313.roshankhata.data.AppLock.isEnabled(this)
            ) {
                startActivity(android.content.Intent(this, LockActivity::class.java).putExtra(LockActivity.EXTRA_RELOCK, true))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (--startedScreens <= 0) { startedScreens = 0; stoppedAt = System.currentTimeMillis() }
    }

    private companion object {
        /** Screens shown before the book opens: the splash's router, App Lock, and the first-run questions. */
        val BEFORE_THE_BOOK = setOf("GateActivity", "LockActivity", "LanguageActivity", "ShopTypeActivity", "ShopNameActivity")
        /** Away this long, App Lock asks again. */
        const val RELOCK_MS = 5 * 60_000L
        var startedScreens = 0
        var stoppedAt = 0L
    }

    /** After setContentView: let fixed-height buttons grow if the text did. */
    override fun onContentChanged() {
        super.onContentChanged()
        TextFit.relax(findViewById(android.R.id.content))
        ViewerMode.lockWriteButtons(this)
    }
}
