package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.InvoiceFonts
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The business card designs (redesigned 4 Oct 2026).
 *
 * Twenty-four cards the owner chose one by one: ten general styles, ten built
 * for a trade (medical, mobile, tailor, hardware, dairy, kiryana, auto,
 * furniture, salon, solar), and four of the original shapes rebuilt to the
 * same standard. Each follows the rules he set:
 *  - the shop's name once, the owner's name once, a number shown once even when
 *    it is both the phone and the WhatsApp line;
 *  - every field optional — a blank one is simply not drawn and the rest close up;
 *  - nothing may run off the card or under a shape: text is measured, wrapped
 *    onto a second line, and only then trimmed;
 *  - modern contact lines: WhatsApp, email, website/social, and a QR code that
 *    opens a WhatsApp chat with the shop.
 *
 * Drawn on the phone with shapes, gradients and the app's own fonts. Nothing is
 * downloaded and no artwork is bundled.
 *
 * [Template.draw] takes a phase: -1 for the still card that is shared, or 0..1
 * for the looping preview (a needle that sweeps, rays that flow, cells that
 * light). Every moving part has a resting position, and the shared image is
 * always that resting card.
 */
object CardTemplates {

    /** What a card has to show. Blank fields are skipped. */
    data class CardData(
        val name: String,
        val type: String,
        val owner: String,
        val phone: String,
        val address: String,
        val footer: String,
        val whatsapp: String = "",
        val email: String = "",
        val web: String = "",
        /** One short line the shop wants seen: "24/7", "Ghar tak delivery". */
        val tagline: String = ""
    )

    /** One design: a name for the picker, and how to draw it. */
    class Template(
        val id: Int,
        val labelRes: Int,
        /** A soft band of light crosses this card in the preview. */
        val shine: Boolean = false,
        val draw: (Canvas, CardData, Int, Int, Float) -> Unit
    )

    const val W = 1200
    const val H = 700

    private val INK = Color.parseColor("#1A1A18")
    private val WHITE = Color.WHITE
    private val GOLD = Color.parseColor("#C9A227")
    private val GOLD_PALE = Color.parseColor("#EBCB78")
    private val AMBER = Color.parseColor("#EF9F27")

    // ---------- fonts ----------

    private var sansTf: Typeface = Typeface.DEFAULT
    private var boldTf: Typeface = Typeface.DEFAULT_BOLD
    private var serifTf: Typeface = Typeface.SERIF

    /** Loads the app's fonts once. Without it the cards still draw, in the system font. */
    fun init(context: Context) {
        sansTf = InvoiceFonts.manrope(context)
        boldTf = InvoiceFonts.manropeBold(context)
        serifTf = InvoiceFonts.playfairDisplay(context)
    }

    // ---------- paint helpers ----------

    private fun paint(
        size: Float,
        colour: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT,
        serif: Boolean = false,
        spacing: Float = 0f
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        textSize = size
        textAlign = align
        typeface = if (serif) serifTf else if (bold) boldTf else sansTf
        if (serif && bold) isFakeBoldText = false
        letterSpacing = spacing
    }

    private fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }

    private fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun linear(x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP)
        }

    private fun alpha(colour: Int, a: Int) =
        Color.argb(a, Color.red(colour), Color.green(colour), Color.blue(colour))

    private fun path(block: Path.() -> Unit) = Path().apply(block)

    private fun live(t: Float) = t >= 0f

    /** 0..1..0 over one loop, for things that breathe. */
    private fun wave(t: Float) = if (t < 0f) 1f else (0.5f - 0.5f * cos(2 * PI * t)).toFloat()

    // ---------- text helpers ----------

    /** The smallest type this card will set. Below it, wrap or trim instead. */
    private const val MIN_READABLE = 22f

    private fun ellipsise(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var end = text.length
        while (end > 1 && p.measureText(text.take(end) + "…") > max) end--
        return text.take(end).trimEnd() + "…"
    }

    /** Break on word boundaries into at most [maxLines]; trim any line that still overflows. */
    private fun wrap(text: String, p: Paint, max: Float, maxLines: Int = 2): List<String> {
        if (p.measureText(text) <= max) return listOf(text)
        val words = text.split(' ').filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(candidate) <= max) {
                line = StringBuilder(candidate)
            } else {
                if (line.isNotEmpty()) lines += line.toString()
                line = StringBuilder(word)
                if (lines.size == maxLines) break
            }
        }
        if (lines.size < maxLines && line.isNotEmpty()) lines += line.toString()
        val kept = lines.take(maxLines).toMutableList()
        // Words left over after the last line: say so, rather than drop them silently.
        val used = kept.joinToString(" ").length
        if (used < text.trim().length && kept.isNotEmpty()) {
            kept[kept.size - 1] = ellipsise(kept.last() + " …", p, max)
        }
        return kept.map { ellipsise(it, p, max) }.ifEmpty { listOf(ellipsise(text, p, max)) }
    }

    /**
     * A heading of up to [lines] lines, set as large as it fits from [size] down.
     * [top] is the top of the text block; returns the bottom of the block.
     */
    private fun title(
        c: Canvas,
        text: String,
        x: Float,
        top: Float,
        max: Float,
        size: Float,
        colour: Int,
        align: Paint.Align = Paint.Align.LEFT,
        serif: Boolean = false,
        lines: Int = 2,
        lineGap: Float = 1.05f
    ): Float {
        if (text.isEmpty()) return top
        var s = size
        var p = paint(s, colour, bold = !serif, align = align, serif = serif)
        var parts = wrap(text, p, max, lines)
        while (s > 30f && parts.any { it.endsWith("…") }) {
            s -= 3f
            p = paint(s, colour, bold = !serif, align = align, serif = serif)
            parts = wrap(text, p, max, lines)
        }
        var y = top + s * 0.86f
        parts.forEachIndexed { i, part ->
            c.drawText(part, x, y, p)
            if (i < parts.size - 1) y += s * lineGap
        }
        return y + s * 0.2f
    }

    /** One line in spaced capitals — "PESTICIDES · SEEDS · FEED". Returns its baseline. */
    private fun caps(
        c: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        max: Float,
        colour: Int,
        size: Float = 22f,
        align: Paint.Align = Paint.Align.LEFT
    ): Float {
        if (text.isEmpty()) return baseline
        var s = size
        var p = paint(s, colour, bold = true, align = align, spacing = 0.16f)
        val t = text.uppercase()
        while (p.measureText(t) > max && s > 16f) {
            s -= 1f
            p = paint(s, colour, bold = true, align = align, spacing = 0.16f)
        }
        c.drawText(ellipsise(t, p, max), x, baseline, p)
        return baseline
    }

    /** A rounded label with text, e.g. the tagline. Returns its width. */
    private fun pill(
        c: Canvas,
        text: String,
        x: Float,
        y: Float,
        bg: Int,
        fg: Int,
        size: Float = 24f,
        alignRight: Boolean = false,
        max: Float = 520f
    ): Float {
        if (text.isEmpty()) return 0f
        val p = paint(size, fg, bold = true)
        val shown = ellipsise(text, p, max - size * 1.4f)
        val w = p.measureText(shown) + size * 1.4f
        val h = size * 1.7f
        val left = if (alignRight) x - w else x
        c.drawRoundRect(RectF(left, y, left + w, y + h), h / 2, h / 2, fill(bg))
        c.drawText(shown, left + size * 0.7f, y + h * 0.68f, p)
        return w
    }

    // ---------- glyphs ----------

    private enum class Ico { PHONE, WHATSAPP, PHONE_WA, MAIL, WEB, PIN, USER }

    private fun handset(c: Canvas, cx: Float, cy: Float, s: Float, colour: Int) {
        val h = path {
            moveTo(cx - s * 0.42f, cy - s * 0.5f)
            lineTo(cx - s * 0.12f, cy - s * 0.5f)
            lineTo(cx - s * 0.02f, cy - s * 0.12f)
            lineTo(cx - s * 0.2f, cy + s * 0.02f)
            lineTo(cx + s * 0.08f, cy + s * 0.32f)
            lineTo(cx + s * 0.26f, cy + s * 0.16f)
            lineTo(cx + s * 0.5f, cy + s * 0.3f)
            lineTo(cx + s * 0.5f, cy + s * 0.55f)
            lineTo(cx + s * 0.1f, cy + s * 0.55f)
            cubicTo(cx - s * 0.3f, cy + s * 0.4f, cx - s * 0.5f, cy, cx - s * 0.42f, cy - s * 0.5f)
            close()
        }
        c.drawPath(h, fill(colour))
    }

    private fun glyph(c: Canvas, ico: Ico, cx: Float, cy: Float, s: Float, colour: Int) {
        val line = stroke(colour, s * 0.11f)
        when (ico) {
            Ico.PHONE -> handset(c, cx, cy, s, colour)
            Ico.WHATSAPP -> {
                c.drawCircle(cx, cy - s * 0.04f, s * 0.44f, line)
                c.drawPath(path {
                    moveTo(cx - s * 0.36f, cy + s * 0.24f)
                    lineTo(cx - s * 0.48f, cy + s * 0.5f)
                    lineTo(cx - s * 0.18f, cy + s * 0.4f)
                }, line)
                handset(c, cx, cy - s * 0.04f, s * 0.5f, colour)
            }
            Ico.PHONE_WA -> {
                glyph(c, Ico.PHONE, cx - s * 0.36f, cy, s * 0.82f, colour)
                glyph(c, Ico.WHATSAPP, cx + s * 0.52f, cy, s * 0.82f, colour)
            }
            Ico.MAIL -> {
                val r = RectF(cx - s * 0.48f, cy - s * 0.32f, cx + s * 0.48f, cy + s * 0.32f)
                c.drawRoundRect(r, s * 0.06f, s * 0.06f, line)
                c.drawPath(path {
                    moveTo(r.left, r.top + s * 0.04f)
                    lineTo(cx, cy + s * 0.06f)
                    lineTo(r.right, r.top + s * 0.04f)
                }, line)
            }
            Ico.WEB -> {
                c.drawCircle(cx, cy, s * 0.45f, line)
                c.drawOval(RectF(cx - s * 0.2f, cy - s * 0.45f, cx + s * 0.2f, cy + s * 0.45f), line)
                c.drawLine(cx - s * 0.45f, cy, cx + s * 0.45f, cy, line)
            }
            Ico.PIN -> {
                c.drawPath(path {
                    moveTo(cx, cy + s * 0.52f)
                    cubicTo(cx - s * 0.5f, cy, cx - s * 0.4f, cy - s * 0.52f, cx, cy - s * 0.52f)
                    cubicTo(cx + s * 0.4f, cy - s * 0.52f, cx + s * 0.5f, cy, cx, cy + s * 0.52f)
                    close()
                }, line)
                c.drawCircle(cx, cy - s * 0.12f, s * 0.13f, fill(colour))
            }
            Ico.USER -> {
                c.drawCircle(cx, cy - s * 0.2f, s * 0.2f, line)
                c.drawArc(RectF(cx - s * 0.42f, cy + s * 0.08f, cx + s * 0.42f, cy + s * 0.8f), 180f, 180f, false, line)
            }
        }
    }

    /** A simple two-leaf sprout, the mark for farm and dairy shops. */
    private fun sprout(c: Canvas, cx: Float, cy: Float, s: Float, colour: Int) {
        val p = stroke(colour, s * 0.08f)
        c.drawLine(cx, cy + s * 0.5f, cx, cy - s * 0.05f, p)
        c.drawPath(path {
            moveTo(cx, cy)
            cubicTo(cx - s * 0.1f, cy - s * 0.45f, cx - s * 0.5f, cy - s * 0.5f, cx - s * 0.5f, cy - s * 0.5f)
            cubicTo(cx - s * 0.5f, cy - s * 0.1f, cx - s * 0.25f, cy + s * 0.05f, cx, cy)
        }, fill(colour))
        c.drawPath(path {
            moveTo(cx, cy - s * 0.15f)
            cubicTo(cx + s * 0.1f, cy - s * 0.6f, cx + s * 0.5f, cy - s * 0.62f, cx + s * 0.52f, cy - s * 0.62f)
            cubicTo(cx + s * 0.5f, cy - s * 0.22f, cx + s * 0.25f, cy - s * 0.1f, cx, cy - s * 0.15f)
        }, fill(colour))
    }

    // ---------- contact lines ----------

    private fun digits(s: String): String = s.filter { it.isDigit() }.takeLast(10)

    /** The contact rows to show, with the phone and WhatsApp merged when they are one number. */
    private fun rows(d: CardData): List<Pair<Ico, String>> {
        val out = mutableListOf<Pair<Ico, String>>()
        val same = d.whatsapp.isNotEmpty() && d.phone.isNotEmpty() &&
            digits(d.whatsapp) == digits(d.phone)
        if (d.phone.isNotEmpty()) out += (if (same) Ico.PHONE_WA else Ico.PHONE) to d.phone
        if (d.whatsapp.isNotEmpty() && !same) out += Ico.WHATSAPP to d.whatsapp
        if (d.email.isNotEmpty()) out += Ico.MAIL to d.email
        if (d.web.isNotEmpty()) out += Ico.WEB to d.web
        if (d.address.isNotEmpty()) out += Ico.PIN to d.address
        return out
    }

    /**
     * Draw contact rows from [top] (a baseline) downward. Rows that would cross
     * [bottom] are left out rather than drawn over a shape. Returns the next baseline.
     */
    private fun rowList(
        c: Canvas,
        list: List<Pair<Ico, String>>,
        x: Float,
        top: Float,
        max: Float,
        text: Int,
        icon: Int,
        size: Float = 27f,
        gap: Float = 42f,
        align: Paint.Align = Paint.Align.LEFT,
        bottom: Float = H - 30f
    ): Float {
        var y = top
        val right = align == Paint.Align.RIGHT
        for ((ico, value) in list) {
            if (y > bottom) break
            val iconW = if (ico == Ico.PHONE_WA) size * 1.9f else size * 1.15f
            val room = max - iconW
            val p = paint(size, text, align = align)
            val ix = if (right) x - size * 0.5f - (if (ico == Ico.PHONE_WA) size * 0.45f else 0f)
            else x + size * 0.5f + (if (ico == Ico.PHONE_WA) size * 0.36f else 0f)
            val tx = if (right) x - iconW else x + iconW
            glyph(c, ico, ix, y - size * 0.35f, size, icon)
            val allowTwo = ico == Ico.PIN && y + gap * 0.75f <= bottom
            val parts = if (allowTwo) wrap(value, p, room, 2) else listOf(ellipsise(value, p, room))
            parts.forEachIndexed { i, part ->
                c.drawText(part, tx, y, p)
                if (i < parts.size - 1) y += gap * 0.75f
            }
            y += gap
        }
        return y
    }

    /** Owner's name (bold) then the contact rows. Returns the next baseline. */
    private fun info(
        c: Canvas,
        d: CardData,
        x: Float,
        top: Float,
        max: Float,
        text: Int,
        icon: Int,
        ownerColour: Int = text,
        size: Float = 27f,
        gap: Float = 42f,
        align: Paint.Align = Paint.Align.LEFT,
        bottom: Float = H - 30f
    ): Float {
        var y = top
        if (d.owner.isNotEmpty()) {
            val p = paint(size * 1.12f, ownerColour, bold = true, align = align)
            c.drawText(ellipsise(d.owner, p, max), x, y, p)
            y += gap * 1.05f
        }
        return rowList(c, rows(d), x, y, max, text, icon, size, gap, align, bottom)
    }

    /** How tall [info] will be, so a block can be anchored to the bottom of a card. */
    private fun infoHeight(d: CardData, gap: Float = 42f): Float {
        val n = rows(d).size + if (d.owner.isNotEmpty()) 1 else 0
        val twoLineAddress = d.address.length > 34
        return n * gap + (if (twoLineAddress) gap * 0.75f else 0f)
    }

    /** Top baseline that makes an [info] block end at [bottom]. */
    private fun infoTopFor(d: CardData, bottom: Float, gap: Float = 42f): Float =
        bottom - infoHeight(d, gap) + gap * 0.75f

    private fun watermark(c: Canvas, d: CardData, x: Float, y: Float, onDark: Boolean, align: Paint.Align = Paint.Align.RIGHT) {
        if (d.footer.isEmpty()) return
        val base = if (onDark) WHITE else INK
        c.drawText(d.footer, x, y, paint(16f, alpha(base, 96), align = align))
    }

    // ---------- QR ----------

    private val qrCache = HashMap<String, BitMatrix?>()

    /** wa.me link for the WhatsApp number (or the phone, if that is all there is). */
    internal fun qrPayload(d: CardData): String? {
        var n = d.whatsapp.ifEmpty { d.phone }.filter { it.isDigit() }
        if (n.startsWith("00")) n = n.drop(2)
        if (n.startsWith("0")) n = "92" + n.drop(1)
        if (n.length < 11 || n.length > 15) return null
        return "https://wa.me/$n"
    }

    private fun matrix(payload: String): BitMatrix? = qrCache.getOrPut(payload) {
        try {
            QRCodeWriter().encode(
                payload, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(
                    EncodeHintType.MARGIN to 0,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    /** A real, scannable QR on a white tile. Draws nothing without a usable number. */
    private fun qr(c: Canvas, d: CardData, x: Float, y: Float, size: Float, dark: Int = INK): Boolean {
        val m = qrPayload(d)?.let { matrix(it) } ?: return false
        val pad = size * 0.07f
        c.drawRoundRect(RectF(x, y, x + size, y + size), size * 0.05f, size * 0.05f, fill(WHITE))
        val cell = (size - pad * 2) / m.width
        val p = fill(dark)
        for (yy in 0 until m.height) for (xx in 0 until m.width) {
            if (m.get(xx, yy)) {
                val l = x + pad + xx * cell
                val t = y + pad + yy * cell
                c.drawRect(l, t, l + cell + 0.5f, t + cell + 0.5f, p)
            }
        }
        return true
    }

    // ======================================================================
    // The general ten
    // ======================================================================

    /** 1 · Editorial — cream, serif name, clay accent, QR. */
    private fun editorial(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val clay = Color.parseColor("#D85A30")
        c.drawColor(Color.parseColor("#F3EEE4"))
        val b = title(c, d.name, 70f, 64f, 820f, 76f, INK, serif = true)
        caps(c, d.type, 72f, b + 30f, 820f, Color.parseColor("#993C1D"))
        c.drawRect(72f, b + 50f, 72f + 90f, b + 57f, fill(clay))
        val hasQr = qr(c, d, w - 70f - 160f, h - 70f - 160f, 160f)
        val maxW = if (hasQr) 820f else 1060f
        info(c, d, 72f, infoTopFor(d, h - 60f), maxW, INK, clay, bottom = h - 40f)
        val r = 14f + 10f * (if (live(t)) wave(t) else 0f)
        c.drawCircle(w - 80f, 80f, 14f, fill(clay))
        if (live(t)) c.drawCircle(w - 80f, 80f, r, stroke(alpha(clay, (160 * (1 - wave(t))).toInt()), 3f))
        watermark(c, d, w - 70f, 40f, onDark = false)
    }

    /** 2 · Seal — deep green, gold double ring around the name. */
    private fun seal(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        c.drawColor(Color.parseColor("#0E3F26"))
        caps(c, d.type, w / 2f, 78f, 900f, Color.parseColor("#C0DD97"), align = Paint.Align.CENTER)
        val cx = w / 2f
        val cy = 285f
        val outer = stroke(GOLD, 3f).apply {
            pathEffect = DashPathEffect(floatArrayOf(10f, 9f), if (live(t)) t * 38f else 0f)
        }
        c.drawCircle(cx, cy, 152f, outer)
        c.drawCircle(cx, cy, 132f, stroke(GOLD, 5f))
        sprout(c, cx, cy - 58f, 62f, GOLD_PALE)
        title(c, d.name, cx, cy - 6f, 220f, 36f, WHITE, Paint.Align.CENTER, lines = 2, lineGap = 1.1f)
        val all = rows(d)
        val left = mutableListOf<Pair<Ico, String>>()
        val right = mutableListOf<Pair<Ico, String>>()
        all.forEachIndexed { i, r -> if (i % 2 == 0) left += r else right += r }
        val top = 520f
        var ly = top
        if (d.owner.isNotEmpty()) {
            c.drawText(ellipsise(d.owner, paint(30f, GOLD_PALE, true), 500f), 70f, ly, paint(30f, GOLD_PALE, true))
            ly += 44f
        }
        rowList(c, left, 70f, ly, 500f, WHITE, GOLD, bottom = h - 30f)
        rowList(c, right, w - 70f, top, 500f, WHITE, GOLD, align = Paint.Align.RIGHT, bottom = h - 30f)
        watermark(c, d, w / 2f, h - 18f, onDark = true, align = Paint.Align.CENTER)
    }

    /** 3 · Swiss — the number is the design. */
    private fun swiss(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val red = Color.parseColor("#E24B4A")
        c.drawColor(WHITE)
        val barW = if (live(t)) w * min(1f, 0.6f + 0.4f * wave(t)) else w.toFloat()
        c.drawRect(0f, 0f, barW, 22f, fill(red))
        val small = paint(26f, Color.parseColor("#888780"))
        c.drawText(ellipsise(d.name, small, 600f), 70f, 96f, small)
        val hero = d.whatsapp.ifEmpty { d.phone }
        if (hero.isNotEmpty()) {
            val sp = hero.indexOf(' ')
            val parts = if (sp > 0) listOf(hero.substring(0, sp), hero.substring(sp + 1)) else listOf(hero)
            var y = 210f
            parts.forEach { part ->
                val p = paint(116f, INK, bold = true, spacing = -0.03f)
                var s = 116f
                while (p.measureText(part) > 620f && s > 60f) { s -= 4f; p.textSize = s }
                c.drawText(part, 66f, y, p)
                y += s * 0.98f
            }
            glyph(c, Ico.PHONE_WA, 100f, y - 20f, 30f, red)
        } else {
            title(c, d.name, 66f, 130f, 620f, 90f, INK)
        }
        val list = rows(d).filter { it.first != Ico.PHONE && it.first != Ico.PHONE_WA && it.first != Ico.WHATSAPP }
        var ry = 96f
        if (d.type.isNotEmpty()) {
            val p = paint(24f, Color.parseColor("#5F5E5A"), align = Paint.Align.RIGHT)
            c.drawText(ellipsise(d.type, p, 400f), w - 70f, ry, p); ry += 38f
        }
        ry = rowList(c, list, w - 70f, ry, 420f, Color.parseColor("#5F5E5A"), red, size = 24f, gap = 38f, align = Paint.Align.RIGHT, bottom = 470f)
        qr(c, d, w - 70f - 120f, min(ry, 470f), 120f)
        c.drawRect(70f, h - 92f, w - 70f, h - 89f, fill(INK))
        val op = paint(28f, INK)
        c.drawText(ellipsise(d.owner, op, 700f), 70f, h - 46f, op)
        caps(c, d.tagline, w - 70f, h - 46f, 380f, red, align = Paint.Align.RIGHT)
        watermark(c, d, w - 70f, 52f, onDark = false)
    }

    /** 4 · Glass — green gradient, frosted panel holding every contact line. */
    private fun glass(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(),
            linear(0f, 0f, w.toFloat(), h.toFloat(), Color.parseColor("#04342C"), Color.parseColor("#1D9E75")))
        c.drawCircle(w - 90f, 60f, 230f + 12f * wave(t), fill(alpha(Color.parseColor("#9FE1CB"), 46)))
        val b = title(c, d.name, 64f, 48f, 1000f, 60f, WHITE, lines = 1)
        caps(c, d.type, 66f, b + 22f, 1000f, Color.parseColor("#9FE1CB"))
        val panel = RectF(64f, b + 54f, w - 64f, h - 50f)
        c.drawRoundRect(panel, 28f, 28f, fill(alpha(WHITE, 30)))
        c.drawRoundRect(panel, 28f, 28f, stroke(alpha(WHITE, 90), 2f))
        val list = mutableListOf<Pair<Ico, String>>()
        if (d.owner.isNotEmpty()) list += Ico.USER to d.owner
        list += rows(d)
        val colW = (panel.width() - 80f) / 2
        val mid = (list.size + 1) / 2
        val rowsTop = panel.top + max(56f, (panel.height() - mid * 50f) / 2 + 34f)
        rowList(c, list.take(mid), panel.left + 36f, rowsTop, colW, WHITE, Color.parseColor("#9FE1CB"), gap = 50f, bottom = panel.bottom - 20f)
        rowList(c, list.drop(mid), panel.left + 44f + colW, rowsTop, colW, WHITE, Color.parseColor("#9FE1CB"), gap = 50f, bottom = panel.bottom - 20f)
        watermark(c, d, w - 64f, h - 18f, onDark = true)
    }

    /** 5 · Band — a black vertical band carries the shop's name. */
    private fun band(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val amber = AMBER
        c.drawColor(Color.parseColor("#F7F6F2"))
        val bandL = w - 60f - 140f
        c.drawRect(bandL, 0f, w - 60f, h.toFloat(), fill(INK))
        c.save()
        c.rotate(90f, bandL + 70f, h / 2f)
        val np = paint(40f, Color.parseColor("#F3EEE4"), bold = true, align = Paint.Align.CENTER, spacing = 0.12f)
        var s = 40f
        val name = d.name.uppercase()
        while (np.measureText(name) > h - 120f && s > 22f) { s -= 2f; np.textSize = s }
        c.drawText(ellipsise(name, np, h - 120f), bandL + 70f, h / 2f + s * 0.35f, np)
        c.restore()
        c.drawCircle(bandL + 70f, h - 44f, 12f * (0.8f + 0.4f * wave(t)), fill(amber))
        caps(c, d.type, 70f, 92f, bandL - 120f, Color.parseColor("#854F0B"))
        c.drawRect(70f, 116f, 70f + 280f * (if (live(t)) 0.5f + 0.5f * wave(t) else 1f), 122f, fill(amber))
        var y = 196f
        if (d.owner.isNotEmpty()) {
            val op = paint(52f, INK, bold = true)
            c.drawText(ellipsise(d.owner, op, bandL - 120f), 70f, y, op); y += 64f
        }
        val hasQr = qr(c, d, bandL - 40f - 140f, h - 60f - 140f, 140f)
        rowList(c, rows(d), 70f, y + 10f, bandL - 120f - (if (hasQr) 160f else 0f), INK, amber, bottom = h - 40f)
        pill(c, d.tagline, 70f, 22f, INK, amber, size = 20f)
        watermark(c, d, bandL - 30f, 40f, onDark = false)
    }

    /** 6 · Naqsh — geometric circle pattern, olive and gold. */
    private fun naqsh(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        c.drawColor(Color.parseColor("#1F3A2B"))
        val ring = stroke(alpha(GOLD, 70), 2f)
        val off = if (live(t)) t * 70f else 0f
        var yy = -70f + off
        while (yy < h + 70f) {
            var xx = -70f + off
            while (xx < w + 70f) { c.drawCircle(xx, yy, 24f, ring); xx += 70f }
            yy += 70f
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), linear(0f, 0f, w * 0.7f, 0f, alpha(Color.parseColor("#1F3A2B"), 245), alpha(Color.parseColor("#1F3A2B"), 120)))
        val b = title(c, d.name, 70f, 64f, 820f, 64f, GOLD_PALE)
        caps(c, d.type, 72f, b + 28f, 820f, Color.parseColor("#C0DD97"))
        info(c, d, 72f, infoTopFor(d, h - 60f), 760f, WHITE, GOLD, ownerColour = GOLD_PALE, bottom = h - 40f)
        c.save()
        c.rotate(45f, w - 190f, h - 200f)
        c.drawRect(w - 270f, h - 280f, w - 110f, h - 120f, stroke(GOLD, 4f))
        c.restore()
        sprout(c, w - 190f, h - 196f, 74f, GOLD_PALE)
        pill(c, d.tagline, w - 70f, 60f, GOLD, Color.parseColor("#1F3A2B"), alignRight = true)
        watermark(c, d, w - 70f, h - 22f, onDark = true)
    }

    /** 7 · Brutal — thick border, hard shadow, yellow. */
    private fun brutal(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        c.drawColor(Color.parseColor("#FAC775"))
        val panel = RectF(44f, 40f, w - 64f, h - 64f)
        c.drawRect(RectF(panel.left + 16f, panel.top + 16f, panel.right + 16f, panel.bottom + 16f), fill(INK))
        c.drawRect(panel, fill(WHITE))
        c.drawRect(panel, stroke(INK, 7f).apply { strokeCap = Paint.Cap.SQUARE; strokeJoin = Paint.Join.MITER })
        val b = title(c, d.name.uppercase(), panel.left + 40f, panel.top + 34f, panel.width() - 80f, 76f, INK, lineGap = 0.98f)
        if (d.type.isNotEmpty()) {
            val p = paint(20f, Color.parseColor("#FAC775"), bold = true, spacing = 0.14f)
            val txt = ellipsise(d.type.uppercase(), p, panel.width() - 120f)
            val tw = p.measureText(txt)
            c.drawRect(panel.left + 40f, b + 4f, panel.left + 60f + tw, b + 42f, fill(INK))
            c.drawText(txt, panel.left + 50f, b + 31f, p)
        }
        info(c, d, panel.left + 40f, infoTopFor(d, panel.bottom - 30f), panel.width() - 260f, INK, INK, bottom = panel.bottom - 20f)
        c.save()
        c.rotate(if (live(t)) -6f + 12f * wave(t) else 0f, panel.right - 90f, panel.bottom - 70f)
        c.drawText("→", panel.right - 90f, panel.bottom - 40f, paint(110f, INK, bold = true, align = Paint.Align.CENTER))
        c.restore()
        pill(c, d.tagline, panel.right - 30f, panel.top + 26f, INK, Color.parseColor("#FAC775"), size = 20f, alignRight = true, max = 360f)
        watermark(c, d, w - 64f, h - 20f, onDark = false)
    }

    /** 8 · Ticket — dashed edge, a tear-off stub with the QR. */
    private fun ticket(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val brown = Color.parseColor("#854F0B")
        val deep = Color.parseColor("#412402")
        c.drawColor(Color.parseColor("#F1EFE8"))
        val r = RectF(40f, 40f, w - 40f, h - 40f)
        c.drawRect(r, fill(Color.parseColor("#FFF9EC")))
        val dash = stroke(brown, 3f).apply { pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f) }
        c.drawRect(r, dash)
        val stubX = w - 250f
        c.drawLine(stubX, r.top + 30f, stubX, r.bottom - 30f, dash)
        c.drawCircle(stubX, r.top, 26f, fill(Color.parseColor("#F1EFE8")))
        c.drawCircle(stubX, r.bottom, 26f, fill(Color.parseColor("#F1EFE8")))
        caps(c, d.type, 90f, 116f, stubX - 140f, brown)
        val b = title(c, d.name, 90f, 140f, stubX - 140f, 62f, deep, serif = true)
        info(c, d, 90f, max(b + 60f, infoTopFor(d, r.bottom - 40f)), stubX - 140f, deep, Color.parseColor("#BA7517"), bottom = r.bottom - 26f)
        val hasQr = qr(c, d, stubX + 45f, r.top + 70f, 120f, deep)
        if (d.tagline.isNotEmpty()) {
            c.save()
            c.rotate(90f, stubX + 105f, r.top + (if (hasQr) 360f else 260f))
            c.drawText(ellipsise(d.tagline, paint(22f, brown, bold = true, align = Paint.Align.CENTER), 260f),
                stubX + 105f, r.top + (if (hasQr) 368f else 268f), paint(22f, brown, bold = true, align = Paint.Align.CENTER))
            c.restore()
        }
        watermark(c, d, stubX - 30f, r.bottom - 14f, onDark = false)
    }

    /** 9 · Mesh — soft colour fog with a frosted contact panel. */
    private fun mesh(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        c.drawColor(Color.parseColor("#26215C"))
        val drift = if (live(t)) 40f * sin(2 * PI * t).toFloat() else 0f
        fun blob(x: Float, y: Float, r: Float, col: Int) {
            c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(x, y, r, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            })
        }
        blob(w * 0.2f + drift, h * 0.3f, 560f, Color.parseColor("#7F77DD"))
        blob(w * 0.8f - drift, h * 0.2f, 520f, Color.parseColor("#D4537E"))
        blob(w * 0.6f, h * 0.95f + drift, 520f, Color.parseColor("#1D9E75"))
        val panelTop = h - 70f - max(160f, ((rows(d).size + 1 + 1) / 2) * 50f + 40f)
        val b = title(c, d.name, 66f, max(50f, panelTop - 150f), 1060f, 62f, WHITE, lines = 1)
        caps(c, d.type, 68f, b + 22f, 1060f, Color.parseColor("#EEEDFE"))
        val panel = RectF(66f, panelTop, w - 66f, h - 60f)
        c.drawRoundRect(panel, 24f, 24f, fill(alpha(WHITE, 36)))
        c.drawRoundRect(panel, 24f, 24f, stroke(alpha(WHITE, 100), 2f))
        val list = mutableListOf<Pair<Ico, String>>()
        if (d.owner.isNotEmpty()) list += Ico.USER to d.owner
        list += rows(d)
        val colW = (panel.width() - 80f) / 2
        val mid = (list.size + 1) / 2
        rowList(c, list.take(mid), panel.left + 32f, panel.top + 58f, colW, WHITE, WHITE, gap = 50f, bottom = panel.bottom - 16f)
        rowList(c, list.drop(mid), panel.left + 48f + colW, panel.top + 58f, colW, WHITE, WHITE, gap = 50f, bottom = panel.bottom - 16f)
        pill(c, d.tagline, w - 66f, 50f, alpha(WHITE, 60), WHITE, alignRight = true)
        watermark(c, d, w - 66f, h - 22f, onDark = true)
    }

    /** 10 · Field — striped earth panel, the shop beside it. */
    private fun fieldCard(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val copper = Color.parseColor("#B87333")
        c.drawColor(Color.parseColor("#F7F6F2"))
        val pw = w * 0.38f
        c.save()
        c.clipRect(0f, 0f, pw, h.toFloat())
        c.drawColor(Color.parseColor("#173404"))
        val stripe = stroke(Color.parseColor("#1F4A08"), 18f).apply { strokeCap = Paint.Cap.BUTT }
        var x = -h.toFloat() + (if (live(t)) t * 72f else 0f)
        while (x < pw + h) { c.drawLine(x, h.toFloat(), x + h, 0f, stripe); x += 36f }
        c.restore()
        sprout(c, 90f, h - 150f, 90f, Color.parseColor("#C0DD97"))
        caps(c, d.tagline, 60f, h - 60f, pw - 90f, Color.parseColor("#C0DD97"), size = 20f)
        val x0 = pw + 50f
        val room = w - x0 - 60f
        val b = title(c, d.name, x0, 60f, room, 54f, Color.parseColor("#173404"))
        caps(c, d.type, x0, b + 26f, room, copper, size = 20f)
        info(c, d, x0, infoTopFor(d, h - 56f), room, INK, copper, bottom = h - 34f)
        watermark(c, d, w - 50f, 36f, onDark = false)
    }

    // ======================================================================
    // The trade ten
    // ======================================================================

    /** 11 · Medical — a two-tone capsule and a heartbeat line. */
    private fun medical(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val teal = Color.parseColor("#0F6E56")
        val mint = Color.parseColor("#1D9E75")
        c.drawColor(WHITE)
        val pl = w * 0.62f
        c.drawRect(pl, 0f, w.toFloat(), h.toFloat(), fill(Color.parseColor("#E1F5EE")))
        c.save()
        c.clipRect(pl, 0f, w.toFloat(), h.toFloat())
        val bob = if (live(t)) -10f * wave(t) else 0f
        c.rotate(32f, w - 150f, 280f + bob)
        val cap = RectF(w - 230f, 80f + bob, w - 70f, 480f + bob)
        c.drawRoundRect(RectF(cap.left + 10f, cap.top + 18f, cap.right + 10f, cap.bottom + 18f), 80f, 80f, fill(alpha(teal, 40)))
        c.save(); c.clipRect(cap.left, cap.top, cap.right, cap.centerY())
        c.drawRoundRect(cap, 80f, 80f, fill(teal)); c.restore()
        c.save(); c.clipRect(cap.left, cap.centerY(), cap.right, cap.bottom)
        c.drawRoundRect(cap, 80f, 80f, fill(WHITE)); c.restore()
        c.drawRoundRect(cap, 80f, 80f, stroke(teal, 7f))
        c.restore()
        val ecg = path {
            moveTo(pl, h - 50f); lineTo(pl + 80f, h - 50f); lineTo(pl + 100f, h - 90f)
            lineTo(pl + 124f, h - 14f); lineTo(pl + 146f, h - 70f); lineTo(pl + 162f, h - 50f); lineTo(w.toFloat(), h - 50f)
        }
        c.drawPath(ecg, stroke(mint, 5f).apply {
            if (live(t)) pathEffect = DashPathEffect(floatArrayOf(700f * t + 1f, 1400f), 0f)
        })
        val b = title(c, d.name, 70f, 64f, pl - 110f, 62f, Color.parseColor("#0C3D2A"))
        c.drawRect(72f, b + 8f, 72f + 70f, b + 15f, fill(mint))
        caps(c, d.type, 72f, b + 52f, pl - 110f, teal)
        val end = info(c, d, 72f, infoTopFor(d, h - (if (d.tagline.isNotEmpty()) 110f else 56f)), pl - 110f, INK, mint, bottom = h - 70f)
        pill(c, d.tagline, 72f, min(end - 20f, h - 84f), teal, WHITE)
        watermark(c, d, pl - 30f, 40f, onDark = false)
    }

    /** 12 · Mobile — the shop as an app icon, with signal bars. */
    private fun mobile(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val blue = Color.parseColor("#378ADD")
        c.drawColor(Color.parseColor("#0B0F14"))
        val tile = RectF(64f, h / 2f - 140f, 344f, h / 2f + 140f)
        c.drawRoundRect(RectF(tile.left, tile.top + 18f, tile.right, tile.bottom + 18f), 64f, 64f, fill(alpha(blue, 70)))
        c.drawRoundRect(tile, 64f, 64f, linear(tile.left, tile.top, tile.right, tile.bottom, blue, Color.parseColor("#7F77DD")))
        val initial = d.name.trim().take(1).uppercase().ifEmpty { "•" }
        c.drawText(initial, tile.centerX(), tile.centerY() + 62f, paint(176f, WHITE, bold = true, align = Paint.Align.CENTER))
        val x0 = 400f
        val room = w - x0 - 64f
        val b = title(c, d.name, x0, 60f, room, 56f, WHITE)
        caps(c, d.type, x0, b + 24f, room, Color.parseColor("#85B7EB"))
        info(c, d, x0, infoTopFor(d, h - 60f), room - 120f, Color.parseColor("#F1EFE8"), blue, ownerColour = WHITE, bottom = h - 40f)
        val heights = floatArrayOf(30f, 52f, 76f, 104f)
        heights.forEachIndexed { i, bh ->
            val k = if (live(t)) 0.55f + 0.45f * wave((t + i * 0.15f) % 1f) else 1f
            val hh = bh * k
            c.drawRoundRect(RectF(w - 64f - (4 - i) * 30f, h - 60f - hh, w - 64f - (4 - i) * 30f + 20f, h - 60f), 5f, 5f, fill(blue))
        }
        pill(c, d.tagline, w - 64f, 40f, alpha(blue, 70), WHITE, alignRight = true, max = 380f)
        watermark(c, d, w - 64f, h - 20f, onDark = true)
    }

    /** 13 · Tailor — a script name and a measuring tape along the foot. */
    private fun tailor(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val clay = Color.parseColor("#D85A30")
        c.drawColor(Color.parseColor("#FBF3F2"))
        val b = title(c, d.name, 70f, 50f, w - 140f, 86f, Color.parseColor("#4A1A14"), serif = true, lines = 1)
        caps(c, d.type, 72f, b + 30f, w - 140f, Color.parseColor("#993C1D"))
        val tapeTop = h - 70f
        info(c, d, 72f, infoTopFor(d, tapeTop - 34f), 720f, INK, clay, bottom = tapeTop - 20f)
        if (d.tagline.isNotEmpty()) {
            val p = paint(26f, Color.parseColor("#993C1D"), align = Paint.Align.RIGHT)
            wrap(d.tagline, p, 340f, 2).forEachIndexed { i, s -> c.drawText(s, w - 70f, tapeTop - 70f + i * 34f, p) }
        }
        c.drawRect(0f, tapeTop, w.toFloat(), h.toFloat(), fill(Color.parseColor("#FAC775")))
        val shift = if (live(t)) t * 90f else 0f
        var x = -90f + shift
        var i = 0
        val tick = stroke(INK, 2f).apply { strokeCap = Paint.Cap.BUTT }
        while (x < w + 90f) {
            val long = i % 5 == 0
            c.drawLine(x, h.toFloat(), x, h - (if (long) 36f else 18f), tick)
            x += 18f; i++
        }
        watermark(c, d, w - 70f, 40f, onDark = false)
    }

    /** 14 · Hardware — hazard stripes, heavy type, a big initial. */
    private fun hardware(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val amber = AMBER
        c.drawColor(INK)
        fun hazard(top: Float, bottom: Float) {
            c.save(); c.clipRect(0f, top, w.toFloat(), bottom)
            c.drawColor(INK)
            val s = stroke(amber, 22f).apply { strokeCap = Paint.Cap.BUTT }
            var x = -60f + (if (live(t)) t * 44f else 0f)
            while (x < w + 60f) { c.drawLine(x, bottom, x + (bottom - top), top, s); x += 44f }
            c.restore()
        }
        hazard(0f, 34f)
        hazard(h - 18f, h.toFloat())
        val initial = d.name.trim().take(1).uppercase()
        c.drawText(initial, w - 70f, h - 70f, paint(320f, Color.parseColor("#2E2A24"), bold = true, align = Paint.Align.RIGHT))
        val b = title(c, d.name.uppercase(), 70f, 70f, w - 380f, 84f, amber, lineGap = 0.95f)
        caps(c, d.type, 72f, b + 26f, w - 380f, Color.parseColor("#9C9A92"))
        info(c, d, 72f, infoTopFor(d, h - 60f), w - 420f, Color.parseColor("#F1EFE8"), amber, ownerColour = WHITE, bottom = h - 40f)
        caps(c, d.tagline, w - 70f, h - 48f, 300f, amber, size = 20f, align = Paint.Align.RIGHT)
        watermark(c, d, w - 70f, 64f, onDark = true)
    }

    /** 15 · Dairy — a rising sun over green hills. */
    private fun dairy(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        c.drawColor(Color.parseColor("#FFF4D6"))
        val sy = 220f - (if (live(t)) 18f * wave(t) else 0f)
        if (live(t)) c.drawCircle(w - 260f, sy, 120f + 26f * wave(t), fill(alpha(AMBER, (70 * (1 - wave(t))).toInt())))
        c.drawCircle(w - 260f, sy, 110f, fill(AMBER))
        c.drawOval(RectF(-300f, h * 0.55f, w * 0.72f, h * 1.9f), fill(Color.parseColor("#639922")))
        c.drawOval(RectF(w * 0.3f, h * 0.62f, w + 360f, h * 2.1f), fill(Color.parseColor("#3B6D11")))
        val b = title(c, d.name, 70f, 50f, 640f, 58f, Color.parseColor("#173404"))
        caps(c, d.type, 72f, b + 26f, 640f, Color.parseColor("#854F0B"))
        info(c, d, 72f, infoTopFor(d, h - 50f), 640f, WHITE, Color.parseColor("#C0DD97"), ownerColour = Color.parseColor("#C0DD97"), bottom = h - 30f)
        pill(c, d.tagline, w - 60f, h - 100f, Color.parseColor("#FFF4D6"), Color.parseColor("#173404"), alignRight = true, max = 420f)
        watermark(c, d, w - 60f, h - 20f, onDark = true)
    }

    /** 16 · Kiryana — shop shelves behind a clean label. */
    private fun kiryana(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val brown = Color.parseColor("#412402")
        c.drawColor(Color.parseColor("#F7E7B3"))
        var y = 40f
        while (y < h) { c.drawRect(0f, y, w.toFloat(), y + 10f, fill(AMBER)); y += 150f }
        val panel = RectF(40f, 40f, w - 40f, h - 40f)
        c.drawRoundRect(panel, 26f, 26f, fill(WHITE))
        val bx = panel.right - 110f
        val by = panel.top + 90f + (if (live(t)) -8f * wave(t) else 0f)
        c.drawRoundRect(RectF(bx - 44f, by - 30f, bx + 44f, by + 50f), 10f, 10f, stroke(AMBER, 7f))
        c.drawArc(RectF(bx - 24f, by - 60f, bx + 24f, by - 10f), 180f, 180f, false, stroke(AMBER, 7f))
        val b = title(c, d.name, panel.left + 44f, panel.top + 40f, panel.width() - 220f, 58f, brown)
        caps(c, d.type, panel.left + 46f, b + 26f, panel.width() - 220f, Color.parseColor("#BA7517"))
        info(c, d, panel.left + 46f, infoTopFor(d, panel.bottom - 40f), panel.width() - 440f, INK, Color.parseColor("#BA7517"), bottom = panel.bottom - 24f)
        pill(c, d.tagline, panel.right - 40f, panel.bottom - 90f, Color.parseColor("#F7E7B3"), brown, alignRight = true, max = 380f)
        watermark(c, d, panel.right - 30f, panel.bottom - 14f, onDark = false)
    }

    /** 17 · Auto — racing stripe and a speedometer whose needle sweeps. */
    private fun auto(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val red = Color.parseColor("#E24B4A")
        c.drawColor(Color.parseColor("#111214"))
        val shift = if (live(t)) t * 96f else 0f
        var x = -96f + shift
        while (x < w) { c.drawRect(x, 0f, x + 80f, 16f, fill(red)); c.drawRect(x + 80f, 0f, x + 96f, 16f, fill(WHITE)); x += 96f }
        val cx = w - 250f
        val cy = h - 40f
        val r = 230f
        val arc = RectF(cx - r, cy - r, cx + r, cy + r)
        c.drawArc(arc, 180f, 180f, false, stroke(Color.parseColor("#2A2B2E"), 34f).apply { strokeCap = Paint.Cap.BUTT })
        c.drawArc(arc, 180f, 120f, false, stroke(red, 34f).apply { strokeCap = Paint.Cap.BUTT })
        val tk = stroke(Color.parseColor("#9C9A92"), 4f)
        for (k in 0..4) {
            val a = PI + PI * k / 4
            c.drawLine(cx + (r - 50f) * cos(a).toFloat(), cy + (r - 50f) * sin(a).toFloat(),
                cx + (r - 30f) * cos(a).toFloat(), cy + (r - 30f) * sin(a).toFloat(), tk)
        }
        val deg = if (live(t)) -70f + 130f * wave(t) else 35f
        val a = Math.toRadians((deg - 90).toDouble())
        c.drawLine(cx, cy, cx + (r - 64f) * cos(a).toFloat(), cy + (r - 64f) * sin(a).toFloat(), stroke(red, 8f))
        c.drawCircle(cx, cy, 18f, fill(red))
        val words = d.name.trim().uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = words.firstOrNull().orEmpty()
        val rest = words.drop(1).joinToString(" ")
        val room = w - 600f
        val p1 = paint(82f, WHITE, bold = true)
        var s = 82f
        while ((p1.measureText(first) > room || paint(s, red, true).measureText(rest) > room) && s > 40f) { s -= 3f; p1.textSize = s }
        c.drawText(ellipsise(first, p1, room), 70f, 120f, p1)
        var y = 120f
        if (rest.isNotEmpty()) { y += s * 0.95f; c.drawText(ellipsise(rest, paint(s, red, true), room), 70f, y, paint(s, red, true)) }
        caps(c, d.type, 72f, y + 46f, room, Color.parseColor("#9C9A92"))
        info(c, d, 72f, infoTopFor(d, h - 56f), room - 40f, Color.parseColor("#F1EFE8"), red, ownerColour = WHITE, bottom = h - 34f)
        caps(c, d.tagline, w - 60f, 70f, 420f, Color.parseColor("#9C9A92"), size = 20f, align = Paint.Align.RIGHT)
        watermark(c, d, w - 60f, 100f, onDark = true)
    }

    /** 18 · Furniture — wood grain under a cream label. */
    private fun furniture(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val brown = Color.parseColor("#854F0B")
        c.drawColor(Color.parseColor("#8B5A2B"))
        var y = 0f
        var i = 0
        val tones = intArrayOf(Color.parseColor("#9C6A38"), Color.parseColor("#7E4F24"), Color.parseColor("#93602F"))
        while (y < h) { val hh = 6f + (i * 7 % 11); c.drawRect(0f, y, w.toFloat(), y + hh, fill(tones[i % 3])); y += hh + 4f; i++ }
        val panel = RectF(64f, 60f, w - 64f, h - 60f)
        c.drawRect(RectF(panel.left + 10f, panel.top + 14f, panel.right + 10f, panel.bottom + 14f), fill(alpha(Color.BLACK, 70)))
        c.drawRect(panel, fill(Color.parseColor("#FFF9EC")))
        val b = title(c, d.name, panel.left + 44f, panel.top + 40f, panel.width() - 88f, 60f, Color.parseColor("#412402"), serif = true)
        caps(c, d.type, panel.left + 46f, b + 26f, panel.width() - 88f, brown)
        info(c, d, panel.left + 46f, infoTopFor(d, panel.bottom - 36f), panel.width() - 260f, INK, brown, bottom = panel.bottom - 20f)
        val cx = panel.right - 110f
        val cy = panel.bottom - 90f
        val ch = stroke(brown, 8f)
        c.drawRoundRect(RectF(cx - 50f, cy - 50f, cx + 50f, cy + 10f), 16f, 16f, ch)
        c.drawRoundRect(RectF(cx - 66f, cy - 10f, cx + 66f, cy + 34f), 14f, 14f, ch)
        c.drawLine(cx - 50f, cy + 34f, cx - 50f, cy + 56f, ch)
        c.drawLine(cx + 50f, cy + 34f, cx + 50f, cy + 56f, ch)
        pill(c, d.tagline, panel.right - 40f, panel.top + 30f, Color.parseColor("#F3E1C0"), Color.parseColor("#412402"), size = 20f, alignRight = true, max = 360f)
        watermark(c, d, panel.right, h - 22f, onDark = true)
    }

    /** 19 · Salon — a vanity mirror whose bulbs light in turn. */
    private fun salon(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val plum = Color.parseColor("#4A1A14")
        c.drawColor(Color.parseColor("#F6D9E0"))
        val m = RectF(70f, 70f, 520f, h - 60f)
        val mirror = path {
            moveTo(m.left, m.bottom)
            lineTo(m.left, m.top + m.width() / 2)
            arcTo(RectF(m.left, m.top, m.right, m.top + m.width()), 180f, 180f)
            lineTo(m.right, m.bottom)
            close()
        }
        c.drawPath(mirror, fill(Color.parseColor("#FFF9F3")))
        c.drawPath(mirror, stroke(GOLD, 10f))
        val bulbs = listOf(
            m.centerX() to m.top, m.left + 50f to m.top + 70f, m.right - 50f to m.top + 70f,
            m.left to m.top + 220f, m.right to m.top + 220f, m.left to m.top + 380f, m.right to m.top + 380f,
            m.left to m.bottom - 6f, m.right to m.bottom - 6f
        )
        bulbs.forEachIndexed { i, (bx, by) ->
            val on = !live(t) || ((t * bulbs.size).toInt() >= i)
            if (on) c.drawCircle(bx, by, 22f, fill(alpha(AMBER, 80)))
            c.drawCircle(bx, by, 11f, fill(if (on) AMBER else Color.parseColor("#F7E7B3")))
        }
        val nb = title(c, d.name, m.centerX(), m.top + m.width() * 0.45f, m.width() - 90f, 58f, plum, Paint.Align.CENTER, serif = true)
        caps(c, d.type, m.centerX(), nb + 24f, m.width() - 90f, GOLD, size = 18f, align = Paint.Align.CENTER)
        val x0 = 600f
        c.drawRect(x0, 120f, x0 + 70f, 126f, fill(GOLD))
        val end = info(c, d, x0, 190f, w - x0 - 60f, plum, GOLD, bottom = h - 110f)
        pill(c, d.tagline, x0, min(end - 10f, h - 100f), plum, WHITE)
        watermark(c, d, w - 60f, h - 24f, onDark = false)
    }

    /** 20 · Solar & Electric — the sun's rays fall onto a panel whose cells light up. */
    private fun solar(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val navy = Color.parseColor("#0B1F3A")
        val blue = Color.parseColor("#378ADD")
        c.drawColor(navy)
        val sx = w - 190f
        val sy = 130f
        c.drawCircle(sx, sy, 62f, fill(AMBER))
        val rays = stroke(AMBER, 6f).apply {
            pathEffect = DashPathEffect(floatArrayOf(16f, 14f), if (live(t)) -t * 60f else 0f)
        }
        val px0 = w - 520f
        val pTop = 330f
        listOf(px0 + 60f, px0 + 170f, px0 + 280f, px0 + 390f).forEach { tx ->
            c.drawLine(sx - 20f, sy + 60f, tx, pTop - 16f, rays)
        }
        val cols = 4
        val rowsN = 3
        val pw = 440f
        val ph = 250f
        val skew = 70f
        val frame = path {
            moveTo(px0 + skew, pTop); lineTo(px0 + skew + pw, pTop)
            lineTo(px0 + pw, pTop + ph); lineTo(px0, pTop + ph); close()
        }
        c.drawPath(frame, fill(navy))
        c.drawPath(frame, stroke(Color.parseColor("#85B7EB"), 5f))
        val lit = if (live(t)) (t * (cols * rowsN + 4)).toInt() else cols * rowsN
        for (ry in 0 until rowsN) for (cx in 0 until cols) {
            val idx = ry * cols + cx
            val y0 = pTop + 8f + ry * (ph - 16f) / rowsN
            val y1 = y0 + (ph - 16f) / rowsN - 8f
            fun xAt(y: Float, col: Float) = px0 + skew * (1 - (y - pTop) / ph) + 8f + col * (pw - 16f) / cols
            val cell = path {
                moveTo(xAt(y0, cx.toFloat()), y0); lineTo(xAt(y0, cx + 1f) - 8f, y0)
                lineTo(xAt(y1, cx + 1f) - 8f, y1); lineTo(xAt(y1, cx.toFloat()), y1); close()
            }
            c.drawPath(cell, fill(if (idx < lit) blue else Color.parseColor("#1C3F73")))
        }
        val room = px0 - 110f
        caps(c, d.tagline, 70f, 80f, room, AMBER, size = 20f)
        val b = title(c, d.name, 70f, 104f, room, 58f, WHITE)
        caps(c, d.type, 72f, b + 26f, room, Color.parseColor("#85B7EB"), size = 20f)
        info(c, d, 72f, infoTopFor(d, h - 56f), room, Color.parseColor("#F1EFE8"), AMBER, ownerColour = WHITE, bottom = h - 34f)
        watermark(c, d, w - 60f, h - 22f, onDark = true)
    }

    // ======================================================================
    // Four original shapes, rebuilt
    // ======================================================================

    /** Dusk — night gradient, a blue wedge with a gold edge. */
    private fun dusk(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(),
            linear(0f, 0f, w.toFloat(), h.toFloat(), Color.parseColor("#1B2A4A"), Color.parseColor("#3A1F5A")))
        val lift = if (live(t)) 16f * wave(t) else 0f
        val wedge = path { moveTo(w * 0.55f, h.toFloat()); lineTo(w.toFloat(), h * 0.38f - lift); lineTo(w.toFloat(), h.toFloat()); close() }
        c.drawPath(wedge, fill(Color.parseColor("#2F7FC4")))
        c.drawLine(w * 0.55f - 16f, h.toFloat(), w.toFloat() - 16f, h * 0.38f - lift, stroke(GOLD, 6f))
        val b = title(c, d.name, 70f, 64f, 900f, 62f, WHITE)
        caps(c, d.type, 72f, b + 26f, 900f, GOLD)
        info(c, d, 72f, infoTopFor(d, h - 56f), w * 0.55f - 40f, WHITE, GOLD, ownerColour = GOLD_PALE, bottom = h - 34f)
        pill(c, d.tagline, w - 60f, 50f, alpha(GOLD, 220), Color.parseColor("#1B2A4A"), alignRight = true)
        watermark(c, d, w - 60f, h - 22f, onDark = true)
    }

    /** Blade — a red half-disc, the name on a red label. */
    private fun blade(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val red = Color.parseColor("#A32D2D")
        c.drawColor(WHITE)
        val grow = if (live(t)) 12f * wave(t) else 0f
        c.drawCircle(-80f, h / 2f, 470f + grow, fill(red))
        c.drawCircle(-80f, h / 2f, 390f, fill(Color.parseColor("#7F1D1D")))
        sprout(c, 120f, h - 160f, 80f, Color.parseColor("#F7C8C8"))
        caps(c, d.tagline, 70f, h - 70f, 300f, Color.parseColor("#F7C8C8"), size = 20f)
        val x0 = 480f
        val room = w - x0 - 60f
        val np = paint(46f, WHITE, bold = true)
        var s = 46f
        while (np.measureText(d.name) > room - 40f && s > 28f) { s -= 2f; np.textSize = s }
        val label = ellipsise(d.name, np, room - 40f)
        c.drawRect(x0, 70f, x0 + np.measureText(label) + 40f, 70f + s * 1.5f, fill(red))
        c.drawText(label, x0 + 20f, 70f + s * 1.08f, np)
        caps(c, d.type, x0, 70f + s * 1.5f + 44f, room, red)
        info(c, d, x0, infoTopFor(d, h - 56f), room, INK, red, bottom = h - 34f)
        watermark(c, d, w - 60f, h - 22f, onDark = false)
    }

    /** Wave — an amber name bar and a navy swell. */
    private fun waveCard(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val navy = Color.parseColor("#0F2438")
        c.drawColor(WHITE)
        val swell = if (live(t)) 22f * wave(t) else 0f
        c.drawPath(path {
            moveTo(380f, h.toFloat())
            cubicTo(560f, h.toFloat(), 640f, 300f - swell, 860f, 300f - swell)
            lineTo(w.toFloat(), 300f - swell); lineTo(w.toFloat(), h.toFloat()); close()
        }, fill(navy))
        c.drawPath(path {
            moveTo(0f, h - 70f)
            cubicTo(400f, h - 90f, 700f, h + 10f, w.toFloat(), h - 40f)
            lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
        }, fill(AMBER))
        val np = paint(44f, INK, bold = true)
        var s = 44f
        while (np.measureText(d.name) > 640f && s > 26f) { s -= 2f; np.textSize = s }
        val label = ellipsise(d.name, np, 640f)
        val barR = 70f + np.measureText(label) + 70f
        c.drawPath(path {
            moveTo(0f, 110f); lineTo(barR - 50f, 110f)
            quadTo(barR, 110f, barR, 160f); quadTo(barR, 210f, barR - 50f, 210f)
            lineTo(0f, 210f); close()
        }, fill(AMBER))
        c.drawText(label, 70f, 160f + s * 0.35f, np)
        caps(c, d.type, 72f, 262f, 600f, Color.parseColor("#854F0B"))
        info(c, d, w - 70f, infoTopFor(d, h - 90f), 400f, WHITE, AMBER, ownerColour = AMBER, align = Paint.Align.RIGHT, bottom = h - 80f)
        pill(c, d.tagline, 70f, 300f, navy, WHITE, size = 22f, max = 420f)
        watermark(c, d, 70f, h - 90f, onDark = false, align = Paint.Align.LEFT)
    }

    /** Envelope — a deep teal card with cream and amber flaps. */
    private fun envelope(c: Canvas, d: CardData, w: Int, h: Int, t: Float) {
        val cream = Color.parseColor("#F3E9C6")
        c.drawColor(Color.parseColor("#163A36"))
        val open = if (live(t)) 20f * wave(t) else 0f
        c.drawPath(path { moveTo(w * 0.56f, 0f); lineTo(w.toFloat(), 0f); lineTo(w.toFloat(), h * 0.6f + open); close() }, fill(cream))
        c.drawPath(path { moveTo(w.toFloat(), h * 0.47f); lineTo(w.toFloat(), h.toFloat()); lineTo(w * 0.58f, h.toFloat()); close() }, fill(AMBER))
        c.drawRect(60f, 0f, 92f, h * 0.56f, fill(AMBER))
        val x0 = 140f
        val room = w * 0.56f - x0 - 20f
        val b = title(c, d.name, x0, 64f, room, 60f, WHITE)
        caps(c, d.type, x0 + 2f, b + 26f, room, AMBER)
        info(c, d, x0 + 2f, infoTopFor(d, h - 56f), w * 0.58f - x0 - 10f, WHITE, AMBER, ownerColour = cream, bottom = h - 34f)
        pill(c, d.tagline, x0, b + 56f, alpha(AMBER, 230), Color.parseColor("#163A36"), size = 20f, max = room)
        watermark(c, d, 70f, h - 20f, onDark = true, align = Paint.Align.LEFT)
    }

    // ---------- the list ----------

    /**
     * The order the picker shows. Ids are stored in preferences, so they never
     * change meaning: the four rebuilt originals keep their old ids (8-11), the
     * new designs take 101-120, and an id that no longer exists (an old design
     * that was removed) falls back to the first card.
     */
    val all: List<Template> = listOf(
        Template(101, R.string.biz_tpl_editorial, draw = ::editorial),
        Template(102, R.string.biz_tpl_seal, shine = true, draw = ::seal),
        Template(103, R.string.biz_tpl_swiss, draw = ::swiss),
        Template(104, R.string.biz_tpl_glass, shine = true, draw = ::glass),
        Template(105, R.string.biz_tpl_band, draw = ::band),
        Template(106, R.string.biz_tpl_naqsh, shine = true, draw = ::naqsh),
        Template(107, R.string.biz_tpl_brutal, draw = ::brutal),
        Template(108, R.string.biz_tpl_ticket, draw = ::ticket),
        Template(109, R.string.biz_tpl_mesh, shine = true, draw = ::mesh),
        Template(110, R.string.biz_tpl_field, draw = ::fieldCard),
        Template(111, R.string.biz_tpl_medical, draw = ::medical),
        Template(112, R.string.biz_tpl_mobile, draw = ::mobile),
        Template(113, R.string.biz_tpl_tailor, draw = ::tailor),
        Template(114, R.string.biz_tpl_hardware, draw = ::hardware),
        Template(115, R.string.biz_tpl_dairy, draw = ::dairy),
        Template(116, R.string.biz_tpl_kiryana, draw = ::kiryana),
        Template(117, R.string.biz_tpl_auto, draw = ::auto),
        Template(118, R.string.biz_tpl_furniture, draw = ::furniture),
        Template(119, R.string.biz_tpl_salon, draw = ::salon),
        Template(120, R.string.biz_tpl_solar, draw = ::solar),
        Template(9, R.string.biz_tpl_slate, shine = true, draw = ::dusk),
        Template(11, R.string.biz_tpl_stamp, draw = ::blade),
        Template(8, R.string.biz_tpl_arch, draw = ::waveCard),
        Template(10, R.string.biz_tpl_olive, draw = ::envelope)
    )

    fun byId(id: Int): Template = all.firstOrNull { it.id == id } ?: all.first()
}
