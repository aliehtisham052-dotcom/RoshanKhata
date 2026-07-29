package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberWordsTest {

    @Test
    fun `zero`() {
        assertEquals("Sifar Rupay", NumberWords.rupeesInWordsRomanUrdu(0.0))
    }

    @Test
    fun `single digit and teen`() {
        assertEquals("Saat Rupay", NumberWords.rupeesInWordsRomanUrdu(7.0))
        assertEquals("Unnees Rupay", NumberWords.rupeesInWordsRomanUrdu(19.0))
    }

    @Test
    fun `the exact figure from the finalised invoice mockup`() {
        // T1 (Teal Corporate) shows this as the worked example for Rs 19,285.
        assertEquals("Unnees Hazar Do Sou Pichhasi Rupay", NumberWords.rupeesInWordsRomanUrdu(19285.0))
    }

    @Test
    fun `hundred alone, no hazar or sau below it`() {
        assertEquals("Ek Sou Rupay", NumberWords.rupeesInWordsRomanUrdu(100.0))
    }

    @Test
    fun `thousand exactly, no sau or remainder`() {
        assertEquals("Paanch Hazar Rupay", NumberWords.rupeesInWordsRomanUrdu(5000.0))
    }

    @Test
    fun `lakh and crore scale`() {
        assertEquals("Ek Lakh Rupay", NumberWords.rupeesInWordsRomanUrdu(100000.0))
        assertEquals("Ek Crore Rupay", NumberWords.rupeesInWordsRomanUrdu(10000000.0))
    }

    @Test
    fun `every group present at once`() {
        // 1,23,456 -> Ek Lakh Teis Hazar Chaar Sou Chhappan
        assertEquals(
            "Ek Lakh Teis Hazar Chaar Sou Chhappan Rupay",
            NumberWords.rupeesInWordsRomanUrdu(123456.0)
        )
    }

    @Test
    fun `only whole rupees, fractional part dropped`() {
        assertEquals("Ek Sou Rupay", NumberWords.rupeesInWordsRomanUrdu(100.75))
    }

    @Test
    fun `negative is treated as zero rather than crashing`() {
        assertEquals("Sifar Rupay", NumberWords.rupeesInWordsRomanUrdu(-500.0))
    }

    // ---- English wording, which composes rather than using a lookup ----

    @Test
    fun `english composes the irregular-looking twenties`() {
        assertEquals("Twenty-One Rupees", NumberWords.rupeesInWordsEnglish(21.0))
        assertEquals("Forty Rupees", NumberWords.rupeesInWordsEnglish(40.0))
        assertEquals("Ninety-Nine Rupees", NumberWords.rupeesInWordsEnglish(99.0))
    }

    @Test
    fun `english keeps south asian grouping, not millions`() {
        // Pakistani English on an invoice says Lakh, not Hundred Thousand.
        assertEquals("One Lakh Rupees", NumberWords.rupeesInWordsEnglish(100000.0))
        assertEquals(
            "One Lakh Twenty-Three Thousand Four Hundred Fifty-Six Rupees",
            NumberWords.rupeesInWordsEnglish(123456.0)
        )
    }

    @Test
    fun `english zero and negative behave like the roman urdu version`() {
        assertEquals("Zero Rupees", NumberWords.rupeesInWordsEnglish(0.0))
        assertEquals("Zero Rupees", NumberWords.rupeesInWordsEnglish(-500.0))
    }
}
