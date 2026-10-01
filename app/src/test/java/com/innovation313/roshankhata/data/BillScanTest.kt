package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Reading a supplier's printed bill. The rows below are the shape of a real
 * pesticide distributor's invoice as the phone reads it: header, a product
 * table with batch and expiry, totals at the foot.
 */
class BillScanTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = day(2026, 10, 1)

    private fun day(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance(utc).apply { clear(); set(y, m - 1, d, 12, 0, 0) }.timeInMillis

    private fun lastDay(y: Int, m: Int): Long =
        Calendar.getInstance(utc).apply {
            clear(); set(y, m - 1, 1, 12, 0, 0)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }.timeInMillis

    private val bill = listOf(
        "AL-NOOR AGRO DISTRIBUTORS",
        "Pasrur Road, Sialkot   Ph: 0300-1234567",
        "Invoice No: INV-2026/418          Date: 12/09/2026",
        "Customer: Roshan Traders",
        "S.No  Product  Batch  Exp  Qty  Rate  Amount",
        "1  Coragen 20SC 100ml  B2401  12/2026  10  2,800  28,000",
        "2  Roundup 1L  RU7781  06/2027  5  1,450  7,250",
        "3  Decis 25 EC 250ml  DC-552  Mar 2027  12  640.50  7,686",
        "Sub Total  42,936",
        "Discount  936",
        "Grand Total  42,000"
    )

    @Test
    fun `header fields are read`() {
        val b = BillScan.parseRows(bill, now = now, tz = utc)
        assertEquals("INV-2026/418", b.billNumber)
        assertEquals(day(2026, 9, 12), b.billDate)
        assertEquals(42_000.0, b.total!!, 0.0)
    }

    @Test
    fun `product lines are read with batch and expiry`() {
        val items = BillScan.parseRows(bill, now = now, tz = utc).items
        assertEquals(3, items.size)

        val c = items[0]
        assertEquals("Coragen 20SC 100ml", c.name)
        assertEquals(10.0, c.quantity, 0.0)
        assertEquals(2_800.0, c.rate, 0.0)
        assertEquals(28_000.0, c.amount, 0.0)
        assertEquals("B2401", c.batch)
        assertEquals(lastDay(2026, 12), c.expiry)

        assertEquals("Roundup 1L", items[1].name)
        assertEquals("RU7781", items[1].batch)

        // A name that starts like a month ("Decis" / Dec) stays a name, and a
        // written month is still read as the expiry.
        assertEquals("Decis 25 EC 250ml", items[2].name)
        assertEquals("DC-552", items[2].batch)
        assertEquals(640.5, items[2].rate, 0.0)
        assertEquals(lastDay(2027, 3), items[2].expiry)
    }

    @Test
    fun `items total is offered to check against the bill`() {
        val b = BillScan.parseRows(bill, now = now, tz = utc)
        assertEquals(42_936.0, b.itemsTotal, 0.0)
    }

    /** One misread digit (28,000 read as 23,000) breaks q x r = a: the line is left out, not filled in wrong. */
    @Test
    fun `a line whose figures do not agree is dropped`() {
        val misread = BillScan.parseRows(
            listOf("Coragen 20SC 100ml  10  2,800  23,000"), now = now, tz = utc
        )
        assertTrue(misread.items.isEmpty())
    }

    @Test
    fun `a header that lists rate before qty is honoured`() {
        val b = BillScan.parseRows(
            listOf("Item  Rate  Qty  Amount", "Urea Bag  4,500  20  90,000"), now = now, tz = utc
        )
        assertEquals(20.0, b.items.single().quantity, 0.0)
        assertEquals(4_500.0, b.items.single().rate, 0.0)
        assertEquals("Urea", b.items.single().name)
        assertEquals("bag", b.items.single().unit)
    }

    @Test
    fun `quantity totals are not the bill total`() {
        val b = BillScan.parseRows(listOf("Total Qty  27", "Net Amount  42,000"), now = now, tz = utc)
        assertEquals(42_000.0, b.total!!, 0.0)
        assertEquals(42_000.0, BillScan.parseRows(listOf("Total Items 3", "Total 42,000"), now = now, tz = utc).total!!, 0.0)
    }

    /** "Roundup" and "Discovery" contain "round" and "disc"; they are products, not summary rows. */
    @Test
    fun `products named like summary words are kept`() {
        val b = BillScan.parseRows(
            listOf("Roundup 1L  2  1,450  2,900", "Discovery 500ml  4  900  3,600"), now = now, tz = utc
        )
        assertEquals(2, b.items.size)
    }

    @Test
    fun `a manufacturing date is not taken for expiry`() {
        val b = BillScan.parseRows(
            listOf("Date: 12/09/2026", "Score 250ml  01/2026  3  1,000  3,000"), now = now, tz = utc
        )
        assertNull(b.items.single().expiry)
    }

    @Test
    fun `the shop's own product name is used when it matches`() {
        val b = BillScan.parseRows(
            listOf("CORAGEN 20SC 100ML  10  2,800  28,000"), listOf("Coragen 20SC", "Confidor"), now, utc
        )
        assertEquals("Coragen 20SC", b.items.single().name)
    }

    @Test
    fun `a phone number or date is never a bill number`() {
        assertNull(BillScan.parseRows(listOf("Bill To: Roshan Traders", "Invoice 12/09/2026"), now = now, tz = utc).billNumber)
    }

    /** Columns read as separate lines are put back on one row by height. */
    @Test
    fun `columns at one height become one row`() {
        val lines = listOf(
            OcrLine("Coragen 20SC", 10, 100, 200, 120),
            OcrLine("28,000", 600, 102, 680, 121),
            OcrLine("10", 300, 101, 320, 119),
            OcrLine("2,800", 400, 99, 470, 120),
            OcrLine("Grand Total 28,000", 10, 200, 400, 220)
        )
        assertEquals(listOf("Coragen 20SC  10  2,800  28,000", "Grand Total 28,000"), BillScan.rows(lines))
        val b = BillScan.parse(lines, now = now, tz = utc)
        assertEquals(1, b.items.size)
        assertEquals(28_000.0, b.total!!, 0.0)
    }

    @Test
    fun `nothing readable gives an empty result`() {
        assertTrue(BillScan.parseRows(listOf("Thank you for your business"), now = now, tz = utc).isEmpty)
        assertFalse(BillScan.parseRows(bill, now = now, tz = utc).isEmpty)
    }

    // -------------------------------------------- the owner's own bill, 1 Oct

    /**
     * Sayban International's supply order, as the owner photographed it.
     * Before this, only the total and date came through: the company name,
     * the "S.O No." and the batches (printed as bare digits) were lost.
     */
    private val sayban = listOf(
        "SAYBAN INTERNATIONAL",
        "62-KM MULTAN ROAD, ROHI NALA KHAN KEY MORE, PHOOL NAGAR, TEHSIL PATTOKI DISTT KASUR",
        "SUPPLY ORDER / INVOICE",
        "Date: 20-07-2026",
        "S.O No. : 2101003668  Ref. No.",
        "Customer Name:  BHATTI TRADERS LAPPAY WALI",
        "G.S.T./NTN/CNIC No.:",
        "Address:  LAPPAY WALI TEHSIL PASRUR DISTRICT SIALKOT 0348-7239466",
        "Territory:  NAROWAL  Order #:  Please Confirm this Order",
        "PRODUCT  BATCH NO.  QTY  Rate  Gross Amount  Disc %  Discount Amount  Net Amount",
        "NAAMVAR UREA PHOSPHATE 1C  20260706  15  5660  84,900.00  .00  .00  84,900.00",
        "ANAAJ GOLI 56% (TAB) 90 GM  202501  100  288  28,800.00  .00  .00  28,800.00",
        "LEPTOKILL 20%EC 800 ML  SI/CBM/26050801  24  2760  66,240.00  .00  .00  66,240.00",
        "Total:  179,940.00  .00  179,940.00",
        "Gate Pass #:  G.P. Date:  Veh. #:  Bilty # :  Opening Balance:  (329,048.44)",
        "Current D.C.:  179,940.00",
        "Transporter:  Driver Name:  Balance:  (149,108.44)"
    )

    @Test
    fun `the owner's sayban bill - header`() {
        val b = BillScan.parseRows(sayban, now = now, tz = utc)
        assertEquals("Sayban International", b.supplierName)
        assertEquals("2101003668", b.billNumber)
        assertEquals(day(2026, 7, 20), b.billDate)
        assertEquals(179_940.0, b.total!!, 0.0)
    }

    @Test
    fun `the owner's sayban bill - every product with its batch`() {
        val items = BillScan.parseRows(sayban, now = now, tz = utc).items
        assertEquals(3, items.size)
        assertEquals("NAAMVAR UREA PHOSPHATE 1C", items[0].name)
        assertEquals("20260706", items[0].batch)
        assertEquals(15.0, items[0].quantity, 0.0)
        assertEquals(5_660.0, items[0].rate, 0.0)
        assertEquals("ANAAJ GOLI 56% (TAB) 90 GM", items[1].name)
        assertEquals("202501", items[1].batch)
        assertEquals(100.0, items[1].quantity, 0.0)
        assertEquals("LEPTOKILL 20%EC 800 ML", items[2].name)
        assertEquals("SI/CBM/26050801", items[2].batch)
        assertEquals(179_940.0, BillScan.parseRows(sayban, now = now, tz = utc).itemsTotal, 0.0)
    }

    /** The shop's own name is printed as the CUSTOMER; it must never become the supplier. */
    @Test
    fun `the customer line is never taken for the supplier`() {
        val b = BillScan.parseRows(listOf("Customer Name: BHATTI TRADERS", "Date: 20-07-2026"), now = now, tz = utc)
        assertNull(b.supplierName)
    }

    @Test
    fun `a supplier already in the book is matched by its own spelling`() {
        val b = BillScan.parseRows(sayban, emptyList(), now, utc, listOf("Sayban Intl", "Sayban International Pattoki"))
        assertEquals("Sayban International Pattoki", b.supplierName)
    }

    /** From real lines: the tallest heading near the top, not the title "SUPPLY ORDER / INVOICE". */
    @Test
    fun `supplier is the big heading at the top`() {
        val lines = listOf(
            OcrLine("Phone 0300-1112223", 100, 10, 400, 30),
            OcrLine("SAYBAN INTERNATIONAL", 300, 40, 1100, 100),
            OcrLine("62-KM MULTAN ROAD", 200, 110, 1200, 135),
            OcrLine("SUPPLY ORDER / INVOICE", 350, 150, 1050, 205),
            OcrLine("Grand Total 1,000", 10, 900, 400, 930)
        )
        assertEquals("Sayban International", BillScan.parse(lines, now = now, tz = utc).supplierName)
    }

    /** The owner's phone read 800 as "BOO". Mended only in front of a unit. */
    @Test
    fun `a pack size misread as letters is mended`() {
        val b = BillScan.parseRows(
            listOf("LEPTOKILL 20%EC BOO ML  24  2760  66,240.00", "Bio Power 2SO ML  2  100  200", "BIO SOIL ML  1  50  50"),
            now = now, tz = utc
        )
        assertEquals("LEPTOKILL 20%EC 800 ML", b.items[0].name)
        assertEquals("Bio Power 250 ML", b.items[1].name)
        assertEquals("BIO SOIL ML", b.items[2].name)          // a real word is left alone
        assertEquals("Coragen 100ML", BillScan.parseRows(listOf("Coragen 1OOML  2  100  200"), now = now, tz = utc).items[0].name)
    }
}

