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

/**
 * What a suggestion field offers, and how it decides what to offer.
 *
 * WHY THIS EXISTS.
 *
 * Six fields in this app already suggest, and every one of them hands a plain
 * ArrayAdapter a list of strings. That adapter's filter matches on the START
 * of the text and nothing else, so a dealer who types "kunfidor" is told
 * Confidor does not exist, and one who types "sencor" never reaches
 * "Sencor 70WP" because the stored name is longer than what they typed only
 * at the end. Meanwhile [NameSearch] — written for this app, for exactly
 * these spellings — sits next door answering the search box.
 *
 * So this is not a new matcher. It is the matcher the app already has,
 * finally wired to the boxes people type product names into.
 *
 * WHAT IT WILL AND WILL NOT DO.
 *
 * A suggestion is an offer, never a decision. Nothing here writes to the
 * ledger, nothing replaces what was typed, and the owner can always ignore
 * the list and type their own words — a product this shop has never sold is
 * still a product, and a field that refuses it would be a worse field.
 *
 * And the offers have to be RIGHT. A list that pads itself out with near
 * misses teaches the owner to stop reading it, which is worse than no list
 * at all: they will tap the second row by habit and the wrong product goes
 * on someone's account. So the tiers below get stricter as they get looser,
 * and the loosest tier — forgiving one wrong letter — is only reached by a
 * query long enough for that to mean something, [MIN_TYPO_LEN]. Below that a
 * single wrong letter is not a typo, it is a different word.
 */
object SmartSuggest {

    /**
     * One row of the dropdown.
     *
     * [value] is what lands in the box when it is tapped. [subtitle] is what
     * tells two similar rows apart — the company, the unit — and is shown
     * small underneath. Two products called "Super" from two companies are an
     * ordinary week in this trade, and a list that shows only the name makes
     * the owner guess.
     */
    data class Item(val value: String, val subtitle: String? = null)

    /**
     * A candidate with its folded forms already worked out.
     *
     * [rank] reads the folded name and the folded words of every candidate on
     * every keystroke. Folding is not free — it walks the string three times
     * and builds two more — and a shop with five hundred products typing an
     * eight-letter name would fold four thousand names to answer one box.
     *
     * So each name is folded ONCE, when the field is attached, and the
     * keystrokes read the answer. Same result, and the work no longer grows
     * with how fast someone types.
     */
    private class Candidate(val item: Item) {
        val lower: String = item.value.lowercase()
        val words: List<String> = lower.split(*SEPARATORS).filter { it.isNotEmpty() }
        val folded: String = NameSearch.fold(item.value)
        val foldedWords: List<String> =
            item.value.split(*SEPARATORS).map { NameSearch.fold(it) }.filter { it.isNotEmpty() }
        val skeleton: String = skeletonOf(item.value)
    }

    /** How a product name breaks into words. Matches [NameSearch]'s own separators. */
    private val SEPARATORS = charArrayOf(' ', '-', '/', '(', ')', ',', '.')

    /**
     * Shortest query worth forgiving a wrong letter in.
     *
     * Four, matching [NameSearch]'s own floor, and for the same reason: it
     * was measured there against a real book rather than guessed. Below four
     * letters, one edit away covers so much of the alphabet that the list
     * stops being an answer to anything.
     */
    private const val MIN_TYPO_LEN = 4

    /**
     * Shortest folded query that can mean anything at all.
     *
     * Three, matching [NameSearch]'s own MIN_OVERLAP. Everything from tier 3
     * down reads a FOLDED query, and folding throws away exactly the detail
     * that makes a short query specific — doubled letters, the silent h, the
     * difference between c, k and q. Two folded letters match most of a shelf.
     */
    private const val MIN_FOLD_LEN = 3

    /** Most rows a dropdown will ever show. Beyond this it is a list to scroll, not a suggestion. */
    private const val MAX_ROWS = 8

    /**
     * How well one candidate answers what was typed. Lower is better; null
     * means it does not answer it at all and must not be shown.
     *
     * The tiers, strictest first:
     *
     * 0 — the name starts with exactly what was typed. "conf" → "Confidor".
     * 1 — a WORD of the name starts with it. "70" → "Sencor 70WP", and more
     *     to the point "bayer" → anything whose name carries it.
     * 2 — it appears somewhere in the name, as typed.
     * 3 — it appears in the name once both are folded: the same word, spelled
     *     the way this shop says it. "kunfidor" → "Confidor", because [fold]
     *     treats k and q and c alike and evens out the doubled letters.
     * 4 — one letter wrong, missing or extra, against a single word of the
     *     name. "confidr" → "Confidor". Only from [MIN_TYPO_LEN] up.
     * 5 — the consonants agree, and ONLY when the query was typed in Urdu
     *     script, which does not write short vowels. See the tier itself.
     *
     * Tiers 0-2 read the name as it is stored, so a dealer who types a name
     * exactly right sees exactly what they expect, in the order they expect,
     * before any clever matching gets a say.
     */
    private fun rank(c: Candidate, typedRaw: String, typedFolded: String, urduTyped: Boolean): Int? {
        if (c.lower.startsWith(typedRaw)) return 0
        if (c.words.any { it.startsWith(typedRaw) }) return 1
        if (c.lower.contains(typedRaw)) return 2

        // Below three folded letters a fold is a coincidence, not a name —
        // the same floor [NameSearch] applies to its own search box, and it
        // earns its place here too. Folding collapses doubled letters, so
        // "zzz" folds to a bare "z" and, without this, matched every product
        // with a z anywhere in it. A dealer who typed three z's by accident
        // was being offered Zinc Sulphate as though the app had understood
        // something.
        if (typedFolded.length < MIN_FOLD_LEN) return null
        if (c.folded.contains(typedFolded)) return 3

        if (typedFolded.length >= MIN_TYPO_LEN &&
            c.foldedWords.any { NameSearch.withinOneEdit(typedFolded, it) }
        ) {
            return 4
        }

        // 5 — the name typed in Urdu script, against a name stored in Latin.
        //
        // Urdu does not write short vowels. "Confidor" is کنفیڈور: k-n-f-y-D-w-r,
        // with the o's simply absent, because the script leaves them to the
        // reader. That is not a misspelling to be forgiven — it is how the
        // writing system works, and no amount of tolerance for wrong letters
        // reaches it, because two vowels missing is two edits and two edits
        // is where a matcher starts inventing things.
        //
        // So the vowels are dropped from BOTH sides and the consonants
        // compared. Which is exactly the technique this app's own history
        // warns about: matching on consonants alone once wrote five thousand
        // rupees against a Hassan when an Ahsaan was meant, because h-s-n is
        // h-s-n.
        //
        // What makes it safe here and not there is [urduTyped]. This tier is
        // only ever reached when the query itself carries Urdu or Arabic
        // letters, so a dealer typing Roman — which is nearly all of them,
        // nearly all the time — can never land on it, and the behaviour they
        // already know is untouched to the letter. Inside the tier the same
        // floor applies: three consonants at least, or "dp" finds DAP, Top
        // and half the shelf.
        if (urduTyped) {
            val skeleton = skeletonOf(typedRaw)
            if (skeleton.length >= MIN_FOLD_LEN && c.skeleton.contains(skeleton)) {
                return 5
            }
        }
        return null
    }

    /**
     * A name reduced to the consonants Urdu script would actually write down.
     *
     * Two steps, and the first is the one worth explaining. This app's fold
     * treats an h riding on a consonant as silent, so "Sulphur" folds to
     * "sulpur" — right for a name where the h is decoration, wrong for "ph",
     * which is not a p with a silent h but the f sound, and is written ف in
     * Urdu. Left alone, سلفر and Sulphur disagreed on one letter and never
     * met; Nitrophos and نائٹروفاس likewise.
     *
     * The rule is applied HERE and not in [NameSearch], deliberately. Fold is
     * what the search box and the voice entry run on, and those two decide
     * whose account a payment lands on. This tier only ever draws a dropdown,
     * and only for a query typed in Urdu — a far smaller thing to be wrong
     * about, and the right place to carry a rule that only this tier needs.
     */
    private fun skeletonOf(name: String): String =
        NameSearch.fold(name.lowercase().replace("ph", "f")).filter { it !in "aeiou" }

    /** True when what was typed contains Urdu or Arabic letters rather than Latin. */
    private fun hasUrduScript(s: String): Boolean = s.any { it in '\u0600'..'\u06FF' }

    /**
     * The rows worth showing for [typed], best first.
     *
     * Kept out of the adapter so it can be reasoned about — and tested —
     * without a screen.
     */
    fun rank(items: List<Item>, typed: String): List<Item> =
        rankCandidates(items.map { Candidate(it) }, typed)

    private fun rankCandidates(candidates: List<Candidate>, typed: String): List<Item> {
        val raw = typed.trim().lowercase()
        if (raw.isEmpty()) return candidates.take(MAX_ROWS).map { it.item }
        val folded = NameSearch.fold(raw)
        val urdu = hasUrduScript(raw)

        return candidates
            .mapNotNull { c -> rank(c, raw, folded, urdu)?.let { it to c } }
            .sortedWith(
                compareBy({ it.first }, { it.second.item.value.length }, { it.second.lower })
            )
            .map { it.second.item }
            .take(MAX_ROWS)
    }

    /**
     * Attach suggestions to a field.
     *
     * Safe to call again with a fresh list — a dialog that adds a product
     * mid-entry can re-attach and the next keystroke sees it. Passing an
     * empty list leaves the field an ordinary text box rather than showing an
     * empty dropdown.
     */
    fun attach(field: AutoCompleteTextView, items: List<Item>) {
        field.setAdapter(Adapter(field.context, items))
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
     * Its filter does the ranking above and publishes the result. It never
     * uses reflection and holds nothing but strings, so R8 has nothing here
     * it can quietly break.
     */
    private class Adapter(
        context: Context,
        all: List<Item>
    ) : BaseAdapter(), Filterable {

        /** Folded once here, so no keystroke ever folds the shelf again. */
        private val candidates: List<Candidate> = all.map { Candidate(it) }

        private val inflater = LayoutInflater.from(context)
        private var shown: List<Item> = emptyList()

        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Item = shown[position]
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
                (resultValue as? Item)?.value ?: ""

            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val matches = rankCandidates(candidates, constraint?.toString().orEmpty())
                return FilterResults().apply {
                    values = matches
                    count = matches.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                shown = (results?.values as? List<Item>) ?: emptyList()
                if (shown.isEmpty()) notifyDataSetInvalidated() else notifyDataSetChanged()
            }
        }
    }
}

/**
 * The shop's own products as suggestion rows.
 *
 * Written once here rather than four times across the screens that need it,
 * because a product offered one way in Add Entry and another way on a
 * supplier bill is the same inconsistency this file exists to remove.
 *
 * The subtitle carries company and unit when the product has them. Both are
 * optional on a Product and most shops fill them in gradually, so the line is
 * built from whatever is actually there and omitted entirely when neither is.
 */
fun List<com.innovation313.roshankhata.data.Product>.asSuggestions(): List<SmartSuggest.Item> =
    map { p ->
        val parts = listOfNotNull(
            p.company?.takeIf { it.isNotBlank() },
            p.defaultUnit?.takeIf { it.isNotBlank() }
        )
        SmartSuggest.Item(
            value = p.name,
            subtitle = parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        )
    }
