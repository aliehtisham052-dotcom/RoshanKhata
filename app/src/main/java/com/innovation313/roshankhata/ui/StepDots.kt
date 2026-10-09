package com.innovation313.roshankhata.ui

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.innovation313.roshankhata.R

/**
 * "Step 2 of 3" and three dots, on each first-run screen (9 Oct).
 *
 * Language, backup and shop type are one setup in three steps. Without a
 * marker each looked like a separate interruption, and the owner could not
 * tell how many more were coming. The current step is a short green bar,
 * the others small dots - the pattern every onboarding uses.
 *
 * Colours are fixed: the first-run screens are drawn on the light painting
 * by day and by night alike.
 */
object StepDots {

    const val TOTAL = 3

    fun show(dots: LinearLayout, label: TextView?, step: Int) {
        val d = dots.resources.displayMetrics.density
        dots.removeAllViews()
        for (i in 1..TOTAL) {
            val on = i == step
            val v = View(dots.context)
            v.background = GradientDrawable().apply {
                cornerRadius = 3 * d
                setColor(if (on) 0xFF1B5E3A.toInt() else 0xFFC9D8CE.toInt())
            }
            v.layoutParams = LinearLayout.LayoutParams(
                ((if (on) 18 else 6) * d).toInt(), (6 * d).toInt()
            ).apply {
                marginStart = (3 * d).toInt()
                marginEnd = (3 * d).toInt()
            }
            dots.addView(v)
        }
        dots.contentDescription = label(dots, step)
        dots.importantForAccessibility = if (label != null) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
        dots.visibility = View.VISIBLE
        label?.apply {
            text = label(this, step)
            visibility = View.VISIBLE
        }
    }

    private fun label(v: View, step: Int): String =
        v.context.getString(R.string.onboard_step, step.toString(), TOTAL.toString())
}
