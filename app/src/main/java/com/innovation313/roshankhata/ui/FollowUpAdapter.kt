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
import com.innovation313.roshankhata.data.FollowUpRank
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentHabit

/**
 * Everyone who owes the shop, as an action list rather than a balance view.
 *
 * Each row answers the day's actual question — "kis se lena hai?" — with the
 * amount, how long the account has sat quiet, and a one-tap WhatsApp reminder.
 * The 30-day mark that the home summary already counts as "worth a reminder"
 * is the same mark that turns a row's age line red here: one definition of
 * overdue in the app, not two.
 *
 * Like the promo list, there is deliberately no send-to-all. The owner sends
 * one message at a time, sees who they are writing to, and can skip anyone —
 * a hundred identical messages leaving one number in a minute is what gets
 * that number restricted, and the owner's WhatsApp is their shop.
 */
class FollowUpAdapter(
    private val onOpen: (PartyWithBalance) -> Unit,
    private val onSend: (PartyWithBalance) -> Unit
) : ListAdapter<PartyWithBalance, FollowUpAdapter.VH>(DIFF) {

    /**
     * Each debtor's payment habit, by party id. Set just before each
     * submitList, so a list and the habits beside it always arrive together;
     * a party missing here has too little history and shows no habit line.
     */
    var habits: Map<Long, PaymentHabit.Habit> = emptyMap()

    /** Party id → earliest open promised date; set with [habits], before submitList. */
    var promises: Map<Long, Long> = emptyMap()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFollowUpName)
        val tvAge: TextView = view.findViewById(R.id.tvFollowUpAge)
        val tvHabit: TextView = view.findViewById(R.id.tvFollowUpHabit)
        val tvAmount: TextView = view.findViewById(R.id.tvFollowUpAmount)
        val tvSend: TextView = view.findViewById(R.id.tvFollowUpSend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_followup, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = getItem(position)
        val ctx = holder.itemView.context

        holder.tvName.text = p.name
        holder.tvAmount.text = Format.money(p.balance)

        // Whole days since the last entry — the age of the silence, which is
        // what the owner is ranking on. Red from 30 days, the same cutoff the
        // home summary counts as "worth a reminder".
        val days = ((System.currentTimeMillis() - p.lastActivity) /
            (24L * 60 * 60 * 1000)).toInt()
        val overdue = days >= 30
        holder.tvAge.text = if (days <= 0) {
            ctx.getString(R.string.followup_today)
        } else {
            ctx.resources.getQuantityString(R.plurals.followup_last_ago, days, days)
        }
        holder.tvAge.setTextColor(
            ContextCompat.getColor(ctx, if (overdue) R.color.red_gave_text else R.color.text_muted)
        )

        // Their own rhythm: muted when on time, gold when behind it — gold,
        // not red, because red already means "30 days quiet" on the line
        // above and the two are different facts.
        val habit = habits[p.id]
        val promise = promises[p.id]
        if (habit == null && promise == null) {
            holder.tvHabit.visibility = View.GONE
        } else {
            holder.tvHabit.visibility = View.VISIBLE
            val res = ctx.resources
            val habitText = habit?.let {
                if (it.isLate) res.getQuantityString(R.plurals.followup_habit_late, it.waitingDays, it.waitingDays, it.typicalDays)
                else res.getQuantityString(R.plurals.followup_habit, it.typicalDays, it.typicalDays)
            }
            // The customer's own date comes first: it is what the reminder quotes.
            val promiseText = promise?.let { ctx.getString(R.string.followup_promised, Format.dateOnly(it)) }
            holder.tvHabit.text = listOfNotNull(promiseText, habitText).joinToString("  ·  ")
            val urgent = habit?.isLate == true ||
                FollowUpRank.isDue(promise, System.currentTimeMillis(), java.util.TimeZone.getDefault())
            holder.tvHabit.setTextColor(
                ContextCompat.getColor(ctx, if (urgent) R.color.gold_accent else R.color.text_muted)
            )
            holder.tvHabit.setTypeface(null, if (urgent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

        // A customer with no number cannot be messaged, and the row should
        // say so rather than opening WhatsApp to nothing.
        val hasPhone = !p.phone.isNullOrBlank()
        holder.tvSend.text = ctx.getString(
            if (hasPhone) R.string.promo_send else R.string.no_phone_number
        )
        holder.tvSend.isEnabled = hasPhone
        holder.tvSend.setOnClickListener { if (hasPhone) onSend(p) }

        // The row itself opens the ledger — the reminder is the shortcut on
        // the side, not the only thing a row can do.
        holder.itemView.setOnClickListener { onOpen(p) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PartyWithBalance>() {
            override fun areItemsTheSame(a: PartyWithBalance, b: PartyWithBalance) = a.id == b.id
            override fun areContentsTheSame(a: PartyWithBalance, b: PartyWithBalance) = a == b
        }
    }
}
