package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * BC-280 / BC-282 (25 Nov 2026, money and stock, importance 8): two small rules each said three times.
 *  - the number of customers who pay late (Home's line, the phone's bell row, the computer's bell row and badge) was three
 *    hand-written copies of one loop; it is [FollowUpRank.lateCount]
 *  - "the shelf is low" was the Products/stock page's own test and a second one in the bell; it is [Valuation.isLow].
 * Invented customers; plain Kotlin.
 */
class LateCountAndLowStockTest {

    private val day = 24L * 60 * 60 * 1000
    private val tz = TimeZone.getTimeZone("Asia/Karachi")
    private val base = 1_800_000_000_000L

    private fun party(id: Long, balance: Double) = PartyWithBalance(id, "P$id", null, true, null, balance)

    /** Gave [owed] on day 0, then paid 100 on each day of [paidOn]. */
    private fun lines(id: Long, owed: Double, vararg paidOn: Int): List<LedgerPoint> =
        listOf(LedgerPoint(id, base, owed, true, id * 100)) + paidOn.mapIndexed { i, d -> LedgerPoint(id, base + d * day, 100.0, false, id * 100 + 1 + i) }

    private val now = base + 60 * day

    @Test fun `a customer who pays every ten days and has been quiet thirty is counted, one who paid lately is not`() {
        val slow = lines(1, 1000.0, 10, 20, 30)            // owes 700, last paid day 30, now day 60
        val fresh = lines(2, 1000.0, 10, 20, 55)           // last paid day 55
        val parties = listOf(party(1, 700.0), party(2, 700.0))
        assertEquals(1, FollowUpRank.lateCount(parties, slow + fresh, emptyMap(), now, tz))
    }

    @Test fun `a day he named that is still ahead is not late, one that has come is`() {
        val points = lines(1, 1000.0, 10, 20, 30)
        val parties = listOf(party(1, 700.0))
        assertEquals(0, FollowUpRank.lateCount(parties, points, mapOf(1L to now + 5 * day), now, tz))
        assertEquals(1, FollowUpRank.lateCount(parties, points, mapOf(1L to now - 1 * day), now, tz))
    }

    @Test fun `only customers who owe are counted, and none without lines`() {
        val points = lines(1, 1000.0, 10, 20, 30)
        assertEquals("settled", 0, FollowUpRank.lateCount(listOf(party(1, 0.0)), points, emptyMap(), now, tz))
        assertEquals("the shop owes him", 0, FollowUpRank.lateCount(listOf(party(1, -50.0)), points, emptyMap(), now, tz))
        assertEquals("no lines, no habit", 0, FollowUpRank.lateCount(listOf(party(9, 500.0)), points, emptyMap(), now, tz))
        assertEquals(0, FollowUpRank.lateCount(emptyList(), emptyList(), emptyMap(), now, tz))
    }

    @Test fun `no screen keeps its own late loop`() {
        val root = generateSequence(java.io.File(".").absoluteFile) { it.parentFile }.first { java.io.File(it, "shared/src/jvmSharedMain").isDirectory }
        val loop = Regex("""habits\.keys\.count\s*\{""")
        val hits = listOf("app/src/main", "desktop/src/main", "shared/src/jvmSharedMain").map { java.io.File(root, it) }.filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" && f.name != "FollowUpRank.kt" }.toList() }
            .filter { loop.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals("a late-payer count of its own in: $hits", emptyList<String>(), hits)
    }

    // ---- low stock ----------------------------------------------------------------------------------------------

    @Test fun `low means known, a level set, and at or under it`() {
        assertTrue(Valuation.isLow(5.0, 5.0))
        assertTrue(Valuation.isLow(0.0, 5.0))
        assertTrue(Valuation.isLow(-2.0, 5.0))
        assertFalse(Valuation.isLow(5.5, 5.0))
        assertFalse("shelf unknown", Valuation.isLow(null, 5.0))
        assertFalse("no level set", Valuation.isLow(1.0, null))
        assertFalse(Valuation.isLow(null, null))
    }

    @Test fun `a valuation row and the bell read the one test`() {
        val row = Valuation.Row(1, "Urea", 5.0, "bag", null, 5.0)
        assertEquals(Valuation.isLow(row.onHand, row.lowStockLevel), row.isLow)
        val src = java.io.File(generateSequence(java.io.File(".").absoluteFile) { it.parentFile }.first { java.io.File(it, "shared/src/jvmSharedMain").isDirectory },
            "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/LedgerAlerts.kt").readText()
        assertTrue("the bell calls Valuation.isLow", "Valuation.isLow(" in src)
        assertFalse("and compares nothing of its own", Regex("""onHand\?\.let \{ it <= """).containsMatchIn(src))
    }
}
