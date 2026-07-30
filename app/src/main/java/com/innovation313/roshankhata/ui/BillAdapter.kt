package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.BillSummary

class BillAdapter(
    private val onClick: (BillSummary) -> Unit
) : RecyclerView.Adapter<BillAdapter.VH>() {

    private var items: List<BillSummary> = emptyList()

    fun submit(newItems: List<BillSummary>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSupplier: TextView = view.findViewById(R.id.tvBillSupplier)
        val tvAmount: TextView = view.findViewById(R.id.tvBillAmount)
        val tvNumber: TextView = view.findViewById(R.id.tvBillNumber)
        val tvMeta: TextView = view.findViewById(R.id.tvBillMeta)
        val tvStatus: TextView = view.findViewById(R.id.tvBillStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_bill, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = items[position]
        val ctx = holder.itemView.context

        holder.tvSupplier.text = b.partyName
        holder.tvAmount.text = Format.money(b.totalAmount)

        holder.tvNumber.text = b.billNumber.orEmpty()
        holder.tvNumber.visibility = if (b.billNumber.isNullOrBlank()) View.GONE else View.VISIBLE

        holder.tvMeta.text = buildString {
            append(Format.dateOnly(b.billDate))
            if (b.itemCount > 0) {
                append(" · ")
                append(ctx.getString(R.string.items_count, b.itemCount))
            }
        }

        // Status, truest first. A cash bill was settled on delivery. Otherwise
        // the LEDGER decides: if the supplier's account is clear (balance >= 0),
        // the account is settled — a payment entered in the khata shows here
        // with nothing more to do. Only when money is still owed do we fall back
        // to the due-date reminder.
        if (b.isPaidInFull) {
            holder.tvStatus.setText(R.string.plan_closed)
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.green_got))
            holder.tvStatus.visibility = View.VISIBLE
        } else if (b.supplierBalance >= 0) {
            // Owe them nothing right now — the account is clear.
            holder.tvStatus.setText(R.string.bill_account_settled)
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.green_got))
            holder.tvStatus.visibility = View.VISIBLE
        } else {
            // Still owed. Show how much is outstanding to this supplier, and the
            // due date if the bill carried one.
            holder.tvStatus.visibility = View.VISIBLE
            val owed = Format.money(-b.supplierBalance)
            val due = b.dueDate
            if (due != null && due < System.currentTimeMillis()) {
                holder.tvStatus.text = ctx.getString(R.string.bill_owe_overdue, owed)
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.red_gave))
            } else {
                holder.tvStatus.text = ctx.getString(R.string.bill_owe_amount, owed)
                holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.ink))
            }
        }

        holder.itemView.setOnClickListener { onClick(b) }
    }
}
