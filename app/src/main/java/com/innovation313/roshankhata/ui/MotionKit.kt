package com.innovation313.roshankhata.ui

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.innovation313.roshankhata.R

/**
 * The small pieces the 3 Nov 2026 animations are made of (the owner: "implement all animations carefully"):
 * a mark that pops in, words that rise into place, a check that draws itself, a colour that eases.
 *
 * The rules of ui/Motion hold for every one of them: each is feedback for something that happened, never
 * decoration; nothing bounces; every one stands down when the phone's animations are off and shows its end
 * state at once. The curves and lengths are Material's own (material-components-android,
 * docs/theming/Motion.md): short 50-200 ms, medium 250-400 ms.
 */
object MotionKit {

    val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)
    val STANDARD_ACCELERATE = PathInterpolator(0.3f, 0f, 1f, 1f)

    /** A mark arriving: from [from] of its size and clear to whole, on the emphasized curve. */
    fun popIn(view: View, delay: Long = 0, duration: Long = 220, from: Float = 0.6f) {
        view.animate().cancel()
        if (!Motion.enabled(view.context)) {
            view.alpha = 1f; view.scaleX = 1f; view.scaleY = 1f
            return
        }
        view.alpha = 0f
        view.scaleX = from
        view.scaleY = from
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delay).setDuration(duration).setInterpolator(EMPHASIZED_DECELERATE).start()
    }

    /** Words arriving after their mark: a few dp up into place as they fade in. */
    fun riseIn(view: View, delay: Long = 0, duration: Long = 200, rise: Float = 6f) {
        view.animate().cancel()
        if (!Motion.enabled(view.context)) {
            view.alpha = 1f; view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = rise * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay(delay).setDuration(duration).setInterpolator(EMPHASIZED_DECELERATE).start()
    }

    /** Fades [view] in, or shows it at once. */
    fun fadeIn(view: View, delay: Long = 0, duration: Long = 160) {
        view.animate().cancel()
        if (!Motion.enabled(view.context)) { view.alpha = 1f; return }
        view.alpha = 0f
        view.animate().alpha(1f).setStartDelay(delay).setDuration(duration).setInterpolator(STANDARD).start()
    }

    /**
     * A check that draws itself: [animated] (an animated-vector) started in [image], or [still] with
     * [stillTint] when the phone's animations are off.
     */
    fun drawCheck(image: ImageView, animated: Int, still: Int, stillTint: Int? = null) {
        if (Motion.enabled(image.context)) {
            image.imageTintList = null
            image.setImageDrawable(ContextCompat.getDrawable(image.context, animated)?.mutate())
            (image.drawable as? Animatable)?.start()
        } else {
            image.setImageResource(still)
            image.imageTintList = stillTint?.let { ColorStateList.valueOf(ContextCompat.getColor(image.context, it)) }
        }
    }

    /**
     * A background tint that eases from one colour to the next (a result's wash), remembered on the view so the
     * next result cancels this one. [to] is a colour, not a resource.
     */
    fun tintTo(view: View, from: Int, to: Int, duration: Long = 160) {
        (view.getTag(R.id.motion_result) as? Animator)?.cancel()
        if (!Motion.enabled(view.context) || from == to) {
            view.backgroundTintList = ColorStateList.valueOf(to)
            return
        }
        val argb = ArgbEvaluator()
        val run = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = STANDARD
            addUpdateListener { view.backgroundTintList = ColorStateList.valueOf(argb.evaluate(it.animatedFraction, from, to) as Int) }
        }
        view.setTag(R.id.motion_result, run)
        run.start()
    }
}
