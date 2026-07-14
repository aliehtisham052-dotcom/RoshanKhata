package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.ExpiringBatch

class ExpiringAdapter : RecyclerView.Adapter<ExpiringAdapter.VH>() {

    private var items: List<ExpiringBatch> = emptyList()

    fun submit(newItems: List<ExpiringBatch>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvProduct: TextView = view.findViewById(R.id.tvExpProduct)
        val tvQty: TextView = view.findViewById(R.id.tvExpQty)
        val tvDays: TextView = view.findViewById(R.id.tvExpDays)
        val tvBatch: TextView = view.findViewById(R.id.tvExpBatch)
        val tvSupplier: TextView = view.findViewById(R.id.tvExpSupplier)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_expiring, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val ctx = holder.itemView.context

        holder.tvProduct.text = e.productName
        holder.tvQty.text = Format.goods(e.quantity, e.unit)

        // Expired stock is already a loss. Stock merely approaching expiry can
        // still be saved. Those are different situations and they must not look
        // the same on screen.
        val days = e.daysLeft
        when {
            e.hasExpired -> {
                holder.tvDays.text = ctx.getString(R.string.expired_days, -days)
                holder.tvDays.setTextColor(ContextCompat.getColor(ctx, R.color.red_gave))
            }
            days == 0 -> {
                holder.tvDays.setText(R.string.expires_today)
                holder.tvDays.setTextColor(ContextCompat.getColor(ctx, R.color.red_gave))
            }
            else -> {
                holder.tvDays.text = ctx.getString(R.string.expires_days, days)
                holder.tvDays.setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        if (days <= 14) R.color.red_gave else R.color.gold_accent
                    )
                )
            }
        }

        holder.tvBatch.text = if (e.batchNumber.isNullOrBlank()) {
            ctx.getString(R.string.batch_none)
        } else {
            ctx.getString(R.string.batch_label, e.batchNumber)
        }

        // Who to return it to is half the point of the warning.
        holder.tvSupplier.text = if (e.billNumber.isNullOrBlank()) {
            ctx.getString(R.string.from_supplier, e.partyName)
        } else {
            ctx.getString(R.string.from_supplier_bill, e.partyName, e.billNumber)
        }
    }
}
