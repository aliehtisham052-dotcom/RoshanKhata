package com.innovation313.roshankhata.ui.poster3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.innovation313.roshankhata.ui.PosterEmblems

/**
 * What every face of New stock's 3D crate wears (naya_maal.glb, material "crate"): the emerald face with its
 * gold frame, as store/poster3d/make_models.py drew it, and in the middle the shop's own trade glyph
 * ([PosterEmblems], the one the flat crate's plaque wears) instead of the farm sprout the model was made with —
 * a salon's or a restaurant's New stock poster showed a sprout (14 Nov 2026).
 *
 * Two maps as for the seal (see [SealFace]): colour, and metal-roughness (roughness in green, metal in blue).
 * Each of the cube's six faces carries the whole texture, upright, as the generator laid them out.
 */
object CrateFace {

    private const val SIZE = 512
    /** The face's lacquer: roughness 0.42, no metal (the generator's own value). */
    private const val FACE_MR = 0xFF006B00.toInt()
    /** Gold frame and glyph: roughness 0.22, all metal, as the generator's frame and sprout. */
    private const val GOLD_MR = 0xFF0038FF.toInt()
    private const val GOLD = 0xFFFFCD6E.toInt()

    /** [trade] is [com.innovation313.roshankhata.data.TradeVocab.key]; a key with no glyph draws the "other" shop's. */
    fun draw(trade: String): SealFace.Maps {
        val colour = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val metal = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(colour)
        val m = Canvas(metal)
        val lacquer = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, SIZE.toFloat(), 0xFF268C62.toInt(), 0xFF0A3422.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), lacquer)
        m.drawColor(FACE_MR)
        val inset = SIZE * 0.07f
        val stroke = SIZE * 0.025f
        for ((canvas, col) in listOf(c to GOLD, m to GOLD_MR)) {
            val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = col }
            canvas.drawRect(inset, inset, SIZE - inset, SIZE - inset, frame)
        }
        val cx = SIZE / 2f
        val cy = SIZE / 2f
        // The glyph in the flat crate's two golds; on the metal map in gold's own value, its light part at 40%
        // there too, so the light part is part metal as it is part gold.
        PosterEmblems.draw(c, trade, cx, cy, SIZE * 0.56f, 0xFFFFF1C1.toInt(), 0xFFD9A441.toInt())
        PosterEmblems.draw(m, trade, cx, cy, SIZE * 0.56f, GOLD_MR, GOLD_MR)
        return SealFace.Maps(colour, metal)
    }
}
