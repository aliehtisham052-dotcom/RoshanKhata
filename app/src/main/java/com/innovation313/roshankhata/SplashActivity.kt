package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/**
 * The opening screen: the logo, and "Powered by Innovation-313".
 *
 * The owner asked for this to WAIT for a tap rather than time out. That is a
 * deliberate choice and worth respecting exactly — a splash that vanishes on a
 * timer is a splash nobody reads. This one holds until touched, so the brand
 * actually registers, and the whole screen is the target so there is nothing
 * to aim for.
 *
 * It sits IN FRONT of the gate, not in place of it. Once tapped it hands off to
 * GateActivity, which decides whether the App Lock must be cleared first. The
 * splash makes no security decision of its own — it only shows the brand and
 * steps aside.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        findViewById<View>(android.R.id.content).setOnClickListener {
            startActivity(Intent(this, GateActivity::class.java))
            finish()
        }
    }
}
