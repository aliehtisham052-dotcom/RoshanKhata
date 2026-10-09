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
 * A screen of its own, with the trade picker page as its content rather
 * than a dialog shown over an empty window: a dialog opened from onCreate
 * left a blank sheet on screen for a moment after the language was chosen.
 * Back does nothing here - a new book must have a trade.
 */
class ShopTypeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A restored backup can bring its trade with it: then nothing to ask.
        if (BusinessProfile.trade(this) != null) {
            proceed()
            return
        }
        // The picker page as this screen's own content, in its first frame.
        setContentView(R.layout.dialog_trade_picker)
        TradePicker.bindActivity(this, findViewById(R.id.tradeRoot), step = 2) {
            BusinessProfile.setTrade(this, it)
            proceed()
        }
        // Required step: Back does not return to a half-done language screen.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
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
