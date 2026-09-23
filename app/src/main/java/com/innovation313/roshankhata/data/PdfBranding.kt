package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File

/**
 * The logo on every printed page.
 *
 * A statement is the one thing from this app that leaves the shop — it goes to
 * the customer, into a file, sometimes onto a noticeboard. So it is also the
 * best advertisement the app has, and the owner asked for it to carry the mark:
 * the logo, and the Roshan Khata name. Someone who receives a clean, branded
 * statement knows what produced it, and that is worth more than any banner.
 *
 * The logo is loaded once and reused. Decoding it afresh for every page of a
 * long statement would be wasteful for no gain — it is the same image each time.
 */
object PdfBranding {

    private var cached: Bitmap? = null

    fun logo(context: Context): Bitmap? {
        cached?.let { return it }
        return try {
            context.assets.open("roshan_logo.png").use { stream ->
                BitmapFactory.decodeStream(stream)?.also { cached = it }
            }
        } catch (e: Exception) {
            // No logo is not a failure. A statement without the mark is still a
            // correct statement — the numbers are what matter, and they do not
            // depend on an image loading.
            null
        }
    }

    /**
     * Draw the logo at the top-right of a header band, mirroring the business
     * name on the left. Right-aligned so it never collides with the title.
     */
    fun drawInHeader(
        canvas: Canvas,
        logo: Bitmap?,
        pageWidth: Int,
        margin: Float,
        bandHeight: Float
    ) {
        logo ?: return
        val size = bandHeight - 20f
        val right = pageWidth - margin
        val top = (bandHeight - size) / 2f
        val dst = RectF(right - size, top, right, top + size)
        canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height), dst, Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        })
    }

    /**
     * A faint mark across the middle of a page, drawn BEFORE anything else
     * so every figure and line lands on top of it.
     *
     * Two deliberate limits, because a watermark on a money document is a
     * real risk and not just decoration:
     *
     * 1. Alpha stays very low (logo 22/255, wordmark 20/255). On paper and
     *    on screen it reads as a tint, not as text — an amount printed
     *    over it stays exactly as readable as it was. A watermark that
     *    competes with the numbers on a statement is a defect, however
     *    handsome it looks.
     * 2. It is centred and rotated across the sheet's middle — which is
     *    where the empty space is on a short report — and never moves with
     *    the content, so it cannot crowd a table on one page and sit alone
     *    on another.
     *
     * Called per page, so every page carries the identity, including one a
     * reader flips to without seeing the header.
     */
    fun drawWatermark(
        context: Context,
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        tint: Int
    ) {
        // The whole mark lives in the LOWER part of the sheet, rotated
        // around its own centre — not the page's. The owner's diagnosis of
        // the earlier version was exact: anything painted with an opaque
        // colour on top of the watermark ERASES it, and with the mark hung
        // on the page centre it sat right where the table and its solid
        // header strip land. Moving the group down puts it where reports
        // are empty; the translucent zebra lets it read through the rows
        // that do reach it; and the rotated top corner is MEASURED to stay
        // below the opaque table-header strip. First placement used a
        // GUESSED floor of 385 and sank the mark too low — the owner
        // caught it. Traced through the report's own drawing code the
        // strip ends by ~294 on page one, so the compact group's rotated
        // top at ~326 clears it with margin, ninety points higher, every
        // corner still 24pt inside the sheet.
        val cx = pageWidth / 2f
        val cy = pageHeight * 0.62f

        // SIZED FROM THE SHORT SIDE, not the width.
        //
        // Every number below was fitted against a portrait sheet, where the
        // width IS the short side, so "pageWidth * 0.28" quietly meant "28%
        // of 595". The stock register is the one landscape document in the
        // app — 842 across, 595 down — and the same expression handed it 28%
        // of 842 instead. Logo and wordmark came out 42% larger on a sheet
        // that is 42% shorter, and the mark ran to the edges of a page whose
        // other numbers were all checked for 24pt of clearance.
        //
        // The short side is what the mark actually has to fit inside once it
        // is rotated, so that is what it is measured against. On the four
        // portrait documents min() returns the width and not one pixel of
        // their output changes; the register gets the mark at the size it
        // was always drawn for.
        val shortSide = minOf(pageWidth, pageHeight).toFloat()

        canvas.save()
        canvas.rotate(-30f, cx, cy)

        // One composed unit — logo above, wordmark just below, both hung on
        // the page centre — with every corner MEASURED to stay at least
        // 24pt inside the sheet after the rotation. The first version
        // placed the wordmark far below centre at a larger size; rotated,
        // its ends left the page and the printed mark arrived cut off at
        // the edges. Sizes and offsets here are the ones the geometry
        // check passed, not the ones that merely looked right.
        logo(context)?.let { mark ->
            val size = shortSide * 0.28f
            val dst = RectF(
                cx - size / 2f, cy - 95f - size / 2f,
                cx + size / 2f, cy - 95f + size / 2f
            )
            canvas.drawBitmap(mark, Rect(0, 0, mark.width, mark.height), dst, Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                alpha = 18
            })
        }

        // The wordmark back at FULL size — the owner asked for it big, and
        // in the lower half there is room to give it: the shrink was
        // compensating for a bad position, not a real constraint.
        canvas.drawText(
            "ROSHAN KHATA",
            cx,
            cy + 45f,
            Paint().apply {
                isAntiAlias = true
                color = tint
                alpha = 20
                textSize = shortSide * 0.085f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                letterSpacing = 0.18f
            }
        )

        canvas.restore()
    }

    // ------------------------------------------------------------------
    // The download banner
    // ------------------------------------------------------------------

    /** Height of [drawDownloadBanner]; callers reserve this much room above their footer. */
    const val BANNER_HEIGHT = 44f

    private const val BAND = 0xFFEAF3EE.toInt()
    private const val BAND_EDGE = 0xFFCFE0D5.toInt()
    private const val BRAND_GREEN = 0xFF094C2E.toInt()
    private const val INK = 0xFF1A1A18.toInt()
    private const val SOFT = 0xFF4B5B52.toInt()

    /** The app's Play listing. One place, so every document points at the same address. */
    fun storeUrl(context: Context): String =
        "https://play.google.com/store/apps/details?id=${context.packageName}"

    // Links wait here, keyed by the document they belong to, until the file is
    // written: a link can only be added to a PDF that exists. Weak, so a
    // document that is thrown away (the Inspector report lays itself out once
    // just to count pages) takes its entry with it.
    private val pending: MutableMap<PdfDocument, MutableList<PdfLinks.Link>> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap())

    private fun register(doc: PdfDocument, band: RectF, context: Context) {
        // Pages finished so far == the index of the page being drawn.
        val link = PdfLinks.Link(doc.pages.size, band.left, band.top, band.right, band.bottom, storeUrl(context))
        pending.getOrPut(doc) { mutableListOf() }.add(link)
    }

    /**
     * Once the file is written, make every banner drawn on [doc] tappable.
     * Returns false if there was nothing to add or the file's shape was not
     * one this can safely touch; the document is then simply a correct PDF
     * whose button does not open anything.
     */
    fun applyLinks(doc: PdfDocument, file: File): Boolean {
        val links = pending.remove(doc) ?: return false
        return PdfLinks.addLinks(file, links)
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint().apply {
        this.color = color
        textSize = size
        isAntiAlias = true
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /**
     * "Apna khata ab mobile par / Roshan Khata download karein" and a green
     * DOWNLOAD button, in the brand's own colours with its own logo. The whole
     * band is the tap target, not just the button, so a thumb that lands near
     * it still works.
     *
     * Drawn at [top] across [width] and returns its bottom edge.
     */
    fun drawDownloadBanner(
        context: Context,
        doc: PdfDocument,
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float
    ): Float {
        val right = left + width
        val bottom = top + BANNER_HEIGHT
        val band = RectF(left, top, right, bottom)

        canvas.drawRoundRect(band, 10f, 10f, Paint().apply { color = BAND; isAntiAlias = true })
        canvas.drawRoundRect(
            band, 10f, 10f,
            Paint().apply {
                color = BAND_EDGE; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true
            }
        )

        var textLeft = left + 12f
        logo(context)?.let { mark ->
            val size = 30f
            canvas.drawBitmap(
                mark,
                Rect(0, 0, mark.width, mark.height),
                RectF(left + 8f, top + 7f, left + 8f + size, top + 7f + size),
                Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            )
            textLeft = left + 8f + size + 8f
        }

        canvas.drawText("Apna khata ab mobile par", textLeft, top + 19f, paint(INK, 10.5f, bold = true))
        canvas.drawText("Roshan Khata download karein", textLeft, top + 33f, paint(SOFT, 9f))

        val pillW = 92f
        val pillH = 20f
        val pill = RectF(right - 10f - pillW, top + (BANNER_HEIGHT - pillH) / 2f, right - 10f, top + (BANNER_HEIGHT + pillH) / 2f)
        canvas.drawRoundRect(pill, 10f, 10f, Paint().apply { color = BRAND_GREEN; isAntiAlias = true })
        val label = paint(Color.WHITE, 8.5f, bold = true).apply { letterSpacing = 0.08f; textAlign = Paint.Align.CENTER }
        canvas.drawText("DOWNLOAD", pill.centerX(), pill.centerY() + 3f, label)

        register(doc, band, context)
        return bottom
    }

    /**
     * The quietest form of the invitation, for a sheet that goes to someone
     * else's customer: a hairline across the foot of the page, the mark, the
     * app's name and tagline, and a small outlined DOWNLOAD.
     *
     * A statement is the SHOP's document. A bordered advert under the last
     * entry, or a mark across the middle of the page, makes it look like
     * ours; a footer line says where the sheet was made and gets out of the
     * way. [bottom] is the baseline it sits on — pass the page's bottom
     * margin. Returns the top of the line, so a caller can keep clear of it.
     */
    fun drawFootLine(
        context: Context,
        doc: PdfDocument,
        canvas: Canvas,
        left: Float,
        bottom: Float,
        right: Float,
        mark: Bitmap? = null
    ): Float {
        val ruleY = bottom - 20f
        canvas.drawLine(
            left, ruleY, right, ruleY,
            Paint().apply { color = BAND_EDGE; strokeWidth = 0.6f; isAntiAlias = true }
        )

        var x = left
        (mark ?: logo(context))?.let { bmp ->
            val size = 12f
            canvas.drawBitmap(
                bmp,
                Rect(0, 0, bmp.width, bmp.height),
                RectF(x, ruleY + 4f, x + size, ruleY + 4f + size),
                Paint().apply { isAntiAlias = true; isFilterBitmap = true }
            )
            x += size + 6f
        }

        val name = paint(BRAND_GREEN, 8.5f, bold = true)
        canvas.drawText("Roshan Khata", x, ruleY + 13f, name)
        x += name.measureText("Roshan Khata") + 6f
        canvas.drawText(
            "Har Hisaab Roshan",
            x, ruleY + 13f,
            paint(SOFT, 7.5f).apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
        )

        // The tappable area, and the outline that shows where it is.
        val pillW = 62f
        val pillH = 14f
        val pill = RectF(right - pillW, ruleY + 2f, right, ruleY + 2f + pillH)
        canvas.drawRoundRect(
            pill, 7f, 7f,
            Paint().apply {
                color = BRAND_GREEN; style = Paint.Style.STROKE
                strokeWidth = 0.6f; isAntiAlias = true
            }
        )
        canvas.drawText(
            "DOWNLOAD",
            pill.centerX(), pill.centerY() + 2.5f,
            paint(BRAND_GREEN, 6.5f, bold = true).apply {
                letterSpacing = 0.06f
                textAlign = Paint.Align.CENTER
            }
        )

        register(doc, pill, context)
        return ruleY
    }

    /** Height of [drawDownloadBannerCompact]. */
    const val COMPACT_BANNER_HEIGHT = 40f

    /**
     * The same invitation for the narrow thermal receipt: one centred line
     * over a centred button, no logo (there is no room beside it).
     */
    fun drawDownloadBannerCompact(
        context: Context,
        doc: PdfDocument,
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float
    ): Float {
        val right = left + width
        val bottom = top + COMPACT_BANNER_HEIGHT
        val band = RectF(left, top, right, bottom)
        canvas.drawRoundRect(band, 8f, 8f, Paint().apply { color = BAND; isAntiAlias = true })
        canvas.drawRoundRect(
            band, 8f, 8f,
            Paint().apply {
                color = BAND_EDGE; style = Paint.Style.STROKE; strokeWidth = 0.8f; isAntiAlias = true
            }
        )
        val cx = (left + right) / 2f
        canvas.drawText(
            "Roshan Khata download karein", cx, top + 14f,
            paint(INK, 7.5f, bold = true).apply { textAlign = Paint.Align.CENTER }
        )
        val pill = RectF(cx - 42f, top + 20f, cx + 42f, top + 35f)
        canvas.drawRoundRect(pill, 7.5f, 7.5f, Paint().apply { color = BRAND_GREEN; isAntiAlias = true })
        canvas.drawText(
            "DOWNLOAD", cx, pill.centerY() + 2.6f,
            paint(Color.WHITE, 7f, bold = true).apply { letterSpacing = 0.08f; textAlign = Paint.Align.CENTER }
        )
        register(doc, band, context)
        return bottom
    }
}
