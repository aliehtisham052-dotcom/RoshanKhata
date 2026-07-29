package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberWordsTest {

    @Test
    fun `zero`() {
        assertEquals("Sifar Rupay", NumberWords.rupeesInWords(0.0))
    }

    @Test
    fun `single digit and teen`() {
        assertEquals("Saat Rupay", NumberWords.rupeesInWords(7.0))
        assertEquals("Unnees Rupay", NumberWords.rupeesInWords(19.0))
    }

    @Test
    fun `the exact figure from the finalised invoice mockup`() {
        // T1 (Teal Corporate) shows this as the worked example for Rs 19,285.
        assertEquals("Unnees Hazar Do Sou Pichhasi Rupay", NumberWords.rupeesInWords(19285.0))
    }

    @Test
    fun `hundred alone, no hazar or sau below it`() {
        assertEquals("Ek Sou Rupay", NumberWords.rupeesInWords(100.0))
    }

    @Test
    fun `thousand exactly, no sau or remainder`() {
        assertEquals("Paanch Hazar Rupay", NumberWords.rupeesInWords(5000.0))
    }

    @Test
    fun `lakh and crore scale`() {
        assertEquals("Ek Lakh Rupay", NumberWords.rupeesInWords(100000.0))
        assertEquals("Ek Crore Rupay", NumberWords.rupeesInWords(10000000.0))
    }

    @Test
    fun `every group present at once`() {
        // 1,23,456 -> Ek Lakh Teis Hazar Chaar Sou Chhappan
        assertEquals(
            "Ek Lakh Teis Hazar Chaar Sou Chhappan Rupay",
            NumberWords.rupeesInWords(123456.0)
        )
    }

    @Test
    fun `only whole rupees, fractional part dropped`() {
        assertEquals("Ek Sou Rupay", NumberWords.rupeesInWords(100.75))
    }

    @Test
    fun `negative is treated as zero rather than crashing`() {
        assertEquals("Sifar Rupay", NumberWords.rupeesInWords(-500.0))
    }
}
