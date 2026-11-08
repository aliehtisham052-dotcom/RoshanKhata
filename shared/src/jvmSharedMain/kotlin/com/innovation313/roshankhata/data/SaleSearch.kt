package com.innovation313.roshankhata.data

/**
 * One search rule for the counter's ticket lists, on the phone and on Windows (8 Nov 2026 review, M69): a ticket
 * whose number or customer holds the typed words, any case, any digits; every ticket for a blank box.
 */
object SaleSearch {
    fun matching(rows: List<SaleSummary>, query: String): List<SaleSummary> {
        val q = Digits.toLatin(query.trim()).lowercase()
        if (q.isEmpty()) return rows
        return rows.filter { s -> s.saleNumber.lowercase().contains(q) || (s.customerName?.lowercase()?.contains(q) == true) }
    }

    /** The same for the returns list: the return's number, the ticket it came from, or the customer. */
    fun matchingReturns(rows: List<ReturnSummary>, query: String): List<ReturnSummary> {
        val q = Digits.toLatin(query.trim()).lowercase()
        if (q.isEmpty()) return rows
        return rows.filter { r ->
            r.returnNumber.lowercase().contains(q) || (r.saleNumber?.lowercase()?.contains(q) == true) ||
                (r.customerName?.lowercase()?.contains(q) == true)
        }
    }
}
