package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.PartyPhoto
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
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val tvInitials: TextView = view.findViewById(R.id.tvInitials)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvPhone: TextView = view.findViewById(R.id.tvPhone)
        val tvBalance: TextView = view.findViewById(R.id.tvBalance)
        val tvBalanceLabel: TextView = view.findViewById(R.id.tvBalanceLabel)
        val tvLimitWarning: TextView = view.findViewById(R.id.tvLimitWarning)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_party, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        holder.tvName.text = item.name

        // Photo if we have one, initials if not. A list of blank grey circles
        // helps nobody; initials at least tell the eye who is who.
        val photo = PartyPhoto.load(ctx, item.id)
        if (photo != null) {
            holder.ivAvatar.setImageBitmap(photo)
            holder.ivAvatar.clipToOutline = true
            holder.ivAvatar.background = ContextCompat.getDrawable(ctx, R.drawable.bg_avatar_circle)
            holder.ivAvatar.visibility = View.VISIBLE
            holder.tvInitials.visibility = View.GONE
        } else {
            holder.ivAvatar.visibility = View.GONE
            holder.tvInitials.visibility = View.VISIBLE
            holder.tvInitials.text = initialsOf(item.name)
        }

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

        // The badge only speaks when a limit was actually set and the party
        // is at or beyond it. Below the line it stays quiet — a warning that
        // fires all the time stops being a warning.
        val limit = item.creditLimit
        if (limit != null && limit > 0 && item.balance > 0) {
            when {
                item.balance >= limit -> {
                    holder.tvLimitWarning.visibility = View.VISIBLE
                    holder.tvLimitWarning.setText(R.string.over_limit_badge)
                    holder.tvLimitWarning.setTextColor(
                        ContextCompat.getColor(ctx, R.color.red_gave)
                    )
                }
                item.balance >= limit * 0.9 -> {
                    holder.tvLimitWarning.visibility = View.VISIBLE
                    holder.tvLimitWarning.setText(R.string.near_limit_badge)
                    holder.tvLimitWarning.setTextColor(
                        ContextCompat.getColor(ctx, R.color.gold_accent)
                    )
                }
                else -> holder.tvLimitWarning.visibility = View.GONE
            }
        } else {
            holder.tvLimitWarning.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    /** "Nawab Batta Wala" -> "NB". One letter if there is only one word. */
    private fun initialsOf(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }
}
