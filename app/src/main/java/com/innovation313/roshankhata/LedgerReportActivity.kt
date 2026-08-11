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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.CsvExport
import com.innovation313.roshankhata.data.EntryWithParty
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerReport
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.PdfShare
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The whole ledger on one screen — every entry across every customer in a
 * chosen window, read BEFORE any PDF exists.
 *
 * The window is set by two date buttons and an "All" reset — the owner's own
 * correction after using the first version: the chip rail read as clutter,
 * and the competitor's start/end pair as clarity. Each button wears the date
 * it holds, so the current window is read off the controls themselves; one
 * bound alone is a valid window ("everything since March", "everything till
 * June").
 *
 * Download offers the same rows as PDF (preview-first, as every sight-unseen
 * document) or as an Excel-openable CSV for an accountant. Share goes
 * straight to the sheet, because here — uniquely — the owner is already
 * looking at exactly what the document will hold.
 */
class LedgerReportActivity : AppCompatActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private lateinit var adapter: Adapter
    private lateinit var btnStartDate: MaterialButton
    private lateinit var btnEndDate: MaterialButton
    private lateinit var btnRangeAll: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var tvTotalGave: TextView
    private lateinit var tvTotalGot: TextView
    private lateinit var tvNetChange: TextView
    private lateinit var tvEmpty: TextView

    /** Null means unbounded on that side; both null is the whole book. */
    private var startMs: Long? = null
    private var endMs: Long? = null

    private var entries: List<EntryWithParty> = emptyList()

    private val buttonDateFmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ledger_report)
        ScreenInsets.on(this)

        btnStartDate = findViewById(R.id.btnLrStartDate)
        btnEndDate = findViewById(R.id.btnLrEndDate)
        tvTotalGave = findViewById(R.id.tvLrTotalGave)
        tvTotalGot = findViewById(R.id.tvLrTotalGot)
        tvNetChange = findViewById(R.id.tvLrNetChange)
        tvEmpty = findViewById(R.id.tvLrEmpty)

        adapter = Adapter()
        val rv: RecyclerView = findViewById(R.id.rvLedgerReport)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btnStartDate.setOnClickListener { pickDate(isStart = true) }
        btnEndDate.setOnClickListener { pickDate(isStart = false) }
        btnRangeAll = findViewById(R.id.btnLrRangeAll)
        btnRangeAll.setOnClickListener {
            startMs = null
            endMs = null
            applyRange()
        }

        btnDownload = findViewById(R.id.btnLrDownload)
        btnShare = findViewById(R.id.btnLrShare)
        btnDownload.setOnClickListener { chooseDownload() }
        btnShare.setOnClickListener {
            buildPdf { file -> PdfShare.shareDirect(this, file) }
        }

        applyRange()
    }

    /** Re-read on every return: an entry added meanwhile belongs on the page. */
    override fun onResume() {
        super.onResume()
        load()
    }

    /**
     * One picker per button, seeded at the date it already holds. The chosen
     * day is widened to its own start or end — a start bound begins at
     * midnight and an end bound runs to 23:59:59, so a same-day pair still
     * holds that whole day's entries.
     */
    private fun pickDate(isStart: Boolean) {
        val seed = Calendar.getInstance().apply {
            (if (isStart) startMs else endMs)?.let { timeInMillis = it }
        }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                if (isStart) {
                    val v = Calendar.getInstance().apply {
                        set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val e = endMs
                    if (e != null && e < v) {
                        Toast.makeText(this, R.string.invalid_range, Toast.LENGTH_LONG).show()
                        return@DatePickerDialog
                    }
                    startMs = v
                } else {
                    val v = Calendar.getInstance().apply {
                        set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                    val s = startMs
                    if (s != null && v < s) {
                        Toast.makeText(this, R.string.invalid_range, Toast.LENGTH_LONG).show()
                        return@DatePickerDialog
                    }
                    endMs = v
                }
                applyRange()
            },
            seed.get(Calendar.YEAR),
            seed.get(Calendar.MONTH),
            seed.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle(getString(if (isStart) R.string.pick_start_date else R.string.pick_end_date))
        }.show()
    }

    /** The buttons wear their own state; then the list follows. */
    private fun applyRange() {
        btnStartDate.text = startMs?.let { buttonDateFmt.format(Date(it)) }
            ?: getString(R.string.report_start_date)
        btnEndDate.text = endMs?.let { buttonDateFmt.format(Date(it)) }
            ?: getString(R.string.report_end_date)

        // On the white card, state is carried by ink weight, not by gold:
        // a chosen date sits dark like content, an empty slot sits grey
        // like a hint — the same visual grammar the competitor's card uses,
        // which is what the owner asked to match.
        fun tint(view: com.google.android.material.button.MaterialButton, set: Boolean) {
            view.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    this, if (set) R.color.ink else R.color.text_muted
                )
            )
        }
        tint(btnStartDate, startMs != null)
        tint(btnEndDate, endMs != null)

        // All is the CURRENT state when neither bound is set, and an action
        // to return to otherwise — and it looks like whichever it is.
        val whole = startMs == null && endMs == null
        btnRangeAll.isEnabled = !whole
        // The owner's catch: all three rows wore the same face, so the
        // status could not be told from the buttons. The status row now
        // dresses differently — a soft green strip with a green icon —
        // while as an ACTION it goes back to a plain white row like its
        // neighbours. Same control, two clearly different clothes.
        val ctx = this
        fun colour(id: Int) = androidx.core.content.ContextCompat.getColor(ctx, id)
        btnRangeAll.backgroundTintList =
            android.content.res.ColorStateList.valueOf(colour(if (whole) R.color.brand_green_soft else R.color.white))
        btnRangeAll.iconTint =
            android.content.res.ColorStateList.valueOf(colour(if (whole) R.color.brand_green else R.color.text_muted))
        btnRangeAll.setTextColor(colour(if (whole) R.color.brand_green else R.color.ink))
        btnRangeAll.text = getString(
            if (whole) R.string.ledger_report_showing_all else R.string.ledger_report_show_all
        )

        load()
    }

    /** What the PDF/CSV header calls this window. */
    private fun rangeLabel(): String {
        val s = startMs
        val e = endMs
        return when {
            s == null && e == null -> getString(R.string.range_all)
            s != null && e != null ->
                "${buttonDateFmt.format(Date(s))} \u2013 ${buttonDateFmt.format(Date(e))}"
            s != null -> getString(R.string.ledger_report_from, buttonDateFmt.format(Date(s)))
            else -> getString(R.string.ledger_report_till, buttonDateFmt.format(Date(e!!)))
        }
    }

    private fun load() {
        val from = startMs ?: 0L
        val to = endMs ?: Long.MAX_VALUE
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) { dao.entriesInRange(from, to) }
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

        // Nothing in the window means nothing to hand anyone — a PDF or CSV
        // of an empty table is a confusing artefact, not a document. The
        // buttons grey out with the list instead of producing one.
        val has = entries.isNotEmpty()
        btnDownload.isEnabled = has
        btnShare.isEnabled = has
        btnDownload.alpha = if (has) 1f else 0.5f
        btnShare.alpha = if (has) 1f else 0.5f

        val gave = entries.filter { it.isGiven }.sumOf { it.amount }
        val got = entries.filter { !it.isGiven }.sumOf { it.amount }
        tvTotalGave.text = Format.money(gave)
        tvTotalGot.text = Format.money(got)
        tvNetChange.text = Format.signedTotal(got - gave)
    }

    /**
     * Download offers two shapes of the same rows: the PDF for reading and
     * printing, and a CSV that Excel opens directly — the accountant's copy
     * the owner asked for.
     */
    private fun chooseDownload() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ledger_report_download_as)
            .setItems(arrayOf("PDF", getString(R.string.export_excel))) { _, which ->
                if (which == 0) {
                    buildPdf { file -> PdfShare.present(this, file) }
                } else {
                    buildCsv()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * One builder behind PDF paths. Built fresh on each tap so it always
     * matches the rows on screen, never a range picked earlier.
     */
    private fun buildPdf(then: (File) -> Unit) {
        Toast.makeText(this, R.string.ledger_report_generating, Toast.LENGTH_SHORT).show()
        val from = startMs ?: 0L
        val to = endMs ?: Long.MAX_VALUE
        val label = rangeLabel()
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                LedgerReport.build(this@LedgerReportActivity, dao, from, to, label)
            }
            if (file == null) {
                Toast.makeText(this@LedgerReportActivity, R.string.ledger_report_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            then(file)
        }
    }

    private fun buildCsv() {
        Toast.makeText(this, R.string.ledger_report_generating, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                CsvExport.ledger(this@LedgerReportActivity, entries)
            }
            if (file == null) {
                Toast.makeText(this@LedgerReportActivity, R.string.ledger_report_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            CsvExport.share(this@LedgerReportActivity, file)
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
