package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The keys a backup's big tables point by ([BigKeys], 17 Nov 2026, L001): the orphan count and the pairing rule a
 * file read a page at a time shares with the list path. Each rule is held one hole at a time, and against the list
 * path's own formula as it was written before (counted row by row) on random files.
 */
class BigKeysTest {

    private val parties = setOf(1L, 2L)
    private val products = setOf(10L)
    private val billLines = setOf(100L)

    private fun whole() = BigKeys().apply {
        entry(5, 1, 10, 100); entry(6, 2, null, null)
        entryItem(5, 10, 100); entryItem(6, null, null)
        sale(50); saleLine(50); saleLine(50)
    }

    @Test
    fun `a whole file has no holes`() = assertEquals(0, whole().orphans(parties, products, billLines))

    @Test
    fun `each kind of hole is counted once per row`() {
        val cases = listOf<(BigKeys) -> Unit>(
            { it.entry(7, 3, null, null) },          // an entry's party the file lacks
            { it.entry(8, 1, 11, null) },            // an entry's product
            { it.entry(9, 1, null, 101) },           // an entry's bill line
            { it.entryItem(70, null, null) },        // a goods line's entry
            { it.entryItem(5, 12, null) },           // a goods line's product
            { it.entryItem(5, null, 102) },          // a goods line's bill line
            { it.saleLine(51) }                      // a ticket's line or payment with no ticket
        )
        for ((i, hole) in cases.withIndex()) {
            val k = whole().also(hole)
            assertEquals("case $i", 1, k.orphans(parties, products, billLines))
            hole(k)
            assertEquals("case $i twice", 2, k.orphans(parties, products, billLines))
        }
    }

    @Test
    fun `a pairing is kept only when its other half is in the file`() {
        val k = whole()
        assertEquals(6L, k.pairKept(6))
        assertNull(k.pairKept(99))
        assertNull(k.pairKept(null))
        assertTrue(k.hasEntry(5))
        assertFalse(k.hasEntry(4))
    }

    @Test
    fun `entries taken out of order are found`() {
        val k = BigKeys()
        for (id in listOf(9L, 3L, 7L, 1L)) k.entry(id, 1, null, null)
        assertTrue(listOf(1L, 3L, 7L, 9L).all { k.hasEntry(it) })
        assertFalse(k.hasEntry(2))
        assertEquals(4, k.entries)
        // More than the first block's room.
        val big = BigKeys()
        for (id in 5000L downTo 1L) big.entry(id, 1, null, null)
        assertTrue(big.hasEntry(1) && big.hasEntry(2500) && big.hasEntry(5000) && !big.hasEntry(5001))
    }

    private data class E(val id: Long, val partyId: Long, val productId: Long?, val billItemId: Long?, val paired: Long?)
    private data class I(val entryId: Long, val productId: Long?, val billItemId: Long?)

    @Test
    fun `the keys judge random files as the list formula did`() {
        val rnd = Random(313)
        repeat(200) { round ->
            val partyIds = (1L..rnd.nextLong(1, 6)).toSet()
            val productIds = (10L..rnd.nextLong(10, 14)).toSet()
            val billItemIds = (100L..rnd.nextLong(100, 103)).toSet()
            fun maybe(range: LongRange) = if (rnd.nextBoolean()) null else rnd.nextLong(range.first, range.last + 1)
            val entries = List(rnd.nextInt(0, 30)) { i ->
                E(rnd.nextLong(1, 60) * 2 + i, rnd.nextLong(1, 8), maybe(9L..15L), maybe(99L..104L), maybe(1L..140L))
            }.distinctBy { it.id }
            val items = List(rnd.nextInt(0, 30)) { I(rnd.nextLong(1, 140), maybe(9L..15L), maybe(99L..104L)) }
            val sales = List(rnd.nextInt(0, 5)) { rnd.nextLong(1, 10) }.toSet()
            val lines = List(rnd.nextInt(0, 12)) { rnd.nextLong(1, 12) }
            val k = BigKeys().apply {
                for (e in entries) entry(e.id, e.partyId, e.productId, e.billItemId)
                for (i in items) entryItem(i.entryId, i.productId, i.billItemId)
                for (s in sales) sale(s)
                for (l in lines) saleLine(l)
            }
            // The list path's formula before 17 Nov 2026, row by row.
            val entryIds = entries.map { it.id }.toSet()
            val expected = lines.count { it !in sales } +
                entries.count { it.productId != null && it.productId !in productIds } +
                items.count { it.entryId !in entryIds } +
                items.count { it.productId != null && it.productId !in productIds } +
                items.count { it.billItemId != null && it.billItemId !in billItemIds } +
                entries.count { it.billItemId != null && it.billItemId !in billItemIds } +
                entries.count { it.partyId !in partyIds }
            assertEquals("round $round", expected, k.orphans(partyIds, productIds, billItemIds))
            for (e in entries) assertEquals("round $round pair", e.paired?.takeIf { it in entryIds }, k.pairKept(e.paired))
        }
    }
}
