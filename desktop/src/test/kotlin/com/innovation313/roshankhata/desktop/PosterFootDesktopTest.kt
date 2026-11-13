package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
import com.innovation313.roshankhata.desktop.poster.PosterDraw
import com.innovation313.roshankhata.desktop.poster.PosterFields
import com.innovation313.roshankhata.desktop.poster.PosterInput
import com.innovation313.roshankhata.desktop.poster.PosterOccasion
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The Thank-you poster's foot, held on the DRAWN picture (13 Nov 2026; the arithmetic is PosterFootTest's): the shop's name,
 * number and address rows never reach the seal, and the brand mark steps out under the card's frame only when the foot
 * leaves it no room inside. Before, an Urdu name with a phone number, two address rows and the mark switched on was drawn
 * over the seal's body (the name's ink began about 13 px above where the body ends).
 *
 * Every assertion compares two pictures of the same poster (with and without the foot, with and without the mark), so
 * nothing here depends on one machine's text rendering to the pixel. The rows are those of the 1080 x 1920 picture: the seal's
 * body ends on row 1596 and its soft shadow about 1612; the card's inner frame is at 1859 and its outer frame at 1876..1879.
 * Pictures go to build/poster-proof to be looked at (lesson 16). Made-up names and numbers only (lesson 80).
 */
class PosterFootDesktopTest {

    private val call = "0300 1234567"
    private val other = "0300 7654321"
    private val email = "shop@example.com"

    /** The row the seal's body ends on, and how far under it the foot must stay. */
    private val sealBodyEnd = 1596
    private val clear = 8

    /** The first row under the card's outer frame, and the last row a stepped-out mark may reach. */
    private val underFrame = 1881
    private val lastRow = 1918

    private class Lang(val tag: String, val shop: String, val shortAddress: String, val longAddress: String) {
        val rtl: Boolean get() = tag == "ur"
    }

    private val english = Lang(
        "en", "Al-Noor Traders", "Main Bazaar, Sample Town",
        "Shop 14, Block C, Main Boulevard Commercial Market, Sample Town, Sample District"
    )
    private val urdu = Lang(
        "ur", "النور ٹریڈرز", "مین بازار",
        "دکان نمبر چودہ، بلاک سی، مین بلیوارڈ کمرشل مارکیٹ، نمونہ ٹاؤن، ضلع نمونہ"
    )

    private fun context(lang: Lang): Context {
        val tmp = Files.createTempDirectory("rk-foot").toFile()
        val settings = FileShopSettings(File(tmp, "s.properties"))
        settings.setBusinessName("Al-Noor Traders")
        settings.setBusinessNameUrdu("النور ٹریڈرز")
        val locale = Locale.forLanguageTag(lang.tag)
        return Context(desktopWords(locale), settings, ProfileImages(tmp), lang.rtl, locale)
    }

    /** One foot: whether the shop gives a number, which address and whether an email. */
    private class Foot(val phone: String?, val address: String?, val mail: String?) {
        override fun toString() = "number ${phone != null}, address ${address?.length ?: 0} letters, email ${mail != null}"
    }

    /** The Thank-you poster with the given foot; [withMark] is the brand mark the app has switched off today. */
    private fun thanks(ctx: Context, lang: Lang, foot: Foot, withMark: Boolean, whatsapp: String = ""): BufferedImage =
        PosterDraw.image(ctx, PosterInput(
            occasion = PosterOccasion.THANKS,
            title = PosterOccasion.THANKS.titleIn(ctx, "agri"),
            message = PosterOccasion.THANKS.messageIn(ctx, "agri"),
            shopName = lang.shop,
            phone = foot.phone,
            address = foot.address,
            logo = null,
            mark = if (withMark) ctx.getString(W.made_with_app) else null,
            fields = PosterFields(whatsappNumber = whatsapp),
            email = foot.mail,
            whatsapp = false
        ))

    /** How many pixels of the rows [from] until [to] are not the same in the two pictures. */
    private fun differing(a: BufferedImage, b: BufferedImage, from: Int, to: Int): Int {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        val ra = IntArray(a.width)
        val rb = IntArray(a.width)
        var n = 0
        for (y in from until minOf(to, a.height)) {
            a.getRGB(0, y, a.width, 1, ra, 0, a.width)
            b.getRGB(0, y, b.width, 1, rb, 0, b.width)
            if (!ra.contentEquals(rb)) for (x in ra.indices) if (ra[x] != rb[x]) n++
        }
        return n
    }

    /** From the first row to the last on which the two pictures differ, or null when they are the same. */
    private fun changedRows(a: BufferedImage, b: BufferedImage): IntRange? {
        var first = -1
        var last = -1
        for (y in 0 until a.height) {
            if (differing(a, b, y, y + 1) > 0) {
                if (first < 0) first = y
                last = y
            }
        }
        return if (first < 0) null else first..last
    }

    private fun keep(name: String, img: BufferedImage) {
        val dir = File("build/poster-proof")
        dir.mkdirs()
        ImageIO.write(img, "png", File(dir, name))
    }

    private val bareFoot = Foot(null, null, null)

    @Test
    fun `the longest Urdu foot with the mark on stays clear of the seal`() {
        val ctx = context(urdu)
        val bare = thanks(ctx, urdu, bareFoot, withMark = true)
        val full = thanks(ctx, urdu, Foot(call, urdu.longAddress, email), withMark = true)
        keep("poster-thanks-ur-long-foot-mark.png", full)
        assertEquals(
            "nothing of the name, number or address is drawn within $clear px of the seal's body",
            0, differing(bare, full, 0, sealBodyEnd + clear)
        )
        assertTrue("the foot is drawn", differing(bare, full, sealBodyEnd + clear, 1880) > 5000)
    }

    @Test
    fun `the mark steps out under the frame and moves nothing else`() {
        val ctx = context(urdu)
        val foot = Foot(call, urdu.longAddress, email)
        val without = thanks(ctx, urdu, foot, withMark = false)
        val with = thanks(ctx, urdu, foot, withMark = true)
        keep("poster-thanks-ur-long-foot-no-mark.png", without)
        val rows = changedRows(without, with)
        assertNotNull("the mark is drawn", rows)
        // The mark is the ONLY difference: the name, number and address stand where they stand without it (the roomy layout);
        // its ink is under the outer frame (rows 1876..1879) and inside the picture.
        assertTrue("the mark is under the frame, first changed row ${rows!!.first}", rows.first >= underFrame)
        assertTrue("the mark is inside the picture, last changed row ${rows.last}", rows.last <= lastRow)
        val ink = differing(without, with, underFrame, 1920)
        assertTrue("the mark is one small line of ink, $ink pixels", ink in 100..3000)
    }

    @Test
    fun `a second number on the sign-off row keeps the foot clear and the mark out`() {
        val ctx = context(urdu)
        val foot = Foot(call, urdu.longAddress, email)
        val bare = thanks(ctx, urdu, bareFoot, withMark = true)
        val one = thanks(ctx, urdu, foot, withMark = true)
        val two = thanks(ctx, urdu, foot, withMark = true, whatsapp = other)
        val twoNoMark = thanks(ctx, urdu, foot, withMark = false, whatsapp = other)
        assertEquals("still clear of the seal", 0, differing(bare, two, 0, sealBodyEnd + clear))
        val rows = changedRows(twoNoMark, two)
        assertNotNull("the mark is drawn", rows)
        assertTrue("the mark still steps out alone, rows $rows", rows!!.first >= underFrame && rows.last <= lastRow)
        assertTrue("the second number is drawn", differing(one, two, sealBodyEnd + clear, underFrame) > 100)
    }

    @Test
    fun `the mark stays inside the frame wherever it has room`() {
        val cases = listOf(
            Triple("English", english, Foot(call, english.longAddress, email)),
            Triple("English", english, Foot(call, english.shortAddress, null)),
            Triple("Urdu", urdu, Foot(call, urdu.shortAddress, null)),
            Triple("Urdu", urdu, Foot(call, urdu.shortAddress, email)),
            Triple("Urdu", urdu, Foot(null, urdu.longAddress, email))
        )
        val contexts = mapOf(english.tag to context(english), urdu.tag to context(urdu))
        for ((label, lang, foot) in cases) {
            val ctx = contexts.getValue(lang.tag)
            val without = thanks(ctx, lang, foot, withMark = false)
            val with = thanks(ctx, lang, foot, withMark = true)
            assertEquals("$label, $foot: nothing of the mark is under the frame", 0, differing(without, with, underFrame, 1920))
            assertTrue("$label, $foot: the mark is drawn inside the card", differing(without, with, 0, underFrame) > 100)
        }
    }

    @Test
    fun `every foot of both languages stays clear of the seal and the mark never half steps out`() {
        for (lang in listOf(english, urdu)) {
            val ctx = context(lang)
            val bare = thanks(ctx, lang, bareFoot, withMark = true)
            for (phone in listOf(call, null)) for (address in listOf(null, lang.shortAddress, lang.longAddress)) for (mail in listOf(email, null)) {
                val foot = Foot(phone, address, mail)
                val name = "${lang.tag}, $foot"
                val without = thanks(ctx, lang, foot, withMark = false)
                val with = thanks(ctx, lang, foot, withMark = true)
                assertEquals("$name: clear of the seal without the mark", 0, differing(bare, without, 0, sealBodyEnd + clear))
                assertEquals("$name: clear of the seal with the mark", 0, differing(bare, with, 0, sealBodyEnd + clear))
                // Either the mark is inside the card, or it is under the frame and it is all that changed: never both.
                val under = differing(without, with, underFrame, 1920)
                val inside = differing(without, with, 0, underFrame)
                assertTrue("$name: the mark is under the frame ($under changed) or inside it ($inside changed), not both", under == 0 || inside == 0)
            }
        }
    }
}
