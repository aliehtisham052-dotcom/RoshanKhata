package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns
import com.innovation313.roshankhata.desktop.invoice.InvoiceFonts
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
 * The phone's ten invoice designs drawn on the computer (Phase 10), every
 * one, in English and Urdu, with a made-up shop (never real customers). The
 * first page of each is kept (build/invoice-proof) and desktop.yml puts the
 * pictures on the desktop-test pre-release, to be looked at (lesson 16).
 */
class InvoiceDesignsTest {

    private val items = listOf(
        InvoiceItem(invoiceId = 1, itemName = "Urea", quantity = 10.0, unit = "bag", rate = 4650.0),
        InvoiceItem(invoiceId = 1, itemName = "DAP", quantity = 2.0, unit = "bag", rate = 12800.0),
        InvoiceItem(invoiceId = 1, itemName = "Wheat seed", quantity = 50.0, unit = "kg", rate = 190.0)
    )

    private fun context(tag: String): Context {
        val dir = Files.createTempDirectory("rk-invoice").toFile()
        val settings = FileShopSettings(File(dir, "shop.properties")).apply {
            setBusinessName("Al-Noor Agri Store")
            setBusinessAddress("Main Bazaar, Sample Town")
            setBusinessPhone("0300 0000000")
            setBankName("Sample Bank")
            setBankAccountTitle("Al-Noor Agri Store")
            setTermsAndConditions("Goods once sold are not returned.")
        }
        val locale = Locale.forLanguageTag(tag)
        // The app's own RTL rule (Main: BidiPort.isRtlLocale), so every language is drawn as the app draws it.
        return Context(desktopWords(locale), settings, ProfileImages(File(dir, "pictures")), rtl = com.innovation313.roshankhata.ui.BidiPort.isRtlLocale(locale), locale = locale)
    }

    private fun invoice(design: Int) = Invoice(
        id = 1, invoiceNumber = "INV-0007", customerName = "Sample Customer", customerPhone = "0311 1111111",
        invoiceDate = 1_760_000_000_000L, templateId = design, discountPercent = 5.0, receivedAmount = 20000.0,
        note = "Thank you"
    )

    private fun ink(img: BufferedImage): Int {
        var n = 0
        for (y in 0 until img.height step 3) for (x in 0 until img.width step 3) if (img.getRGB(x, y) and 0xFFFFFF != 0xFFFFFF) n++
        return n
    }

    @Test
    fun `the invoice fonts and the mark are packed with the app`() {
        assertTrue(InvoiceFonts.allPresent())
        assertNotNull(InvoiceDesignsTest::class.java.getResource("/roshan_logo.png"))
    }

    @Test
    fun `every one of the ten designs draws a page with the invoice on it`() {
        val dir = File("build/invoice-proof").apply { mkdirs() }
        assertEquals((1..10).toList(), InvoiceDesigns.NAMES.map { it.first })
        // All nine of the app's languages (21 Oct 2026, the owner: check every language's mirror image).
        for (tag in listOf("en", "ur", "ur-Latn", "sd", "ar", "fa", "hi", "bn", "id")) {
            val previous = Locale.getDefault()
            Locale.setDefault(Locale.forLanguageTag(tag))
            try {
                val ctx = context(tag)
                for (design in 1..10) {
                    val doc = InvoiceDesigns.pages(ctx, invoice(design), items)
                    assertNotNull("design $design ($tag) drew nothing", doc)
                    val img = doc!!.picture(0, 1.5f)
                    val inked = ink(img)
                    assertTrue("design $design ($tag) looks empty: $inked", inked > 800)
                    ImageIO.write(img, "png", File(dir, "invoice-$tag-$design.png"))
                }
            } finally {
                Locale.setDefault(previous)
            }
        }
    }

    @Test
    fun `a long invoice runs onto a second page instead of off the first`() {
        val many = (1..40).map { InvoiceItem(invoiceId = 1, itemName = "Item $it", quantity = 1.0, rate = 100.0) }
        val doc = InvoiceDesigns.pages(context("en"), invoice(1), many)!!
        assertTrue(doc.finished.size >= 2)
    }
}
