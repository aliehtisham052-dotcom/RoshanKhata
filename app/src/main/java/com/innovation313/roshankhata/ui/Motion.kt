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
     * The change to a figure, shown BESIDE it (5 Oct): "+Rs 5,000" rises
     * over the number, holds a second and is gone. The figure itself is set
     * straight to its new value by the caller — the owner asked that a figure
     * never count its way there (the old countUp showed amounts that were
     * never true for half a second). Drawn in the window's overlay, so it
     * moves nothing and catches no touch.
     */
    fun delta(anchor: TextView, label: String) {
        if (!enabled(anchor.context) || !anchor.isLaidOut) return
        val root = anchor.rootView as? android.view.ViewGroup ?: return
        val chip = android.view.LayoutInflater.from(anchor.context)
            .inflate(com.innovation313.roshankhata.R.layout.view_delta_chip, root, false) as TextView
        chip.text = label
        val unspecified = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        chip.measure(unspecified, unspecified)
        val a = IntArray(2).also { anchor.getLocationInWindow(it) }
        val o = IntArray(2).also { root.getLocationInWindow(it) }
        val layout = anchor.layout
        val textMid = if (layout != null && layout.lineCount > 0)
            anchor.paddingLeft + (layout.getLineLeft(0) + layout.getLineRight(0)) / 2f else anchor.width / 2f
        val left = (a[0] - o[0] + textMid - chip.measuredWidth / 2f).toInt()
            .coerceIn(0, (root.width - chip.measuredWidth).coerceAtLeast(0))
        val top = (a[1] - o[1] - chip.measuredHeight - 2 * anchor.resources.displayMetrics.density).toInt()
        chip.layout(left, top, left + chip.measuredWidth, top + chip.measuredHeight)
        val rise = 10 * anchor.resources.displayMetrics.density
        root.overlay.add(chip)
        chip.alpha = 0f
        chip.translationY = rise
        chip.animate().alpha(1f).translationY(0f).setDuration(240)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                chip.animate().alpha(0f).translationY(-rise).setStartDelay(1000).setDuration(320)
                    .withEndAction { root.overlay.remove(chip) }.start()
            }.start()
    }

    /**
     * An entry is saved (5 Oct): a green disc pops up in the middle of the
     * screen, a white tick draws itself, and it fades — about 1.1 s, over
     * everything, touching nothing, so the owner can carry on at once. Felt
     * as well (see [confirm]). Not shown with animations off.
     */
    fun savedTick(activity: android.app.Activity) {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        if (!enabled(activity) || !root.isLaidOut) return
        val tick = android.view.LayoutInflater.from(activity)
            .inflate(com.innovation313.roshankhata.R.layout.view_save_tick, root, false) as android.widget.ImageView
        val size = (104 * activity.resources.displayMetrics.density).toInt()
        val left = (root.width - size) / 2
        val top = (root.height * 0.42f - size / 2f).toInt()
        tick.layout(left, top, left + size, top + size)
        root.overlay.add(tick)
        tick.alpha = 0f
        tick.scaleX = 0.6f
        tick.scaleY = 0.6f
        tick.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
            .withStartAction { (tick.drawable as? android.graphics.drawable.Animatable)?.start() }
            .withEndAction {
                tick.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setStartDelay(560).setDuration(240)
                    .withEndAction { root.overlay.remove(tick) }.start()
            }.start()
    }
}
