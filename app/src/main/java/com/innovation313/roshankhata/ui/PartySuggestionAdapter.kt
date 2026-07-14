package com.innovation313.roshankhata.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.PartyWithBalance

/**
 * Name suggestions as the owner types.
 *
 * Filtering the list below the box is not the same thing as suggesting. With
 * two hundred customers, a filtered list still means finding the row and
 * tapping it. A suggestion goes straight to the account.
 *
 * The balance rides along in each suggestion, because a shopkeeper typing a
 * name is nearly always asking one question — what does this person owe me? —
 * and it can be answered before they even open the account.
 */
class PartySuggestionAdapter(
    context: Context,
    private val onPicked: (PartyWithBalance) -> Unit
) : ArrayAdapter<PartyWithBalance>(context, 0) {

    /** Everything we could suggest from. */
    private var source: List<PartyWithBalance> = emptyList()

    /** What matched the current query. */
    private var matches: List<PartyWithBalance> = emptyList()

    fun setSource(parties: List<PartyWithBalance>) {
        source = parties
    }

    override fun getCount() = matches.size

    override fun getItem(position: Int): PartyWithBalance? = matches.getOrNull(position)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_party_suggestion, parent, false)

        val p = matches[position]

        view.findViewById<TextView>(R.id.tvSuggestName).text = p.name

        val phone = view.findViewById<TextView>(R.id.tvSuggestPhone)
        phone.text = p.phone.orEmpty()
        phone.visibility = if (p.phone.isNullOrBlank()) View.GONE else View.VISIBLE

        val tvBal = view.findViewById<TextView>(R.id.tvSuggestBalance)
        when {
            p.balance > 0 -> {
                tvBal.text = Format.money(p.balance)
                tvBal.setTextColor(ContextCompat.getColor(context, R.color.green_got))
            }
            p.balance < 0 -> {
                tvBal.text = Format.money(-p.balance)
                tvBal.setTextColor(ContextCompat.getColor(context, R.color.red_gave))
            }
            else -> {
                tvBal.setText(R.string.settled)
                tvBal.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            }
        }

        return view
    }

    override fun getFilter(): Filter = object : Filter() {

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val q = constraint?.toString()?.trim().orEmpty()

            val found = if (q.isEmpty()) {
                emptyList()
            } else {
                // Match on the name OR the phone. A shopkeeper who cannot recall
                // the spelling often remembers the number, and vice versa.
                //
                // Digits are compared with the separators stripped, because
                // '0344 4266412' and '03444266412' are the same number and the
                // owner should not have to guess which way they typed it in.
                val digits = q.filter { it.isDigit() }

                source.filter { p ->
                    p.name.contains(q, ignoreCase = true) ||
                        (digits.isNotEmpty() &&
                            p.phone?.filter { it.isDigit() }?.contains(digits) == true)
                }
                    // Names that START with what was typed come first. Typing
                    // 'Ch' should offer 'Chand' before 'Bakhsh Chaudhry'.
                    .sortedWith(
                        compareByDescending<PartyWithBalance> {
                            it.name.startsWith(q, ignoreCase = true)
                        }.thenBy { it.name.lowercase() }
                    )
                    .take(8)
            }

            return FilterResults().apply {
                values = found
                count = found.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            matches = (results?.values as? List<PartyWithBalance>).orEmpty()
            if (matches.isEmpty()) notifyDataSetInvalidated() else notifyDataSetChanged()
        }

        /**
         * What lands in the box when a suggestion is tapped.
         *
         * The name, not the balance — the box is a search field, and putting a
         * figure in it would leave the owner staring at a query that matches
         * nothing.
         */
        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? PartyWithBalance)?.name.orEmpty()
    }
}
