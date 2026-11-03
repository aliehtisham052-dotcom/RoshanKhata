package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch

/**
 * How the Products page finds a product (3 Nov 2026, the competitor study's price check), one rule on the
 * phone and the computer. By its name the way the khata finds a name ([NameSearch]: typed as it is said,
 * one slip forgiven), by a second name the page also searches (the technical name on the phone, the
 * company on the computer), by its barcode — the whole code, as a scanner types it, or four or more of its
 * digits, as a cashier reads them off the label — and by the words of its note ("Rack 3", "store room").
 * Before, a scanned code typed into the search found nothing: the box looked only at names.
 */
object ProductFind {

    /** The fewest digits that find a barcode by a part of it: fewer would match half the shelf. */
    const val MIN_CODE_DIGITS = 4

    fun matches(name: String, secondary: String?, barcode: String?, note: String?, query: String): Boolean {
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
        return !note.isNullOrBlank() && NameSearch.matches(note, null, low)
    }

    /** The one product a scanned or typed code names exactly, or null when none or more than one carry it. */
    fun byCode(products: List<Product>, code: String): Product? {
        val q = code.trim()
        if (q.isEmpty()) return null
        return products.filter { !it.isDeleted && it.barcode?.trim()?.equals(q, ignoreCase = true) == true }.singleOrNull()
    }
}
