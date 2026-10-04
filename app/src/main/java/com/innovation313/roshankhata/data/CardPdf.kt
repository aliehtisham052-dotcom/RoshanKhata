package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.innovation313.roshankhata.ui.CardTemplates
import java.io.File
import java.io.FileOutputStream

/**
 * The business card as a print-ready PDF (4 Oct 2026).
 *
 * Print-shop file specs agree on the essentials: a 3.5 x 2 inch card, an extra
 * 0.125 inch of artwork on every side that the cutter trims away (the bleed),
 * important text kept a further 0.125 inch inside the cut, and images at 300
 * DPI or more at printed size. So the page here is 3.75 x 2.25 inches.
 *
 * The card is drawn once at 1225 x 700 — exactly the 3.5 : 2 shape, so nothing
 * is stretched — which prints at 350 DPI. The bleed is filled with the same
 * card drawn slightly larger underneath, so the colour runs past the cut line
 * the way a printer expects instead of leaving a white hairline when the cut
 * wanders. Every design keeps its text at least 60 px (0.17 inch) from the
 * edge, inside the 0.125 inch safe zone.
 *
 * The page goes through [PdfRtl] like every PDF in the app; the card is one
 * picture, so in a right-to-left language it still prints the right way round.
 */
object CardPdf {

    private const val PT_PER_IN = 72f
    private const val TRIM_W_IN = 3.5f
    private const val TRIM_H_IN = 2f
    private const val BLEED_IN = 0.125f

    /** Pixel width that gives the card exactly the 3.5 : 2 shape at H = 700. */
    const val PRINT_W = 1225

    fun write(context: Context, template: CardTemplates.Template, data: CardTemplates.CardData): File {
        val bmp = Bitmap.createBitmap(PRINT_W, CardTemplates.H, Bitmap.Config.ARGB_8888)
        template.draw(Canvas(bmp), data, PRINT_W, CardTemplates.H, -1f)

        val pageW = ((TRIM_W_IN + 2 * BLEED_IN) * PT_PER_IN).toInt()   // 270 pt
        val pageH = ((TRIM_H_IN + 2 * BLEED_IN) * PT_PER_IN).toInt()   // 162 pt
        val bleed = BLEED_IN * PT_PER_IN                                 // 9 pt
        val doc = PdfDocument()
        try {
            val page = PdfRtl.startPage(context, doc, PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
            val c = page.canvas
            val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            // Underlay: the card stretched to the full page, so the bleed carries its colours.
            PdfRtl.drawBitmap(c, bmp, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), smooth)
            // The card itself, exactly at trim size.
            PdfRtl.drawBitmap(c, bmp, null, RectF(bleed, bleed, pageW - bleed, pageH - bleed), smooth)
            doc.finishPage(page)
            val dir = File(context.cacheDir, "cards").apply { mkdirs() }
            val file = File(dir, "dukan-card-print.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            return file
        } finally {
            doc.close()
            bmp.recycle()
        }
    }
}
