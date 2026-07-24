package com.innovation313.roshankhata.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * The little marks beside a phone number or an address on a business card.
 *
 * Drawn rather than shipped as images: a card is rendered at 1200x700 and
 * again at whatever a print shop asks for, and a path stays sharp at both
 * while a bitmap does not. It also keeps the app's download size where it is.
 */
object CardGlyphs {

    /** A handset, tilted the way it is on every card ever printed. */
    fun phone(c: Canvas, cx: Float, cy: Float, size: Float, colour: Int) {
        val p = stroke(colour, size * 0.14f)
        val r = size / 2f
        val path = Path().apply {
            moveTo(cx - r * 0.55f, cy - r * 0.75f)
            quadTo(cx - r * 0.15f, cy - r * 0.2f, cx + r * 0.15f, cy + r * 0.2f)
            quadTo(cx + r * 0.5f, cy + r * 0.6f, cx + r * 0.75f, cy + r * 0.35f)
        }
        c.drawPath(path, p)
        c.drawCircle(cx - r * 0.55f, cy - r * 0.75f, size * 0.1f, fill(colour))
        c.drawCircle(cx + r * 0.75f, cy + r * 0.35f, size * 0.1f, fill(colour))
    }

    /** An envelope: a rectangle with its flap creased down the middle. */
    fun mail(c: Canvas, cx: Float, cy: Float, size: Float, colour: Int) {
        val p = stroke(colour, size * 0.12f)
        val w = size * 0.9f
        val h = size * 0.62f
        val box = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        c.drawRect(box, p)
        val flap = Path().apply {
            moveTo(box.left, box.top)
            lineTo(cx, cy + h * 0.12f)
            lineTo(box.right, box.top)
        }
        c.drawPath(flap, p)
    }

    /** A map pin. */
    fun pin(c: Canvas, cx: Float, cy: Float, size: Float, colour: Int) {
        val p = stroke(colour, size * 0.12f)
        val r = size * 0.3f
        val top = cy - size * 0.24f
        c.drawCircle(cx, top, r, p)
        val tail = Path().apply {
            moveTo(cx - r * 0.72f, top + r * 0.7f)
            lineTo(cx, cy + size * 0.42f)
            lineTo(cx + r * 0.72f, top + r * 0.7f)
        }
        c.drawPath(tail, p)
    }

    /** A person, for the owner's own name. */
    fun person(c: Canvas, cx: Float, cy: Float, size: Float, colour: Int) {
        val p = stroke(colour, size * 0.12f)
        c.drawCircle(cx, cy - size * 0.2f, size * 0.22f, p)
        val body = Path().apply {
            moveTo(cx - size * 0.36f, cy + size * 0.42f)
            quadTo(cx, cy - size * 0.02f, cx + size * 0.36f, cy + size * 0.42f)
        }
        c.drawPath(body, p)
    }

    /**
     * The glyph inside a filled disc — the arrangement most of these cards use,
     * where the mark is knocked out of a coloured circle.
     */
    fun disc(
        c: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        discColour: Int,
        glyphColour: Int,
        glyph: (Canvas, Float, Float, Float, Int) -> Unit
    ) {
        c.drawCircle(cx, cy, radius, fill(discColour))
        glyph(c, cx, cy, radius * 1.05f, glyphColour)
    }

    private fun stroke(colour: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
}
