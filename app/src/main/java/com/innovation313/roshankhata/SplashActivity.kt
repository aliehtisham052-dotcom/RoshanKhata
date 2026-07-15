package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.innovation313.roshankhata.data.AppLock

/**
 * The opening screen: the logo, and "Powered by Innovation-313".
 *
 * It waits for a tap rather than timing out on its own — the owner asked for
 * this deliberately, and there is sense in it. A splash that vanishes after two
 * seconds is a splash nobody reads; one that waits lets the brand actually land
 * before the work begins. The owner controls the moment, not a timer.
 *
 * It is exported and set as the launcher, so it is the first thing seen. It
 * carries no data and touches no ledger — it only shows the mark and steps
 * aside.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the system splash before anything else. This is what makes
        // the OS show OUR mark on OUR background during the cold-start frame,
        // instead of the bare launcher icon on a default background — the little
        // circle that used to flash up first and read as a separate screen.
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Show the fingerprint ONLY when app lock is actually on. A lock symbol
        // on an app that isn't locked is a promise the app isn't keeping — so
        // the owner who set no lock sees the plain "Tap to continue", and the
        // owner who did set one sees the fingerprint and "Touch to unlock". The
        // two are mutually exclusive: the app either needs unlocking or it does
        // not.
        val locked = AppLock.isEnabled(this) && AppLock.isAvailable(this)

        val fingerprint = findViewById<ImageView>(R.id.ivFingerprint)
        val unlockHint = findViewById<TextView>(R.id.tvUnlockHint)
        val tapHint = findViewById<TextView>(R.id.tvTapHint)

        if (locked) {
            fingerprint.visibility = View.VISIBLE
            unlockHint.visibility = View.VISIBLE
            tapHint.visibility = View.GONE
        } else {
            fingerprint.visibility = View.GONE
            unlockHint.visibility = View.GONE
            tapHint.visibility = View.VISIBLE
        }

        val proceed = View.OnClickListener {
            // GateActivity is the real decision point — it sends a locked app to
            // the unlock screen and an unlocked one straight in. The splash only
            // hands off; it never decides. One gate, not two.
            startActivity(Intent(this, GateActivity::class.java))
            finish()
        }

        findViewById<View>(R.id.splashRoot).setOnClickListener(proceed)
        fingerprint.setOnClickListener(proceed)
    }
}
