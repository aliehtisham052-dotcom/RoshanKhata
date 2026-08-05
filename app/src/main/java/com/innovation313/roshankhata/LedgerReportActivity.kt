package com.innovation313.roshankhata

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.innovation313.roshankhata.data.EntryWithParty
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerReport
import com.innovation313.roshankhata.ui.DateRangeFilter
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.PdfShare
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The whole ledger on one screen — every entry across every customer in a
 * chosen window, read BEFORE any PDF exists.
 *
 * Until now the whole-ledger report only existed as a PDF: pick a range,
 * wait, open a document. What the owner actually wanted (seen working in a
 * competitor and asked for by name) is the report as a living screen —
 * totals in sight, rows scrolling under them, and the PDF demoted to what
 * it really is: the takeaway copy of a page already read. Download opens
 * that PDF preview-first, as every sight-unseen document does; Share goes
 * straight to the share sheet, because here — uniquely — the owner is
 * already looking at exactly what the document will hold.
 *
 * Everything under the surface is reused, not rebuilt: the same
 * [DateRangeFilter] windows as the chooser dialog, the same
 * `entriesInRange` query, the same [LedgerReport] renderer. One definition
 * of the report, two doors to it.
 */
class LedgerReportActivity : AppCompatActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private lateinit var adapter: Adapter
    private lateinit var tvRangeLabel: TextView
    private lateinit var tvTotalGave: TextView
    private lateinit var tvTotalGot: TextView
    private lateinit var tvNetChange: TextView
    private lateinit var tvEmpty: TextView

    private var range: DateRangeFilter.Range = DateRangeFilter.Range.ALL
    private var entries: List<EntryWithParty> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ledger_report)
        ScreenInsets.on(this)

        tvRangeLabel = findViewById(R.id.tvLrRangeLabel)
        tvTotalGave = findViewById(R.id.tvLrTotalGave)
        tvTotalGot = findViewById(R.id.tvLrTotalGot)
        tvNetChange = findViewById(R.id.tvLrNetChange)
        tvEmpty = findViewById(R.id.tvLrEmpty)

        adapter = Adapter()
        val rv: RecyclerView = findViewById(R.id.rvLedgerReport)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ChipGroup>(R.id.chipLedgerRange).setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                R.id.chipLrAll -> setRange(DateRangeFilter.Range.ALL)
                R.id.chipLrToday -> setRange(DateRangeFilter.today())
                R.id.chipLrYesterday -> setRange(DateRangeFilter.yesterday())
                R.id.chipLrWeek -> setRange(DateRangeFilter.thisWeek())
                R.id.chipLrMonth -> setRange(DateRangeFilter.thisMonth())
                R.id.chipLrCustom -> pickCustomRange()
            }
        }

        findViewById<MaterialButton>(R.id.btnLrDownload).setOnClickListener {
            buildPdf { file -> PdfShare.present(this, file) }
        }
        findViewById<MaterialButton>(R.id.btnLrShare).setOnClickListener {
            buildPdf { file -> PdfShare.shareDirect(this, file) }
        }

        setRange(DateRangeFilter.Range.ALL)
    }

    /** Re-read on every return: an entry added meanwhile belongs on the page. */
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun setRange(picked: DateRangeFilter.Range) {
        range = picked
        tvRangeLabel.text = DateRangeFilter.label(this, picked)
        load()
    }

    /**
     * The same two-picker gesture as the per-party report: start seeded at
     * today, end seeded after the start, end-of-day on the closing date so a
     * same-day pair still holds that day's entries.
     */
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
                        val end = Calendar.getInstance().apply {
                            set(y2, m2, d2, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }.timeInMillis
                        if (end < start) {
                            Toast.makeText(this, R.string.invalid_range, Toast.LENGTH_LONG).show()
                            return@DatePickerDialog
                        }
                        setRange(DateRangeFilter.Range(start, end, R.string.range_custom))
                    },
                    y1, m1, d1
                ).apply { setTitle(getString(R.string.pick_end_date)) }.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply { setTitle(getString(R.string.pick_start_date)) }.show()
    }

    private fun load() {
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                dao.entriesInRange(range.from, range.to)
            }
            // Newest first on screen — the owner opens this to see what just
            // happened. The PDF stays oldest-first, as a printed statement
            // reads.
            entries = rows.reversed()
            render()
        }
    }

    private fun render() {
        adapter.submit(entries)
        tvEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        val gave = entries.filter { it.isGiven }.sumOf { it.amount }
        val got = entries.filter { !it.isGiven }.sumOf { it.amount }
        tvTotalGave.text = Format.money(gave)
        tvTotalGot.text = Format.money(got)
        tvNetChange.text = Format.signedTotal(got - gave)
    }

    /**
     * One builder behind both buttons. The PDF is built fresh on each tap so
     * it always matches the rows on screen, never a range picked earlier.
     */
    private fun buildPdf(then: (java.io.File) -> Unit) {
        Toast.makeText(this, R.string.ledger_report_generating, Toast.LENGTH_SHORT).show()
        val label = DateRangeFilter.label(this, range)
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                LedgerReport.build(this@LedgerReportActivity, dao, range.from, range.to, label)
            }
            if (file == null) {
                Toast.makeText(this@LedgerReportActivity, R.string.ledger_report_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            then(file)
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        private val dateFmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)
        private var items: List<EntryWithParty> = emptyList()

        fun submit(list: List<EntryWithParty>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvLrEntryName)
            val date: TextView = v.findViewById(R.id.tvLrEntryDate)
            val gave: TextView = v.findViewById(R.id.tvLrEntryGave)
            val got: TextView = v.findViewById(R.id.tvLrEntryGot)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_ledger_report_entry, parent, false)
            )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.name.text = e.partyName
            holder.date.text = dateFmt.format(Date(e.timestamp))
            // The amount lands in exactly one of the two columns; the other
            // is emptied, not hidden, so the columns keep their width and
            // every row's money stays on the same vertical line.
            if (e.isGiven) {
                holder.gave.text = Format.money(e.amount)
                holder.got.text = ""
            } else {
                holder.gave.text = ""
                holder.got.text = Format.money(e.amount)
            }
        }
    }
}
