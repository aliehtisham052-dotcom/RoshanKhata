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
    fun `the app's own export reads back in all nine languages, its type column too`() {
        // 25 Oct 2026: the Arabic phone header "رقم الهاتف" holds "رقم", the Urdu word for an amount, and was
        // taken for the balance column; the export's fourth column (customer or supplier) is passed over.
        val heads = listOf(
            listOf("Name", "Phone number", "Net balance", "Customer / Supplier"),
            listOf("نام", "فون نمبر", "کل بیلنس", "گاہک / سپلائر"),
            listOf("Naam", "Phone number", "Kul balance", "Gahak / Supplier"),
            listOf("نالو", "فون نمبر", "خالص بيلنس", "گراهڪ / سپلائر"),
            listOf("الاسم", "رقم الهاتف", "الرصيد الصافي", "عميل / مورّد"),
            listOf("نام", "شمارهٔ تلفن", "مانده خالص", "مشتری / تأمین‌کننده"),
            listOf("नाम", "फ़ोन नंबर", "कुल बैलेंस", "ग्राहक / सप्लायर"),
            listOf("নাম", "ফোন নম্বর", "নিট ব্যালেন্স", "গ্রাহক / সরবরাহকারী"),
            listOf("Nama", "Nomor telepon", "Saldo bersih", "Pelanggan / Pemasok")
        )
        for (h in heads) {
            val sb = StringBuilder("\uFEFF")
            CsvCell.appendRow(sb, h)
            CsvCell.appendRow(sb, listOf("Ali", "0300 1234567", "2000", h[3].substringBefore(" /")))
            CsvCell.appendRow(sb, listOf("Seeds Co.", "", "-150.5", h[3].substringAfter("/ ")))
            assertEquals(
                h[1],
                listOf(CsvImport.Row("Ali", "0300 1234567", 2000.0), CsvImport.Row("Seeds Co.", null, -150.5)),
                CsvImport.parse(sb.toString())
            )
        }
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

    @Test
    fun `the decimal mark is read from the figure - Indonesian sheets 23 Oct`() {
        // A mark used twice groups; with both marks the last one is the decimal.
        assertEquals(1500000.0, CsvImport.amount("1.500.000", groupDots = false)!!, 0.0)
        assertEquals(1250.5, CsvImport.amount("1.250,50", groupDots = false)!!, 0.0)
        assertEquals(1200.5, CsvImport.amount("Rs 1,200.50", groupDots = false)!!, 0.0)
        assertEquals(150000.0, CsvImport.amount("1,50,000", groupDots = false)!!, 0.0)
        // A lone comma: before three digits it groups, before one or two it is the decimal.
        assertEquals(1200.0, CsvImport.amount("1,200/-", groupDots = false)!!, 0.0)
        assertEquals(12.5, CsvImport.amount("12,5", groupDots = false)!!, 0.0)
        // A lone dot before three digits groups only where dots group (or the cell says Rp).
        assertEquals(15000.0, CsvImport.amount("15.000", groupDots = true)!!, 0.0)
        assertEquals(15000.0, CsvImport.amount("Rp 15.000", groupDots = false)!!, 0.0)
        assertEquals(1.125, CsvImport.amount("1.125", groupDots = false)!!, 0.0)
        assertEquals(-500.0, CsvImport.amount("(500)", groupDots = true)!!, 0.0)
    }

    @Test
    fun `a semicolon sheet groups with dots`() {
        assertTrue(CsvImport.dotGroups("Nama;Harga\nBeras;15.000\n"))
        val rows = ProductCsv.parse("Nama;Harga\nBeras;15.000\nGula;12,5\n")
        assertEquals(listOf(15000.0, 12.5), rows.map { it.salePrice })
    }
}
