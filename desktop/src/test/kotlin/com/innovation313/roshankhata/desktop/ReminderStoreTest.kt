package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.ReminderLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/**
 * The computer's reminder ladder (Marhala 4, 25 Oct 2026): what it counts is kept per business beside the
 * books, read back by the next opening, and the next reminder climbs a step until the customer pays.
 */
class ReminderStoreTest {

    @Test
    fun `reminders opened are counted per business and kept across openings`() {
        val home = Files.createTempDirectory("rk-reminders").toFile()
        try {
            val day = 24L * 60 * 60 * 1000
            val store = ReminderStore.forBusiness(home, 1L)
            assertNull(store.state(7L))
            store.record(7L, 5_000.0, now = 100 * day)
            store.record(7L, 5_000.0, now = 101 * day)
            // Opened again (the app restarted): two reminders, so the next is the serious one.
            val again = ReminderStore.forBusiness(home, 1L)
            assertEquals(2, again.state(7L)!!.count)
            assertEquals(ReminderLadder.SERIOUS, ReminderLadder.step(again.state(7L), 5_000.0, 102 * day))
            // A payment since starts the chase again, gently.
            assertEquals(ReminderLadder.GENTLE, ReminderLadder.step(again.state(7L), 3_000.0, 102 * day))
            // Another business keeps its own.
            assertNull(ReminderStore.forBusiness(home, 2L).state(7L))
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `a customer reminded today on this computer is known to be reminded today`() {
        // Follow-up's "to remind today" never went down on Windows (25 Oct 2026, the audit).
        val home = Files.createTempDirectory("rk-reminded").toFile()
        try {
            val utc = java.util.TimeZone.getTimeZone("UTC")
            val day = 24L * 60 * 60 * 1000
            val store = ReminderStore.forBusiness(home, 1L)
            store.record(7L, 5_000.0, now = 100 * day + 9 * 3_600_000L)
            store.record(8L, 2_000.0, now = 99 * day + 20 * 3_600_000L)
            assertEquals(setOf(7L), store.openedToday(now = 100 * day + 18 * 3_600_000L, tz = utc))
            assertEquals(emptySet<Long>(), store.openedToday(now = 101 * day, tz = utc))
            // Kept across openings, as the ladder is.
            assertEquals(setOf(7L), ReminderStore.forBusiness(home, 1L).openedToday(now = 100 * day + 23 * 3_600_000L, tz = utc))
        } finally {
            home.deleteRecursively()
        }
    }
}
