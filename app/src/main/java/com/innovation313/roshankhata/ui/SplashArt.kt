package com.innovation313.roshankhata.ui

import android.graphics.Matrix
import android.widget.ImageView

/**
 * The splash painting placed at the top of a first-run screen (9 Oct).
 *
 * The language screen and the welcome screen show the same picture the
 * splash does, so the first screens read as one picture with a panel that
 * changes under it. Each screen's panel is a different height, so the
 * painting is placed by its TAGLINE, not by its top: scaled and moved so
 * that "Har Hisaab Roshan" ends [gapDp] above the bottom of the region on
 * every screen. The painting is never narrower than the region; on a short
 * region the width sets the scale and the top (the gold arc) is cropped,
 * never the words.
 */
object SplashArt {

    /** splash_art.webp: its size, and how far down it the tagline ends. */
    private const val ART_W = 887f
    private const val ART_H = 1774f
    private const val TAGLINE_OF_ART = 0.62f

    /** Re-place whenever the region changes size. Call once from onCreate. */
    fun anchor(view: ImageView, gapDp: Float) {
        view.addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) place(v as ImageView, gapDp)
        }
    }

    fun place(view: ImageView, gapDp: Float) {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val gap = gapDp * view.resources.displayMetrics.density
        val scale = maxOf(w / ART_W, (h - gap) / (TAGLINE_OF_ART * ART_H))
        val drawnW = ART_W * scale
        val drawnH = ART_H * scale
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate((w - drawnW) / 2f, (h - gap) - TAGLINE_OF_ART * drawnH)
        view.imageMatrix = matrix
    }
}
