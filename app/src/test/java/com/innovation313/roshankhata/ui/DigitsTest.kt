package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.EntryNumber
import com.innovation313.roshankhata.data.InvoiceNumber
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's language is the JVM/Android default locale, and for Sindhi,
 * Persian and Arabic that locale's own digits are not 0-9. Every test here
 * switches the default to those languages first — exactly what choosing
 * them in the app does — and checks that what is printed, stored and read
 * back is still 0-9. Before [Digits] existed, the JVM's CLDR data turned
 * "Rs %,.0f" of 27628 into Arabic-Indic digits under all three.
 */
class DigitsTest {

    private lateinit var saved: Locale
    private val nonLatin = listOf("sd", "fa", "ar")

    @Before fun remember() { saved = Locale.getDefault() }
    @After fun restore() { Locale.setDefault(saved) }

    private fun under(tag: String, block: () -> Unit) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        block()
    }

    private fun assertLatinDigits(text: String, where: String) {
        val bad = text.filter { it.isDigit() && it !in '0'..'9' }
        assertTrue("$where printed non-Latin digits: '$text'", bad.isEmpty())
    }

    @Test
    fun `money prints 0-9 in every language`() {
        for (tag in nonLatin) under(tag) {
            assertEquals("Rs 27,628", Format.money(27628.0))
            assertEquals("Rs 1,250.50", Format.money(1250.5))
        }
    }

    @Test
    fun `invoice and receipt numbers are stored with 0-9`() {
        for (tag in nonLatin) under(tag) {
            assertEquals("INV-000012", InvoiceNumber.next(11))
            assertEquals("RK-000042", EntryNumber.next(41))
        }
    }

    @Test
    fun `quantities print 0-9`() {
        for (tag in nonLatin) under(tag) {
            assertLatinDigits(Format.qty(12.5, "kg"), "Format.qty [$tag]")
        }
    }

    @Test
    fun `dates keep their language but use 0-9`() {
        val jan5 = Date(4L * 24 * 60 * 60 * 1000)
        for (tag in nonLatin) under(tag) {
            val text = SimpleDateFormat("d MMM yyyy", Digits.latinIn()).format(jan5)
            assertLatinDigits(text, "date [$tag]")
            assertTrue("date [$tag] should still read 1970: '$text'", text.contains("1970"))
        }
        // The month name is still the language's own — only digits changed.
        under("fa") {
            val text = SimpleDateFormat("MMMM", Digits.latinIn()).format(jan5)
            assertTrue("Persian month name was replaced: '$text'", text.none { it in 'A'..'z' })
        }
    }

    @Test
    fun `typed Arabic and Persian digits are read as numbers`() {
        assertEquals(276.0, Digits.parse("۲۷۶")!!, 0.0)          // Persian keyboard
        assertEquals(276.0, Digits.parse("٢٧٦")!!, 0.0)          // Arabic / Sindhi keyboard
        assertEquals(12.5, Digits.parse(" ١٢٫٥ ")!!, 0.0)       // Arabic decimal separator
        assertEquals(27628.0, Digits.parse("٢٧٬٦٢٨")!!, 0.0)     // Arabic thousands separator
        assertEquals(500.0, Digits.parse("500")!!, 0.0)           // 0-9 untouched
        assertNull(Digits.parse(""))
        assertNull(Digits.parse(null))
        assertNull(Digits.parse("abc"))
    }

    @Test
    fun `the calculator reads a Persian keyboard, including the handover's own example`() {
        assertEquals(27628.0, Calc.evalAmount("۷۹۲۶-۳۵۵۵۴")!!, 0.001)
        assertEquals(160.0, Calc.evalPad("٥٠٠×٣٢٪")!!, 0.001)   // Arabic percent sign
        assertEquals(800.0, Calc.eval("٥٠٠+٣٠٠")!!, 0.001)       // direct eval path too
    }

    @Test
    fun `text without Arabic or Persian digits is returned as-is`() {
        val s = "500+300×2%"
        assertTrue(Digits.toLatin(s) === s)
    }
}
