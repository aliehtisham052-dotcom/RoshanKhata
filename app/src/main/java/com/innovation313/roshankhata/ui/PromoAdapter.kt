package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.PartyWithBalance

/**
 * Customers who bought a particular product, ready to be told it is back.
 *
 * One row, one tap, one message. Deliberately not a "send to all" list: a
 * hundred identical messages leaving one number in a minute is what gets a
 * number blocked, and a shopkeeper's WhatsApp is their shop. The owner presses
 * send each time, sees who they are writing to, and can skip anyone.
 */
class PromoAdapter(
    private val onSend: (PartyWithBalance) -> Unit
) : ListAdapter<PartyWithBalance, PromoAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvPromoName)
        val tvWhen: TextView = view.findViewById(R.id.tvPromoWhen)
        val tvSend: TextView = view.findViewById(R.id.tvPromoSend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_promo, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        val ctx = holder.itemView.context

        holder.tvName.text = p.name
        holder.tvWhen.text = if (p.lastActivity > 0) {
            ctx.getString(R.string.promo_last_bought, Format.dateOnly(p.lastActivity))
        } else {
            ""
        }

        // A customer with no number cannot be messaged, and the row should say
        // so rather than opening WhatsApp to nothing.
        val hasPhone = !p.phone.isNullOrBlank()
        holder.tvSend.text = ctx.getString(
            if (hasPhone) R.string.promo_send else R.string.no_phone_number
        )
        holder.tvSend.isEnabled = hasPhone
        holder.itemView.isEnabled = hasPhone
        holder.itemView.setOnClickListener { if (hasPhone) onSend(p) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PartyWithBalance>() {
            override fun areItemsTheSame(a: PartyWithBalance, b: PartyWithBalance) = a.id == b.id
            override fun areContentsTheSame(a: PartyWithBalance, b: PartyWithBalance) = a == b
        }
    }
}
