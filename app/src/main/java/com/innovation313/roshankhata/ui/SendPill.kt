package com.innovation313.roshankhata.ui

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.transition.ChangeBounds
import android.transition.TransitionManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.innovation313.roshankhata.R

/**
 * A pill button that turns into what it did (3 Nov 2026, the owner: "professional karo"): Follow-up's
 * Message pill, whose gold plane is its icon and flies out of it (Motion.paperPlane), and which reads
 * "Opened today" with a check that draws itself once WhatsApp has really opened.
 *
 * [message] and [opened] bind a row at rest; [sending], [openedNow] and [back] are the moment itself:
 * the press, the words fading as the plane leaves, the new words and check as WhatsApp opens, or the
 * Message words coming back when WhatsApp could not open. With animations off the states come at once.
 */
object SendPill {

    private val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)

    /** The words for a pill that did its job: followup_reminded_today without its "✓", the check is drawn instead. */
    private fun openedWords(button: MaterialButton): String =
        button.context.getString(R.string.followup_reminded_today).trimEnd().removeSuffix("✓").trimEnd()

    private fun ink(button: MaterialButton) = ContextCompat.getColor(button.context, R.color.green_got_text)

    @Suppress("UNCHECKED_CAST")
    private fun cancel(button: MaterialButton) {
        (button.getTag(R.id.motion_pill) as? MutableList<Animator>)?.forEach { it.cancel() }
        button.setTag(R.id.motion_pill, null)
        button.animate().cancel()
        button.scaleX = 1f
        button.scaleY = 1f
    }

    @Suppress("UNCHECKED_CAST")
    private fun keep(button: MaterialButton, animator: Animator) {
        val list = (button.getTag(R.id.motion_pill) as? MutableList<Animator>) ?: mutableListOf<Animator>().also {
            button.setTag(R.id.motion_pill, it)
        }
        list.add(animator)
    }

    /** "Message" with the plane: a customer not reminded today. */
    fun message(button: MaterialButton) {
        cancel(button)
        button.text = button.context.getString(R.string.promo_send)
        button.iconTint = null
        button.icon = ContextCompat.getDrawable(button.context, R.drawable.ic_paper_plane_gold)?.mutate()
        button.icon?.alpha = 255
        button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        button.setTextColor(ink(button))
    }

    /** "Opened today" with the check, on the soft green: a customer already reminded today. */
    fun opened(button: MaterialButton) {
        cancel(button)
        button.text = openedWords(button)
        button.iconTint = null
        button.icon = ContextCompat.getDrawable(button.context, R.drawable.ic_check_stroke)?.mutate()
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(button.context, R.color.pill_done_bg))
        button.setTextColor(ink(button))
    }

    /** The tap: pressed in and back, the plane leaves the pill (Motion draws it), the words fade. */
    fun sending(button: MaterialButton) {
        cancel(button)
        if (!Motion.enabled(button.context)) return
        button.icon?.alpha = 0
        button.animate().scaleX(0.96f).scaleY(0.96f).setDuration(60).setInterpolator(STANDARD).withEndAction {
            button.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(STANDARD).start()
        }.start()
        val base = ink(button)
        ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = 130
            duration = 100
            interpolator = STANDARD
            addUpdateListener { button.setTextColor(ColorUtils.setAlphaComponent(base, (255 * (it.animatedValue as Float)).toInt())) }
            keep(button, this)
            start()
        }
    }

    /** WhatsApp has opened: the pill grows into "Opened today", the soft green comes in, the check draws. */
    fun openedNow(button: MaterialButton) {
        if (!Motion.enabled(button.context)) { opened(button); return }
        cancel(button)
        (button.parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it, ChangeBounds().setDuration(180)) }
        button.text = openedWords(button)
        button.iconTint = null
        val check = ContextCompat.getDrawable(button.context, R.drawable.avd_pill_check)?.mutate()
        button.icon = check
        (button.icon as? Animatable)?.start()
        val base = ink(button)
        val fill = ContextCompat.getColor(button.context, R.color.pill_done_bg)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160
            interpolator = STANDARD
            val argb = ArgbEvaluator()
            addUpdateListener {
                val k = it.animatedValue as Float
                button.setTextColor(ColorUtils.setAlphaComponent(base, (255 * k).toInt()))
                button.backgroundTintList = ColorStateList.valueOf(argb.evaluate(k, Color.TRANSPARENT, fill) as Int)
            }
            keep(button, this)
            start()
        }
    }

    /** WhatsApp could not open: the plane comes back to the pill and the words return. */
    fun back(button: MaterialButton) {
        cancel(button)
        button.icon?.alpha = 255
        button.setTextColor(ink(button))
    }
}
