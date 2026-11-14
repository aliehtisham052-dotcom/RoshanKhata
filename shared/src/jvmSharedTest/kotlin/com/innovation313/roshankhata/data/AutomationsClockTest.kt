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
        assertTrue(Automations.due(k, p, at(2026, 10, 31, 15, 0), tz))
    }

    @Test
    fun `nothing is said into the night unless the owner set it that late`() {
        // 3 Nov 2026 audit, M23: a phone first awake at 23:50 said the morning's notices with sound.
        assertFalse(Automations.due(Automations.Kind.COLLECT, Prefs(hour = 9), at(2026, 10, 31, 23, 50), tz))
        assertTrue("the next morning it is said again", Automations.due(Automations.Kind.COLLECT, Prefs(hour = 9), at(2026, 11, 1, 9, 10), tz))
        assertTrue("a 21:00 closing summary until midnight", Automations.due(Automations.Kind.DAY_END, Prefs(hour = 21), at(2026, 10, 31, 23, 50), tz))
        assertEquals(22, Automations.lastHour(9))
        assertEquals(24, Automations.lastHour(21))
    }

    @Test
    fun `switched off never speaks`() {
        assertFalse(Automations.due(Automations.Kind.REORDER, Prefs(on = false, hour = 0), at(2026, 10, 31, 12), tz))
    }

    @Test
    fun `the cheque reminders come at nine, once a day, never when switched off`() {
        val p = Prefs(hour = 9)
        val k = Automations.Kind.CHEQUES
        assertEquals(9, k.defaultHour)
        assertFalse(Automations.due(k, p, at(2026, 11, 14, 8, 59), tz))
        assertTrue(Automations.due(k, p, at(2026, 11, 14, 9, 0), tz))
        p.setSaid(k, Automations.stamp(k, at(2026, 11, 14, 9, 0), tz))
        assertFalse("said once that day", Automations.due(k, p, at(2026, 11, 14, 18, 0), tz))
        assertTrue("and again the next morning", Automations.due(k, p, at(2026, 11, 15, 9, 5), tz))
        assertFalse(Automations.due(k, Prefs(on = false, hour = 0), at(2026, 11, 14, 12), tz))
    }

    @Test
    fun `an automation's key is its own and the ones stored before the cheque reminders kept theirs`() {
        // Said-marks and switches are stored by key (per device): adding a kind must not move any that exist.
        assertEquals(
            listOf("collect", "cheques", "reorder", "expiry", "month", "dayend"),
            Automations.Kind.values().map { it.key }
        )
        assertEquals(Automations.Kind.values().size, Automations.Kind.values().map { it.key }.toSet().size)
    }

    /** Switches by kind: only the listed ones are on. */
    private class OnlyOn(vararg on: Automations.Kind) : Automations.Settings {
        private val set = on.toSet()
        override fun isOn(kind: Automations.Kind) = kind in set
        override fun hour(kind: Automations.Kind) = kind.defaultHour
        override fun lastSaid(kind: Automations.Kind): Long? = null
        override fun setSaid(kind: Automations.Kind, stamp: Long) = Unit
    }

    private fun alert(kind: LedgerAlerts.Kind) = LedgerAlerts.Alert(kind, "title", "sub")

    @Test
    fun `the bell rows an automation says in detail are covered only while it is on`() {
        // 14 Nov 2026: the daily sweep and the automation each said the same cheques (and the computer's note still
        // said the expiry twice). One mapping decides which rows the sweep leaves to the automation.
        assertEquals(Automations.Kind.CHEQUES, Automations.coveredBy(LedgerAlerts.Kind.CHEQUE))
        assertEquals(Automations.Kind.EXPIRY, Automations.coveredBy(LedgerAlerts.Kind.EXPIRING))
        for (k in LedgerAlerts.Kind.values().filter { it != LedgerAlerts.Kind.CHEQUE && it != LedgerAlerts.Kind.EXPIRING })
            assertEquals("$k has no automation of its own", null, Automations.coveredBy(k))

        val all = LedgerAlerts.Kind.values().map { alert(it) }
        val onlyCheques = Automations.leftToAutomations(all, OnlyOn(Automations.Kind.CHEQUES)).map { it.kind }
        assertFalse(LedgerAlerts.Kind.CHEQUE in onlyCheques)
        assertTrue("the expiry automation is off, so its row stays", LedgerAlerts.Kind.EXPIRING in onlyCheques)
        val both = Automations.leftToAutomations(all, OnlyOn(Automations.Kind.CHEQUES, Automations.Kind.EXPIRY)).map { it.kind }
        assertFalse(LedgerAlerts.Kind.CHEQUE in both)
        assertFalse(LedgerAlerts.Kind.EXPIRING in both)
        assertEquals("every other row is kept", LedgerAlerts.Kind.values().size - 2, both.size)
        assertEquals("both automations off: nothing is left out", all.size, Automations.leftToAutomations(all, OnlyOn()).size)
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
