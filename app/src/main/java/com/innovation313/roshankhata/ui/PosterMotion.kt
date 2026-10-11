package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.provider.Settings
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
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

    data class Spec(
        val stars: List<Star> = emptyList(),
        val glint: Glint? = null,
        val neon: Neon? = null,
        val aurora: Aurora? = null
    ) {
        val moves get() = stars.isNotEmpty() || glint != null || neon != null || aurora != null
    }

    /** Draw [spec] at [t] seconds on [c], already in poster pixels. */
    fun draw(c: Canvas, spec: Spec, t: Float, cache: Cache) {
        spec.aurora?.let { aurora(c, it, t) }
        spec.neon?.let { neon(c, it, t, cache) }
        if (spec.stars.isNotEmpty()) stars(c, spec.stars, t)
        spec.glint?.let { glint(c, it, t) }
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
        fun clear() { neonGlow?.recycle(); neonGlow = null; neonFor = null }
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
        if (android.os.Build.VERSION.SDK_INT >= 26) c.clipOutRect(r)
        else c.clipRect(r, android.graphics.Region.Op.DIFFERENCE)
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

/**
 * The view the live preview lays over the poster for [PosterMotion]. It
 * keeps redrawing only while it is shown and the phone's animations are on.
 */
class PosterMotionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var spec = PosterMotion.Spec()
    private val cache = PosterMotion.Cache()
    private val start = SystemClock.uptimeMillis()
    private val animate = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } catch (e: Exception) {
        true
    }

    fun show(s: PosterMotion.Spec) {
        spec = s
        cache.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!spec.moves || width == 0 || !animate) return
        val k = width / PosterKit.W
        canvas.save()
        canvas.scale(k, k)
        PosterMotion.draw(canvas, spec, (SystemClock.uptimeMillis() - start) / 1000f, cache)
        canvas.restore()
        if (isShown) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        cache.clear()
        super.onDetachedFromWindow()
    }
}
