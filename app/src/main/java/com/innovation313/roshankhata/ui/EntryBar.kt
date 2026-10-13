package com.innovation313.roshankhata.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.innovation313.roshankhata.R

/**
 * The bar that comes up after an entry is saved (13 Oct): "Send this to
 * Aslam on WhatsApp?" and "Rs 85 received in cash · Undo".
 *
 * The owner found the plain grey bar sat at the bottom too long (eight
 * seconds) and looked like an error. This one is a rounded dark-green card
 * lifted off the screen edge, with its own mark (WhatsApp, a tick), the
 * action in gold, and it goes by itself after [millis] — a few seconds,
 * long enough to tap, short enough not to be in the way of the next entry.
 */
object EntryBar {

    fun show(
        activity: Activity,
        text: CharSequence,
        @DrawableRes icon: Int,
        action: CharSequence,
        millis: Int,
        onAction: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val dp = activity.resources.displayMetrics.density
        val bar = Snackbar.make(root, text, millis).setAction(action) { onAction() }
        bar.setActionTextColor(ContextCompat.getColor(activity, R.color.gold_on_dark))
        bar.setTextColor(ContextCompat.getColor(activity, R.color.white))
        bar.setTextMaxLines(3)
        bar.animationMode = Snackbar.ANIMATION_MODE_SLIDE

        val v = bar.view
        v.background = GradientDrawable().apply {
            cornerRadius = 18 * dp
            setColor(ContextCompat.getColor(activity, R.color.brand_green_dark))
        }
        v.elevation = 10 * dp
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val side = (14 * dp).toInt()
            lp.setMargins(side, lp.topMargin, side, (16 * dp).toInt())
            v.layoutParams = lp
        }
        v.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
            ContextCompat.getDrawable(activity, icon)?.mutate()?.let { d ->
                val s = (22 * dp).toInt()
                d.setBounds(0, 0, s, s)
                d.setTint(ContextCompat.getColor(activity, R.color.white))
                setCompoundDrawablesRelative(d, null, null, null)
            }
            compoundDrawablePadding = (12 * dp).toInt()
            textSize = 14f
        }
        v.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)?.apply {
            isAllCaps = false
            letterSpacing = 0f
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        bar.show()
    }
}
