package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Day report as a WhatsApp message (8 Nov 2026, the owner's chat photo): the shop and date as a heading, three
 * blocks, one figure a line, entries short, and every label saying what its figure is. Made-up names and figures.
 */
class DayBookShareTextTest {

    private val names = object : Words {
        override fun text(name: String, vararg args: Any): String = if (args.isEmpty()) "<$name>" else "<$name:${args.joinToString(",")}>"
        override fun plural(name: String, count: Int, vararg args: Any): String = "<$name>"
        override fun list(name: String): List<String> = emptyList()
    }
    private val money: (Double) -> String = { "Rs " + kotlin.math.abs(it).toLong() }

    private fun day(counted: Double? = null) = ZReport(
        from = 0L, to = 1L, saleCount = 0, salesTotal = 80900.0, byMethod = emptyMap(), onAccount = 0.0,
        discounts = 0.0, tax = 0.0, voidCount = 0, voidedTotal = 0.0, returnCount = 0, returnsTotal = 0.0,
        refundsByMethod = emptyMap(), khataCashIn = 900000.0, khataCashOut = 0.0, cashbookIn = 55000.0, cashbookOut = 0.0,
        opening = 100.0, counted = counted, profit = null, closeExpected = 555.0, closedAt = 1000L
    )

    @Test
    fun `counter tickets and all sales are told apart and the window of each figure is named`() {
        val lines = day().lines(names, money).toMap()
        assertEquals("0", lines["<${W.z_counter_tickets}>"])
        assertEquals("Rs 80900", lines["<${W.z_sales_all}>"])
        assertEquals("Rs 955000", lines["<${W.z_cash_in_day}>"])
        assertTrue(lines.containsKey("<${W.z_expected}>"))
        val secs = day().sections(names, money) { "T$it" }
        val drawer = secs.last().rows.toMap()
        assertEquals("Rs 555", drawer["<${W.z_expected_at}:T1000>"])
    }

    @Test
    fun `the message has a bold heading, three blocks and short entries`() {
        val rows = listOf(DayBook.Row(60_000L, DayBook.Kind.GAVE, "Test Customer", PaymentMethod.CASH, 800.0, null))
        val text = DayBook.shareText(names, "Test Shop", "8 Nov 2026", day(counted = 600.0), rows, money, { "t$it" }, "1 entry", bold = true)
        val lines = text.lines()
        assertEquals("*Test Shop*", lines[0])
        assertEquals("*<${W.z_title}> · 8 Nov 2026*", lines[1])
        assertTrue(text.contains("*<${W.z_sales_count}>*"))
        assertTrue(text.contains("*<${W.z_sec_money}>*"))
        assertTrue(text.contains("*<${W.z_sec_drawer}>*"))
        assertTrue(text.contains("*1 entry*"))
        assertTrue(lines.last().startsWith("t60000 · Test Customer · "))
        assertTrue(lines.last().endsWith(" · Rs 800"))
    }

    @Test
    fun `the printer gets the headings without asterisks`() {
        val text = DayBook.shareText(names, "", "8 Nov 2026", null, emptyList(), money, { "t$it" }, "0 entries", bold = false)
        assertEquals("<${W.z_title}> · 8 Nov 2026\n\n0 entries", text)
    }
}
