package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Trade

/**
 * The shop's own logo, drawn on the phone (10 Nov 2026, the owner: most shops have none, and a shopkeeper is not
 * a designer). Every design is built from what the app already knows of the shop - its name (or its Urdu name,
 * or its letters), its trade's own two-tone picture and the trade's own line ("Pesticides · seeds · feed") - in
 * the trade's colour first and five more pairs a shop may prefer. Nothing is fetched, nothing is paid for, and
 * the picture is drawn at full size for print, so it is never a blurred photo.
 *
 * Eight designs: a ringed medallion, a serif monogram, a shield, a seal with the name around it, a side-by-side
 * mark, an Urdu medallion, a badge and a plain outline. No company's mark or emblem is copied; the trade
 * pictures are the app's own.
 */
object LogoDesigns {

    enum class Style { RING, MONOGRAM, SHIELD, SEAL, SIDE, URDU, BADGE, OUTLINE }

    /** What the big text on a design says. */
    enum class Show { NAME, LETTERS, URDU }

    /** A fill and the colour drawn on it. */
    data class Palette(val fill: Int, val accent: Int, val ink: Int = Color.WHITE)

    data class Spec(
        val style: Style,
        val palette: Palette,
        val show: Show,
        val name: String,
        val urduName: String?,
        val tagline: String,
        val trade: Trade
    )

    private const val GOLD = 0xFFE9B949.toInt()
    private const val CREAM = 0xFFFFF4DC.toInt()

    /** The trade's own colour first, then five pairs that read well on paper and on a phone. */
    fun palettes(context: Context, trade: Trade): List<Palette> {
        val own = darker(ContextCompat.getColor(context, trade.inkRes))
        return listOf(
            Palette(own, GOLD),
            Palette(0xFF1F3A5F.toInt(), GOLD),
            Palette(0xFF7A1F2B.toInt(), GOLD),
            Palette(0xFF0F5E5E.toInt(), CREAM),
            Palette(0xFF1A1A18.toInt(), GOLD),
            Palette(0xFFB4501A.toInt(), CREAM)
        ).distinctBy { it.fill }
    }

    /** Every design that can be drawn for this shop: the Urdu one only when an Urdu name is kept. */
    fun styles(urduName: String?): List<Style> =
        Style.values().filter { it != Style.URDU || !urduName.isNullOrBlank() }

    /** Up to two letters: the first of the first two words that start with a letter. */
    fun letters(name: String): String {
        val words = name.split(Regex("[^\\p{L}\\p{N}]+")).filter { w -> w.isNotEmpty() && w.first().isLetter() }
        return words.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "•" }
    }

    fun draw(context: Context, spec: Spec, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size / 1000f
        c.scale(s, s)
        val f = Fonts(context)
        when (spec.style) {
            Style.RING -> ring(context, c, spec, f)
            Style.MONOGRAM -> monogram(c, spec, f)
            Style.SHIELD -> shield(context, c, spec, f)
            Style.SEAL -> seal(context, c, spec, f)
            Style.SIDE -> side(context, c, spec, f)
            Style.URDU -> urdu(c, spec, f)
            Style.BADGE -> badge(c, spec, f)
            Style.OUTLINE -> outline(context, c, spec, f)
        }
        return bmp
    }

    // ---------- the designs, drawn on a 1000 x 1000 square ----------

    private fun ring(context: Context, c: Canvas, s: Spec, f: Fonts) {
        c.drawCircle(500f, 500f, 480f, fill(s.palette.fill))
        c.drawCircle(500f, 500f, 440f, stroke(s.palette.accent, 14f))
        picture(context, c, s.trade, 500f, 300f, 230f, s.palette.accent)
        mainText(c, s, f, 500f, 600f, 640f, 120f, s.palette.ink)
        line(c, s.tagline.uppercase(), 500f, 790f, 560f, 40f, s.palette.accent, f.sans, spacing = 0.12f)
    }

    private fun monogram(c: Canvas, s: Spec, f: Fonts) {
        c.drawRoundRect(RectF(40f, 40f, 960f, 960f), 200f, 200f, fill(s.palette.fill))
        c.drawRoundRect(RectF(80f, 80f, 920f, 920f), 170f, 170f, stroke(s.palette.accent, 8f))
        line(c, letters(s.name), 500f, 560f, 640f, 380f, s.palette.accent, f.serif)
        c.drawRect(330f, 640f, 670f, 652f, fill(s.palette.accent))
        line(c, s.name.uppercase(), 500f, 740f, 680f, 64f, s.palette.ink, f.sans, spacing = 0.08f)
        line(c, s.tagline, 500f, 820f, 640f, 40f, s.palette.accent, f.sans)
    }

    private fun shield(context: Context, c: Canvas, s: Spec, f: Fonts) {
        val outer = Path().apply {
            moveTo(500f, 30f); lineTo(900f, 160f); lineTo(900f, 470f)
            cubicTo(900f, 740f, 720f, 900f, 500f, 980f); cubicTo(280f, 900f, 100f, 740f, 100f, 470f)
            lineTo(100f, 160f); close()
        }
        val inner = Path().apply {
            moveTo(500f, 90f); lineTo(850f, 200f); lineTo(850f, 470f)
            cubicTo(850f, 700f, 695f, 845f, 500f, 920f); cubicTo(305f, 845f, 150f, 700f, 150f, 470f)
            lineTo(150f, 200f); close()
        }
        c.drawPath(outer, fill(s.palette.fill))
        c.drawPath(inner, stroke(s.palette.accent, 12f))
        picture(context, c, s.trade, 500f, 330f, 220f, s.palette.accent)
        mainText(c, s, f, 500f, 610f, 560f, 104f, s.palette.ink)
        line(c, s.tagline.uppercase(), 500f, 780f, 440f, 34f, s.palette.accent, f.sans, spacing = 0.1f)
    }

    private fun seal(context: Context, c: Canvas, s: Spec, f: Fonts) {
        c.drawCircle(500f, 500f, 480f, fill(s.palette.fill))
        c.drawCircle(500f, 500f, 455f, stroke(s.palette.accent, 6f))
        c.drawCircle(500f, 500f, 290f, stroke(s.palette.accent, 6f))
        // The name over the top and the trade's line under the bottom, each read left to right and upright, a dot
        // between them on each side - a stamp's own order (10 Nov 2026, the owner: one line once round turned the
        // bottom words upside down).
        val r = 372f
        val box = RectF(500f - r, 500f - r, 500f + r, 500f + r)
        val top = Path().apply { addArc(box, 180f, 180f) }      // left -> over the top -> right
        val bottom = Path().apply { addArc(box, 180f, -180f) }  // left -> under the bottom -> right
        val half = (Math.PI * r).toFloat() * 0.84f
        val name = s.name.uppercase()
        val tag = s.tagline.uppercase()
        val p = text(s.palette.ink, 66f, f.sansBold).apply { letterSpacing = 0.1f; textAlign = Paint.Align.LEFT }
        while (p.measureText(name) > half && p.textSize > 30f) p.textSize -= 2f
        val q = text(s.palette.accent, 46f, f.sansBold).apply { letterSpacing = 0.12f; textAlign = Paint.Align.LEFT }
        while (q.measureText(tag) > half && q.textSize > 24f) q.textSize -= 2f
        val arc = (Math.PI * r).toFloat()
        // Each centred on its half of the ring; the letters' middle on the ring's middle line (on the top the
        // letters' foot faces the centre, on the bottom their head does, so the same offset serves both).
        c.drawTextOnPath(name, top, (arc - p.measureText(name)) / 2f, p.textSize * 0.36f, p)
        c.drawTextOnPath(tag, bottom, (arc - q.measureText(tag)) / 2f, q.textSize * 0.36f, q)
        c.drawCircle(500f - r, 500f, 13f, fill(s.palette.accent))
        c.drawCircle(500f + r, 500f, 13f, fill(s.palette.accent))
        picture(context, c, s.trade, 500f, 500f, 300f, s.palette.accent)
    }

    private fun side(context: Context, c: Canvas, s: Spec, f: Fonts) {
        c.drawRoundRect(RectF(60f, 260f, 940f, 740f), 80f, 80f, fill(Color.WHITE))
        c.drawRoundRect(RectF(60f, 260f, 940f, 740f), 80f, 80f, stroke(s.palette.fill, 10f))
        c.drawRoundRect(RectF(110f, 330f, 450f, 670f), 70f, 70f, fill(s.palette.fill))
        picture(context, c, s.trade, 280f, 500f, 220f, s.palette.accent)
        val top = if (s.show == Show.URDU && !s.urduName.isNullOrBlank()) s.urduName else s.name
        val two = split(top)
        val face = if (s.show == Show.URDU && !s.urduName.isNullOrBlank()) f.urdu else f.sansBold
        lineStart(c, two.first, 490f, if (two.second == null) 510f else 470f, 420f, 96f, s.palette.fill, face)
        two.second?.let { lineStart(c, it, 490f, 570f, 420f, 72f, s.palette.fill, face) }
        lineStart(c, s.tagline, 490f, 640f, 420f, 34f, 0xFF5F5E5A.toInt(), f.sans)
    }

    private fun urdu(c: Canvas, s: Spec, f: Fonts) {
        c.drawCircle(500f, 500f, 480f, fill(s.palette.fill))
        c.drawCircle(500f, 500f, 440f, stroke(s.palette.accent, 12f))
        line(c, s.urduName.orEmpty(), 500f, 560f, 700f, 150f, s.palette.ink, f.urdu)
        c.drawRect(380f, 650f, 620f, 660f, fill(s.palette.accent))
        line(c, s.tagline, 500f, 740f, 560f, 40f, s.palette.accent, f.sans)
    }

    private fun badge(c: Canvas, s: Spec, f: Fonts) {
        c.drawRoundRect(RectF(40f, 40f, 960f, 960f), 260f, 260f, fill(s.palette.fill))
        val big = if (s.show == Show.URDU && !s.urduName.isNullOrBlank()) s.urduName else letters(s.name)
        val face = if (s.show == Show.URDU && !s.urduName.isNullOrBlank()) f.urdu else f.sansBold
        line(c, big, 500f, 560f, 720f, 340f, s.palette.ink, face)
        line(c, s.name.uppercase(), 500f, 740f, 680f, 58f, s.palette.accent, f.sans, spacing = 0.12f)
    }

    private fun outline(context: Context, c: Canvas, s: Spec, f: Fonts) {
        c.drawCircle(500f, 500f, 470f, fill(Color.WHITE))
        c.drawCircle(500f, 500f, 460f, stroke(s.palette.fill, 22f))
        picture(context, c, s.trade, 500f, 310f, 230f, s.palette.fill)
        mainText(c, s, f, 500f, 610f, 640f, 116f, s.palette.fill)
        line(c, s.tagline.uppercase(), 500f, 790f, 560f, 40f, 0xFF5F5E5A.toInt(), f.sans, spacing = 0.12f)
    }

    // ---------- pieces ----------

    /** The big words of a medallion: the name in up to two lines, the letters, or the Urdu name. */
    private fun mainText(c: Canvas, s: Spec, f: Fonts, cx: Float, y: Float, maxW: Float, size: Float, colour: Int) {
        when {
            s.show == Show.URDU && !s.urduName.isNullOrBlank() -> line(c, s.urduName, cx, y + size * 0.3f, maxW, size * 1.15f, colour, f.urdu)
            s.show == Show.LETTERS -> line(c, letters(s.name), cx, y + size * 0.45f, maxW, size * 1.9f, colour, f.serif)
            else -> {
                val (a, b) = split(s.name.uppercase())
                if (b == null) line(c, a, cx, y + size * 0.35f, maxW, size, colour, f.sansBold, spacing = 0.04f)
                else {
                    line(c, a, cx, y, maxW, size, colour, f.sansBold, spacing = 0.04f)
                    line(c, b, cx, y + size * 0.95f, maxW, size * 0.62f, colour, f.sansBold, spacing = 0.16f)
                }
            }
        }
    }

    /** A name in two lines when it has more than one word: the first word, then the rest. */
    private fun split(name: String): Pair<String, String?> {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return (words.firstOrNull() ?: name) to null
        return words.first() to words.drop(1).joinToString(" ")
    }

    /** The trade's two-tone picture in one colour (both tones keep their alpha, lesson 56). */
    private fun picture(context: Context, c: Canvas, trade: Trade, cx: Float, cy: Float, size: Float, colour: Int) {
        val d = ContextCompat.getDrawable(context, trade.iconRes)?.mutate() ?: return
        d.setTint(colour)
        val h = size / 2
        d.setBounds((cx - h).toInt(), (cy - h).toInt(), (cx + h).toInt(), (cy + h).toInt())
        d.draw(c)
    }

    /** One centred line, made smaller until it fits [maxW]. */
    private fun line(c: Canvas, t: String, cx: Float, baseline: Float, maxW: Float, size: Float, colour: Int, face: Typeface, spacing: Float = 0f) {
        if (t.isBlank()) return
        val p = text(colour, size, face).apply { letterSpacing = spacing; textAlign = Paint.Align.CENTER }
        while (p.measureText(t) > maxW && p.textSize > 18f) p.textSize -= 2f
        c.drawText(t, cx, baseline, p)
    }

    /** One line from a starting x, made smaller until it fits [maxW]. */
    private fun lineStart(c: Canvas, t: String, x: Float, baseline: Float, maxW: Float, size: Float, colour: Int, face: Typeface) {
        if (t.isBlank()) return
        val p = text(colour, size, face).apply { textAlign = Paint.Align.LEFT }
        while (p.measureText(t) > maxW && p.textSize > 18f) p.textSize -= 2f
        c.drawText(t, x, baseline, p)
    }

    private fun text(colour: Int, size: Float, face: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour; textSize = size; typeface = face
    }

    private fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = Paint.Style.FILL }

    private fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour; style = Paint.Style.STROKE; strokeWidth = width
    }

    /** The trade's chip ink made deep enough to carry white words. */
    private fun darker(colour: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(colour, hsv)
        hsv[2] = minOf(hsv[2], 0.42f)
        return Color.HSVToColor(hsv)
    }

    private class Fonts(context: Context) {
        val sans: Typeface = font(context, R.font.manrope_regular, Typeface.SANS_SERIF)
        val sansBold: Typeface = font(context, R.font.manrope_bold, Typeface.DEFAULT_BOLD)
        val serif: Typeface = font(context, R.font.playfair_display_variable, Typeface.SERIF)
        val urdu: Typeface = font(context, R.font.noto_nastaliq_urdu, Typeface.DEFAULT)

        private fun font(context: Context, id: Int, fallback: Typeface): Typeface =
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
    }
}
