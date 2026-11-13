package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The arithmetic of the owner's check list (HudhudCheck, 14 Nov 2026): rows, marks and the success rate. */
class CheckLogTest {

    private fun add(lines: List<String>, q: String) = CheckLog.add(lines, "2026-11-14 09:00", "ur", q, "help: intro_cheques", "Cheques")

    @Test
    fun rowsAreNumberedAfterTheHighest() {
        val (one, first) = add(emptyList(), "cheque kaise add karun")
        val (two, second) = add(one, "aaj ki sale")
        assertEquals(1, first)
        assertEquals(2, second)
        assertEquals(CheckLog.HEADER, two.first())
        assertEquals(3, two.size)
    }

    @Test
    fun aFieldNeverBreaksTheRow() {
        val (lines, _) = CheckLog.add(emptyList(), "t", "en", "a\tb\nc", "x", "y\r\nz")
        val row = lines.last().split('\t')
        assertEquals(7, row.size)
        assertEquals("a b c", row[3])
        assertEquals("y z", row[5])
    }

    @Test
    fun marksCountAndASecondTapChangesIt() {
        var (lines, _) = add(emptyList(), "q1")
        lines = add(lines, "q2").first
        lines = add(lines, "q3").first
        assertNull(CheckLog.counts(lines).percent)
        lines = CheckLog.mark(lines, 1, right = true)
        lines = CheckLog.mark(lines, 2, right = false)
        lines = CheckLog.mark(lines, 3, right = true)
        assertEquals(CheckLog.Counts(asked = 3, right = 2, wrong = 1), CheckLog.counts(lines))
        assertEquals(67, CheckLog.counts(lines).percent)
        lines = CheckLog.mark(lines, 2, right = true)
        assertEquals(100, CheckLog.counts(lines).percent)
    }

    @Test
    fun aMissingRowChangesNothing() {
        val (lines, _) = add(emptyList(), "q1")
        assertEquals(lines, CheckLog.mark(lines, 9, right = true))
    }

    @Test
    fun theOldestGoPastTheCap() {
        var lines = emptyList<String>()
        repeat(CheckLog.MAX_ROWS + 5) { lines = add(lines, "q$it").first }
        assertEquals(CheckLog.MAX_ROWS, CheckLog.counts(lines).asked)
        assertEquals((CheckLog.MAX_ROWS + 5).toString(), lines.last().substringBefore('\t'))
    }
}
