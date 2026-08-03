package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Money

/** One row in the Recycle Bin — either a whole party or a single entry. */
sealed class BinItem {
    abstract val deletedAt: Long

    data class DeletedParty(
        val id: Long,
        val name: String,
        val phone: String?,
        /** What the ledger came to, entries and all. Shown before restore or destroy. */
        val balance: Double,
        val entryCount: Int,
        override val deletedAt: Long
    ) : BinItem()

    data class DeletedEntry(
        val id: Long,
        val partyName: String,
        val amount: Double,
        val isGiven: Boolean,
        val entryNumber: String,
        override val deletedAt: Long
    ) : BinItem()
}

class BinAdapter(
    private val onRestore: (BinItem) -> Unit,
    private val onDeleteForever: (BinItem) -> Unit
) : RecyclerView.Adapter<BinAdapter.VH>() {

    private var items: List<BinItem> = emptyList()

    fun submit(newItems: List<BinItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvBinTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvBinSubtitle)
        val tvDeletedAt: TextView = view.findViewById(R.id.tvBinDeletedAt)
        val btnRestore: MaterialButton = view.findViewById(R.id.btnRestore)
        val btnDeleteForever: MaterialButton = view.findViewById(R.id.btnDeleteForever)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bin, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        when (item) {
            is BinItem.DeletedParty -> {
                // The name told the owner who, and nothing told them what.
                // Restoring is harmless either way, but Delete here is
                // permanent, and no one should destroy a book without being
                // shown what was in it.
                holder.tvTitle.text = item.name
                holder.tvSubtitle.text = buildString {
                    append(ctx.getString(R.string.bin_party_note))
                    append(" · ")
                    append(
                        ctx.resources.getQuantityString(
                            R.plurals.bin_party_entries, item.entryCount, item.entryCount
                        )
                    )
                    if (Money.isNotZero(item.balance)) {
                        append(" · ")
                        append(
                            ctx.getString(
                                if (Money.isPositive(item.balance)) R.string.bin_party_owed_to_me
                                else R.string.bin_party_i_owe,
                                Format.money(item.balance)
                            )
                        )
                    }
                }
            }
            is BinItem.DeletedEntry -> {
                val dir = ctx.getString(if (item.isGiven) R.string.i_gave else R.string.i_got)
                holder.tvTitle.text = "$dir — ${Format.money(item.amount)}"
                holder.tvSubtitle.text = "${item.partyName} · ${item.entryNumber}"
            }
        }

        holder.tvDeletedAt.text = ctx.getString(
            R.string.deleted_on,
            Format.dateTime(item.deletedAt)
        )

        holder.btnRestore.setOnClickListener { onRestore(item) }
        holder.btnDeleteForever.setOnClickListener { onDeleteForever(item) }
    }
}
