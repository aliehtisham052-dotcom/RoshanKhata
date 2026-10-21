package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a CSV from another ledger app may become: name, phone, balance; nothing guessed. */
class CsvImportTest {

    @Test
    fun `the app's own customer export reads back exactly`() {
        val csv = "﻿\"نام\",\"فون نمبر\",\"کل بیلنس\"\r\n\"Zahid, \"\"Pasrur\"\"\",\"\",\"-150.50\"\r\n\"علی\",\"0300 1234567\",\"2000\"\r\n"
        assertEquals(
            listOf(CsvImport.Row("Zahid, \"Pasrur\"", null, -150.5), CsvImport.Row("علی", "0300 1234567", 2000.0)),
            CsvImport.parse(csv)
        )
    }

    @Test
    fun `semicolons, rupee signs and thousands separators`() {
        val csv = "Customer Name;Mobile;Due Amount\nBilal;03001112223;Rs 1,200.50\nAkram;;(500)\n"
        assertEquals(
            listOf(CsvImport.Row("Bilal", "03001112223", 1200.5), CsvImport.Row("Akram", null, -500.0)),
            CsvImport.parse(csv)
        )
    }

    @Test
    fun `no header row - the phone and the balance are found by what they hold`() {
        val csv = "Bilal,0300-1112223,700\nAkram,0321 4445556,-50\n"
        val rows = CsvImport.parse(csv)
        assertEquals(listOf("Bilal", "Akram"), rows.map { it.name })
        assertEquals(listOf("0300-1112223", "0321 4445556"), rows.map { it.phone })
        assertEquals(listOf(700.0, -50.0), rows.map { it.balance })
    }

    @Test
    fun `columns in another order, tab separated, Urdu digits`() {
        val csv = "Balance\tName\tPhone\n۱۲۰۰\tنواز\t\n"
        assertEquals(listOf(CsvImport.Row("نواز", null, 1200.0)), CsvImport.parse(csv))
    }

    @Test
    fun `rows without a name are left out, an unreadable balance is zero`() {
        val csv = "Name,Phone,Balance\n,0300,500\nKashif,,n/a\n"
        assertEquals(listOf(CsvImport.Row("Kashif", null, 0.0)), CsvImport.parse(csv))
    }

    @Test
    fun `amounts`() {
        assertEquals(1200.0, CsvImport.amount("1,200")!!, 0.0)
        assertEquals(-75.25, CsvImport.amount("-75.25")!!, 0.0)
        assertEquals(-300.0, CsvImport.amount("300-")!!, 0.0)
        // The rupee's closing "/-" is not a minus.
        assertEquals(1200.0, CsvImport.amount("Rs. 1,200/-")!!, 0.0)
        assertEquals(-1200.0, CsvImport.amount("(Rs 1,200/-)")!!, 0.0)
        assertNull(CsvImport.amount("Ali"))
        assertNull(CsvImport.amount(""))
    }

    @Test
    fun `someone already in the book is recognised`() {
        val book = listOf(PartyWithBalance(1, "Bilal  Ahmed", "0300 1112223", true, null, 0.0))
        assertTrue(CsvImport.alreadyIn(CsvImport.Row("bilal ahmed", "+92 300 1112223", 5.0), book))
        assertTrue(CsvImport.alreadyIn(CsvImport.Row("Bilal Ahmed", null, 5.0), book))
        assertEquals(false, CsvImport.alreadyIn(CsvImport.Row("Bilal Ahmed", "0333 9998887", 5.0), book))
    }
}
