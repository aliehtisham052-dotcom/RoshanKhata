package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.InvoiceFonts
import com.innovation313.roshankhata.data.Trade
import com.innovation313.roshankhata.data.TradeVocab
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * New stock, the premium design (11 Oct, the owner's approved mockup): a dark
 * emerald studio, a spotlight from above, the shop's crate turning on a gold
 * rimmed plinth (a real 3D render, see poster3d.HeroStage), the headline in
 * gold foil, what arrived on a glass strip, and a gold call button.
 *
 * Everything on it speaks the poster's language: in Urdu the type is
 * Nastaliq and the layout reads from the right; in English it is Playfair
 * and Manrope from the left. The phone number stays in 0–9 so it can be
 * dialled as printed.
 *
 * Drawn at 1080 × 1920. [HERO] is where the 3D crate sits — the live preview
 * lays its TextureView over the same fractions of the picture.
 */
object PosterNewStock {

    /** The crate's place on the 1080 × 1920 poster. */
    val HERO = RectF(180f, 560f, 900f, 1230f)

    private const val W = 1080f
    private const val GOLD = 0xFFF3CF6A.toInt()
    private const val GOLD_DEEP = 0xFFA8751C.toInt()
    private const val CREAM = 0xFFF6EBD0.toInt()

    data class Out(val titleSize: Float, val titleLines: Int, val messageSize: Float, val messageLines: Int)

    /** True when the poster's words are Urdu in Arabic script (not Roman Urdu). */
    fun urduScript(locale: Locale) = locale.language == "ur" && locale.script != "Latn"

    fun drawBackground(c: Canvas) {
        val bg = Paint()
        bg.shader = LinearGradient(0f, 0f, 0f, 1920f,
            intArrayOf(0xFF071A12.toInt(), 0xFF0A2A1C.toInt(), 0xFF040C08.toInt()),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, 1920f, bg)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG)
        glow.shader = RadialGradient(540f, 960f, 820f, 0x991E6B4A.toInt(), 0x001E6B4A, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, 1920f, glow)
        // The spotlight: a soft cone from the top centre down to the plinth.
        val cone = Path().apply { moveTo(470f, -30f); lineTo(610f, -30f); lineTo(900f, 1380f); lineTo(180f, 1380f); close() }
        val beam = Paint(Paint.ANTI_ALIAS_FLAG)
        beam.shader = LinearGradient(0f, 0f, 0f, 1380f,
            intArrayOf(0x38FFF0C8, 0x0DFFF0C8, 0x00FFF0C8), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        beam.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
        c.drawPath(cone, beam)
        val dust = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD }
        for ((x, y, r) in listOf(Triple(192f, 630f, 4.8f), Triple(900f, 570f, 3.9f), Triple(288f, 900f, 3.3f),
                Triple(828f, 858f, 5.1f), Triple(120f, 1140f, 3.6f), Triple(978f, 1110f, 3.6f), Triple(666f, 642f, 4.2f))) {
            dust.alpha = 170
            c.drawCircle(x, y, r, dust)
        }
        // The plinth's pool of light, under where the hero stands.
        val pool = Paint(Paint.ANTI_ALIAS_FLAG)
        pool.shader = RadialGradient(540f, 1100f, 340f, 0x59F3CF6A, 0x00F3CF6A, Shader.TileMode.CLAMP)
        c.save(); c.scale(1f, 0.25f, 540f, 1100f); c.drawCircle(540f, 1100f, 340f, pool); c.restore()
    }

    fun draw(
        context: Context, c: Canvas, locale: Locale, rtl: Boolean,
        title: String, message: String, shopName: String, phone: String?, address: String?,
        logo: Bitmap?, hero: Bitmap?, mark: String?, heroOverlay: Boolean = false
    ): Out {
        val urdu = urduScript(locale)
        drawBackground(c)

        // Header: the shop's seal and name, on the reading side.
        val seal = 138f
        val sealCx = if (rtl) W - 66f - seal / 2 else 66f + seal / 2
        val sealCy = 66f + seal / 2
        drawSeal(context, c, sealCx, sealCy, seal / 2, shopName, logo)
        val nameX = if (rtl) sealCx - seal / 2 - 30f else sealCx + seal / 2 + 30f
        val nameAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        val namePaint = textPaint(context, shopName, 51f, Color.WHITE, true, locale, urdu)
        namePaint.textAlign = nameAlign
        fitWidth(namePaint, shopName, 560f)
        inkLine(c, shopName, namePaint, nameX, sealCy - 24f)
        val trade = Trade.current(context).example(context)
        val tradePaint = textPaint(context, trade, 31f, 0xB3F6EBD0.toInt(), false, locale, urdu)
        tradePaint.textAlign = nameAlign
        fitWidth(tradePaint, trade, 560f)
        inkLine(c, trade, tradePaint, nameX, sealCy + 34f)

        // Kicker between two gold hairlines.
        val kicker = context.getString(R.string.poster_kicker_new_stock)
        val kp = textPaint(context, kicker, if (urdu) 39f else 33f, GOLD, true, locale, urdu)
        kp.textAlign = Paint.Align.CENTER
        if (!urdu) kp.letterSpacing = 0.45f
        inkLine(c, kicker, kp, 540f, 300f)
        val kw = kp.measureText(kicker) / 2 + 36f
        val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
        hair.shader = LinearGradient(540f - kw - 102f, 0f, 540f - kw, 0f, 0x00F3CF6A, GOLD, Shader.TileMode.CLAMP)
        c.drawLine(540f - kw - 102f, 300f, 540f - kw, 300f, hair)
        hair.shader = LinearGradient(540f + kw, 0f, 540f + kw + 102f, 0f, GOLD, 0x00F3CF6A, Shader.TileMode.CLAMP)
        c.drawLine(540f + kw, 300f, 540f + kw + 102f, 300f, hair)

        // Headline in gold foil.
        val head = title.ifBlank { " " }
        val hp = textPaint(context, head, if (urdu) 138f else 126f, GOLD, true, locale, urdu)
        hp.textAlign = Paint.Align.CENTER
        fitWidth(hp, head, 960f, floor = 72f)
        val lines = if (hp.measureText(head) > 960f) 2 else 1
        val titleSize = hp.textSize
        hp.setShadowLayer(30f, 0f, 12f, 0x80000000.toInt())
        hp.shader = LinearGradient(0f, 380f, 0f, 560f,
            intArrayOf(0xFFFFF8D6.toInt(), GOLD, GOLD_DEEP), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        if (lines == 1) inkLine(c, head, hp, 540f, 470f)
        else block(head, hp, 960, rtl).let { l -> c.save(); c.translate(60f, 470f - l.height / 2f); l.draw(c); c.restore() }

        // The hero: the 3D crate if the phone could render it, else a flat one.
        if (hero != null) c.drawBitmap(hero, null, HERO, Paint(Paint.FILTER_BITMAP_FLAG))
        else if (!heroOverlay) flatCrate(c)

        // What arrived, on a strip of glass.
        val items = TradeVocab.items(context).take(3).joinToString("   ◆   ")
        val strip = RectF(66f, 1230f, W - 66f, 1374f)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x12FFFFFF }
        c.drawRoundRect(strip, 72f, 72f, glass)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x59F3CF6A }
        c.drawRoundRect(strip, 72f, 72f, edge)
        val ip = textPaint(context, items, if (urdu) 42f else 39f, Color.WHITE, urdu, locale, urdu)
        ip.textAlign = Paint.Align.CENTER
        fitWidth(ip, items, strip.width() - 96f, floor = 26f)
        inkLine(c, items, ip, 540f, strip.centerY())

        // The owner's line under it.
        val msg = message.ifBlank { context.getString(R.string.poster_new_stock_msg) }
        val mp = textPaint(context, msg, if (urdu) 45f else 46f, 0xDDF6EBD0.toInt(), false, locale, urdu)
        if (!urdu) mp.typeface = Typeface.create(InvoiceFonts.playfairDisplay(context), Typeface.ITALIC)
        var ml = block(msg, mp, 900, rtl)
        while (ml.lineCount > 2 && mp.textSize > 34f) { mp.textSize -= 3f; ml = block(msg, mp, 900, rtl) }
        c.save(); c.translate(90f, 1460f - ml.height / 2f); ml.draw(c); c.restore()

        // The gold call button, or nothing if the profile has no phone.
        if (!phone.isNullOrBlank()) {
            val pill = RectF(102f, 1530f, W - 102f, 1710f)
            val gold = Paint(Paint.ANTI_ALIAS_FLAG)
            gold.shader = LinearGradient(0f, pill.top, 0f, pill.bottom,
                intArrayOf(0xFFFBE6A2.toInt(), 0xFFE2B44C.toInt(), 0xFFB98322.toInt()), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
            gold.setShadowLayer(42f, 0f, 36f, 0x80000000.toInt())
            c.drawRoundRect(pill, 90f, 90f, gold)
            val icx = if (rtl) pill.right - 90f else pill.left + 90f
            val disc = Paint(Paint.ANTI_ALIAS_FLAG)
            disc.shader = RadialGradient(icx - 20f, pill.centerY() - 20f, 80f, 0xFF1E6B4A.toInt(), 0xFF062418.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(icx, pill.centerY(), 66f, disc)
            CardGlyphs.phone(c, icx, pill.centerY(), 60f, GOLD)
            val pp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(InvoiceFonts.sora(context), Typeface.BOLD)
                textSize = 69f; color = 0xFF1F1505.toInt(); textAlign = Paint.Align.CENTER; letterSpacing = 0.02f
            }
            val number = phone.trim()
            fitWidth(pp, number, pill.width() - 300f, floor = 40f)
            inkLine(c, number, pp, pill.centerX() + if (rtl) -40f else 40f, pill.centerY())
        }

        if (!address.isNullOrBlank()) {
            val ap = textPaint(context, address, if (urdu) 39f else 38f, 0xD9F6EBD0.toInt(), false, locale, urdu)
            ap.textAlign = Paint.Align.CENTER
            fitWidth(ap, address, 860f, floor = 26f)
            val aw = ap.measureText(address)
            val pinX = if (rtl) 540f + aw / 2 + 30f else 540f - aw / 2 - 30f
            CardGlyphs.pin(c, pinX, 1770f, 40f, GOLD)
            inkLine(c, address, ap, 540f, 1770f)
        }

        mark?.let {
            val mk = textPaint(context, it, if (urdu) 27f else 25f, 0x66F6EBD0, false, locale, urdu)
            mk.textAlign = Paint.Align.CENTER
            inkLine(c, it, mk, 540f, 1880f)
        }
        return Out(titleSize, lines, mp.textSize, ml.lineCount)
    }

    /** The shop's round seal: its logo, or its initials, inside a gold ring. */
    private fun drawSeal(context: Context, c: Canvas, cx: Float, cy: Float, r: Float, name: String, logo: Bitmap?) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(0xFFFFF1C1.toInt(), 0xFFC8962B.toInt(), 0xFFF7DC8C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, ring)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG)
        inner.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.3f, 0xFF1E6B4A.toInt(), 0xFF08301C.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r - 6f, inner)
        if (logo != null) {
            val clip = Path().apply { addCircle(cx, cy, r - 12f, Path.Direction.CW) }
            c.save(); c.clipPath(clip)
            val s = (2 * (r - 12f)) / min(logo.width, logo.height)
            val w = logo.width * s; val h = logo.height * s
            c.drawBitmap(logo, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
            return
        }
        val initials = initials(name)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (hasArabic(initials)) InvoiceFonts.bold(InvoiceFonts.nastaliq(context))
                else Typeface.create(InvoiceFonts.playfairDisplay(context), Typeface.BOLD)
            textSize = r * 0.72f; color = GOLD; textAlign = Paint.Align.CENTER
        }
        inkLine(c, initials, p, cx, cy)
    }

    /** A flat crate for phones that cannot render the 3D one. */
    private fun flatCrate(c: Canvas) {
        val plinthTop = RectF(270f, 978f, 810f, 1092f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(540f, 1010f, 300f, 0xFF2F8A62.toInt(), 0xFF12452F.toInt(), Shader.TileMode.CLAMP)
        c.drawOval(plinthTop, p)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4.5f; color = 0xFFD9B25A.toInt() }
        c.drawOval(plinthTop, rim)
        val box = RectF(405f, 642f, 675f, 912f)
        val f = Paint(Paint.ANTI_ALIAS_FLAG)
        f.shader = LinearGradient(box.left, box.top, box.right, box.bottom, 0xFF2C8C62.toInt(), 0xFF0B3B27.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(box, f)
        rim.strokeWidth = 9f; rim.color = 0xFFE7C268.toInt()
        c.drawRect(box, rim)
    }

    private fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "★"
        if (hasArabic(words[0])) return words[0].take(4)
        return words.take(2).joinToString("") { it.take(1).uppercase(Locale.ROOT) }
    }

    private fun hasArabic(s: String) = s.any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' }

    /** A paint in the right face for [text]: Nastaliq for Urdu, the system's for other Arabic-script text, Sora/Manrope for Latin. */
    private fun textPaint(context: Context, text: String, size: Float, colour: Int, bold: Boolean, locale: Locale, urdu: Boolean): TextPaint {
        val tf = when {
            hasArabic(text) && urdu -> InvoiceFonts.nastaliq(context).let { if (bold) InvoiceFonts.bold(it) else it }
            hasArabic(text) -> if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            bold && size >= 100f -> Typeface.create(InvoiceFonts.playfairDisplay(context), Typeface.BOLD)
            bold -> InvoiceFonts.manropeBold(context)
            else -> InvoiceFonts.manrope(context)
        }
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf; textSize = size; color = colour; textLocale = locale
        }
    }

    /** Shrink [p] until [text] is no wider than [width], not below [floor]. */
    private fun fitWidth(p: Paint, text: String, width: Float, floor: Float = 22f) {
        while (p.measureText(text) > width && p.textSize > floor) p.textSize -= 2f
    }

    /**
     * Draw one line so its INK is centred on [cy]. Nastaliq's line box is far
     * taller than its letters, so centring the box would set Urdu visibly low.
     */
    private fun inkLine(c: Canvas, text: String, p: Paint, x: Float, cy: Float) {
        val b = Rect()
        p.getTextBounds(text, 0, text.length, b)
        c.drawText(text, x, cy - (b.top + b.bottom) / 2f, p)
    }

    private fun block(text: String, p: TextPaint, width: Int, rtl: Boolean): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .build()
}
