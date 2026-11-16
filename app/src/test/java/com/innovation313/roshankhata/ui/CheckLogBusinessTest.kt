package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Each row of Hudhud's check list says which shop it was asked in (16 Nov 2026, audit S-06): one file holds every shop's
 * questions, so a deleted shop's rows go with it, and an older list (seven fields, no business) still reads and marks.
 * Made-up questions.
 */
class CheckLogBusinessTest {

    private fun add(lines: List<String>, q: String, business: Long?) =
        CheckLog.add(lines, "2026-11-16 09:00", "ur", q, "balance: [1]", "Ali Raza", business).first

    @Test
    fun aRowSaysItsShop() {
        val lines = add(emptyList(), "Ali Raza ka balance", 2L)
        assertEquals(CheckLog.HEADER, lines.first())
        assertEquals(2L, CheckLog.businessOf(lines.last()))
        assertEquals(CheckLog.FIELDS, lines.last().split('\t').size)
        assertNull(CheckLog.businessOf(add(emptyList(), "q", null).last()))
    }

    @Test
    fun aDeletedShopTakesOnlyItsOwnRows() {
        var lines = add(emptyList(), "q1", 1L)
        lines = add(lines, "q2", 2L)
        lines = add(lines, "q3", 2L)
        lines = add(lines, "q4", null)
        val kept = CheckLog.withoutBusiness(lines, 2L)
        assertEquals(listOf("1", "4"), kept.drop(1).map { it.substringBefore('\t') })
        assertEquals(CheckLog.HEADER, kept.first())
        // A shop with no rows changes nothing (the same list, not rewritten).
        assertSame(lines, CheckLog.withoutBusiness(lines, 9L))
        // A mark keeps the row's shop.
        val marked = CheckLog.mark(lines, 2, right = false)
        assertEquals(2L, CheckLog.businessOf(marked[2]))
        assertEquals(CheckLog.Counts(asked = 4, right = 0, wrong = 1), CheckLog.counts(marked))
    }

    @Test
    fun anOlderListStillReadsAndMarks() {
        val old = listOf(
            "no\ttime\tlanguage\tquestion\ttaken_as\tanswer\tmark",
            "1\t2026-11-14 09:00\tur\taaj ki sale\tfigure: SALES_TODAY\tSale\tright",
            "2\t2026-11-14 09:01\tur\tAli ka number\tperson: PHONE [1]\tAli\t"
        )
        assertEquals(CheckLog.Counts(asked = 2, right = 1, wrong = 0), CheckLog.counts(old))
        val marked = CheckLog.mark(old, 2, right = true)
        assertEquals(CheckLog.Counts(asked = 2, right = 2, wrong = 0), CheckLog.counts(marked))
        assertNull(CheckLog.businessOf(marked[2]))
        // The next row goes on from the old numbers under the new header.
        val (more, next) = CheckLog.add(old, "t", "en", "q", "x", "y", 1L)
        assertEquals(3, next)
        assertEquals(CheckLog.HEADER, more.first())
        assertEquals(3, CheckLog.counts(more).asked)
        // Old rows name no shop, so a delete keeps them.
        assertEquals(2, CheckLog.counts(CheckLog.withoutBusiness(more, 1L)).asked)
    }
}
