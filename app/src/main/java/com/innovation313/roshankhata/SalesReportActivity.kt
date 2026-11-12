package com.innovation313.roshankhata

import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.SalesReport
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.screens.SalesReportScreen
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.ProvideWords
import kotlinx.coroutines.launch

/** The sales report (POS Phase 7): the shared screen; the profit summary shared as text. */
class SalesReportActivity : BaseActivity() {
    private var latest: SalesReport? = null
    /** The rows on screen and their heading, for Excel (3 Nov 2026): what the computer's page exports. */
    private var latestRows: Triple<String, List<String>, List<List<String>>>? = null
    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    /** Excel: the system's save sheet names the file; the rows on screen are written to it, as on the computer. */
    private val saveCsv = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri: android.net.Uri? ->
        val rows = latestRows
        if (uri == null || rows == null) return@registerForActivityResult
        writeCsv(uri, rows)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sales_report)
        com.innovation313.roshankhata.ui.ScreenInsets.on(this)
        // A range picker the system brought back after a rotation has lost what it should tell (its callback was the
        // old screen's); it is closed rather than left to do nothing (8 Nov 2026 review, L147). Tap Custom again.
        (supportFragmentManager.findFragmentByTag("report_range") as? androidx.fragment.app.DialogFragment)?.dismissAllowingStateLoss()
        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
            menu.add(R.string.share).setOnMenuItemClickListener { share(); true }
            // The rows on screen as a spreadsheet (3 Nov 2026, the competitor study: the phone shared text only). An
            // export waits for a manager's PIN where a cashier may not export, as the computer's does.
            menu.add(R.string.export_excel).setOnMenuItemClickListener { exportExcel(); true }
            // Dues by age (21 Oct 2026): who owes what, and for how long.
            menu.add(R.string.aging_title).setOnMenuItemClickListener { startActivity(Intent(this@SalesReportActivity, AgingActivity::class.java)); true }
        }
        val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val words = AndroidWords(this)
        // Opened on a period of its own (12 Nov 2026, Hudhud's "is hafte ki sale", "pichle mahine ki sale").
        val startPeriod = intent.getStringExtra(EXTRA_PERIOD)
            ?.let { name -> com.innovation313.roshankhata.screens.ReportPeriod.values().firstOrNull { it.name == name } }
            ?: com.innovation313.roshankhata.screens.ReportPeriod.TODAY
        val firstDay = intent.getLongExtra(EXTRA_FIRST_DAY, 0L)
        val lastDay = intent.getLongExtra(EXTRA_LAST_DAY, 0L)
        val startCustom = if (firstDay > 0L && lastDay >= firstDay) firstDay to lastDay else null
        findViewById<ComposeView>(R.id.reportCompose).setContent {
            RoshanTheme(dark = dark) { ProvideWords(words) {
                SalesReportScreen(dao, onReport = { latest = it }, pickRange = { current, picked -> pickRange(current, picked) },
                    onRows = { heading, head, rows -> latestRows = Triple(heading, head, rows) },
                    startPeriod = startPeriod, startCustom = startCustom)
            } }
        }
    }

    companion object {
        /** A ReportPeriod's name to open on. */
        const val EXTRA_PERIOD = "sales_report_period"
        /** The first and last day to open on, each as the start of its day (the custom period). */
        const val EXTRA_FIRST_DAY = "sales_report_first_day"
        const val EXTRA_LAST_DAY = "sales_report_last_day"
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

    private fun exportExcel() {
        if (latestRows == null) return
        val p = com.innovation313.roshankhata.data.Access.Permission.EXPORT
        val go = { saveCsv.launch("RoshanKhata_Sales_" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date()) + ".csv") }
        if (com.innovation313.roshankhata.data.Access.can(p)) go()
        else com.innovation313.roshankhata.ui.AccessUi.allowed(this, dao, p, com.innovation313.roshankhata.ui.AccessUi.what(this, p)) { _ -> go() }
    }

    /** The heading, the column names and the rows, quoted cell by cell (CsvCell), with the mark Excel reads UTF-8 by. */
    private fun writeCsv(uri: android.net.Uri, rows: Triple<String, List<String>, List<List<String>>>) {
        lifecycleScope.launch {
            // The name the save sheet gave the file, as the owner will look for it.
            val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull()
            } ?: "RoshanKhata_Sales.csv"
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val csv = StringBuilder("\ufeff")
                    com.innovation313.roshankhata.data.CsvCell.appendRow(csv, listOf(rows.first))
                    (listOf(rows.second) + rows.third).forEach { row -> com.innovation313.roshankhata.data.CsvCell.appendRow(csv, row) }
                    contentResolver.openOutputStream(uri)!!.use { it.write(csv.toString().toByteArray(Charsets.UTF_8)) }
                    // Who did what (25 Oct 2026, the computer's rule): a file of the shop's sales left the app.
                    runCatching { dao.log(com.innovation313.roshankhata.data.AuditAction.EXPORT, com.innovation313.roshankhata.data.Access.userName, detail = name) }
                }.isSuccess
            }
            android.widget.Toast.makeText(this@SalesReportActivity,
                if (ok) getString(R.string.report_saved_to, name) else getString(R.string.ledger_report_failed), android.widget.Toast.LENGTH_LONG).show()
        }
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
