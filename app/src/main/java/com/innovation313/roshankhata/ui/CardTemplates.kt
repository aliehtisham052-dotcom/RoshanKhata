package com.innovation313.roshankhata.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.innovation313.roshankhata.R

/**
 * The business card designs.
 *
 * Twelve of them, and each is a different arrangement rather than the same
 * card in another colour — a corner wedge, a left spine, a framed panel, a
 * split field. A shopkeeper choosing a card is choosing how their shop looks
 * to a customer, and three recolours of one layout is not a choice.
 *
 * Everything is drawn here on the phone. Nothing is uploaded and no template
 * is fetched: the card a shop shares is made entirely on the device it was
 * typed into.
 */
object CardTemplates {

    /** What a card has to show. Blank fields are simply skipped. */
    data class CardData(
        val name: String,
        val type: String,
        val owner: String,
        val phone: String,
        val address: String,
        val footer: String
    )

    /** One design: a name for the picker, and how to draw it. */
    class Template(
        val id: Int,
        val labelRes: Int,
        val draw: (Canvas, CardData, Int, Int) -> Unit
    )

    const val W = 1200
    const val H = 700

    // Inside the app's own palette, plus a few deeper tones that only make
    // sense at card size.
    private val INK = Color.parseColor("#1A1A18")
    private val PAPER = Color.parseColor("#F7F6F2")
    private val CREAM = Color.parseColor("#FAF6EC")
    private val GREEN = Color.parseColor("#1B5E3A")
    private val GREEN_DEEP = Color.parseColor("#0E3F26")
    private val TEAL = Color.parseColor("#12564E")
    private val GOLD = Color.parseColor("#D9B44A")
    private val GOLD_PALE = Color.parseColor("#F2D27C")
    private val OLIVE = Color.parseColor("#3E5B2A")
    private val CLAY = Color.parseColor("#8A4B2A")
    private val SLATE = Color.parseColor("#33414C")
    private val WHITE = Color.WHITE

    private fun paint(
        size: Float,
        colour: Int,
        bold: Boolean,
        align: Paint.Align = Paint.Align.CENTER
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = colour
        textSize = size
        textAlign = align
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }

    /**
     * Shrink the name until it fits the width it has.
     *
     * A shop called "Bhatti Traders" and one called "Al-Madina Pesticides and
     * General Order Suppliers" both have to sit on the same card, and a name
     * running off the edge is worse than a name set a little smaller.
     */
    private fun fitted(
        text: String,
        max: Float,
        start: Float,
        colour: Int,
        bold: Boolean,
        align: Paint.Align = Paint.Align.CENTER
    ): Paint {
        var size = start
        var p = paint(size, colour, bold, align)
        while (p.measureText(text) > max && size > 28f) {
            size -= 3f
            p = paint(size, colour, bold, align)
        }
        return p
    }

    /** The lines under the name, in the order they read. */
    private fun details(data: CardData): List<Pair<String, Boolean>> = listOfNotNull(
        data.owner.takeIf { it.isNotEmpty() }?.let { it to true },
        data.phone.takeIf { it.isNotEmpty() }?.let { it to true },
        data.address.takeIf { it.isNotEmpty() }?.let { it to false }
    )

    // ---------- The twelve ----------

    /** Paper: dark type on a plain sheet, a rule under the name. */
    private fun classic(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        val cx = w / 2f
        c.drawText(d.name, cx, 250f, fitted(d.name, w - 160f, 92f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, cx, 310f, paint(44f, GREEN, false))
        c.drawLine(cx - 220f, 360f, cx + 220f, 360f, fill(GREEN).apply { strokeWidth = 5f })
        var y = 440f
        details(d).forEach { (line, bold) ->
            c.drawText(line, cx, y, paint(if (bold) 52f else 42f, INK, bold)); y += 62f
        }
        c.drawText(d.footer, cx, h - 50f, paint(30f, GREEN, false))
    }

    /** Banded: gold rules top and bottom on deep green. */
    private fun banded(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(GREEN)
        c.drawRect(0f, 0f, w.toFloat(), 26f, fill(GOLD_PALE))
        c.drawRect(0f, h - 26f, w.toFloat(), h.toFloat(), fill(GOLD_PALE))
        val cx = w / 2f
        c.drawText(d.name, cx, 250f, fitted(d.name, w - 160f, 88f, WHITE, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, cx, 308f, paint(42f, GOLD_PALE, false))
        c.drawLine(cx - 200f, 360f, cx + 200f, 360f, fill(GOLD_PALE).apply { strokeWidth = 4f })
        var y = 440f
        details(d).forEach { (line, bold) ->
            c.drawText(line, cx, y, paint(if (bold) 50f else 42f, WHITE, bold)); y += 60f
        }
        c.drawText(d.footer, cx, h - 58f, paint(30f, GOLD_PALE, false))
    }

    /** Framed: a thin gold rule inset from the edge of a dark field. */
    private fun framed(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(INK)
        val inset = 34f
        c.drawRect(
            RectF(inset, inset, w - inset, h - inset),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = GOLD
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
        )
        val cx = w / 2f
        c.drawText(d.name, cx, 260f, fitted(d.name, w - 220f, 90f, GOLD, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, cx, 320f, paint(40f, WHITE, false))
        var y = 420f
        details(d).forEach { (line, bold) ->
            c.drawText(line, cx, y, paint(if (bold) 48f else 40f, WHITE, bold)); y += 58f
        }
        c.drawText(d.footer, cx, h - 68f, paint(28f, GOLD, false))
    }

    /** Corner: a green wedge across the top-left, text ranged right. */
    private fun corner(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        val wedge = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.52f, 0f)
            lineTo(0f, h * 0.62f)
            close()
        }
        c.drawPath(wedge, fill(GREEN))
        val right = w - 70f
        c.drawText(d.name, right, 250f, fitted(d.name, w * 0.6f, 78f, INK, true, Paint.Align.RIGHT))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, right, 305f, paint(38f, GREEN, false, Paint.Align.RIGHT))
        }
        var y = 400f
        details(d).forEach { (line, bold) ->
            c.drawText(line, right, y, paint(if (bold) 46f else 38f, INK, bold, Paint.Align.RIGHT))
            y += 56f
        }
        c.drawText(d.footer, right, h - 44f, paint(28f, GREEN, false, Paint.Align.RIGHT))
    }

    /** Spine: a coloured band down the left, everything else on paper. */
    private fun spine(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        c.drawRect(0f, 0f, 150f, h.toFloat(), fill(TEAL))
        c.drawRect(150f, 0f, 172f, h.toFloat(), fill(GOLD))
        val left = 230f
        c.drawText(d.name, left, 250f, fitted(d.name, w - left - 70f, 84f, INK, true, Paint.Align.LEFT))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, left, 310f, paint(40f, TEAL, false, Paint.Align.LEFT))
        }
        var y = 400f
        details(d).forEach { (line, bold) ->
            c.drawText(line, left, y, paint(if (bold) 48f else 40f, INK, bold, Paint.Align.LEFT))
            y += 58f
        }
        c.drawText(d.footer, left, h - 46f, paint(28f, TEAL, false, Paint.Align.LEFT))
    }

    /** Split: green above, paper below. */
    private fun split(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        c.drawRect(0f, 0f, w.toFloat(), h * 0.46f, fill(GREEN))
        val cx = w / 2f
        c.drawText(d.name, cx, 200f, fitted(d.name, w - 160f, 86f, WHITE, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, cx, 262f, paint(40f, GOLD_PALE, false))
        var y = h * 0.46f + 110f
        details(d).forEach { (line, bold) ->
            c.drawText(line, cx, y, paint(if (bold) 50f else 42f, INK, bold)); y += 60f
        }
        c.drawText(d.footer, cx, h - 44f, paint(28f, GREEN, false))
    }

    /** Monogram: the shop's initial as a large mark beside the details. */
    private fun monogram(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(CREAM)
        val initial = d.name.trim().firstOrNull()?.uppercase() ?: "?"
        c.drawCircle(250f, h / 2f, 170f, fill(GREEN))
        c.drawText(initial, 250f, h / 2f + 62f, paint(180f, GOLD_PALE, true))
        val left = 480f
        c.drawText(d.name, left, 250f, fitted(d.name, w - left - 60f, 70f, INK, true, Paint.Align.LEFT))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, left, 305f, paint(36f, GREEN, false, Paint.Align.LEFT))
        }
        var y = 390f
        details(d).forEach { (line, bold) ->
            c.drawText(line, left, y, paint(if (bold) 44f else 38f, INK, bold, Paint.Align.LEFT))
            y += 54f
        }
        c.drawText(d.footer, left, h - 46f, paint(26f, GREEN, false, Paint.Align.LEFT))
    }

    /** Ledger: ruled lines behind the text, like a page from a khata. */
    private fun ledger(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(CREAM)
        val rule = fill(Color.parseColor("#DED8C6")).apply { strokeWidth = 2f }
        var ly = 120f
        while (ly < h - 60f) {
            c.drawLine(60f, ly, w - 60f, ly, rule)
            ly += 58f
        }
        c.drawRect(0f, 0f, w.toFloat(), 14f, fill(CLAY))
        val left = 90f
        c.drawText(d.name, left, 230f, fitted(d.name, w - 180f, 80f, INK, true, Paint.Align.LEFT))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, left, 288f, paint(38f, CLAY, false, Paint.Align.LEFT))
        }
        var y = 400f
        details(d).forEach { (line, bold) ->
            c.drawText(line, left, y, paint(if (bold) 46f else 38f, INK, bold, Paint.Align.LEFT))
            y += 58f
        }
        c.drawText(d.footer, w - 90f, h - 40f, paint(26f, CLAY, false, Paint.Align.RIGHT))
    }

    /** Arch: a rounded field holding the name, details beneath it. */
    private fun arch(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        val panel = RectF(90f, 60f, w - 90f, 330f)
        c.drawRoundRect(panel, 150f, 150f, fill(GREEN_DEEP))
        val cx = w / 2f
        c.drawText(d.name, cx, 200f, fitted(d.name, w - 320f, 78f, WHITE, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, cx, 258f, paint(36f, GOLD_PALE, false))
        var y = 430f
        details(d).forEach { (line, bold) ->
            c.drawText(line, cx, y, paint(if (bold) 48f else 40f, INK, bold)); y += 58f
        }
        c.drawText(d.footer, cx, h - 44f, paint(28f, GREEN, false))
    }

    /** Slate: a cool grey field with a single accent rule. */
    private fun slate(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(SLATE)
        c.drawRect(0f, 0f, 18f, h.toFloat(), fill(GOLD))
        val left = 90f
        c.drawText(d.name, left, 240f, fitted(d.name, w - 180f, 84f, WHITE, true, Paint.Align.LEFT))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, left, 300f, paint(38f, GOLD_PALE, false, Paint.Align.LEFT))
        }
        c.drawLine(left, 350f, left + 260f, 350f, fill(GOLD).apply { strokeWidth = 4f })
        var y = 430f
        details(d).forEach { (line, bold) ->
            c.drawText(line, left, y, paint(if (bold) 46f else 40f, WHITE, bold, Paint.Align.LEFT))
            y += 58f
        }
        c.drawText(d.footer, left, h - 44f, paint(26f, GOLD_PALE, false, Paint.Align.LEFT))
    }

    /** Olive: name high and wide on a field, details on cream below. */
    private fun olive(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(OLIVE)
        c.drawRect(0f, h * 0.62f, w.toFloat(), h.toFloat(), fill(CREAM))
        val cx = w / 2f
        c.drawText(d.name, cx, h * 0.4f, fitted(d.name, w - 160f, 92f, CREAM, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, cx, h * 0.4f + 58f, paint(40f, WHITE, false))
        var y = h * 0.62f + 90f
        details(d).forEach { (line, bold) ->
            c.drawText(line, cx, y, paint(if (bold) 46f else 40f, INK, bold)); y += 56f
        }
        c.drawText(d.footer, cx, h - 34f, paint(26f, OLIVE, false))
    }

    /** Stamp: a bordered block in the corner, the rest left quiet. */
    private fun stamp(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        val block = RectF(70f, 70f, 470f, 300f)
        c.drawRect(block, fill(TEAL))
        c.drawText(
            d.name.trim().firstOrNull()?.uppercase() ?: "?",
            block.centerX(), block.centerY() + 60f, paint(160f, GOLD_PALE, true)
        )
        val left = 70f
        c.drawText(d.name, left, 400f, fitted(d.name, w - 140f, 74f, INK, true, Paint.Align.LEFT))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, left, 452f, paint(36f, TEAL, false, Paint.Align.LEFT))
        }
        var y = 530f
        details(d).forEach { (line, bold) ->
            c.drawText(line, left, y, paint(if (bold) 44f else 38f, INK, bold, Paint.Align.LEFT))
            y += 52f
        }
        c.drawText(d.footer, w - 70f, h - 40f, paint(26f, TEAL, false, Paint.Align.RIGHT))
    }

    /**
     * Every design, in the order they appear in the picker.
     *
     * The id is what gets stored, so these must not be renumbered: a shop that
     * picked number seven should still have number seven after an update. Ids
     * 0, 1 and 2 keep the three designs that already existed, so nobody's
     * saved choice moves under them.
     */
    val all: List<Template> = listOf(
        Template(0, R.string.biz_tpl_classic, ::classic),
        Template(1, R.string.biz_tpl_gold, ::framed),
        Template(2, R.string.biz_tpl_green, ::banded),
        Template(3, R.string.biz_tpl_corner, ::corner),
        Template(4, R.string.biz_tpl_spine, ::spine),
        Template(5, R.string.biz_tpl_split, ::split),
        Template(6, R.string.biz_tpl_monogram, ::monogram),
        Template(7, R.string.biz_tpl_ledger, ::ledger),
        Template(8, R.string.biz_tpl_arch, ::arch),
        Template(9, R.string.biz_tpl_slate, ::slate),
        Template(10, R.string.biz_tpl_olive, ::olive),
        Template(11, R.string.biz_tpl_stamp, ::stamp)
    )

    /** The design with this id, or the first one if the id is unknown. */
    fun byId(id: Int): Template = all.firstOrNull { it.id == id } ?: all.first()
}
