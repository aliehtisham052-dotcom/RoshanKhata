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
        }
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        val dao = KhataDatabase.get(this).khataDao()
        findViewById<ComposeView>(R.id.reportCompose).setContent {
            RoshanTheme(dark = dark) { ProvideWords(words) { SalesReportScreen(dao, onReport = { latest = it }) } }
        }
    }

    private fun share() {
        val r = latest ?: return
        val text = getString(R.string.sales_report_title) + "\n" + Format.dateOnly(r.from) + " – " + Format.dateOnly(r.to - 1) + "\n\n" +
            getString(R.string.srep_revenue) + ": " + Format.money(r.revenue) + "\n" +
            getString(R.string.srep_col_count) + ": " + r.saleCount + "\n" +
            getString(R.string.srep_returns) + ": " + Format.money(r.returnsTotal) + "\n" +
            getString(R.string.srep_cost) + ": " + Format.money(r.cost) + "\n" +
            getString(R.string.srep_gross) + ": " + Format.money(r.grossProfit) + "\n" +
            getString(R.string.srep_expenses) + ": " + Format.money(r.expenses) + "\n" +
            getString(R.string.srep_net) + ": " + Format.money(r.netProfit) + "\n\n" +
            r.byItem.take(15).joinToString("\n") { it.key + ": " + Format.money(it.revenue) }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.sales_report_title)))
    }
}
