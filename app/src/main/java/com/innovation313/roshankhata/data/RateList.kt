package com.innovation313.roshankhata.data

import java.util.Locale

/**
 * The shop's rate list: its own selling prices, as a picture to send on
 * WhatsApp ("this week's rates").
 *
 * Only the two SELLING prices a product already carries go out — the cash
 * price and the credit price from Products → Edit details. The purchase price
 * is never on this list and there is no field here that could put it there:
 * a rate list is handed to customers, and what the shop paid is not theirs
 * to read.
 *
 * Nothing is written to the database. Which products the owner left out is
 * a screen preference that RateListActivity keeps itself, per shop, in shared
 * preferences — so this works the same on the helper's read-only phone.
 *
 * Plain Kotlin, no Android: the picture is drawn by ui/RateListImage, and
 * every decision about WHAT goes on it lives here, where it is unit-tested.
 */
object RateList {

    data class Row(
        val productId: Long,
        val name: String,
        val company: String?,
        val unit: String?,
        val cash: Double?,
        val credit: Double?
    )

    /** A price worth printing: present and above zero. */
    private fun priced(v: Double?): Double? = v?.takeIf { it > 0.0 }

    private fun companyKey(c: String?): String? =
        c?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)

    /** True when the product has at least one selling price to show. */
    fun hasPrice(p: Product): Boolean = priced(p.salePrice) != null || priced(p.creditPrice) != null

    /** Products that cannot go on a rate list because neither price is set. */
    fun unpricedCount(products: List<Product>): Int =
        products.count { !it.isDeleted && !hasPrice(it) }

    /**
     * The companies among the priced products, each once, in the spelling met
     * first, sorted for the chip row. "Engro" and "engro " are one company.
     */
    fun companies(products: List<Product>): List<String> {
        val seen = LinkedHashMap<String, String>()
        products.filter { !it.isDeleted && hasPrice(it) }.forEach { p ->
            val key = companyKey(p.company) ?: return@forEach
            seen.getOrPut(key) { p.company!!.trim() }
        }
        return seen.values.sortedBy { it.lowercase(Locale.ROOT) }
    }

    /**
     * The products the screen offers, in name order: priced, not deleted,
     * and of [company] when one is chosen (null = every company).
     */
    fun candidates(products: List<Product>, company: String?): List<Product> {
        val want = companyKey(company)
        return products
            .filter { !it.isDeleted && hasPrice(it) }
            .filter { want == null || companyKey(it.company) == want }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    /** The rows that go on the picture: [candidates] minus what the owner unticked. */
    fun rows(products: List<Product>, company: String?, excluded: Set<Long>): List<Row> =
        candidates(products, company)
            .filter { it.id !in excluded }
            .map {
                Row(
                    productId = it.id,
                    name = it.name.trim(),
                    company = it.company?.trim()?.takeIf { c -> c.isNotEmpty() },
                    unit = it.defaultUnit?.trim()?.takeIf { u -> u.isNotEmpty() },
                    cash = priced(it.salePrice),
                    credit = priced(it.creditPrice)
                )
            }

    /**
     * Which price columns the picture carries.
     *
     * The owner's switch decides first. A column the owner left on still
     * drops out when NOT ONE row has that price: a whole column of dashes
     * says nothing. When both would drop (every row priced in the column the
     * owner switched off), the column that has prices is kept — a list with
     * no prices on it is not a rate list.
     */
    data class Columns(val cash: Boolean, val credit: Boolean)

    fun columns(rows: List<Row>, wantCash: Boolean, wantCredit: Boolean): Columns {
        val anyCash = rows.any { it.cash != null }
        val anyCredit = rows.any { it.credit != null }
        var cash = wantCash && anyCash
        var credit = wantCredit && anyCredit
        if (!cash && !credit) {
            cash = anyCash
            credit = !anyCash && anyCredit
        }
        return Columns(cash, credit)
    }

    /**
     * Rows that carry no price in any column being shown are left off — a
     * product with only a credit price has nothing to say on a cash-only list.
     */
    fun visible(rows: List<Row>, columns: Columns): List<Row> =
        rows.filter { (columns.cash && it.cash != null) || (columns.credit && it.credit != null) }

    /**
     * Split rows into pages: as many whole rows as fit in [capacity] pixels,
     * in order, never splitting a row. A single row taller than a page (a
     * name long enough to wrap many times at the largest text) gets a page of
     * its own rather than being cut.
     *
     * @return index ranges into the row list, one per page; empty for no rows.
     */
    fun paginate(heights: List<Int>, capacity: Int): List<IntRange> {
        require(capacity > 0) { "capacity must be positive" }
        val pages = ArrayList<IntRange>()
        var start = 0
        var used = 0
        heights.forEachIndexed { i, h ->
            if (i > start && used + h > capacity) {
                pages += start until i
                start = i
                used = 0
            }
            used += h
        }
        if (start < heights.size) pages += start until heights.size
        return pages
    }
}
