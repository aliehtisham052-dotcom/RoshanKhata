package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.PhoneContact

/** A contact together with whether its box is ticked, so the tick can be diffed. */
data class ContactRow(val contact: PhoneContact, val isSelected: Boolean)

class ContactAdapter(
    private val onToggle: (PhoneContact) -> Unit
) : ListAdapter<ContactRow, ContactAdapter.VH>(DIFF) {

    companion object {
        /**
         * A phone's address book runs to thousands of names, and this list was
         * rebuilt whole on every change — including every single tick, since
         * ticking a box re-submits the same list with a new selection. The
         * owner picking twenty customers out of two thousand contacts paid for
         * two thousand rebinds, twenty times.
         *
         * The tick is carried INSIDE the diffed row rather than held beside
         * the list, so DiffUtil can see it: tick one box and exactly one row
         * is found changed, and exactly one row repaints. Matched on phone
         * number, which is what the selection itself is keyed by.
         */
        private val DIFF = object : DiffUtil.ItemCallback<ContactRow>() {
            override fun areItemsTheSame(a: ContactRow, b: ContactRow) =
                a.contact.phone == b.contact.phone
            override fun areContentsTheSame(a: ContactRow, b: ContactRow) = a == b
        }
    }

    fun submit(newItems: List<PhoneContact>, newSelected: Set<String>) {
        submitList(newItems.map { ContactRow(it, it.phone in newSelected) })
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cb: MaterialCheckBox = view.findViewById(R.id.cbSelect)
        val tvName: TextView = view.findViewById(R.id.tvContactName)
        val tvPhone: TextView = view.findViewById(R.id.tvContactPhone)
        val tvAdded: TextView = view.findViewById(R.id.tvAdded)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val c = row.contact

        holder.tvName.text = c.name
        holder.tvPhone.text = c.phone

        if (c.alreadyAdded || c.inRecycleBin) {
            // Already on the books one way or the other — shown, but not
            // offered again. A binned contact says so, so the owner knows to
            // restore them (with their history) instead of wondering why the
            // name is greyed out.
            holder.cb.visibility = View.INVISIBLE
            holder.tvAdded.visibility = View.VISIBLE
            holder.tvAdded.setText(
                if (c.inRecycleBin) R.string.contact_in_recycle_bin else R.string.already_added
            )
            holder.itemView.isEnabled = false
            holder.itemView.alpha = 0.55f
            holder.itemView.setOnClickListener(null)
        } else {
            holder.cb.visibility = View.VISIBLE
            holder.tvAdded.visibility = View.GONE
            holder.itemView.isEnabled = true
            holder.itemView.alpha = 1f
            holder.cb.isChecked = row.isSelected
            holder.itemView.setOnClickListener { onToggle(c) }
        }
    }
}
