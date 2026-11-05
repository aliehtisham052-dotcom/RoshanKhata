package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.ui.TradePicker

/**
 * First run, step 2 of 3: what the shop sells (9 Oct; the name became step 3 on 11 Oct).
 *
 * The first run used to be splash, a painting, language, a Google backup
 * page and then the shop type - six screens, the logo on four of them -
 * before the owner saw his ledger. It is now the splash, the language, this
 * question and the shop's details. The backup offer moved to the moment it
 * means something: Home asks once, after the first customer is written.
 *
 * A screen of its own, with the trade picker page as its content rather
 * than a dialog shown over an empty window: a dialog opened from onCreate
 * left a blank sheet on screen for a moment after the language was chosen.
 *
 * Back goes to the language (5 Nov 2026, the owner's report: Back did nothing here, and the
 * setup could only be left by closing the app). A trade already chosen - the owner walking
 * back from step 3 - is shown marked, so Continue goes straight on. The setup is finished on
 * step 3 alone ([com.innovation313.roshankhata.data.SetupProgress]).
 */
class ShopTypeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The picker page as this screen's own content, in its first frame.
        setContentView(R.layout.dialog_trade_picker)
        val chosen = BusinessProfile.trade(this)?.let { Trade.of(it) }
        TradePicker.bindActivity(this, findViewById(R.id.tradeRoot), step = 2, current = chosen, onBack = { back() }) {
            BusinessProfile.setTrade(this, it)
            proceed()
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
    }

    /** Back to step 1, the language, with the one in use marked. */
    private fun back() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, LanguageActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.screen_close_enter, R.anim.screen_close_exit)
        finish()
    }

    /** On to step 3, the shop's details. */
    private fun proceed() {
        if (isFinishing || isDestroyed) return
        startActivity(Intent(this, ShopNameActivity::class.java))
        finish()
    }
}
