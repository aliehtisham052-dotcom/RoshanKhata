package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.innovation313.roshankhata.data.InvoiceFonts
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The pieces every premium poster is built from (11 Oct): the right face for
 * each language, lines centred on their ink, gold foil, glass panels, the
 * shop's seal, the gold call button and the address line.
 *
 * Language: in Urdu (Arabic script) the type is Noto Nastaliq Urdu, and a
 * greeting may be set in Aref Ruqaa calligraphy when every letter of it is in
 * that font; other Arabic-script languages use the phone's own Arabic face;
 * Latin text uses Playfair Display for display lines and Manrope/Sora for the
 * rest. A phone number is always printed in 0–9 so it can be dialled as read.
 */
object PosterKit {

    const val W = 1080f
    const val H = 1920f

    const val GOLD = 0xFFF3CF6A.toInt()
    const val GOLD_DEEP = 0xFFA8751C.toInt()
    const val GOLD_PALE = 0xFFFFF8D6.toInt()
    const val CREAM = 0xFFF6EBD0.toInt()

    /** Everything a design draws from. [shopName] is already the one for this language. */
    class Page(
        val context: Context,
        val c: Canvas,
        val locale: Locale,
        val rtl: Boolean,
        val title: String,
        val message: String,
        val shopName: String,
        val phone: String?,
        val address: String?,
        val logo: Bitmap?,
        val mark: String?,
        val hero: Bitmap?,
        val heroOverlay: Boolean
    ) {
        val urdu = urduScript(locale)
        /** Draw the hero here, or its flat stand-in, unless the live 3D view covers the place. */
        fun drawHero(rect: RectF, flat: (Canvas) -> Unit) {
            when {
                hero != null -> c.drawBitmap(hero, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                !heroOverlay -> flat(c)
            }
        }
    }

    /** How the words fitted, and what the live preview animates on top (see PosterMotion). */
    data class Out(
        val titleSize: Float,
        val titleLines: Int,
        val messageSize: Float,
        val messageLines: Int,
        val motion: PosterMotion.Spec = PosterMotion.Spec()
    )

    /** True when the poster's words are Urdu in Arabic script (not Roman Urdu). */
    fun urduScript(locale: Locale) = locale.language == "ur" && locale.script != "Latn"

    fun hasArabic(s: String) = s.any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' || it in 'ﭐ'..'﻿' }

    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "★"
        if (hasArabic(words[0])) return words[0].take(4)
        return words.take(2).joinToString("") { it.take(1).uppercase(Locale.ROOT) }
    }

    /** A paint in the right face for [text]: Nastaliq for Urdu, the phone's for other Arabic script, Playfair/Manrope for Latin. */
    fun textPaint(context: Context, text: String, size: Float, colour: Int, bold: Boolean, locale: Locale, urdu: Boolean): TextPaint {
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

    /**
     * A greeting's display face: Aref Ruqaa calligraphy for Arabic-script text
     * whose every letter it has (عید مبارک, رمضان مبارک, شکریہ — checked against
     * the font file), else what [textPaint] gives. Latin greetings get Playfair.
     */
    fun greetingPaint(context: Context, text: String, size: Float, colour: Int, locale: Locale, urdu: Boolean): TextPaint {
        if (hasArabic(text)) {
            if (text.all { it.isWhitespace() || it in RUQAA }) return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = InvoiceFonts.arefRuqaa(context); textSize = size; color = colour; textLocale = locale
            }
            return textPaint(context, text, size, colour, true, locale, urdu)
        }
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(InvoiceFonts.playfairDisplay(context), Typeface.BOLD)
            textSize = size; color = colour; textLocale = locale
        }
    }

    /**
     * Every character aref_ruqaa_bold.ttf draws in Arabic (U+0600–06FF) and
     * ASCII, read from its cmap (11 Oct). Paint.hasGlyph cannot answer this:
     * it counts the phone's fallback fonts too.
     */
    private val RUQAA: Set<Char> = "\u060C\u061B\u061F\u0621\u0622\u0623\u0624\u0625\u0626\u0627\u0628\u0629\u062A\u062B\u062C\u062D\u062E\u062F\u0630\u0631\u0632\u0633\u0634\u0635\u0636\u0637\u0638\u0639\u063A\u0640\u0641\u0642\u0643\u0644\u0645\u0646\u0647\u0648\u0649\u064A\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0653\u0654\u0655\u0656\u0657\u0658\u065A\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669\u066A\u066E\u066F\u0670\u0671\u0674\u0679\u067E\u0686\u0688\u068E\u0691\u0695\u0698\u06A0\u06A1\u06A4\u06A9\u06AC\u06AF\u06B5\u06BA\u06BB\u06BD\u06BE\u06C0\u06C1\u06C6\u06CC\u06CE\u06CF\u06D2\u06D3\u06D4\u06D5\u06E1\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9\u0020\u0021\u0023\u0025\u0026\u0027\u0028\u0029\u002A\u002B\u002C\u002D\u002E\u002F\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037\u0038\u0039\u003A\u003B\u003C\u003D\u003E\u003F\u0040\u0041\u0042\u0043\u0044\u0045\u0046\u0047\u0048\u0049\u004A\u004B\u004C\u004D\u004E\u004F\u0050\u0051\u0052\u0053\u0054\u0055\u0056\u0057\u0058\u0059\u005A\u005B\u005D\u005E\u005F\u0060\u0061\u0062\u0063\u0064\u0065\u0066\u0067\u0068\u0069\u006A\u006B\u006C\u006D\u006E\u006F\u0070\u0071\u0072\u0073\u0074\u0075\u0076\u0077\u0078\u0079\u007A\u007B\u007C\u007D\u007E".toSet()

    /** Shrink [p] until [text] is no wider than [width], not below [floor]. */
    fun fitWidth(p: Paint, text: String, width: Float, floor: Float = 22f) {
        while (p.measureText(text) > width && p.textSize > floor) p.textSize -= 2f
    }

    /**
     * Draw one line so its INK is centred on [cy]. Nastaliq's line box is far
     * taller than its letters, so centring the box would set Urdu visibly low.
     */
    fun inkLine(c: Canvas, text: String, p: Paint, x: Float, cy: Float) {
        c.drawText(text, x, inkBaseline(text, p, cy), p)
    }

    fun inkBaseline(text: String, p: Paint, cy: Float): Float {
        val b = Rect()
        p.getTextBounds(text, 0, text.length, b)
        return cy - (b.top + b.bottom) / 2f
    }

    fun block(text: String, p: TextPaint, width: Int, rtl: Boolean, spacing: Float = 1.05f,
              align: Layout.Alignment = Layout.Alignment.ALIGN_CENTER): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, max(1, width))
            .setAlignment(align)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setIncludePad(false)
            .setLineSpacing(0f, spacing)
            .build()

    /** A wrapped block shrunk until it is at most [maxLines] lines (or reaches [floor]). */
    fun fitBlock(text: String, p: TextPaint, width: Int, rtl: Boolean, maxLines: Int, floor: Float,
                 spacing: Float = 1.05f): StaticLayout {
        var l = block(text, p, width, rtl, spacing)
        while (l.lineCount > maxLines && p.textSize > floor) {
            p.textSize -= 2f
            l = block(text, p, width, rtl, spacing)
        }
        return l
    }

    fun put(c: Canvas, l: StaticLayout, x: Float, top: Float) {
        c.save(); c.translate(x, top); l.draw(c); c.restore()
    }

    /** Gold foil across a band from [top] to [bottom]. */
    fun foil(top: Float, bottom: Float, light: Int = GOLD_PALE, mid: Int = GOLD, dark: Int = GOLD_DEEP): Shader =
        LinearGradient(0f, top, 0f, bottom, intArrayOf(light, mid, dark), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)

    /**
     * A greeting in gold foil, one line centred on [cy] (two if it must wrap),
     * shrunk to fit [width]. Returns the paint it used and what the live
     * preview sweeps a glint across.
     */
    fun foilHeadline(page: Page, text: String, start: Float, floor: Float, cy: Float, width: Float = 960f,
                     greeting: Boolean = true, shadow: Int = 0x80000000.toInt(),
                     light: Int = GOLD_PALE, mid: Int = GOLD, dark: Int = GOLD_DEEP): Pair<TextPaint, PosterMotion.Glint> {
        val head = text.ifBlank { " " }
        val p = if (greeting) greetingPaint(page.context, head, start, mid, page.locale, page.urdu)
            else textPaint(page.context, head, start, mid, true, page.locale, page.urdu)
        p.textAlign = Paint.Align.CENTER
        fitWidth(p, head, width, floor)
        val c = page.c
        if (p.measureText(head) <= width) {
            val b = Rect(); p.getTextBounds(head, 0, head.length, b)
            val base = cy - (b.top + b.bottom) / 2f
            p.shader = foil(base + b.top, base + b.bottom, light, mid, dark)
            p.setShadowLayer(30f, 0f, 12f, shadow)
            c.drawText(head, W / 2, base, p)
            p.setShadowLayer(0f, 0f, 0f, 0)
            return p to PosterMotion.Glint(RectF(W / 2 - p.measureText(head) / 2, base + b.top, W / 2 + p.measureText(head) / 2, base + b.bottom),
                head, TextPaint(p), W / 2, base, null)
        }
        p.textAlign = Paint.Align.LEFT
        val l = block(head, p, width.toInt(), page.rtl, 1.0f)
        val top = cy - l.height / 2f
        p.shader = foil(top, top + l.height, light, mid, dark)
        p.setShadowLayer(30f, 0f, 12f, shadow)
        val x = (W - width) / 2
        put(c, l, x, top)
        p.setShadowLayer(0f, 0f, 0f, 0)
        return p to PosterMotion.Glint(RectF(x, top, x + width, top + l.height), head, TextPaint(p), x, top, l)
    }

    /** A small line between two gold hairlines, centred on [cy]. */
    fun kicker(page: Page, text: String, cy: Float, colour: Int = GOLD, size: Float = 33f, spaced: Boolean = true) {
        val p = textPaint(page.context, text, if (page.urdu) size + 6f else size, colour, true, page.locale, page.urdu)
        p.textAlign = Paint.Align.CENTER
        if (!page.urdu && spaced && !hasArabic(text)) p.letterSpacing = 0.4f
        fitWidth(p, text, 760f)
        inkLine(page.c, text, p, W / 2, cy)
        val kw = p.measureText(text) / 2 + 36f
        val hair = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3f }
        val clear = colour and 0x00FFFFFF
        hair.shader = LinearGradient(W / 2 - kw - 110f, 0f, W / 2 - kw, 0f, clear, colour, Shader.TileMode.CLAMP)
        page.c.drawLine(W / 2 - kw - 110f, cy, W / 2 - kw, cy, hair)
        hair.shader = LinearGradient(W / 2 + kw, 0f, W / 2 + kw + 110f, 0f, colour, clear, Shader.TileMode.CLAMP)
        page.c.drawLine(W / 2 + kw, cy, W / 2 + kw + 110f, cy, hair)
    }

    /** Frosted glass: a faint fill, a lighter top edge and a gold hairline. */
    fun glass(c: Canvas, r: RectF, radius: Float = 66f, fill: Int = 0x1AFFFFFF, edge: Int = 0x59F3CF6A) {
        val f = Paint(Paint.ANTI_ALIAS_FLAG)
        f.shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(fill, fill and 0x00FFFFFF or 0x08000000), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, f)
        val e = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = edge }
        c.drawRoundRect(r, radius, radius, e)
    }

    /** The shop's round seal: its logo, or its initials, inside a gold ring. */
    fun seal(context: Context, c: Canvas, cx: Float, cy: Float, r: Float, name: String, logo: Bitmap?,
             inner: Int = 0xFF1E6B4A.toInt(), innerDark: Int = 0xFF08301C.toInt(), ink: Int = GOLD) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG)
        ring.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(0xFFFFF1C1.toInt(), 0xFFC8962B.toInt(), 0xFFF7DC8C.toInt()), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, ring)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG)
        disc.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.3f, inner, innerDark, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r - r * 0.09f, disc)
        if (logo != null) {
            val rr = r - r * 0.17f
            val clip = Path().apply { addCircle(cx, cy, rr, Path.Direction.CW) }
            c.save(); c.clipPath(clip)
            val s = (2 * rr) / min(logo.width, logo.height)
            val w = logo.width * s; val h = logo.height * s
            c.drawBitmap(logo, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
            return
        }
        val initials = initials(name)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (hasArabic(initials)) InvoiceFonts.bold(InvoiceFonts.nastaliq(context))
                else Typeface.create(InvoiceFonts.playfairDisplay(context), Typeface.BOLD)
            textSize = r * 0.72f; color = ink; textAlign = Paint.Align.CENTER
        }
        fitWidth(p, initials, r * 1.5f, 10f)
        inkLine(c, initials, p, cx, cy)
    }

    /** Seal and name at the top, on the reading side (or centred), with the trade line under the name. */
    fun header(page: Page, top: Float = 66f, sealSize: Float = 138f, centred: Boolean = false, tradeLine: String? = null,
               nameColour: Int = 0xFFFFFFFF.toInt(), sub: Int = 0xB3F6EBD0.toInt(),
               inner: Int = 0xFF1E6B4A.toInt(), innerDark: Int = 0xFF08301C.toInt()) {
        val c = page.c
        val name = page.shopName
        val np = textPaint(page.context, name, if (page.urdu) 51f else 48f, nameColour, true, page.locale, page.urdu)
        fitWidth(np, name, if (centred) 640f else 560f)
        val sealCy = top + sealSize / 2
        if (centred) {
            val gap = 26f
            val total = sealSize + gap + np.measureText(name)
            val start = (W - total) / 2
            val sealCx = if (page.rtl) start + total - sealSize / 2 else start + sealSize / 2
            seal(page.context, c, sealCx, sealCy, sealSize / 2, name, page.logo, inner, innerDark)
            np.textAlign = if (page.rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            val nx = if (page.rtl) sealCx - sealSize / 2 - gap else sealCx + sealSize / 2 + gap
            inkLine(c, name, np, nx, sealCy)
            return
        }
        val sealCx = if (page.rtl) W - 66f - sealSize / 2 else 66f + sealSize / 2
        seal(page.context, c, sealCx, sealCy, sealSize / 2, name, page.logo, inner, innerDark)
        val nameX = if (page.rtl) sealCx - sealSize / 2 - 30f else sealCx + sealSize / 2 + 30f
        np.textAlign = if (page.rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        if (tradeLine.isNullOrBlank()) {
            inkLine(c, name, np, nameX, sealCy)
            return
        }
        inkLine(c, name, np, nameX, sealCy - 24f)
        val tp = textPaint(page.context, tradeLine, 31f, sub, false, page.locale, page.urdu)
        tp.textAlign = np.textAlign
        fitWidth(tp, tradeLine, 560f)
        inkLine(c, tradeLine, tp, nameX, sealCy + 34f)
    }

    /**
     * The sign-off at the foot: the seal beside "from" over the shop's name,
     * centred, with the phone under it in 0–9.
     */
    fun signOff(page: Page, from: String, cy: Float, nameColour: Int = GOLD, fromColour: Int = 0xFF9FE3D6.toInt(),
                phoneColour: Int = 0xB3F6EBD0.toInt(), inner: Int = 0xFF157A72.toInt(), innerDark: Int = 0xFF04211F.toInt(),
                sealSize: Float = 138f, showPhone: Boolean = true) {
        val c = page.c
        val name = page.shopName
        val np = textPaint(page.context, name, if (page.urdu) 54f else 52f, nameColour, true, page.locale, page.urdu)
        fitWidth(np, name, 620f)
        val fp = textPaint(page.context, from, if (page.urdu) 33f else 32f, fromColour, false, page.locale, page.urdu)
        if (!page.urdu && !hasArabic(from)) fp.typeface = Typeface.create(InvoiceFonts.playfairDisplay(page.context), Typeface.ITALIC)
        val textW = max(np.measureText(name), fp.measureText(from))
        val gap = 30f
        val total = sealSize + gap + textW
        val start = (W - total) / 2
        val sealCx = if (page.rtl) start + total - sealSize / 2 else start + sealSize / 2
        seal(page.context, c, sealCx, cy, sealSize / 2, name, page.logo, inner, innerDark)
        val align = if (page.rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        val tx = if (page.rtl) sealCx - sealSize / 2 - gap else sealCx + sealSize / 2 + gap
        np.textAlign = align; fp.textAlign = align
        inkLine(c, from, fp, tx, cy - 36f)
        inkLine(c, name, np, tx, cy + 22f)
        if (showPhone && !page.phone.isNullOrBlank()) {
            val pp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(InvoiceFonts.sora(page.context), Typeface.BOLD)
                textSize = 36f; color = phoneColour; textAlign = Paint.Align.CENTER; letterSpacing = 0.14f
            }
            inkLine(c, page.phone.trim(), pp, W / 2, cy + sealSize / 2 + 62f)
        }
    }

    /** The gold call button, or nothing if the profile has no phone. */
    fun phonePill(page: Page, pill: RectF, disc: Int = 0xFF1E6B4A.toInt(), discDark: Int = 0xFF062418.toInt(),
                  glyph: Int = GOLD, size: Float = 69f) {
        val phone = page.phone?.trim()
        if (phone.isNullOrBlank()) return
        val c = page.c
        val gold = Paint(Paint.ANTI_ALIAS_FLAG)
        gold.shader = LinearGradient(0f, pill.top, 0f, pill.bottom,
            intArrayOf(0xFFFBE6A2.toInt(), 0xFFE2B44C.toInt(), 0xFFB98322.toInt()), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        gold.setShadowLayer(42f, 0f, 30f, 0x80000000.toInt())
        val r = pill.height() / 2
        c.drawRoundRect(pill, r, r, gold)
        val dr = r - 22f
        val icx = if (page.rtl) pill.right - 22f - dr else pill.left + 22f + dr
        val d = Paint(Paint.ANTI_ALIAS_FLAG)
        d.shader = RadialGradient(icx - 20f, pill.centerY() - 20f, dr * 1.3f, disc, discDark, Shader.TileMode.CLAMP)
        c.drawCircle(icx, pill.centerY(), dr, d)
        CardGlyphs.phone(c, icx, pill.centerY(), dr * 0.9f, glyph)
        val pp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(InvoiceFonts.sora(page.context), Typeface.BOLD)
            textSize = size; color = 0xFF1F1505.toInt(); textAlign = Paint.Align.CENTER; letterSpacing = 0.02f
        }
        fitWidth(pp, phone, pill.width() - 2 * dr - 120f, floor = 40f)
        inkLine(c, phone, pp, pill.centerX() + if (page.rtl) -(dr - 10f) else (dr - 10f), pill.centerY())
    }

    /** The address with a map pin, centred on [cy]. */
    fun address(page: Page, cy: Float, colour: Int = 0xD9F6EBD0.toInt(), pin: Int = GOLD) {
        val a = page.address?.trim()
        if (a.isNullOrBlank()) return
        val ap = textPaint(page.context, a, if (page.urdu) 39f else 38f, colour, false, page.locale, page.urdu)
        ap.textAlign = Paint.Align.CENTER
        fitWidth(ap, a, 860f, floor = 26f)
        val aw = ap.measureText(a)
        val pinX = if (page.rtl) W / 2 + aw / 2 + 30f else W / 2 - aw / 2 - 30f
        CardGlyphs.pin(page.c, pinX, cy, 40f, pin)
        inkLine(page.c, a, ap, W / 2, cy)
    }

    /** "Roshan Khata", faint, at the very foot. */
    fun mark(page: Page, colour: Int = 0x66F6EBD0, cy: Float = 1880f) {
        val m = page.mark ?: return
        val p = textPaint(page.context, m, if (page.urdu) 27f else 25f, colour, false, page.locale, page.urdu)
        p.textAlign = Paint.Align.CENTER
        inkLine(page.c, m, p, W / 2, cy)
    }

    /** The owner's message, centred in [width], at most [lines] lines, with its middle on [cy]. */
    fun message(page: Page, text: String, cy: Float, colour: Int, size: Float, lines: Int = 3, width: Int = 900,
                italicLatin: Boolean = false, floor: Float = 32f): StaticLayout? {
        if (text.isBlank()) return null
        val p = textPaint(page.context, text, size, colour, false, page.locale, page.urdu)
        if (italicLatin && !page.urdu && !hasArabic(text)) p.typeface = Typeface.create(InvoiceFonts.playfairDisplay(page.context), Typeface.ITALIC)
        val l = fitBlock(text, p, width, page.rtl, lines, floor, if (page.urdu) 1.0f else 1.12f)
        put(page.c, l, (W - width) / 2, cy - l.height / 2f)
        return l
    }

    /** A soft round glow (a halo behind a moon or a lamp). */
    fun halo(c: Canvas, cx: Float, cy: Float, r: Float, colour: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx, cy, r, colour, colour and 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
    }

    /** Fixed little stars, drawn faint; the live preview makes them twinkle. */
    fun stars(c: Canvas, at: List<PosterMotion.Star>, colour: Int = 0xFFFFF0C0.toInt()) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
        for (s in at) { p.alpha = 150; c.drawCircle(s.x, s.y, s.r, p) }
    }

    /** An eight-point star outline, the tile of a geometric pattern. */
    fun eightPoint(cx: Float, cy: Float, outer: Float, inner: Float): Path {
        val p = Path()
        for (i in 0 until 16) {
            val rad = if (i % 2 == 0) outer else inner
            val a = Math.PI * i / 8 - Math.PI / 2
            val x = cx + (rad * cos(a)).toFloat()
            val y = cy + (rad * sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }
}
