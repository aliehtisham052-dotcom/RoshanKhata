package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The money totals on Windows (25 Oct 2026, the audit): they opened on show
 * and the eye forgot its choice; the phone keeps them as dots until the owner
 * opens them, and remembers (BalancePrivacy). A customer at the counter sees
 * dots, not the shop's position.
 */
class TotalsHiddenTest {

    @Test
    fun `totals start hidden and the eye's choice survives a restart`() {
        val dir = Files.createTempDirectory("rk-totals").toFile()
        try {
            val file = dir.resolve("app.properties")
            assertTrue("hidden on a computer that never chose", AppPrefs(file).balancesHidden())
            AppPrefs(file).setBalancesHidden(false)
            assertFalse("opened stays opened", AppPrefs(file).balancesHidden())
            AppPrefs(file).setBalancesHidden(true)
            assertTrue(AppPrefs(file).balancesHidden())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the mask gives nothing of the figure away`() {
        assertFalse(TOTALS_MASK.any { it.isDigit() })
    }
}
