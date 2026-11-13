package com.innovation313.roshankhata.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The poster's own WhatsApp number (13 Nov 2026): kept with the poster's other choices, per shop, never in the shop
 * profile. Posters saved before it existed have none. Made-up numbers only.
 */
@RunWith(AndroidJUnit4::class)
class PosterFieldsContactTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a new poster has no WhatsApp number of its own`() {
        assertEquals("", PosterFields().whatsappNumber)
        assertEquals("", PosterFields.fromJson(null).whatsappNumber)
    }

    @Test
    fun `a file from before the number existed reads with none and keeps its other choices`() {
        val old = JSONObject("""{"showPhone":false,"onWhatsapp":false,"eidDays":2}""")
        val f = PosterFields.fromJson(old)
        assertEquals("", f.whatsappNumber)
        assertFalse(f.showPhone)
        assertFalse(f.onWhatsapp)
        assertEquals(2, f.eidDays)
    }

    @Test
    fun `a kept number comes back as it was beside the tick`() {
        val f = PosterFields(whatsappNumber = "0300 7654321", onWhatsapp = false)
        val back = PosterFields.fromJson(JSONObject(f.toJson().toString()))
        assertEquals("0300 7654321", back.whatsappNumber)
        assertFalse(back.onWhatsapp)
        assertTrue(back.showPhone)
    }

    @Test
    fun `a number in a file is trimmed and cut at the longest a number is kept`() {
        val spaced = PosterFields.fromJson(JSONObject("""{"whatsappNumber":"  0300 7654321  "}"""))
        assertEquals("0300 7654321", spaced.whatsappNumber)
        val long = PosterFields.fromJson(JSONObject("""{"whatsappNumber":"${"9".repeat(60)}"}"""))
        assertEquals(PosterFields.MAX_NUMBER, long.whatsappNumber.length)
    }

    @Test
    fun `the number is kept per shop`() {
        PosterFields.save(context, "shop-a", PosterFields(whatsappNumber = "0300 7654321"))
        PosterFields.save(context, "shop-b", PosterFields())
        assertEquals("0300 7654321", PosterFields.load(context, "shop-a").whatsappNumber)
        assertEquals("", PosterFields.load(context, "shop-b").whatsappNumber)
        // Taking it away is kept too.
        PosterFields.save(context, "shop-a", PosterFields.load(context, "shop-a").copy(whatsappNumber = ""))
        assertEquals("", PosterFields.load(context, "shop-a").whatsappNumber)
    }
}
