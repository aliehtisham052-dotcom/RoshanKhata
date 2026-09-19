package com.innovation313.roshankhata

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.Downloads
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.RegisterReport
import com.innovation313.roshankhata.ui.DateRangeFilter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.PdfShare
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/**
 * The sales-and-purchase register for a period the owner names.
 *
 * The counterpart to [InspectorReportActivity], and deliberately the opposite
 * in one way: that screen refuses a date range because stock is a fact about
 * today, while this one is built around a range because a register IS a fact
 * about a period. The default is the current month — the range a supplier or an
 * inspector asks about most often — and any other stretch is one tap away.
 *
 * A SCREEN first and a PDF second, the same order the other reports settled
 * into: the owner sees the totals for the chosen window here before he hands the
 * document to anyone.
 */
class RegisterReportActivity : AppCompatActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private lateinit var btnRange: MaterialButton
    private lateinit var btnPreview: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var tvSummary: TextView
    private lateinit var tvEmpty: TextView

    private var range: DateRangeFilter.Range = thisMonth()
    private var data: RegisterReport.ReportData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_report)
        ScreenInsets.on(this)

        tvSummary = findViewById(R.id.tvRegSummary)
        tvEmpty = findViewById(R.id.tvRegEmpty)

        btnRange = findViewById(R.id.btnRegRange)
        btnRange.setOnClickListener {
            DateRangeFilter.choose(this, range) { picked ->
                // "All time" is not a meaningful register window; fall back to
                // this month rather than printing the whole book undated.
                range = if (picked == DateRangeFilter.Range.ALL) thisMonth() else picked
                paintRange()
                load()
            }
        }

        btnPreview = findViewById(R.id.btnRegPreview)
        btnDownload = findViewById(R.id.btnRegDownload)
        btnShare = findViewById(R.id.btnRegShare)
        btnPreview.setOnClickListener { buildPdf { file -> PdfShare.open(this, file) } }
        btnDownload.setOnClickListener { buildPdf { file -> saveToDownloads(file) } }
        btnShare.setOnClickListener { buildPdf { file -> PdfShare.shareDirect(this, file) } }

        paintRange()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun paintRange() {
        btnRange.text = DateRangeFilter.label(this, range)
    }

    private fun load() {
        val r = range
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) {
                RegisterReport.gather(this@RegisterReportActivity, dao, r.from, r.to)
            }
            data = d
            render(d)
        }
    }

    private fun render(d: RegisterReport.ReportData) {
        tvSummary.text = buildString {
            append(getString(R.string.register_summary_sales, d.sales.size, Format.money(d.salesTotal)))
            append("\n")
            append(getString(R.string.register_summary_purchases, d.purchases.size, Format.money(d.purchaseTotal)))
            if (d.purchasesMissingRate > 0) {
                append("\n")
                append(getString(R.string.register_missing_rate, d.purchasesMissingRate))
            }
        }

        val has = !d.isEmpty
        tvEmpty.visibility = if (has) View.GONE else View.VISIBLE
        tvSummary.visibility = if (has) View.VISIBLE else View.GONE
        // Preview follows the same rule as the other two: with nothing to
        // report there is nothing to preview either, and a live button that
        // opens an empty document is worse than a grey one.
        btnPreview.isEnabled = has
        btnDownload.isEnabled = has
        btnShare.isEnabled = has
        btnPreview.alpha = if (has) 1f else 0.5f
        btnDownload.alpha = if (has) 1f else 0.5f
        btnShare.alpha = if (has) 1f else 0.5f
    }

    /** Built fresh on each tap, from the same range the screen is showing. */
    private fun buildPdf(then: (File) -> Unit) {
        Toast.makeText(this, R.string.register_generating, Toast.LENGTH_SHORT).show()
        val r = range
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                RegisterReport.build(this@RegisterReportActivity, dao, r.from, r.to)
            }
            if (file == null) {
                Toast.makeText(this@RegisterReportActivity, R.string.register_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            then(file)
        }
    }

    private fun saveToDownloads(file: File) {
        lifecycleScope.launch {
            val where = withContext(Dispatchers.IO) {
                Downloads.save(this@RegisterReportActivity, file, "application/pdf")
            }
            Toast.makeText(
                this@RegisterReportActivity,
                if (where != null) getString(R.string.report_saved_to, where)
                else getString(R.string.register_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** First day of the current month, 00:00, through this very moment. */
    private fun thisMonth(): DateRangeFilter.Range {
        val c = Calendar.getInstance()
        val from = Calendar.getInstance().apply {
            set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return DateRangeFilter.Range(from, System.currentTimeMillis(), R.string.range_this_month)
    }
}
