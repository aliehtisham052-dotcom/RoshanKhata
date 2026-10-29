package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.quantity

import com.innovation313.roshankhata.data.Digits

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.DocStatus
import com.innovation313.roshankhata.data.InvoiceSummary
import com.innovation313.roshankhata.data.WordIds

/**
 * One document per row on the Documents screen (29 Oct 2026, schema 40; the
 * invoice list before): the customer, the total, the number — whose prefix
 * says the kind (Q-, SO-, DC-, INV-) — with a status tag beside it for
 * Accepted, Converted and Cancelled (nothing while Open), and the date with
 * the line count. A cancelled document is muted and its total struck through:
 * it is read as a record, not a bill.
 *
 * [onOpen] runs when the row itself is tapped — open the document.
 * [onMore] runs from the three-dot button — the list of actions.
 */
class InvoiceAdapter(
    private val onOpen: (InvoiceSummary) -> Unit,
    private val onMore: (InvoiceSummary) -> Unit
) : RecyclerView.Adapter<InvoiceAdapter.VH>() {

    private var items: List<InvoiceSummary> = emptyList()

    fun submit(newItems: List<InvoiceSummary>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvCustomer: TextView = view.findViewById(R.id.tvInvoiceCustomer)
        val tvTotal: TextView = view.findViewById(R.id.tvInvoiceTotal)
        val tvNumber: TextView = view.findViewById(R.id.tvInvoiceNumber)
        val tvStatus: TextView = view.findViewById(R.id.tvInvoiceStatus)
        val tvMeta: TextView = view.findViewById(R.id.tvInvoiceMeta)
        val btnMore: ImageButton = view.findViewById(R.id.btnInvoiceMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_invoice, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val inv = items[position]
        val ctx = holder.itemView.context

        holder.tvCustomer.text = inv.customerName
        holder.tvTotal.text = Format.money(inv.grandTotal)
        holder.tvNumber.text = inv.invoiceNumber

        // The status tag: its words from the same keys the computer uses (DocStatus.wordKey), its colours
        // the khata's — green for accepted, grey for converted (it lives on as the next step), red for cancelled.
        val cancelled = inv.status == DocStatus.CANCELLED
        val tag = when (inv.status) {
            DocStatus.ACCEPTED -> R.color.brand_green_text to R.color.brand_green_soft
            DocStatus.CONVERTED -> R.color.ink_soft to R.color.page_line
            DocStatus.CANCELLED -> R.color.bal_owed_to_me to R.color.summary_get_bg
            else -> null
        }
        if (tag == null) {
            holder.tvStatus.visibility = View.GONE
        } else {
            holder.tvStatus.visibility = View.VISIBLE
            holder.tvStatus.text = ctx.getString(WordIds.string(DocStatus.wordKey(inv.status)))
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, tag.first))
            holder.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, tag.second))
        }
        holder.itemView.alpha = if (cancelled) 0.55f else 1f
        holder.tvTotal.paintFlags =
            if (cancelled) holder.tvTotal.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else holder.tvTotal.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

        holder.tvMeta.text = buildString {
            append(Format.dateOnly(inv.invoiceDate))
            append(" · ")
            append(Digits.quantity(ctx.resources, R.plurals.items_count, inv.itemCount, inv.itemCount))
        }

        holder.itemView.setOnClickListener { onOpen(inv) }
        holder.btnMore.setOnClickListener { onMore(inv) }
    }
}
