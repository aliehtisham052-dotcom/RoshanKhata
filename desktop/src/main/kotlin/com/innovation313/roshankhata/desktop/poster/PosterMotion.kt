// GENERATED from app/src/main/java/com/innovation313/roshankhata/ui/PosterMotion.kt
// by scripts/gen_desktop_poster.py. Do not edit: change the phone's file and re-run.
package com.innovation313.roshankhata.desktop.poster

import com.innovation313.roshankhata.desktop.invoice.Context
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.BlurMaskFilter
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.gfx.LinearGradient
import com.innovation313.roshankhata.desktop.gfx.Paint
import com.innovation313.roshankhata.desktop.gfx.PointF
import com.innovation313.roshankhata.desktop.gfx.RadialGradient
import com.innovation313.roshankhata.desktop.gfx.RectF
import com.innovation313.roshankhata.desktop.gfx.Shader
import com.innovation313.roshankhata.desktop.gfx.StaticLayout
import com.innovation313.roshankhata.desktop.gfx.TextPaint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The 2D half of a poster's animation, drawn over the picture in the live
 * preview (the 3D hero is the other half, see poster3d.HeroStage): stars that
 * twinkle, a glint that sweeps across gold lettering, a neon sign that hums
 * and now and then flickers, an aurora that drifts behind glass.
 *
 * Everything is in poster pixels (1080 × 1920) and scaled to the view. The
 * shared picture is the still poster underneath; nothing here is in it.
 * With the phone's animations turned off, nothing moves.
 */
object PosterMotion {

    data class Star(val x: Float, val y: Float, val r: Float)

    /** Gold lettering to glint: drawn again over itself with a moving band of light. */
    class Glint(
        val bounds: RectF,
        val text: String,
        val paint: TextPaint,
        /** For one line: x and baseline. For a wrapped [layout]: its left and top. */
        val x: Float,
        val y: Float,
        val layout: StaticLayout?
    )

    class Neon(val rect: RectF, val radius: Float, val colour: Int)

    class Aurora(val colours: IntArray, val keepClear: List<RectF>)

    /** A flag on a pole, waving: the cloth's rectangle; the pole is drawn with the poster. */
    class Flag(val rect: RectF)

    /** Where fireworks burst, in turn. */
    class Fireworks(val at: List<PointF>)

    data class Spec(
        val stars: List<Star> = emptyList(),
        val glint: Glint? = null,
        val neon: Neon? = null,
        val aurora: Aurora? = null,
        /** Gold, white and red paper falling over the whole poster (Offer). */
        val confetti: Boolean = false,
        /** Golden motes rising through this area (Jumu'ah). */
        val embers: RectF? = null,
        val fireworks: Fireworks? = null,
        val flag: Flag? = null
    ) {
        val moves get() = stars.isNotEmpty() || glint != null || neon != null || aurora != null ||
            confetti || embers != null || fireworks != null || flag != null
    }

    /** Draw [spec] at [t] seconds on [c], already in poster pixels. */
    fun draw(c: Canvas, spec: Spec, t: Float, cache: Cache) {
        spec.aurora?.let { aurora(c, it, t) }
        spec.fireworks?.let { fireworks(c, it, t) }
        spec.flag?.let { flag(c, it, t, cache) }
        spec.neon?.let { neon(c, it, t, cache) }
        spec.embers?.let { embers(c, it, t) }
        if (spec.stars.isNotEmpty()) stars(c, spec.stars, t)
        spec.glint?.let { glint(c, it, t) }
        if (spec.confetti) confetti(c, t)
    }

    /** A steady pseudo-random number in 0..1 for piece [i], kind [k]: the same every frame. */
    private fun rnd(i: Int, k: Int): Float {
        var x = i * 374761393 + k * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
    }

    private val piece = Paint(Paint.ANTI_ALIAS_FLAG)
    private val CONFETTI = intArrayOf(0xFFF3CF6A.toInt(), 0xFFFFF4D0.toInt(), 0xFFFFFFFF.toInt(), 0xFFE2B44C.toInt(), 0xFFFF8A80.toInt())

    /** Forty pieces of paper falling, turning and drifting, each on its own course. */
    private fun confetti(c: Canvas, t: Float) {
        for (i in 0 until 40) {
            val speed = 140f + 220f * rnd(i, 1)
            val y = ((rnd(i, 2) * (PosterKit.H + 200f) + t * speed) % (PosterKit.H + 200f)) - 100f
            val x = rnd(i, 3) * PosterKit.W + 40f * sin((t * (0.8f + rnd(i, 4)) + i) * 2 * PI).toFloat()
            val spin = (t * (90f + 180f * rnd(i, 5)) + 360f * rnd(i, 6)) % 360f
            val w = 10f + 10f * rnd(i, 7)
            // Turning about its own long axis: the paper narrows and widens.
            val h = (18f + 10f * rnd(i, 8)) * abs(cos((t * (1.5f + rnd(i, 9)) + i) * PI).toFloat()).coerceAtLeast(0.15f)
            piece.color = CONFETTI[i % CONFETTI.size]
            c.save(); c.rotate(spin, x, y); c.drawRect(x - w / 2, y - h / 2, x + w / 2, y + h / 2, piece); c.restore()
        }
    }

    private val ember = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Thirty golden motes rising slowly, swaying, glowing brighter then fading near the top. */
    private fun embers(c: Canvas, area: RectF, t: Float) {
        for (i in 0 until 30) {
            val speed = 40f + 60f * rnd(i, 1)
            val f = ((rnd(i, 2) * area.height() + t * speed) % area.height()) / area.height()
            val y = area.bottom - f * area.height()
            val x = area.left + rnd(i, 3) * area.width() + 18f * sin((t * 0.5f + rnd(i, 4) * 6f) * 2 * PI).toFloat()
            val r = 2.5f + 3.5f * rnd(i, 5)
            val life = sin(f * PI).toFloat()
            ember.shader = RadialGradient(x, y, r * 3f, intArrayOf(0xFFFFE9A8.toInt(), 0x00F3CF6A), null, Shader.TileMode.CLAMP)
            ember.alpha = (220 * life).toInt().coerceIn(0, 255)
            c.drawCircle(x, y, r * 3f, ember)
        }
    }

    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val FIRE = intArrayOf(0xFFFFFFFF.toInt(), 0xFF9EF0B8.toInt(), 0xFFF3CF6A.toInt())

    /** Bursts in turn, each 1.6 s: sparks fly out, slow, fall a little and fade. */
    private fun fireworks(c: Canvas, fw: Fireworks, t: Float) {
        val cycle = 1.6f
        for ((k, p) in fw.at.withIndex()) {
            val local = (t - k * 0.55f) % (cycle * fw.at.size.coerceAtLeast(1) * 0.75f)
            if (local < 0f || local > cycle) continue
            val f = local / cycle
            val reach = 150f * (1f - (1f - f) * (1f - f))
            spark.color = FIRE[k % FIRE.size]
            spark.alpha = (255 * (1f - f)).toInt()
            spark.strokeWidth = 5f
            for (i in 0 until 16) {
                val a = 2 * PI * i / 16 + k
                val x0 = p.x + (reach * 0.55f * cos(a)).toFloat()
                val y0 = p.y + (reach * 0.55f * sin(a)).toFloat() + 40f * f * f
                val x1 = p.x + (reach * cos(a)).toFloat()
                val y1 = p.y + (reach * sin(a)).toFloat() + 60f * f * f
                c.drawLine(x0, y0, x1, y1, spark)
            }
        }
    }

    /**
     * The flag waving: the cloth drawn on a bending mesh, a wave travelling
     * out from the pole that grows toward the fly, with light and shade
     * across its folds. At t = 0 it is the shape the still poster shows.
     */
    fun flag(c: Canvas, fl: Flag, t: Float, cache: Cache) {
        val r = fl.rect
        val cloth = cache.flag ?: PosterFlat.pakistanFlag(r.width().toInt(), r.height().toInt()).also { cache.flag = it }
        val cols = 24
        val rows = 8
        val verts = FloatArray((cols + 1) * (rows + 1) * 2)
        val colours = IntArray((cols + 1) * (rows + 1))
        var n = 0
        for (j in 0..rows) {
            for (i in 0..cols) {
                val u = i / cols.toFloat()
                val v = j / rows.toFloat()
                val phase = (u * 1.6f - t * 0.9f) * 2 * PI
                val amp = r.height() * 0.07f * u
                val dy = (sin(phase) * amp).toFloat() + r.height() * 0.04f * u * u
                val dx = (-cos(phase) * amp * 0.25f).toFloat() - r.width() * 0.02f * u
                verts[n * 2] = r.left + u * r.width() + dx
                verts[n * 2 + 1] = r.top + v * r.height() + dy
                // Lit where the cloth faces the light, darker in the folds.
                val shade = (0.82f + 0.18f * cos(phase - 0.6)).toFloat().coerceIn(0f, 1f)
                val g = (255 * shade).toInt()
                colours[n] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                n++
            }
        }
        c.drawBitmapMesh(cloth, cols, rows, verts, 0, colours, 0, null)
    }

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF4D0.toInt() }

    private fun stars(c: Canvas, stars: List<Star>, t: Float) {
        for ((i, s) in stars.withIndex()) {
            val period = 2.2f + (i % 5) * 0.45f
            val k = 0.5f + 0.5f * sin((t / period + i * 0.37f) * 2 * PI).toFloat()
            starPaint.alpha = (60 + 195 * k).toInt()
            c.drawCircle(s.x, s.y, s.r * (0.8f + 0.5f * k), starPaint)
            if (k > 0.75f) {
                // A brief four-point sparkle at the brightest moment.
                val len = s.r * 5f * (k - 0.75f) * 4f
                starPaint.strokeWidth = s.r * 0.45f
                c.drawLine(s.x - len, s.y, s.x + len, s.y, starPaint)
                c.drawLine(s.x, s.y - len, s.x, s.y + len, starPaint)
            }
        }
    }

    private val glintPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    /** Every 4.5 s a band of light crosses the lettering in 1.3 s. */
    private fun glint(c: Canvas, g: Glint, t: Float) {
        val cycle = 4.5f
        val run = 1.3f
        val at = t % cycle
        if (at > run) return
        val f = at / run
        val b = g.bounds
        val h = b.height()
        val x0 = b.left - h + f * (b.width() + h)
        val band = LinearGradient(x0, b.top, x0 + h * 0.9f, b.bottom,
            intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        // The band is clear except at its middle, so drawn as the same letters it
        // lights only the letters.
        if (g.layout != null) {
            val p = g.layout.paint
            val keep = p.shader
            p.shader = band
            c.save(); c.translate(g.x, g.y); g.layout.draw(c); c.restore()
            p.shader = keep
            return
        }
        glintPaint.set(g.paint)
        glintPaint.clearShadowLayer()
        glintPaint.shader = band
        c.drawText(g.text, g.x, g.y, glintPaint)
    }

    /** Pre-drawn glows, made once per spec (blur is costly to draw every frame). */
    class Cache {
        var neonGlow: Bitmap? = null
        var neonFor: Neon? = null
        var flag: Bitmap? = null
        fun clear() {
            neonGlow?.recycle(); neonGlow = null; neonFor = null
            flag?.recycle(); flag = null
        }
    }

    /**
     * The sign hums (its glow breathes) and, twice in every 7 s, stutters:
     * a quick off-on-off-on as a real tube does.
     */
    private fun neon(c: Canvas, n: Neon, t: Float, cache: Cache) {
        val pad = 120f
        if (cache.neonFor !== n) {
            cache.clear()
            val w = (n.rect.width() + 2 * pad).toInt()
            val h = (n.rect.height() + 2 * pad).toInt()
            val bmp = Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
            val bc = Canvas(bmp)
            bc.scale(0.5f, 0.5f)
            val r = RectF(pad, pad, pad + n.rect.width(), pad + n.rect.height())
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 34f; color = n.colour
                maskFilter = BlurMaskFilter(46f, BlurMaskFilter.Blur.NORMAL)
            }
            bc.drawRoundRect(r, n.radius, n.radius, glow)
            cache.neonGlow = bmp
            cache.neonFor = n
        }
        val cycle = t % 7f
        val off = (cycle in 2.0f..2.08f) || (cycle in 2.16f..2.22f) || (cycle in 5.4f..5.5f)
        val src = cache.neonGlow ?: return
        val dst = RectF(n.rect.left - pad, n.rect.top - pad, n.rect.right + pad, n.rect.bottom + pad)
        if (off) {
            val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB0140A0B.toInt() }
            c.drawRoundRect(RectF(n.rect.left - 14f, n.rect.top - 14f, n.rect.right + 14f, n.rect.bottom + 14f),
                n.radius + 14f, n.radius + 14f, dim)
            return
        }
        val hum = 0.55f + 0.45f * (0.5f + 0.5f * sin(t * 2 * PI / 2.4).toFloat())
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (hum * 255).toInt() }
        c.drawBitmap(src, null, dst, p)
    }

    private val blob = Paint(Paint.ANTI_ALIAS_FLAG)

    @Suppress("DEPRECATION")
    private fun clipOut(c: Canvas, r: RectF) {
        c.clipOutRect(r)
    }

    /** Three slow lights drifting behind the glass. */
    private fun aurora(c: Canvas, a: Aurora, t: Float) {
        c.save()
        for (r in a.keepClear) clipOut(c, r)
        val centres = listOf(
            Triple(200f + 160f * sin(t * 2 * PI / 13).toFloat(), 260f + 120f * cos(t * 2 * PI / 17).toFloat(), 620f),
            Triple(980f + 90f * cos(t * 2 * PI / 11).toFloat(), 900f + 160f * sin(t * 2 * PI / 15).toFloat(), 560f),
            Triple(260f + 140f * cos(t * 2 * PI / 19).toFloat(), 1700f + 90f * sin(t * 2 * PI / 12).toFloat(), 600f)
        )
        for ((i, ctr) in centres.withIndex()) {
            val col = a.colours[i % a.colours.size]
            val alpha = (0x38 * (0.6f + 0.4f * abs(sin(t * 2 * PI / (9 + i * 3)).toFloat()))).toInt()
            val from = (col and 0x00FFFFFF) or (alpha shl 24)
            blob.shader = RadialGradient(ctr.first, ctr.second, ctr.third, from, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(ctr.first, ctr.second, ctr.third, blob)
        }
        c.restore()
    }
}

