package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the phone's sync notes may hold ([SyncLog]): the kind of failure and a message with nothing of
 * the book in it — no names, amounts, phone numbers or keys — and a file that stays small.
 */
class SyncLogTest {

    @Test
    fun quotedTextLongNumbersAndCodesAreTakenOut() {
        val cleaned = SyncLog.clean("For input string: \"Ali Khan\" 03001234567 key 9f8e7d6c5b4a39281706f5e4 'Sara'")!!
        assertFalse(cleaned, cleaned.contains("Ali"))
        assertFalse(cleaned, cleaned.contains("Sara"))
        assertFalse(cleaned, cleaned.contains("03001234567"))
        assertFalse(cleaned, cleaned.contains("9f8e7d6c5b4a39281706f5e4"))
        assertTrue(cleaned, cleaned.startsWith("For input string"))
    }

    @Test
    fun aStatusAPortAndTheFailuresOwnWordsStay() {
        val cleaned = SyncLog.clean("failed to connect to /192.168.1.5 (port 47313) after 10000ms: HTTP 503")!!
        assertTrue(cleaned, cleaned.contains("port 47313"))
        assertTrue(cleaned, cleaned.contains("HTTP 503"))
        assertTrue(cleaned, cleaned.contains("failed to connect"))
    }

    @Test
    fun aMessageIsOneShortLine() {
        val cleaned = SyncLog.clean("first line\nsecond line " + "x".repeat(400))!!
        assertFalse(cleaned.contains("\n"))
        assertTrue(cleaned.length <= 161)
        assertNull(SyncLog.clean(null))
        assertNull(SyncLog.clean("   "))
    }

    @Test
    fun aJsonParsersMessageIsLeftOutWhole() {
        class FakeJsonException(message: String) : Exception(message)
        val line = SyncLog.line("2026-10-23 10:00:00", "live sync, phone round", FakeJsonException("Value Ali Khan at name"))
        assertFalse(line, line.contains("Ali"))
        assertTrue(line, line.contains("FakeJsonException"))
    }

    @Test
    fun aNoteNamesTheKindItsCauseAndTheCodeLine() {
        val e = IllegalStateException("live sync is off", java.io.IOException("too big"))
        val line = SyncLog.line("2026-10-23 10:00:00", "live sync, phone round", e)
        assertTrue(line, line.startsWith("2026-10-23 10:00:00  live sync, phone round: java.lang.IllegalStateException: live sync is off"))
        assertTrue(line, line.contains("| cause: java.io.IOException: too big"))
        assertTrue(line, line.contains("| at SyncLogTest."))
        assertFalse(line.contains("\n"))
    }

    @Test
    fun aLongFileKeepsOnlyItsNewestLinesWithinTheSize() {
        val lines = (1..1000).map { "2026-10-23 10:00:00  note number $it" }
        val kept = SyncLog.trimmed(lines, 2_000)
        assertEquals("2026-10-23 10:00:00  note number 1000", kept.last())
        assertTrue(kept.sumOf { it.toByteArray().size + 1 } <= 2_000)
        assertTrue(kept.size in 2 until 1000)
    }
}
