package com.innovation313.roshankhata.ui.poster3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.InvoiceFonts
import com.innovation313.roshankhata.ui.PosterKit
import java.util.Locale

/**
 * What is struck on the Offer poster's gold coin (offer.glb, "coin_face"):
 * the discount, "20%", over the word for it in the poster's language
 * ("رعایت", "OFF"), in deep red on gold, inside a dotted ring.
 *
 * Two maps as for the seal (see SealFace): colour, and metal-roughness —
 * the gold is part metal so it keeps its colour in the dark studio; the
 * lettering is matt so it reads.
 */
object CoinFace {

    private const val SIZE = 512
    private const val INK = 0xFF7A1414.toInt()
    /**
     * Gold: roughness 0.4, metal 0.3. More metal (0.6 at first) mirrored the
     * dark studio and the face came out olive on CI (12 Oct).
     */
    private const val GOLD_MR = 0xFF00664D.toInt()
    /** Lettering: roughness 0.5, no metal. */
    private const val INK_MR = 0xFF008000.toInt()

    /** [context] speaks the poster's language. Figures are 0–9, as everywhere. */
    fun draw(context: Context, percent: Int): SealFace.Maps {
        val colour = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val metal = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(colour)
        val m = Canvas(metal)
        val gold = Paint(Paint.ANTI_ALIAS_FLAG)
        gold.shader = RadialGradient(SIZE * 0.4f, SIZE * 0.35f, SIZE * 0.75f,
            intArrayOf(0xFFFFF1C1.toInt(), 0xFFF3CF6A.toInt(), 0xFFC8962B.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), gold)
        m.drawColor(GOLD_MR)
        val cx = SIZE / 2f
        val cy = SIZE / 2f
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val urdu = PosterKit.urduScript(locale)
        for ((canvas, col) in listOf(c to INK, m to INK_MR)) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 4f; color = col
                pathEffect = DashPathEffect(floatArrayOf(10f, 9f), 0f)
                alpha = if (col == INK) 150 else 255
            }
            canvas.drawCircle(cx, cy, 214f, ring)
            val figure = "$percent%"
            val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(InvoiceFonts.sora(context), Typeface.BOLD)
                textSize = 170f; color = col; textAlign = Paint.Align.CENTER
            }
            while (fp.measureText(figure) > 360f && fp.textSize > 60f) fp.textSize -= 6f
            val b = Rect()
            fp.getTextBounds(figure, 0, figure.length, b)
            canvas.drawText(figure, cx, cy + 10f - (b.top + b.bottom) / 2f - 40f, fp)
            val word = context.getString(R.string.poster_off_word)
            val wp = PosterKit.greetingPaint(context, word, if (urdu) 92f else 64f, col, locale, urdu)
            if (!PosterKit.hasArabic(word)) wp.typeface = Typeface.create(InvoiceFonts.sora(context), Typeface.BOLD)
            wp.textAlign = Paint.Align.CENTER
            if (!PosterKit.hasArabic(word)) wp.letterSpacing = 0.2f
            PosterKit.fitWidth(wp, word, 300f, 24f)
            PosterKit.inkLine(canvas, word, wp, cx, cy + 112f)
        }
        return SealFace.Maps(colour, metal)
    }
}
