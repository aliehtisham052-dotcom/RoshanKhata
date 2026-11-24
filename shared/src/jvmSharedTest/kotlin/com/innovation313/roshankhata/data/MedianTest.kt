package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.TimeZone

/**
 * BC-264 (24 Nov 2026, money): the Payment Radar floored an even count's middle two ([3, 4] gave 3) and the Follow-up rhythm
 * rounded them half up (4): one customer, two paying days. Both read [Median.of]. Invented figures.
 */
class MedianTest {

    private val day = 24L * 60 * 60 * 1000
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun `an odd count gives its middle one, in any order`() {
        assertEquals(5, Median.of(listOf(9, 1, 5)))
        assertEquals(7, Median.of(listOf(7)))
    }

    @Test fun `an even count gives the middle two half up`() {
        assertEquals(4, Median.of(listOf(3, 4)))
        assertEquals(15, Median.of(listOf(20, 10)))
        assertEquals(3, Median.of(listOf(1, 2, 4, 5)))
        assertEquals(4, Median.of(listOf(4, 3, 3, 4, 4, 3, 4, 3)))
        assertEquals(-1, Median.of(listOf(-2, -1)))
    }

    @Test fun `nothing has no median`() {
        try { Median.of(emptyList()); throw AssertionError("a median of nothing") } catch (e: IllegalArgumentException) { }
    }

    private fun gave(a: Double, t: Long, p: Long = 1) = LedgerPoint(p, t, a, true)
    private fun got(a: Double, t: Long, p: Long = 1) = LedgerPoint(p, t, a, false)

    @Test fun `the shop's normal over repaid days 3 and 4 is 4, as the habit reads them`() {
        val t0 = 1_700_000_000_000L
        val rows = (1L..8L).flatMap { c ->
            val t = t0 + c * 40 * day
            val days = if (c <= 4) 3 else 4
            listOf(gave(1_000.0, t, c), got(1_000.0, t + days * day, c))
        }
        assertEquals(4, PaymentRadar.shopTypicalDays(rows))
    }

    @Test fun `a customer's usual gap of 3 and 4 days is 4 days`() {
        val t0 = 1_700_000_000_000L
        val lines = listOf(gave(10_000.0, t0), got(100.0, t0 + 1 * day), got(100.0, t0 + 4 * day), got(100.0, t0 + 8 * day))
        val habit = PaymentHabit.of(lines, now = t0 + 9 * day, tz = utc)
        assertEquals(4, habit!!.typicalDays)
    }

    @Test fun `neither payment rule keeps a median of its own`() {
        val dir = File("shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data").takeIf { it.isDirectory }
            ?: generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data") }.first { it.isDirectory }
        for (name in listOf("PaymentRadar.kt", "PaymentHabit.kt")) {
            val src = File(dir, name).readText().replace("\r\n", "\n")
            assertTrue("$name reads Median.of", src.contains("Median.of("))
            assertTrue("$name sorts nothing for a median of its own", !Regex("""fun median\([^)]*\)[^=\n]*\{""").containsMatchIn(src))
        }
    }
}
