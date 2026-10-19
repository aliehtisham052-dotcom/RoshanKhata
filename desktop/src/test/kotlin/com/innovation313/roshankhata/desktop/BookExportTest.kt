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
}
