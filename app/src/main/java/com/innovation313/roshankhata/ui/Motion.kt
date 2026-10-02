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
