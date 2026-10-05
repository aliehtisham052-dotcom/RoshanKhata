package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.innovation313.roshankhata.data.PdfRtl
import com.innovation313.roshankhata.data.PosterOccasion
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A WhatsApp Status poster, 1080 × 1920 (9:16, the size Status shows full
 * screen): the occasion's emblem, a big line, a message, and the shop's
 * name, phone and logo on a white card at the foot.
 *
 * Every line is centred, so the same composition reads correctly in a
 * right-to-left language without mirroring anything (the business card's
 * approach); each block is still laid out in the reading direction so a
 * line that wraps breaks where that language breaks.
 *
 * NOTHING IS CUT. The big line and the message shrink step by step until
 * they fit their space; only the shop's name and address, which the owner
 * controls in Profile, stop at two lines.
 *
 * Fixed colours, not the theme's: the picture goes to other people's phones.
 */
object PosterImage {

    const val WIDTH = 1080
    const val HEIGHT = 1920

    private const val CREAM = 0xFFFFF8E7.toInt()
    private const val GOLD = 0xFFD4A02A.toInt()
    private const val INK = 0xFF1A1A18.toInt()
    private const val MUTED = 0xFF5F5E5A.toInt()

    data class Input(
        val occasion: PosterOccasion,
        val title: String,
        val message: String,
        val shopName: String,
        val phone: String?,
        val address: String?,
        val logo: Bitmap?,
        val mark: String?
    )

    /** Sizes the big line and message ended at — for the on-device test. */
    data class Fit(val titleSize: Float, val titleLines: Int, val messageSize: Float, val messageLines: Int)

    private fun paint(size: Float, color: Int, bold: Boolean, locale: Locale) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textLocale = locale
        }

    private fun block(text: String, p: TextPaint, width: Int, rtl: Boolean, maxLines: Int = Int.MAX_VALUE): StaticLayout {
        val b = StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.1f)
        if (maxLines != Int.MAX_VALUE) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        return b.build()
    }

    /** The largest size from [start] down to [floor] at which [text] fits [maxH] in at most [maxLines]. */
    private fun fit(text: String, start: Float, floor: Float, color: Int, bold: Boolean, width: Int,
                    maxH: Float, maxLines: Int, rtl: Boolean, locale: Locale): StaticLayout {
        var size = start
        while (true) {
            val l = block(text, paint(size, color, bold, locale), width, rtl)
            if ((l.height <= maxH && l.lineCount <= maxLines) || size <= floor) return l
            size -= 4f
        }
    }

    private fun Canvas.put(l: StaticLayout, x: Float, y: Float) {
        save(); translate(x, y); l.draw(this); restore()
    }

    private fun emblem(c: Canvas, crescent: Boolean, cx: Float, cy: Float, r: Float) {
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        if (crescent) {
            val moon = Path().apply { addCircle(cx - r * 0.15f, cy, r, Path.Direction.CW) }
            val cut = Path().apply { addCircle(cx + r * 0.25f, cy - r * 0.12f, r * 0.85f, Path.Direction.CW) }
            moon.op(cut, Path.Op.DIFFERENCE)
            c.drawPath(moon, gold)
            c.drawPath(star(cx + r * 0.55f, cy - r * 0.05f, r * 0.32f, r * 0.13f, 5), gold)
        } else {
            // Eight-point star: two squares turned 45° to each other, a ring inside.
            c.drawPath(star(cx, cy, r, r * 0.62f, 8), gold)
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CREAM; style = Paint.Style.STROKE; strokeWidth = 6f }
            c.drawCircle(cx, cy, r * 0.38f, ring)
        }
    }

    private fun star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int): Path {
        val p = Path()
        for (i in 0 until points * 2) {
            val rad = if (i % 2 == 0) outer else inner
            val a = Math.PI * i / points - Math.PI / 2
            val x = cx + (rad * cos(a)).toFloat()
            val y = cy + (rad * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private fun diamond(c: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        val p = Path().apply { moveTo(cx, cy - s); lineTo(cx + s, cy); lineTo(cx, cy + s); lineTo(cx - s, cy); close() }
        c.drawPath(p, paint)
    }

    /** Draw the poster. Returns the bitmap (caller recycles) and how the text fitted. */
    fun draw(context: Context, input: Input): Pair<Bitmap, Fit> {
        val rtl = PdfRtl.isRtl(context)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val o = input.occasion

        // Ground and frame.
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), o.top, o.bottom, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bg)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; style = Paint.Style.STROKE; strokeWidth = 4f }
        c.drawRoundRect(RectF(40f, 40f, WIDTH - 40f, HEIGHT - 40f), 36f, 36f, frame)
        frame.strokeWidth = 1.5f
        c.drawRoundRect(RectF(56f, 56f, WIDTH - 56f, HEIGHT - 56f), 28f, 28f, frame)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        for ((x, y) in listOf(40f to 40f, WIDTH - 40f to 40f, 40f to HEIGHT - 40f, WIDTH - 40f to HEIGHT - 40f)) diamond(c, x, y, 18f, gold)

        emblem(c, o.crescent, WIDTH / 2f, 300f, 110f)

        // The shop card at the foot, measured first: the words above get what is left.
        val cardW = WIDTH - 2 * 96
        val inner = cardW - 2 * 40
        val showLogo = input.logo
        val name = block(input.shopName.ifBlank { " " }, paint(58f, INK, true, locale), inner, rtl, 2)
        val phone = input.phone?.takeIf { it.isNotBlank() }?.let { block(Format.ltr(it), paint(42f, INK, false, locale), inner, rtl, 1) }
        val addr = input.address?.takeIf { it.isNotBlank() }?.let { block(it, paint(32f, MUTED, false, locale), inner, rtl, 2) }
        val logoH = if (showLogo != null) 130f else 0f
        val cardH = 40f + logoH + (if (showLogo != null) 16f else 0f) + name.height +
            (phone?.let { 10f + it.height } ?: 0f) + (addr?.let { 8f + it.height } ?: 0f) + 40f
        val cardBottom = HEIGHT - 130f
        val cardTop = cardBottom - cardH
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        c.drawRoundRect(RectF(96f, cardTop, WIDTH - 96f, cardBottom), 32f, 32f, white)
        var y = cardTop + 40f
        if (showLogo != null) {
            val s = min(logoH / showLogo.width, logoH / showLogo.height)
            val w = showLogo.width * s; val h = showLogo.height * s
            c.drawBitmap(showLogo, null, RectF((WIDTH - w) / 2f, y + (logoH - h) / 2f, (WIDTH + w) / 2f, y + (logoH + h) / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            y += logoH + 16f
        }
        c.put(name, 136f, y); y += name.height
        phone?.let { y += 10f; c.put(it, 136f, y); y += it.height }
        addr?.let { y += 8f; c.put(it, 136f, y) }

        // The words, centred in the space between the emblem and the card.
        val textW = WIDTH - 2 * 110
        val space = cardTop - 60f - 450f
        val title = fit(input.title.ifBlank { " " }, 112f, 56f, CREAM, true, textW, space * 0.55f, 4, rtl, locale)
        val message = if (input.message.isBlank()) null
            else fit(input.message, 52f, 30f, CREAM, false, textW, space * 0.40f, 6, rtl, locale)
        val gap = 56f
        val total = title.height + (message?.let { gap + it.height } ?: 0f)
        y = 450f + max(0f, (space - total) / 2f)
        c.put(title, 110f, y)
        y += title.height
        if (message != null) {
            val line = Paint().apply { color = GOLD; strokeWidth = 5f }
            c.drawLine(WIDTH / 2f - 90f, y + gap / 2f, WIDTH / 2f + 90f, y + gap / 2f, line)
            c.put(message, 110f, y + gap)
        }

        input.mark?.let {
            val m = block(it, paint(28f, CREAM, false, locale), WIDTH - 200, rtl, 1)
            c.put(m, 100f, HEIGHT - 105f)
        }
        return bmp to Fit(title.paint.textSize, title.lineCount, message?.paint?.textSize ?: 0f, message?.lineCount ?: 0)
    }
}
