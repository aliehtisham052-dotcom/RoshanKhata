package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Amounts as a shop says them (16 Nov 2026, audit T30 / H-01): "5k" was read as Rs 5 and "dedh hazar" as Rs 1,000 —
 * the entry form opened with a wrong figure and only the owner's eye stood between it and the book. Invented names.
 */
class VoiceAmountShorthandTest {

    private fun amount(said: String, lang: String? = null): Double? = VoiceEntry.parse(said, listOf("Ali Raza"), lang).amount

    @Test
    fun `a k joined to the figure is thousands`() {
        assertEquals(5000.0, amount("Ali Raza ko 5k diye")!!, 0.0)
        assertEquals(5000.0, amount("Ali Raza ko 5K diye")!!, 0.0)
        assertEquals(1500.0, amount("Ali Raza ko 1.5k diye")!!, 0.0)
        assertEquals(25000.0, amount("Ali Raza se 25k mile")!!, 0.0)
        assertEquals(5000.0, amount("Rs5k Ali Raza ko diye")!!, 0.0)
    }

    @Test
    fun `a scale word joined to the figure counts too`() {
        assertEquals(200000.0, amount("Ali Raza ko 2lac diye")!!, 0.0)
        assertEquals(250000.0, amount("Ali Raza ko 2.5lakh diye")!!, 0.0)
        assertEquals(5000.0, amount("Ali Raza kasih 5rb", "id")!!, 0.0)
    }

    @Test
    fun `a k standing apart is not a scale`() {
        // "500 k saman" is "500 ke saman": the figure stays 500.
        assertEquals(500.0, amount("Ali Raza ko 500 k saman diye")!!, 0.0)
        assertEquals(5.0, amount("Ali Raza ko 5kg diye")!!, 0.0)
    }

    @Test
    fun `one and a half and two and a half`() {
        assertEquals(1500.0, amount("Ali Raza ko dedh hazar diye")!!, 0.0)
        assertEquals(150.0, amount("Ali Raza ko derh sau diye")!!, 0.0)
        assertEquals(150000.0, amount("Ali Raza ko dedh lakh diye")!!, 0.0)
        assertEquals(2500.0, amount("Ali Raza ko dhai hazar diye")!!, 0.0)
        assertEquals(250.0, amount("Ali Raza ko adhai sau diye")!!, 0.0)
        assertEquals(1500.0, amount("علی رضا کو ڈیڑھ ہزار دیے")!!, 0.0)
        assertEquals(2500.0, amount("علی رضا کو ڈھائی ہزار دیے")!!, 0.0)
    }

    @Test
    fun `sawa saade and paune change the number after them`() {
        assertEquals(125.0, amount("Ali Raza ko sawa sau diye")!!, 0.0)
        assertEquals(1250.0, amount("Ali Raza ko sawa hazar diye")!!, 0.0)
        assertEquals(125000.0, amount("Ali Raza ko sawa lakh diye")!!, 0.0)
        assertEquals(3500.0, amount("Ali Raza ko saade teen hazar diye")!!, 0.0)
        assertEquals(550.0, amount("Ali Raza ko saade paanch sau diye")!!, 0.0)
        assertEquals(175000.0, amount("Ali Raza ko paune do lakh diye")!!, 0.0)
        assertEquals(75.0, amount("Ali Raza ko paune sau diye")!!, 0.0)
        assertEquals(3500.0, amount("علی رضا کو ساڑھے تین ہزار دیے")!!, 0.0)
        assertEquals(1750.0, amount("علی رضا کو پونے دو ہزار دیے")!!, 0.0)
    }

    @Test
    fun `a modifier with no number after it changes nothing`() {
        // Urdu سوا also means "apart from".
        assertEquals(500.0, amount("علی رضا کو اس کے سوا 500 دیے")!!, 0.0)
        assertNull(amount("Ali Raza sawa"))
        // A word between them breaks the link: "sawa" here belongs to nothing.
        assertEquals(300.0, amount("sawa Ali Raza ko teen sau diye")!!, 0.0)
    }

    @Test
    fun `the figures are never part of the name`() {
        val p = VoiceEntry.parse("Ali Raza ko 5k diye", listOf("Ali Raza"))
        assertEquals("Ali Raza", p.partyName)
        assertEquals(listOf("ali", "raza"), VoiceEntry.nameWords("Ali Raza ko 5k dedh sawa diye").filter { it != "ko" })
    }

    @Test
    fun `old readings are unchanged`() {
        assertEquals(5000.0, amount("Ali Raza ko paanch hazaar diye")!!, 0.0)
        assertEquals(5000.0, amount("Ali Raza ko 5 hazar diye")!!, 0.0)
        assertEquals(100000.0, amount("Ali Raza ko one hundred thousand diye")!!, 0.0)
        assertEquals(1000.0, amount("Ali Raza ko hazaar diye")!!, 0.0)
        assertEquals(1500.0, amount("Ali Raza ko ek hazar paanch sau diye")!!, 0.0)
    }
}
