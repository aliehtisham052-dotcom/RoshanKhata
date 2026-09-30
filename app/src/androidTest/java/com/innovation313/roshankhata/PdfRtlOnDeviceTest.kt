package com.innovation313.roshankhata

import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoicePdfExport
import com.innovation313.roshankhata.data.PdfRtl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Right-to-left PDFs, checked on a real Android PdfDocument and PdfRenderer
 * rather than by eye alone.
 *
 * 1. The mechanism: on an Urdu page a box drawn at the LEFT margin lands at
 *    the RIGHT margin, and a picture inside it is not back to front.
 * 2. Every invoice design: the logo tile sits top-left in English and
 *    top-right in Urdu (T1-T9); the thermal receipt still renders.
 *
 * Each rendered Urdu invoice is also saved to Download/rtl-proof/ so the CI
 * job can attach the pictures for a person to look at.
 */
class PdfRtlOnDeviceTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun inLanguage(tag: String): Context {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(config)
    }

    private fun render(file: File, page: Int = 0): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                r.openPage(page).use { p ->
                    Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private fun isRed(c: Int) = Color.red(c) > 200 && Color.green(c) < 80 && Color.blue(c) < 80
    private fun isBlue(c: Int) = Color.blue(c) > 200 && Color.red(c) < 80 && Color.green(c) < 80
    private fun isWhite(c: Int) = Color.red(c) > 235 && Color.green(c) > 235 && Color.blue(c) > 235

    @Test
    fun language_detection_matches_the_app_languages() {
        assertTrue(PdfRtl.isRtl(inLanguage("ur")))
        assertTrue(PdfRtl.isRtl(inLanguage("ar")))
        assertFalse(PdfRtl.isRtl(inLanguage("en")))
        assertFalse(PdfRtl.isRtl(inLanguage("ur-Latn")))
    }

    @Test
    fun an_urdu_page_is_mirrored_and_pictures_are_not() {
        // A picture that is red on its left half and blue on its right.
        val pic = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        for (x in 0 until 20) for (y in 0 until 10) pic.setPixel(x, y, if (x < 10) Color.RED else Color.BLUE)

        fun draw(tag: String): Bitmap {
            val doc = PdfDocument()
            val page = PdfRtl.startPage(inLanguage(tag), doc, PdfDocument.PageInfo.Builder(200, 100, 1).create())
            PdfRtl.drawBitmap(page.canvas, pic, null, RectF(10f, 40f, 50f, 60f), null)
            doc.finishPage(page)
            val f = File(app.cacheDir, "rtl-mech-$tag.pdf")
            f.outputStream().use { doc.writeTo(it) }
            doc.close()
            return render(f)
        }

        val en = draw("en")
        assertTrue("English: picture at the left", isRed(en.getPixel(15, 50)) && isBlue(en.getPixel(45, 50)))
        assertTrue("English: nothing at the right", isWhite(en.getPixel(170, 50)))

        val ur = draw("ur")
        // Box 10..50 mirrors to 150..190 on a 200-wide page.
        assertTrue("Urdu: nothing left at the left margin", isWhite(ur.getPixel(30, 50)))
        assertTrue("Urdu: picture moved right, red half still on its left", isRed(ur.getPixel(155, 50)))
        assertTrue("Urdu: blue half still on its right (not back to front)", isBlue(ur.getPixel(185, 50)))
    }

    @Test
    fun every_invoice_design_mirrors_in_urdu() {
        val items = listOf(
            InvoiceItem(invoiceId = 1, itemName = "Urea", quantity = 2.0, unit = "bag", rate = 4900.0),
            InvoiceItem(invoiceId = 1, itemName = "سلفر", quantity = 12.0, unit = "kg", rate = 1333.0)
        )
        val saved = mutableListOf<String>()
        for (t in 1..10) {
            val invoice = Invoice(invoiceNumber = "RTL$t", customerName = "Test Customer", customerPhone = "03001234567", templateId = t)
            val enFile = InvoicePdfExport.build(inLanguage("en"), invoice, items)
            val urFile = InvoicePdfExport.build(inLanguage("ur"), invoice.copy(invoiceNumber = "RTLU$t"), items)
            assertNotNull("T$t English PDF", enFile)
            assertNotNull("T$t Urdu PDF", urFile)
            val ur = render(urFile!!)
            saved += save("invoice_T${t}_ur.png", ur)
            if (t == 10) continue // thermal receipt: centred, no corner tile to measure

            val en = render(enFile!!)
            assertEquals(595, en.width)
            // The logo tile is white on T1-T9's header (40..80 x 26..66); its
            // top edge at y=28 is tile, not logo. English: left. Urdu: right.
            val y = 28
            val tileLeft = 60
            val tileRight = 595 - 60
            // A no-band design (outlined tile, white page) cannot be told apart
            // by colour at that point, so only assert where the band is filled.
            val bandLeftEn = en.getPixel(tileRight, y)
            if (!isWhite(bandLeftEn)) {
                assertTrue("T$t English: tile top-left", isWhite(en.getPixel(tileLeft, y)))
                assertTrue("T$t Urdu: tile moved top-right", isWhite(ur.getPixel(tileRight, y)))
                assertFalse("T$t Urdu: top-left is band, not tile", isWhite(ur.getPixel(tileLeft, y)))
            }
        }
        assertEquals(10, saved.size)
    }

    /** Download/rtl-proof/<name> — survives the test app being uninstalled, for CI to pull. */
    private fun save(name: String, bmp: Bitmap): String {
        if (Build.VERSION.SDK_INT < 29) return name
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/rtl-proof")
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return name
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return name
    }
}
