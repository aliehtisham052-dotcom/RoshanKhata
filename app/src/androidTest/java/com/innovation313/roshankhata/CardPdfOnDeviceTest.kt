package com.innovation313.roshankhata

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.innovation313.roshankhata.data.CardPdf
import com.innovation313.roshankhata.ui.CardTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The print-ready business card PDF (4 Oct 2026), opened with Android's own
 * PdfRenderer the way a print shop's software would open it: one page,
 * 3.75 x 2.25 inches (a 3.5 x 2 card plus 0.125 inch bleed each side), and the
 * bleed carrying the card's colour rather than a white edge for the cutter to
 * expose.
 */
@RunWith(AndroidJUnit4::class)
class CardPdfOnDeviceTest {

    private val data = CardTemplates.CardData(
        name = "Al-Noor Zarai Store", type = "Pesticides · Seeds", owner = "Ehtisham Ali",
        phone = "0300 1234567", address = "Main Bazar, Pasrur", footer = "",
        qrMessage = "Salam"
    )

    @Test
    fun printSizeWithColouredBleed() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        CardTemplates.init(ctx)
        // Seal: a deep green card edge to edge, so its bleed must be green too.
        val file = CardPdf.write(ctx, CardTemplates.byId(102), data)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                assertEquals(1, r.pageCount)
                r.openPage(0).use { page ->
                    assertEquals(270, page.width)   // 3.75 in at 72 pt/in
                    assertEquals(162, page.height)  // 2.25 in
                    val bmp = Bitmap.createBitmap(page.width * 4, page.height * 4, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    // A point inside the bleed, outside the trim line.
                    val px = bmp.getPixel(8, bmp.height / 2)
                    assertTrue(
                        "bleed should carry the card's green, was #${Integer.toHexString(px)}",
                        Color.green(px) > Color.red(px) && Color.red(px) < 120
                    )
                }
            }
        }
    }

    @Test
    fun everyDesignWritesAPdf() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        CardTemplates.init(ctx)
        CardTemplates.all.forEach { tpl ->
            val f = CardPdf.write(ctx, tpl, data)
            assertTrue("design ${tpl.id} wrote an empty PDF", f.length() > 1000)
        }
    }
}
