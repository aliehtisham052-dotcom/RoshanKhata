package com.innovation313.roshankhata

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable

/**
 * Measures what the owner sees: text, icons and outlines against the colour
 * really drawn behind them. Shared by EveryScreenOpensTest (whole screens,
 * with data) and EveryLayoutReadableInDarkTest (every layout file, dialogs
 * and list rows included).
 */
object ContrastCheck {

    /**
     * Walks the screen and records every visible, enabled text whose colour
     * is too close to the colour really drawn behind it (contrast below 3:1,
     * the level at which text stops being comfortably readable).
     */
    fun text(v: View?, page: Int, out: MutableList<String>) {
        if (v == null || !v.isShown || v.alpha < 0.5f) return
        if (v is TextView && v.isEnabled && v.text.isNotBlank() && v !is EditText) {
            val bg = backgroundBehind(v, page)
            val fg = ColorUtils.compositeColors(v.currentTextColor, bg)
            val ratio = ColorUtils.calculateContrast(fg, bg)
            if (ratio < 3.0) {
                val id = if (v.id != View.NO_ID) v.resources.getResourceEntryName(v.id) else "?"
                out += "'${v.text.toString().take(24)}' ($id) %.1f:1 on #%06X".format(ratio, bg and 0xFFFFFF)
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) text(v.getChildAt(i), page, out)
    }

    /** The first solid colour found behind [v], walking up its parents. */
    fun backgroundBehind(v: View, page: Int): Int {
        var p: View? = v
        while (p != null) {
            solidColourOf(p)?.let { c ->
                if (Color.alpha(c) >= 200) return ColorUtils.compositeColors(c, page)
            }
            p = p.parent as? View
        }
        return page
    }

    private fun solidColourOf(v: View): Int? {
        val state = v.drawableState
        if (v is MaterialCardView) return v.cardBackgroundColor.getColorForState(state, v.cardBackgroundColor.defaultColor)
        if (v is MaterialButton) v.backgroundTintList?.let { return it.getColorForState(state, it.defaultColor) }
        val bg = v.background ?: return null
        v.backgroundTintList?.let { return it.getColorForState(state, it.defaultColor) }
        return drawableColour(bg, state)
    }

    private fun drawableColour(d: Drawable, state: IntArray): Int? = when (d) {
        is ColorDrawable -> d.color
        // A gradient header: its average colour is what text sits on.
        is GradientDrawable -> d.color?.let { it.getColorForState(state, it.defaultColor) }
            ?: d.colors?.takeIf { it.isNotEmpty() }?.let { average(it) }
        is MaterialShapeDrawable -> d.fillColor?.let { it.getColorForState(state, it.defaultColor) }
        // A ripple (every tappable row) carries a white MASK layer that is
        // never drawn: it only bounds the ripple. Reading it as the colour
        // behind the text flagged every list row as "on #FFFFFF". Skip the
        // mask; a ripple with no other layer is transparent, so the search
        // carries on up to the parent.
        is RippleDrawable -> (d.numberOfLayers - 1 downTo 0)
            .filter { d.getId(it) != android.R.id.mask }
            .filter { d.getLayerHeight(it) <= 0 && d.getLayerWidth(it) <= 0 }
            .firstNotNullOfOrNull { drawableColour(d.getDrawable(it), state) }
        // Top-most layer that covers the whole area. A layer with its own
        // height or width is decoration (the 2dp gold rule under the Home
        // header), not the colour behind the text.
        is LayerDrawable -> (d.numberOfLayers - 1 downTo 0)
            .filter { d.getLayerHeight(it) <= 0 && d.getLayerWidth(it) <= 0 }
            .firstNotNullOfOrNull { drawableColour(d.getDrawable(it), state) }
        is InsetDrawable -> d.drawable?.let { drawableColour(it, state) }
        else -> null
    }

    private fun average(colours: IntArray): Int = Color.rgb(
        colours.sumOf { Color.red(it) } / colours.size,
        colours.sumOf { Color.green(it) } / colours.size,
        colours.sumOf { Color.blue(it) } / colours.size
    )


    /**
     * Icons and outlines, which text-only checking missed: the owner found
     * the Add-entry chips' green text AND their borders gone in dark mode.
     * Icons need 3:1 like text; a component's outline needs 2:1 to be seen
     * at all (it sits next to readable text that already identifies it).
     */
    fun iconsAndBorders(v: View?, page: Int, out: MutableList<String>) {
        if (v == null || !v.isShown || v.alpha < 0.5f) return
        val id = if (v.id != View.NO_ID) v.resources.getResourceEntryName(v.id) else "?"
        if (v.isEnabled) {
            val bg = backgroundBehind(v, page)
            if (v is MaterialButton) {
                if (v.icon != null) v.iconTint?.let { t ->
                    val c = ColorUtils.compositeColors(t.getColorForState(v.drawableState, t.defaultColor), bg)
                    val r = ColorUtils.calculateContrast(c, bg)
                    if (r < 3.0) out += "icon of ($id) %.1f:1 on #%06X".format(r, bg and 0xFFFFFF)
                }
                if (v.strokeWidth > 0) v.strokeColor?.let { t ->
                    val outside = v.parent?.let { backgroundBehind(it as View, page) } ?: page
                    val c = ColorUtils.compositeColors(t.getColorForState(v.drawableState, t.defaultColor), outside)
                    val r = ColorUtils.calculateContrast(c, outside)
                    if (r < 2.0) out += "border of ($id) %.1f:1 on #%06X".format(r, outside and 0xFFFFFF)
                }
            } else if (v is ImageView && v.drawable != null) {
                v.imageTintList?.let { t ->
                    val c = ColorUtils.compositeColors(t.getColorForState(v.drawableState, t.defaultColor), bg)
                    val r = ColorUtils.calculateContrast(c, bg)
                    if (r < 3.0) out += "icon ($id) %.1f:1 on #%06X".format(r, bg and 0xFFFFFF)
                }
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) iconsAndBorders(v.getChildAt(i), page, out)
    }
}
