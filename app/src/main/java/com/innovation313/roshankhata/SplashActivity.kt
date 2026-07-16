package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * A short opening screen showing only "Powered by Innovation-313" on the brand
 * background, then it hands off to GateActivity (which decides lock-or-home).
 *
 * Deliberately text-only — the previous logo splash showed a black-boxed image
 * two or three times, which the owner disliked. This is one clean wordmark line
 * for just over a second.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, GateActivity::class.java))
            finish()
        }, 1200)
    }
}
