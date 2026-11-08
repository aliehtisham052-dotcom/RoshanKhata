package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The counter's ticket and return lists are searched alike on the phone and on Windows (M69). Made-up names. */
class SaleSearchTest {
    private fun sale(id: Long, number: String, who: String?) = SaleSummary(id, number, who, id, 10.0, 10.0, SaleStatus.DONE, 1)
    private fun ret(id: Long, number: String, sale: String?, who: String?) = ReturnSummary(id, number, sale, who, id, 5.0, "cash", 5.0, "done")

    @Test
    fun ticketsByNumberOrCustomer() {
        val rows = listOf(sale(1, "S-000101-A", "Aslam"), sale(2, "S-000102-A", null))
        assertEquals(rows, SaleSearch.matching(rows, " "))
        assertEquals(listOf(1L), SaleSearch.matching(rows, "aslam").map { it.id })
        assertEquals(listOf(2L), SaleSearch.matching(rows, "١٠٢").map { it.id })
    }

    @Test
    fun returnsByNumberTicketOrCustomer() {
        val rows = listOf(ret(1, "R-000001-A", "S-000101-A", "Aslam"), ret(2, "R-000002-A", null, null))
        assertEquals(listOf(1L), SaleSearch.matchingReturns(rows, "s-000101").map { it.id })
        assertEquals(listOf(2L), SaleSearch.matchingReturns(rows, "r-000002").map { it.id })
    }
}
