package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.LedgerEntry

/** Pairs an entry with the balance as it stood immediately after that entry. */
data class EntryRow(
    val entry: LedgerEntry,
    val runningBalance: Double
)

class EntryAdapter(
    private val onLongClick: (LedgerEntry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.VH>() {

    private var rows: List<EntryRow> = emptyList()

    fun submit(newRows: List<EntryRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDateTime: TextView = view.findViewById(R.id.tvDateTime)
        val tvNote: TextView = view.findViewById(R.id.tvNote)
        val tvEntryNumber: TextView = view.findViewById(R.id.tvEntryNumber)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvRunningBalance: TextView = view.findViewById(R.id.tvRunningBalance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false)
        return VH(v)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = rows[position]
        val e = row.entry
        val ctx = holder.itemView.context

        holder.tvDateTime.text = Format.dateTime(e.timestamp)

        holder.tvNote.text = e.note.orEmpty()
        holder.tvNote.visibility = if (e.note.isNullOrBlank()) View.GONE else View.VISIBLE

        holder.tvEntryNumber.text = e.entryNumber

        holder.tvAmount.text = Format.money(e.amount)
        val colour = if (e.isGiven) R.color.red_gave else R.color.green_got
        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, colour))

        holder.tvRunningBalance.text = "Bal: ${Format.money(row.runningBalance)}"

        holder.itemView.setOnLongClickListener {
            onLongClick(e)
            true
        }
    }
}
