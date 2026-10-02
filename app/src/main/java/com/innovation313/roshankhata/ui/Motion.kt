package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.animation.DecelerateInterpolator
import android.widget.TextView

/**
 * The app's few animations, in one place (2 Oct). Every one of them is
 * feedback for something that happened — never decoration — and every one
 * stands down when the phone's animation scale is 0 ("Remove animations"):
 * the end state is shown at once instead.
 */
object Motion {

    /**
     * The app-wide rule for celebrations (2 Oct): delight wears thin, so a
     * celebration plays in full only its first [times] showings, then gives
     * way to a quiet confirmation. Counts per [key], on this phone.
     */
    fun firstTimes(context: Context, key: String, times: Int): Boolean {
        val prefs = context.getSharedPreferences("motion_counts", Context.MODE_PRIVATE)
        val shown = prefs.getInt(key, 0)
        prefs.edit().putInt(key, shown + 1).apply()
        return shown < times
    }

    /**
     * A small "it is done" tap felt in the hand (2 Oct): CONFIRM on Android
     * 11+, a key tap before. The phone's own touch-feedback setting decides
     * whether it is felt at all; no permission is involved.
     */
    fun confirm(view: android.view.View) {
        view.performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    /** "Saved ✓" for a moment in [view], then [after]; a fade, or nothing with animations off. */
    fun flashSaved(view: TextView, saved: CharSequence, after: () -> Unit) {
        if (!enabled(view.context)) { after(); return }
        view.animate().cancel()
        view.text = saved
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(250).withEndAction {
            view.postDelayed({
                view.animate().alpha(0f).setDuration(200).withEndAction {
                    after()
                    view.animate().alpha(1f).setDuration(200).start()
                }.start()
            }, 1800)
        }.start()
    }

    /** False when the owner turned animations off on the phone. */
    fun enabled(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }.getOrDefault(true)

    /**
     * A figure counting from [from] to [to] (Material: ~300 ms for a small
     * change; a little longer here because a number has to be read).
     */
    fun countUp(view: TextView, from: Double, to: Double, format: (Double) -> String) {
        (view.getTag(view.id) as? ValueAnimator)?.cancel()
        if (!enabled(view.context) || from == to) {
            view.text = format(to)
            return
        }
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                view.text = format(if (f >= 1f) to else from + (to - from) * f)
            }
        }
        view.setTag(view.id, anim)
        anim.start()
    }
}
