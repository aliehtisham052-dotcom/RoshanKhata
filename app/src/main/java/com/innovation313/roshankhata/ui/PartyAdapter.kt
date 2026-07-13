package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.PartyWithBalance

class PartyAdapter(
    private val onClick: (PartyWithBalance) -> Unit,
    private val onLongClick: (PartyWithBalance) -> Unit
) : ListAdapter<PartyWithBalance, PartyAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PartyWithBalance>() {
            override fun areItemsTheSame(a: PartyWithBalance, b: PartyWithBalance) = a.id == b.id
            override fun areContentsTheSame(a: PartyWithBalance, b: PartyWithBalance) = a == b
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val tvBalance: TextView = view.findViewById(R.id.tvBalance)
        val tvBalanceLabel: TextView = view.findViewById(R.id.tvBalanceLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_party, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        holder.tvName.text = item.name
        holder.tvPhone.text = item.phone.orEmpty()
        holder.tvPhone.visibility = if (item.phone.isNullOrBlank()) View.GONE else View.VISIBLE

        holder.tvBalance.text = Format.money(item.balance)

        when {
            item.balance > 0 -> {
                holder.tvBalance.setTextColor(ContextCompat.getColor(ctx, R.color.green_got))
                holder.tvBalanceLabel.setText(R.string.you_will_get)
            }
            item.balance < 0 -> {
                holder.tvBalance.setTextColor(ContextCompat.getColor(ctx, R.color.red_gave))
                holder.tvBalanceLabel.setText(R.string.you_will_give)
            }
            else -> {
                holder.tvBalance.setTextColor(ContextCompat.getColor(ctx, R.color.black))
                holder.tvBalanceLabel.setText(R.string.settled)
            }
        }

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }
}
