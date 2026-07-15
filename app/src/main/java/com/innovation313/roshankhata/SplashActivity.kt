package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

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

        findViewById<View>(R.id.splashRoot).setOnClickListener {
            startActivity(Intent(this, GateActivity::class.java))
            // No going "back" to the splash — it has done its job.
            finish()
        }
    }
}
