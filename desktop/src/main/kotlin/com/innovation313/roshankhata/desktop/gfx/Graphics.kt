package com.innovation313.roshankhata.desktop.gfx

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.MultipleGradientPaint
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
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
    private val colors: IntArray, private val positions: FloatArray?, private val tile: TileMode
) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, tile: TileMode) :
        this(x0, y0, x1, y1, intArrayOf(c0, c1), null, tile)

    override fun awt(): java.awt.Paint? {
        if (x0 == x1 && y0 == y1) return java.awt.Color(colors.first(), true)
        val stops = positions ?: FloatArray(colors.size) { i -> if (colors.size == 1) 0f else i / (colors.size - 1f) }
        // Java wants strictly rising stops; nudge equal neighbours apart.
        val fixed = stops.copyOf()
        for (i in 1 until fixed.size) if (fixed[i] <= fixed[i - 1]) fixed[i] = (fixed[i - 1] + 1e-4f).coerceAtMost(1f)
        val cycle = when (tile) {
            TileMode.CLAMP -> MultipleGradientPaint.CycleMethod.NO_CYCLE
            TileMode.REPEAT -> MultipleGradientPaint.CycleMethod.REPEAT
            TileMode.MIRROR -> MultipleGradientPaint.CycleMethod.REFLECT
        }
        return LinearGradientPaint(Point2D.Float(x0, y0), Point2D.Float(x1, y1), fixed, colors.map { java.awt.Color(it, true) }.toTypedArray(), cycle)
    }
}

open class PathEffect
class DashPathEffect(val intervals: FloatArray, val phase: Float) : PathEffect()

class Bitmap(val image: BufferedImage) {
    val width: Int get() = image.width
    val height: Int get() = image.height
}

class Paint(flags: Int = 0) {
    enum class Align { LEFT, CENTER, RIGHT }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }

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
    var isFilterBitmap: Boolean = false
    var strokeCap: Cap = Cap.BUTT

    constructor(other: Paint) : this() {
        isAntiAlias = other.isAntiAlias; color = other.color; textSize = other.textSize; textAlign = other.textAlign
        typeface = other.typeface; isFakeBoldText = other.isFakeBoldText; shader = other.shader
        strokeWidth = other.strokeWidth; style = other.style; pathEffect = other.pathEffect
        letterSpacing = other.letterSpacing; isFilterBitmap = other.isFilterBitmap; strokeCap = other.strokeCap
    }

    fun measureText(text: String): Float = Text.advance(text, this)

    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
    }
}

/** Text the Android way on Java2D: a script the chosen face lacks (Urdu in Sora) falls back to the system's, as Android's does. */
internal object Text {
    private val frc = FontRenderContext(null, true, true)
    private val fallbackBase = Font(Font.DIALOG, Font.PLAIN, 1)

    fun font(p: Paint): Font {
        val tf = p.typeface ?: Typeface.DEFAULT
        var style = 0
        if (tf.style and Typeface.BOLD != 0 || p.isFakeBoldText) style = style or Font.BOLD
        if (tf.style and Typeface.ITALIC != 0) style = style or Font.ITALIC
        return tf.font.deriveFont(style, p.textSize)
    }

    fun layout(text: String, p: Paint): TextLayout? {
        if (text.isEmpty()) return null
        val main = font(p)
        val fallback = fallbackBase.deriveFont(main.style, p.textSize)
        val s = AttributedString(text)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            val f = if (main.canDisplay(cp) || Character.isWhitespace(cp)) main else fallback
            s.addAttribute(TextAttribute.FONT, f, i, i + n)
            i += n
        }
        if (p.letterSpacing != 0f) s.addAttribute(TextAttribute.TRACKING, p.letterSpacing)
        return TextLayout(s.iterator, frc)
    }

    fun advance(text: String, p: Paint): Float = layout(text, p)?.advance ?: 0f
}

/** One page's drawing, replayed onto any Graphics2D. */
class Replay(val g: Graphics2D) {
    private val stack = ArrayDeque<AffineTransform>()
    fun push() { stack.addLast(g.transform) }
    fun pop() { stack.removeLastOrNull()?.let { g.transform = it } }
}

class Canvas(val width: Int, val height: Int) {
    internal val ops = mutableListOf<(Replay) -> Unit>()
    private var depth = 1

    /** Returns the count to hand to [restoreToCount], as Android does. */
    fun save(): Int { val before = depth; depth++; ops += { it.push() }; return before }
    fun restore() { if (depth > 1) { depth--; ops += { it.pop() } } }
    fun restoreToCount(count: Int) { while (depth > count.coerceAtLeast(1)) restore() }

    fun translate(dx: Float, dy: Float) { ops += { it.g.translate(dx.toDouble(), dy.toDouble()) } }
    fun scale(sx: Float, sy: Float) { ops += { it.g.scale(sx.toDouble(), sy.toDouble()) } }
    fun scale(sx: Float, sy: Float, px: Float, py: Float) {
        ops += {
            it.g.translate(px.toDouble(), py.toDouble()); it.g.scale(sx.toDouble(), sy.toDouble()); it.g.translate(-px.toDouble(), -py.toDouble())
        }
    }
    fun rotate(degrees: Float) { ops += { it.g.rotate(Math.toRadians(degrees.toDouble())) } }
    fun rotate(degrees: Float, px: Float, py: Float) { ops += { it.g.rotate(Math.toRadians(degrees.toDouble()), px.toDouble(), py.toDouble()) } }

    fun drawColor(color: Int) {
        ops += { r -> r.g.color = java.awt.Color(color, true); r.g.fill(Rectangle2D.Float(-10_000f, -10_000f, 20_000f, 20_000f)) }
    }

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
        shape(Rectangle2D.Float(left, top, right - left, bottom - top), paint)

    fun drawRect(r: RectF, paint: Paint) = drawRect(r.left, r.top, r.right, r.bottom, paint)
    fun drawRect(r: Rect, paint: Paint) = drawRect(RectF(r), paint)

    fun drawRoundRect(r: RectF, rx: Float, ry: Float, paint: Paint) =
        shape(RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), rx * 2, ry * 2), paint)

    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) =
        drawRoundRect(RectF(left, top, right, bottom), rx, ry, paint)

    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) =
        shape(Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2), paint)

    fun drawOval(r: RectF, paint: Paint) = shape(Ellipse2D.Float(r.left, r.top, r.width(), r.height()), paint)

    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        val p = Paint(paint)
        ops += { r -> setup(r.g, p); r.g.stroke = stroke(p); r.g.draw(Line2D.Float(x1, y1, x2, y2)) }
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
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

    fun drawBitmap(bmp: Bitmap, src: Rect?, dst: RectF, paint: Paint?) {
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
    }

    private fun stroke(p: Paint): BasicStroke {
        val cap = when (p.strokeCap) { Paint.Cap.BUTT -> BasicStroke.CAP_BUTT; Paint.Cap.ROUND -> BasicStroke.CAP_ROUND; Paint.Cap.SQUARE -> BasicStroke.CAP_SQUARE }
        val dash = (p.pathEffect as? DashPathEffect)?.intervals?.takeIf { it.isNotEmpty() && it.any { v -> v > 0f } }
        return if (dash != null) BasicStroke(p.strokeWidth, cap, BasicStroke.JOIN_MITER, 10f, dash, (p.pathEffect as DashPathEffect).phase)
        else BasicStroke(p.strokeWidth, cap, BasicStroke.JOIN_MITER)
    }

    /** Plays this page onto [g], in the page's own units (points). */
    fun replay(g: Graphics2D) {
        val r = Replay(g)
        val start = g.transform
        ops.forEach { it(r) }
        g.transform = start
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
