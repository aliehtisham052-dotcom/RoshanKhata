package com.innovation313.roshankhata.desktop.invoice

import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.gfx.Color
import com.innovation313.roshankhata.desktop.gfx.Paint
import com.innovation313.roshankhata.desktop.gfx.PdfDocument
import com.innovation313.roshankhata.desktop.gfx.Rect
import com.innovation313.roshankhata.desktop.gfx.RectF
import com.innovation313.roshankhata.desktop.gfx.Typeface
import java.util.Collections
import java.util.WeakHashMap

/**
 * The phone's PdfRtl, unchanged in what it does: in a right-to-left language
 * the page is flipped, and every text and picture is flipped back in its own
 * place, so Urdu reads right to left and the layout mirrors as a whole.
 */
object PdfRtl {
    private val mirrored: MutableMap<Canvas, Float> = Collections.synchronizedMap(WeakHashMap())

    fun startPage(context: Context, doc: PdfDocument, info: PdfDocument.PageInfo): PdfDocument.Page {
        val page = doc.startPage(info)
        if (context.rtl) {
            val w = info.pageWidth.toFloat()
            page.canvas.scale(-1f, 1f, w / 2f, 0f)
            mirrored[page.canvas] = w
        }
        return page
    }

    fun isMirrored(c: Canvas): Boolean = mirrored.containsKey(c)

    fun drawText(c: Canvas, raw: String, x: Float, y: Float, paint: Paint) {
        // The language's own digits when the owner chose them for printed sheets (21 Oct 2026).
        val text = com.innovation313.roshankhata.data.Digits.forPrint(raw)
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

    fun drawBitmap(c: Canvas, bmp: Bitmap, src: Rect?, dst: Rect, paint: Paint?) = drawBitmap(c, bmp, src, RectF(dst), paint)

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
}

/**
 * The phone's PdfBranding pieces an invoice carries: the Roshan Khata mark,
 * the one brand line, and the DOWNLOAD banner (full and compact). A printed
 * page has no link to tap, so nothing is registered for one.
 */
object PdfBranding {
    const val BANNER_HEIGHT = 44f
    const val COMPACT_BANNER_HEIGHT = 40f
    private const val BAND = 0xFFEAF3EE.toInt()
    private const val BAND_EDGE = 0xFFCFE0D5.toInt()
    private const val BRAND_GREEN = 0xFF094C2E.toInt()
    private const val SOFT = 0xFF4B5B52.toInt()
    const val BRAND_NAME = "Roshan Khata"
    const val BRAND_TAGLINE = "Har Hisaab Roshan"

    private val cached: Bitmap? by lazy {
        runCatching { PdfBranding::class.java.getResourceAsStream("/roshan_logo.png")!!.use { javax.imageio.ImageIO.read(it)?.let(::Bitmap) } }.getOrNull()
    }

    @Suppress("UNUSED_PARAMETER")
    fun logo(context: Context): Bitmap? = cached

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    fun drawBrandLine(
        canvas: Canvas, x: Float, baseline: Float, nameSize: Float, tagSize: Float,
        nameColor: Int, tagColor: Int, centered: Boolean = false
    ): Float {
        val name = paint(nameColor, nameSize, bold = true)
        val tag = paint(tagColor, tagSize).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC) }
        val rest = " — $BRAND_TAGLINE"
        val total = name.measureText(BRAND_NAME) + tag.measureText(rest)
        val start = if (centered) x - total / 2f else x
        PdfRtl.asOne(canvas, start, start + total) {
            PdfRtl.drawText(canvas, BRAND_NAME, start, baseline, name)
            PdfRtl.drawText(canvas, rest, start + name.measureText(BRAND_NAME), baseline, tag)
        }
        return start + total
    }

    /** The button's word in the document's language, as the phone's (21 Oct 2026). */
    private fun downloadWord(context: Context): String =
        context.getString("ledger_report_download").uppercase(java.util.Locale.ROOT)

    private fun latin(word: String): Boolean = word.all { it.code < 0x0250 }

    @Suppress("UNUSED_PARAMETER")
    fun drawDownloadBanner(context: Context, doc: PdfDocument, canvas: Canvas, left: Float, top: Float, width: Float): Float {
        val right = left + width
        val bottom = top + BANNER_HEIGHT
        val band = RectF(left, top, right, bottom)
        canvas.drawRoundRect(band, 10f, 10f, Paint().apply { color = BAND; isAntiAlias = true })
        canvas.drawRoundRect(band, 10f, 10f, Paint().apply { color = BAND_EDGE; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true })
        var textLeft = left + 12f
        logo(context)?.let { mark ->
            val size = 30f
            PdfRtl.drawBitmap(canvas, mark, Rect(0, 0, mark.width, mark.height), RectF(left + 8f, top + 7f, left + 8f + size, top + 7f + size), Paint().apply { isAntiAlias = true; isFilterBitmap = true })
            textLeft = left + 8f + size + 8f
        }
        drawBrandLine(canvas, textLeft, top + BANNER_HEIGHT / 2f + 4f, 11f, 10f, BRAND_GREEN, SOFT)
        val pillW = 92f
        val pillH = 20f
        val pill = RectF(right - 10f - pillW, top + (BANNER_HEIGHT - pillH) / 2f, right - 10f, top + (BANNER_HEIGHT + pillH) / 2f)
        canvas.drawRoundRect(pill, 10f, 10f, Paint().apply { color = BRAND_GREEN; isAntiAlias = true })
        val word = downloadWord(context)
        val label = paint(Color.WHITE, 8.5f, bold = true).apply { letterSpacing = if (latin(word)) 0.08f else 0f; textAlign = Paint.Align.CENTER }
        PdfRtl.drawText(canvas, word, pill.centerX(), pill.centerY() + 3f, label)
        return bottom
    }

    @Suppress("UNUSED_PARAMETER")
    fun drawDownloadBannerCompact(context: Context, doc: PdfDocument, canvas: Canvas, left: Float, top: Float, width: Float): Float {
        val right = left + width
        val bottom = top + COMPACT_BANNER_HEIGHT
        val band = RectF(left, top, right, bottom)
        canvas.drawRoundRect(band, 8f, 8f, Paint().apply { color = BAND; isAntiAlias = true })
        canvas.drawRoundRect(band, 8f, 8f, Paint().apply { color = BAND_EDGE; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true })
        val cx = (left + right) / 2f
        drawBrandLine(canvas, cx, top + 14f, 7.5f, 7f, BRAND_GREEN, SOFT, centered = true)
        val pill = RectF(cx - 42f, top + 20f, cx + 42f, top + 35f)
        canvas.drawRoundRect(pill, 7.5f, 7.5f, Paint().apply { color = BRAND_GREEN; isAntiAlias = true })
        val word = downloadWord(context)
        PdfRtl.drawText(canvas, word, cx, pill.centerY() + 2.6f, paint(Color.WHITE, 7f, bold = true).apply { letterSpacing = if (latin(word)) 0.08f else 0f; textAlign = Paint.Align.CENTER })
        return bottom
    }
}
