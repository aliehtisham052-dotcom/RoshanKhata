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

class Bitmap(val image: BufferedImage) {
    val width: Int get() = image.width
    val height: Int get() = image.height
    val isRecycled: Boolean get() = false
}

class Paint(flags: Int = 0) {
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
    var isFilterBitmap: Boolean = flags and FILTER_BITMAP_FLAG != 0
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER

    constructor(other: Paint) : this() {
        isAntiAlias = other.isAntiAlias; color = other.color; textSize = other.textSize; textAlign = other.textAlign
        typeface = other.typeface; isFakeBoldText = other.isFakeBoldText; shader = other.shader
        strokeWidth = other.strokeWidth; style = other.style; pathEffect = other.pathEffect
        letterSpacing = other.letterSpacing; isFilterBitmap = other.isFilterBitmap; strokeCap = other.strokeCap
        strokeJoin = other.strokeJoin
    }

    fun measureText(text: String): Float = Text.advance(text, this)
    fun measureText(text: CharSequence, start: Int, end: Int): Float = Text.advance(text.subSequence(start, end).toString(), this)

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
            val f = if (main.canDisplay(cp) || Character.isWhitespace(cp)) main else fallback
            s.addAttribute(TextAttribute.FONT, f, i, i + n)
            i += n
        }
        return TextLayout(s.iterator, frc)
    }

    fun advance(text: String, p: Paint): Float = layout(text, p)?.advance ?: 0f
}

/** android.graphics.Path on a Path2D, with Android's arc angles (degrees, clockwise from three o'clock). */
class Path {
    enum class Direction { CW, CCW }
    enum class FillType { WINDING, EVEN_ODD }

    internal val p = Path2D.Float()
    var fillType: FillType = FillType.WINDING
        set(value) { field = value; p.windingRule = if (value == FillType.EVEN_ODD) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO }

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

/** android.graphics.Matrix, as far as the cards need it: map a box through the canvas's transform. */
class Matrix {
    internal var at = AffineTransform()
    fun reset() { at = AffineTransform() }
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
                setup(r.g, p)
                if (p.style == Paint.Style.STROKE) {
                    r.g.stroke = stroke(p)
                    r.g.draw(layout.getOutline(AffineTransform.getTranslateInstance(left.toDouble(), y.toDouble())))
                } else {
                    layout.draw(r.g, left, y)
                }
            }
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
            setup(r.g, p)
            if (p.style == Paint.Style.FILL || p.style == Paint.Style.FILL_AND_STROKE) r.g.fill(s)
            if (p.style == Paint.Style.STROKE || p.style == Paint.Style.FILL_AND_STROKE) { r.g.stroke = stroke(p); r.g.draw(s) }
        }
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
