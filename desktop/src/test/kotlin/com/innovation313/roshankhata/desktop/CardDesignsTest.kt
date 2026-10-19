package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.desktop.card.CardTemplates
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.ProfileImages
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
 * The phone's twenty-four business cards drawn on the computer (Phase 10),
 * English and Urdu, with a made-up shop. Pictures kept in build/card-proof
 * and put on the desktop-test pre-release by desktop.yml, to be looked at.
 */
class CardDesignsTest {

    private fun data(rtl: Boolean, logo: com.innovation313.roshankhata.desktop.gfx.Bitmap? = null) = CardTemplates.CardData(
        name = if (rtl) "النور ایگری اسٹور" else "Al-Noor Agri Store",
        type = if (rtl) "کھاد اور بیج" else "Fertiliser and seeds",
        owner = if (rtl) "احمد علی" else "Ahmed Ali",
        phone = "0300 0000000",
        address = if (rtl) "مین بازار" else "Main Bazaar, Sample Town",
        footer = "Roshan Khata",
        email = "shop@example.com",
        tagline = if (rtl) "گھر تک ڈیلیوری" else "Home delivery",
        qrMessage = "Assalam o Alaikum",
        logo = logo,
        rtl = rtl
    )

    private fun ink(img: BufferedImage): Int {
        val bg = img.getRGB(2, 2)
        var n = 0
        for (y in 0 until img.height step 4) for (x in 0 until img.width step 4) if (img.getRGB(x, y) != bg) n++
        return n
    }

    @Test
    fun `all twenty-four designs draw, in English and Urdu`() {
        val dir = File("build/card-proof").apply { mkdirs() }
        val tmp = Files.createTempDirectory("rk-card").toFile()
        CardTemplates.init(Context(desktopWords(Locale.ENGLISH), FileShopSettings(File(tmp, "s.properties")), ProfileImages(tmp), false))
        assertEquals(24, CardTemplates.all.size)
        for (rtl in listOf(false, true)) {
            for (t in CardTemplates.all) {
                val img = CardDraw.image(t, data(rtl))
                assertEquals(CardTemplates.W, img.width)
                val inked = ink(img)
                assertTrue("card ${t.id} (rtl=$rtl) looks empty: $inked", inked > 2000)
                if (!rtl || t.id in listOf(101, 106, 111)) ImageIO.write(img, "png", File(dir, "card-${if (rtl) "ur" else "en"}-${t.id}.png"))
            }
        }
    }

    @Test
    fun `the QR on a card opens a WhatsApp chat with the shop`() {
        assertEquals("https://wa.me/923000000000?text=Assalam%20o%20Alaikum", CardTemplates.qrPayload(data(false)))
    }

    @Test
    fun `a logo finds a free corner and is drawn`() {
        val logo = BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics(); g.color = java.awt.Color.RED; g.fillRect(0, 0, 200, 200); g.dispose()
        }
        val t = CardTemplates.byId(101)
        val slot = com.innovation313.roshankhata.desktop.card.CardLogo.slot(t.layout(data(false), CardTemplates.W, CardTemplates.H), CardTemplates.W, CardTemplates.H)
        assertNotNull(slot)
        val img = CardDraw.image(t, data(false, com.innovation313.roshankhata.desktop.gfx.Bitmap(logo)))
        val s = slot!!
        val px = img.getRGB(s.centerX().toInt(), s.centerY().toInt())
        assertEquals("the logo's red in its slot", 0xFF, (px shr 16) and 0xFF)
    }
}
