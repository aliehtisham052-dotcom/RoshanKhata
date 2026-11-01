package com.innovation313.roshankhata.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.innovation313.roshankhata.R

/**
 * "It is in" (1 Nov 2026, the owner's design "A"). The bare green disc that popped up in the
 * middle of the screen said nothing about WHAT was written and offered no way back; after it a
 * second bar asked about WhatsApp. This is one card instead, above the I gave / I got bar: the
 * gold-ringed tick drawing itself, a title, the line of what was written (way · amount · number),
 * and up to two actions - Undo and Send on WhatsApp. It goes by itself after [millis] or with a
 * swipe; TalkBack users get it until they move on (Snackbar's own rule).
 *
 * Above 120% text the actions go under the words, so the words keep the card's width
 * ([TextFit.stackWhenLarge], lesson 58).
 */
object SavedCard {

    fun show(
        activity: Activity,
        title: CharSequence,
        line: CharSequence? = null,
        /** The view the card sits above (the I gave / I got bar); null = the screen's foot. */
        anchor: View? = null,
        onUndo: (() -> Unit)? = null,
        onShare: (() -> Unit)? = null,
        millis: Int = 5000
    ): Snackbar? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val root = activity.findViewById<View>(android.R.id.content) ?: return null
        val dp = activity.resources.displayMetrics.density
        val bar = Snackbar.make(root, "", millis)
        bar.animationMode = Snackbar.ANIMATION_MODE_SLIDE
        val shell = bar.view as? ViewGroup ?: return null

        // Snackbar's own words and button give way to the card.
        for (i in 0 until shell.childCount) shell.getChildAt(i).visibility = View.GONE
        shell.background = ContextCompat.getDrawable(activity, R.drawable.bg_saved_card)
        shell.backgroundTintList = null
        shell.elevation = 10 * dp
        shell.setPadding(0, 0, 0, 0)
        (shell.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val side = (12 * dp).toInt()
            lp.setMargins(side, lp.topMargin, side, (10 * dp).toInt())
            shell.layoutParams = lp
        }

        val card = LayoutInflater.from(activity).inflate(R.layout.view_saved_card, shell, false)
        card.findViewById<TextView>(R.id.tvSavedTitle).text = title
        card.findViewById<TextView>(R.id.tvSavedLine).apply {
            if (line.isNullOrBlank()) visibility = View.GONE else { text = line; visibility = View.VISIBLE }
        }

        // The tick draws itself; with animations off it is simply there (the drawing starts empty).
        val tick = card.findViewById<ImageView>(R.id.ivSavedTick)
        if (Motion.enabled(activity)) {
            tick.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.avd_save_check)?.mutate())
        } else {
            tick.setImageResource(R.drawable.ic_check_plain)
            tick.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.white))
        }

        card.findViewById<ImageButton>(R.id.btnSavedShare).apply {
            if (onShare == null) visibility = View.GONE else {
                visibility = View.VISIBLE
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.brand_green_text))
                setOnClickListener { bar.dismiss(); onShare() }
            }
        }
        card.findViewById<MaterialButton>(R.id.btnSavedUndo).apply {
            if (onUndo == null) visibility = View.GONE else {
                visibility = View.VISIBLE
                setOnClickListener { bar.dismiss(); onUndo() }
            }
        }
        val actions = card.findViewById<ViewGroup>(R.id.savedActions)
        if (onShare == null && onUndo == null) actions.visibility = View.GONE
        else TextFit.stackWhenLarge(card.findViewById(R.id.savedText), actions)

        TextFit.relax(card)
        shell.addView(card)
        if (anchor != null && anchor.isShown) bar.anchorView = anchor
        bar.addCallback(object : Snackbar.Callback() {
            override fun onShown(sb: Snackbar?) {
                (tick.drawable as? Animatable)?.start()
            }
        })
        bar.show()
        return bar
    }
}
