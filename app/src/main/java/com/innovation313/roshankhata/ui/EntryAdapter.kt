package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.LedgerEntry

/** Pairs an entry with the balance as it stood immediately after that entry. */
data class EntryRow(
    val entry: LedgerEntry,
    val runningBalance: Double
)

class EntryAdapter(
    private val onClick: (LedgerEntry) -> Unit,
    private val onLongClick: (LedgerEntry) -> Unit,
    /** Fires instead of [onClick] once selection mode is on — a tap toggles rather than opens. */
    private val onToggleSelect: (LedgerEntry) -> Unit = {}
) : ListAdapter<EntryRow, EntryAdapter.VH>(DIFF) {

    companion object {
        /**
         * One party's history is the longest list in the app — a customer of
         * several years runs to hundreds of entries — and it was rebuilt whole
         * on every change: add one entry and all of them were re-bound, which
         * on an older phone shows as a stutter and can throw away where the
         * owner had scrolled to.
         *
         * areContentsTheSame compares the WHOLE row, running balance included,
         * on purpose: inserting an entry in the middle changes the balance
         * printed beside every entry after it, and those rows must repaint
         * even though their own amounts did not change.
         */
        private val DIFF = object : DiffUtil.ItemCallback<EntryRow>() {
            override fun areItemsTheSame(a: EntryRow, b: EntryRow) = a.entry.id == b.entry.id
            override fun areContentsTheSame(a: EntryRow, b: EntryRow) = a == b
        }
    }

    /**
     * Select-and-delete for this one party's history — the bounded
     * replacement for the "Delete all customers" button removed from the
     * Recycle Bin. [selectionMode] swaps every row's tap from "open it" to
     * "toggle it", and shows the checkbox; [selectedIds] decides which
     * checkboxes are ticked. Both are read fresh on every bind rather than
     * tracked per-holder, since selecting one entry has to repaint only
     * that row without a full rebind of the whole list.
     */
    var selectionMode: Boolean = false
        private set
    private var selectedIds: Set<Long> = emptySet()

    fun submit(newRows: List<EntryRow>) {
        submitList(newRows)
    }

    /**
     * Selection is NOT part of [EntryRow], so DiffUtil cannot see it and the
     * rows that changed have to be named here.
     *
     * Turning the mode on or off shows or hides the checkbox on every row, so
     * that repaints all of them. Ticking one entry, which is what happens over
     * and over, repaints only the rows whose tick actually changed — the thing
     * the comment above has claimed since this was written, and now true.
     */
    fun setSelectionState(active: Boolean, selected: Set<Long>) {
        val modeChanged = selectionMode != active
        val changedIds = (selectedIds - selected) + (selected - selectedIds)
        selectionMode = active
        selectedIds = selected

        if (modeChanged) {
            notifyItemRangeChanged(0, itemCount)
            return
        }
        for (id in changedIds) {
            val at = currentList.indexOfFirst { it.entry.id == id }
            if (at != -1) notifyItemChanged(at)
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDateTime: TextView = view.findViewById(R.id.tvDateTime)
        val tvNote: TextView = view.findViewById(R.id.tvNote)
        val tvGoods: TextView = view.findViewById(R.id.tvGoods)
        val tvEntryNumber: TextView = view.findViewById(R.id.tvEntryNumber)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
        val tvRunningBalance: TextView = view.findViewById(R.id.tvRunningBalance)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val e = row.entry
        val ctx = holder.itemView.context

        holder.tvDateTime.text = Format.dateTime(e.timestamp)

        holder.tvNote.text = e.note.orEmpty()
        holder.tvNote.visibility = if (e.note.isNullOrBlank()) View.GONE else View.VISIBLE

        // Show the goods only when something actually moved.
        val goods = Format.goods(e.itemName, e.quantity, e.unit)
        holder.tvGoods.text = goods.orEmpty()
        holder.tvGoods.visibility = if (goods == null) View.GONE else View.VISIBLE

        holder.tvEntryNumber.text = e.entryNumber

        // Coloured to match the balance they build: goods given out (a
        // receivable) in red, payments received in green — the same convention
        // as the party's total, so a column of red entries clearly sums to a
        // red "to collect" balance.
        holder.tvAmount.text = Format.money(e.amount)
        val colour = if (e.isGiven) R.color.bal_owed_to_me else R.color.bal_i_owe
        holder.tvAmount.setTextColor(ContextCompat.getColor(ctx, colour))

        holder.tvRunningBalance.text =
            ctx.getString(R.string.running_balance_short, Format.money(row.runningBalance))

        holder.cbSelect.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.cbSelect.isChecked = e.id in selectedIds

        holder.itemView.setOnClickListener {
            if (selectionMode) onToggleSelect(e) else onClick(e)
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) onLongClick(e)
            true
        }
    }
}
