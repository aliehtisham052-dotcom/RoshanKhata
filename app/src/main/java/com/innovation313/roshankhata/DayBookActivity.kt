package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.DayBook
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.ZReport
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.screens.DayBookScreen
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.ProvideWords

/**
 * The day book (3 Nov 2026, the competitor study: the Day report (Z) was a dialog of the day's totals, today's
 * only, with no entries): the same figures over every money movement of the day in the order it happened, any
 * day stepped back to, shared or printed whole. The Cashbook opens it behind the REPORTS permission, as it opened
 * the dialog (a day's profit and the till's difference are the shop's figures).
 */
class DayBookActivity : BaseActivity() {

    private var shown: Pair<ZReport?, List<DayBook.Row>>? = null
    private var dayShown: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day_book)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        val today = DayCloseMath.dayOf(System.currentTimeMillis())
        val start = savedInstanceState?.getLong(STATE_DAY, today) ?: intent.getLongExtra(EXTRA_DAY, today)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
            menu.add(R.string.share).setOnMenuItemClickListener { share(); true }
            menu.add(R.string.print_receipt).setOnMenuItemClickListener { print(); true }
        }
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        val dao = KhataDatabase.get(this).khataDao()
        dayShown = start.coerceAtMost(today)
        findViewById<ComposeView>(R.id.dayBookCompose).setContent {
            var day by androidx.compose.runtime.remember { mutableLongStateOf(dayShown) }
            RoshanTheme(dark = dark) {
                ProvideWords(words) {
                    DayBookScreen(dao, day, onDay = { d -> day = d; dayShown = d; shown = null },
                        onLoaded = { z, rows -> shown = z to rows })
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_DAY, dayShown)
    }

    /** The day as text: the shop and date, the report's three blocks, then each entry short. [bold] is for WhatsApp, not the printer. */
    private fun text(bold: Boolean): String? {
        val (z, rows) = shown ?: return null
        val words = AndroidWords(this)
        val date = Format.dateOnly(DayCloseMath.startOf(dayShown) + 12L * 60 * 60 * 1000)
        val shop = com.innovation313.roshankhata.data.BusinessProfile.businessName(this)?.trim().orEmpty()
        val clock = DateWords.formatter("h:mm a")
        return DayBook.shareText(
            words, shop, date, z, rows,
            money = { Format.money(it) },
            time = { clock.format(java.util.Date(it)) },
            entriesHeading = Digits.quantity(resources, R.plurals.bin_party_entries, rows.size, rows.size),
            bold = bold
        )
    }

    private fun share() {
        val t = text(bold = true) ?: return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), getString(R.string.z_title)))
    }

    private fun print() {
        val t = text(bold = false) ?: return
        com.innovation313.roshankhata.ui.TextReceipt.print(this, lifecycleScope, t)
    }

    companion object {
        /** The day to open on (a DayCloseMath day number); today when left out. */
        const val EXTRA_DAY = "day"
        private const val STATE_DAY = "day_shown"
    }
}
