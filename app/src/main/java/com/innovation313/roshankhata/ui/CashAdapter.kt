package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.CashEntry

class CashAdapter(
    private val onLongClick: (CashEntry) -> Unit
) : RecyclerView.Adapter<CashAdapter.VH>() {

    private var items: List<CashEntry> = emptyList()

    fun submit(newItems: List<CashEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvCategory: TextView = view.findViewById(R.id.tvCashCategory)
        val tvNote: TextView = view.findViewById(R.id.tvCashNote)
        val tvDate: TextView = view.findViewById(R.id.tvCashDate)
        val tvAmount: TextView = view.findViewById(R.id.tvCashAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cash, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val ctx = holder.itemView.context

        holder.tvCategory.text = e.category

        holder.tvNote.text = e.note.orEmpty()
        holder.tvNote.visibility = if (e.note.isNullOrBlank()) View.GONE else View.VISIBLE

        holder.tvDate.text = Format.dateTime(e.timestamp)

        // A leading sign, not just colour — colour alone fails anyone who
        // cannot tell red from green, and this is money.
        val sign = if (e.isIncome) "+" else "−"
        holder.tvAmount.text = "$sign ${Format.money(e.amount)}"
        holder.tvAmount.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (e.isIncome) R.color.green_got else R.color.red_gave
            )
        )

        holder.itemView.setOnLongClickListener {
            onLongClick(e)
            true
        }
    }
}
