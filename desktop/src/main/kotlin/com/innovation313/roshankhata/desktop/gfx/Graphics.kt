package com.innovation313.roshankhata.desktop.gfx

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.text.AttributedString

/*
 * The few pieces of android.graphics the phone's printed designs use
 * (invoices, and later the business cards), on Java2D (Phase 10).
 *
 * The point is that the phone's drawing code is COPIED, not redrawn: the same
 * coordinates, the same palettes, the same order of calls, with only its
 * imports pointed here. One design drawn twice by two hands drifts; one
 * design drawn by the same code on two canvases cannot.
 *
 * Android's rules kept: drawText's x is the left, centre or right of the text
 * by Paint.Align and y is the baseline; drawLine strokes whatever the style;
 * a stroke width of 0 is a hairline; alpha lives in the colour; a Paint is
 * copied when drawn, so changing it afterwards does not change what was drawn.
 *
 * A Canvas records, a page replays: a page can then be printed at any scale,
 * shown on screen, or turned into a picture for a test to look at.
 */

object Color {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val TRANSPARENT = 0
    const val GRAY = 0xFF888888.toInt()
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)
    fun alpha(c: Int): Int = c ushr 24
    fun red(c: Int): Int = (c shr 16) and 0xFF
    fun green(c: Int): Int = (c shr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF

    /** "#RRGGBB" or "#AARRGGBB", as Android reads it. */
    fun parseColor(s: String): Int {
        val hex = s.removePrefix("#")
        val v = hex.toLong(16)
        return when (hex.length) {
            6 -> (0xFF000000 or v).toInt()
            8 -> v.toInt()
            else -> throw IllegalArgumentException("Unknown color $s")
        }
    }
}

class Rect(@JvmField var left: Int = 0, @JvmField var top: Int = 0, @JvmField var right: Int = 0, @JvmField var bottom: Int = 0) {
    fun width() = right - left
    fun height() = bottom - top
}

class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    constructor(r: Rect) : this(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    constructor(r: RectF) : this(r.left, r.top, r.right, r.bottom)
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun inset(dx: Float, dy: Float) { left += dx; top += dy; right -= dx; bottom -= dy }
    fun offset(dx: Float, dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    fun contains(x: Float, y: Float) = left < right && top < bottom && x >= left && x < right && y >= top && y < bottom
    fun intersects(l: Float, t: Float, r: Float, b: Float) = left < r && l < right && top < b && t < bottom

    companion object {
        fun intersects(a: RectF, b: RectF) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    }
}

/** A face plus a style. Android's synthetic bold/italic becomes Java2D's, which embolds a face that has no bold file. */
class Typeface internal constructor(val font: Font, val style: Int) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3
        val DEFAULT = Typeface(Font(Font.SANS_SERIF, Font.PLAIN, 1), NORMAL)
        val DEFAULT_BOLD = Typeface(Font(Font.SANS_SERIF, Font.PLAIN, 1), BOLD)
        val SANS_SERIF = DEFAULT
        val SERIF = Typeface(Font(Font.SERIF, Font.PLAIN, 1), NORMAL)
        val MONOSPACE = Typeface(Font(Font.MONOSPACED, Font.PLAIN, 1), NORMAL)
        fun create(family: Typeface?, style: Int) = Typeface((family ?: DEFAULT).font, style)
        fun of(font: Font) = Typeface(font, NORMAL)
    }
}

open class Shader {
    enum class TileMode { CLAMP, REPEAT, MIRROR }
    internal open fun awt(): java.awt.Paint? = null
}

class LinearGradient(
    private val x0: Float, private val y0: Float, private val x1: Float, private val y1: Float,
    private val colors: IntArray, private val positions: FloatArray?, private val tile: Shader.TileMode
) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, tile: Shader.TileMode) :
        this(x0, y0, x1, y1, intArrayOf(c0, c1), null, tile)

    override fun awt(): java.awt.Paint? {
        if (x0 == x1 && y0 == y1) return java.awt.Color(colors.first(), true)
        val stops = positions ?: FloatArray(colors.size) { i -> if (colors.size == 1) 0f else i / (colors.size - 1f) }
        // Java wants strictly rising stops; nudge equal neighbours apart.
        val fixed = stops.copyOf()
        for (i in 1 until fixed.size) if (fixed[i] <= fixed[i - 1]) fixed[i] = (fixed[i - 1] + 1e-4f).coerceAtMost(1f)
        val cycle = when (tile) {
            Shader.TileMode.CLAMP -> MultipleGradientPaint.CycleMethod.NO_CYCLE
            Shader.TileMode.REPEAT -> MultipleGradientPaint.CycleMethod.REPEAT
            Shader.TileMode.MIRROR -> MultipleGradientPaint.CycleMethod.REFLECT
        }
        return LinearGradientPaint(Point2D.Float(x0, y0), Point2D.Float(x1, y1), fixed, colors.map { java.awt.Color(it, true) }.toTypedArray(), cycle)
    }
}

open class PathEffect
class DashPathEffect(val intervals: FloatArray, val phase: Float) : PathEffect()

/**
 * A picture. One made by [createBitmap] can be drawn into with Canvas(bitmap),
 * as on Android (the posters' flag, coin face and neon glow are drawn that
 * way): what such a canvas records is played onto the picture the moment
 * the picture is read, so it is always up to date when it is drawn.
 */
class Bitmap(private val img: BufferedImage) {
    internal val painters = mutableListOf<Canvas>()
    val image: BufferedImage get() { painters.forEach { it.flushInto(img) }; return img }
    val width: Int get() = img.width
    val height: Int get() = img.height
    var isRecycled: Boolean = false
        private set
    fun recycle() { isRecycled = true }

    enum class Config { ARGB_8888 }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap =
            Bitmap(BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB))
    }
}

/** android.graphics.PointF. */
class PointF(@JvmField var x: Float = 0f, @JvmField var y: Float = 0f)

/** A blur on what a paint draws (android.graphics.BlurMaskFilter); only NORMAL is drawn differently from none. */
open class MaskFilter
class BlurMaskFilter(val radius: Float, val style: Blur) : MaskFilter() {
    enum class Blur { NORMAL, SOLID, OUTER, INNER }
}

open class Paint(flags: Int = 0) {
    enum class Align { LEFT, CENTER, RIGHT }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }

    /** Android's FontMetrics: ascent and top are negative (above the baseline). */
    class FontMetrics {
        @JvmField var top = 0f
        @JvmField var ascent = 0f
        @JvmField var descent = 0f
        @JvmField var bottom = 0f
        @JvmField var leading = 0f
    }

    var isAntiAlias: Boolean = flags and ANTI_ALIAS_FLAG != 0
    var color: Int = Color.BLACK
    var alpha: Int
        get() = color ushr 24
        set(value) { color = (color and 0x00FFFFFF) or (value.coerceIn(0, 255) shl 24) }
    var textSize: Float = 12f
    var textAlign: Align = Align.LEFT
    var typeface: Typeface? = null
    var isFakeBoldText: Boolean = false
    var shader: Shader? = null
    var strokeWidth: Float = 0f
    var style: Style = Style.FILL
    var pathEffect: PathEffect? = null
    var letterSpacing: Float = 0f
    /** OpenType features the phone asks for ("tnum", tabular figures); kept, not applied — Java2D's text layout has no switch for them. */
    var fontFeatureSettings: String? = null
    var isFilterBitmap: Boolean = flags and FILTER_BITMAP_FLAG != 0
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var maskFilter: MaskFilter? = null
    var textLocale: java.util.Locale = java.util.Locale.getDefault()

    /** A soft shadow under what is drawn (setShadowLayer); radius 0 is none. */
    var shadowRadius = 0f
        private set
    var shadowDx = 0f
        private set
    var shadowDy = 0f
        private set
    var shadowColor = 0
        private set

    fun setShadowLayer(radius: Float, dx: Float, dy: Float, shadowColor: Int) {
        shadowRadius = radius; shadowDx = dx; shadowDy = dy; this.shadowColor = shadowColor
    }

    fun clearShadowLayer() = setShadowLayer(0f, 0f, 0f, 0)

    constructor(other: Paint) : this() { set(other) }

    /** Everything [other] has, copied into this paint (Android's Paint.set). */
    fun set(other: Paint) {
        isAntiAlias = other.isAntiAlias; color = other.color; textSize = other.textSize; textAlign = other.textAlign
        typeface = other.typeface; isFakeBoldText = other.isFakeBoldText; shader = other.shader
        strokeWidth = other.strokeWidth; style = other.style; pathEffect = other.pathEffect
        letterSpacing = other.letterSpacing; fontFeatureSettings = other.fontFeatureSettings; isFilterBitmap = other.isFilterBitmap; strokeCap = other.strokeCap
        strokeJoin = other.strokeJoin; maskFilter = other.maskFilter; textLocale = other.textLocale
        setShadowLayer(other.shadowRadius, other.shadowDx, other.shadowDy, other.shadowColor)
    }

    fun measureText(text: String): Float = Text.advance(text, this)
    fun measureText(text: CharSequence, start: Int, end: Int): Float = Text.advance(text.subSequence(start, end).toString(), this)

    /** Android's getTextBounds: the ink of [text] about its baseline at x = 0 (top negative), rounded outward. */
    fun getTextBounds(text: String, start: Int, end: Int, bounds: Rect) {
        val b = Text.layout(text.substring(start, end), this)?.bounds
        if (b == null) { bounds.left = 0; bounds.top = 0; bounds.right = 0; bounds.bottom = 0; return }
        bounds.left = kotlin.math.floor(b.minX).toInt()
        bounds.top = kotlin.math.floor(b.minY).toInt()
        bounds.right = kotlin.math.ceil(b.maxX).toInt()
        bounds.bottom = kotlin.math.ceil(b.maxY).toInt()
    }

    val fontMetrics: FontMetrics
        get() {
            val lm = Text.font(this).getLineMetrics("Ag", Text.frc)
            return FontMetrics().apply {
                ascent = -lm.ascent; descent = lm.descent; leading = lm.leading
                top = ascent * 1.1f; bottom = descent * 1.1f
            }
        }

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
    }
}

/** android.text.TextPaint: a Paint for a [StaticLayout]. */
class TextPaint : Paint {
    constructor(flags: Int = 0) : super(flags)
    constructor(other: Paint) : super(other)
}

/** A sweep of colours around a centre, clockwise from three o'clock (android.graphics.SweepGradient). */
class SweepGradient(
    private val cx: Float, private val cy: Float, private val colors: IntArray, private val positions: FloatArray?
) : Shader() {
    override fun awt(): java.awt.Paint = SweepPaint(cx, cy, colors, positions ?: FloatArray(colors.size) { i ->
        if (colors.size == 1) 0f else i / (colors.size - 1f)
    })
}

private class SweepPaint(val cx: Float, val cy: Float, val colors: IntArray, val stops: FloatArray) : java.awt.Paint {
    override fun getTransparency() = java.awt.Transparency.TRANSLUCENT

    override fun createContext(
        cm: java.awt.image.ColorModel?, deviceBounds: java.awt.Rectangle?, userBounds: Rectangle2D?,
        xform: AffineTransform, hints: RenderingHints?
    ): java.awt.PaintContext {
        val inverse = runCatching { xform.createInverse() }.getOrDefault(AffineTransform())
        val model = java.awt.image.ColorModel.getRGBdefault()
        return object : java.awt.PaintContext {
            override fun dispose() {}
            override fun getColorModel() = model
            override fun getRaster(x: Int, y: Int, w: Int, h: Int): java.awt.image.Raster {
                val raster = model.createCompatibleWritableRaster(w, h)
                val row = IntArray(w)
                val pt = Point2D.Double()
                for (j in 0 until h) {
                    for (i in 0 until w) {
                        pt.setLocation(x + i + 0.5, y + j + 0.5)
                        inverse.transform(pt, pt)
                        var a = kotlin.math.atan2(pt.y - cy, pt.x - cx) / (2 * Math.PI)
                        if (a < 0) a += 1.0
                        row[i] = colourAt(a.toFloat())
                    }
                    raster.setDataElements(0, j, w, 1, row)
                }
                return raster
            }
        }
    }

    private fun colourAt(t: Float): Int {
        if (t <= stops.first()) return colors.first()
        for (k in 1 until stops.size) {
            if (t <= stops[k]) {
                val span = (stops[k] - stops[k - 1]).takeIf { it > 0f } ?: return colors[k]
                val f = (t - stops[k - 1]) / span
                val a = colors[k - 1]
                val b = colors[k]
                fun ch(shift: Int) = ((((a ushr shift) and 0xFF) * (1 - f)) + (((b ushr shift) and 0xFF) * f)).toInt().coerceIn(0, 255)
                return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        return colors.last()
    }
}

/**
 * android.text.Layout's few pieces the posters use. A layout is a block of
 * wrapped lines, each aligned in the block's width.
 */
open class Layout {
    enum class Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }
}

class TextDirectionHeuristic internal constructor(val rtl: Boolean)

object TextDirectionHeuristics {
    val LTR = TextDirectionHeuristic(false)
    val RTL = TextDirectionHeuristic(true)
    val FIRSTSTRONG_LTR = TextDirectionHeuristic(false)
}

/**
 * android.text.StaticLayout on Java2D: words wrapped greedily into [width]
 * (a word wider than the line is broken by letters, as Android does), line
 * height from the face's ascent and descent times the spacing (none added
 * after the last line, as Android's), each line aligned by [alignment] and
 * the reading direction. The paint is kept by reference, as Android's is: a
 * shader or shadow set on it after the layout is built is what is drawn.
 */
class StaticLayout private constructor(
    private val text: String,
    val paint: TextPaint,
    val width: Int,
    private val alignment: Layout.Alignment,
    private val rtl: Boolean,
    private val spacingAdd: Float,
    private val spacingMult: Float
) : Layout() {
    private val lines: List<String>
    private val ascent: Float
    private val descent: Float
    private val extra: Float

    init {
        val fm = paint.fontMetrics
        ascent = fm.ascent
        descent = fm.descent
        extra = (descent - ascent) * (spacingMult - 1f) + spacingAdd
        lines = wrap()
    }

    val lineCount: Int get() = lines.size

    /** The block's height: every line's box, the spacing between them but not after the last. */
    val height: Int get() = kotlin.math.ceil((descent - ascent) * lines.size + extra * (lines.size - 1).coerceAtLeast(0)).toInt()

    fun getLineWidth(line: Int): Float = paint.measureText(lines[line])

    private fun fits(s: String) = paint.measureText(s) <= width + 0.5f

    private fun wrap(): List<String> {
        val out = ArrayList<String>()
        for (para in text.split('\n')) {
            val words = para.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) { out += ""; continue }
            var line = ""
            for (w in words) {
                val tried = if (line.isEmpty()) w else "$line $w"
                if (fits(tried)) { line = tried; continue }
                if (line.isNotEmpty()) out += line
                if (fits(w)) { line = w; continue }
                // A word longer than the line: as many letters as fit, then the rest.
                var rest = w
                while (rest.isNotEmpty() && !fits(rest)) {
                    var n = rest.length - 1
                    while (n > 1 && !fits(rest.substring(0, n))) n--
                    out += rest.substring(0, n)
                    rest = rest.substring(n)
                }
                line = rest
            }
            out += line
        }
        return out
    }

    fun draw(c: Canvas) {
        val p = TextPaint(paint).apply { textAlign = Paint.Align.LEFT }
        var top = 0f
        for (line in lines) {
            val w = paint.measureText(line)
            val x = when (alignment) {
                Layout.Alignment.ALIGN_CENTER -> (width - w) / 2f
                Layout.Alignment.ALIGN_NORMAL -> if (rtl) width - w else 0f
                Layout.Alignment.ALIGN_OPPOSITE -> if (rtl) 0f else width - w
            }
            if (line.isNotEmpty()) c.drawText(line, x, top - ascent, p)
            top += (descent - ascent) + extra
        }
    }

    class Builder private constructor(private val text: String, private val paint: TextPaint, private val width: Int) {
        private var alignment = Layout.Alignment.ALIGN_NORMAL
        private var rtl = false
        private var add = 0f
        private var mult = 1f

        fun setAlignment(a: Layout.Alignment) = apply { alignment = a }
        fun setTextDirection(d: TextDirectionHeuristic) = apply { rtl = d.rtl }
        @Suppress("UNUSED_PARAMETER")
        fun setIncludePad(include: Boolean) = this
        fun setLineSpacing(spacingAdd: Float, spacingMult: Float) = apply { add = spacingAdd; mult = spacingMult }
        fun build() = StaticLayout(text, paint, width, alignment, rtl, add, mult)

        companion object {
            fun obtain(source: CharSequence, start: Int, end: Int, paint: TextPaint, width: Int) =
                Builder(source.subSequence(start, end).toString(), paint, width)
        }
    }
}

/** Text the Android way on Java2D: a script the chosen face lacks (Urdu in Sora) falls back to the system's, as Android's does. */
internal object Text {
    val frc = FontRenderContext(null, true, true)
    private val fallbackBase = Font(Font.DIALOG, Font.PLAIN, 1)

    fun font(p: Paint): Font {
        val tf = p.typeface ?: Typeface.DEFAULT
        var style = 0
        if (tf.style and Typeface.BOLD != 0 || p.isFakeBoldText) style = style or Font.BOLD
        if (tf.style and Typeface.ITALIC != 0) style = style or Font.ITALIC
        return tf.font.deriveFont(style, p.textSize)
    }

    private fun tracked(f: Font, p: Paint): Font =
        if (p.letterSpacing == 0f) f else f.deriveFont(mapOf(TextAttribute.TRACKING to p.letterSpacing))

    fun layout(text: String, p: Paint): TextLayout? {
        if (text.isEmpty()) return null
        val main = tracked(font(p), p)
        val fallback = tracked(fallbackBase.deriveFont(main.style, p.textSize), p)
        val s = AttributedString(text)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            // Dialog first, then Nirmala UI for Hindi and Bengali, which Dialog cannot draw on a stock Windows.
            val f = if (main.canDisplay(cp) || Character.isWhitespace(cp)) main
                else com.innovation313.roshankhata.desktop.PrintFonts.forCodePoint(fallback, cp)
            s.addAttribute(TextAttribute.FONT, f, i, i + n)
            i += n
        }
        return TextLayout(s.iterator, frc)
    }

    fun advance(text: String, p: Paint): Float = layout(text, p)?.advance ?: 0f
}

/** android.graphics.Path on a Path2D, with Android's arc angles (degrees, clockwise from three o'clock). */
class Path() {
    enum class Direction { CW, CCW }
    enum class FillType { WINDING, EVEN_ODD }
    enum class Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

    internal val p = Path2D.Float()
    var fillType: FillType = FillType.WINDING
        set(value) { field = value; p.windingRule = if (value == FillType.EVEN_ODD) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO }

    /** A copy of [src] (Android's Path(Path)). */
    constructor(src: Path) : this() {
        p.append(src.p, false)
        fillType = src.fillType
    }

    /** This path combined with [other] as areas (Android's Path.op); the result is kept here. */
    fun op(other: Path, op: Op): Boolean {
        val a = java.awt.geom.Area(p)
        val b = java.awt.geom.Area(other.p)
        when (op) {
            Op.DIFFERENCE -> a.subtract(b)
            Op.INTERSECT -> a.intersect(b)
            Op.UNION -> a.add(b)
            Op.XOR -> a.exclusiveOr(b)
            Op.REVERSE_DIFFERENCE -> { b.subtract(a); a.reset(); a.add(b) }
        }
        p.reset()
        p.append(a, false)
        return true
    }

    /** Every point of this path through [m]. */
    fun transform(m: Matrix) = p.transform(m.at)

    private fun started() = p.currentPoint != null

    fun moveTo(x: Float, y: Float) = p.moveTo(x, y)
    fun lineTo(x: Float, y: Float) { if (!started()) p.moveTo(0f, 0f); p.lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) { if (!started()) p.moveTo(0f, 0f); p.quadTo(x1, y1, x2, y2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { if (!started()) p.moveTo(0f, 0f); p.curveTo(x1, y1, x2, y2, x3, y3) }
    fun close() { if (started()) p.closePath() }
    fun reset() = p.reset()
    fun addRect(r: RectF, dir: Direction) = p.append(Rectangle2D.Float(r.left, r.top, r.width(), r.height()), false)
    fun addRoundRect(r: RectF, rx: Float, ry: Float, dir: Direction) =
        p.append(RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), false)
    fun addCircle(cx: Float, cy: Float, radius: Float, dir: Direction) =
        p.append(Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2), false)
    fun addOval(r: RectF, dir: Direction) = p.append(Ellipse2D.Float(r.left, r.top, r.width(), r.height()), false)
    fun arcTo(oval: RectF, startAngle: Float, sweepAngle: Float, forceMoveTo: Boolean = false) {
        val arc = Arc2D.Float(oval.left, oval.top, oval.width(), oval.height(), -startAngle, -sweepAngle, Arc2D.OPEN)
        p.append(arc, started() && !forceMoveTo)
    }
    fun addArc(oval: RectF, startAngle: Float, sweepAngle: Float) = arcTo(oval, startAngle, sweepAngle, true)
    fun computeBounds(bounds: RectF, exact: Boolean) {
        val b = p.bounds2D
        bounds.set(b.minX.toFloat(), b.minY.toFloat(), b.maxX.toFloat(), b.maxY.toFloat())
    }
}

/**
 * androidx.core.graphics.PathParser: SVG path data ("M6.62,10.79c1.44,2.83 …")
 * to a Path. Every command but the elliptical arc (A), which none of the
 * app's icons use; one is refused rather than drawn wrong.
 */
object PathParser {
    private val token = Regex("""[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?""")

    fun createPathFromPathData(data: String): Path {
        val t = token.findAll(data).map { it.value }.toList()
        val out = Path()
        var i = 0
        var cmd = ' '
        var x = 0f; var y = 0f
        var sx = 0f; var sy = 0f
        var cx = 0f; var cy = 0f // the last control point, for S and T
        var last = ' '
        fun num(): Float = t[i++].toFloat()
        fun more() = i < t.size && !t[i][0].isLetter()
        while (i < t.size) {
            if (t[i][0].isLetter()) { cmd = t[i][0]; i++ }
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            when (cmd.uppercaseChar()) {
                'M' -> {
                    x = ox + num(); y = oy + num(); out.moveTo(x, y); sx = x; sy = y
                    cmd = if (rel) 'l' else 'L'
                }
                'L' -> { x = ox + num(); y = oy + num(); out.lineTo(x, y) }
                'H' -> { x = (if (rel) x else 0f) + num(); out.lineTo(x, y) }
                'V' -> { y = (if (rel) y else 0f) + num(); out.lineTo(x, y) }
                'C' -> {
                    val x1 = ox + num(); val y1 = oy + num()
                    cx = ox + num(); cy = oy + num()
                    x = ox + num(); y = oy + num()
                    out.cubicTo(x1, y1, cx, cy, x, y)
                }
                'S' -> {
                    val x1 = if (last.uppercaseChar() == 'C' || last.uppercaseChar() == 'S') 2 * x - cx else x
                    val y1 = if (last.uppercaseChar() == 'C' || last.uppercaseChar() == 'S') 2 * y - cy else y
                    cx = ox + num(); cy = oy + num()
                    x = ox + num(); y = oy + num()
                    out.cubicTo(x1, y1, cx, cy, x, y)
                }
                'Q' -> {
                    cx = ox + num(); cy = oy + num()
                    x = ox + num(); y = oy + num()
                    out.quadTo(cx, cy, x, y)
                }
                'T' -> {
                    cx = if (last.uppercaseChar() == 'Q' || last.uppercaseChar() == 'T') 2 * x - cx else x
                    cy = if (last.uppercaseChar() == 'Q' || last.uppercaseChar() == 'T') 2 * y - cy else y
                    x = ox + num(); y = oy + num()
                    out.quadTo(cx, cy, x, y)
                }
                'Z' -> { out.close(); x = sx; y = sy }
                else -> throw IllegalArgumentException("path command '$cmd' is not supported")
            }
            last = cmd
            if (cmd.uppercaseChar() == 'Z' && more()) throw IllegalArgumentException("numbers after Z")
        }
        return out
    }
}

/** android.graphics.Matrix, as far as the cards need it: map a box through the canvas's transform. */
class Matrix {
    internal var at = AffineTransform()
    fun reset() { at = AffineTransform() }
    fun setScale(sx: Float, sy: Float) { at = AffineTransform.getScaleInstance(sx.toDouble(), sy.toDouble()) }
    fun setTranslate(dx: Float, dy: Float) { at = AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble()) }
    /** Then move by (dx, dy): Android's postTranslate, applied after what the matrix already does. */
    fun postTranslate(dx: Float, dy: Float): Boolean { at.preConcatenate(AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble())); return true }
    fun postScale(sx: Float, sy: Float): Boolean { at.preConcatenate(AffineTransform.getScaleInstance(sx.toDouble(), sy.toDouble())); return true }
    fun mapRect(r: RectF): Boolean {
        val b = at.createTransformedShape(Rectangle2D.Float(r.left, r.top, r.width(), r.height())).bounds2D
        r.set(b.minX.toFloat(), b.minY.toFloat(), b.maxX.toFloat(), b.maxY.toFloat())
        return at.type and (AffineTransform.TYPE_GENERAL_ROTATION or AffineTransform.TYPE_GENERAL_TRANSFORM) == 0
    }
}

class RadialGradient(
    private val cx: Float, private val cy: Float, private val radius: Float,
    private val colors: IntArray, private val positions: FloatArray?, private val tile: Shader.TileMode
) : Shader() {
    constructor(cx: Float, cy: Float, radius: Float, c0: Int, c1: Int, tile: Shader.TileMode) :
        this(cx, cy, radius, intArrayOf(c0, c1), null, tile)

    override fun awt(): java.awt.Paint? {
        if (radius <= 0f) return java.awt.Color(colors.last(), true)
        val stops = positions ?: FloatArray(colors.size) { i -> if (colors.size == 1) 0f else i / (colors.size - 1f) }
        val fixed = stops.copyOf()
        for (i in 1 until fixed.size) if (fixed[i] <= fixed[i - 1]) fixed[i] = (fixed[i - 1] + 1e-4f).coerceAtMost(1f)
        val cycle = when (tile) {
            Shader.TileMode.CLAMP -> MultipleGradientPaint.CycleMethod.NO_CYCLE
            Shader.TileMode.REPEAT -> MultipleGradientPaint.CycleMethod.REPEAT
            Shader.TileMode.MIRROR -> MultipleGradientPaint.CycleMethod.REFLECT
        }
        return RadialGradientPaint(Point2D.Float(cx, cy), radius, fixed, colors.map { java.awt.Color(it, true) }.toTypedArray(), cycle)
    }
}

/** One page's drawing, replayed onto any Graphics2D: the transform and the clip are saved and restored together. */
class Replay(val g: Graphics2D) {
    private val stack = ArrayDeque<Pair<AffineTransform, java.awt.Shape?>>()
    fun push() { stack.addLast(g.transform to g.clip) }
    fun pop() { stack.removeLastOrNull()?.let { (t, c) -> g.transform = t; g.clip = c } }
}

/**
 * Records what is drawn, for replaying. Open, as Android's is: the business
 * cards measure themselves on a Canvas that only notes where things land.
 * The current transform is kept while recording, so [getMatrix] answers.
 */
open class Canvas(val width: Int = 0, val height: Int = 0) {
    internal val ops = mutableListOf<(Replay) -> Unit>()
    private var depth = 1
    private var current = AffineTransform()
    private val saved = ArrayDeque<AffineTransform>()

    // Drawing into a picture (Canvas(bitmap)): what is recorded is played onto it when it is read.
    private var intoPicture: Replay? = null
    private var played = 0

    /** A canvas that draws into [bitmap], as Android's Canvas(Bitmap). */
    constructor(bitmap: Bitmap) : this(bitmap.width, bitmap.height) { bitmap.painters += this }

    internal fun flushInto(img: BufferedImage) {
        if (played >= ops.size) return
        val r = intoPicture ?: Replay(img.createGraphics()).also { intoPicture = it }
        while (played < ops.size) { ops[played](r); played++ }
    }

    /** Returns the count to hand to [restoreToCount], as Android does. */
    open fun save(): Int { val before = depth; depth++; saved.addLast(AffineTransform(current)); ops += { it.push() }; return before }
    open fun restore() { if (depth > 1) { depth--; saved.removeLastOrNull()?.let { current = it }; ops += { it.pop() } } }
    fun restoreToCount(count: Int) { while (depth > count.coerceAtLeast(1)) restore() }
    val saveCount: Int get() = depth

    fun getMatrix(m: Matrix) { m.at = AffineTransform(current) }

    open fun translate(dx: Float, dy: Float) { current.translate(dx.toDouble(), dy.toDouble()); ops += { it.g.translate(dx.toDouble(), dy.toDouble()) } }
    open fun scale(sx: Float, sy: Float) { current.scale(sx.toDouble(), sy.toDouble()); ops += { it.g.scale(sx.toDouble(), sy.toDouble()) } }
    open fun scale(sx: Float, sy: Float, px: Float, py: Float) {
        current.translate(px.toDouble(), py.toDouble()); current.scale(sx.toDouble(), sy.toDouble()); current.translate(-px.toDouble(), -py.toDouble())
        ops += {
            it.g.translate(px.toDouble(), py.toDouble()); it.g.scale(sx.toDouble(), sy.toDouble()); it.g.translate(-px.toDouble(), -py.toDouble())
        }
    }
    open fun rotate(degrees: Float) { current.rotate(Math.toRadians(degrees.toDouble())); ops += { it.g.rotate(Math.toRadians(degrees.toDouble())) } }
    open fun rotate(degrees: Float, px: Float, py: Float) {
        current.rotate(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble())
        ops += { it.g.rotate(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble()) }
    }

    open fun clipRect(left: Float, top: Float, right: Float, bottom: Float): Boolean {
        ops += { it.g.clip(Rectangle2D.Float(left, top, right - left, bottom - top)) }; return true
    }
    fun clipRect(r: RectF): Boolean = clipRect(r.left, r.top, r.right, r.bottom)
    fun clipRect(r: Rect): Boolean = clipRect(RectF(r))
    fun clipRect(left: Int, top: Int, right: Int, bottom: Int): Boolean = clipRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    open fun clipPath(path: Path): Boolean { val shape = Path2D.Float(path.p); ops += { it.g.clip(shape) }; return true }

    /** Everything but [r] stays drawable (Android's clipOutRect). */
    open fun clipOutRect(r: RectF): Boolean {
        val hole = Rectangle2D.Float(r.left, r.top, r.width(), r.height())
        ops += { rp ->
            val area = java.awt.geom.Area(rp.g.clip ?: Rectangle2D.Float(-100_000f, -100_000f, 200_000f, 200_000f))
            area.subtract(java.awt.geom.Area(hole))
            rp.g.clip = area
        }
        return true
    }

    /**
     * A picture bent over a grid of [meshWidth] x [meshHeight] cells (Android's
     * drawBitmapMesh: the posters' waving flag). Each cell is two triangles,
     * each a straight piece of the picture; a vertex colour darkens its
     * corner of the picture as Android's multiply does for greys.
     */
    open fun drawBitmapMesh(
        bitmap: Bitmap, meshWidth: Int, meshHeight: Int, verts: FloatArray, vertOffset: Int,
        colors: IntArray?, colorOffset: Int, paint: Paint?
    ) {
        val v = verts.copyOf()
        val cols = colors?.copyOf()
        ops += { r ->
            val g = r.g
            val img = bitmap.image
            val clip = g.clip
            val comp = g.composite
            g.composite = AlphaComposite.SrcOver
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            fun at(i: Int, j: Int) = j * (meshWidth + 1) + i
            fun tri(a: Int, b: Int, c: Int, ai: Int, aj: Int, bi: Int, bj: Int, ci: Int, cj: Int) {
                val sx0 = ai * img.width.toDouble() / meshWidth; val sy0 = aj * img.height.toDouble() / meshHeight
                val sx1 = bi * img.width.toDouble() / meshWidth; val sy1 = bj * img.height.toDouble() / meshHeight
                val sx2 = ci * img.width.toDouble() / meshWidth; val sy2 = cj * img.height.toDouble() / meshHeight
                val dx0 = v[vertOffset + a * 2].toDouble(); val dy0 = v[vertOffset + a * 2 + 1].toDouble()
                val dx1 = v[vertOffset + b * 2].toDouble(); val dy1 = v[vertOffset + b * 2 + 1].toDouble()
                val dx2 = v[vertOffset + c * 2].toDouble(); val dy2 = v[vertOffset + c * 2 + 1].toDouble()
                val s00 = sx1 - sx0; val s01 = sx2 - sx0; val s10 = sy1 - sy0; val s11 = sy2 - sy0
                val det = s00 * s11 - s01 * s10
                if (kotlin.math.abs(det) < 1e-9) return
                val i00 = s11 / det; val i01 = -s01 / det; val i10 = -s10 / det; val i11 = s00 / det
                val d00 = dx1 - dx0; val d01 = dx2 - dx0; val d10 = dy1 - dy0; val d11 = dy2 - dy0
                val m00 = d00 * i00 + d01 * i10; val m01 = d00 * i01 + d01 * i11
                val m10 = d10 * i00 + d11 * i10; val m11 = d10 * i01 + d11 * i11
                val tx = dx0 - (m00 * sx0 + m01 * sy0); val ty = dy0 - (m10 * sx0 + m11 * sy0)
                val shape = Path2D.Double().apply { moveTo(dx0, dy0); lineTo(dx1, dy1); lineTo(dx2, dy2); closePath() }
                g.clip = clip
                g.clip(shape)
                g.drawImage(img, AffineTransform(m00, m10, m01, m11, tx, ty), null)
                if (cols != null) {
                    fun grey(k: Int) = (cols[colorOffset + k] shr 16) and 0xFF
                    val dark = 255 - (grey(a) + grey(b) + grey(c)) / 3
                    if (dark > 0) { g.color = java.awt.Color(0, 0, 0, dark.coerceAtMost(255)); g.fill(shape) }
                }
            }
            for (j in 0 until meshHeight) for (i in 0 until meshWidth) {
                tri(at(i, j), at(i + 1, j), at(i, j + 1), i, j, i + 1, j, i, j + 1)
                tri(at(i + 1, j), at(i + 1, j + 1), at(i, j + 1), i + 1, j, i + 1, j + 1, i, j + 1)
            }
            g.clip = clip
            g.composite = comp
        }
    }

    open fun drawColor(color: Int) {
        ops += { r ->
            val g = r.g
            val old = g.transform
            g.transform = AffineTransform()
            g.color = java.awt.Color(color, true)
            g.fill(g.clip ?: Rectangle2D.Float(-100_000f, -100_000f, 200_000f, 200_000f))
            g.transform = old
        }
    }

    open fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
        shape(Rectangle2D.Float(minOf(left, right), minOf(top, bottom), kotlin.math.abs(right - left), kotlin.math.abs(bottom - top)), paint)

    open fun drawRect(rect: RectF, paint: Paint) = drawRect(rect.left, rect.top, rect.right, rect.bottom, paint)
    fun drawRect(r: Rect, paint: Paint) = drawRect(RectF(r), paint)

    open fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) =
        shape(RoundRectangle2D.Float(rect.left, rect.top, rect.width(), rect.height(), rx * 2, ry * 2), paint)

    open fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) =
        drawRoundRect(RectF(left, top, right, bottom), rx, ry, paint)

    open fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) =
        shape(Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2), paint)

    open fun drawOval(oval: RectF, paint: Paint) = shape(Ellipse2D.Float(oval.left, oval.top, oval.width(), oval.height()), paint)
    open fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = drawOval(RectF(left, top, right, bottom), paint)

    open fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) =
        shape(Arc2D.Float(oval.left, oval.top, oval.width(), oval.height(), -startAngle, -sweepAngle, if (useCenter) Arc2D.PIE else Arc2D.OPEN), paint)

    open fun drawArc(left: Float, top: Float, right: Float, bottom: Float, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) =
        drawArc(RectF(left, top, right, bottom), startAngle, sweepAngle, useCenter, paint)

    open fun drawPath(path: Path, paint: Paint) = shape(Path2D.Float(path.p).apply { windingRule = path.p.windingRule }, paint)

    open fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        val p = Paint(paint)
        ops += { r -> setup(r.g, p); r.g.stroke = stroke(p); r.g.draw(Line2D.Float(startX, startY, stopX, stopY)) }
    }

    open fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        val p = Paint(paint)
        ops += { r ->
            Text.layout(text, p)?.let { layout ->
                val w = layout.advance
                val left = when (p.textAlign) {
                    Paint.Align.LEFT -> x
                    Paint.Align.CENTER -> x - w / 2f
                    Paint.Align.RIGHT -> x - w
                }
                val blur = (p.maskFilter as? BlurMaskFilter)?.radius ?: 0f
                if (!hasShadow(p) && blur <= 0f) {
                    glyphs(r.g, layout, left, y, p)
                } else {
                    val box = layout.getOutline(AffineTransform.getTranslateInstance(left.toDouble(), y.toDouble())).bounds2D
                    val pad = if (p.style == Paint.Style.FILL) 0f else p.strokeWidth
                    if (hasShadow(p)) soft(r.g, box, pad, p.shadowRadius, p.shadowDx, p.shadowDy) { glyphs(it, layout, left, y, shadowOf(p)) }
                    if (blur > 0f) soft(r.g, box, pad, blur, 0f, 0f) { glyphs(it, layout, left, y, p) }
                    else glyphs(r.g, layout, left, y, p)
                }
            }
        }
    }

    private fun glyphs(g: Graphics2D, layout: TextLayout, left: Float, y: Float, p: Paint) {
        setup(g, p)
        if (p.style == Paint.Style.STROKE) {
            g.stroke = stroke(p)
            g.draw(layout.getOutline(AffineTransform.getTranslateInstance(left.toDouble(), y.toDouble())))
        } else {
            layout.draw(g, left, y)
        }
    }

    open fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) = drawText(text.substring(start, end), x, y, paint)
    open fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) = drawText(text.subSequence(start, end).toString(), x, y, paint)
    open fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) = drawText(String(text, index, count), x, y, paint)

    open fun drawBitmap(bmp: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
        val p = paint?.let { Paint(it) }
        ops += { r ->
            val g = r.g
            val old = g.composite
            p?.let { if (it.alpha < 255) g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, it.alpha / 255f) }
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val s = src ?: Rect(0, 0, bmp.width, bmp.height)
            val sub = if (s.left == 0 && s.top == 0 && s.right == bmp.width && s.bottom == bmp.height) bmp.image
                else bmp.image.getSubimage(s.left, s.top, s.width().coerceAtLeast(1), s.height().coerceAtLeast(1))
            // Into a float box by transform, so a fractional box keeps its size exactly.
            val at = AffineTransform()
            at.translate(dst.left.toDouble(), dst.top.toDouble())
            at.scale(dst.width().toDouble() / sub.width, dst.height().toDouble() / sub.height)
            g.drawImage(sub, at, null)
            g.composite = old
        }
    }

    fun drawBitmap(bmp: Bitmap, src: Rect?, dst: Rect, paint: Paint?) = drawBitmap(bmp, src, RectF(dst), paint)

    fun drawBitmap(bmp: Bitmap, left: Float, top: Float, paint: Paint?) =
        drawBitmap(bmp, null, RectF(left, top, left + bmp.width, top + bmp.height), paint)

    private fun shape(s: java.awt.Shape, paint: Paint) {
        val p = Paint(paint)
        ops += { r ->
            val blur = (p.maskFilter as? BlurMaskFilter)?.radius ?: 0f
            if (!hasShadow(p) && blur <= 0f) {
                paintShape(r.g, s, p)
            } else {
                val pad = if (p.style == Paint.Style.FILL) 0f else p.strokeWidth
                if (hasShadow(p)) soft(r.g, s.bounds2D, pad, p.shadowRadius, p.shadowDx, p.shadowDy) { paintShape(it, s, shadowOf(p)) }
                if (blur > 0f) soft(r.g, s.bounds2D, pad, blur, 0f, 0f) { paintShape(it, s, p) }
                else paintShape(r.g, s, p)
            }
        }
    }

    private fun paintShape(g: Graphics2D, s: java.awt.Shape, p: Paint) {
        setup(g, p)
        if (p.style == Paint.Style.FILL || p.style == Paint.Style.FILL_AND_STROKE) g.fill(s)
        if (p.style == Paint.Style.STROKE || p.style == Paint.Style.FILL_AND_STROKE) { g.stroke = stroke(p); g.draw(s) }
    }

    private fun hasShadow(p: Paint) = p.shadowRadius > 0f && Color.alpha(p.shadowColor) > 0

    /** The paint a shadow is drawn with: the shadow's colour, faded with the paint's own alpha, no shader, no blur. */
    private fun shadowOf(p: Paint) = Paint(p).apply {
        shader = null
        maskFilter = null
        clearShadowLayer()
        val a = Color.alpha(p.shadowColor) * (if (p.shader == null) p.alpha else 255) / 255
        color = (p.shadowColor and 0x00FFFFFF) or (a shl 24)
    }

    /**
     * Draws [draw] soft: into a picture off to the side, in device pixels,
     * blurred (a Gaussian of Android's sigma for [radius], by three box
     * passes), and laid on at ([dx], [dy]). [box] is what is drawn, in this
     * canvas's units; [pad] its stroke. Kept to the clip, so a glow over a
     * whole poster does not make a picture larger than the poster.
     */
    private fun soft(g: Graphics2D, box: Rectangle2D, pad: Float, radius: Float, dx: Float, dy: Float, draw: (Graphics2D) -> Unit) {
        val t = g.transform
        val k = kotlin.math.sqrt(kotlin.math.abs(t.determinant)).toFloat().coerceAtLeast(1e-3f)
        val sigma = (0.57735f * radius + 0.5f) * k
        val margin = sigma * 3f + pad * k + 2f
        val shifted = Rectangle2D.Double(box.x + dx, box.y + dy, box.width, box.height)
        val dev = t.createTransformedShape(shifted).bounds2D
        var x0 = kotlin.math.floor(dev.minX - margin).toInt()
        var y0 = kotlin.math.floor(dev.minY - margin).toInt()
        var x1 = kotlin.math.ceil(dev.maxX + margin).toInt()
        var y1 = kotlin.math.ceil(dev.maxY + margin).toInt()
        g.clip?.let { c ->
            val cb = t.createTransformedShape(c).bounds2D
            x0 = maxOf(x0, kotlin.math.floor(cb.minX - margin).toInt()); y0 = maxOf(y0, kotlin.math.floor(cb.minY - margin).toInt())
            x1 = minOf(x1, kotlin.math.ceil(cb.maxX + margin).toInt()); y1 = minOf(y1, kotlin.math.ceil(cb.maxY + margin).toInt())
        }
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0 || h <= 0 || w.toLong() * h > 40_000_000L) return
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
        val og = img.createGraphics()
        og.transform = AffineTransform.getTranslateInstance(-x0.toDouble(), -y0.toDouble()).apply {
            concatenate(t)
            translate(dx.toDouble(), dy.toDouble())
        }
        draw(og)
        og.dispose()
        Blur.gaussian(img, sigma)
        val comp = g.composite
        g.transform = AffineTransform()
        g.composite = AlphaComposite.SrcOver
        g.drawImage(img, x0, y0, null)
        g.transform = t
        g.composite = comp
    }

    private fun setup(g: Graphics2D, p: Paint) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, if (p.isAntiAlias) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.paint = p.shader?.awt() ?: java.awt.Color(p.color, true)
        // A shader keeps the paint's alpha, as on Android.
        if (p.shader != null && p.alpha < 255) g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha / 255f)
        else g.composite = AlphaComposite.SrcOver
    }

    private fun stroke(p: Paint): BasicStroke {
        val cap = when (p.strokeCap) { Paint.Cap.BUTT -> BasicStroke.CAP_BUTT; Paint.Cap.ROUND -> BasicStroke.CAP_ROUND; Paint.Cap.SQUARE -> BasicStroke.CAP_SQUARE }
        val join = when (p.strokeJoin) { Paint.Join.MITER -> BasicStroke.JOIN_MITER; Paint.Join.ROUND -> BasicStroke.JOIN_ROUND; Paint.Join.BEVEL -> BasicStroke.JOIN_BEVEL }
        val dash = (p.pathEffect as? DashPathEffect)?.intervals?.takeIf { it.isNotEmpty() && it.any { v -> v > 0f } }
        return if (dash != null) BasicStroke(p.strokeWidth, cap, join, 10f, dash, (p.pathEffect as DashPathEffect).phase)
        else BasicStroke(p.strokeWidth, cap, join, 4f)
    }

    /** Plays this page onto [g], in the page's own units (points). */
    fun replay(g: Graphics2D) {
        val r = Replay(g)
        val start = g.transform
        val clip = g.clip
        ops.forEach { it(r) }
        g.transform = start
        g.clip = clip
        g.composite = AlphaComposite.SrcOver
    }

    /** This canvas drawn into a new picture of [w] x [h] pixels on [background]. */
    fun picture(w: Int, h: Int, background: Int = Color.WHITE): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(background, true)
        g.fillRect(0, 0, w, h)
        replay(g)
        g.dispose()
        return img
    }
}

/** android.graphics.pdf.PdfDocument's shape: pages started, drawn and finished in order. */
class PdfDocument {
    class PageInfo(val pageWidth: Int, val pageHeight: Int, val pageNumber: Int) {
        class Builder(private val w: Int, private val h: Int, private val n: Int) {
            fun create() = PageInfo(w, h, n)
        }
    }

    class Page(val info: PageInfo, val canvas: Canvas)

    /** The finished pages' infos, as Android's [pages] list. */
    val pages: List<PageInfo> get() = finished.map { it.info }
    val finished = mutableListOf<Page>()

    fun startPage(info: PageInfo) = Page(info, Canvas(info.pageWidth, info.pageHeight))
    fun finishPage(page: Page) { if (finished.none { it === page }) finished += page }
    fun close() {}

    /** A finished page as a white-backed picture, [scale] pixels per point — for a preview or a test. */
    fun picture(index: Int, scale: Float = 2f): BufferedImage {
        val page = finished[index]
        val img = BufferedImage((page.info.pageWidth * scale).toInt(), (page.info.pageHeight * scale).toInt(), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, img.width, img.height)
        g.scale(scale.toDouble(), scale.toDouble())
        page.canvas.replay(g)
        g.dispose()
        return img
    }
}

/** A Gaussian blur of a premultiplied picture, in place: three box passes each way, the standard close match. */
internal object Blur {
    fun gaussian(img: BufferedImage, sigma: Float) {
        if (sigma < 0.6f) return
        val w = img.width
        val h = img.height
        val px = img.getRGB(0, 0, w, h, null, 0, w)
        val ch = Array(4) { IntArray(w * h) }
        // getRGB gives non-premultiplied ARGB; blur premultiplied so edges do not darken.
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            ch[0][i] = a
            ch[1][i] = ((c shr 16) and 0xFF) * a / 255
            ch[2][i] = ((c shr 8) and 0xFF) * a / 255
            ch[3][i] = (c and 0xFF) * a / 255
        }
        val tmp = IntArray(w * h)
        for (size in boxes(sigma)) {
            val r = (size - 1) / 2
            for (c in ch) {
                horizontal(c, tmp, w, h, r)
                vertical(tmp, c, w, h, r)
            }
        }
        for (i in px.indices) {
            val a = ch[0][i].coerceIn(0, 255)
            if (a == 0) { px[i] = 0; continue }
            val rr = (ch[1][i] * 255 / a).coerceIn(0, 255)
            val gg = (ch[2][i] * 255 / a).coerceIn(0, 255)
            val bb = (ch[3][i] * 255 / a).coerceIn(0, 255)
            px[i] = (a shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        img.setRGB(0, 0, w, h, px, 0, w)
    }

    private fun boxes(sigma: Float): IntArray {
        val n = 3
        val ideal = kotlin.math.sqrt(12.0 * sigma * sigma / n + 1)
        var wl = kotlin.math.floor(ideal).toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val m = Math.round((12.0 * sigma * sigma - n * wl * wl - 4.0 * n * wl - 3.0 * n) / (-4.0 * wl - 4.0)).toInt()
        return IntArray(n) { if (it < m) wl else wu }
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (i in -r..r) if (i in 0 until w) sum += src[row + i]
            for (x in 0 until w) {
                dst[row + x] = sum / div
                val out = x - r
                val inn = x + r + 1
                if (out >= 0) sum -= src[row + out]
                if (inn < w) sum += src[row + inn]
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (i in -r..r) if (i in 0 until h) sum += src[i * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = sum / div
                val out = y - r
                val inn = y + r + 1
                if (out >= 0) sum -= src[out * w + x]
                if (inn < h) sum += src[inn * w + x]
            }
        }
    }
}
