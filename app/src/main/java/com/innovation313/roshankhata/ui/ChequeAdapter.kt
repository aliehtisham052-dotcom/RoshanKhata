package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.ChequeStatus
import com.innovation313.roshankhata.data.ChequeWithParty
import java.util.concurrent.TimeUnit

class ChequeAdapter(
    private val onClick: (ChequeWithParty) -> Unit
) : RecyclerView.Adapter<ChequeAdapter.VH>() {

    private var items: List<ChequeWithParty> = emptyList()

    fun submit(newItems: List<ChequeWithParty>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val stripe: View = view.findViewById(R.id.vStatusStripe)
        val tvParty: TextView = view.findViewById(R.id.tvChequeParty)
        val tvDetail: TextView = view.findViewById(R.id.tvChequeDetail)
        val tvDue: TextView = view.findViewById(R.id.tvChequeDue)
        val tvAmount: TextView = view.findViewById(R.id.tvChequeAmount)
        val tvStatus: TextView = view.findViewById(R.id.tvChequeStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cheque, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        val ctx = holder.itemView.context

        holder.tvParty.text = c.partyName

        val detail = buildList {
            add(ctx.getString(if (c.isReceived) R.string.cheque_received else R.string.cheque_issued))
            c.chequeNumber?.takeIf { it.isNotBlank() }?.let { add("#$it") }
            c.bankName?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
        holder.tvDetail.text = detail

        holder.tvAmount.text = Format.money(c.amount)
        holder.tvAmount.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (c.isReceived) R.color.green_got else R.color.red_gave
            )
        )

        // A pending cheque's due date is the thing that matters — it is what
        // tells the owner to go to the bank today. Once settled, the countdown
        // is noise, so it goes away.
        if (c.status == ChequeStatus.PENDING) {
            val days = daysUntil(c.dueDate)
            holder.tvDue.visibility = View.VISIBLE
            holder.tvDue.text = when {
                days == 0L -> ctx.getString(R.string.due_today)
                days > 0 -> ctx.getString(R.string.due_in_days, days.toInt())
                else -> ctx.getString(R.string.overdue_by_days, (-days).toInt())
            }
            holder.tvDue.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    if (days <= 0) R.color.red_gave else R.color.ink
                )
            )
        } else {
            holder.tvDue.visibility = View.GONE
        }

        val (statusText, statusColour) = when (c.status) {
            ChequeStatus.PENDING -> R.string.cheque_pending to R.color.ink
            ChequeStatus.CLEARED -> R.string.cheque_cleared to R.color.green_got
            ChequeStatus.BOUNCED -> R.string.cheque_bounced to R.color.red_gave
            else -> R.string.cheque_cancelled to R.color.text_muted
        }
        holder.tvStatus.setText(statusText)
        holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, statusColour))
        holder.stripe.setBackgroundColor(ContextCompat.getColor(ctx, statusColour))

        // Settled cheques stay visible as a record, but faded — they are
        // history, not something needing action.
        holder.itemView.alpha = if (c.status == ChequeStatus.PENDING) 1f else 0.6f

        holder.itemView.setOnClickListener { onClick(c) }
    }

    /** Whole days from today to the due date. Negative once overdue. */
    private fun daysUntil(dueDate: Long): Long {
        val startOfToday = startOfDay(System.currentTimeMillis())
        val startOfDue = startOfDay(dueDate)
        return TimeUnit.MILLISECONDS.toDays(startOfDue - startOfToday)
    }

    private fun startOfDay(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
