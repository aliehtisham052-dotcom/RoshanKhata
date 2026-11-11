package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.SealMark
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.invoice.InvoiceTemplateKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The seal with shops named in every script and at every length, drawn on the computer (11 Nov 2026): the rim is
 * shaped whole and then bent (gfx drawTextOnPath), so Urdu stays joined and a long name stays inside its band. The
 * picture goes to the desktop-test release with the other proofs; made-up names (lesson 80).
 */
class SealNamesProofTest {
    @Test
    fun sealsWithEveryKindOfName() {
        val names = listOf(
            "AL-NOOR TRADERS", "AL-NOOR GENERAL STORE AND WHOLESALE TRADERS",
            "النور ٹریڈرز", "النور جنرل اسٹور اینڈ ہول سیل ٹریڈرز", "अल-नूर ट्रेडर्स", "আল-নূর ট্রেডার্স"
        )
        val side = 420
        val bmp = Bitmap.createBitmap(side * 3, side * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFFFFF.toInt())
        names.forEachIndexed { i, n ->
            InvoiceTemplateKit.drawRecordSeal(
                c, side * (i % 3) + side / 2f, side * (i / 3) + side / 2f, side * 0.86f, "11 NOV 2026", "INV-000005", top = n
            )
        }
        SealMark.clear()
        val dir = File("build/screen-proof").apply { mkdirs() }
        val out = File(dir, "seal-names.png")
        ImageIO.write(bmp.image, "png", out)
        assertTrue(out.length() > 0)
    }

    @Test
    fun theSealSaysWhereOnThePageItLanded() {
        // The stamp lands on the printed seal (11 Nov 2026): the mark carries the place as shares of the page,
        // through whatever the page is moved by.
        val c = Canvas(595, 842)
        c.translate(10f, 20f)
        InvoiceTemplateKit.drawRecordSeal(c, 390f, 680f, 92f, "11 NOV 2026", "INV-000005", top = "AL-NOOR TRADERS")
        val m = SealMark.take()!!
        assertTrue(m.placed)
        assertEquals("AL-NOOR TRADERS", m.top)
        assertEquals(400f / 595f, m.x, 0.001f)
        assertEquals(700f / 842f, m.y, 0.001f)
        assertEquals(92f / 595f, m.size, 0.001f)
        assertEquals(842f / 595f, m.aspect, 0.001f)
    }
}
