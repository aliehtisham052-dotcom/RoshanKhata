package com.innovation313.roshankhata

import com.innovation313.roshankhata.data.quantity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.text.BidiFormatter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.innovation313.roshankhata.data.BookSeal
import com.innovation313.roshankhata.data.BookSeals
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.get
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.ScreenInsets
import com.innovation313.roshankhata.ui.TextFit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The book's seals (Kitab ki Muhar, 4 Nov 2026), opened from the Backup screen: what the seal proves and what it is
 * not, then each day's seal, newest first — when it was made, how many entries it holds, and a mark per witness
 * (✓ stamped, … still asked, ✕ given up after a week). Read only: nothing here writes the book.
 */
class SealsActivity : BaseActivity() {

    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seals)
        ScreenInsets.on(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        adapter = Adapter()
        findViewById<RecyclerView>(R.id.rvSeals).apply {
            layoutManager = LinearLayoutManager(this@SealsActivity)
            adapter = this@SealsActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val seals = withContext(Dispatchers.IO) {
                runCatching { KhataDatabase.get(this@SealsActivity).khataDao().sealsNewestFirst(SHOWN) }.getOrDefault(emptyList())
            }
            if (isFinishing || isDestroyed) return@launch
            adapter.submit(seals)
        }
    }

    /** One seal's witnesses as one line: "✓ FreeTSA   ✓ Sectigo   … DigiCert"; names kept whole in a right-to-left line. */
    private fun witnessLine(seal: BookSeal, now: Long): String {
        val stamped = BookSeals.stamps(seal)
        val waiting = now - seal.sealedAt <= BookSeals.RETRY_MS
        val bidi = BidiFormatter.getInstance()
        return BookSeals.WITNESSES.joinToString("   ") { w ->
            val mark = when {
                w.name in stamped -> "✓"
                waiting -> "…"
                else -> "✕"
            }
            mark + " " + bidi.unicodeWrap(w.name)
        }
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var seals: List<BookSeal> = emptyList()

        fun submit(list: List<BookSeal>) {
            seals = list
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int) = if (position == 0) HEAD else ROW

        override fun getItemCount() = seals.size + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = if (viewType == HEAD) R.layout.item_seal_help else R.layout.item_seal
            val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            TextFit.relax(v)
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView
            if (position == 0) {
                v.findViewById<TextView>(R.id.tvSealsEmpty).visibility = if (seals.isEmpty()) View.VISIBLE else View.GONE
                v.findViewById<TextView>(R.id.tvSealLegend).visibility = if (seals.isEmpty()) View.GONE else View.VISIBLE
                return
            }
            val s = seals[position - 1]
            v.findViewById<TextView>(R.id.tvSealDate).text = Digits.toLatin(Format.dateTime(s.sealedAt))
            // The first seal holds no entry: it marks where sealing starts.
            v.findViewById<TextView>(R.id.tvSealEntries).text = if (s.prevDigest == null) getString(R.string.muhar_first_seal)
                else Digits.quantity(resources, R.plurals.muhar_entries, s.leafCount, s.leafCount)
            v.findViewById<TextView>(R.id.tvSealWitnesses).text = witnessLine(s, System.currentTimeMillis())
        }
    }

    private companion object {
        const val HEAD = 0
        const val ROW = 1
        /** A year and more of daily seals; every one is in the backup whatever is shown. */
        const val SHOWN = 400
    }
}
