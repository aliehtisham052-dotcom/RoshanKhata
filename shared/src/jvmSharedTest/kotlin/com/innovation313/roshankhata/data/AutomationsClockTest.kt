package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** When each automation may speak: its hour reached, once a day (the month's once a month), only when on. */
class AutomationsClockTest {

    private val tz = TimeZone.getTimeZone("Asia/Karachi")

    private class Prefs(var on: Boolean = true, var hour: Int = 9) : Automations.Settings {
        val said = mutableMapOf<Automations.Kind, Long>()
        override fun isOn(kind: Automations.Kind) = on
        override fun hour(kind: Automations.Kind) = hour
        override fun lastSaid(kind: Automations.Kind) = said[kind]
        override fun setSaid(kind: Automations.Kind, stamp: Long) { said[kind] = stamp }
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test
    fun `not before its hour, then once that day`() {
        val p = Prefs(hour = 9)
        val k = Automations.Kind.COLLECT
        assertFalse(Automations.due(k, p, at(2026, 10, 31, 8, 59), tz))
        assertTrue(Automations.due(k, p, at(2026, 10, 31, 9, 0), tz))
        p.setSaid(k, Automations.stamp(k, at(2026, 10, 31, 9, 0), tz))
        assertFalse(Automations.due(k, p, at(2026, 10, 31, 22, 0), tz))
        assertTrue(Automations.due(k, p, at(2026, 11, 1, 9, 30), tz))
    }

    @Test
    fun `a phone asleep through the hour says it when it wakes, late but once`() {
        val p = Prefs(hour = 9)
        val k = Automations.Kind.DAY_END
        assertTrue(Automations.due(k, p, at(2026, 10, 31, 23, 50), tz))
    }

    @Test
    fun `switched off never speaks`() {
        assertFalse(Automations.due(Automations.Kind.REORDER, Prefs(on = false, hour = 0), at(2026, 10, 31, 12), tz))
    }

    @Test
    fun `the month's report once a calendar month`() {
        val p = Prefs(hour = 9)
        val k = Automations.Kind.MONTH
        val first = at(2026, 11, 1, 9, 5)
        assertTrue(Automations.due(k, p, first, tz))
        p.setSaid(k, Automations.stamp(k, first, tz))
        assertFalse(Automations.due(k, p, at(2026, 11, 2, 10), tz))
        assertFalse(Automations.due(k, p, at(2026, 11, 30, 23), tz))
        assertTrue(Automations.due(k, p, at(2026, 12, 1, 9), tz))
    }

    @Test
    fun `one order goes to the supplier who carries most, unknown suppliers only when none is known`() {
        fun l(id: Long, s: Long?) = Automations.ReorderLine(id, "P$id", 1.0, null, s)
        val (sup, lines) = Automations.firstOrder(listOf(l(1, 7), l(2, 9), l(3, 9), l(4, null)))
        assertEquals(9L, sup)
        assertEquals(listOf(2L, 3L), lines.map { it.productId })
        val (none, all) = Automations.firstOrder(listOf(l(1, null), l(2, null)))
        assertEquals(null, none)
        assertEquals(2, all.size)
    }

    @Test
    fun `last month runs from its first millisecond to its last`() {
        val (from, to) = Automations.lastMonth(at(2026, 11, 1, 9), tz)
        assertEquals(at(2026, 10, 1, 0), from)
        assertEquals(at(2026, 11, 1, 0) - 1, to)
        val (jFrom, jTo) = Automations.lastMonth(at(2027, 1, 15, 12), tz)
        assertEquals(at(2026, 12, 1, 0), jFrom)
        assertEquals(at(2027, 1, 1, 0) - 1, jTo)
    }
}
