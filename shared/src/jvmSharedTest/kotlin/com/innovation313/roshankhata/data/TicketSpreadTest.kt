package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

/**
 * BC-252 (24 Nov 2026, money, importance 10): one ticket's money is shared over its lines to the coin, so the lines of
 * a ticket add up to the ticket's own total. Made-up figures, in the three kinds of currency (2, 3 and no decimals).
 */
class TicketSpreadTest {

    private fun <T> withSign(sign: String, body: () -> T): T {
        val before = Currency.symbol
        Currency.symbol = sign
        try { return body() } finally { Currency.symbol = before }
    }

    @Test fun `a hundred off three equal lines is 966 67 966 67 and 966 66 and adds up`() {
        // 3,000 of goods less 100 = 2,900.00 = 290,000 paisa over three lines worth 100,000 each.
        val coins = TicketSpread.split(290_000, listOf(100_000, 100_000, 100_000))
        assertEquals(listOf(96_667L, 96_667L, 96_666L), coins)
        assertEquals(290_000L, coins.sum())
        // What the old per-line rounding gave: three lines of 966.67 = 2,900.01, a paisa more than the ticket.
        assertEquals(290_001L, 3 * Money.minor(966.67, 2))
    }

    @Test fun `the fractions decide, the first line wins a tie, and a line worth nothing gets nothing`() {
        // 10 coins over weights 1:2:3 -> exact 1.67, 3.33, 5: the whole coins 1, 3, 5 leave one, to the largest fraction (.67)
        assertEquals(listOf(2L, 3L, 5L), TicketSpread.split(10, listOf(1, 2, 3)))
        // a tie in fractions goes to the earlier line
        assertEquals(listOf(1L, 0L), TicketSpread.split(1, listOf(1, 1)))
        assertEquals(listOf(0L, 1L), TicketSpread.split(1, listOf(0, 1)))
        assertEquals(listOf(0L, 0L, 5L), TicketSpread.split(5, listOf(0, 0, 7)))
        assertEquals(listOf(0L, 0L), TicketSpread.split(0, listOf(3, 4)))
    }

    @Test fun `the spare coin goes to the largest fraction wherever that line stands`() {
        // 10 over 3:2:1 -> 5, 3.33, 1.67: whole coins 5, 3, 1 and the spare one to the LAST line (.67), not the first
        assertEquals(listOf(5L, 3L, 2L), TicketSpread.split(10, listOf(3, 2, 1)))
        // 10 over 1:2:4:1 -> 1.25, 2.5, 5, 1.25: the spare coin to the .5, the middle of the ticket
        assertEquals(listOf(1L, 3L, 5L, 1L), TicketSpread.split(10, listOf(1, 2, 4, 1)))
        // 4 over 2:3:3 -> 1, 1.5, 1.5: two equal fractions, the earlier of them wins
        assertEquals(listOf(1L, 2L, 1L), TicketSpread.split(4, listOf(2, 3, 3)))
    }

    @Test fun `any ticket of any size adds up exactly, in any order of lines`() {
        val rnd = Random(2411)
        repeat(2_000) {
            val n = 1 + rnd.nextInt(9)
            val weights = List(n) { rnd.nextInt(5_000_000).toLong() + (if (it == 0) 1 else 0) }
            val total = (rnd.nextDouble() * weights.sum()).toLong()
            val cut = TicketSpread.split(total, weights)
            assertEquals(total, cut.sum())
            assertTrue(cut.all { it >= 0 })
            // a line never gets more than one coin away from its exact share
            val exact = weights.map { total.toDouble() * it / weights.sum() }
            cut.indices.forEach { i -> assertTrue("line $i: ${cut[i]} vs ${exact[i]}", Math.abs(cut[i] - exact[i]) < 1.0 + 1e-6) }
            // reversing the lines reverses the cut, up to which tied line got the spare coin
            assertEquals(total, TicketSpread.split(total, weights.reversed()).sum())
        }
    }

    @Test fun `a very big wholesaler ticket does not overflow`() {
        // 9,999,999,999,999.99 of goods, in paisa 999,999,999,999,999 — times a weight of the same size is past a Long.
        val big = 999_999_999_999_999L
        val cut = TicketSpread.split(big - 1, listOf(big / 3, big / 3, big - 2 * (big / 3)))
        assertEquals(big - 1, cut.sum())
    }

    @Test fun `bad input is refused, or means nothing to share`() {
        assertNull(TicketSpread.shares(100, emptyList()))
        assertNull(TicketSpread.shares(100, listOf(0, 0)))
        assertNull(TicketSpread.shares(100, listOf(5, -1)))
        assertNull(TicketSpread.shares(-1, listOf(5)))
        assertEquals(listOf(100L), TicketSpread.shares(100, listOf(5)))
        try { TicketSpread.split(-1, listOf(1)); fail() } catch (_: IllegalArgumentException) {}
        try { TicketSpread.split(1, listOf(0)); fail() } catch (_: IllegalArgumentException) {}
        try { TicketSpread.split(1, listOf(2, -1)); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun `the coin is the shop's own, two decimals, three or none`() {
        // Rs: 100 off 3 x 1,000.00 ; KWD: 1.000 off 3 x 10.000 ; Rp: 100 off 3 x 1,000
        for ((sign, price, off) in listOf(Triple("Rs", 1000.0, 100.0), Triple("KWD", 10.0, 1.0), Triple("Rp", 1000.0, 100.0))) withSign(sign) {
            val d = Currency.decimals()
            val worth = List(3) { Money.minor(price, d) }
            val taxable = worth.sum() - Money.minor(off, d)
            val shares = TicketSpread.shares(taxable, worth)!!.map { TicketSpread.figure(it, d) }
            val total = Money.round(3 * price - off, d)
            assertEquals("$sign: the lines add up to the ticket", Money.minor(total, d), shares.sumOf { Money.minor(it, d) })
            // the old way: each line's share rounded alone
            val perLine = Money.round(price * (1.0 - off / (3 * price)), d)
            assertFalse("$sign: rounding each line alone misses the ticket by a coin", Money.same(3 * perLine, total, d))
            assertTrue("$sign: no two lines differ by more than one coin", shares.maxOrNull()!! - shares.minOrNull()!! <= 1.0 / Math.pow(10.0, d.toDouble()) + 1e-9)
        }
    }

    @Test fun `figure turns coins back into the shop's amount`() {
        assertEquals(966.67, TicketSpread.figure(96_667, 2), 0.0)
        assertEquals(9.667, TicketSpread.figure(9_667, 3), 0.0)
        assertEquals(967.0, TicketSpread.figure(967, 0), 0.0)
        assertEquals(-0.01, TicketSpread.figure(-1, 2), 0.0)
    }

    @Test fun `a khata entry under its goods is cut, over its goods keeps each worth and the exact rest`() {
        // three goods worth 100.00 each, an amount of 200.00: a 100 discount cut 66.67 66.67 66.66
        val under = TicketSpread.cut(20_000, listOf(10_000, 10_000, 10_000))
        assertTrue(under.discounted)
        assertEquals(listOf(6_667L, 6_667L, 6_666L), under.shares)
        assertEquals(0L, under.rest)
        assertEquals(20_000L, under.shares.sum() + under.rest)
        // an amount of 350.55 over goods worth 300.00: every good keeps its worth, 50.55 is the line of no item
        val over = TicketSpread.cut(35_055, listOf(10_000, 10_000, 10_000))
        assertFalse(over.discounted)
        assertEquals(listOf(10_000L, 10_000L, 10_000L), over.shares)
        assertEquals(5_055L, over.rest)
        assertEquals(35_055L, over.shares.sum() + over.rest)
        // an amount equal to the goods: nothing to cut, nothing over
        val equal = TicketSpread.cut(30_000, listOf(10_000, 10_000, 10_000))
        assertFalse(equal.discounted)
        assertEquals(0L, equal.rest)
        // no priced goods at all: the whole amount is the rest
        val none = TicketSpread.cut(1_234, emptyList())
        assertFalse(none.discounted)
        assertEquals(1_234L, none.rest)
        // an amount of nothing shares nothing
        val zero = TicketSpread.cut(0, listOf(10_000, 10_000))
        assertTrue(zero.discounted)
        assertEquals(listOf(0L, 0L), zero.shares)
    }
}
