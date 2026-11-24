package com.innovation313.roshankhata.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

/**
 * BC-250 (24 Nov 2026): a deleted business's "_b<id>" keys leave app.properties, a neighbour's never do, and the window's own
 * copy of the settings cannot write them back. NOT RUN where it was written (no Gradle): CI is its first run.
 */
class BusinessPrefsForgetTest {
    @Test
    fun `forgetting business 3 takes its keys and not business 13's or business 1's`() {
        val home = Files.createTempDirectory("rk-prefs-forget").toFile()
        try {
            val prefs = AppPrefs(java.io.File(home, "app.properties"))
            prefs.setLastSavedBackup(3, 111L)
            prefs.setLastSavedBackup(13, 222L)
            prefs.setLastSavedBackup(1, 333L)
            prefs.setProp("bin_kinds_since_b3", "5")
            prefs.setProp("auto_said_cheques_b3", "6")
            prefs.setProp("auto_said_cheques", "7")
            prefs.setInvoiceFields(InvoiceFields(tax = false), 3)
            prefs.setInvoiceFields(InvoiceFields(tax = false), 13)
            val gone = prefs.forgetBusiness(3)
            // last backup, bin day, said mark, six invoice switches
            assertEquals(9, gone)
            assertNull(prefs.lastSavedBackup(3))
            assertNull(prefs.prop("bin_kinds_since_b3"))
            assertNull(prefs.prop("auto_said_cheques_b3"))
            assertEquals(222L, prefs.lastSavedBackup(13))
            assertEquals(333L, prefs.lastSavedBackup(1))
            assertEquals("7", prefs.prop("auto_said_cheques"))
            assertEquals(false, prefs.invoiceFields(13).tax)
            assertEquals("the forgetting reached the disk", null, AppPrefs(java.io.File(home, "app.properties")).lastSavedBackup(3))
        } finally { home.deleteRecursively() }
    }

    @Test
    fun `deleting a business removes its settings through the window's own copy, which then cannot write them back`() {
        val home = Files.createTempDirectory("rk-prefs-delete").toFile()
        try {
            val windowCopy = AppPrefs(java.io.File(home, "app.properties"))   // the one Main holds
            val b = Businesses(home)
            val shop = b.create("Shop Two")
            windowCopy.setLastSavedBackup(shop.id, 999L)
            windowCopy.setProp("bin_kinds_since_b${shop.id}", "1")
            b.dbFile(shop.id).writeText("x")
            b.delete(shop.id)
            assertNull(windowCopy.lastSavedBackup(shop.id))
            windowCopy.setTextSize(2)                               // an unrelated save must not bring the keys back
            val reread = AppPrefs(java.io.File(home, "app.properties"))
            assertNull(reread.lastSavedBackup(shop.id))
            assertNull(reread.prop("bin_kinds_since_b${shop.id}"))
            assertNotNull(reread.textSize())
        } finally { home.deleteRecursively() }
    }
}
