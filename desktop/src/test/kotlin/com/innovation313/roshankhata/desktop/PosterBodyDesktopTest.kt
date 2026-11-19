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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The five designs that end in the call button, drawn with a full foot and with none (19 Nov 2026, the owner's photos:
 * with no number and no address the lowest third of the picture was empty, because the body stood where a full foot
 * had put it). PosterBodyTest holds the arithmetic; this holds that the designs USE it: a poster with nothing at its
 * foot is not the full poster with its foot cut off — its body has come down into the room. Pictures go to
 * build/poster-proof to be looked at (lesson 16). Made-up names and numbers only.
 */
class PosterBodyDesktopTest {

    private val designs = listOf(
        PosterOccasion.NEW_STOCK, PosterOccasion.HOURS, PosterOccasion.OFFER, PosterOccasion.CLOSED, PosterOccasion.CUSTOM
    )

    private fun context(rtl: Boolean): Context {
        val tmp = Files.createTempDirectory("rk-body").toFile()
        val settings = FileShopSettings(File(tmp, "s.properties"))
        settings.setBusinessName("Al-Noor Traders")
        settings.setBusinessNameUrdu("النور ٹریڈرز")
        val locale = Locale.forLanguageTag(if (rtl) "ur" else "en")
        return Context(desktopWords(locale), settings, ProfileImages(tmp), rtl, locale)
    }

    private fun draw(ctx: Context, o: PosterOccasion, rtl: Boolean, phone: String?, address: String?): BufferedImage =
        PosterDraw.image(ctx, PosterInput(
            occasion = o,
            title = if (o.hasWords) o.titleIn(ctx, "agri") else ctx.getString(W.poster_custom_title_hint),
            message = if (o.hasWords) o.messageIn(ctx, "agri") else ctx.getString(W.poster_custom_msg_hint),
            shopName = if (rtl) "النور ٹریڈرز" else "Al-Noor Traders",
            phone = phone, address = address, logo = null, mark = null,
            fields = PosterFields(), email = null, whatsapp = false
        ))

    /** How many pixels in the rows [from] until [to] are not the same in the two pictures. */
    private fun differing(a: BufferedImage, b: BufferedImage, from: Int, to: Int): Int {
        assertEquals(a.width, b.width)
        assertEquals(a.height, b.height)
        var n = 0
        for (y in from until minOf(to, a.height)) for (x in 0 until a.width) if (a.getRGB(x, y) != b.getRGB(x, y)) n++
        return n
    }

    @Test
    fun `a poster with nothing at its foot brings its body down into the room`() {
        val dir = File("build/poster-proof").apply { mkdirs() }
        for (rtl in listOf(false, true)) {
            val ctx = context(rtl)
            val tag = if (rtl) "ur" else "en"
            for (o in designs) {
                val full = draw(ctx, o, rtl, "0300 1234567", if (rtl) "مین بازار" else "Main Bazaar, Sample Town")
                val bare = draw(ctx, o, rtl, null, null)
                ImageIO.write(bare, "png", File(dir, "poster-body-$tag-${o.key}-bare.png"))
                ImageIO.write(full, "png", File(dir, "poster-body-$tag-${o.key}-full.png"))
                // Above the foot the old designs were the very same picture whatever the foot held: 0 pixels apart.
                val moved = differing(full, bare, 200, 1400)
                assertTrue("$tag/${o.key}: the body stood still over an empty foot ($moved pixels differ)", moved > 20_000)
            }
        }
    }

    @Test
    fun `a number with no address under it sits lower than a number with one`() {
        val ctx = context(false)
        for (o in designs) {
            val withAddress = draw(ctx, o, false, "0300 1234567", "Main Bazaar, Sample Town")
            val alone = draw(ctx, o, false, "0300 1234567", null)
            // The button's own band: it has moved, so the two pictures differ there by far more than an address row.
            assertTrue("${o.key}: the call button did not sink", differing(withAddress, alone, 1500, 1800) > 30_000)
        }
    }
}
