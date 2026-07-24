package com.innovation313.roshankhata.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.innovation313.roshankhata.R

/**
 * The business card designs.
 *
 * Twelve arrangements built the way a print shop builds them: a diagonal
 * sweep, a curved sash, a folded corner, a two-tone split with a rule between.
 * Not twelve recolours of one layout — the shape is what makes a card look
 * designed rather than typed.
 *
 * Everything is drawn on the phone with shapes and gradients. No template is
 * downloaded and no artwork is bundled, so the app stays small and a card is
 * made entirely on the device the shop's details were typed into.
 */
object CardTemplates {

    /** What a card has to show. Blank fields are skipped. */
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

    // Pairs that print well: one deep colour to hold the shape, one bright to
    // cut across it.
    private val INK = Color.parseColor("#1A1A18")
    private val WHITE = Color.WHITE
    private val PAPER = Color.parseColor("#F7F6F2")
    private val CHARCOAL = Color.parseColor("#2B2B2B")

    private val NAVY = Color.parseColor("#12324F")
    private val NAVY_DEEP = Color.parseColor("#0B2237")
    private val TEAL = Color.parseColor("#14524B")
    private val GREEN = Color.parseColor("#1B5E3A")
    private val GREEN_DEEP = Color.parseColor("#0E3F26")
    private val MAROON = Color.parseColor("#8E2230")
    private val PLUM = Color.parseColor("#3F2A4D")

    private val AMBER = Color.parseColor("#E8A33D")
    private val GOLD = Color.parseColor("#C9A227")
    private val GOLD_PALE = Color.parseColor("#EBCB78")
    private val ORANGE = Color.parseColor("#E2622F")
    private val CRIMSON = Color.parseColor("#C6303B")
    private val LIME = Color.parseColor("#7DA82B")
    private val SKY = Color.parseColor("#2E7CB8")

    // ---------- drawing helpers ----------

    private fun paint(
        size: Float,
        colour: Int,
        bold: Boolean,
        align: Paint.Align = Paint.Align.LEFT
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = colour
        textSize = size
        textAlign = align
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }

    private fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = colour
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    /** A two-stop gradient across the given box, for the fields that need depth. */
    private fun gradient(x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP)
        }

    /**
     * Shrink text until it fits the width it has been given.
     *
     * "Bhatti Traders" and "Al-Madina Pesticides and General Order Suppliers"
     * both have to sit on the same card; a name running off the edge is worse
     * than a name set smaller.
     */
    private fun fitted(
        text: String,
        max: Float,
        start: Float,
        colour: Int,
        bold: Boolean,
        align: Paint.Align = Paint.Align.LEFT
    ): Paint {
        var size = start
        var p = paint(size, colour, bold, align)
        while (p.measureText(text) > max && size > 26f) {
            size -= 2f
            p = paint(size, colour, bold, align)
        }
        return p
    }

    private fun path(block: Path.() -> Unit) = Path().apply(block)

    /**
     * The little marks beside a phone number or an address.
     *
     * Drawn rather than bundled: three vector shapes cost nothing and a card
     * without them looks like a list of text, which is exactly what the plain
     * templates looked like.
     */
    private fun phoneIcon(c: Canvas, cx: Float, cy: Float, r: Float, colour: Int) {
        c.drawCircle(cx, cy, r, fill(colour))
        val s = r * 0.5f
        val handset = path {
            moveTo(cx - s * 0.55f, cy - s * 0.75f)
            lineTo(cx - s * 0.05f, cy - s * 0.75f)
            lineTo(cx + s * 0.1f, cy - s * 0.15f)
            lineTo(cx - s * 0.2f, cy + s * 0.05f)
            lineTo(cx + s * 0.2f, cy + s * 0.6f)
            lineTo(cx + s * 0.6f, cy + s * 0.35f)
            lineTo(cx + s * 0.85f, cy + s * 0.8f)
            lineTo(cx + s * 0.2f, cy + s * 0.95f)
            close()
        }
        c.drawPath(handset, fill(WHITE))
    }

    private fun mailIcon(c: Canvas, cx: Float, cy: Float, r: Float, colour: Int) {
        c.drawCircle(cx, cy, r, fill(colour))
        val w = r * 1.05f
        val h = r * 0.72f
        val box = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        c.drawRect(box, fill(WHITE))
        c.drawPath(
            path {
                moveTo(box.left, box.top)
                lineTo(cx, cy + h * 0.12f)
                lineTo(box.right, box.top)
            },
            stroke(colour, r * 0.16f)
        )
    }

    private fun pinIcon(c: Canvas, cx: Float, cy: Float, r: Float, colour: Int) {
        c.drawCircle(cx, cy, r, fill(colour))
        val drop = path {
            moveTo(cx, cy + r * 0.62f)
            cubicTo(cx - r * 0.62f, cy - r * 0.1f, cx - r * 0.34f, cy - r * 0.62f, cx, cy - r * 0.62f)
            cubicTo(cx + r * 0.34f, cy - r * 0.62f, cx + r * 0.62f, cy - r * 0.1f, cx, cy + r * 0.62f)
            close()
        }
        c.drawPath(drop, fill(WHITE))
        c.drawCircle(cx, cy - r * 0.16f, r * 0.2f, fill(colour))
    }

    /**
     * The contact block: an icon, then the line, repeated down the card.
     * Returns the y it finished at.
     */
    private fun contacts(
        c: Canvas,
        d: CardData,
        left: Float,
        top: Float,
        textColour: Int,
        iconColour: Int,
        size: Float = 34f,
        gap: Float = 58f
    ): Float {
        var y = top
        val r = size * 0.62f
        val textLeft = left + r * 2.6f

        if (d.owner.isNotEmpty()) {
            c.drawCircle(left + r, y - size * 0.32f, r, fill(iconColour))
            c.drawText(
                d.owner.take(1).uppercase(),
                left + r, y - size * 0.32f + size * 0.34f,
                paint(size * 0.8f, WHITE, true, Paint.Align.CENTER)
            )
            c.drawText(d.owner, textLeft, y, paint(size, textColour, true))
            y += gap
        }
        if (d.phone.isNotEmpty()) {
            phoneIcon(c, left + r, y - size * 0.32f, r, iconColour)
            c.drawText(d.phone, textLeft, y, paint(size, textColour, false))
            y += gap
        }
        if (d.address.isNotEmpty()) {
            pinIcon(c, left + r, y - size * 0.32f, r, iconColour)
            c.drawText(d.address, textLeft, y, paint(size * 0.92f, textColour, false))
            y += gap
        }
        return y
    }

    // ---------- the twelve ----------

    /** 1. Sweep: a diagonal band of colour across a pale card. */
    private fun sweep(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        c.drawPath(
            path {
                moveTo(w * 0.52f, 0f); lineTo(w.toFloat(), 0f)
                lineTo(w.toFloat(), h.toFloat()); lineTo(w * 0.26f, h.toFloat()); close()
            },
            fill(NAVY)
        )
        c.drawPath(
            path {
                moveTo(w * 0.48f, 0f); lineTo(w * 0.545f, 0f)
                lineTo(w * 0.285f, h.toFloat()); lineTo(w * 0.22f, h.toFloat()); close()
            },
            fill(AMBER)
        )

        c.drawText(d.name, 70f, 200f, fitted(d.name, w * 0.42f, 66f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 252f, paint(32f, NAVY, false))
        contacts(c, d, 70f, 360f, INK, NAVY)
        c.drawText(d.footer, 70f, h - 46f, paint(24f, NAVY, false))
    }

    /** 2. Sash: a curved ribbon of colour sweeping under the name. */
    private fun sash(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)
        c.drawPath(
            path {
                moveTo(0f, h * 0.62f)
                cubicTo(w * 0.3f, h * 0.44f, w * 0.62f, h * 0.9f, w.toFloat(), h * 0.66f)
                lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
            },
            fill(CRIMSON)
        )
        c.drawPath(
            path {
                moveTo(0f, h * 0.56f)
                cubicTo(w * 0.3f, h * 0.38f, w * 0.62f, h * 0.84f, w.toFloat(), h * 0.6f)
                lineTo(w.toFloat(), h * 0.66f)
                cubicTo(w * 0.62f, h * 0.9f, w * 0.3f, h * 0.44f, 0f, h * 0.62f)
                close()
            },
            fill(Color.parseColor("#E4E2DC"))
        )

        c.drawText(d.name, 70f, 170f, fitted(d.name, w - 140f, 70f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 222f, paint(32f, CRIMSON, false))
        contacts(c, d, 70f, 320f, INK, CRIMSON, gap = 54f)
        c.drawText(d.footer, w - 70f, h - 40f, paint(24f, WHITE, false, Paint.Align.RIGHT))
    }

    /** 3. Fold: a corner turned back, as if the card were paper. */
    private fun fold(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)
        c.drawPath(
            path {
                moveTo(w * 0.58f, 0f); lineTo(w.toFloat(), 0f)
                lineTo(w.toFloat(), h.toFloat()); lineTo(w * 0.42f, h.toFloat()); close()
            },
            fill(NAVY_DEEP)
        )
        c.drawPath(
            path { moveTo(w * 0.58f, 0f); lineTo(w * 0.78f, 0f); lineTo(w * 0.5f, h * 0.52f); close() },
            fill(ORANGE)
        )

        c.drawText(d.name, 70f, 190f, fitted(d.name, w * 0.46f, 60f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 240f, paint(30f, ORANGE, false))
        contacts(c, d, 70f, 350f, INK, NAVY_DEEP, size = 30f, gap = 52f)

        val right = w - 60f
        c.drawText(d.footer, right, h - 44f, paint(24f, GOLD_PALE, false, Paint.Align.RIGHT))
    }

    /** 4. Bar: a solid field above, details below a coloured rule. */
    private fun bar(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)
        c.drawRect(0f, 0f, w.toFloat(), h * 0.4f, fill(TEAL))
        c.drawRect(0f, h * 0.4f, w.toFloat(), h * 0.4f + 14f, fill(GOLD))

        c.drawText(d.name, 70f, 160f, fitted(d.name, w - 140f, 70f, WHITE, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 214f, paint(32f, GOLD_PALE, false))
        contacts(c, d, 70f, h * 0.4f + 100f, INK, TEAL, gap = 56f)
        c.drawText(d.footer, w - 70f, h - 40f, paint(24f, TEAL, false, Paint.Align.RIGHT))
    }

    /** 5. Wedge: two triangles meeting at a bright seam. */
    private fun wedge(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        c.drawPath(
            path { moveTo(0f, 0f); lineTo(w * 0.46f, 0f); lineTo(0f, h.toFloat()); close() },
            fill(GREEN_DEEP)
        )
        c.drawPath(
            path {
                moveTo(w * 0.46f, 0f); lineTo(w * 0.58f, 0f)
                lineTo(w * 0.12f, h.toFloat()); lineTo(0f, h.toFloat()); close()
            },
            fill(LIME)
        )

        val right = w - 70f
        c.drawText(d.name, right, 190f, fitted(d.name, w * 0.5f, 62f, INK, true, Paint.Align.RIGHT))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, right, 240f, paint(30f, GREEN_DEEP, false, Paint.Align.RIGHT))
        }

        var y = 340f
        listOfNotNull(
            d.owner.takeIf { it.isNotEmpty() },
            d.phone.takeIf { it.isNotEmpty() },
            d.address.takeIf { it.isNotEmpty() }
        ).forEach { line ->
            c.drawText(line, right, y, paint(32f, INK, false, Paint.Align.RIGHT)); y += 52f
        }
        c.drawText(d.footer, right, h - 44f, paint(24f, GREEN_DEEP, false, Paint.Align.RIGHT))
    }

    /** 6. Arc: a quarter circle anchoring the corner. */
    private fun arc(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)
        val r = h * 1.05f
        c.drawCircle(-r * 0.18f, h * 0.5f, r * 0.62f, fill(MAROON))
        c.drawCircle(-r * 0.18f, h * 0.5f, r * 0.66f, stroke(GOLD, 6f))

        val left = w * 0.42f
        c.drawText(d.name, left, 190f, fitted(d.name, w - left - 60f, 62f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, left, 240f, paint(30f, MAROON, false))
        contacts(c, d, left, 340f, INK, MAROON, size = 30f, gap = 52f)
        c.drawText(d.footer, left, h - 44f, paint(24f, MAROON, false))
    }

    /** 7. Twin: the card halved, name on the dark side. */
    private fun twin(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)
        c.drawRect(w * 0.45f, 0f, w.toFloat(), h.toFloat(), fill(CHARCOAL))
        c.drawRect(w * 0.45f - 12f, 0f, w * 0.45f, h.toFloat(), fill(AMBER))

        val right = w - 60f
        c.drawText(d.name, right, 170f, fitted(d.name, w * 0.48f, 56f, WHITE, true, Paint.Align.RIGHT))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, right, 218f, paint(28f, AMBER, false, Paint.Align.RIGHT))
        }
        var y = h - 190f
        listOfNotNull(
            d.phone.takeIf { it.isNotEmpty() },
            d.address.takeIf { it.isNotEmpty() }
        ).forEach { line ->
            c.drawText(line, right, y, paint(28f, WHITE, false, Paint.Align.RIGHT)); y += 46f
        }

        if (d.owner.isNotEmpty()) {
            c.drawText(d.owner, 70f, h * 0.5f, fitted(d.owner, w * 0.38f, 52f, INK, true))
        }
        c.drawText(d.footer, 70f, h - 46f, paint(24f, CHARCOAL, false))
    }

    /** 8. Ribbon: a bright band across a deep field, name inside it. */
    private fun ribbon(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(NAVY)
        c.drawPath(
            path {
                moveTo(0f, h * 0.2f); lineTo(w.toFloat(), h * 0.1f)
                lineTo(w.toFloat(), h * 0.42f); lineTo(0f, h * 0.52f); close()
            },
            fill(CRIMSON)
        )
        c.drawPath(
            path {
                moveTo(0f, h * 0.52f); lineTo(w.toFloat(), h * 0.42f)
                lineTo(w.toFloat(), h * 0.45f); lineTo(0f, h * 0.55f); close()
            },
            fill(WHITE)
        )

        c.drawText(d.name, 70f, h * 0.36f, fitted(d.name, w - 200f, 62f, WHITE, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, h * 0.63f, paint(30f, GOLD_PALE, false))
        contacts(c, d, 70f, h * 0.75f, WHITE, CRIMSON, size = 28f, gap = 44f)
    }

    /** 9. Gild: a dark field with a fine gold frame and rule. */
    private fun gild(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(INK)
        c.drawRect(RectF(28f, 28f, w - 28f, h - 28f), stroke(GOLD, 3f))
        c.drawRect(
            RectF(40f, 40f, w - 40f, h - 40f),
            stroke(Color.parseColor("#4A3F1E"), 1.5f)
        )

        val cx = w / 2f
        c.drawText(d.name, cx, 220f, fitted(d.name, w - 200f, 68f, GOLD_PALE, true, Paint.Align.CENTER))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, cx, 272f, paint(30f, WHITE, false, Paint.Align.CENTER))
        }
        c.drawLine(cx - 140f, 316f, cx + 140f, 316f, fill(GOLD).apply { strokeWidth = 2f })

        var y = 400f
        listOfNotNull(
            d.owner.takeIf { it.isNotEmpty() },
            d.phone.takeIf { it.isNotEmpty() },
            d.address.takeIf { it.isNotEmpty() }
        ).forEach { line ->
            c.drawText(line, cx, y, paint(32f, WHITE, false, Paint.Align.CENTER)); y += 50f
        }
        c.drawText(d.footer, cx, h - 70f, paint(24f, GOLD, false, Paint.Align.CENTER))
    }

    /** 10. Dusk: a gradient field, text ranged left over the dark end. */
    private fun dusk(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            gradient(0f, 0f, w.toFloat(), h.toFloat(), NAVY_DEEP, PLUM)
        )
        c.drawPath(
            path {
                moveTo(w * 0.72f, h.toFloat()); lineTo(w.toFloat(), h * 0.55f)
                lineTo(w.toFloat(), h.toFloat()); close()
            },
            fill(SKY)
        )

        c.drawText(d.name, 70f, 190f, fitted(d.name, w * 0.72f, 66f, WHITE, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 242f, paint(30f, GOLD_PALE, false))
        contacts(c, d, 70f, 350f, WHITE, SKY, size = 30f, gap = 52f)
        c.drawText(d.footer, 70f, h - 46f, paint(24f, Color.parseColor("#9FB6C9"), false))
    }

    /** 11. Rule: quiet paper, one strong line, everything aligned to it. */
    private fun rule(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        c.drawRect(0f, 0f, 20f, h.toFloat(), fill(GREEN))
        c.drawLine(70f, 250f, w - 70f, 250f, fill(GREEN).apply { strokeWidth = 3f })

        c.drawText(d.name, 70f, 200f, fitted(d.name, w - 140f, 72f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 306f, paint(32f, GREEN, false))
        contacts(c, d, 70f, 400f, INK, GREEN, gap = 56f)
        c.drawText(d.footer, w - 70f, h - 44f, paint(24f, GREEN, false, Paint.Align.RIGHT))
    }

    /** 12. Peak: layered chevrons rising from the foot of the card. */
    private fun peak(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)
        c.drawPath(
            path {
                moveTo(0f, h.toFloat()); lineTo(w * 0.42f, h * 0.34f)
                lineTo(w * 0.56f, h * 0.34f); lineTo(w * 0.14f, h.toFloat()); close()
            },
            fill(Color.parseColor("#DCE3E8"))
        )
        c.drawPath(
            path {
                moveTo(w * 0.16f, h.toFloat()); lineTo(w * 0.58f, h * 0.34f)
                lineTo(w * 0.72f, h * 0.34f); lineTo(w * 0.3f, h.toFloat()); close()
            },
            fill(AMBER)
        )
        c.drawPath(
            path {
                moveTo(w * 0.32f, h.toFloat()); lineTo(w * 0.74f, h * 0.34f)
                lineTo(w.toFloat(), h * 0.34f); lineTo(w.toFloat(), h.toFloat()); close()
            },
            fill(NAVY)
        )

        c.drawText(d.name, 70f, 150f, fitted(d.name, w - 140f, 64f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 200f, paint(30f, NAVY, false))
        contacts(c, d, 70f, 285f, INK, NAVY, size = 28f, gap = 46f)
        c.drawText(d.footer, w - 60f, h - 40f, paint(24f, WHITE, false, Paint.Align.RIGHT))
    }

    /**
     * Every design, in picker order.
     *
     * The id is what gets stored, so these must not be renumbered: a shop that
     * picked number seven should still have number seven after an update.
     */
    val all: List<Template> = listOf(
        Template(0, R.string.biz_tpl_classic, ::rule),
        Template(1, R.string.biz_tpl_gold, ::gild),
        Template(2, R.string.biz_tpl_green, ::bar),
        Template(3, R.string.biz_tpl_corner, ::sweep),
        Template(4, R.string.biz_tpl_spine, ::twin),
        Template(5, R.string.biz_tpl_split, ::sash),
        Template(6, R.string.biz_tpl_monogram, ::arc),
        Template(7, R.string.biz_tpl_ledger, ::fold),
        Template(8, R.string.biz_tpl_arch, ::ribbon),
        Template(9, R.string.biz_tpl_slate, ::dusk),
        Template(10, R.string.biz_tpl_olive, ::wedge),
        Template(11, R.string.biz_tpl_stamp, ::peak)
    )

    /** The design with this id, or the first if the id is unknown. */
    fun byId(id: Int): Template = all.firstOrNull { it.id == id } ?: all.first()
}
