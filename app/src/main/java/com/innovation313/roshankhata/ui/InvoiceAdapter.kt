package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.InvoiceSummary

/**
 * [onOpen] runs when the row itself is tapped — open the invoice.
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

        holder.tvMeta.text = buildString {
            append(Format.dateOnly(inv.invoiceDate))
            append(" · ")
            append(ctx.resources.getQuantityString(R.plurals.items_count, inv.itemCount, inv.itemCount))
        }

        holder.itemView.setOnClickListener { onOpen(inv) }
        holder.btnMore.setOnClickListener { onMore(inv) }
    }
}
