package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.quantity

import com.innovation313.roshankhata.data.Digits

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Money

/** One row in the Recycle Bin: a whole party, a single entry, or (8 Nov 2026) a cashbook line, cheque, supplier bill, document or scheme. */
sealed class BinItem {
    abstract val id: Long
    abstract val deletedAt: Long

    data class DeletedParty(
        override val id: Long,
        val name: String,
        val phone: String?,
        /** What the ledger came to, entries and all. Shown before restore or destroy. */
        val balance: Double,
        val entryCount: Int,
        override val deletedAt: Long
    ) : BinItem()

    data class DeletedEntry(
        override val id: Long,
        val partyName: String,
        val amount: Double,
        val isGiven: Boolean,
        val entryNumber: String,
        override val deletedAt: Long
    ) : BinItem()

    /** A cashbook line the owner deleted. */
    data class DeletedCash(
        override val id: Long,
        val isIncome: Boolean,
        val amount: Double,
        val category: String,
        val note: String?,
        override val deletedAt: Long
    ) : BinItem()

    data class DeletedCheque(
        override val id: Long,
        val partyName: String,
        val amount: Double,
        val isReceived: Boolean,
        val chequeNumber: String?,
        override val deletedAt: Long
    ) : BinItem()

    data class DeletedBill(
        override val id: Long,
        val partyName: String,
        val billNumber: String?,
        val amount: Double,
        override val deletedAt: Long
    ) : BinItem()

    /** A quotation, order, challan or invoice; [kind] is a DocKind, [amount] its grand total. */
    data class DeletedDoc(
        override val id: Long,
        val kind: String,
        val number: String,
        val customerName: String,
        val amount: Double,
        override val deletedAt: Long
    ) : BinItem()

    /** A company scheme; it keeps no deletion day, so [deletedAt] is 0 and the row shows no date. */
    data class DeletedScheme(
        override val id: Long,
        val name: String,
        val company: String?,
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
        val tvKind: TextView = view.findViewById(R.id.tvBinKind)
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

        holder.tvKind.visibility = View.GONE
        fun kind(tag: String) { holder.tvKind.text = tag; holder.tvKind.visibility = View.VISIBLE }
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
                        Digits.quantity(ctx.resources, 
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
            is BinItem.DeletedCash -> {
                kind(ctx.getString(R.string.cashbook))
                val dir = ctx.getString(if (item.isIncome) R.string.cash_income else R.string.cash_expense)
                holder.tvTitle.text = "$dir — ${Format.moneyLtr(item.amount)}"
                holder.tvSubtitle.text = listOfNotNull(item.category.takeIf { it.isNotBlank() }, item.note?.takeIf { it.isNotBlank() })
                    .joinToString(" · ").ifBlank { dir }
            }
            is BinItem.DeletedCheque -> {
                kind(ctx.getString(R.string.payment_method_cheque))
                val dir = ctx.getString(if (item.isReceived) R.string.i_got else R.string.i_gave)
                holder.tvTitle.text = "$dir — ${Format.moneyLtr(item.amount)}"
                holder.tvSubtitle.text = listOfNotNull(Format.isolate(item.partyName), item.chequeNumber?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) })
                    .joinToString(" · ")
            }
            is BinItem.DeletedBill -> {
                kind(ctx.getString(R.string.cf_kind_bill))
                holder.tvTitle.text = Format.moneyLtr(item.amount)
                holder.tvSubtitle.text = listOfNotNull(Format.isolate(item.partyName), item.billNumber?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) })
                    .joinToString(" · ")
            }
            is BinItem.DeletedDoc -> {
                kind(ctx.getString(docKindLabel(item.kind)))
                holder.tvTitle.text = "${Format.ltr(item.number)} — ${Format.moneyLtr(item.amount)}"
                holder.tvSubtitle.text = Format.isolate(item.customerName)
            }
            is BinItem.DeletedScheme -> {
                kind(ctx.getString(R.string.bin_kind_scheme))
                holder.tvTitle.text = item.name
                holder.tvSubtitle.text = item.company?.takeIf { it.isNotBlank() }?.let { Format.isolate(it) }.orEmpty()
            }
        }
        holder.tvSubtitle.visibility = if (holder.tvSubtitle.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        // A scheme keeps no deletion day: no line rather than a made-up date.
        holder.tvDeletedAt.visibility = if (item.deletedAt > 0L) View.VISIBLE else View.GONE
        holder.tvDeletedAt.text = ctx.getString(
            R.string.deleted_on,
            Format.dateTime(item.deletedAt)
        )

        holder.btnRestore.setOnClickListener { onRestore(item) }
        holder.btnDeleteForever.setOnClickListener { onDeleteForever(item) }
    }
}

/** The app's word for a document kind (DocKind), as a string resource. */
fun docKindLabel(kind: String): Int = when (kind) {
    com.innovation313.roshankhata.data.DocKind.QUOTATION -> R.string.doc_kind_quotation
    com.innovation313.roshankhata.data.DocKind.PROFORMA -> R.string.doc_kind_proforma
    com.innovation313.roshankhata.data.DocKind.SALES_ORDER -> R.string.doc_kind_sales_order
    com.innovation313.roshankhata.data.DocKind.CHALLAN -> R.string.doc_kind_challan
    com.innovation313.roshankhata.data.DocKind.CREDIT_NOTE -> R.string.doc_kind_credit_note
    com.innovation313.roshankhata.data.DocKind.DEBIT_NOTE -> R.string.doc_kind_debit_note
    else -> R.string.doc_kind_invoice
}
