package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merged pages (Marhala 3, 25 Oct 2026, the audit's "pages that do each
 * other's work"): every tab belongs to one page, that page has its row in the
 * rail, and the rail is what the owner's mockup shows.
 */
class PageTabsTest {

    @Test
    fun `every place opens a page the rail names`() {
        for (s in Section.entries) {
            val host = PageTabs.hostOf(s)
            assertTrue("${s.name} opens ${host.name}, which has no row", PageTabs.inRail(host))
            assertTrue("${s.name} is not among its page's tabs", s in PageTabs.of(s))
            assertEquals("${s.name}'s page", host, PageTabs.of(s).first())
        }
    }

    @Test
    fun `no place is a tab of two pages`() {
        val tabs = Section.entries.flatMap { s -> PageTabs.of(s).filter { PageTabs.inRail(s) } }
        val twice = tabs.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("on two pages: $twice", twice.isEmpty())
    }

    @Test
    fun `the rail holds the mockup's pages and no more`() {
        val rail = Section.entries.filter { PageTabs.inRail(it) }
        // 34 places, 25 rows (before the trade hides Seasons for a shop that is not agri).
        assertEquals(34, Section.entries.size)
        assertEquals(25, rail.size)
        assertEquals(
            listOf(Section.COUNTER, Section.PRODUCTS, Section.INVENTORY, Section.ORDERS, Section.INVOICES),
            rail.filter { it.group == Group.SALES }
        )
        assertEquals(
            listOf(Section.KHATA, Section.CASHBOOK, Section.CHEQUES, Section.BILLS, Section.FOLLOWUP, Section.PLANS),
            rail.filter { it.group == Group.DAILY }
        )
        // The bell's places still open: an expiring lot shows Stock & expiry on its Expiry tab.
        assertEquals(Section.INVENTORY, PageTabs.hostOf(Section.EXPIRY))
        assertEquals(Section.FOLLOWUP, PageTabs.hostOf(Section.AGING))
        assertEquals(Section.BILLS, PageTabs.hostOf(Section.BUY_RATES))
    }
}
