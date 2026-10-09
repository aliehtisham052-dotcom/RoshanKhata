package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.ui.TradePicker

/**
 * First run, step 2 of 2: what the shop sells (9 Oct).
 *
 * The first run used to be splash, a painting, language, a Google backup
 * page and then the shop type - six screens, the logo on four of them -
 * before the owner saw his ledger. It is now the splash, the language and
 * this question. The backup offer moved to the moment it means something:
 * Home asks once, after the first customer is written (MainActivity).
 *
 * A screen of its own rather than a dialog over the language screen,
 * because choosing a language recreates that screen in the new language and
 * would take a dialog with it. The page itself is the trade picker; this
 * activity only holds it, and builds it again after a rotation.
 */
class ShopTypeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restored backup can bring its trade with it: then nothing to ask.
        if (BusinessProfile.trade(this) != null) {
            proceed()
            return
        }
        TradePicker.show(this, null, required = true, step = 2) {
            BusinessProfile.setTrade(this, it)
            proceed()
        }
    }

    private fun proceed() {
        if (isFinishing || isDestroyed) return
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_UNLOCKED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
