package com.innovation313.roshankhata.data

/**
 * How a shop's own keys are told from every other shop's (24 Nov 2026, stage 4 "each business stays apart").
 *
 * Every shop has its own book file; what lives OUTSIDE the book — preference files that all shops share — is kept apart by
 * a key that names the shop. Business 1 carries no suffix (so a phone that only ever had one shop reads what it always
 * did); every other shop's id is written after an underscore. An id is never given out twice (Businesses' highest-id
 * mark), so a key can never be read by a later shop. Pure: the phone's [Businesses] and the tests use the same rule.
 */
object BusinessKeys {
    /** "" for Business 1, "_b<id>" for the others: the suffix of a book, a profile, a copy folder. */
    fun suffixFor(id: Long): String = if (id == 1L) "" else "_b$id"

    /** [base] made this shop's own: the bare name for Business 1, the name and the suffix for the others. */
    fun keyOf(base: String, id: Long): String = base + suffixFor(id)

    /** A key that ends "_b<id>" (never Business 1's, which has no suffix): "said_cheques_b3" for 3, not for 13 or 33. */
    fun endsWithBusinessSuffix(key: String, id: Long): Boolean = id != 1L && key.endsWith("_b$id")

    /** A key that ends "_<id>" (the poster's and the rate list's older form: "fields_3"); never Business 1's. */
    fun endsWithShopId(key: String, id: Long): Boolean = id != 1L && key.endsWith("_$id")

    /** A key that names a book FILE as its last or middle "|" field (customer links and live sync's notes). */
    fun namesBookFile(key: String, file: String): Boolean = key.endsWith("|$file") || key.contains("|$file|")

    /** The preference files that every shop shares and how each names its shop in a key. */
    private val SUFFIXED = setOf("automations", "form_drafts", "book_seal", "seal_offer", "snapshots")
    private val BY_ID = setOf("poster", "rate_list")
    private val BY_FILE = setOf("customer_link", "live_sync")

    /** Every shared preference file [owns] looks through: a new shared file with per-shop keys is added here, with a test. */
    val SHARED_FILES: Set<String> = SUFFIXED + BY_ID + BY_FILE

    /**
     * Is [key] of the preference file [file] a key of shop [id] (whose book file is [bookFile])? True only for that
     * shop's own, never a neighbour's and never Business 1's — a deleted shop's leftovers are exactly the keys this finds.
     */
    fun owns(file: String, key: String, id: Long, bookFile: String): Boolean = when (file) {
        in SUFFIXED -> endsWithBusinessSuffix(key, id)
        in BY_ID -> endsWithShopId(key, id)
        in BY_FILE -> id != 1L && namesBookFile(key, bookFile)
        else -> false
    }
}
