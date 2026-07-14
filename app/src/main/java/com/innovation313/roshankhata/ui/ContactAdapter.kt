package com.innovation313.roshankhata.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.PhoneContact

class ContactAdapter(
    private val onToggle: (PhoneContact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.VH>() {

    private var items: List<PhoneContact> = emptyList()
    private var selected: Set<String> = emptySet()

    fun submit(newItems: List<PhoneContact>, newSelected: Set<String>) {
        items = newItems
        selected = newSelected
        notifyDataSetChanged()
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

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]

        holder.tvName.text = c.name
        holder.tvPhone.text = c.phone

        if (c.alreadyAdded) {
            // Already on the books — shown, but not offered again.
            holder.cb.visibility = View.INVISIBLE
            holder.tvAdded.visibility = View.VISIBLE
            holder.itemView.isEnabled = false
            holder.itemView.alpha = 0.55f
            holder.itemView.setOnClickListener(null)
        } else {
            holder.cb.visibility = View.VISIBLE
            holder.tvAdded.visibility = View.GONE
            holder.itemView.isEnabled = true
            holder.itemView.alpha = 1f
            holder.cb.isChecked = c.phone in selected
            holder.itemView.setOnClickListener { onToggle(c) }
        }
    }
}
