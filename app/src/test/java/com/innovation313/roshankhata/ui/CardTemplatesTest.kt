package com.innovation313.roshankhata.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 24 business card designs (4 Oct 2026): the set the owner chose, each one
 * drawable with every field filled to the brim, with almost nothing, and in
 * Urdu — still and at every point of its preview animation — and the QR always
 * pointing at the shop's own WhatsApp.
 */
@RunWith(AndroidJUnit4::class)
class CardTemplatesTest {

    private val full = CardTemplates.CardData(
        name = "Al-Madina Pesticides and General Order Suppliers Pasrur",
        type = "Pesticides, Seeds, Fertiliser, Livestock Feed and Sprayers",
        owner = "Chaudhry Muhammad Ehtisham Ali Bajwa",
        phone = "0300 1234567",
        address = "Lappay wali tehsil Pasrur district Sialkot Punjab Pakistan",
        footer = "Made with Roshan Khata",
        whatsapp = "0321 7654321",
        email = "al.madina.pesticides.pasrur@gmail.com",
        web = "facebook.com/almadinapesticidespasrur",
        tagline = "Ghar tak delivery, har roz subah 8 se raat 10 baje tak"
    )

    private val bare = CardTemplates.CardData(
        name = "A", type = "", owner = "", phone = "", address = "", footer = ""
    )

    private val urdu = CardTemplates.CardData(
        name = "النور زرعی سٹور", type = "زرعی ادویات · بیج", owner = "احتشام علی",
        phone = "0300 1234567", address = "مین بازار پسرور", footer = "",
        whatsapp = "0300 1234567"
    )

    @Before
    fun fonts() = CardTemplates.init(ApplicationProvider.getApplicationContext())

    @Test
    fun theOwnersTwentyFourAndNoOthers() {
        assertEquals(24, CardTemplates.all.size)
        assertEquals(24, CardTemplates.all.map { it.id }.toSet().size)
        // The four originals he kept keep their old ids, so a saved choice survives.
        listOf(8, 9, 10, 11).forEach { id -> assertEquals(id, CardTemplates.byId(id).id) }
        // A removed design falls back to the first card instead of crashing.
        listOf(0, 1, 2, 3, 4, 5, 6, 7, 999).forEach { id ->
            assertEquals(CardTemplates.all.first().id, CardTemplates.byId(id).id)
        }
    }

    @Test
    fun everyDesignDrawsAnyDataStillAndMoving() {
        val bmp = Bitmap.createBitmap(CardTemplates.W, CardTemplates.H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        for (tpl in CardTemplates.all) {
            for (d in listOf(full, bare, urdu, full.copy(qrMessage = "السلام علیکم، آپ کا کارڈ دیکھ کر آیا ہوں"))) {
                for (phase in floatArrayOf(-1f, 0f, 0.25f, 0.5f, 0.99f)) {
                    try {
                        tpl.draw(canvas, d, CardTemplates.W, CardTemplates.H, phase)
                    } catch (e: Throwable) {
                        throw AssertionError("design ${tpl.id} failed for '${d.name}' at phase $phase", e)
                    }
                }
            }
        }
    }

    @Test
    fun qrOpensTheShopsWhatsApp() {
        assertEquals("https://wa.me/923217654321", CardTemplates.qrPayload(full))
        // No separate WhatsApp: the phone number is used.
        assertEquals(
            "https://wa.me/923001234567",
            CardTemplates.qrPayload(bare.copy(phone = "0300-1234567"))
        )
        assertEquals(
            "https://wa.me/923001234567",
            CardTemplates.qrPayload(bare.copy(whatsapp = "+92 300 1234567"))
        )
        // No usable number: no QR at all rather than a code that opens nothing.
        assertNull(CardTemplates.qrPayload(bare))
        assertNull(CardTemplates.qrPayload(bare.copy(phone = "1234")))
        // A pre-filled message rides along, URL-encoded with %20 for spaces.
        assertEquals(
            "https://wa.me/923001234567?text=Salam%2C%20card%20se%20aaya%20hoon",
            CardTemplates.qrPayload(bare.copy(phone = "0300 1234567", qrMessage = "Salam, card se aaya hoon"))
        )
        assertEquals(
            "https://wa.me/923001234567",
            CardTemplates.qrPayload(bare.copy(phone = "0300 1234567", qrMessage = "   "))
        )
        assertNotNull(CardTemplates.qrPayload(urdu))
        assertTrue(CardTemplates.qrPayload(urdu)!!.startsWith("https://wa.me/92"))
    }
}
