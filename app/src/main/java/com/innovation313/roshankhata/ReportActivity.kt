package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.innovation313.roshankhata.data.BusinessProfile
import com.innovation313.roshankhata.data.PartyPhoto
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.PdfExport
import com.innovation313.roshankhata.ui.EntryAdapter
import com.innovation313.roshankhata.ui.EntryRow
import com.innovation313.roshankhata.ui.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * A party's ledger over a chosen period.
 *
 * The distinction that matters here: the three totals describe the WINDOW, but
 * the closing balance describes the WHOLE ACCOUNT. A customer who owes 50,000
 * still owes 50,000 even if they happened to pay nothing last week — reporting
 * a period's net as though it were the balance would be a false statement, and
 * this document is shareable.
 */
class ReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PARTY_ID = "party_id"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }

    private var partyId: Long = 0
    private var partyName: String = ""
    private var partyPhone: String? = null

    private lateinit var adapter: EntryAdapter
    private lateinit var tvRangeLabel: TextView
    private lateinit var tvTotalGave: TextView
    private lateinit var tvTotalGot: TextView
    private lateinit var tvNetChange: TextView
    private lateinit var tvClosingNote: TextView
    private lateinit var tvEmpty: TextView

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    /** Every row, chronologically, with true running balances. */
    private var allRows: List<EntryRow> = emptyList()

    /** Whole-ledger closing balance. Independent of any window. */
    private var closingBalance: Double = 0.0

    private var rangeStart: Long? = null
    private var rangeEnd: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        partyId = intent.getLongExtra(EXTRA_PARTY_ID, 0)
        if (partyId == 0L) {
            finish()
            return
        }

        tvRangeLabel = findViewById(R.id.tvRangeLabel)
        tvTotalGave = findViewById(R.id.tvTotalGave)
        tvTotalGot = findViewById(R.id.tvTotalGot)
        tvNetChange = findViewById(R.id.tvNetChange)
        tvClosingNote = findViewById(R.id.tvClosingNote)
        tvEmpty = findViewById(R.id.tvReportEmpty)

        adapter = EntryAdapter(
            onClick = { /* read-only report — tapping does nothing here */ },
            onLongClick = { /* read-only here — a report is not the place to delete */ }
        )
        val rv: RecyclerView = findViewById(R.id.rvReport)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ChipGroup>(R.id.chipRange).setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                R.id.chipAll -> setRange(null, null)
                R.id.chipToday -> setRange(startOfDay(System.currentTimeMillis()), null)
                R.id.chipWeek -> setRange(startOfDay(System.currentTimeMillis() - 7 * DAY_MS), null)
                R.id.chipMonth -> setRange(startOfDay(System.currentTimeMillis() - 30 * DAY_MS), null)
                R.id.chipCustom -> pickCustomRange()
            }
        }

        findViewById<MaterialButton>(R.id.btnShareReport).setOnClickListener { shareReport() }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            dao.getParty(partyId)?.let { p ->
                partyName = p.name
                partyPhone = p.phone
                findViewById<TextView>(R.id.tvReportTitle).text =
                    getString(R.string.report_title, p.name)
            }
        }

        lifecycleScope.launch {
            dao.observeEntries(partyId).collectLatest { newestFirst ->
                val oldestFirst = newestFirst.reversed()

                var running = 0.0
                allRows = oldestFirst.map { e ->
                    running += if (e.isGiven) e.amount else -e.amount
                    EntryRow(e, running)
                }.reversed()

                // The account's true position, regardless of the window chosen.
                closingBalance = running

                render()
            }
        }
    }

    private fun setRange(start: Long?, end: Long?) {
        rangeStart = start
        rangeEnd = end
        render()
    }

    private fun pickCustomRange() {
        val cal = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, y1, m1, d1 ->
                val start = Calendar.getInstance().apply {
                    set(y1, m1, d1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                DatePickerDialog(
                    this,
                    { _, y2, m2, d2 ->
                        // End of the chosen day, not its start — otherwise a
                        // same-day range would show nothing, which would look
                        // like data loss rather than an off-by-one.
                        val end = Calendar.getInstance().apply {
                            set(y2, m2, d2, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }.timeInMillis

                        if (end < start) {
                            Toast.makeText(this, R.string.invalid_range, Toast.LENGTH_LONG).show()
                            return@DatePickerDialog
                        }

                        setRange(start, end)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).apply { setTitle(getString(R.string.pick_end_date)) }.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply { setTitle(getString(R.string.pick_start_date)) }.show()
    }

    private fun rowsInRange(): List<EntryRow> {
        val start = rangeStart
        val end = rangeEnd

        return allRows.filter { row ->
            val t = row.entry.timestamp
            (start == null || t >= start) && (end == null || t <= end)
        }
    }

    private fun render() {
        val rows = rowsInRange()
        adapter.submit(rows)

        tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        val gave = rows.filter { it.entry.isGiven }.sumOf { it.entry.amount }
        val got = rows.filter { !it.entry.isGiven }.sumOf { it.entry.amount }

        tvTotalGave.text = Format.money(gave)
        tvTotalGot.text = Format.money(got)
        tvNetChange.text = Format.money(gave - got)

        tvRangeLabel.text = when {
            rangeStart == null -> getString(R.string.range_label_all)
            else -> getString(
                R.string.range_label,
                Format.dateOnly(rangeStart!!),
                Format.dateOnly(rangeEnd ?: System.currentTimeMillis())
            )
        }

        // Spell out the difference between the window and the account, so the
        // owner never mistakes one for the other.
        tvClosingNote.text = if (rangeStart == null) {
            getString(R.string.closing_note_all, Format.money(closingBalance))
        } else {
            getString(R.string.closing_note_range, Format.money(closingBalance))
        }
    }

    private fun shareReport() {
        val rows = rowsInRange()
        if (rows.isEmpty()) {
            Toast.makeText(this, R.string.no_entries_in_range, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                PdfExport.buildStatement(
                    context = this@ReportActivity,
                    partyName = partyName,
                    partyPhone = partyPhone,
                    rows = rows.map { PdfExport.StatementRow(it.entry, it.runningBalance) },
                    // The whole account's balance, never the window's net —
                    // this PDF goes to the customer.
                    closingBalance = closingBalance,
                    businessName = BusinessProfile.businessName(this@ReportActivity),
                    paymentQr = BusinessProfile.loadQr(this@ReportActivity),
                    // Only if the owner has turned it on. Off by default.
                    partyPhoto = if (BusinessProfile.photoOnStatement(this@ReportActivity)) {
                        PartyPhoto.load(this@ReportActivity, partyId)
                    } else {
                        null
                    }
                )
            }

            if (file == null) {
                Toast.makeText(this@ReportActivity, R.string.statement_failed, Toast.LENGTH_LONG)
                    .show()
                return@launch
            }

            val uri = FileProvider.getUriForFile(
                this@ReportActivity,
                "$packageName.fileprovider",
                file
            )

            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_statement))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.share_statement)))
        }
    }

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
