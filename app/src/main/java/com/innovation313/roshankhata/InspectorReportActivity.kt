package com.innovation313.roshankhata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.data.Downloads
import com.innovation313.roshankhata.data.InspectorBatch
import com.innovation313.roshankhata.data.InspectorReport
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.PdfShare
import com.innovation313.roshankhata.ui.ScreenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inspector mode: what is on the shelf, batch by batch, before anyone asks.
 *
 * A SCREEN first and a PDF second, the same order the ledger report settled
 * into: the owner reads his own stock here, and the document is the takeaway of
 * a page he has already seen. A sight-unseen register handed to an inspector is
 * how a wrong figure gets signed.
 *
 * The one control is the expiry window, not a date range. Stock is a fact about
 * today — "what was on the shelf last March" is not something the app can
 * reconstruct and a date picker here would imply that it can. What genuinely
 * varies is how far ahead the owner wants to be warned, which is what the
 * 30/60/90 buttons choose.
 */
class InspectorReportActivity : AppCompatActivity() {

    private val dao by lazy { KhataDatabase.get(this).khataDao() }

    private lateinit var adapter: Adapter
    private lateinit var btn30: MaterialButton
    private lateinit var btn60: MaterialButton
    private lateinit var btn90: MaterialButton
    private lateinit var btnPreview: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var tvBatches: TextView
    private lateinit var tvExpiring: TextView
    private lateinit var tvExpired: TextView
    private lateinit var tvGaps: TextView
    private lateinit var tvEmpty: TextView

    /** How far ahead to warn. The middle option is the default. */
    private var windowDays = 60

    private var data: InspectorReport.ReportData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inspector_report)
        ScreenInsets.on(this)

        tvBatches = findViewById(R.id.tvInsBatches)
        tvExpiring = findViewById(R.id.tvInsExpiring)
        tvExpired = findViewById(R.id.tvInsExpired)
        tvGaps = findViewById(R.id.tvInsGaps)
        tvEmpty = findViewById(R.id.tvInsEmpty)

        adapter = Adapter()
        val rv: RecyclerView = findViewById(R.id.rvInspector)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        btn30 = findViewById(R.id.btnInsWindow30)
        btn60 = findViewById(R.id.btnInsWindow60)
        btn90 = findViewById(R.id.btnInsWindow90)
        btn30.setOnClickListener { setWindow(30) }
        btn60.setOnClickListener { setWindow(60) }
        btn90.setOnClickListener { setWindow(90) }

        btnPreview = findViewById(R.id.btnInsPreview)
        btnDownload = findViewById(R.id.btnInsDownload)
        btnShare = findViewById(R.id.btnInsShare)
        // Read it before it leaves your hands: the register is the one
        // document here that gets handed to an inspector.
        btnPreview.setOnClickListener { buildPdf { file -> PdfShare.open(this, file) } }
        btnDownload.setOnClickListener { buildPdf { file -> saveToDownloads(file) } }
        btnShare.setOnClickListener { buildPdf { file -> PdfShare.shareDirect(this, file) } }

        paintWindow()
    }

    /**
     * Re-read on every return. Editing a product's label details is one tap
     * away from here, and coming back to a register that had not noticed is how
     * an owner stops trusting the screen.
     */
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun setWindow(days: Int) {
        windowDays = days
        paintWindow()
        load()
    }

    /** The buttons wear their own state, the way the ledger report's do. */
    private fun paintWindow() {
        fun dress(view: MaterialButton, on: Boolean) {
            val colour = ContextCompat.getColor(this, if (on) R.color.brand_green else R.color.white)
            view.backgroundTintList = android.content.res.ColorStateList.valueOf(colour)
            view.setTextColor(
                ContextCompat.getColor(this, if (on) R.color.white else R.color.ink)
            )
        }
        dress(btn30, windowDays == 30)
        dress(btn60, windowDays == 60)
        dress(btn90, windowDays == 90)
    }

    private fun load() {
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) {
                InspectorReport.gather(this@InspectorReportActivity, dao, windowDays)
            }
            data = d
            render(d)
        }
    }

    private fun render(d: InspectorReport.ReportData) {
        // Expired first, then expiring, then the rest — the same order the PDF
        // prints and the same order the shop should act in.
        val expiringIds = d.expiringSoon.map { it.itemId }.toSet()
        val expiredIds = d.expired.map { it.itemId }.toSet()
        val ordered = d.expired +
            d.expiringSoon +
            d.batches.filter { it.itemId !in expiredIds && it.itemId !in expiringIds }

        adapter.submit(ordered, windowDays)

        tvBatches.text = d.batches.size.toString()
        tvExpiring.text = d.expiringSoon.size.toString()
        tvExpired.text = d.expired.size.toString()

        // The gap line is the actionable one: these are the products whose
        // label details an inspector will ask for and the app cannot print.
        val gaps = d.incompleteProducts.size
        val noExpiry = d.noExpiryCount
        val notes = buildList {
            if (gaps > 0) add(getString(R.string.inspector_gap_labels, gaps))
            if (noExpiry > 0) add(getString(R.string.inspector_gap_expiry, noExpiry))
        }
        tvGaps.text = notes.joinToString(" \u00B7 ")
        tvGaps.visibility = if (notes.isEmpty()) View.GONE else View.VISIBLE

        val has = d.batches.isNotEmpty() || d.stock.any { !it.isUntouched }
        tvEmpty.visibility = if (has) View.GONE else View.VISIBLE
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

    /**
     * Built fresh on each tap, from the same window the screen is showing —
     * never a window chosen earlier and never a cached file.
     */
    private fun buildPdf(then: (File) -> Unit) {
        Toast.makeText(this, R.string.inspector_generating, Toast.LENGTH_SHORT).show()
        val days = windowDays
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                InspectorReport.build(this@InspectorReportActivity, dao, days)
            }
            if (file == null) {
                Toast.makeText(this@InspectorReportActivity, R.string.inspector_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            then(file)
        }
    }

    private fun saveToDownloads(file: File) {
        lifecycleScope.launch {
            val where = withContext(Dispatchers.IO) {
                Downloads.save(this@InspectorReportActivity, file, "application/pdf")
            }
            Toast.makeText(
                this@InspectorReportActivity,
                if (where != null) getString(R.string.report_saved_to, where)
                else getString(R.string.inspector_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        private val dayFmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
        private var items: List<InspectorBatch> = emptyList()
        private var window = 60

        fun submit(list: List<InspectorBatch>, windowDays: Int) {
            items = list
            window = windowDays
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvInsRowName)
            val sub: TextView = v.findViewById(R.id.tvInsRowSub)
            val expiry: TextView = v.findViewById(R.id.tvInsRowExpiry)
            val left: TextView = v.findViewById(R.id.tvInsRowLeft)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_inspector_batch, parent, false)
            )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val b = items[position]
            holder.name.text = b.productName
            holder.left.text = Format.qty(b.remaining, b.unit)

            // Three states, and the middle one has to be unmistakable: expired
            // stock on a shelf is a fine, not a reminder.
            val days = b.daysLeft
            val exp = b.expiryDate
            when {
                exp == null -> {
                    holder.expiry.text = getString(R.string.inspector_no_expiry)
                    holder.expiry.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_muted))
                }
                b.hasExpired -> {
                    holder.expiry.text = getString(R.string.inspector_expired)
                    holder.expiry.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.red_gave))
                }
                else -> {
                    holder.expiry.text = dayFmt.format(Date(exp))
                    val soon = (days ?: Int.MAX_VALUE) <= window
                    holder.expiry.setTextColor(
                        ContextCompat.getColor(
                            holder.itemView.context,
                            if (soon) R.color.red_gave else R.color.ink
                        )
                    )
                }
            }

            holder.sub.text = buildString {
                append(getString(R.string.inspector_batch_prefix))
                append(" ")
                append(b.batchNumber?.takeIf { it.isNotBlank() } ?: "\u2014")
                b.company?.takeIf { it.isNotBlank() }?.let { append(" \u00B7 $it") }
                append(" \u00B7 ")
                append(b.partyName)
            }
        }
    }
}
