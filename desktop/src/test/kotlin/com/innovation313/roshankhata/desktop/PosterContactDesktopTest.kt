package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
import com.innovation313.roshankhata.desktop.poster.PosterContact
import com.innovation313.roshankhata.desktop.poster.PosterDraw
import com.innovation313.roshankhata.desktop.poster.PosterFields
import com.innovation313.roshankhata.desktop.poster.PosterInput
import com.innovation313.roshankhata.desktop.poster.PosterOccasion
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The poster's own WhatsApp number on the computer (13 Nov 2026): drawn flat from the phone's own files, so what the
 * phone's tests hold about which numbers show (PosterContactTest) is held here about what is DRAWN. A different number
 * adds a line and moves nothing above it; the call number typed again is the picture the "On WhatsApp" tick drew; a
 * WhatsApp number can stand alone. Pictures go to build/poster-proof to be looked at (lesson 16). Made-up numbers only.
 */
class PosterContactDesktopTest {

    private val call = "0300 1234567"
    private val other = "0300 7654321"

    /** Designs whose call number is a gold pill, and designs that sign off with a row of text. */
    private val pillDesigns = listOf(
        PosterOccasion.NEW_STOCK, PosterOccasion.HOURS, PosterOccasion.OFFER, PosterOccasion.CLOSED, PosterOccasion.CUSTOM
    )
    private val signOffDesigns = listOf(
        PosterOccasion.EID_FITR, PosterOccasion.RAMAZAN, PosterOccasion.JUMMA, PosterOccasion.AZADI,
        PosterOccasion.EID_ADHA, PosterOccasion.THANKS
    )

    private fun context(rtl: Boolean): Context {
        val tmp = Files.createTempDirectory("rk-contact").toFile()
        val settings = FileShopSettings(File(tmp, "s.properties"))
        settings.setBusinessName("Al-Noor Traders")
        settings.setBusinessNameUrdu("النور ٹریڈرز")
        val locale = Locale.forLanguageTag(if (rtl) "ur" else "en")
        return Context(desktopWords(locale), settings, ProfileImages(tmp), rtl, locale)
    }

    private fun draw(
        ctx: Context, o: PosterOccasion, phone: String?, onWhatsapp: Boolean, number: String, rtl: Boolean = false
    ): BufferedImage = PosterDraw.image(ctx, PosterInput(
        occasion = o,
        title = if (o.hasWords) o.titleIn(ctx, "agri") else ctx.getString(W.poster_custom_title_hint),
        message = if (o.hasWords) o.messageIn(ctx, "agri") else ctx.getString(W.poster_custom_msg_hint),
        shopName = if (rtl) "النور ٹریڈرز" else "Al-Noor Traders",
        phone = phone,
        address = if (rtl) "مین بازار" else "Main Bazaar, Sample Town",
        logo = null, mark = null,
        fields = PosterFields(eidDays = 3, ramazanHours = true, reason = PosterFields.Reason.EID.key, whatsappNumber = number),
        email = "shop@example.com",
        whatsapp = onWhatsapp
    ))

    /** How many pixels in the rows [from] until [to] are not the same in the two pictures. */
    private fun differing(a: BufferedImage, b: BufferedImage, from: Int, to: Int): Int {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        var n = 0
        for (y in from until minOf(to, a.height)) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) n++
        return n
    }

    /** The first row on which the two pictures differ, or -1 when they are the same. */
    private fun firstDifferentRow(a: BufferedImage, b: BufferedImage): Int = changedRows(a, b)?.first ?: -1

    /** From the first row to the last on which the two pictures differ, or null when they are the same. */
    private fun changedRows(a: BufferedImage, b: BufferedImage): IntRange? {
        var first = -1
        var last = -1
        for (y in 0 until a.height) {
            for (x in 0 until a.width) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    if (first < 0) first = y
                    last = y
                    break
                }
            }
        }
        return if (first < 0) null else first..last
    }

    private fun keep(dir: File, name: String, img: BufferedImage) {
        dir.mkdirs()
        ImageIO.write(img, "png", File(dir, name))
    }

    @Test
    fun `the call number typed again is the picture the On WhatsApp tick drew`() {
        val ctx = context(false)
        for (o in pillDesigns + signOffDesigns) {
            val ticked = draw(ctx, o, call, onWhatsapp = true, number = "")
            val typed = draw(ctx, o, call, onWhatsapp = false, number = "+92 300 1234567")
            assertEquals("${o.key}: the same number is one number", -1, firstDifferentRow(ticked, typed))
        }
    }

    @Test
    fun `a different number adds a second pill and moves nothing above it`() {
        val ctx = context(false)
        val dir = File("build/poster-proof")
        for (o in pillDesigns) {
            val one = draw(ctx, o, call, onWhatsapp = true, number = "")
            val two = draw(ctx, o, call, onWhatsapp = true, number = other)
            keep(dir, "poster-contact-en-${o.key}.png", two)
            // The pills' band starts at 1446 on the highest design, its shadow a little above: over that the poster
            // is the very same picture, and under the pills the address rows are too.
            val rows = changedRows(one, two)
            assertTrue("${o.key}: the second number is drawn", rows != null && differing(one, two, 1400, 1800) > 5000)
            assertTrue("${o.key}: nothing above the pills moved, first change at row ${rows?.first}", rows!!.first >= 1400)
            assertTrue("${o.key}: nothing below the pills moved, last change at row ${rows.last}", rows.last < 1800)
        }
    }

    @Test
    fun `a different number joins the call number on a sign-off row and moves nothing above the foot`() {
        val ctx = context(false)
        val dir = File("build/poster-proof")
        for (o in signOffDesigns) {
            val one = draw(ctx, o, call, onWhatsapp = true, number = "")
            val two = draw(ctx, o, call, onWhatsapp = true, number = other)
            keep(dir, "poster-contact-en-${o.key}.png", two)
            val rows = changedRows(one, two)
            assertTrue("${o.key}: the second number is drawn", rows != null)
            // Only the number row changes: one line of text, nothing of the seal, the name or the address rows.
            assertTrue("${o.key}: nothing above the foot moved, first change at row ${rows!!.first}", rows.first >= 1700)
            assertTrue("${o.key}: only the number row changed, rows ${rows.first} to ${rows.last}", rows.last - rows.first < 60)
        }
    }

    @Test
    fun `a WhatsApp number stands alone when there is no call number`() {
        val ctx = context(false)
        val none = draw(ctx, PosterOccasion.NEW_STOCK, null, onWhatsapp = true, number = "")
        val alone = draw(ctx, PosterOccasion.NEW_STOCK, null, onWhatsapp = true, number = other)
        val withCall = draw(ctx, PosterOccasion.NEW_STOCK, call, onWhatsapp = false, number = "")
        keep(File("build/poster-proof"), "poster-contact-en-whatsapp-alone.png", alone)
        assertTrue("one pill for the WhatsApp number", differing(none, alone, 1400, 1800) > 5000)
        // Its pill is the call pill's size and place: the same rows change as for a call number.
        assertEquals(changedRows(none, withCall)?.first, changedRows(none, alone)?.first)
        assertEquals(changedRows(none, withCall)?.last, changedRows(none, alone)?.last)
    }

    @Test
    fun `the closed poster says message when only WhatsApp can reach the shop`() {
        val ctx = context(false)
        val both = draw(ctx, PosterOccasion.CLOSED, call, onWhatsapp = true, number = other)
        val callOnly = draw(ctx, PosterOccasion.CLOSED, call, onWhatsapp = true, number = "")
        val waOnly = draw(ctx, PosterOccasion.CLOSED, null, onWhatsapp = true, number = other)
        // The line above the numbers is the same for a call number alone and for both, and its own for WhatsApp alone.
        assertEquals(0, differing(callOnly, both, 1440, 1500))
        assertTrue(differing(callOnly, waOnly, 1440, 1500) > 200)
    }

    @Test
    fun `every design draws a different number in Urdu too`() {
        val ctx = context(true)
        val dir = File("build/poster-proof")
        for (o in PosterOccasion.entries) {
            val one = draw(ctx, o, call, onWhatsapp = true, number = "", rtl = true)
            val two = draw(ctx, o, call, onWhatsapp = true, number = other, rtl = true)
            keep(dir, "poster-contact-ur-${o.key}.png", two)
            assertTrue("${o.key}: the second number is drawn", firstDifferentRow(one, two) >= 0)
        }
    }

    @Test
    fun `a half typed number is never drawn`() {
        val ctx = context(false)
        val typed = "0300 12"
        assertEquals("", PosterContact.kept(typed))
        val none = draw(ctx, PosterOccasion.NEW_STOCK, call, onWhatsapp = true, number = PosterContact.kept(typed))
        val plain = draw(ctx, PosterOccasion.NEW_STOCK, call, onWhatsapp = true, number = "")
        assertEquals(-1, firstDifferentRow(none, plain))
        assertFalse(PosterContact.kept(typed).isNotEmpty())
    }
}
