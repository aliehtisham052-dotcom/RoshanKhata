package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The products spreadsheet both ways, and the shop's own barcodes (POS Phase 2). */
class ProductCsvTest {

    private fun product(name: String, id: Long = 0, barcode: String? = null) =
        Product(id = id, name = name, nameKey = ProductName.key(name), normalisedName = ProductName.normalised(name), barcode = barcode)

    @Test
    fun aFileWrittenHereReadsBackWhole() {
        val out = ProductCsv.text(listOf(
            product("DAP").copy(company = "Engro", defaultUnit = "bag", salePrice = 3500.0, creditPrice = 3700.5, costPrice = 3000.0, barcode = "8901234567890", lowStockLevel = 5.0, productType = "Fertilizer"),
            product("Say \"hi\", seed").copy(salePrice = 12.0)
        ))
        val rows = ProductCsv.parse(out)
        assertEquals(2, rows.size)
        val dap = rows.first { it.name == "DAP" }
        assertEquals("Engro", dap.company); assertEquals("bag", dap.unit)
        assertEquals(3500.0, dap.salePrice!!, 0.0); assertEquals(3700.5, dap.creditPrice!!, 0.0)
        assertEquals(3000.0, dap.costPrice!!, 0.0); assertEquals("8901234567890", dap.barcode)
        assertEquals(5.0, dap.lowStock!!, 0.0); assertEquals("Fertilizer", dap.type)
        assertEquals("Say \"hi\", seed", rows[1].name)
    }

    @Test
    fun anotherAppsSheetIsReadByItsHeaderWords() {
        val rows = ProductCsv.parse("Product,Brand,Rate,Code\nUrea,Fauji,4800,123\nZinc,,950,\n")
        assertEquals(2, rows.size)
        assertEquals("Fauji", rows[0].company); assertEquals(4800.0, rows[0].salePrice!!, 0.0); assertEquals("123", rows[0].barcode)
        assertNull(rows[1].company); assertNull(rows[1].barcode)
        // No header: name, price, barcode.
        val bare = ProductCsv.parse("Urea,4800,123\n")
        assertEquals("Urea", bare.single().name); assertEquals(4800.0, bare.single().salePrice!!, 0.0)
    }

    @Test
    fun thePlanAddsUpdatesAndCountsBarcodeClashes() {
        val have = listOf(product("Urea", 1, "111"), product("DAP", 2, "222"))
        val rows = listOf(
            ProductCsv.Row("urea ", salePrice = 5000.0, barcode = "222"),
            ProductCsv.Row("Zinc", barcode = "333"),
            ProductCsv.Row("Zinc", barcode = "444")
        )
        val plan = ProductCsv.plan(rows, have)
        assertEquals(listOf("Zinc"), plan.toAdd.map { it.name })
        assertEquals("Urea", plan.toUpdate.single().first.name)
        assertEquals(1, plan.barcodeClashes)
        val updated = ProductCsv.apply(have[0].copy(company = "Fauji"), rows[0])
        assertEquals("Fauji", updated.company)
        assertEquals(5000.0, updated.salePrice!!, 0.0)
    }

    @Test
    fun internalEanHasAValidCheckDigit() {
        val code = Barcodes.internalEan13(42)
        assertEquals(13, code.length)
        assertTrue(code.startsWith("2"))
        assertTrue(Barcodes.isValidEan(code))
        assertTrue(Barcodes.isValidEan("8901234567890".dropLast(1) + Barcodes.checkDigit("890123456789")))
        assertEquals(1, Barcodes.checkDigit("400638133393")) // 4006381333931, the standard's own example
        assertTrue(Barcodes.isValidEan("4006381333931"))
        assertFalse(Barcodes.isValidEan("4006381333932"))
        assertTrue(Barcodes.internalEan13(42) != Barcodes.internalEan13(43))
    }

    @Test
    fun `a barcode keeps its digits through Excel, and a code Excel already spoiled is not taken`() {
        assertEquals("=\"8901234567890\"", ProductCsv.asText("8901234567890"))
        assertEquals("8901234567890", ProductCsv.barcodeOf("=\"8901234567890\""))
        assertEquals("123", ProductCsv.barcodeOf("123"))
        assertNull(ProductCsv.barcodeOf("8.90123E+12"))
    }
}
