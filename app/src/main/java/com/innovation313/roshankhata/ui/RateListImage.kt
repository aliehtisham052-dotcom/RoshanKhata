package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Digits
import com.innovation313.roshankhata.data.PdfRtl
import com.innovation313.roshankhata.data.RateList
import com.innovation313.roshankhata.data.UnitWords
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the rate list as pictures 1080 pixels wide — the width WhatsApp keeps
 * sharp — one picture per page, each page standing on its own when forwarded:
 * shop band, heading, column titles, rows, and "page 2 / 3" at the foot.
 *
 * WHY NOT THE PDF PAGE-FLIP. Every PDF in this app mirrors for Urdu by flipping
 * the whole page and flipping each text run back (PdfRtl). That works for
 * single lines. A product name here WRAPS — Bengali and Arabic names run long
 * — and a wrapped paragraph is a StaticLayout, which cannot be flipped back
 * piece by piece. So this page is laid out the honest way instead: every
 * column has a start edge measured from the reading side, mirrored once into
 * a real x when the language reads right to left, and every text block is
 * laid out with that direction. Nothing is drawn back to front, so nothing
 * has to be turned round again.
 *
 * NOTHING IS CUT. No row is clipped or ellipsised: a long name wraps and its
 * row grows. Only the shop's own name in the band stops at two lines.
 *
 * The colours are fixed, not the theme's: the picture goes to a customer's
 * phone, and it must look the same whether the owner keeps the app dark or
 * light.
 */
object RateListImage {

    const val WIDTH = 1080
    /** A page stops before this height (9:16) and the rest goes on the next. */
    const val MAX_HEIGHT = 1920

    private const val M = 56f
    private const val GAP = 24f
    private const val ROW_PAD = 22f
    private const val BAND_PAD = 40f
    private const val LOGO = 150f
    private const val ACCENT = 8f
    private const val FOOTER = 84f

    private const val C_BG = 0xFFFFFFFF.toInt()
    private const val C_BAND = 0xFF1B5E3A.toInt()
    private const val C_BAND_SOFT = 0xFFE7F2EA.toInt()
    private const val C_GOLD = 0xFFD4A02A.toInt()
    private const val C_INK = 0xFF1A1A18.toInt()
    private const val C_MUTED = 0xFF5F5E5A.toInt()
    private const val C_LINE = 0xFFE5E3DC.toInt()
    private const val C_ZEBRA = 0xFFF7F6F2.toInt()
    private const val C_HEAD_BG = 0xFFEEF4EF.toInt()
    private const val C_HEAD_INK = 0xFF134228.toInt()

    data class Input(
        val shopName: String,
        /** Phone · address, already joined; null or blank for none. */
        val contact: String?,
        val logo: Bitmap?,
        val heading: String,
        /** Date, and the company when the list is one company's. */
        val subLine: String,
        val rows: List<RateList.Row>,
        val columns: RateList.Columns,
        /** False when the whole list is one company: the heading already says it. */
        val companyPerRow: Boolean,
        /** "Roshan Khata" in the app's language, or null when the owner switched it off. */
        val mark: String?
    )

    /** One column: its edge on the page and how its text lines up. */
    internal class Col(val x: Float, val width: Int, val align: Layout.Alignment)

    internal class RowLayout(
        val name: StaticLayout,
        val company: StaticLayout?,
        val unit: StaticLayout?,
        val cash: StaticLayout?,
        val credit: StaticLayout?,
        val height: Float
    )

    /**
     * Everything measured, nothing drawn yet. Building a plan for three
     * hundred products takes milliseconds, so the screen can say how many
     * pictures it will send before it draws a single one.
     */
    class Plan internal constructor(
        val rtl: Boolean,
        internal val input: Input,
        internal val locale: Locale,
        internal val bandH: Float,
        internal val shop: StaticLayout,
        internal val contact: StaticLayout?,
        internal val textStart: Float,
        internal val textWidth: Int,
        internal val heading: StaticLayout,
        internal val sub: StaticLayout,
        internal val headingH: Float,
        internal val headCells: List<StaticLayout?>,
        internal val headRowH: Float,
        internal val cols: List<Col?>,
        internal val rows: List<RowLayout>,
        val pages: List<IntRange>,
        internal val topH: Float,
        internal val pageWord: (Int, Int) -> String
    ) {
        /** Where the product-name column sits — right side in Urdu, Sindhi, Persian, Arabic. */
        val nameLeft: Float get() = cols[0]!!.x
        val nameWidth: Int get() = cols[0]!!.width
    }

    private fun paint(size: Float, color: Int, bold: Boolean, locale: Locale) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textLocale = locale
        }

    /**
     * A block of text [width] wide, laid out in the page's reading direction.
     * includePad keeps the tall marks of Nastaliq, Devanagari and Bengali
     * inside the block instead of clipped at its edge.
     */
    private fun block(
        text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment,
        rtl: Boolean, maxLines: Int = Int.MAX_VALUE
    ): StaticLayout {
        val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
            .setAlignment(align)
            .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setIncludePad(true)
            .setLineSpacing(0f, 1.08f)
        if (maxLines != Int.MAX_VALUE) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
        return b.build()
    }

    private fun widest(texts: List<String>, paint: TextPaint): Float =
        texts.maxOfOrNull { paint.measureText(it) } ?: 0f

    /** The page's real x for a box [start] in from the reading side and [width] wide. */
    private fun vx(rtl: Boolean, start: Float, width: Float): Float =
        if (rtl) WIDTH - M - start - width else M + start

    fun plan(context: Context, input: Input): Plan {
        val rtl = PdfRtl.isRtl(context)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val start = Layout.Alignment.ALIGN_NORMAL
        val end = Layout.Alignment.ALIGN_OPPOSITE
        val content = WIDTH - 2 * M

        // ---- Band: logo at the reading side, shop name and contact beside it.
        val hasLogo = input.logo != null
        val textStart = if (hasLogo) LOGO + GAP else 0f
        val textWidth = (content - textStart).toInt()
        val shopName = input.shopName.ifBlank { context.getString(R.string.rate_list_title) }
        val shop = block(shopName, paint(54f, 0xFFFFFFFF.toInt(), true, locale), textWidth, start, rtl, maxLines = 2)
        val contact = input.contact?.takeIf { it.isNotBlank() }?.let {
            block(it, paint(28f, C_BAND_SOFT, false, locale), textWidth, start, rtl, maxLines = 2)
        }
        val textH = shop.height + (contact?.let { 8f + it.height } ?: 0f)
        val bandH = 2 * BAND_PAD + max(textH, if (hasLogo) LOGO else 0f)

        // ---- Heading block under the gold line.
        val heading = block(input.heading, paint(44f, C_INK, true, locale), content.toInt(), start, rtl)
        val sub = block(input.subLine, paint(30f, C_MUTED, false, locale), content.toInt(), start, rtl)
        val headingH = 32f + heading.height + 8f + sub.height + 24f

        // ---- Columns, measured from the reading side: name, unit, cash, credit.
        val headPaint = paint(28f, C_HEAD_INK, true, locale)
        val namePaint = paint(36f, C_INK, false, locale)
        val companyPaint = paint(26f, C_MUTED, false, locale)
        val unitPaint = paint(30f, C_MUTED, false, locale)
        val pricePaint = paint(34f, C_INK, true, locale)

        val rows = input.rows
        val unitTexts = rows.map { UnitWords.label(it.unit, locale) }
        val showUnit = unitTexts.any { it.isNotEmpty() }
        // Each figure wrapped left-to-right, so "₹ 4,200" cannot come out as
        // "4,200 ₹" on a right-to-left page (see Format.ltr).
        val cashTexts = rows.map { r -> r.cash?.let { Format.ltr(Format.money(it)) } ?: "\u2014" }
        val creditTexts = rows.map { r -> r.credit?.let { Format.ltr(Format.money(it)) } ?: "\u2014" }

        val hProduct = context.getString(R.string.rate_list_col_product)
        val hUnit = context.getString(R.string.rate_list_col_unit)
        val hCash = context.getString(R.string.sale_price_hint)
        val hCredit = context.getString(R.string.credit_price_hint)

        // A price column is as wide as its widest figure, at least wide enough
        // for most titles on one line; a title wider than that wraps instead.
        fun priceWidth(texts: List<String>, title: String): Float =
            max(widest(texts, pricePaint), min(headPaint.measureText(title), 260f)).coerceIn(150f, 300f) + 4f

        val unitW = if (showUnit) (max(widest(unitTexts, unitPaint), min(headPaint.measureText(hUnit), 180f)).coerceIn(100f, 220f) + 4f) else 0f
        val cashW = if (input.columns.cash) priceWidth(cashTexts, hCash) else 0f
        val creditW = if (input.columns.credit) priceWidth(creditTexts, hCredit) else 0f
        val gaps = listOf(showUnit, input.columns.cash, input.columns.credit).count { it } * GAP
        val nameW = max(320f, content - unitW - cashW - creditW - gaps)

        var at = 0f
        val nameCol = Col(vx(rtl, at, nameW), nameW.toInt(), start); at += nameW + GAP
        val unitCol = if (showUnit) Col(vx(rtl, at, unitW), unitW.toInt(), start).also { at += unitW + GAP } else null
        val cashCol = if (input.columns.cash) Col(vx(rtl, at, cashW), cashW.toInt(), end).also { at += cashW + GAP } else null
        val creditCol = if (input.columns.credit) Col(vx(rtl, at, creditW), creditW.toInt(), end) else null
        val cols = listOf(nameCol, unitCol, cashCol, creditCol)

        val titles = listOf(hProduct, hUnit, hCash, hCredit)
        val headCells = cols.mapIndexed { i, c -> c?.let { block(titles[i], headPaint, it.width, it.align, rtl) } }
        val headRowH = 2 * 18f + (headCells.filterNotNull().maxOfOrNull { it.height } ?: 0).toFloat()

        val rowLayouts = rows.mapIndexed { i, r ->
            val name = block(r.name, namePaint, nameCol.width, start, rtl)
            val company = if (input.companyPerRow && r.company != null)
                block(r.company, companyPaint, nameCol.width, start, rtl) else null
            val unit = unitCol?.let { c -> unitTexts[i].takeIf { it.isNotEmpty() }?.let { block(it, unitPaint, c.width, start, rtl) } }
            val cash = cashCol?.let { block(cashTexts[i], pricePaint, it.width, end, rtl) }
            val credit = creditCol?.let { block(creditTexts[i], pricePaint, it.width, end, rtl) }
            val nameCell = name.height + (company?.let { 4f + it.height } ?: 0f)
            val tallest = listOfNotNull(nameCell, unit?.height?.toFloat(), cash?.height?.toFloat(), credit?.height?.toFloat()).maxOrNull() ?: 0f
            RowLayout(name, company, unit, cash, credit, tallest + 2 * ROW_PAD)
        }

        val topH = bandH + ACCENT + headingH + headRowH
        val capacity = max(1, (MAX_HEIGHT - topH - FOOTER).toInt())
        val pages = RateList.paginate(rowLayouts.map { ceil(it.height).toInt() }, capacity)

        // Figures in the app's own digits (Latin, like every amount), passed in
        // already written: a %d would let an Arabic or Bengali locale switch
        // the page count to its own numerals while the prices above stay Latin.
        val pageWord = { n: Int, of: Int ->
            context.getString(R.string.rate_list_page,
                String.format(Digits.FIGURES, "%d", n), String.format(Digits.FIGURES, "%d", of))
        }

        return Plan(
            rtl, input, locale, bandH, shop, contact, textStart, textWidth,
            heading, sub, headingH, headCells, headRowH, cols, rowLayouts, pages, topH, pageWord
        )
    }

    private fun Canvas.put(l: StaticLayout, x: Float, y: Float) {
        save()
        translate(x, y)
        l.draw(this)
        restore()
    }

    /** Draw page [index] of [plan]. The caller owns the bitmap and recycles it. */
    fun draw(plan: Plan, index: Int): Bitmap {
        val range = plan.pages[index]
        val rowsH = range.sumOf { plan.rows[it].height.toDouble() }.toFloat()
        val h = ceil(plan.topH + rowsH + FOOTER).toInt()
        val bmp = Bitmap.createBitmap(WIDTH, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(C_BG)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val rtl = plan.rtl
        val content = WIDTH - 2 * M

        // Band.
        fill.color = C_BAND
        c.drawRect(0f, 0f, WIDTH.toFloat(), plan.bandH, fill)
        val logo = plan.input.logo
        if (logo != null) {
            val lx = vx(rtl, 0f, LOGO)
            val ly = (plan.bandH - LOGO) / 2f
            fill.color = 0xFFFFFFFF.toInt()
            c.drawRoundRect(RectF(lx, ly, lx + LOGO, ly + LOGO), 20f, 20f, fill)
            val inner = LOGO - 20f
            val scale = min(inner / logo.width, inner / logo.height)
            val w = logo.width * scale
            val hh = logo.height * scale
            val dst = RectF(lx + (LOGO - w) / 2f, ly + (LOGO - hh) / 2f, lx + (LOGO + w) / 2f, ly + (LOGO + hh) / 2f)
            c.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        val textBlockH = plan.shop.height + (plan.contact?.let { 8f + it.height } ?: 0f)
        var ty = (plan.bandH - textBlockH) / 2f
        val tx = vx(rtl, plan.textStart, plan.textWidth.toFloat())
        c.put(plan.shop, tx, ty)
        ty += plan.shop.height + 8f
        plan.contact?.let { c.put(it, tx, ty) }

        // Gold line.
        fill.color = C_GOLD
        c.drawRect(0f, plan.bandH, WIDTH.toFloat(), plan.bandH + ACCENT, fill)

        // Heading and date.
        var y = plan.bandH + ACCENT + 32f
        c.put(plan.heading, M, y)
        y += plan.heading.height + 8f
        c.put(plan.sub, M, y)
        y = plan.bandH + ACCENT + plan.headingH

        // Column titles.
        fill.color = C_HEAD_BG
        c.drawRect(0f, y, WIDTH.toFloat(), y + plan.headRowH, fill)
        plan.cols.forEachIndexed { i, col ->
            val cell = plan.headCells[i]
            if (col != null && cell != null) c.put(cell, col.x, y + 18f)
        }
        y += plan.headRowH

        // Rows.
        val line = Paint().apply { color = C_LINE; strokeWidth = 2f }
        range.forEachIndexed { k, i ->
            val r = plan.rows[i]
            if (k % 2 == 1) {
                fill.color = C_ZEBRA
                c.drawRect(0f, y, WIDTH.toFloat(), y + r.height, fill)
            }
            val top = y + ROW_PAD
            val nameCol = plan.cols[0]!!
            c.put(r.name, nameCol.x, top)
            r.company?.let { c.put(it, nameCol.x, top + r.name.height + 4f) }
            plan.cols[1]?.let { col -> r.unit?.let { c.put(it, col.x, top) } }
            plan.cols[2]?.let { col -> r.cash?.let { c.put(it, col.x, top) } }
            plan.cols[3]?.let { col -> r.credit?.let { c.put(it, col.x, top) } }
            y += r.height
            c.drawLine(M, y - 1f, WIDTH - M, y - 1f, line)
        }

        // Foot: the app's mark at the reading side, the page count at the other.
        val footPaint = paint(26f, C_MUTED, false, plan.locale)
        val half = (content / 2f).toInt()
        val footY = y + (FOOTER - footPaint.fontSpacing) / 2f
        plan.input.mark?.let {
            c.put(block(it, footPaint, half, Layout.Alignment.ALIGN_NORMAL, rtl, maxLines = 1), vx(rtl, 0f, half.toFloat()), footY)
        }
        if (plan.pages.size > 1) {
            val word = plan.pageWord(index + 1, plan.pages.size)
            c.put(block(word, footPaint, half, Layout.Alignment.ALIGN_OPPOSITE, rtl, maxLines = 1),
                vx(rtl, content - half, half.toFloat()), footY)
        }
        return bmp
    }
}
