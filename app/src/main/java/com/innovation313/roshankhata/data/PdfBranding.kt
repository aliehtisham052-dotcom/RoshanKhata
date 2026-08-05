package com.innovation313.roshankhata.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

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
        val cx = pageWidth / 2f
        val cy = pageHeight / 2f

        canvas.save()
        canvas.rotate(-30f, cx, cy)

        logo(context)?.let { mark ->
            val size = pageWidth * 0.42f
            val dst = RectF(
                cx - size / 2f, cy - size / 2f - 30f,
                cx + size / 2f, cy + size / 2f - 30f
            )
            canvas.drawBitmap(mark, Rect(0, 0, mark.width, mark.height), dst, Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                alpha = 22
            })
        }

        canvas.drawText(
            "ROSHAN KHATA",
            cx,
            cy + pageWidth * 0.26f,
            Paint().apply {
                isAntiAlias = true
                color = tint
                alpha = 20
                textSize = pageWidth * 0.085f
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                letterSpacing = 0.18f
            }
        )

        canvas.restore()
    }
}
