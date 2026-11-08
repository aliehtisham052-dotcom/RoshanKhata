// GENERATED from app/src/main/java/com/innovation313/roshankhata/ui/PosterGlyphs.kt
// by scripts/gen_desktop_poster.py. Do not edit: change the phone's file and re-run.
package com.innovation313.roshankhata.desktop.poster

import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.gfx.Matrix
import com.innovation313.roshankhata.desktop.gfx.Paint
import com.innovation313.roshankhata.desktop.gfx.Path
import com.innovation313.roshankhata.desktop.gfx.PathParser

/**
 * The poster's contact marks (12 Oct): call, chat, mail and map pin, filled.
 *
 * The owner found the hand-drawn handset "local". These are the standard
 * Material shapes (Apache 2.0, the icons Android itself uses), drawn as paths
 * so they stay sharp at 1080 x 1920 and in the video. The chat mark is a
 * plain round speech bubble with a handset inside: a generic sign for "write
 * on WhatsApp", not WhatsApp's own logo, which is its owner's trademark.
 */
object PosterGlyphs {

    private const val CALL = "M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 " +
        "1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 -9.39,0 -17,-7.61 -17,-17 " +
        "0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z"
    private const val MAIL = "M20,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 " +
        "-0.9,-2 -2,-2zM20,8l-8,5 -8,-5V6l8,5 8,-5v2z"
    private const val PIN = "M12,2C8.13,2 5,5.13 5,9c0,5.25 7,13 7,13s7,-7.75 7,-13c0,-3.87 -3.13,-7 -7,-7zM12,11.5" +
        "c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5 2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z"

    private val paths = HashMap<String, Path>()

    private fun path(data: String): Path = paths.getOrPut(data) { PathParser.createPathFromPathData(data) }

    /** One 24-unit icon, [size] pixels square, centred on (cx, cy). */
    private fun icon(c: Canvas, data: String, cx: Float, cy: Float, size: Float, colour: Int) {
        val p = Path(path(data))
        val m = Matrix()
        val s = size / 24f
        m.setScale(s, s)
        m.postTranslate(cx - size / 2, cy - size / 2)
        p.transform(m)
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = Paint.Style.FILL })
    }

    fun call(c: Canvas, cx: Float, cy: Float, size: Float, colour: Int) = icon(c, CALL, cx, cy, size, colour)

    fun mail(c: Canvas, cx: Float, cy: Float, size: Float, colour: Int) = icon(c, MAIL, cx, cy, size, colour)

    fun pin(c: Canvas, cx: Float, cy: Float, size: Float, colour: Int) = icon(c, PIN, cx, cy, size, colour)

    /** A round speech bubble, its tail at the lower left, with a small handset cut out of it in [inner]. */
    fun chat(c: Canvas, cx: Float, cy: Float, size: Float, colour: Int, inner: Int) {
        val r = size * 0.42f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
        c.drawCircle(cx, cy - size * 0.03f, r, fill)
        val tail = Path().apply {
            moveTo(cx - r * 0.62f, cy + r * 0.55f)
            lineTo(cx - r * 1.02f, cy + r * 1.08f)
            lineTo(cx - r * 0.08f, cy + r * 0.86f)
            close()
        }
        c.drawPath(tail, fill)
        icon(c, CALL, cx, cy - size * 0.03f, r * 1.15f, inner)
    }
}
