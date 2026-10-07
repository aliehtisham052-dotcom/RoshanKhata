package com.innovation313.roshankhata.ui

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * A colour for a customer's initials, chosen from the name (7 Oct).
 *
 * Every account used to wear the same stone-grey circle, so a list of forty
 * names was forty identical discs and the eye had nothing to land on. Each
 * name now gets one of eight hues — the same pale-square / deep-glyph pairs
 * the Home tiles use, repainted together at night — and the same name gets
 * the same hue every time it appears, on every phone, because it comes from
 * the name itself and not from the row's position.
 */
object AvatarHue {
    private val pairs = listOf(
        R.color.tile_khata_bg to R.color.fi_green_b,
        R.color.tile_bills_bg to R.color.fi_gold_b,
        R.color.tile_insights_bg to R.color.fi_blue_b,
        R.color.tile_stock_bg to R.color.fi_orange_b,
        R.color.tile_card_bg to R.color.fi_violet_b,
        R.color.tile_bin_bg to R.color.fi_red_b,
        R.color.tile_cheques_bg to R.color.fi_teal_b,
        R.color.tile_invoice_bg to R.color.fi_pink_b
    )

    /** Which of the eight pairs a name lands on; stable across runs. */
    fun indexOf(name: String): Int {
        var h = 0
        for (c in name.trim().lowercase()) h = (h * 31 + c.code) and 0x7fffffff
        return h % pairs.size
    }

    /** Paints the initials disc: tinted background, deep glyph. */
    fun paint(initials: TextView, name: String) {
        val (bg, fg) = pairs[indexOf(name)]
        initials.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(initials.context, bg))
        initials.setTextColor(ContextCompat.getColor(initials.context, fg))
    }
}
