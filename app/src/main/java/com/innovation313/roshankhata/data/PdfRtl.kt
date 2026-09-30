package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap

/**
 * Right-to-left layout for every PDF the app prints — invoice, ledger,
 * statement, business, register and inspector reports.
 *
 * The app's screens already mirror in Urdu, Sindhi, Farsi and Arabic
 * (supportsRtl="true"), but a PDF is drawn by hand with x-coordinates
 * measured from the left, so an Urdu invoice came out with "#"/"تفصیل" on
 * the left and "رقم" on the right — English order with Urdu words in it
 * (owner's note, 30 Sep 2026).
 *
 * How it works, rather than rewriting every x in ~280 draw calls:
 *
 * 1. [startPage] flips the whole page horizontally when the app language is
 *    written right to left. Every rectangle, line, band, gradient, column
 *    and margin mirrors at once — nothing can be missed.
 *
 * 2. A flip also turns letters and pictures back to front, so text and
 *    bitmaps go through [drawText] and [drawBitmap], which flip each one
 *    back ABOUT ITS OWN CENTRE. The piece stays where the page-flip put it
 *    (mirrored position) and reads the right way round. A label the code
 *    left-aligns at the left margin ends up right-aligned at the right
 *    margin, which is exactly what RTL layout means.
 *
 * Numbers, amounts and dates are not reversed: each text run keeps its
 * own reading order, so "Rs 15,996" still reads "Rs 15,996".
 *
 * scripts/check_pdf_rtl.py fails the build if a PDF file draws text, a
 * bitmap, or starts a page directly — a raw call would come out as mirror
 * writing in Urdu, and that is the one mistake this design can make.
 *
 * English and Roman Urdu (ur-Latn) are left to right: nothing changes.
 */
object PdfRtl {

    /** Mirrored canvases, with the page width they were mirrored across. Weak: a finished page takes its entry with it. */
    private val mirrored: MutableMap<Canvas, Float> =
        Collections.synchronizedMap(WeakHashMap())

    /** Scripts written right to left. */
    private val RTL_SCRIPTS = setOf("arab", "hebr", "thaa", "syrc", "nkoo", "adlm", "rohg")

    /** Languages whose default script is right to left, when the tag names no script. */
    private val RTL_LANGUAGES = setOf("ar", "fa", "ur", "sd", "he", "iw", "ps", "ug", "yi", "dv", "ckb")

    /**
     * True for a language tag written right to left. An explicit script
     * wins over the language: "ur" is Urdu script (RTL), "ur-Latn" is Roman
     * Urdu (LTR). Plain JVM, no Android — so it is unit-tested directly.
     */
    fun isRtlTag(tag: String): Boolean {
        val locale = Locale.forLanguageTag(tag.replace('_', '-'))
        val script = locale.script.lowercase(Locale.ROOT)
        if (script.isNotEmpty()) return script in RTL_SCRIPTS
        return locale.language.lowercase(Locale.ROOT) in RTL_LANGUAGES
    }

    /** Whether PDFs drawn with this [context] (and so in its language) should be mirrored. */
    fun isRtl(context: Context): Boolean {
        val locales = context.resources.configuration.locales
        if (locales.isEmpty) return false
        return isRtlTag(locales[0].toLanguageTag())
    }

    /**
     * Every PDF page starts here instead of [PdfDocument.startPage]. In a
     * right-to-left language the page's canvas is flipped before anything
     * is drawn on it.
     */
    fun startPage(context: Context, doc: PdfDocument, info: PdfDocument.PageInfo): PdfDocument.Page {
        val page = doc.startPage(info)
        if (isRtl(context)) {
            val w = info.pageWidth.toFloat()
            page.canvas.scale(-1f, 1f, w / 2f, 0f)
            mirrored[page.canvas] = w
        }
        return page
    }

    fun isMirrored(c: Canvas): Boolean = mirrored.containsKey(c)

    /** [Canvas.drawText], readable on a mirrored page. Same arguments, same meaning of [x] and the paint's alignment. */
    fun drawText(c: Canvas, text: String, x: Float, y: Float, paint: Paint) {
        if (!isMirrored(c)) {
            c.drawText(text, x, y, paint)
            return
        }
        val w = paint.measureText(text)
        val centre = when (paint.textAlign) {
            Paint.Align.RIGHT -> x - w / 2f
            Paint.Align.CENTER -> x
            else -> x + w / 2f
        }
        val saved = c.save()
        c.scale(-1f, 1f, centre, 0f)
        c.drawText(text, x, y, paint)
        c.restoreToCount(saved)
    }

    /** [Canvas.drawBitmap] into a float box, the right way round on a mirrored page. */
    fun drawBitmap(c: Canvas, bmp: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        if (!isMirrored(c)) {
            c.drawBitmap(bmp, src, dst, paint)
            return
        }
        val saved = c.save()
        c.scale(-1f, 1f, dst.centerX(), 0f)
        c.drawBitmap(bmp, src, dst, paint)
        c.restoreToCount(saved)
    }

    /** [Canvas.drawBitmap] into an integer box, the right way round on a mirrored page. */
    fun drawBitmap(c: Canvas, bmp: Bitmap, src: Rect?, dst: Rect, paint: Paint?) {
        drawBitmap(c, bmp, src, RectF(dst), paint)
    }

    /**
     * Draws [block] as ONE left-to-right unit: mirrored to its place on a
     * right-to-left page, but not split into pieces whose order swaps. For a
     * line built from several runs side by side that must still read as one
     * phrase — "Roshan Khata — Har Hisaab Roshan" is English and would
     * otherwise print as "Har Hisaab Roshan — Roshan Khata". [left] and
     * [right] are the unit's edges in the code's own coordinates.
     */
    fun asOne(c: Canvas, left: Float, right: Float, block: () -> Unit) {
        val w = mirrored[c]
        if (w == null) {
            block()
            return
        }
        val saved = c.save()
        c.scale(-1f, 1f, (left + right) / 2f, 0f)
        mirrored.remove(c)
        try {
            block()
        } finally {
            mirrored[c] = w
            c.restoreToCount(saved)
        }
    }

    /**
     * Where [r] (in the code's own, left-to-right coordinates) actually
     * lands on the printed page. Needed for anything written into the file
     * by position rather than drawn — the DOWNLOAD link's tappable area —
     * which must follow the button to its mirrored place.
     */
    fun onPage(c: Canvas, r: RectF): RectF {
        val w = mirrored[c] ?: return r
        return RectF(w - r.right, r.top, w - r.left, r.bottom)
    }
}
