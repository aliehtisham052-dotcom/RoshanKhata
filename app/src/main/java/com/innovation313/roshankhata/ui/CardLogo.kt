package com.innovation313.roshankhata.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.min

/**
 * The shop's own logo on its business card (4 Oct 2026).
 *
 * The twenty-four designs were each laid out by hand, and every one fills a
 * different part of the card. Rather than guess twenty-four logo positions —
 * and risk one sitting on the owner's name when his name is long — the card
 * is first drawn onto a [Recorder] that paints nothing and only notes where
 * every line of text, the QR and every shape landed. The logo then goes to the
 * free spot nearest a corner:
 *
 *  1. clear of text, the QR AND the design's own shapes (a seal ring, a needle);
 *  2. if no such spot exists, clear of text and the QR only;
 *  3. if not even that, the card is drawn without a logo.
 *
 * So the logo can never cover a word or the QR, whatever the owner types, in
 * any language — a rule checked on the emulator for every design.
 *
 * The logo sits on a small white tile so a dark or transparent logo stays
 * readable on a dark card, the way printed cards carry a logo badge.
 */
object CardLogo {

    /** Kept at least this far from the card's edge — the print safe zone (see CardPdf). */
    const val EDGE = 60f

    /** Tile sizes tried, largest first. 150 px of a 700 px card is ~0.43 inch printed. */
    private val SIZES = floatArrayOf(150f, 128f, 108f)

    private const val STEP = 12f

    /** A shape larger than this share of the card is background, not a feature to avoid. */
    private const val BACKGROUND_SHARE = 0.18f

    /** What a card's layout is made of, as drawn. */
    class Layout(val text: List<RectF>, val shapes: List<RectF>, val qr: RectF?)

    /**
     * A canvas that paints nothing and records where things would be drawn,
     * in card coordinates (a rotated line is recorded where it really lands).
     */
    class Recorder : Canvas() {
        val text = ArrayList<RectF>()
        val shapes = ArrayList<RectF>()
        var qr: RectF? = null

        /** Set while the QR's own modules are drawn: one tile, not a thousand dots. */
        var muted = false

        private val m = Matrix()

        private fun mapped(r: RectF): RectF {
            @Suppress("DEPRECATION")
            getMatrix(m)
            val out = RectF(r)
            m.mapRect(out)
            return out
        }

        fun markQr(r: RectF) {
            qr = mapped(r)
        }

        private fun shape(r: RectF) {
            if (muted) return
            shapes += mapped(RectF(min(r.left, r.right), min(r.top, r.bottom),
                maxOf(r.left, r.right), maxOf(r.top, r.bottom)))
        }

        private fun words(s: CharSequence, x: Float, y: Float, p: Paint) {
            if (muted || s.isEmpty()) return
            val w = p.measureText(s, 0, s.length)
            val left = when (p.textAlign) {
                Paint.Align.CENTER -> x - w / 2
                Paint.Align.RIGHT -> x - w
                else -> x
            }
            val fm = p.fontMetrics
            // Room either side for the row's icon, which is drawn as a shape.
            val pad = min(p.textSize * 1.4f, 60f)
            val v = min(p.textSize * 0.25f, 20f)
            text += mapped(RectF(left - pad, y + fm.ascent - v, left + w + pad, y + fm.descent + v))
        }

        override fun drawText(text: String, x: Float, y: Float, paint: Paint) = words(text, x, y, paint)
        override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) =
            words(text.substring(start, end), x, y, paint)
        override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) =
            words(text.subSequence(start, end), x, y, paint)
        override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) =
            words(String(text, index, count), x, y, paint)

        override fun drawRect(rect: RectF, paint: Paint) = shape(rect)
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
            shape(RectF(left, top, right, bottom))
        override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) = shape(rect)
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) =
            shape(RectF(left, top, right, bottom))
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) =
            shape(RectF(cx - radius, cy - radius, cx + radius, cy + radius))
        override fun drawOval(oval: RectF, paint: Paint) = shape(oval)
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) =
            shape(RectF(left, top, right, bottom))
        override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint) = shape(oval)
        override fun drawArc(
            left: Float, top: Float, right: Float, bottom: Float,
            startAngle: Float, sweepAngle: Float, useCenter: Boolean, paint: Paint
        ) = shape(RectF(left, top, right, bottom))
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            val half = paint.strokeWidth / 2
            shape(RectF(min(startX, stopX) - half, min(startY, stopY) - half,
                maxOf(startX, stopX) + half, maxOf(startY, stopY) + half))
        }
        override fun drawPath(path: Path, paint: Paint) {
            val r = RectF()
            @Suppress("DEPRECATION")
            path.computeBounds(r, true)
            shape(r)
        }
    }

    /** Where everything on this card lands, drawn at rest. */
    fun layout(body: (Canvas, CardTemplates.CardData, Int, Int, Float) -> Unit,
               d: CardTemplates.CardData, w: Int, h: Int): Layout {
        val rec = Recorder()
        body(rec, d.copy(logo = null), w, h, -1f)
        val area = w.toFloat() * h
        val features = rec.shapes.filter { it.width() * it.height() < area * BACKGROUND_SHARE }
        return Layout(rec.text, features, rec.qr)
    }

    /** The logo's tile on this card, or null when there is no safe room for it. */
    fun slot(layout: Layout, w: Int, h: Int): RectF? {
        val hard = layout.text + listOfNotNull(layout.qr)
        for (avoidShapes in booleanArrayOf(true, false)) {
            val blocked = if (avoidShapes) hard + layout.shapes else hard
            for (size in SIZES) {
                find(blocked, size, w, h)?.let { return it }
            }
        }
        return null
    }

    /** The free tile of [size] nearest a corner, or null. */
    private fun find(blocked: List<RectF>, size: Float, w: Int, h: Int): RectF? {
        var best: RectF? = null
        var bestScore = Float.MAX_VALUE
        val gap = 14f
        var y = EDGE
        while (y + size <= h - EDGE) {
            var x = EDGE
            while (x + size <= w - EDGE) {
                val tile = RectF(x, y, x + size, y + size)
                val score = min(min(x + y, (w - x - size) + y), min(x + (h - y - size), (w - x - size) + (h - y - size)))
                if (score < bestScore) {
                    val padded = RectF(tile.left - gap, tile.top - gap, tile.right + gap, tile.bottom + gap)
                    if (blocked.none { RectF.intersects(it, padded) }) {
                        best = tile
                        bestScore = score
                    }
                }
                x += STEP
            }
            y += STEP
        }
        return best
    }

    // One card's slot is reused for every frame of the preview; typing a letter
    // changes the data and so recomputes it.
    private var cacheKey: Any? = null
    private var cacheSlot: RectF? = null

    /** Draw [logo] on the card in its safe slot, if it has one. */
    fun draw(
        c: Canvas,
        templateId: Int,
        body: (Canvas, CardTemplates.CardData, Int, Int, Float) -> Unit,
        d: CardTemplates.CardData,
        logo: Bitmap,
        w: Int,
        h: Int
    ) {
        if (c is Recorder || logo.isRecycled) return
        val key = listOf(templateId, w, h, d.copy(logo = null))
        val slot = synchronized(this) {
            if (key != cacheKey) {
                cacheSlot = slot(layout(body, d, w, h), w, h)
                cacheKey = key
            }
            cacheSlot
        } ?: return
        paintTile(c, logo, slot)
    }

    private fun paintTile(c: Canvas, logo: Bitmap, r: RectF) {
        val radius = r.width() * 0.16f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(34, 0, 0, 0) }
        c.drawRoundRect(RectF(r.left, r.top + 4f, r.right, r.bottom + 4f), radius, radius, shadow)
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(40, 0, 0, 0)
        })
        // Fit the logo inside the tile, keeping its shape.
        val inner = r.width() * 0.8f
        val scale = min(inner / logo.width, inner / logo.height)
        val lw = logo.width * scale
        val lh = logo.height * scale
        val dst = RectF(r.centerX() - lw / 2, r.centerY() - lh / 2, r.centerX() + lw / 2, r.centerY() + lh / 2)
        c.save()
        val clip = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }
        c.clipPath(clip)
        c.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        c.restore()
    }
}
