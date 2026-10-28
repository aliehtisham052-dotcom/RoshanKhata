package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.SaleStatus
import com.innovation313.roshankhata.data.SaleSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The counter's list of every ticket of 60 days (25 Oct 2026, the audit: a
 * return or void reached only the last 8 tickets on Windows, 20 of 60 days
 * on the phone). Found by number or customer. Made-up names.
 */
class TicketSearchTest {

    private fun sale(id: Long, number: String, customer: String?) =
        SaleSummary(id, number, customer, 1_000L * id, 500.0, 500.0, SaleStatus.DONE, 1)

    private val rows = listOf(sale(3, "S-000103-A", "Aslam Kareem"), sale(2, "S-000102-A", null), sale(1, "S-000013-B", "Bilal"))

    @Test
    fun `a blank search shows every ticket of the window`() {
        assertEquals(rows, ticketsMatching(rows, "  "))
        assertEquals(60L, SALE_DAYS)
    }

    @Test
    fun `a ticket is found by its number or its customer, in any case`() {
        assertEquals(listOf(3L), ticketsMatching(rows, "103").map { it.id })
        assertEquals(listOf(1L), ticketsMatching(rows, "s-000013").map { it.id })
        assertEquals(listOf(3L), ticketsMatching(rows, "aslam").map { it.id })
        assertEquals(emptyList<Long>(), ticketsMatching(rows, "Kashif").map { it.id })
    }

    @Test
    fun `Urdu and Persian digits typed in the box still find the number`() {
        assertEquals(listOf(3L), ticketsMatching(rows, "١٠٣").map { it.id })
        assertEquals(listOf(3L), ticketsMatching(rows, "۱۰۳").map { it.id })
    }
}
