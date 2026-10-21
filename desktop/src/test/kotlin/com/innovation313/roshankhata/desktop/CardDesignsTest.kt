package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.CardLanguage
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
 * in all nine card languages (21 Oct 2026), with a made-up shop. Pictures kept
 * in build/card-proof and put on the desktop-test pre-release by desktop.yml,
 * to be looked at: every English and Urdu card on its own, and one sheet of
 * all twenty-four per language (card-sheet-<code>.png).
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

    /** A made-up shop in each language: name, trade, owner, address, tagline. */
    private val shops = mapOf(
        CardLanguage.ENGLISH to listOf("Al-Noor Agri Store", "Fertiliser and seeds", "Ahmed Ali", "Main Bazaar, Sample Town", "Home delivery"),
        CardLanguage.URDU to listOf("النور ایگری اسٹور", "کھاد اور بیج", "احمد علی", "مین بازار", "گھر تک ڈیلیوری"),
        CardLanguage.ROMAN_URDU to listOf("Al-Noor Agri Store", "Khaad aur beej", "Ahmed Ali", "Main Bazaar", "Ghar tak delivery"),
        CardLanguage.SINDHI to listOf("النور زرعي اسٽور", "ڀاڻ ۽ ٻج", "احمد علي", "مين بازار", "گهر تائين پهچ"),
        CardLanguage.ARABIC to listOf("متجر النور الزراعي", "أسمدة وبذور", "أحمد علي", "السوق الرئيسي", "توصيل إلى المنزل"),
        CardLanguage.FARSI to listOf("فروشگاه کشاورزی النور", "کود و بذر", "احمد علی", "بازار اصلی", "ارسال به خانه"),
        CardLanguage.HINDI to listOf("अल-नूर एग्री स्टोर", "खाद और बीज", "अहमद अली", "मेन बाज़ार", "घर तक डिलीवरी"),
        CardLanguage.BENGALI to listOf("আল-নূর এগ্রি স্টোর", "সার ও বীজ", "আহমেদ আলী", "মেইন বাজার", "বাড়িতে ডেলিভারি"),
        CardLanguage.INDONESIAN to listOf("Toko Tani Al-Noor", "Pupuk dan benih", "Ahmed Ali", "Pasar Utama", "Antar ke rumah")
    )

    private fun card(lang: CardLanguage): CardTemplates.CardData {
        val (name, type, owner, address, tagline) = shops.getValue(lang)
        return CardTemplates.CardData(
            name = name, type = type, owner = owner, phone = "0300 0000000", address = address,
            footer = "Roshan Khata", email = "shop@example.com", tagline = tagline,
            qrMessage = "Assalam o Alaikum", rtl = lang.rtl, nastaliq = lang.nastaliq
        )
    }

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
        val thin = ArrayList<String>()
        for (rtl in listOf(false, true)) {
            for (t in CardTemplates.all) {
                val img = CardDraw.image(t, data(rtl))
                assertEquals(CardTemplates.W, img.width)
                // Pictures first, so a card that fails is still there to look at.
                // Every Urdu card too (20 Oct): a Nastaliq tagline once rose out of its pill on some designs.
                ImageIO.write(img, "png", File(dir, "card-${if (rtl) "ur" else "en"}-${t.id}.png"))
                // Something besides the background: a blank card has 0 (of ~52,000 samples).
                val inked = ink(img)
                if (inked < 300) thin += "${t.id}${if (rtl) "ur" else "en"}=$inked"
            }
        }
        assertTrue("cards that look empty: $thin", thin.isEmpty())
    }

    @Test
    fun `every design draws in all nine card languages`() {
        val dir = File("build/card-proof").apply { mkdirs() }
        val tmp = Files.createTempDirectory("rk-card9").toFile()
        CardTemplates.init(Context(desktopWords(Locale.ENGLISH), FileShopSettings(File(tmp, "s.properties")), ProfileImages(tmp), false))
        assertEquals(9, CardLanguage.entries.size)
        val thin = ArrayList<String>()
        val cw = 600
        val ch = 350
        val cols = 4
        for (lang in CardLanguage.entries) {
            val sheet = BufferedImage(cols * cw, (CardTemplates.all.size + cols - 1) / cols * ch, BufferedImage.TYPE_INT_RGB)
            val g = sheet.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.color = java.awt.Color.WHITE
            g.fillRect(0, 0, sheet.width, sheet.height)
            CardTemplates.all.forEachIndexed { i, t ->
                val img = CardDraw.image(t, card(lang))
                val inked = ink(img)
                if (inked < 300) thin += "${t.id}${lang.code}=$inked"
                g.drawImage(img, (i % cols) * cw + 4, (i / cols) * ch + 4, cw - 8, ch - 8, null)
            }
            g.dispose()
            ImageIO.write(sheet, "png", File(dir, "card-sheet-${lang.code}.png"))
        }
        assertTrue("cards that look empty: $thin", thin.isEmpty())
    }

    @Test
    fun `a card language keeps the keys English and Urdu always had`() {
        assertEquals("owner", CardLanguage.ENGLISH.key("owner"))
        assertEquals("owner_ur", CardLanguage.URDU.key("owner"))
        assertEquals("owner_ur_latn", CardLanguage.ROMAN_URDU.key("owner"))
        assertEquals("owner_sd", CardLanguage.SINDHI.key("owner"))
        assertEquals(CardLanguage.INDONESIAN, CardLanguage.of("in"))
        assertEquals(CardLanguage.ROMAN_URDU, CardLanguage.forApp(Locale.forLanguageTag("ur-Latn")))
        assertEquals(CardLanguage.URDU, CardLanguage.forApp(Locale.forLanguageTag("ur")))
        assertEquals(CardLanguage.ENGLISH, CardLanguage.forApp(Locale.FRENCH))
        // Right to left exactly where the script is; Nastaliq only for Urdu.
        assertEquals(setOf(CardLanguage.URDU, CardLanguage.SINDHI, CardLanguage.ARABIC, CardLanguage.FARSI),
            CardLanguage.entries.filter { it.rtl }.toSet())
        assertEquals(listOf(CardLanguage.URDU), CardLanguage.entries.filter { it.nastaliq })
    }

    @Test
    fun `a name's first letter keeps its vowel sign`() {
        assertEquals("कि", CardTemplates.firstLetter("किसान स्टोर"))
        assertEquals("A", CardTemplates.firstLetter(" al-noor"))
        assertEquals("", CardTemplates.firstLetter("  "))
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
