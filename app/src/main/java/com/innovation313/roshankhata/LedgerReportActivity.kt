package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.data.quantity
import com.innovation313.roshankhata.data.Digits

import com.innovation313.roshankhata.ui.BrandDatePickerDialog
import com.innovation313.roshankhata.ui.DateRangeFilter
import com.innovation313.roshankhata.ui.ChoiceSheet
import com.innovation313.roshankhata.data.DateWords
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.innovation313.roshankhata.data.CsvExport
import com.innovation313.roshankhata.data.Downloads
import com.innovation313.roshankhata.data.EntryWithParty
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LedgerReport
import com.innovation313.roshankhata.data.DayMath
import com.innovation313.roshankhata.data.AndroidWords
import com.innovation313.roshankhata.data.WhenWords
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
 * The 24 Oct 2026 redesign (the owner: "local si, generic si lagti hai"):
 * one-tap period chips on the green band (Today, This week, This month, All,
 * Custom — Custom asks the first day, then the last; one bound alone is a
 * valid window), a summary card lifted over the band (the window, how many
 * entries and parties, gave against got as a share bar, the two totals), the
 * entries grouped by day under a heading with that day's count, and one
 * primary action, Share, with Preview and Download as icon buttons.
 *
 * Deliberately no all-customers "net", neither for the window nor per day:
 * one man's sale and another man's payment folded together belong to
 * nobody's account, and the old net column disagreed with its own PDF's
 * sign. It was removed from screen and PDF before and stays removed.
 *
 * Download offers the same rows as PDF or as an Excel-openable CSV. Share
 * goes straight to the sheet, because here — uniquely — the owner is already
 * looking at exactly what the document will hold.
 */
class LedgerReportActivity : BaseActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private enum class Period { TODAY, WEEK, MONTH, ALL, CUSTOM }

    private lateinit var adapter: Adapter
    private lateinit var chips: Map<Period, TextView>
    private lateinit var tvRange: TextView
    private lateinit var tvCounts: TextView
    private lateinit var shareBar: View
    private lateinit var shareGave: View
    private lateinit var shareGot: View
    private lateinit var btnPreview: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var tvTotalGave: TextView
    private lateinit var tvTotalGot: TextView
    private lateinit var tvEmpty: TextView

    /** Opens on this month (P5, 7 Oct: never the whole book unasked — a wholesaler's is 300,000 rows). */
    private var period = Period.MONTH

    /** Null means unbounded on that side; both null is the whole book. */
    private var startMs: Long? = null
    private var endMs: Long? = null

    private var entries: List<EntryWithParty> = emptyList()

    private val buttonDateFmt get() = DateWords.formatter("d MMM yyyy")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ledger_report)
        ScreenInsets.on(this)

        tvRange = findViewById(R.id.tvLrRange)
        tvCounts = findViewById(R.id.tvLrCounts)
        // At 120-150% text the counts beside the window pill were a few letters wide ("4 en / tries / · 2 / cust /
        // ome / rs", the 31 Oct screen proofs): the pill goes under the counts instead.
        if (resources.configuration.fontScale >= com.innovation313.roshankhata.ui.TextFit.STACK_SCALE) {
            findViewById<android.widget.LinearLayout>(R.id.lrSummaryHead).orientation = android.widget.LinearLayout.VERTICAL
            tvCounts.layoutParams = (tvCounts.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT; weight = 0f
            }
            tvRange.layoutParams = (tvRange.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                marginStart = 0; topMargin = (8 * resources.displayMetrics.density).toInt()
            }
        }
        shareBar = findViewById(R.id.lrShareBar)
        shareGave = findViewById(R.id.lrShareGave)
        shareGot = findViewById(R.id.lrShareGot)
        // The track's rounded ends clip the two coloured parts inside it.
        shareBar.clipToOutline = true
        tvTotalGave = findViewById(R.id.tvLrTotalGave)
        tvTotalGot = findViewById(R.id.tvLrTotalGot)
        tvEmpty = findViewById(R.id.tvLrEmpty)

        adapter = Adapter()
        val rv: RecyclerView = findViewById(R.id.rvLedgerReport)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        chips = mapOf(
            Period.TODAY to findViewById(R.id.chipLrToday),
            Period.WEEK to findViewById(R.id.chipLrWeek),
            Period.MONTH to findViewById(R.id.chipLrMonth),
            Period.ALL to findViewById(R.id.chipLrAll),
            Period.CUSTOM to findViewById(R.id.chipLrCustom)
        )
        chips.forEach { (p, chip) ->
            chip.setOnClickListener { if (p == Period.CUSTOM) pickDate(isStart = true, thenEnd = true) else choose(p) }
        }
        tvRange.setOnClickListener { pickDate(isStart = true, thenEnd = true) }

        val restored = savedInstanceState?.getString(KEY_PERIOD)?.let { n -> Period.values().firstOrNull { it.name == n } }
        if (restored != null) {
            period = restored
            startMs = savedInstanceState.getLong(KEY_START, -1L).takeIf { it >= 0L }
            endMs = savedInstanceState.getLong(KEY_END, -1L).takeIf { it >= 0L }
        } else if (intent.hasExtra(EXTRA_FROM) && intent.hasExtra(EXTRA_TO)) {
            // Last month, from the monthly automation's notification: a window of its own (31 Oct 2026).
            period = Period.CUSTOM
            startMs = intent.getLongExtra(EXTRA_FROM, 0L)
            endMs = intent.getLongExtra(EXTRA_TO, 0L)
        } else {
            setWindow(Period.MONTH)
        }

        btnPreview = findViewById(R.id.btnLrPreview)
        btnDownload = findViewById(R.id.btnLrDownload)
        btnShare = findViewById(R.id.btnLrShare)
        // Icon-only: the name shows on a long press as well as being spoken.
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnPreview, getString(R.string.pdf_preview))
        androidx.appcompat.widget.TooltipCompat.setTooltipText(btnDownload, getString(R.string.ledger_report_download))
        // PDF only, deliberately: this screen also exports CSV, and there is
        // no such thing as previewing a spreadsheet in a PDF viewer.
        btnPreview.setOnClickListener { buildPdf { file -> PdfShare.open(this, file) } }
        btnDownload.setOnClickListener { chooseDownload() }
        btnShare.setOnClickListener {
            buildPdf { file -> PdfShare.shareDirect(this, file) }
        }

        applyRange()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PERIOD, period.name)
        outState.putLong(KEY_START, startMs ?: -1L)
        outState.putLong(KEY_END, endMs ?: -1L)
    }

    /** Re-read on every return: an entry added meanwhile belongs on the page. */
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun choose(p: Period) {
        setWindow(p)
        applyRange()
    }

    /**
     * A named window's bounds. This week and this month run open-ended (no end
     * bound), so an entry written after the screen opened still belongs on it
     * when the owner comes back.
     */
    private fun setWindow(p: Period) {
        period = p
        when (p) {
            Period.TODAY -> DateRangeFilter.today().let { startMs = it.from; endMs = it.to }
            Period.WEEK -> { startMs = DateRangeFilter.thisWeek().from; endMs = null }
            Period.MONTH -> { startMs = DateRangeFilter.thisMonth().from; endMs = null }
            Period.ALL -> { startMs = null; endMs = null }
            Period.CUSTOM -> Unit
        }
    }

    /**
     * One picker per bound, seeded at the date it already holds. The chosen
     * day is widened to its own start or end — a start bound begins at
     * midnight and an end bound runs to 23:59:59, so a same-day pair still
     * holds that whole day's entries. Custom asks the first day and then, if
     * [thenEnd], the last; leaving the second picker keeps "from that day on".
     */
    private fun pickDate(isStart: Boolean, thenEnd: Boolean = false) {
        val seed = Calendar.getInstance().apply {
            (if (isStart) startMs else endMs)?.let { timeInMillis = it }
        }
        BrandDatePickerDialog(
            this,
            { _, y, m, d ->
                if (isStart) {
                    val v = Calendar.getInstance().apply {
                        set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    startMs = v
                    // A new first day drops an end bound that now lies before it.
                    val e = endMs
                    if (period != Period.CUSTOM || (e != null && e < v)) endMs = null
                    period = Period.CUSTOM
                    applyRange()
                    if (thenEnd) pickDate(isStart = false)
                } else {
                    val v = Calendar.getInstance().apply {
                        set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                    val s = startMs
                    if (s != null && v < s) {
                        Toast.makeText(this, R.string.invalid_range, Toast.LENGTH_LONG).show()
                        return@BrandDatePickerDialog
                    }
                    endMs = v
                    period = Period.CUSTOM
                    applyRange()
                }
            },
            seed.get(Calendar.YEAR),
            seed.get(Calendar.MONTH),
            seed.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle(getString(if (isStart) R.string.pick_start_date else R.string.pick_end_date))
        }.show()
    }

    /** The chips and the window's pill wear the state; then the list follows. */
    private fun applyRange() {
        fun colour(id: Int) = androidx.core.content.ContextCompat.getColor(this, id)
        chips.forEach { (p, chip) ->
            val on = p == period
            chip.setBackgroundResource(if (on) R.drawable.bg_lr_chip_on else R.drawable.bg_lr_chip_off)
            val ink = colour(if (on) R.color.brand_green_text else R.color.white)
            chip.setTextColor(ink)
            chip.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                chip, android.content.res.ColorStateList.valueOf(ink)
            )
            chip.isSelected = on
        }
        tvRange.text = windowLabel()
        load()
    }

    /** The pill's words: the window's two days, an open end read as today. */
    private fun windowLabel(): String {
        val s = startMs
        val e = endMs
        if (s == null && e == null) return getString(R.string.range_label_all)
        if (s == null) return getString(R.string.ledger_report_till, buttonDateFmt.format(Date(e!!)))
        val first = buttonDateFmt.format(Date(s))
        val last = buttonDateFmt.format(Date(e ?: System.currentTimeMillis()))
        return if (first == last) first else "$first \u2013 $last"
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
        listOf(btnPreview, btnDownload, btnShare).forEach {
            it.isEnabled = has
            it.alpha = if (has) 1f else 0.5f
        }

        val gave = entries.filter { it.isGiven }.sumOf { it.amount }
        val got = entries.filter { !it.isGiven }.sumOf { it.amount }
        tvTotalGave.text = Format.money(gave)
        tvTotalGot.text = Format.money(got)

        val parties = entries.map { it.partyId }.distinct().size
        tvCounts.text = Digits.quantity(resources, R.plurals.pdf_stmt_entries, entries.size, entries.size) +
            " \u00B7 " + Digits.quantity(resources, R.plurals.customer_count, parties, parties)

        // The bar shows each side's share of the window's money; with no
        // money in the window there is nothing to share out.
        val total = gave + got
        shareBar.visibility = if (total > 0.0) View.VISIBLE else View.INVISIBLE
        if (total > 0.0) {
            (shareGave.layoutParams as android.widget.LinearLayout.LayoutParams).weight = (gave / total).toFloat()
            (shareGot.layoutParams as android.widget.LinearLayout.LayoutParams).weight = (got / total).toFloat()
            shareGave.visibility = if (gave > 0.0) View.VISIBLE else View.GONE
            shareGot.visibility = if (got > 0.0) View.VISIBLE else View.GONE
            shareBar.requestLayout()
        }
    }

    /**
     * Download offers two shapes of the same rows — PDF or Excel CSV — and
     * then DOWNLOADS: the file lands in the phone's public Downloads
     * folder and a toast says where. It used to open a read-first dialog
     * with its own Share button instead, which the owner called out
     * exactly: a Download that saves nothing is a broken promise, and a
     * second Share inside it duplicated the real Share button below.
     * Reading stays available the honest way — the rows ARE on screen, and
     * the saved file opens from Downloads like any other.
     */
    private fun chooseDownload() {
        val options = listOf(
            ChoiceSheet.pdf("PDF"),
            ChoiceSheet.Option(getString(R.string.export_excel), icon = R.drawable.ic_list_all,
                iconTint = R.color.bal_i_owe, tile = R.color.summary_give_bg)
        )
        ChoiceSheet.show(this, getString(R.string.ledger_report_download_as), options) { which ->
            withDownloadsAccess {
                if (which == 0) {
                    buildPdf { file -> saveToDownloads(file, "application/pdf") }
                } else {
                    buildCsv { file -> saveToDownloads(file, "text/csv") }
                }
            }
        }
    }

    private fun saveToDownloads(file: File, mime: String) {
        lifecycleScope.launch {
            val where = withContext(Dispatchers.IO) {
                Downloads.save(this@LedgerReportActivity, file, mime)
            }
            Toast.makeText(
                this@LedgerReportActivity,
                if (where != null) getString(R.string.report_saved_to, where)
                else getString(R.string.ledger_report_failed),
                Toast.LENGTH_LONG
            ).show()
        }
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

    private fun buildCsv(then: (File) -> Unit) {
        Toast.makeText(this, R.string.ledger_report_generating, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                CsvExport.ledger(this@LedgerReportActivity, entries)
            }
            if (file == null) {
                Toast.makeText(this@LedgerReportActivity, R.string.ledger_report_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            then(file)
        }
    }

    /**
     * Entries newest first, grouped by day: the first entry of a day carries
     * the day's heading (its date, Today/Yesterday by name, and its count),
     * and each day's rows sit in one rounded group separated by hairlines.
     */
    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        private val timeFmt get() = DateWords.formatter("h:mm a")
        private val dayFmt get() = DateWords.formatter("d MMM yyyy")
        private var items: List<EntryWithParty> = emptyList()
        private var days: LongArray = LongArray(0)
        private var perDay: Map<Long, Int> = emptyMap()
        /** The moment the list was built: Today and Yesterday are judged against it, not against each row's bind. */
        private var builtAt = 0L
        private val words by lazy { AndroidWords(this@LedgerReportActivity) }

        fun submit(list: List<EntryWithParty>) {
            items = list
            days = LongArray(list.size) { dayOf(list[it].timestamp) }
            perDay = days.toList().groupingBy { it }.eachCount()
            builtAt = System.currentTimeMillis()
            notifyDataSetChanged()
        }

        private fun dayOf(ms: Long): Long = DayMath.startOfDay(ms)

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val dayHead: View = v.findViewById(R.id.lrDayHead)
            val day: TextView = v.findViewById(R.id.tvLrDay)
            val dayCount: TextView = v.findViewById(R.id.tvLrDayCount)
            val row: View = v.findViewById(R.id.lrRow)
            val initial: TextView = v.findViewById(R.id.tvLrInitial)
            val name: TextView = v.findViewById(R.id.tvLrEntryName)
            val date: TextView = v.findViewById(R.id.tvLrEntryDate)
            val amount: TextView = v.findViewById(R.id.tvLrEntryAmount)
            val way: TextView = v.findViewById(R.id.tvLrEntryWay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ledger_report_entry, parent, false)
            com.innovation313.roshankhata.ui.TextFit.relax(v)
            com.innovation313.roshankhata.ui.TextFit.stackWhenLarge(v.findViewById(R.id.lrColName), v.findViewById(R.id.lrColAmount))
            return VH(v).apply {
                initial.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colour(R.color.tile_khata_bg))
                }
            }
        }

        private fun colour(id: Int) = androidx.core.content.ContextCompat.getColor(this@LedgerReportActivity, id)

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            val d = days[position]
            val first = position == 0 || days[position - 1] != d
            val last = position == items.lastIndex || days[position + 1] != d

            holder.dayHead.visibility = if (first) View.VISIBLE else View.GONE
            if (first) {
                val date = dayFmt.format(Date(d))
                // Today · 12 Oct 2026 / Yesterday · … / the date alone: the day word is the shared rule (WhenWords).
                holder.day.text = WhenWords.relativeDay(d, builtAt, words)?.let { it + " \u00B7 " + date } ?: date
                val n = perDay[d] ?: 0
                holder.dayCount.text = Digits.quantity(resources, R.plurals.pdf_stmt_entries, n, n)
            }

            // One rounded group per day; a 1dp gap between rows lets the page
            // show through as the hairline.
            val r = resources.displayMetrics.density * 16f
            val top = if (first) r else 0f
            val bottom = if (last) r else 0f
            holder.row.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(colour(R.color.surface))
                cornerRadii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
            }
            (holder.row.layoutParams as ViewGroup.MarginLayoutParams).topMargin =
                if (first) 0 else resources.displayMetrics.density.toInt().coerceAtLeast(1)

            val shown = e.partyName.trim()
            holder.initial.text = if (shown.isEmpty()) "?"
                else String(Character.toChars(shown.codePointAt(0))).uppercase(Locale.getDefault())
            holder.name.text = e.partyName
            val note = e.note?.lineSequence()?.firstOrNull()?.trim().orEmpty()
            holder.date.text = timeFmt.format(Date(e.timestamp)) + " \u00B7 " +
                (if (note.isNotEmpty()) Format.isolate(note) else e.entryNumber)

            holder.amount.text = Format.money(e.amount)
            holder.amount.setTextColor(colour(if (e.isGiven) R.color.red_gave_text else R.color.green_got_text))
            holder.way.text = getString(if (e.isGiven) R.string.i_gave else R.string.i_got)
            holder.way.setTextColor(colour(if (e.isGiven) R.color.red_gave_text else R.color.green_got_text))
            holder.way.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(colour(if (e.isGiven) R.color.summary_get_bg else R.color.summary_give_bg))
                cornerRadius = resources.displayMetrics.density * 8f
            }
        }
    }

    companion object {
        private const val KEY_PERIOD = "lr_period"
        private const val KEY_START = "lr_start"
        private const val KEY_END = "lr_end"

        /** A window to open on (the monthly automation: last month), first and last millisecond. */
        const val EXTRA_FROM = "lr_from"
        const val EXTRA_TO = "lr_to"
    }
}
