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
data class ContactRow(val contact: PhoneContact, val isSelected: Boolean, val section: String? = null)

/**
 * A contact the phone has no name for: Android hands its number as the name, so the row read the number
 * twice under a "?" (10 Nov 2026, the owner's photo). No letter in it means no name.
 */
fun PhoneContact.hasNoName(): Boolean = name.isBlank() || name.none { it.isLetter() }

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

    /**
     * [withSections]: the list is in name order (no search), so the first name of each letter carries the
     * letter above it, as the phone's own contacts do; names with no letter go under "#".
     */
    /** The shop's calling code, read once per list rather than once per row. */
    private var dialing: String? = null

    fun submit(newItems: List<PhoneContact>, newSelected: Set<String>, withSections: Boolean = false) {
        var last: String? = null
        submitList(newItems.map { c ->
            val letter = if (!withSections) null else sectionOf(c)
            val head = if (letter != null && letter != last) letter else null
            if (letter != null) last = letter
            ContactRow(c, c.phone in newSelected, head)
        })
    }

    private fun sectionOf(c: PhoneContact): String {
        if (c.hasNoName()) return "#"
        val first = c.name.trim().firstOrNull { it.isLetter() } ?: return "#"
        return first.uppercaseChar().toString()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cb: MaterialCheckBox = view.findViewById(R.id.cbSelect)
        val tvName: TextView = view.findViewById(R.id.tvContactName)
        val tvPhone: TextView = view.findViewById(R.id.tvContactPhone)
        val tvAdded: TextView = view.findViewById(R.id.tvAdded)
        val tvInitials: TextView = view.findViewById(R.id.tvContactInitials)
        val tvSection: TextView = view.findViewById(R.id.tvSection)
        val row: View = view.findViewById(R.id.contactRow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val c = row.contact

        val ctx = holder.itemView.context
        holder.tvSection.visibility = if (row.section != null) View.VISIBLE else View.GONE
        holder.tvSection.text = row.section ?: ""
        // Every number in one shape, the shop's calling code first (DuplicateDetector's rule): the list showed
        // "+923001234567", "+92 300 1234567" and "0300 1234567" side by side.
        val dialing = dialing ?: com.innovation313.roshankhata.data.BusinessProfile.country(ctx).dialing.also { dialing = it }
        holder.tvPhone.text = DuplicateDetector.displayPhone(c.phone, dialing)
        if (c.hasNoName()) {
            holder.tvName.text = ctx.getString(R.string.contacts_no_name)
            holder.tvName.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_muted))
            holder.tvInitials.text = "#"
            AvatarHue.paint(holder.tvInitials, c.phone)
        } else {
            holder.tvName.text = Format.name(c.name)
            holder.tvName.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.ink))
            // The Khata list's own avatar: initials on a hue taken from the name.
            holder.tvInitials.text = Format.initials(c.name)
            AvatarHue.paint(holder.tvInitials, c.name)
        }

        if (c.alreadyAdded || c.inRecycleBin) {
            // Already on the books one way or the other — shown, but not
            // offered again. A binned contact says so, so the owner knows to
            // restore them (with their history) instead of wondering why the
            // name is greyed out.
            holder.cb.visibility = View.INVISIBLE
            holder.tvAdded.visibility = View.VISIBLE
            holder.tvAdded.setText(
                if (c.inRecycleBin) R.string.contact_in_recycle_bin else R.string.contact_in_khata
            )
            holder.row.isEnabled = false
            holder.row.alpha = 0.55f
            holder.row.setOnClickListener(null)
        } else {
            holder.cb.visibility = View.VISIBLE
            holder.tvAdded.visibility = View.GONE
            holder.row.isEnabled = true
            holder.row.alpha = 1f
            holder.cb.isChecked = row.isSelected
            holder.row.setOnClickListener { onToggle(c) }
        }
    }
}
