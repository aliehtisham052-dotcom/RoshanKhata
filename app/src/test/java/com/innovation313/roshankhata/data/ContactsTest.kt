package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One rule for both ways contacts arrive: the full list and Android 17's picker. */
class ContactsTest {

    @Test
    fun `blanks are dropped and one number shows once however it is written`() {
        val out = Contacts.fromRows(
            listOf("Aslam" to "0300-1234567", "Aslam Home" to "+92 300 1234567", "" to "0311", "Bilal" to null, "Rashid" to "0321 7654321"),
            existingPhones = emptyList()
        )
        assertEquals(listOf("Aslam", "Rashid"), out.map { it.name })
    }

    @Test
    fun `already on the books and in the recycle bin are marked, not offered`() {
        val out = Contacts.fromRows(
            listOf("Aslam" to "03001234567", "Bilal" to "03111111111", "Rashid" to "03217654321"),
            existingPhones = listOf("+923001234567"),
            binnedPhones = listOf("0311 1111111")
        )
        val byName = out.associateBy { it.name }
        assertTrue(byName.getValue("Aslam").alreadyAdded)
        assertTrue(byName.getValue("Bilal").inRecycleBin)
        assertFalse(byName.getValue("Rashid").alreadyAdded || byName.getValue("Rashid").inRecycleBin)
    }

    @Test
    fun `picked contacts come back in name order whatever order the picker used`() {
        val out = Contacts.fromRows(listOf("zahid" to "1111111111", "Akram" to "2222222222", "bashir" to "3333333333"), emptyList())
        assertEquals(listOf("Akram", "bashir", "zahid"), out.map { it.name })
    }
}
