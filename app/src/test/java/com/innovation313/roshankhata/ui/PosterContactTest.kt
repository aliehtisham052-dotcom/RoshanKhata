package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.ui.PosterContact.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which numbers a poster shows (13 Nov 2026, the owner: the call number and the WhatsApp number are often two). The
 * call number is the shop profile's phone; the WhatsApp number is the poster's own. Made-up numbers only.
 */
class PosterContactTest {

    private val call = "0300 1234567"
    private val other = "0300 7654321"

    private fun kinds(c: PosterContact.Contact) = c.items.map { it.kind }
    private fun numbers(c: PosterContact.Contact) = c.items.map { it.number }

    @Test
    fun `with no WhatsApp number the call number keeps its tick as it always had`() {
        val on = PosterContact.of(call, onWhatsapp = true, whatsappNumber = "")
        assertEquals(listOf(Kind.CALL), kinds(on))
        assertEquals(listOf(call), numbers(on))
        assertTrue(on.chatMark)
        assertFalse(PosterContact.of(call, onWhatsapp = false, whatsappNumber = "").chatMark)
    }

    @Test
    fun `no call number and no WhatsApp number show nothing`() {
        assertFalse(PosterContact.of(null, onWhatsapp = true, whatsappNumber = "").any)
        assertFalse(PosterContact.of("   ", onWhatsapp = true, whatsappNumber = "  ").any)
        assertFalse(PosterContact.of(null, onWhatsapp = true, whatsappNumber = "").chatMark)
    }

    @Test
    fun `a different WhatsApp number is shown beside the call number`() {
        for (tick in listOf(true, false)) {
            val c = PosterContact.of(call, onWhatsapp = tick, whatsappNumber = other)
            assertEquals(listOf(Kind.CALL, Kind.WHATSAPP), kinds(c))
            assertEquals(listOf(call, other), numbers(c))
            assertTrue(c.both)
            assertTrue(c.has(Kind.CALL))
            assertTrue(c.has(Kind.WHATSAPP))
            // The call number is not the WhatsApp one, so it must not claim to be on WhatsApp.
            assertFalse(c.chatMark)
        }
    }

    @Test
    fun `a WhatsApp number can stand alone`() {
        val c = PosterContact.of(null, onWhatsapp = true, whatsappNumber = other)
        assertEquals(listOf(Kind.WHATSAPP), kinds(c))
        assertEquals(listOf(other), numbers(c))
        assertFalse(c.both)
        assertFalse(c.has(Kind.CALL))
        assertFalse(c.chatMark)
    }

    @Test
    fun `the call number typed again is one number on WhatsApp`() {
        val spellings = listOf(
            "0300 1234567", "03001234567", "0300-1234567", "+92 300 1234567", "(0300) 123 4567",
            "0092 300 1234567", "۰۳۰۰ ۱۲۳۴۵۶۷"
        )
        for (s in spellings) {
            val c = PosterContact.of(call, onWhatsapp = false, whatsappNumber = s)
            assertEquals("one item for $s", listOf(Kind.CALL), kinds(c))
            assertEquals(listOf(call), numbers(c))
            assertTrue("the chat mark for $s", c.chatMark)
        }
    }

    @Test
    fun `a number one digit apart is another number`() {
        val c = PosterContact.of(call, onWhatsapp = true, whatsappNumber = "0300 1234568")
        assertEquals(listOf(Kind.CALL, Kind.WHATSAPP), kinds(c))
        assertFalse(c.chatMark)
    }

    @Test
    fun `numbers are the same by their last ten digits when both have that many`() {
        assertTrue(PosterContact.same("0300 1234567", "+92 300 1234567"))
        assertTrue(PosterContact.same("+1 (555) 123-4567", "555 123 4567"))
        assertFalse(PosterContact.same("0300 1234567", "0301 1234567"))
        assertFalse(PosterContact.same("0300 1234567", "0300 7654321"))
    }

    @Test
    fun `shorter numbers are the same when only their leading zeros differ`() {
        assertTrue(PosterContact.same("1234567", "01234567"))
        assertTrue(PosterContact.same("0423456789", "423456789"))
        // An area code is part of the number: a local line is not the same as the same line with its code.
        assertFalse(PosterContact.same("042 3456789", "3456789"))
    }

    @Test
    fun `a number too short to be told apart is never the same`() {
        assertFalse(PosterContact.same("12345", "12345"))
        assertFalse(PosterContact.same("", ""))
        assertFalse(PosterContact.same("call me", "call me"))
    }

    @Test
    fun `digits read any script as 0 to 9`() {
        assertEquals("03001234567", PosterContact.digits("۰۳۰۰ ۱۲۳۴۵۶۷"))
        assertEquals("0300", PosterContact.digits("٠٣٠٠"))
        assertEquals("0300", PosterContact.digits("०३००"))
        assertEquals("0300", PosterContact.digits("০৩০০"))
        assertEquals("923001234567", PosterContact.digits("+92 (300) 123-4567"))
        assertEquals("", PosterContact.digits("no digits"))
    }

    @Test
    fun `clean keeps only what a number is written with`() {
        assertEquals("+92 (300) 123-4567 9", PosterContact.clean("  +92  (300)\t123-4567 ext 9 "))
        assertEquals("+92-3001234567", PosterContact.clean("+92-300#1234567*"))
        assertEquals("03001234567", PosterContact.clean("۰۳۰۰۱۲۳۴۵۶۷"))
        assertEquals("", PosterContact.clean("   "))
        assertEquals("", PosterContact.clean("call me"))
    }

    @Test
    fun `clean cuts a long number and never ends on a space`() {
        assertEquals(PosterContact.MAX_LENGTH, PosterContact.clean("1".repeat(40)).length)
        val cut = PosterContact.clean("12345678901234567890123 456")
        assertEquals("12345678901234567890123", cut)
        assertFalse(cut.endsWith(" "))
    }

    @Test
    fun `kept is the cleaned number only once it is a whole phone number`() {
        assertEquals("", PosterContact.kept(""))
        // Half typed on the computer's box: nothing is drawn yet.
        assertEquals("", PosterContact.kept("0300 12"))
        assertEquals("0300 7654321", PosterContact.kept("  0300   7654321x "))
        assertEquals("+92 300 7654321", PosterContact.kept("+92 300 7654321"))
        assertEquals("", PosterContact.kept("1".repeat(16)))
    }

    @Test
    fun `usable needs the digits of a phone number`() {
        assertFalse(PosterContact.usable(""))
        assertFalse(PosterContact.usable("123456"))
        assertTrue(PosterContact.usable("1234567"))
        assertTrue(PosterContact.usable("+92 300 1234567"))
        assertTrue(PosterContact.usable("1".repeat(15)))
        assertFalse(PosterContact.usable("1".repeat(16)))
    }
}
