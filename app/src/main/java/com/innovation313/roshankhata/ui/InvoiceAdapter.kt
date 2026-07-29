package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.InvoiceSummary

class InvoiceAdapter(
    private val onClick: (InvoiceSummary) -> Unit
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
            append(ctx.getString(R.string.items_count, inv.itemCount))
        }

        holder.itemView.setOnClickListener { onClick(inv) }
    }
}
