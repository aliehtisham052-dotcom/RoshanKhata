package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.SalesReport
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.screens.SalesReportScreen
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.ProvideWords

/** The sales report (POS Phase 7): the shared screen; the profit summary shared as text. */
class SalesReportActivity : BaseActivity() {
    private var latest: SalesReport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_report)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
            menu.add(R.string.share).setOnMenuItemClickListener { share(); true }
            // Dues by age (21 Oct 2026): who owes what, and for how long.
            menu.add(R.string.aging_title).setOnMenuItemClickListener { startActivity(Intent(this@SalesReportActivity, AgingActivity::class.java)); true }
        }
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        val dao = KhataDatabase.get(this).khataDao()
        findViewById<ComposeView>(R.id.reportCompose).setContent {
            RoshanTheme(dark = dark) { ProvideWords(words) { SalesReportScreen(dao, onReport = { latest = it }, pickRange = { current, picked -> pickRange(current, picked) }) } }
        }
    }

    /**
     * The report's own first and last day (21 Oct 2026): Material's range
     * picker. It speaks UTC midnights; the report wants this phone's days,
     * so each end is turned into the local start of the day it names.
     */
    private fun pickRange(current: Pair<Long, Long>?, picked: (Pair<Long, Long>) -> Unit) {
        fun toUtc(local: Long): Long {
            val day = com.innovation313.roshankhata.data.DayCloseMath.dayOf(local)
            return day * 24L * 60 * 60 * 1000
        }
        fun toLocal(utc: Long): Long = com.innovation313.roshankhata.data.DayCloseMath.startOf(Math.floorDiv(utc, 24L * 60 * 60 * 1000))
        val builder = com.google.android.material.datepicker.MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.srep_period_custom)
        current?.let { builder.setSelection(androidx.core.util.Pair(toUtc(it.first), toUtc(it.second))) }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { sel ->
            val a = sel.first ?: return@addOnPositiveButtonClickListener
            val b = sel.second ?: a
            picked(toLocal(a) to toLocal(b))
        }
        picker.show(supportFragmentManager, "report_range")
    }

    private fun share() {
        val r = latest ?: return
        val text = getString(R.string.sales_report_title) + "\n" + Format.dateOnly(r.from) + " – " + Format.dateOnly(r.to - 1) + "\n\n" +
            getString(R.string.srep_revenue) + ": " + Format.money(r.revenue) + "\n" +
            getString(R.string.srep_col_count) + ": " + r.saleCount + "\n" +
            getString(R.string.srep_returns) + ": " + Format.money(r.returnsTotal) + "\n" +
            getString(R.string.srep_cost) + ": " + Format.money(r.cost) + "\n" +
            getString(R.string.srep_gross) + ": " + (r.grossOrNull?.let { Format.signedIfNegative(it) } ?: "–") + "\n" +
            getString(R.string.srep_expenses) + ": " + Format.money(r.expenses) + "\n" +
            // Only when every item sold has its cost (25 Oct 2026, the audit; SalesReport.costComplete).
            getString(R.string.srep_net) + ": " + (r.netOrNull?.let { Format.signedIfNegative(it) } ?: ("–\n" + getString(R.string.srep_net_unknown))) + "\n\n" +
            r.byItem.take(15).joinToString("\n") { it.key + ": " + Format.money(it.revenue) }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.sales_report_title)))
    }
}
