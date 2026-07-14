package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.innovation313.roshankhata.data.AppLock

/**
 * The app's front door.
 *
 * If App Lock is off, this steps aside immediately and the owner never knows
 * it was there. If it is on, the ledger is not started at all until the lock
 * screen has been cleared — so a locked app leaks nothing, not even in the
 * recent-apps thumbnail.
 */
class GateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
}
