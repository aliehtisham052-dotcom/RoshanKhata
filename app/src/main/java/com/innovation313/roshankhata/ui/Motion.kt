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

    // ---------- Listening, and sending (5 Oct) ----------

    /**
     * Rings spreading from [button] while the phone listens. Starts on the
     * tap, so it shows before the phone's own listening screen comes up (and
     * behind it, on phones whose listener is a half-sheet). [stopPulse] when
     * the answer comes back.
     */
    fun pulse(button: android.view.View) {
        stopPulse(button)
        if (!enabled(button.context) || !button.isLaidOut) return
        val root = button.rootView as? android.view.ViewGroup ?: return
        // The rings take the glyph's colour, not the fill's (7 Oct): the mic
        // sits on a pale green square now, and rings in that pale green were
        // invisible on the white sheet. The glyph is the dark tone of the
        // same hue by day and the light tone by night, so it shows on either.
        val mb = button as? com.google.android.material.button.MaterialButton
        val color = mb?.iconTint?.defaultColor?.takeIf { android.graphics.Color.alpha(it) > 0 }
            ?: mb?.backgroundTintList?.defaultColor?.takeIf { android.graphics.Color.alpha(it) > 0 }
            ?: androidx.core.content.ContextCompat.getColor(button.context, com.innovation313.roshankhata.R.color.brand_green)
        val span = (maxOf(button.width, button.height) * 2.2f).toInt()
        val rings = PulseRings(button.context, color, maxOf(button.width, button.height) / 2f * 0.9f)
        val a = IntArray(2).also { button.getLocationInWindow(it) }
        val o = IntArray(2).also { root.getLocationInWindow(it) }
        val cx = a[0] - o[0] + button.width / 2
        val cy = a[1] - o[1] + button.height / 2
        rings.layout(cx - span / 2, cy - span / 2, cx + span / 2, cy + span / 2)
        root.overlay.add(rings)
        rings.go()
        button.setTag(com.innovation313.roshankhata.R.id.motion_pulse, rings)
    }

    fun stopPulse(button: android.view.View) {
        val rings = button.getTag(com.innovation313.roshankhata.R.id.motion_pulse) as? PulseRings ?: return
        rings.stop()
        (button.rootView as? android.view.ViewGroup)?.overlay?.remove(rings)
        button.setTag(com.innovation313.roshankhata.R.id.motion_pulse, null)
    }

    /**
     * The golden paper plane: from the owner's last tap (or the foot of the
     * screen, if that tap was in a dialog or long ago) up to the far top
     * corner — top-left in Urdu and Arabic. [then] runs at 70 % of the
     * flight, so WhatsApp opens as the plane leaves: about 0.4 s added.
     * With animations off, [then] runs at once.
     */
    fun paperPlane(activity: android.app.Activity, then: () -> Unit) {
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        if (root == null || !enabled(activity) || !root.isLaidOut) { then(); return }
        val o = IntArray(2).also { root.getLocationOnScreen(it) }
        val tap = lastTap.takeIf { System.currentTimeMillis() - lastTapAt < 1500 }
        val sx = (tap?.first ?: (o[0] + root.width / 2f)) - o[0]
        val sy = (tap?.second ?: (o[1] + root.height * 0.85f)) - o[1]
        val rtl = root.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
        val ex = if (rtl) root.width * 0.06f else root.width * 0.94f
        val ey = -root.height * 0.02f
        val flight = PlaneFlight(activity, sx, sy, ex, ey)
        flight.layout(0, 0, root.width, root.height)
        root.overlay.add(flight)
        var sent = false
        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 560
            interpolator = android.view.animation.AccelerateInterpolator(1.3f)
            addUpdateListener {
                flight.t = it.animatedValue as Float
                if (!sent && flight.t >= 0.7f) { sent = true; then() }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!sent) { sent = true; then() }
                    root.overlay.remove(flight)
                }
            })
            start()
        }
    }

    /** Where the owner last touched the screen (raw px), kept by BaseActivity. */
    @Volatile internal var lastTap: Pair<Float, Float>? = null
    @Volatile internal var lastTapAt = 0L
}
