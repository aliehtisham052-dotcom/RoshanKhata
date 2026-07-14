package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.PlanProgress

class PlanAdapter(
    private val onClick: (PlanProgress) -> Unit
) : RecyclerView.Adapter<PlanAdapter.VH>() {

    private var items: List<PlanProgress> = emptyList()

    fun submit(newItems: List<PlanProgress>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvParty: TextView = view.findViewById(R.id.tvPlanParty)
        val tvStatus: TextView = view.findViewById(R.id.tvPlanStatus)
        val tvNote: TextView = view.findViewById(R.id.tvPlanNoteRow)
        val pb: ProgressBar = view.findViewById(R.id.pbPlan)
        val tvPaid: TextView = view.findViewById(R.id.tvPlanPaid)
        val tvRemaining: TextView = view.findViewById(R.id.tvPlanRemaining)
        val tvDue: TextView = view.findViewById(R.id.tvPlanDue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_plan, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val ctx = holder.itemView.context

        holder.tvParty.text = p.partyName

        holder.tvNote.text = p.note.orEmpty()
        holder.tvNote.visibility = if (p.note.isNullOrBlank()) View.GONE else View.VISIBLE

        holder.pb.progress = p.percentPaid

        holder.tvPaid.text = ctx.getString(
            R.string.plan_paid,
            Format.money(p.paidSoFar),
            Format.money(p.totalAmount)
        )
        holder.tvRemaining.text = ctx.getString(
            R.string.plan_remaining,
            Format.money(p.remaining)
        )

        if (p.isClosed) {
            holder.tvStatus.setText(R.string.plan_closed)
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.green_got))
            holder.tvDue.visibility = View.GONE
            holder.itemView.alpha = 0.6f
        } else {
            holder.tvStatus.setText(R.string.plan_open)
            holder.tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.navy_primary))
            holder.itemView.alpha = 1f

            holder.tvDue.visibility = View.VISIBLE
            val due = p.nextDueDate

            if (due == null) {
                holder.tvDue.setText(R.string.plan_next_due_none)
                holder.tvDue.setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
            } else {
                val overdue = due < System.currentTimeMillis()
                holder.tvDue.text = ctx.getString(
                    if (overdue) R.string.plan_overdue else R.string.plan_next_due,
                    Format.dateOnly(due)
                )
                holder.tvDue.setTextColor(
                    ContextCompat.getColor(
                        ctx,
                        if (overdue) R.color.red_gave else R.color.navy_primary
                    )
                )
            }
        }

        holder.itemView.setOnClickListener { onClick(p) }
    }
}
