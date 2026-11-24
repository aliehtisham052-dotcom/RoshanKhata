package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BC-265 (24 Nov 2026, money): Insights' "top customers" and its sales total must be one set of rows. Top customers asked
 * `p.isDeleted = 0` of the party, the total did not (nor do six other sales queries): a customer flagged deleted whose
 * entries were still live dropped out of the ranking while his sales stayed in the total. The rule chosen: the party's own
 * flag is not asked by any sales figure (binning a customer bins his entries, so his sales leave them all together). This
 * test holds the TEXT of the two queries to the same filters; the real-database test is TopCustomersAgreeDesktopTest.
 */
class TopCustomersRuleTest {

    private fun dao(): String {
        val rel = "shared/src/jvmSharedMain/kotlin/com/innovation313/roshankhata/data/KhataDao.kt"
        val f = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, rel) }.first { it.isFile }
        return f.readText().replace("\r\n", "\n")
    }

    /** The @Query text of the function [name], its string pieces joined. */
    private fun queryOf(src: String, name: String): String {
        val at = src.indexOf("suspend fun $name(")
        require(at > 0) { "no $name" }
        val q = src.lastIndexOf("@Query(", at)
        val body = src.substring(q + "@Query(".length, at)
        return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(body).joinToString("") { it.groupValues[1] }.replace(Regex("\\s+"), " ")
    }

    private val filters = listOf(
        "t.isGiven = 1", "t.isDeleted = 0", "p.isCustomer = 1", "t.isQarzeHasna = 0", "t.entryKind = 'NORMAL'",
        "t.id NOT IN (SELECT refundEntryId FROM sale_returns WHERE refundEntryId IS NOT NULL)"
    )

    @Test fun `top customers and the sales total keep the same filters`() {
        val src = dao()
        val top = queryOf(src, "topCustomersBetween")
        val total = queryOf(src, "salesTotalBetween")
        for (f in filters) {
            assertTrue("topCustomersBetween has: $f", top.contains(f))
            assertTrue("salesTotalBetween has: $f", total.contains(f))
        }
    }

    @Test fun `neither asks the party's own deleted flag`() {
        val src = dao()
        for (name in listOf("topCustomersBetween", "salesTotalBetween")) {
            assertFalse("$name asks p.isDeleted", queryOf(src, name).contains("p.isDeleted"))
        }
        assertEquals(1, Regex("""JOIN parties p ON p\.id = t\.partyId""").findAll(queryOf(src, "topCustomersBetween")).count())
    }
}
