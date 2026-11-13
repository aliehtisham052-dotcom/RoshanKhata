// GENERATED from app/src/main/java/com/innovation313/roshankhata/ui/PosterKit.kt
// by scripts/gen_desktop_poster.py. Do not edit: change the phone's file and re-run.
package com.innovation313.roshankhata.desktop.poster

import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.gfx.LinearGradient
import com.innovation313.roshankhata.desktop.gfx.Paint
import com.innovation313.roshankhata.desktop.gfx.Path
import com.innovation313.roshankhata.desktop.gfx.RadialGradient
import com.innovation313.roshankhata.desktop.gfx.Rect
import com.innovation313.roshankhata.desktop.gfx.RectF
import com.innovation313.roshankhata.desktop.gfx.Shader
import com.innovation313.roshankhata.desktop.gfx.Typeface
import com.innovation313.roshankhata.desktop.gfx.Layout
import com.innovation313.roshankhata.desktop.gfx.StaticLayout
import com.innovation313.roshankhata.desktop.gfx.TextDirectionHeuristics
import com.innovation313.roshankhata.desktop.gfx.TextPaint
import com.innovation313.roshankhata.desktop.invoice.InvoiceFonts
import com.innovation313.roshankhata.desktop.poster.PosterFields
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
        val heroOverlay: Boolean,
        /** What the owner chose on the poster screen (see PosterFields). */
        val fields: PosterFields = PosterFields(),
        /** The owner's own photo of the stock, for New stock. */
        val photo: Bitmap? = null,
        /**
         * True when the motion layer (PosterMotion) is drawn over this picture —
         * the live preview and the video — so parts it draws moving (the flag,
         * the confetti) are left to it; false for the shared still, which
         * draws them at rest itself.
         */
        val animated: Boolean = false,
        /** The shop's email, when the owner shows it (12 Oct). */
        val email: String? = null,
        /** The phone number is on WhatsApp too: the call button carries a chat mark. */
        val whatsapp: Boolean = false
    ) {
        val urdu = urduScript(locale)
        /** The numbers this poster shows: the call number and, when the owner gave one, a WhatsApp number of its own. */
        val contact: PosterContact.Contact = PosterContact.of(phone, whatsapp, fields.whatsappNumber)
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

    /**
     * Whether a text may be letter-spaced. Tracking opens a gap inside a word whose letters join (Urdu, Sindhi, Arabic
     * and Farsi) and cuts the headline line that Devanagari and Bengali hang their letters from: the Hindi kicker read
     * "अ भी-अ भी आ या" and the closed sign "बं द" (13 Nov). Only Latin words are tracked.
     */
    fun canTrack(s: String): Boolean = !hasArabic(s) && s.none { it in '\u0900'..'\u09FF' }

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
        if (!page.urdu && spaced && canTrack(text)) p.letterSpacing = 0.4f
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
                sealSize: Float = 138f, showPhone: Boolean = true, phoneCy: Float = cy + sealSize / 2 + 62f) {
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
        if (showPhone) contactRow(page, phoneCy, phoneColour)
    }

    /**
     * The numbers as one line of text, centred under a sign-off (the Eid, Ramazan, Jumu'ah and 14 August foot, the Adha
     * column, the Thank-you card). One call number is as it always was: the digits and nothing else. A WhatsApp number
     * brings the marks (a handset, a chat bubble) so the two can be told apart; two numbers share the line, shrunk
     * together until both fit in [maxWidth], the call number first on the reading side.
     */
    fun contactRow(page: Page, cy: Float, colour: Int, size: Float = 36f, maxWidth: Float = 940f) {
        val items = page.contact.items
        if (items.isEmpty()) return
        val c = page.c
        fun paint(sz: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(InvoiceFonts.sora(page.context), Typeface.BOLD)
            textSize = sz; color = colour; textAlign = Paint.Align.LEFT; letterSpacing = 0.14f
        }
        if (items.size == 1 && items[0].kind == PosterContact.Kind.CALL) {
            val p = paint(size)
            p.textAlign = Paint.Align.CENTER
            inkLine(c, items[0].number, p, W / 2, cy)
            return
        }
        var sz = size
        var ps = items.map { paint(sz) }
        fun total(): Float = items.indices.sumOf { (CONTACT_MARK * sz / 36f + CONTACT_GAP * sz / 36f + ps[it].measureText(items[it].number)).toDouble() }.toFloat() +
            (items.size - 1) * CONTACT_BETWEEN * sz / 36f
        while (total() > maxWidth && sz > 26f) { sz -= 2f; ps = items.map { paint(sz) } }
        val k = sz / 36f
        val mark = CONTACT_MARK * k
        val gap = CONTACT_GAP * k
        val between = CONTACT_BETWEEN * k
        // Reading order: the first item on the reading side, from the right on an Urdu poster.
        var x = if (page.rtl) W / 2 + total() / 2 else W / 2 - total() / 2
        for ((i, item) in items.withIndex()) {
            val w = mark + gap + ps[i].measureText(item.number)
            val left = if (page.rtl) x - w else x
            val markCx = if (page.rtl) left + w - mark / 2 else left + mark / 2
            val textX = if (page.rtl) left else left + mark + gap
            when (item.kind) {
                PosterContact.Kind.CALL -> PosterGlyphs.call(c, markCx, cy, mark, colour)
                PosterContact.Kind.WHATSAPP -> PosterGlyphs.chatLine(c, markCx, cy, mark * 1.1f, colour)
            }
            inkLine(c, item.number, ps[i], textX, cy)
            x = if (page.rtl) x - w - between else x + w + between
        }
    }

    // The marks and gaps of a contact row at the usual 36 sized text (scaled with the text when a row must shrink).
    private const val CONTACT_MARK = 34f
    private const val CONTACT_GAP = 12f
    private const val CONTACT_BETWEEN = 60f

    /**
     * The whole foot as one block (13 Nov 2026, see [PosterFoot]): the sign-off, the phone under it, the address and
     * the email, and the app's mark. [cy] and [sealSize] are where the design wants the seal row; it stands there
     * unless the rows under it need the room, and then it is lifted (never above [topLimit], the lowest the art
     * above reaches plus its margin) and, if it must, made smaller. No row can overlap another.
     */
    fun foot(page: Page, from: String, cy: Float, topLimit: Float = 0f, nameColour: Int = GOLD,
             fromColour: Int = 0xFF9FE3D6.toInt(), phoneColour: Int = 0xB3F6EBD0.toInt(),
             addressColour: Int = 0xB3F6EBD0.toInt(), markColour: Int = 0x66F6EBD0,
             inner: Int = 0xFF157A72.toInt(), innerDark: Int = 0xFF04211F.toInt(), sealSize: Float = 138f) {
        val plan = PosterFoot.plan(cy, sealSize, page.contact.any, addressLines(page), page.mark != null, topLimit,
            addressPitch(page))
        signOff(page, from, plan.cy, nameColour, fromColour, phoneColour, inner, innerDark, plan.sealSize, phoneCy = plan.phoneCy)
        address(page, plan.addressCy, addressColour)
        mark(page, markColour, plan.markCy)
    }

    /**
     * The call button (12 Oct, redrawn): a gold pill, a coloured disc ringed in light with the standard filled call
     * mark, the number in 0-9, and, when the number is on WhatsApp, a chat mark at the pill's other end. With a
     * WhatsApp number of its own (13 Nov) the one pill becomes two slimmer ones in the same place, the call number over
     * the WhatsApp number, each disc with its own mark (a handset, a chat bubble); a WhatsApp number alone is one
     * pill with the chat mark in its disc. Nothing when the poster shows no number.
     */
    fun phonePill(page: Page, pill: RectF, disc: Int = 0xFF1E6B4A.toInt(), discDark: Int = 0xFF062418.toInt(),
                  glyph: Int = 0xFFFFFFFF.toInt(), size: Float = 69f) {
        val items = page.contact.items
        if (items.isEmpty()) return
        if (items.size == 1) {
            numberPill(page, pill, items[0], page.contact.chatMark, disc, discDark, glyph, size, slim = false)
            return
        }
        val h = (pill.height() - PILL_GAP) / 2f
        numberPill(page, RectF(pill.left, pill.top, pill.right, pill.top + h), items[0], false, disc, discDark, glyph,
            size * SLIM_TEXT, slim = true)
        numberPill(page, RectF(pill.left, pill.bottom - h, pill.right, pill.bottom), items[1], false, disc, discDark, glyph,
            size * SLIM_TEXT, slim = true)
    }

    /** The space between the two pills of a call and a WhatsApp number, and how much smaller their numbers are set. */
    private const val PILL_GAP = 14f
    private const val SLIM_TEXT = 0.68f

    /** One gold pill for one number: [chatMark] adds the chat mark at its other end, [slim] is the half-height one of a pair. */
    private fun numberPill(page: Page, pill: RectF, item: PosterContact.Item, chatMark: Boolean, disc: Int, discDark: Int,
                           glyph: Int, size: Float, slim: Boolean) {
        val c = page.c
        val gold = Paint(Paint.ANTI_ALIAS_FLAG)
        gold.shader = LinearGradient(0f, pill.top, 0f, pill.bottom,
            intArrayOf(0xFFFBE6A2.toInt(), 0xFFE2B44C.toInt(), 0xFFB98322.toInt()), floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
        if (slim) gold.setShadowLayer(26f, 0f, 16f, 0x66000000) else gold.setShadowLayer(42f, 0f, 30f, 0x80000000.toInt())
        val r = pill.height() / 2
        c.drawRoundRect(pill, r, r, gold)
        // A fine light edge along the pill's top: it reads as metal, not paint.
        c.drawRoundRect(RectF(pill.left + 3f, pill.top + 3f, pill.right - 3f, pill.bottom - 3f), r - 3f, r - 3f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = 0x66FFFFFF })
        val dr = r - if (slim) 10f else 18f
        val icx = if (page.rtl) pill.right - (if (slim) 10f else 18f) - dr else pill.left + (if (slim) 10f else 18f) + dr
        val cy = pill.centerY()
        val d = Paint(Paint.ANTI_ALIAS_FLAG)
        d.shader = RadialGradient(icx - dr * 0.35f, cy - dr * 0.35f, dr * 1.4f, disc, discDark, Shader.TileMode.CLAMP)
        d.setShadowLayer(if (slim) 6f else 10f, 0f, if (slim) 3f else 4f, 0x55000000)
        c.drawCircle(icx, cy, dr, d)
        c.drawCircle(icx, cy, dr - if (slim) 5f else 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f; color = 0x59FFFFFF })
        when (item.kind) {
            PosterContact.Kind.CALL -> PosterGlyphs.call(c, icx, cy, dr * 1.05f, glyph)
            PosterContact.Kind.WHATSAPP -> PosterGlyphs.chat(c, icx, cy, dr * 1.35f, glyph, disc)
        }
        // The chat mark, at the other end (the one call number that is also on WhatsApp).
        var chatRoom = 0f
        if (chatMark) {
            val br = r * 0.40f
            val bcx = if (page.rtl) pill.left + 34f + br else pill.right - 34f - br
            PosterGlyphs.chat(c, bcx, cy, br * 2.1f, discDark, 0xFFF3CF6A.toInt())
            chatRoom = br * 2 + 34f
        }
        val pp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(InvoiceFonts.sora(page.context), Typeface.BOLD)
            textSize = size; color = 0xFF1F1505.toInt(); textAlign = Paint.Align.CENTER; letterSpacing = 0.02f
        }
        val pad = if (slim) 22f else 30f
        val textLeft = if (page.rtl) pill.left + pad + chatRoom else icx + dr + (if (slim) 18f else 24f)
        val textRight = if (page.rtl) icx - dr - (if (slim) 18f else 24f) else pill.right - pad - chatRoom
        fitWidth(pp, item.number, textRight - textLeft, floor = if (slim) 30f else 40f)
        inkLine(c, item.number, pp, (textLeft + textRight) / 2, cy)
    }

    // The address and the email: the plan says how they stand, so the foot can ask how tall they are before drawing.
    private const val ADDRESS_ICON = 40f
    private const val ADDRESS_ICON_GAP = 14f
    private const val ADDRESS_BETWEEN = 54f

    /**
     * How the address and the email stand: [lines] is 1 or 2, [size] the text size of the one line (0 for two), [pitch]
     * the distance from the first row to the second. An address written in Arabic script (Nastaliq) has tails and
     * stacked marks that reach into the row under it at the Latin pitch of 48 (13 Nov proofs), so it has 58.
     */
    private class AddressPlan(val items: List<Pair<Int, String>>, val lines: Int, val size: Float, val pitch: Float = PosterFoot.LINE)

    private fun addressPaint(page: Page, text: String, size: Float, colour: Int): TextPaint {
        val p = if (hasArabic(text)) textPaint(page.context, text, size, colour, false, page.locale, page.urdu)
            else textPaint(page.context, text, size, colour, false, Locale.ENGLISH, false)
        p.textAlign = Paint.Align.LEFT
        return p
    }

    private fun addressWidth(text: String, p: TextPaint) = ADDRESS_ICON + ADDRESS_ICON_GAP + p.measureText(text)

    /** One line when the address and the email fit 900 wide at 30 or more, else two; null when neither is shown. */
    private fun addressPlan(page: Page, colour: Int): AddressPlan? {
        val a = page.address?.trim()?.takeIf { it.isNotEmpty() }
        val e = page.email?.trim()?.takeIf { it.isNotEmpty() }
        if (a == null && e == null) return null
        val items = listOfNotNull(a?.let { 0 to it }, e?.let { 1 to it })
        if (items.size == 1) return AddressPlan(items, 1, 0f)
        val pitch = if (hasArabic(items[0].second)) PosterFoot.LINE_ARABIC else PosterFoot.LINE
        var size = if (page.urdu) 38f else 36f
        while (size >= 30f) {
            val ps = items.map { addressPaint(page, it.second, size, colour) }
            val total = addressWidth(items[0].second, ps[0]) + ADDRESS_BETWEEN + addressWidth(items[1].second, ps[1])
            if (total <= 900f) return AddressPlan(items, 1, size, pitch)
            size -= 2f
        }
        return AddressPlan(items, 2, 0f, pitch)
    }

    /** How many rows [address] draws for this page: 0 with neither an address nor an email, else 1 or 2. */
    fun addressLines(page: Page): Int = addressPlan(page, 0)?.lines ?: 0

    /** The distance between the first and the second row of [address], when there are two (see [AddressPlan]). */
    fun addressPitch(page: Page): Float = addressPlan(page, 0)?.pitch ?: PosterFoot.LINE

    /**
     * The address and the email under the call button, each with its mark:
     * on one line when both fit, else the address over the email (48 lower, 58 for an Arabic-script address).
     * Nothing is drawn for what the owner left out.
     */
    fun address(page: Page, cy: Float, colour: Int = 0xD9F6EBD0.toInt(), pin: Int = GOLD) {
        val plan = addressPlan(page, colour) ?: return
        val items = plan.items
        if (items.size == 2 && plan.lines == 1) {
            val ps = items.map { addressPaint(page, it.second, plan.size, colour) }
            val total = addressWidth(items[0].second, ps[0]) + ADDRESS_BETWEEN + addressWidth(items[1].second, ps[1])
            // Reading order: the address first, from the right on an Urdu poster.
            var x = if (page.rtl) W / 2 + total / 2 else W / 2 - total / 2
            for ((i, item) in items.withIndex()) {
                val w = addressWidth(item.second, ps[i])
                val left = if (page.rtl) x - w else x
                contactItem(page, item.first, item.second, ps[i], left, cy, ADDRESS_ICON, ADDRESS_ICON_GAP, pin)
                x = if (page.rtl) x - w - ADDRESS_BETWEEN else x + w + ADDRESS_BETWEEN
            }
            return
        }
        if (items.size == 2) {
            // Two lines: the address, then the email under it.
            for ((i, item) in items.withIndex()) {
                val p = addressPaint(page, item.second, if (page.urdu && hasArabic(item.second)) 36f else 33f, colour)
                fitWidth(p, item.second, 900f - ADDRESS_ICON - ADDRESS_ICON_GAP, floor = 24f)
                val w = addressWidth(item.second, p)
                contactItem(page, item.first, item.second, p, W / 2 - w / 2, cy + i * plan.pitch, ADDRESS_ICON * 0.9f, ADDRESS_ICON_GAP, pin)
            }
            return
        }
        val (kind, text) = items[0]
        val p = addressPaint(page, text, if (page.urdu) 39f else 38f, colour)
        fitWidth(p, text, 860f - ADDRESS_ICON - ADDRESS_ICON_GAP, floor = 26f)
        val w = addressWidth(text, p)
        contactItem(page, kind, text, p, W / 2 - w / 2, cy, ADDRESS_ICON, ADDRESS_ICON_GAP, pin)
    }

    /**
     * One contact item in the box from [left]: its mark on the reading side,
     * then the words. The mark is placed from the words' ink, not their
     * advance: a Nastaliq letter can reach past it.
     */
    private fun contactItem(page: Page, kind: Int, text: String, p: TextPaint, left: Float, cy: Float,
                            iconSize: Float, iconGap: Float, colour: Int) {
        val tw = p.measureText(text)
        val ink = Rect()
        p.getTextBounds(text, 0, text.length, ink)
        val rtl = page.rtl
        val textX = if (rtl) left else left + iconSize + iconGap
        val iconX = if (rtl) maxOf(textX + ink.right, textX + tw) + iconGap + iconSize / 2
            else minOf(textX + ink.left, textX) - iconGap - iconSize / 2
        if (kind == 0) PosterGlyphs.pin(page.c, iconX, cy, iconSize, colour)
        else PosterGlyphs.mail(page.c, iconX, cy, iconSize * 0.95f, colour)
        inkLine(page.c, text, p, textX, cy)
    }

    /**
     * "Roshan Khata", faint, at the very foot. With [maxInk] above 0 the words shrink until their ink is at most that
     * tall, for a mark that must stand in a narrow band (the Thank-you card's, under its frame): the ink of Nastaliq,
     * Devanagari and Bengali words is far taller than their size.
     */
    fun mark(page: Page, colour: Int = 0x66F6EBD0, cy: Float = 1880f, maxInk: Float = 0f) {
        val m = page.mark ?: return
        val p = textPaint(page.context, m, if (page.urdu) 27f else 25f, colour, false, page.locale, page.urdu)
        p.textAlign = Paint.Align.CENTER
        if (maxInk > 0f) {
            val b = Rect()
            p.getTextBounds(m, 0, m.length, b)
            val ink = (b.bottom - b.top).toFloat()
            if (ink > maxInk) p.textSize = p.textSize * maxInk / ink
        }
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

    /**
     * Label-and-value rows on a panel (timings, why closed, Ramazan hours):
     * the label on the reading side in gold, the value on the far side, a
     * hairline between rows; a row marked closed shows its value in red. An
     * optional [title] sits centred over them. Returns the value size used.
     */
    fun rows(page: Page, panel: RectF, rows: List<PosterWords.Row>, title: String? = null,
             label: Int = GOLD, value: Int = 0xFFFFFFFF.toInt(), closed: Int = 0xFFFF8A80.toInt(),
             good: Int = 0xFF7EE0A8.toInt(), rule: Int = 0x26FFFFFF): Float {
        if (rows.isEmpty()) return 0f
        val c = page.c
        var top = panel.top + 24f
        if (title != null) {
            val tp = textPaint(page.context, title, if (page.urdu) 44f else 38f, label, true, page.locale, page.urdu)
            tp.textAlign = Paint.Align.CENTER
            fitWidth(tp, title, panel.width() - 96f, 24f)
            inkLine(c, title, tp, panel.centerX(), top + 46f)
            top += 96f
            c.drawLine(panel.left + 48f, top, panel.right - 48f, top, Paint().apply { color = rule; strokeWidth = 2f })
        }
        val inner = panel.width() - 96f
        val rowH = (panel.bottom - 24f - top) / rows.size
        val line = Paint().apply { color = rule; strokeWidth = 2f }
        val size = if (page.urdu) 46f else 42f
        var used = 0f
        for ((i, row) in rows.withIndex()) {
            val cy = top + rowH * (i + 0.5f)
            if (i > 0) c.drawLine(panel.left + 48f, top + rowH * i, panel.right - 48f, top + rowH * i, line)
            val lp = textPaint(page.context, row.label, size, label, true, page.locale, page.urdu)
            val vp = textPaint(page.context, row.value, size, when { row.closed -> closed; row.good -> good; else -> value }, true, page.locale, page.urdu)
            fitWidth(lp, row.label, inner * 0.42f, 24f)
            fitWidth(vp, row.value, inner * 0.56f, 24f)
            lp.textAlign = if (page.rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            vp.textAlign = if (page.rtl) Paint.Align.LEFT else Paint.Align.RIGHT
            inkLine(c, row.label, lp, if (page.rtl) panel.right - 48f else panel.left + 48f, cy)
            inkLine(c, row.value, vp, if (page.rtl) panel.left + 48f else panel.right - 48f, cy)
            used = maxOf(used, vp.textSize)
        }
        return used
    }

    /** The height a [rows] panel wants for [n] rows (and a title). */
    fun rowsHeight(n: Int, titled: Boolean) = 48f + n * 112f + if (titled) 96f else 0f

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
