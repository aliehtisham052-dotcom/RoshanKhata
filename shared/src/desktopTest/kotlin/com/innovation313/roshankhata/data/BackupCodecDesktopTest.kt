package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A phone's backup, read on the computer (KMP Phase 4c). The file is the
 * format the phone writes (version 7, nulls written as null, numbers as
 * the phone prints them); the computer reads it with org.json + Gson where
 * the phone uses Android's own. BackupRoundTripTest covers the phone side.
 */
class BackupCodecDesktopTest {

    private val v7 = BackupFixtures.v7

    @Test
    fun `a phone backup reads on the computer, row for row`() {
        val (result, data) = BackupCodec.parseText(v7)
        assertTrue("$result", result is BackupCodec.ImportResult.Ok)
        data!!
        assertEquals(1, data.parties.size)
        assertEquals("Ali (علی)", data.parties[0].name)
        assertEquals("0300 1234567", data.parties[0].phone)
        assertNull(data.parties[0].village)
        val e = data.entries.single()
        assertEquals(4598.0, e.amount, 0.0)
        assertTrue(e.isGiven)
        assertNull("a null creator stays null, not the word null", e.createdBy)
        val line = data.entryItems.single()
        assertEquals("Urea", line.itemName)
        assertEquals(2.5, line.quantity!!, 0.0)
        assertEquals(1839.2, line.rate!!, 0.0)
        assertEquals("Bhatti Traders", data.businessName)
        val p = data.businessProfile!!
        assertEquals("Lappay Wali", p.businessAddress)
        assertNull(p.bankName)
        assertEquals("hijri", p.calendar)
        assertTrue(p.photoOnStatement)
        assertEquals("Bhatti Traders", BackupCodec.businessNameOf(v7))
    }

    @Test
    fun `an older file without goods lines gets them rebuilt as on the phone`() {
        val v6 = v7.replace("\"version\": 7", "\"version\": 6")
            .replace(Regex("""(?s),\s*"entryItems": \[.*?\]"""), "")
            .replace(Regex(""""itemName": null,\s*"quantity": null, "unit": null"""),
                """"itemName": "DAP", "quantity": 1, "unit": "bag"""")
        val (result, data) = BackupCodec.parseText(v6)
        assertTrue("$result", result is BackupCodec.ImportResult.Ok)
        // Each line is its own row (schema 38: its own uid), so the rule is held to everything else.
        assertEquals(listOf(EntryItem.fromLegacy(data!!.entries.single())).filterNotNull().map { it.copy(uid = null) }, data.entryItems.map { it.copy(uid = null) })
        assertEquals("DAP", data.entryItems.single().itemName)
    }

    @Test
    fun `a phone's shop profile lands in the computer's settings and back out the same`() {
        val dir = java.nio.file.Files.createTempDirectory("rk-settings").toFile()
        try {
            val file = java.io.File(dir, "shop.properties")
            val profile = BackupCodec.parseText(v7).second!!.businessProfile!!
            BackupCodec.restoreProfile(FileShopSettings(file), profile)

            // A fresh read of the file: what a restart of the app would see.
            val again = FileShopSettings(file)
            assertEquals("Bhatti Traders", again.businessName())
            assertEquals("Lappay Wali", again.businessAddress())
            assertNull(again.bankName())
            assertEquals(ShopCalendar.Kind.HIJRI, again.calendar())
            assertEquals("Rs", again.currency())
            assertNull(again.tradeKey())
            assertTrue(again.photoOnStatement())
            assertEquals(ShopCalendar.Kind.HIJRI, ShopCalendar.current)

            val out = BackupCodec.profileToJson(again)
            assertEquals("Bhatti Traders", out.getString("businessName"))
            assertTrue(out.isNull("bankName"))
            assertEquals("hijri", out.getString("calendar"))
            assertEquals("Rs", out.getString("currency"))
            assertTrue(out.getBoolean("photoOnStatement"))
        } finally {
            ShopCalendar.useForTest(ShopCalendar.Kind.GREGORIAN)
            dir.deleteRecursively()
        }
    }

    @Test
    fun `refusals are the same kinds as on the phone`() {
        fun why(text: String) = (BackupCodec.parseText(text).first as BackupCodec.ImportResult.Failed).why
        assertEquals(BackupCodec.ImportResult.Why.NOT_OURS, why("""{"format": "SomethingElse", "version": 1}"""))
        assertEquals(BackupCodec.ImportResult.Why.TOO_NEW, why(v7.replace("\"version\": 7", "\"version\": 99")))
        assertEquals(BackupCodec.ImportResult.Why.UNREADABLE, why("not json at all {"))
        assertEquals(BackupCodec.ImportResult.Why.INCOMPLETE, why(v7.replace("\"partyId\": 7", "\"partyId\": 8")))
    }
}
