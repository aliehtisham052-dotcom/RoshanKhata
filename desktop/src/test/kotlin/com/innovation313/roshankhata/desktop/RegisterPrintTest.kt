package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.PurchaseRegisterRow
import com.innovation313.roshankhata.data.SaleRegisterRow
import com.innovation313.roshankhata.data.ShopCountry
import com.innovation313.roshankhata.words.desktopWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

/**
 * The sales and purchase register on paper, the phone's document on the computer (Marhala 4, part 7): the
 * band's identity line by the phone's tax rule, the pages it turns, and proof pictures in English and Urdu
 * (build/statement-proof, onto desktop-test). Made-up shop and parties only.
 */
class RegisterPrintTest {
    private val day = 24L * 60 * 60 * 1000
    private val start = 1_759_276_800_000L // 1 Oct 2025, midnight UTC

    private fun sales(n: Int) = (1..n).map { i ->
        SaleRegisterRow(
            date = start + (i % 25) * day, refNumber = "RK-%06d".format(i), partyName = if (i % 2 == 0) "Ali Raza" else "بلال ٹریڈرز",
            itemName = if (i % 3 == 0) "Emamectin 1.9 EC" else "DAP", batchNumber = if (i % 4 == 0) "EM-2410" else null,
            quantity = (i % 5 + 1).toDouble(), unit = "bag", amount = 3_700.0 * (i % 5 + 1), company = "Sample Agro", registrationNumber = null
        )
    }

    private fun purchases(n: Int) = (1..n).map { i ->
        PurchaseRegisterRow(
            date = start + (i % 25) * day, billNumber = "SB-10$i", billId = i.toLong(), supplierName = "Sample Seeds Co.",
            itemName = "Confidor", batchNumber = "CF-25$i", expiryDate = start + 300 * day, quantity = 24.0, unit = "bottle",
            rate = if (i % 6 == 0) null else 700.0, company = "Sample Agro", registrationNumber = null
        )
    }

    private fun format() = PageFormat().apply {
        paper = Paper().apply { setSize(595.0, 842.0); setImageableArea(0.0, 0.0, 595.0, 842.0) }
    }

    private fun sheet(tag: String, s: List<SaleRegisterRow>, p: List<PurchaseRegisterRow>): RegisterPrint {
        val words = desktopWords(Locale.forLanguageTag(tag))
        val identity = RegisterPrint.identityLine(words, "Main Bazar, Sample Town", "0000000-0", ShopCountry.PAKISTAN)
        return RegisterPrint(words, tag == "ur", "Al-Noor Agri Store", identity, start, start + 30 * day - 1, s, p)
    }

    private fun render(tag: String, s: List<SaleRegisterRow>, p: List<PurchaseRegisterRow>): List<BufferedImage> {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(tag))
        try {
            val r = sheet(tag, s, p)
            val pages = ArrayList<BufferedImage>()
            var i = 0
            while (true) {
                val img = BufferedImage(1190, 1684, BufferedImage.TYPE_INT_RGB)
                val g = img.createGraphics()
                g.color = java.awt.Color.WHITE
                g.fillRect(0, 0, img.width, img.height)
                g.scale(2.0, 2.0)
                val result = r.print(g, format(), i)
                g.dispose()
                if (result == Printable.NO_SUCH_PAGE) break
                pages += img
                i++
            }
            return pages
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `the band names the tax number as the phone does`() {
        val words = desktopWords(Locale.ENGLISH)
        assertEquals("Main Bazar · NTN/STRN: 1234567-8", RegisterPrint.identityLine(words, "Main Bazar", "1234567-8", ShopCountry.PAKISTAN))
        assertEquals("GSTIN: 27ABCDE1234F1Z5", RegisterPrint.identityLine(words, "  ", "27ABCDE1234F1Z5", ShopCountry.INDIA))
        assertEquals("Tax No.: 42", RegisterPrint.identityLine(words, null, "42", ShopCountry.OTHER))
        assertEquals("Main Bazar", RegisterPrint.identityLine(words, "Main Bazar", " ", ShopCountry.PAKISTAN))
        assertNull(RegisterPrint.identityLine(words, null, null, ShopCountry.PAKISTAN))
    }

    @Test
    fun `an empty period is one page, a long one turns pages the preview counts`() {
        assertEquals(1, render("en", emptyList(), emptyList()).size)
        val long = render("en", sales(70), purchases(30))
        assertTrue("70 sales and 30 purchases on ${long.size} pages", long.size >= 4)
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val r = sheet("en", sales(70), purchases(30))
            assertEquals(long.size, r.pageCount())
            assertEquals(r.pageCount(), Sheets.of(r).count)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `the proof pictures, English and Urdu`() {
        val dir = File("build/statement-proof").apply { mkdirs() }
        for (tag in listOf("en", "ur")) {
            val pages = render(tag, sales(30), purchases(8))
            pages.forEachIndexed { i, img -> ImageIO.write(img, "png", File(dir, "register-$tag-${i + 1}.png")) }
            val img = pages.first()
            var ink = 0
            for (y in 300 until 1500 step 4) for (x in 0 until img.width step 4) if (img.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF) ink++
            assertTrue("$tag register page 1 looks empty ($ink inked points)", ink > 500)
        }
    }
}
