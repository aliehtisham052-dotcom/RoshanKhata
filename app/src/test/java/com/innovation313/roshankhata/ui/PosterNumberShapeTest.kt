package com.innovation313.roshankhata.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit L034 (17 Nov 2026): a poster's call and WhatsApp numbers are printed in the app's one shape
 * (`DuplicateDetector.displayPhone`: the shop's calling code, ten digits as 3 + 7), or as typed when that shape cannot be
 * made whole. Robolectric because displayPhone's direction marks come from the phone's BidiFormatter, which the poster
 * takes off again. The computer's copy is held by PosterContactDesktopTest. Made-up numbers only.
 */
@RunWith(AndroidJUnit4::class)
class PosterNumberShapeTest {

    @Test
    fun `three ways of typing one number print alike`() {
        for (typed in listOf("0300-1234567", "03001234567", "+923001234567", "0092 300 1234567", " 0300 1234567 ")) {
            assertEquals(typed, "+92 300 1234567", PosterContact.shown(typed, "92"))
        }
        assertEquals("+92 300 1234567", PosterContact.shown("۰۳۰۰ ۱۲۳۴۵۶۷", "92"))
    }

    @Test
    fun `a number the shape cannot make whole is printed as typed`() {
        assertEquals("+44 20 7946 0000", PosterContact.shown("+44 20 7946 0000", "92"))
        assertEquals("(042) 111-222-333", PosterContact.shown("(042) 111-222-333", "92"))
        assertEquals("0300 1234567", PosterContact.shown("0300 1234567", ""))
        assertEquals("12345", PosterContact.shown("12345", "92"))
    }

    @Test
    fun `the call and WhatsApp numbers share the shape and one number stays one`() {
        val two = PosterContact.of("0300-1234567", onWhatsapp = false, whatsappNumber = "03007654321", dialing = "92")
        assertEquals(listOf("+92 300 1234567", "+92 300 7654321"), two.items.map { it.number })
        val one = PosterContact.of("0300-1234567", onWhatsapp = false, whatsappNumber = "+923001234567", dialing = "92")
        assertEquals(listOf("+92 300 1234567"), one.items.map { it.number })
        assertTrue(one.chatMark)
    }
}
