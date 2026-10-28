package com.innovation313.roshankhata.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import com.innovation313.roshankhata.R

// The phone's side of [SmartSuggest]: the ranking and the product rows moved to :shared on 25 Oct 2026, so the
// computer's product boxes suggest by the same rule; the dropdown on an AutoCompleteTextView and the trade's
// starter goods read from the phone's resources stay here.

/**
 * Attach suggestions to a field.
 *
 * Safe to call again with a fresh list — a dialog that adds a product
 * mid-entry can re-attach and the next keystroke sees it. Passing an
 * empty list leaves the field an ordinary text box rather than showing an
 * empty dropdown.
 *
 * [maxRows] is how many suggestions the list will show. Each row is two
 * lines, so the default eight is close to half a screen: right in a small
 * dialog, wrong on a form where the list opens over the fields still to be
 * filled. A form like that asks for fewer.
 */
fun SmartSuggest.attach(field: AutoCompleteTextView, items: List<SmartSuggest.Item>, maxRows: Int = SmartSuggest.MAX_ROWS) {
    field.setAdapter(SuggestAdapter(field.context, items, maxRows))
    // One character is enough. The owner is typing a name they already
    // know; making them type three before the app admits it knows it too
    // is the app being coy about something it could have said sooner.
    field.threshold = 1
    // A name picked, then returned to, should offer the list again rather
    // than sit there as a dead end.
    field.setOnClickListener { if (field.text.isNotEmpty()) field.showDropDown() }
}

/**
 * The adapter behind an attached field.
 *
 * Its filter ranks with the shared [SmartSuggest.Shelf] and publishes the result. It never
 * uses reflection and holds nothing but strings, so R8 has nothing here
 * it can quietly break.
 */
private class SuggestAdapter(
    context: Context,
    all: List<SmartSuggest.Item>,
    private val maxRows: Int
) : BaseAdapter(), Filterable {

    /** Folded once here, so no keystroke ever folds the shelf again. */
    private val shelf = SmartSuggest.Shelf(all)

    private val inflater = LayoutInflater.from(context)
    private var shown: List<SmartSuggest.Item> = emptyList()

    override fun getCount(): Int = shown.size
    override fun getItem(position: Int): SmartSuggest.Item = shown[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_suggestion, parent, false)
        val item = shown[position]
        view.findViewById<TextView>(R.id.tvSuggestion).text = item.value
        val sub = view.findViewById<TextView>(R.id.tvSuggestionSub)
        val subtitle = item.subtitle
        if (subtitle.isNullOrBlank()) {
            sub.visibility = View.GONE
        } else {
            sub.visibility = View.VISIBLE
            sub.text = subtitle
        }
        return view
    }

    override fun getFilter(): Filter = object : Filter() {

        /**
         * What the box shows once a row is tapped: the name alone. The
         * subtitle is there to tell rows apart while choosing, and has no
         * business being written into the ledger.
         */
        override fun convertResultToString(resultValue: Any?): CharSequence =
            (resultValue as? SmartSuggest.Item)?.value ?: ""

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val matches = shelf.rank(constraint?.toString().orEmpty(), maxRows)
            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            shown = (results?.values as? List<SmartSuggest.Item>) ?: emptyList()
            if (shown.isEmpty()) notifyDataSetInvalidated() else notifyDataSetChanged()
        }
    }
}

/**
 * The shop's products as suggestions, and — while the shop has fewer than
 * [STARTER_UNTIL] of its own — the trade's starter goods after them
 * (9 Oct 2026). A new dairy's first entry form offers Milk, Yogurt, Ghee in
 * the owner's language rather than an empty list; a book that has grown its
 * own products no longer sees them. A starter that is already a product
 * (by the same name rule products use) is not offered twice. Choosing a
 * starter saves the typed name like any other, nothing special is written.
 */
fun List<com.innovation313.roshankhata.data.Product>.withStarters(context: Context): List<SmartSuggest.Item> =
    if (size >= STARTER_UNTIL) asSuggestions()
    else withStarters(
        com.innovation313.roshankhata.data.TradeVocab.starters(context),
        context.getString(R.string.vocab_suggested)
    )
