package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** R153: no exported cell is run as a formula by Excel; figures stay figures; a file read back reads the same. */
class CsvCellTest {

    @Test
    fun `a text Excel would run is made plain text`() {
        assertEquals("'=HYPERLINK(\"x\")", CsvCell.guard("=HYPERLINK(\"x\")"))
        assertEquals("'+SUM(A1)", CsvCell.guard("+SUM(A1)"))
        assertEquals("'-2+3", CsvCell.guard("-2+3"))
        assertEquals("'@cmd", CsvCell.guard("@cmd"))
        assertEquals("'\tx", CsvCell.guard("\tx"))
        assertEquals("'\rx", CsvCell.guard("\rx"))
        assertEquals("\"'=1+1\"", CsvCell.quote("=1+1"))
    }

    @Test
    fun `figures, ordinary words and the barcode text form pass unchanged`() {
        listOf("-1250", "-1250.50", "+923001234567", "1550", "0.5", "", "Bilal", "علی", "a=b", "=\"8964000000123\"")
            .forEach { assertEquals(it, CsvCell.guard(it)) }
    }

    @Test
    fun `the ledger's own rows are guarded and a negative balance stays a number`() {
        val text = LedgerCsv.text(
            listOf(EntryWithParty(1, 1, "=cmd|' /C calc'!A0", 500.0, true, "@note", "RK-1", 1_000_000_000_000)),
            listOf("Date", "Name", "Note", "Gave", "Got")
        )
        assertTrue(text.contains("\"'=cmd|' /C calc'!A0\",\"'@note\",\"500\""))
        val sb = StringBuilder()
        CsvCell.appendRow(sb, listOf("Asif", LedgerCsv.plain(-1250.0)))
        assertEquals("\"Asif\",\"-1250\"\r\n", sb.toString())
    }

    @Test
    fun `a guarded file reads back as it was written`() {
        val sb = StringBuilder()
        CsvCell.appendRow(sb, listOf("=Ali", "-Bilal", "'kept", "-1250"))
        assertEquals(listOf(listOf("=Ali", "-Bilal", "'kept", "-1250")), CsvImport.cells(sb.toString()))
        val products = ProductCsv.parse(ProductCsv.text(listOf(
            Product(name = "+Urea", nameKey = ProductName.key("+Urea"), normalisedName = ProductName.normalised("+Urea"), salePrice = 10.0)
        )))
        assertEquals("+Urea", products.single().name)
    }
}
