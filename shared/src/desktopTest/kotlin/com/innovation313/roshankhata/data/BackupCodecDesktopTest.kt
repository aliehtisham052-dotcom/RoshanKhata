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

    private val v7 = """
        {
          "format": "RoshanKhata",
          "version": 7,
          "exportedAt": 1760770000000,
          "businessName": "Bhatti Traders",
          "cheques": [],
          "cashbook": [{"id": 1, "amount": 500.0, "isIncome": true, "category": "Sale", "note": null, "timestamp": 1760770000000}],
          "businessProfile": {
            "businessName": "Bhatti Traders", "businessNameUrdu": null, "businessAddress": "Lappay Wali",
            "businessPhone": null, "businessEmail": null, "bankName": null, "bankAccountTitle": null,
            "bankIban": null, "bankJazzCash": null, "termsAndConditions": null, "strn": null,
            "currency": "Rs", "calendar": "hijri", "trade": null, "tradeOtherName": null,
            "tradeOtherItems": null, "photoOnStatement": true
          },
          "parties": [
            {"id": 7, "name": "Ali (علی)", "phone": "0300 1234567", "fatherName": null, "village": null,
             "isCustomer": true, "photoPath": null, "creditLimit": null, "createdAt": 1760000000000,
             "isDeleted": false, "deletedAt": null}
          ],
          "entries": [
            {"id": 11, "partyId": 7, "amount": 4598, "isGiven": true, "note": null, "entryNumber": "E-1",
             "timestamp": 1760700000000, "isQarzeHasna": false, "recovery": 0, "itemName": null,
             "quantity": null, "unit": null, "productId": null, "billItemId": null, "createdBy": null,
             "paymentMethod": null, "rateType": null, "pairedEntryId": null, "season": null,
             "isDeleted": false, "deletedAt": null}
          ],
          "entryItems": [
            {"id": 21, "entryId": 11, "lineNo": 0, "itemName": "Urea", "quantity": 2.5, "unit": "bag",
             "rate": 1839.2, "isBonus": false, "productId": null, "billItemId": null}
          ]
        }
    """.trimIndent()

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
        assertEquals(listOf(EntryItem.fromLegacy(data!!.entries.single())).filterNotNull(), data.entryItems)
        assertEquals("DAP", data.entryItems.single().itemName)
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
