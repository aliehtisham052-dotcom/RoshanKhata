package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.PartyWithBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookExportTest {
    @Test
    fun `the customer list opens right in Excel`() {
        val text = BookExport.customersCsv(
            listOf(
                PartyWithBalance(2, "Zahid, \"Pasrur\"", null, true, null, -150.5),
                PartyWithBalance(1, "علی", "0300 1234567", true, null, 2000.0)
            ),
            listOf("Name", "Phone", "Balance")
        )
        assertTrue(text.startsWith("﻿\"Name\""))
        val lines = text.removePrefix("﻿").split("\r\n")
        assertEquals("\"Zahid, \"\"Pasrur\"\"\",\"\",\"-150.50\"", lines[1])
        assertEquals("\"علی\",\"0300 1234567\",\"2000\"", lines[2])
    }

    @Test
    fun `the list says who is a customer and who a supplier`() {
        val text = BookExport.customersCsv(
            listOf(
                PartyWithBalance(1, "Aslam", null, true, null, 500.0),
                PartyWithBalance(2, "Green Fields Agro", null, false, null, -1200.0)
            ),
            listOf("Name", "Phone", "Balance", "Customer / Supplier")
        ) { if (it.isCustomer) "Customer" else "Supplier" }
        val lines = text.removePrefix("\uFEFF").split("\r\n")
        assertEquals("\"Name\",\"Phone\",\"Balance\",\"Customer / Supplier\"", lines[0])
        assertEquals("\"Aslam\",\"\",\"500\",\"Customer\"", lines[1])
        assertEquals("\"Green Fields Agro\",\"\",\"-1200\",\"Supplier\"", lines[2])
        // Brought back in, the names, phones and balances are read as before: the last column is passed over.
        val back = com.innovation313.roshankhata.data.CsvImport.parse(text)
        assertEquals(listOf("Aslam", "Green Fields Agro"), back.map { it.name })
        assertEquals(listOf(500.0, -1200.0), back.map { it.balance })
    }
}
