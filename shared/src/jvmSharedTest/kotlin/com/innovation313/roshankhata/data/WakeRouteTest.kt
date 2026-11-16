package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which shop a relay's wake-up is for (L010): the one it names, else the open shop and then the shops not on
 * screen whose relay is on. Book ids are made up.
 */
class WakeRouteTest {

    private val a = "a".repeat(32)
    private val b = "b".repeat(32)
    private val c = "c".repeat(32)
    private val d = "d".repeat(32)
    private val e = "e".repeat(32)

    @Test
    fun `a message names a book only in a book's own shape`() {
        assertEquals(b, WakeRoute.named(b))
        assertEquals(b, WakeRoute.named(" " + b.uppercase() + " "))
        assertNull(WakeRoute.named(null))
        assertNull(WakeRoute.named(""))
        assertNull(WakeRoute.named("b".repeat(31)))
        assertNull(WakeRoute.named("g".repeat(32)))
        assertNull(WakeRoute.named("../" + b))
    }

    @Test
    fun `an unnamed message is the open shop's first, a named one only when it names that shop`() {
        assertTrue(WakeRoute.forOpen(null, a))
        assertTrue(WakeRoute.forOpen(a, a))
        assertFalse(WakeRoute.forOpen(b, a))
        // The open shop does not sync: nothing to round there.
        assertFalse(WakeRoute.forOpen(null, null))
        assertFalse(WakeRoute.readOthers(a, a))
        assertTrue(WakeRoute.readOthers(b, a))
        assertTrue(WakeRoute.readOthers(null, a))
    }

    @Test
    fun `a named book wakes that shop alone, and a book this phone does not keep wakes nobody`() {
        val shops = listOf(WakeRoute.Shop(2, b, true), WakeRoute.Shop(3, c, true))
        assertEquals(listOf(3L), WakeRoute.others(shops, c, a).map { it.id })
        assertEquals(emptyList<Long>(), WakeRoute.others(shops, d, a).map { it.id })
        // Named, but its owner turned the relay off there: the relay can no longer be what rang.
        assertEquals(emptyList<Long>(), WakeRoute.others(listOf(WakeRoute.Shop(2, b, false)), b, a).map { it.id })
        // Named and open: the open shop's own rounds take it, no other file is opened.
        assertEquals(emptyList<Long>(), WakeRoute.others(listOf(WakeRoute.Shop(2, a, true)), a, a).map { it.id })
    }

    @Test
    fun `an unnamed message wakes the closed shops whose relay is on, lowest first, three at most`() {
        val shops = listOf(
            WakeRoute.Shop(5, e, true), WakeRoute.Shop(2, b, true), WakeRoute.Shop(3, null, true),
            WakeRoute.Shop(4, d, false), WakeRoute.Shop(6, c, true), WakeRoute.Shop(7, "f".repeat(32), true)
        )
        assertEquals(listOf(2L, 5L, 6L), WakeRoute.others(shops, null, a).map { it.id })
        assertEquals(WakeRoute.MAX_OTHERS, WakeRoute.others(shops, null, a).size)
    }

    @Test
    fun `a book two shops hold is woken once, and never the open shop's book again`() {
        val shops = listOf(WakeRoute.Shop(4, b, true), WakeRoute.Shop(2, b, true), WakeRoute.Shop(3, a, true))
        assertEquals(listOf(2L), WakeRoute.others(shops, null, a).map { it.id })
        assertEquals(listOf(2L), WakeRoute.others(shops, b, a).map { it.id })
    }
}
