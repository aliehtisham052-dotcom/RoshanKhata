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
        // Floor raised from 16f. Sixteen points on a card this size is not
        // small type, it is unreadable type — and a card nobody can read is
        // not a card. Below this the answer is to wrap or trim, not to shrink
        // further, which is what the callers now do.
        while (p.measureText(text) > max && size > MIN_READABLE) {
            size -= 1f
            p = paint(size, colour, bold, align)
        }
        return p
    }

    /** The smallest type this card will set. Below it, wrap or trim instead. */
    private const val MIN_READABLE = 22f

    /**
     * Break a line so it fits, on a word boundary, into at most [maxLines].
     *
     * A shop address is the line that breaks these cards — "Lappay wali tehsil
     * pasrur district Sialkot Pakistan" is simply longer than a business card
     * is wide, and shrinking it until it fits produces type the recipient
     * cannot read. It belongs on two lines, which is where it would go on a
     * card from a printer. Anything that still will not fit is trimmed with an
     * ellipsis rather than allowed to run past the edge.
     */
    private fun wrap(text: String, p: Paint, max: Float, maxLines: Int = 2): List<String> {
        if (p.measureText(text) <= max) return listOf(text)

        val words = text.split(' ').filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var line = StringBuilder()

        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(candidate) <= max) {
                line = StringBuilder(candidate)
            } else {
                if (line.isNotEmpty()) lines += line.toString()
                line = StringBuilder(word)
                if (lines.size == maxLines) break
            }
        }
        if (lines.size < maxLines && line.isNotEmpty()) lines += line.toString()

        // Trim ANY line that still overflows, not merely the last. Two long
        // unbreakable words put an over-wide line in FIRST position, and a
        // rule that only guarded the last line let it through — the second
        // thing the simulation caught here. Every line is checked because
        // every line is on the card.
        return lines.take(maxLines)
            .map { if (p.measureText(it) > max) ellipsise(it, p, max) else it }
            .ifEmpty { listOf(ellipsise(text, p, max)) }
    }

    private fun ellipsise(text: String, p: Paint, max: Float): String {
        if (p.measureText(text) <= max) return text
        var end = text.length
        while (end > 1 && p.measureText(text.take(end) + "…") > max) end--
        return text.take(end).trimEnd() + "…"
    }

    /**
     * A run of contact lines set against an edge, fitted and wrapped.
     *
     * Three templates each grew their own copy of this loop with a hard-coded
     * type size and no fitting at all, and all three are the cards the owner
     * photographed: the address ran off the panel, across the shape beside it,
     * or clean off the card. One implementation, so a long address behaves the
     * same wherever it appears.
     *
     * Returns the y it finished at, so a caller can place what follows.
     */
    private fun stackedContacts(
        c: Canvas,
        lines: List<String>,
        x: Float,
        top: Float,
        max: Float,
        colour: Int,
        size: Float,
        gap: Float,
        align: Paint.Align
    ): Float {
        var y = top
        lines.forEach { line ->
            val p = fitted(line, max, size, colour, false, align)
            wrap(line, p, max).forEach { part ->
                c.drawText(part, x, y, p)
                y += gap
            }
        }
        return y
    }

    /**
     * The maker's mark, and nothing more.
     *
     * This card belongs to the shopkeeper. A printer does not put their own
     * name on someone else's visiting card, and the whole point of the card is
     * that the shop looks established — a vendor credit set in the card's own
     * accent colour, at the same weight as the shop's details, worked against
     * exactly that. It read as a line of the shop's identity rather than a
     * signature.
     *
     * So it is set small, at a third of its former presence, and in the plain
     * ink or white of the surface it sits on rather than the card's accent.
     * Visible if you look for it; invisible if you are reading the card.
     *
     * An empty footer draws nothing at all — that is how the owner's switch
     * turns it off.
     */
    private fun watermark(
        c: Canvas,
        d: CardData,
        x: Float,
        y: Float,
        onDark: Boolean,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        if (d.footer.isEmpty()) return
        val base = if (onDark) WHITE else INK
        val faded = Color.argb(96, Color.red(base), Color.green(base), Color.blue(base))
        c.drawText(d.footer, x, y, paint(17f, faded, false, align))
    }

    private fun path(block: Path.() -> Unit) = Path().apply(block)

    /**
     * The little marks beside a phone number or an address.
     *
     * Drawn rather than bundled: three vector shapes cost nothing and a card
     * without them looks like a list of text, which is exactly what the plain
     * templates looked like.
     */
    /**
     * The badge is filled with [colour] and the mark drawn in [glyph].
     *
     * [glyph] used to be hard-coded white, which is right on every dark
     * badge and invisible on a pale one — a white circle with a white
     * mark on it, which is exactly what the Wave card showed.
     */
    private fun phoneIcon(
        c: Canvas, cx: Float, cy: Float, r: Float, colour: Int, glyph: Int = WHITE
    ) {
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
        c.drawPath(handset, fill(glyph))
    }

    /**
     * The badge is filled with [colour] and the mark drawn in [glyph].
     *
     * [glyph] used to be hard-coded white, which is right on every dark
     * badge and invisible on a pale one — a white circle with a white
     * mark on it, which is exactly what the Wave card showed.
     */
    private fun mailIcon(
        c: Canvas, cx: Float, cy: Float, r: Float, colour: Int, glyph: Int = WHITE
    ) {
        c.drawCircle(cx, cy, r, fill(colour))
        val w = r * 1.05f
        val h = r * 0.72f
        val box = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        c.drawRect(box, fill(glyph))
        c.drawPath(
            path {
                moveTo(box.left, box.top)
                lineTo(cx, cy + h * 0.12f)
                lineTo(box.right, box.top)
            },
            stroke(colour, r * 0.16f)
        )
    }

    /**
     * The badge is filled with [colour] and the mark drawn in [glyph].
     *
     * [glyph] used to be hard-coded white, which is right on every dark
     * badge and invisible on a pale one — a white circle with a white
     * mark on it, which is exactly what the Wave card showed.
     */
    private fun pinIcon(
        c: Canvas, cx: Float, cy: Float, r: Float, colour: Int, glyph: Int = WHITE
    ) {
        c.drawCircle(cx, cy, r, fill(colour))
        val drop = path {
            moveTo(cx, cy + r * 0.62f)
            cubicTo(cx - r * 0.62f, cy - r * 0.1f, cx - r * 0.34f, cy - r * 0.62f, cx, cy - r * 0.62f)
            cubicTo(cx + r * 0.34f, cy - r * 0.62f, cx + r * 0.62f, cy - r * 0.1f, cx, cy + r * 0.62f)
            close()
        }
        c.drawPath(drop, fill(glyph))
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
        gap: Float = 58f,
        /**
         * How far these lines may run before they reach the colour beside
         * them.
         *
         * Required, not optional. Without it a long address — "lappay wali
         * tehsil pasrur district Sialkot" — ran straight under the shape next
         * to it and vanished: dark type on a dark field, invisible on the
         * card the shopkeeper was about to send someone.
         */
        maxWidth: Float,
        /**
         * How far down this card the text may go.
         *
         * Wrapping the address onto a second line fixed it running off the
         * side, and introduced the opposite risk: on the cards whose shapes
         * climb from the bottom — the diagonal wedge, the maroon circle, the
         * ribbon's stripes — a second line can now reach the decoration
         * instead. So the choice is made per card rather than assumed. When
         * two lines would cross this floor, the address stays on one and is
         * trimmed. A trimmed address is still readable; an address printed
         * over a red stripe is not.
         */
        maxBottom: Float = Float.MAX_VALUE
    ): Float {
        var y = top
        val r = size * 0.62f
        val textLeft = left + r * 2.6f
        val room = maxWidth - (textLeft - left)

        if (d.owner.isNotEmpty()) {
            c.drawCircle(left + r, y - size * 0.32f, r, fill(iconColour))
            c.drawText(
                d.owner.take(1).uppercase(),
                left + r, y - size * 0.32f + size * 0.34f,
                paint(size * 0.8f, WHITE, true, Paint.Align.CENTER)
            )
            c.drawText(d.owner, textLeft, y, fitted(d.owner, room, size, textColour, true))
            y += gap
        }
        if (d.phone.isNotEmpty()) {
            phoneIcon(c, left + r, y - size * 0.32f, r, iconColour)
            c.drawText(d.phone, textLeft, y, fitted(d.phone, room, size, textColour, false))
            y += gap
        }
        if (d.address.isNotEmpty()) {
            // The address is the line that breaks a card. Set at the same
            // weight as the rest and wrapped onto a second line if it needs
            // one — it used to be shrunk on its own until it was half the
            // height of the phone number above it and barely legible.
            pinIcon(c, left + r, y - size * 0.32f, r, iconColour)
            val p = fitted(d.address, room, size, textColour, false)
            val secondLineAt = y + gap * 0.68f
            val lines =
                if (secondLineAt <= maxBottom) wrap(d.address, p, room)
                else listOf(ellipsise(d.address, p, room))
            lines.forEach { part ->
                c.drawText(part, textLeft, y, p)
                y += gap * 0.68f
            }
            y += gap * 0.32f
        }
        return y
    }

    private fun roundStroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = colour
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
    }

    /**
     * Contact lines with a hairline rule under each, and the icon on the far
     * side — the arrangement on the red-and-white reference, where the rules
     * do the work a box would otherwise have to.
     */
    private fun ruledContacts(
        c: Canvas,
        d: CardData,
        left: Float,
        right: Float,
        top: Float,
        textColour: Int,
        accent: Int
    ) {
        var y = top
        val room = (right - 54f) - left
        listOfNotNull(
            d.address.takeIf { it.isNotEmpty() },
            d.phone.takeIf { it.isNotEmpty() },
            d.owner.takeIf { it.isNotEmpty() }
        ).forEach { line ->
            // Fitted to the space between the rule's start and its dot. At a
            // fixed size a long address ran back past `left` and printed
            // itself across the circle of colour beside it.
            val p = fitted(line, room, 28f, textColour, false, Paint.Align.RIGHT)
            wrap(line, p, room).forEach { part ->
                c.drawText(part, right - 54f, y, p)
                y += 34f
            }
            y -= 34f
            c.drawCircle(right - 22f, y - 10f, 13f, fill(accent))
            c.drawLine(left, y + 16f, right, y + 16f, fill(accent).apply { strokeWidth = 1.6f })
            y += 62f
        }
    }

    // ---------- the twelve ----------

    /** 1. Sweep: a diagonal band of colour across a pale card. */
    private fun sweep(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        c.drawPath(
            path {
                moveTo(w * 0.64f, 0f); lineTo(w.toFloat(), 0f)
                lineTo(w.toFloat(), h.toFloat()); lineTo(w * 0.44f, h.toFloat()); close()
            },
            fill(NAVY)
        )
        c.drawPath(
            path {
                moveTo(w * 0.6f, 0f); lineTo(w * 0.665f, 0f)
                lineTo(w * 0.465f, h.toFloat()); lineTo(w * 0.4f, h.toFloat()); close()
            },
            fill(AMBER)
        )

        c.drawText(d.name, 70f, 200f, fitted(d.name, w * 0.44f, 66f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 252f, paint(32f, NAVY, false))
        // Clear of the band, which is furthest left at the foot of the card.
        // The navy wedge closes in from the right as it descends.
        contacts(c, d, 70f, 360f, INK, NAVY, maxWidth = w * 0.36f, maxBottom = h * 0.62f)
        watermark(c, d, 70f, h - 46f, onDark = false)
    }

    /** 2. Sash: a curved ribbon of colour sweeping under the name. */
    private fun sash(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)
        // The wave sits below the writing, and where it sits was measured.
        //
        // It used to crest at h*0.517 — y=362 on a 700px card — while the
        // phone line sat at 374 and the address at 428. The text was in the
        // right place; the wave had climbed into it, so the address printed
        // half on white and half on grey. Wrapping the address onto a second
        // line, which this card now does, only pushed it deeper.
        //
        // These control points crest at h*0.684, y=479, clear of the lowest
        // ink the block can reach even with a two-line address (452).
        c.drawPath(
            path {
                moveTo(0f, h * 0.78f)
                cubicTo(w * 0.3f, h * 0.62f, w * 0.62f, h * 1.06f, w.toFloat(), h * 0.82f)
                lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
            },
            fill(CRIMSON)
        )
        c.drawPath(
            path {
                moveTo(0f, h * 0.72f)
                cubicTo(w * 0.3f, h * 0.56f, w * 0.62f, h * 1.0f, w.toFloat(), h * 0.76f)
                lineTo(w.toFloat(), h * 0.82f)
                cubicTo(w * 0.62f, h * 1.06f, w * 0.3f, h * 0.62f, 0f, h * 0.78f)
                close()
            },
            fill(Color.parseColor("#E4E2DC"))
        )

        c.drawText(d.name, 70f, 170f, fitted(d.name, w - 140f, 70f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 222f, paint(32f, CRIMSON, false))
        contacts(c, d, 70f, 300f, INK, CRIMSON, gap = 50f, maxWidth = w - 140f)
        watermark(c, d, w - 70f, h - 40f, onDark = true, align = Paint.Align.RIGHT)
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
        contacts(c, d, 70f, 350f, INK, NAVY_DEEP, size = 30f, gap = 52f, maxWidth = w * 0.32f)

        val right = w - 60f
        watermark(c, d, right, h - 44f, onDark = true, align = Paint.Align.RIGHT)
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
        // The maroon circle is at its widest across the middle of the card.
        contacts(
            c, d, left, 340f, INK, MAROON, size = 30f, gap = 52f,
            maxWidth = w - left - 60f, maxBottom = h * 0.64f
        )
        watermark(c, d, left, h - 44f, onDark = false)
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
        // Fitted and wrapped to the dark panel only. At a fixed size the
        // address reached past the amber spine and off the card entirely.
        stackedContacts(
            c,
            listOfNotNull(
                d.phone.takeIf { it.isNotEmpty() },
                d.address.takeIf { it.isNotEmpty() }
            ),
            x = right, top = h - 190f, max = w * 0.5f,
            colour = WHITE, size = 28f, gap = 42f, align = Paint.Align.RIGHT
        )

        if (d.owner.isNotEmpty()) {
            c.drawText(d.owner, 70f, h * 0.5f, fitted(d.owner, w * 0.38f, 52f, INK, true))
        }
        watermark(c, d, 70f, h - 46f, onDark = false)
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
        watermark(c, d, cx, h - 70f, onDark = true, align = Paint.Align.CENTER)
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
        contacts(c, d, 70f, 350f, WHITE, SKY, size = 30f, gap = 52f, maxWidth = w * 0.62f)
        watermark(c, d, 70f, h - 46f, onDark = true)
    }

    /** 11. Rule: quiet paper, one strong line, everything aligned to it. */
    private fun rule(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(PAPER)
        c.drawRect(0f, 0f, 20f, h.toFloat(), fill(GREEN))
        c.drawLine(70f, 250f, w - 70f, 250f, fill(GREEN).apply { strokeWidth = 3f })

        c.drawText(d.name, 70f, 200f, fitted(d.name, w - 140f, 72f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, 306f, paint(32f, GREEN, false))
        contacts(c, d, 70f, 400f, INK, GREEN, gap = 56f, maxWidth = w - 140f)
        watermark(c, d, w - 70f, h - 44f, onDark = false, align = Paint.Align.RIGHT)
    }

    /**
     * Stripes: a red rule across the top and thick rounded ribbons running
     * diagonally out of two corners.
     *
     * The ribbons are strokes with round caps rather than filled shapes —
     * that rounded end is the whole character of this layout, and a polygon
     * cannot give it.
     */
    private fun stripes(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)

        c.drawRect(w * 0.13f, 0f, w.toFloat(), 26f, fill(CRIMSON))
        c.drawPath(
            path {
                moveTo(w * 0.78f, 0f); lineTo(w.toFloat(), 0f)
                lineTo(w.toFloat(), h * 0.24f); close()
            },
            fill(Color.parseColor("#8E1B22"))
        )

        // Out of the top-right corner, and mirrored out of the bottom-left.
        listOf(0f, 62f, 124f).forEachIndexed { i, off ->
            val shade = if (i == 1) CRIMSON else Color.parseColor("#D8434B")
            c.drawLine(
                w * 0.62f + off, h * 0.06f,
                w * 0.86f + off, h * 0.34f,
                roundStroke(shade, 34f)
            )
            c.drawLine(
                w * 0.06f + off, h * 0.94f,
                w * 0.3f + off, h * 0.66f,
                roundStroke(shade, 34f)
            )
        }
        c.drawRect(0f, h - 26f, w * 0.62f, h.toFloat(), fill(CRIMSON))

        c.drawText(d.name, 70f, 170f, fitted(d.name, w * 0.55f, 62f, CRIMSON, true))
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, 70f, 218f, paint(28f, INK, true))
            c.drawLine(70f, 234f, 330f, 234f, fill(CRIMSON).apply { strokeWidth = 3f })
        }
        // The lower run of stripes starts climbing at about h*0.66.
        contacts(
            c, d, 70f, 320f, INK, CRIMSON, size = 28f, gap = 52f,
            maxWidth = w * 0.52f, maxBottom = h * 0.60f
        )
    }

    /**
     * Flap: a deep field with a bright rail down one edge and two folded
     * corners on the other, as if an envelope had been opened.
     */
    private fun flap(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(Color.parseColor("#123A38"))

        // The rail. The run of dots that used to sit beside it has gone: at
        // h*0.44 to h*0.68 it landed exactly level with the contact rows, so
        // it read as a bullet list — with a fourth bullet against nothing,
        // because there are only ever three contact lines. Decoration that
        // lines up with content stops being decoration.
        c.drawRect(w * 0.045f, 0f, w * 0.105f, h * 0.72f, fill(AMBER))

        c.drawPath(
            path {
                moveTo(w.toFloat(), 0f); lineTo(w.toFloat(), h * 0.62f)
                lineTo(w * 0.62f, h * 0.06f); close()
            },
            fill(Color.parseColor("#F0E7C0"))
        )
        c.drawPath(
            path {
                moveTo(w * 0.6f, h.toFloat()); lineTo(w.toFloat(), h * 0.5f)
                lineTo(w.toFloat(), h.toFloat()); close()
            },
            fill(AMBER)
        )

        val left = w * 0.18f
        c.drawText(d.name, left, 150f, fitted(d.name, w * 0.44f, 54f, WHITE, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, left, 196f, paint(28f, AMBER, false))
        contacts(c, d, left, 320f, WHITE, AMBER, size = 26f, gap = 50f, maxWidth = w * 0.46f)
    }

    /**
     * Chevron: a curved point of colour driven in from one edge, the details
     * ruled off on the other.
     */
    private fun chevron(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)

        c.drawPath(
            path {
                moveTo(0f, 0f); lineTo(w * 0.26f, 0f)
                cubicTo(w * 0.5f, h * 0.2f, w * 0.5f, h * 0.8f, w * 0.26f, h.toFloat())
                lineTo(0f, h.toFloat()); close()
            },
            fill(Color.parseColor("#B01F35"))
        )
        c.drawPath(
            path {
                moveTo(0f, h * 0.1f); lineTo(w * 0.2f, h * 0.1f)
                cubicTo(w * 0.42f, h * 0.28f, w * 0.42f, h * 0.72f, w * 0.2f, h * 0.9f)
                lineTo(0f, h * 0.9f); close()
            },
            fill(Color.parseColor("#8E1626"))
        )

        val left = w * 0.5f
        val right = w - 60f
        if (d.name.isNotEmpty()) {
            c.drawRect(left, 96f, right, 150f, fill(Color.parseColor("#B01F35")))
            c.drawText(
                d.name, (left + right) / 2f, 136f,
                fitted(d.name, right - left - 30f, 38f, WHITE, true, Paint.Align.CENTER)
            )
        }
        if (d.type.isNotEmpty()) {
            c.drawText(d.type, (left + right) / 2f, 194f, paint(28f, INK, false, Paint.Align.CENTER))
        }
        ruledContacts(c, d, left, right, 290f, INK, Color.parseColor("#B01F35"))
        watermark(c, d, left, h - 44f, onDark = false)
    }

    /**
     * Bands: two curved sweeps meeting across the foot, one deep, one bright,
     * with the details set on the darker of them.
     */
    private fun bands(c: Canvas, d: CardData, w: Int, h: Int) {
        c.drawColor(WHITE)

        c.drawPath(
            path {
                moveTo(0f, h * 0.2f); lineTo(w * 0.46f, h * 0.2f)
                cubicTo(w * 0.4f, h * 0.34f, w * 0.24f, h * 0.34f, 0f, h * 0.34f)
                close()
            },
            fill(AMBER)
        )

        c.drawPath(
            path {
                moveTo(w.toFloat(), h * 0.46f); lineTo(w.toFloat(), h * 0.86f)
                lineTo(w * 0.34f, h * 0.86f)
                cubicTo(w * 0.5f, h * 0.86f, w * 0.5f, h * 0.46f, w * 0.66f, h * 0.46f)
                close()
            },
            fill(NAVY_DEEP)
        )
        c.drawPath(
            path {
                moveTo(0f, h * 0.9f); lineTo(w * 0.3f, h * 0.9f)
                cubicTo(w * 0.5f, h * 0.9f, w * 0.6f, h.toFloat(), w.toFloat(), h.toFloat())
                lineTo(0f, h.toFloat()); close()
            },
            fill(AMBER)
        )

        c.drawText(d.name, 70f, h * 0.3f, fitted(d.name, w * 0.4f, 46f, INK, true))
        if (d.type.isNotEmpty()) c.drawText(d.type, 70f, h * 0.3f + 40f, paint(24f, INK, false))

        // The badges here used to be plain white rounded rectangles with
        // nothing inside them — blank tiles beside every line, which is what
        // they looked like on the card. Real marks, and the text now fits the
        // navy panel instead of running off its left edge.
        var y = h * 0.56f
        val badge = w - 86f
        val textRight = badge - 34f

        // The navy panel's left edge is a CURVE, so the room available differs
        // on every line — and assuming a straight edge at w*0.36 let the text
        // begin 220px OUTSIDE the panel, which is why the address was cut off
        // mid-word against white. Solved from the same cubic the shape is
        // drawn with, per line, plus a margin.
        fun panelLeftAt(yy: Float): Float {
            var best = 0f
            var bestGap = Float.MAX_VALUE
            for (i in 0..200) {
                val t = i / 200f
                val u = 1 - t
                val py = u * u * u * (h * 0.86f) + 3 * u * u * t * (h * 0.86f) +
                    3 * u * t * t * (h * 0.46f) + t * t * t * (h * 0.46f)
                val gap = kotlin.math.abs(py - yy)
                if (gap < bestGap) {
                    bestGap = gap
                    best = u * u * u * (w * 0.34f) + 3 * u * u * t * (w * 0.5f) +
                        3 * u * t * t * (w * 0.5f) + t * t * t * (w * 0.66f)
                }
            }
            return best + 24f
        }

        if (d.owner.isNotEmpty()) {
            val room = textRight - panelLeftAt(y)
            c.drawText(d.owner, textRight, y, fitted(d.owner, room, 26f, WHITE, true, Paint.Align.RIGHT))
            c.drawCircle(badge, y - 9f, 15f, fill(WHITE))
            c.drawText(
                d.owner.take(1).uppercase(), badge, y - 9f + 9f,
                paint(20f, NAVY_DEEP, true, Paint.Align.CENTER)
            )
            y += 46f
        }
        if (d.phone.isNotEmpty()) {
            val room = textRight - panelLeftAt(y)
            c.drawText(d.phone, textRight, y, fitted(d.phone, room, 26f, WHITE, false, Paint.Align.RIGHT))
            // Pale badge, dark mark — white on white was invisible.
            phoneIcon(c, badge, y - 9f, 15f, WHITE, NAVY_DEEP)
            y += 46f
        }
        if (d.address.isNotEmpty()) {
            val room = textRight - panelLeftAt(y)
            val p = fitted(d.address, room, 26f, WHITE, false, Paint.Align.RIGHT)
            pinIcon(c, badge, y - 9f, 15f, WHITE, NAVY_DEEP)
            // One line only. A second would sit lower, where the panel is
            // wider but the amber wave is already climbing towards it.
            c.drawText(ellipsise(d.address, p, room), textRight, y, p)
            y += 46f
        }
        watermark(c, d, 70f, h - 34f, onDark = false)
    }

    /**
     * Every design, in the order the picker shows them.
     *
     * Strongest first — the diagonal, the ribbons, the blade — because the
     * first two or three are all most owners will scroll past, and the plain
     * ruled card at the end is the one to reach for deliberately rather than
     * to meet first.
     *
     * The ids are unchanged and stay bound to their own design: a shop that
     * picked number seven still has number seven. Only the running order moves.
     */
    val all: List<Template> = listOf(
        Template(3, R.string.biz_tpl_corner, ::sweep),
        Template(2, R.string.biz_tpl_green, ::stripes),
        Template(11, R.string.biz_tpl_stamp, ::chevron),
        Template(10, R.string.biz_tpl_olive, ::flap),
        Template(8, R.string.biz_tpl_arch, ::bands),
        Template(1, R.string.biz_tpl_gold, ::gild),
        Template(9, R.string.biz_tpl_slate, ::dusk),
        Template(5, R.string.biz_tpl_split, ::sash),
        Template(4, R.string.biz_tpl_spine, ::twin),
        Template(6, R.string.biz_tpl_monogram, ::arc),
        Template(7, R.string.biz_tpl_ledger, ::fold),
        Template(0, R.string.biz_tpl_classic, ::rule)
    )

    /** The design with this id, or the first if the id is unknown. */
    fun byId(id: Int): Template = all.firstOrNull { it.id == id } ?: all.first()
}
