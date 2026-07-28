package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.innovation313.roshankhata.R

/**
 * One suspected duplicate group per row.
 *
 * A ListAdapter, matching every other growing list in this app — the same
 * reason as [ProductAdapter]: a full rebind on every change is a stutter the
 * owner feels, and a book with over a thousand customers can produce a real
 * number of groups.
 */
class DuplicateGroupAdapter(
    private val onReview: (DuplicateDetector.Group) -> Unit
) : ListAdapter<DuplicateDetector.Group, DuplicateGroupAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvReason: TextView = view.findViewById(R.id.tvDupReason)
        val tvMembers: TextView = view.findViewById(R.id.tvDupMembers)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_duplicate_group, parent, false)
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

        holder.tvMembers.text = group.members.joinToString("\n") { m ->
            val phone = m.phone?.takeIf { it.isNotBlank() } ?: "—"
            "${m.name}  —  $phone  —  ${Format.money(m.balance)}"
        }

        holder.itemView.setOnClickListener { onReview(group) }
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
