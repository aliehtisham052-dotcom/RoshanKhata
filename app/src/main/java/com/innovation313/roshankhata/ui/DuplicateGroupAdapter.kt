package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.quantity

/**
 * One suspected duplicate group per row.
 *
 * A ListAdapter, matching every other growing list in this app — the same
 * reason as [ProductAdapter]: a full rebind on every change is a stutter the
 * owner feels, and a book with over a thousand customers can produce a real
 * number of groups.
 */
class DuplicateGroupAdapter(
    private val onReview: (DuplicateDetector.Group) -> Unit,
    private val onNotSame: (DuplicateDetector.Group) -> Unit
) : ListAdapter<DuplicateDetector.Group, DuplicateGroupAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvReason: TextView = view.findViewById(R.id.tvDupReason)
        val tvCount: TextView = view.findViewById(R.id.tvDupCount)
        val tvCaution: TextView = view.findViewById(R.id.tvDupCaution)
        val members: LinearLayout = view.findViewById(R.id.dupMembers)
        val btnNotSame: View = view.findViewById(R.id.btnDupNotSame)
        val btnMerge: View = view.findViewById(R.id.btnDupMerge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_duplicate_group, parent, false)
        TextFit.relax(v)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val group = getItem(position)
        val ctx = holder.itemView.context

        holder.tvReason.text = ctx.getString(
            when (group.reason) {
                DuplicateDetector.Reason.NAME -> R.string.duplicate_reason_name
                DuplicateDetector.Reason.PHONE -> R.string.duplicate_reason_phone
                DuplicateDetector.Reason.BOTH -> R.string.duplicate_reason_both
            }
        )
        val n = group.members.size
        holder.tvCount.text = Digits.quantity(ctx.resources, R.plurals.duplicate_count, n, n)

        when (DuplicateDetector.caution(group)) {
            DuplicateDetector.Caution.ONE_DIGIT -> holder.tvCaution.setText(R.string.duplicate_caution_one_digit)
            DuplicateDetector.Caution.NUMBERS_DIFFER -> holder.tvCaution.setText(R.string.duplicate_caution_numbers)
            DuplicateDetector.Caution.NONE -> holder.tvCaution.text = null
        }
        holder.tvCaution.visibility = if (holder.tvCaution.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        // Each record on its own two lines: the name, then its number in one shape and its balance.
        val dialing = com.innovation313.roshankhata.data.BusinessProfile.country(ctx).dialing
        val d = ctx.resources.displayMetrics.density
        holder.members.removeAllViews()
        group.members.forEachIndexed { i, m ->
            val name = TextView(ctx).apply {
                text = Format.isolate(m.name)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setTextColor(ContextCompat.getColor(ctx, R.color.ink))
            }
            val balance = when {
                Money.isZero(m.balance) -> ctx.getString(R.string.settled)
                m.isCustomer -> Format.customerBalance(m.balance)
                else -> Format.money(m.balance)
            }
            val line = TextView(ctx).apply {
                text = DuplicateDetector.displayPhone(m.phone, dialing) + "  \u00b7  " + balance
                textSize = 13f
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setTextColor(ContextCompat.getColor(ctx, R.color.text_muted))
            }
            holder.members.addView(name, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { if (i > 0) topMargin = (10 * d).toInt() })
            holder.members.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        holder.btnNotSame.setOnClickListener { onNotSame(group) }
        holder.btnMerge.setOnClickListener { onReview(group) }
        holder.itemView.setOnClickListener(null)
        holder.itemView.isClickable = false
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DuplicateDetector.Group>() {
            override fun areItemsTheSame(a: DuplicateDetector.Group, b: DuplicateDetector.Group) =
                a.members.map { it.partyId }.toSet() == b.members.map { it.partyId }.toSet()

            override fun areContentsTheSame(a: DuplicateDetector.Group, b: DuplicateDetector.Group) =
                a == b
        }
    }
}
