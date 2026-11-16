package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch

/**
 * How the Products page finds a product (3 Nov 2026, the competitor study's price check), one rule on the
 * phone and the computer. By its name the way the khata finds a name ([NameSearch]: typed as it is said,
 * one slip forgiven), by a second name the page also searches (the technical name on the phone, the
 * company on the computer), by its barcode — the whole code, as a scanner types it, or four or more of its
 * digits, as a cashier reads them off the label — and by the words of its note ("Rack 3", "store room").
 * Before, a scanned code typed into the search found nothing: the box looked only at names.
 *
 * And by its other names (schema 45, 16 Nov 2026, the audit's T34): a product saved as "Urea" was never found by
 * "یوریا" or "यूरिया" — the name search folds spellings within a script, not across scripts, and no rule loose enough to
 * meet a loanword across scripts leaves ordinary words alone. So the shop writes the names it uses itself
 * ([Product.otherNames]), and every one of them finds the product as its name does.
 */
object ProductFind {

    /** The fewest digits that find a barcode by a part of it: fewer would match half the shelf. */
    const val MIN_CODE_DIGITS = 4

    /** The most names one product keeps, and the longest the whole field may be: a list, not a description. */
    const val MAX_OTHER_NAMES = 12
    const val MAX_OTHER_NAMES_CHARS = 300

    /** What separates the names: commas (Latin, Arabic-script), semicolons, new lines. */
    private val SEPARATORS = Regex("[,،;؛\\n\\r]+")

    /** The names in [text], each trimmed and kept once (by its letters, case aside), at most [MAX_OTHER_NAMES]. */
    fun otherNamesOf(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val seen = HashSet<String>()
        return text.split(SEPARATORS).map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
            .take(MAX_OTHER_NAMES)
    }

    /**
     * The field as it is kept: the names joined by ", ", or null when there are none — what a form saves and what a
     * restore reads, so a hand-edited file cannot store 500 names or a blank. A name past [MAX_OTHER_NAMES_CHARS] in all
     * is left out whole, never cut in the middle.
     */
    fun cleanOtherNames(text: String?): String? {
        val kept = ArrayList<String>()
        var length = 0
        for (n in otherNamesOf(text)) {
            val add = n.length + if (kept.isEmpty()) 0 else 2
            if (length + add > MAX_OTHER_NAMES_CHARS) break
            kept += n; length += add
        }
        return kept.joinToString(", ").ifEmpty { null }
    }

    fun matches(name: String, secondary: String?, barcode: String?, note: String?, query: String, otherNames: String? = null): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val code = barcode?.trim().orEmpty()
        if (code.isNotEmpty()) {
            if (code.equals(q, ignoreCase = true)) return true
            val digits = Digits.toLatin(q)
            if (digits.length >= MIN_CODE_DIGITS && digits.all { it in '0'..'9' } && code.contains(digits)) return true
        }
        val low = q.lowercase()
        if (NameSearch.matches(name, null, low)) return true
        if (!secondary.isNullOrBlank() && NameSearch.matches(secondary, null, low)) return true
        if (otherNamesOf(otherNames).any { NameSearch.matches(it, null, low) }) return true
        return !note.isNullOrBlank() && NameSearch.matches(note, null, low)
    }

    /** The one product a scanned or typed code names exactly, or null when none or more than one carry it. */
    fun byCode(products: List<Product>, code: String): Product? {
        val q = code.trim()
        if (q.isEmpty()) return null
        return products.filter { !it.isDeleted && it.barcode?.trim()?.equals(q, ignoreCase = true) == true }.singleOrNull()
    }
}
