package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * The heroes drawn flat, for a phone that cannot run the 3D renderer (or the
 * emulator's old GL translator, see poster3d.Studio). Same places and
 * proportions as the 3D models, so the layout is the same either way.
 */
object PosterFlat {

    private fun metal(x0: Float, x1: Float): Shader =
        LinearGradient(x0, 0f, x1, 0f, intArrayOf(0xFF8A5E14.toInt(), 0xFFFFE6A0.toInt(), 0xFF9A6A16.toInt()),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)

    /** A gold crescent opening to the upper right, outer radius [r]. */
    fun crescent(c: Canvas, cx: Float, cy: Float, r: Float) {
        val moon = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        val cut = Path().apply { addCircle(cx + r * 0.42f, cy - r * 0.2f, r * 0.84f, Path.Direction.CW) }
        moon.op(cut, Path.Op.DIFFERENCE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.6f,
            intArrayOf(0xFFFFF6CF.toInt(), 0xFFF3CF6A.toInt(), 0xFF9A6A16.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        p.setShadowLayer(r * 0.25f, 0f, r * 0.08f, 0x66000000)
        c.drawPath(moon, p)
    }

    /**
     * A hexagonal lantern hanging from its ring at ([x], [ringY]), [k] poster
     * pixels per unit of the mockup's 44 × 170 lantern; the chain runs to [chainTop].
     */
    fun lantern(c: Canvas, x: Float, ringY: Float, k: Float, chainTop: Float = 0f) {
        val ox = x - 22f * k
        val oy = ringY - 78f * k
        fun X(v: Float) = ox + v * k
        fun Y(v: Float) = oy + v * k
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9B25A.toInt(); strokeWidth = 1.2f * k }
        c.drawLine(x, chainTop, x, Y(74f), line)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * k; color = 0xFFE7C268.toInt() }
        c.drawCircle(X(22f), Y(78f), 4f * k, ring)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = metal(X(3f), X(41f)) }
        c.drawPath(Path().apply { moveTo(X(10f), Y(96f)); quadTo(X(10f), Y(84f), X(22f), Y(84f)); quadTo(X(34f), Y(84f), X(34f), Y(96f)); close() }, gold)
        c.drawRoundRect(RectF(X(7f), Y(96f), X(37f), Y(101f)), 2f * k, 2f * k, gold)
        val body = Path().apply {
            moveTo(X(8f), Y(101f)); lineTo(X(36f), Y(101f)); lineTo(X(41f), Y(126f))
            lineTo(X(36f), Y(152f)); lineTo(X(8f), Y(152f)); lineTo(X(3f), Y(126f)); close()
        }
        val fire = Paint(Paint.ANTI_ALIAS_FLAG)
        fire.shader = RadialGradient(X(22f), Y(126f), 26f * k,
            intArrayOf(0xFFFFE9A8.toInt(), 0xFFF5A623.toInt(), 0xFFB5651D.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(body, fire)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * k; shader = metal(X(3f), X(41f)) }
        c.drawPath(body, frame)
        val bars = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A5E14.toInt(); strokeWidth = 1f * k }
        for (bx in floatArrayOf(15f, 22f, 29f)) c.drawLine(X(bx), Y(102f), X(bx), Y(151f), bars)
        c.drawLine(X(3f), Y(126f), X(41f), Y(126f), bars)
        c.drawPath(Path().apply { moveTo(X(8f), Y(152f)); lineTo(X(36f), Y(152f)); lineTo(X(29f), Y(160f)); lineTo(X(15f), Y(160f)); close() }, gold)
        c.drawPath(Path().apply { moveTo(X(19f), Y(160f)); lineTo(X(25f), Y(160f)); lineTo(X(22f), Y(170f)); close() },
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7C268.toInt() })
    }

    /** A mosque, ivory walls under gold domes, centred on [cx], standing on [baseY], [h] tall. */
    fun mosque(c: Canvas, cx: Float, baseY: Float, h: Float) {
        val u = h / 1.9f
        val ivory = Paint(Paint.ANTI_ALIAS_FLAG)
        ivory.shader = LinearGradient(cx - u, 0f, cx + u, 0f, 0xFFFBF3E2.toInt(), 0xFFD9CBB0.toInt(), Shader.TileMode.CLAMP)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG)
        gold.shader = LinearGradient(cx - u, 0f, cx + u, 0f,
            intArrayOf(0xFF9A6A16.toInt(), 0xFFFFE6A0.toInt(), 0xFFC8962B.toInt(), 0xFF8A5E14.toInt()), null, Shader.TileMode.CLAMP)
        fun y(v: Float) = baseY - (v + 0.96f) * u
        c.drawRect(cx - 0.85f * u, y(-0.93f), cx + 0.85f * u, baseY, gold)
        c.drawRect(cx - 0.75f * u, y(-0.35f), cx + 0.75f * u, y(-0.93f), ivory)
        c.drawRect(cx - 0.36f * u, y(-0.19f), cx + 0.36f * u, y(-0.35f), ivory)
        val dome = Path().apply {
            moveTo(cx - 0.36f * u, y(-0.19f))
            cubicTo(cx - 0.52f * u, y(0.15f), cx - 0.2f * u, y(0.45f), cx, y(0.59f))
            cubicTo(cx + 0.2f * u, y(0.45f), cx + 0.52f * u, y(0.15f), cx + 0.36f * u, y(-0.19f))
            close()
        }
        c.drawPath(dome, gold)
        for (sx in floatArrayOf(-0.55f, 0.55f)) c.drawArc(RectF(cx + (sx - 0.16f) * u, y(-0.19f), cx + (sx + 0.16f) * u, y(-0.51f)), 180f, 180f, true, gold)
        c.drawRect(cx - 0.012f * u, y(0.76f), cx + 0.012f * u, y(0.56f), gold)
        crescent(c, cx, y(0.82f), 0.07f * u)
        for (sx in floatArrayOf(-0.92f, 0.92f)) {
            c.drawRect(cx + (sx - 0.07f) * u, y(0.62f), cx + (sx + 0.07f) * u, y(-0.93f), ivory)
            for (by in floatArrayOf(-0.15f, 0.42f)) c.drawRect(cx + (sx - 0.11f) * u, y(by + 0.025f), cx + (sx + 0.11f) * u, y(by - 0.025f), gold)
            c.drawPath(Path().apply { moveTo(cx + (sx - 0.085f) * u, y(0.62f)); lineTo(cx + sx * u, y(0.92f)); lineTo(cx + (sx + 0.085f) * u, y(0.62f)); close() }, gold)
        }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5A623.toInt() }
        for ((dx, w, hh, y0) in listOf(floatArrayOf(0f, 0.28f, 0.38f, -0.9f), floatArrayOf(-0.45f, 0.14f, 0.2f, -0.68f), floatArrayOf(0.45f, 0.14f, 0.2f, -0.68f))) {
            val r = RectF(cx + (dx - w / 2) * u, y(y0 + hh), cx + (dx + w / 2) * u, y(y0))
            c.drawPath(Path().apply {
                moveTo(r.left, r.bottom); lineTo(r.left, r.top + r.width() / 2)
                arcTo(RectF(r.left, r.top, r.right, r.top + r.width()), 180f, 180f)
                lineTo(r.right, r.bottom); close()
            }, glow)
        }
    }

    /** A gold clock reading 10:10, radius [r]. */
    fun clock(c: Canvas, cx: Float, cy: Float, r: Float) {
        val bezel = Paint(Paint.ANTI_ALIAS_FLAG)
        bezel.shader = android.graphics.SweepGradient(cx, cy,
            intArrayOf(0xFF8A5E14.toInt(), 0xFFFFF1C1.toInt(), 0xFFC8962B.toInt(), 0xFF8A5E14.toInt(), 0xFFF7DC8C.toInt(), 0xFF8A5E14.toInt()), null)
        bezel.setShadowLayer(r * 0.12f, 0f, r * 0.08f, 0x80000000.toInt())
        c.drawCircle(cx, cy, r, bezel)
        val face = Paint(Paint.ANTI_ALIAS_FLAG)
        face.shader = RadialGradient(cx - r * 0.25f, cy - r * 0.3f, r * 1.2f,
            intArrayOf(0xFF1F6B4A.toInt(), 0xFF0A2E1F.toInt(), 0xFF04140D.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.88f, face)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE2A0.toInt(); strokeCap = Paint.Cap.ROUND }
        for (k in 0 until 12) {
            val a = 2 * Math.PI * k / 12
            val long = k % 3 == 0
            tick.strokeWidth = r * (if (long) 0.045f else 0.028f)
            val r0 = r * (if (long) 0.62f else 0.7f)
            c.drawLine(cx + (r0 * sin(a)).toFloat(), cy - (r0 * cos(a)).toFloat(),
                cx + (r * 0.8f * sin(a)).toFloat(), cy - (r * 0.8f * cos(a)).toFloat(), tick)
        }
        fun hand(deg: Double, len: Float, w: Float, col: Int) {
            val a = Math.toRadians(deg)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; strokeWidth = w; strokeCap = Paint.Cap.ROUND }
            c.drawLine(cx, cy, cx + (len * sin(a)).toFloat(), cy - (len * cos(a)).toFloat(), p)
        }
        hand(305.0, r * 0.45f, r * 0.06f, 0xFFFFE2A0.toInt())
        hand(60.0, r * 0.66f, r * 0.04f, 0xFFFFE2A0.toInt())
        hand(216.0, r * 0.74f, r * 0.016f, 0xFFE86050.toInt())
        c.drawCircle(cx, cy, r * 0.055f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3CF6A.toInt() })
    }

    /** A green wax seal with the shop's mark, radius [r]. */
    fun seal(context: Context, c: Canvas, cx: Float, cy: Float, r: Float, name: String, logo: Bitmap?) {
        val wax = Path()
        for (i in 0..72) {
            val a = 2 * Math.PI * i / 72
            val rr = r * (1f + 0.035f * sin(a * 5 + 0.7).toFloat() + 0.02f * cos(a * 3).toFloat())
            val x = cx + (rr * cos(a)).toFloat()
            val y = cy + (rr * sin(a)).toFloat()
            if (i == 0) wax.moveTo(x, y) else wax.lineTo(x, y)
        }
        wax.close()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.4f,
            intArrayOf(0xFF2C8C62.toInt(), 0xFF0F4A33.toInt(), 0xFF072A1C.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        p.setShadowLayer(r * 0.18f, 0f, r * 0.12f, 0x73000000)
        c.drawPath(wax, p)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * 0.04f; color = 0xFFE9BE64.toInt() }
        c.drawCircle(cx, cy, r * 0.66f, ring)
        PosterKit.seal(context, c, cx, cy, r * 0.56f, name, logo, 0xFF15603F.toInt(), 0xFF0A3A26.toInt(), 0xFFE9BE64.toInt())
    }

    /**
     * Pakistan's flag, [w] × [h] (2 : 3): a white band of a quarter of the
     * length at the hoist, the dark green field, and the white crescent and
     * five-pointed star set on the field's diagonal from its lower hoist to
     * its upper fly — the national flag's own construction (outer circle
     * 3/10 of the height on the field's centre, the cutting circle 11/40,
     * the star in a circle of 1/10).
     */
    fun pakistanFlag(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(3), h.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val green = 0xFF01411C.toInt()
        c.drawColor(green)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        c.drawRect(0f, 0f, w / 4f, h.toFloat(), white)
        val fh = h.toFloat()
        val gx = w / 4f + (w * 3f / 4f) / 2f
        val gy = fh / 2f
        // The field's diagonal, toward its upper fly corner.
        val dxv = w * 3f / 4f
        val dyv = -fh
        val len = kotlin.math.sqrt(dxv * dxv + dyv * dyv)
        val ux = dxv / len
        val uy = dyv / len
        val moon = Path().apply { addCircle(gx, gy, 0.3f * fh, Path.Direction.CW) }
        val cut = Path().apply { addCircle(gx + ux * 0.0625f * fh, gy + uy * 0.0625f * fh, 0.275f * fh, Path.Direction.CW) }
        moon.op(cut, Path.Op.DIFFERENCE)
        c.drawPath(moon, white)
        val sx = gx + ux * 0.215f * fh
        val sy = gy + uy * 0.215f * fh
        val starR = 0.1f * fh
        val star = Path()
        // One point toward the upper fly corner, along the diagonal.
        val start = kotlin.math.atan2(uy.toDouble(), ux.toDouble())
        for (k in 0 until 10) {
            val rad = if (k % 2 == 0) starR else starR * 0.382f
            val a = start + Math.PI * k / 5
            val x = sx + (rad * cos(a)).toFloat()
            val y = sy + (rad * sin(a)).toFloat()
            if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        c.drawPath(star, white)
        return bmp
    }

    /** The flagpole: a gold pole with a ball on top, standing beside the cloth's hoist. */
    fun flagPole(c: Canvas, x: Float, top: Float, bottom: Float) {
        val pole = Paint(Paint.ANTI_ALIAS_FLAG)
        pole.shader = LinearGradient(x - 9f, 0f, x + 9f, 0f, intArrayOf(0xFF8A5E14.toInt(), 0xFFFFE6A0.toInt(), 0xFF9A6A16.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(x - 8f, top + 18f, x + 8f, bottom), 8f, 8f, pole)
        val ball = Paint(Paint.ANTI_ALIAS_FLAG)
        ball.shader = RadialGradient(x - 6f, top + 4f, 26f, intArrayOf(0xFFFFF4D0.toInt(), 0xFFE2B44C.toInt(), 0xFF8A5E14.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, top + 10f, 18f, ball)
    }

    /** A flat gold coin with the discount, for a phone without 3D. */
    fun coin(context: Context, c: Canvas, cx: Float, cy: Float, r: Float, percent: Int) {
        val rim = Paint(Paint.ANTI_ALIAS_FLAG)
        rim.shader = android.graphics.SweepGradient(cx, cy, intArrayOf(0xFF8A5E14.toInt(), 0xFFFFF1C1.toInt(), 0xFFC8962B.toInt(),
            0xFF8A5E14.toInt(), 0xFFF7DC8C.toInt(), 0xFF8A5E14.toInt()), null)
        rim.setShadowLayer(r * 0.12f, 0f, r * 0.08f, 0x80000000.toInt())
        c.drawCircle(cx, cy, r, rim)
        val face = com.innovation313.roshankhata.ui.poster3d.CoinFace.draw(context, percent)
        val inner = r * 0.87f
        val clip = Path().apply { addCircle(cx, cy, inner, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        c.drawBitmap(face.colour, null, RectF(cx - inner, cy - inner, cx + inner, cy + inner), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        c.restore()
        face.recycle()
    }
}
