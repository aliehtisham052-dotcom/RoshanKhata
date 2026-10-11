package com.innovation313.roshankhata.ui.poster3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.innovation313.roshankhata.data.InvoiceFonts
import com.innovation313.roshankhata.ui.PosterKit
import kotlin.math.min

/**
 * What is pressed into the Thank-you poster's wax seal: the shop's logo, or
 * its initials in gold leaf, inside a gold ring.
 *
 * Two square maps for the seal's face material (thanks.glb, "seal_face"):
 * its colour, and its metal and roughness (glTF packs roughness in green and
 * metal in blue), so the gold is real metal on glossy wax. The texture spans
 * the seal's outline; its centre is the seal's centre (checked with a test
 * image in Filament's own viewer, 11 Oct: upright, not mirrored).
 */
object SealFace {

    data class Maps(val colour: Bitmap, val metalRough: Bitmap) {
        fun recycle() { colour.recycle(); metalRough.recycle() }
    }

    private const val SIZE = 512
    private const val WAX = 0xFF0C462C.toInt()
    private const val GOLD = 0xFFE9BE64.toInt()
    /** Glossy wax: roughness 0.26, no metal. */
    private const val WAX_MR = 0xFF004200.toInt()
    /** Gold leaf: roughness 0.3, all metal. */
    private const val GOLD_MR = 0xFF004DFF.toInt()

    fun draw(context: Context, shopName: String, logo: Bitmap?): Maps {
        val colour = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val metal = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(colour)
        val m = Canvas(metal)
        c.drawColor(WAX)
        m.drawColor(WAX_MR)
        val cx = SIZE / 2f
        val cy = SIZE / 2f
        // Two gold rings just inside the pressed rim.
        for ((canvas, col) in listOf(c to GOLD, m to GOLD_MR)) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = col }
            ring.strokeWidth = 7f; canvas.drawCircle(cx, cy, 160f, ring)
            ring.strokeWidth = 2.5f; canvas.drawCircle(cx, cy, 146f, ring)
        }
        if (logo != null) {
            val r = 128f
            val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            c.save(); c.clipPath(clip)
            val s = (2 * r) / min(logo.width, logo.height)
            val w = logo.width * s
            val h = logo.height * s
            c.drawBitmap(logo, null, RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        } else {
            val initials = PosterKit.initials(shopName)
            val arabic = PosterKit.hasArabic(initials)
            for ((canvas, col) in listOf(c to GOLD, m to GOLD_MR)) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = if (arabic) InvoiceFonts.bold(InvoiceFonts.nastaliq(context))
                        else Typeface.create(InvoiceFonts.playfairDisplay(context), Typeface.BOLD)
                    color = col
                    textAlign = Paint.Align.CENTER
                    textSize = if (arabic) 120f else 150f
                }
                while (p.measureText(initials) > 230f && p.textSize > 40f) p.textSize -= 4f
                val b = Rect()
                p.getTextBounds(initials, 0, initials.length, b)
                canvas.drawText(initials, cx, cy - (b.top + b.bottom) / 2f, p)
            }
        }
        return Maps(colour, metal)
    }
}
