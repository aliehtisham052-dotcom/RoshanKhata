package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.PartyWhen
import com.innovation313.roshankhata.data.PartyWithBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Khata list's rule on the computer is the phone's (KhataActivity.rank):
 * days, side, type, then the chosen order. Made-up parties only.
 */
class KhataRulesTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = System.currentTimeMillis()

    private val book = listOf(
        PartyWithBalance(1, "Bashir", null, true, null, 500.0, now - 60_000),
        PartyWithBalance(2, "Aslam", null, true, null, 0.0, now - 3 * day),
        PartyWithBalance(3, "Seeds Co", null, false, null, -900.0, now - 40 * day),
        PartyWithBalance(4, "Zahid", null, true, null, 1200.0, now - 2 * day)
    )

    @Test
    fun `side and type narrow as on the phone`() {
        assertEquals(listOf(1L, 4L), book.shownBy("", PartySide.TO_GET, PartyType.ALL, PartySort.RECENT).map { it.id })
        assertEquals(listOf(3L), book.shownBy("", PartySide.TO_GIVE, PartyType.SUPPLIERS, PartySort.RECENT).map { it.id })
        assertEquals(listOf(2L), book.shownBy("", PartySide.SETTLED, PartyType.CUSTOMERS, PartySort.RECENT).map { it.id })
    }

    @Test
    fun `the five orders`() {
        assertEquals(listOf(1L, 4L, 2L, 3L), book.shownBy("", PartySide.ALL, PartyType.ALL, PartySort.RECENT).map { it.id })
        assertEquals(listOf(2L, 1L, 3L, 4L), book.shownBy("", PartySide.ALL, PartyType.ALL, PartySort.NAME_AZ).map { it.id })
        assertEquals(listOf(4L, 1L, 2L, 3L), book.shownBy("", PartySide.ALL, PartyType.ALL, PartySort.OWES_MOST).map { it.id })
        assertEquals(listOf(3L, 2L, 1L, 4L), book.shownBy("", PartySide.ALL, PartyType.ALL, PartySort.I_OWE_MOST).map { it.id })
    }

    @Test
    fun `today keeps only those dealt with today`() {
        val today = PartyWhen.TODAY.window(now)!!
        assertTrue(now - 60_000 in today || today.first > now - 60_000) // a minute ago may be yesterday just after midnight
        assertNull(PartyWhen.ALL.window(now))
        val month = book.shownBy("", PartySide.ALL, PartyType.ALL, PartySort.RECENT, PartyWhen.THIS_MONTH.window(now)).map { it.id }
        assertTrue("a party 40 days back is not this month", 3L !in month)
    }
}
