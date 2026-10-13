package com.innovation313.roshankhata.ui

import android.app.Activity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.innovation313.roshankhata.R

/**
 * The Profile screen's foldable sections (13 Oct 2026).
 *
 * The profile had grown into one long form — logo, names, address, phone,
 * email, tax number, trade, currency, calendar, bank, QR, stamp, signature,
 * terms, three switches — and the owner lost his place in it. Each section is
 * now a card whose header row folds the body away; folded, the header's
 * second line says what the section holds, so the page reads at a glance.
 *
 * Folding is only visibility: every field stays inflated and bound, so saving
 * reads them exactly as before whether or not they are shown.
 */
class ProfileSections(private val activity: Activity) {

    inner class Section(
        val header: View,
        val body: View,
        private val chevron: ImageView,
        private val summaryView: TextView,
        private val title: String,
        private val summary: () -> String,
    ) {
        val isOpen: Boolean get() = body.visibility == View.VISIBLE

        fun setOpen(open: Boolean) {
            body.visibility = if (open) View.VISIBLE else View.GONE
            chevron.rotation = if (open) 180f else 0f
            refresh()
        }

        /** The second line: shown folded, hidden open (the fields say it). */
        fun refresh() {
            val text = summary().trim()
            summaryView.text = text
            summaryView.visibility = if (!isOpen && text.isNotEmpty()) View.VISIBLE else View.GONE
            header.contentDescription = if (text.isEmpty()) title else "$title. $text"
        }
    }

    private val sections = mutableListOf<Section>()

    fun add(
        @IdRes headerId: Int,
        @IdRes bodyId: Int,
        @StringRes titleRes: Int,
        @DrawableRes iconRes: Int,
        startOpen: Boolean,
        summary: () -> String,
    ): Section {
        val header = activity.findViewById<View>(headerId)
        val body = activity.findViewById<View>(bodyId)
        val title = activity.getString(titleRes)
        header.findViewById<TextView>(R.id.tvSectionTitle).text = title
        header.findViewById<ImageView>(R.id.ivSectionIcon).setImageResource(iconRes)
        val section = Section(
            header, body,
            header.findViewById(R.id.ivSectionChevron),
            header.findViewById(R.id.tvSectionSummary),
            title, summary,
        )
        header.setOnClickListener { section.setOpen(!section.isOpen) }
        section.setOpen(startOpen)
        sections += section
        return section
    }

    fun refreshAll() = sections.forEach { it.refresh() }

    /** Opens whichever section holds [view], so a jump to it lands on a shown field. */
    fun reveal(view: View) {
        var v: View? = view
        while (v != null) {
            sections.firstOrNull { it.body === v }?.let { if (!it.isOpen) it.setOpen(true) }
            v = v.parent as? View
        }
    }
}
