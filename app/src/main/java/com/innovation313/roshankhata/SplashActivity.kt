package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
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

        val locked = AppLock.isEnabled(this) && AppLock.isAvailable(this)

        val fingerprint = findViewById<ImageView>(R.id.ivFingerprint)
        val unlockHint = findViewById<TextView>(R.id.tvUnlockHint)
        val tapHint = findViewById<TextView>(R.id.tvTapHint)

        if (locked) {
            // Locked: the fingerprint icon is shown, but only as a SIGN of what
            // is about to happen. The owner does not tap it — the real unlock
            // prompt comes up on its own, the way the apps they already use
            // behave. A splash that makes them tap first, then authenticate, is
            // two steps where one will do.
            fingerprint.visibility = View.VISIBLE
            unlockHint.visibility = View.VISIBLE
            tapHint.visibility = View.GONE

            // Go straight to the gate, which raises the biometric prompt. A
            // short delay lets the logo actually register first — otherwise the
            // system sheet slides up over a splash nobody had time to see.
            fingerprint.postDelayed({
                if (!isFinishing) {
                    startActivity(Intent(this, GateActivity::class.java))
                    finish()
                }
            }, 550)
        } else {
            // Not locked: nothing to authenticate, so a plain tap advances. No
            // fingerprint — a lock symbol on an unlocked app is a promise the
            // app is not keeping.
            fingerprint.visibility = View.GONE
            unlockHint.visibility = View.GONE
            tapHint.visibility = View.VISIBLE

            findViewById<View>(R.id.splashRoot).setOnClickListener {
                startActivity(Intent(this, GateActivity::class.java))
                finish()
            }
        }
    }
}
