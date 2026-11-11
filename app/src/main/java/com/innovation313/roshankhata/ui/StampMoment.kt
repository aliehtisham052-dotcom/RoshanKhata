package com.innovation313.roshankhata.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.innovation313.roshankhata.data.InvoiceTemplateKit
import com.innovation313.roshankhata.data.SealMark

/**
 * The seal pressed onto the paper the moment it is shown (10 Nov 2026, the owner: "jaise payment apps ka thappa"),
 * on the page itself, where it is printed (11 Nov 2026, the owner: no card of its own — the stamp lands on the PDF,
 * with the shop's name it carries there). Played once over the page view when a PDF just made is opened:
 *
 *  - the printed seal's spot is covered with paper, so the page shows no seal yet;
 *  - the stamp, exactly the printed one (the shop's name, the date, or the shop's own stamp picture), comes down from
 *    above the spot, large and faint, turned a little further than it will rest;
 *  - it strikes in 200 ms, speeding up as a hand does, and is pressed a touch flat; the cover goes, the phone gives
 *    one tick and an ink ring spreads from the rim;
 *  - it eases back to the seal's own size and angle, sits exactly on the printed seal, and fades into it.
 *
 * Feedback for something real: it plays only for a paper that carries this seal, made moments ago. A tap ends it at
 * once. Nothing bounces. With the phone's animations off it is not played at all.
 */
object StampMoment {

    /**
     * Plays [mark] onto [page] (a view showing the whole page, edge to edge). Does nothing when the seal's place on
     * the page is not known, the page is not laid out, or animations are off.
     */
    fun play(activity: Activity, mark: SealMark.Mark, page: View) {
        if (!Motion.enabled(activity) || !mark.placed) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (page.width <= 0 || page.height <= 0 || !page.isAttachedToWindow) return
        val d = activity.resources.displayMetrics.density

        // Where the seal sits on the screen, in the root's own coordinates.
        val at = IntArray(2).also { page.getLocationInWindow(it) }
        val origin = IntArray(2).also { root.getLocationInWindow(it) }
        val cx = at[0] - origin[0] + mark.x * page.width
        val cy = at[1] - origin[1] + mark.y * page.height
        val side = (mark.size * page.width).toInt()
        if (side < (24 * d).toInt()) return
        val picture = sealPicture(activity, side, mark) ?: return

        fun FrameLayout.place(v: View, size: Int) =
            addView(v, FrameLayout.LayoutParams(size, size).apply {
                leftMargin = (cx - size / 2f).toInt()
                topMargin = (cy - size / 2f).toInt()
            })

        // Over the page, transparent: it takes the taps (a tap ends the moment) and holds the page still meanwhile.
        val stage = FrameLayout(activity).apply {
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            clipChildren = false
        }
        // Paper over the printed seal until the stamp strikes.
        val cover = View(activity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        }
        stage.place(cover, (side * 1.04f).toInt())
        val ring = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((3 * d).toInt().coerceAtLeast(2), InvoiceTemplateKit.SEAL_INK)
            }
            alpha = 0f
        }
        stage.place(ring, side)
        val stamp = ImageView(activity).apply {
            setImageBitmap(picture)
            alpha = 0f
            scaleX = 1.9f; scaleY = 1.9f
            rotation = InvoiceTemplateKit.SEAL_TILT - 14f
            translationY = -48 * d
            elevation = 10 * d
        }
        stage.place(stamp, side)
        root.addView(stage, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val standard = PathInterpolator(0.2f, 0f, 0f, 1f)

        // Raised over the spot, coming into sight.
        val lift = ObjectAnimator.ofPropertyValuesHolder(
            stamp,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.85f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.9f, 1.7f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.9f, 1.7f)
        ).apply { duration = 180; interpolator = decelerate }
        val strike = ObjectAnimator.ofPropertyValuesHolder(
            stamp,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.7f, 0.95f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.7f, 0.92f),
            PropertyValuesHolder.ofFloat(View.ROTATION, InvoiceTemplateKit.SEAL_TILT - 14f, InvoiceTemplateKit.SEAL_TILT),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -48 * d, 0f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.85f, 1f)
        ).apply { duration = 200; interpolator = AccelerateInterpolator(1.8f) }
        val settle = ObjectAnimator.ofPropertyValuesHolder(
            stamp,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.95f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f, 1f),
            PropertyValuesHolder.ofFloat(View.ELEVATION, 10 * d, 0f)
        ).apply { duration = 220; interpolator = decelerate }
        val ink = ObjectAnimator.ofPropertyValuesHolder(
            ring,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.98f, 1.45f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.98f, 1.45f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 0f)
        ).apply { duration = 460; interpolator = decelerate }
        // The stamp sits on the printed seal a moment, then gives way to it: the page alone remains.
        val fade = ObjectAnimator.ofFloat(stamp, View.ALPHA, 1f, 0f).apply { duration = 220; startDelay = 520; interpolator = standard }

        strike.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                stage.removeView(cover)
                stamp.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        })
        val all = AnimatorSet().apply {
            play(lift)
            play(strike).after(lift)
            play(settle).after(strike)
            play(ink).after(strike)
            play(fade).after(settle)
        }
        var gone = false
        fun end() {
            if (gone) return
            gone = true
            root.removeView(stage)
        }
        fade.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = end()
        })
        stage.setOnClickListener {
            all.cancel()
            end()
        }
        all.start()
    }

    /**
     * The seal exactly as printed, [side] pixels square, unturned (the view turns it to the printed angle): the shop's
     * own stamp picture when that was printed, else the seal with the very words on the paper.
     */
    private fun sealPicture(activity: Activity, side: Int, mark: SealMark.Mark): Bitmap? {
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (mark.own) {
            val own = com.innovation313.roshankhata.data.BusinessProfile.loadStamp(activity) ?: return null
            InvoiceTemplateKit.drawBitmapFit(c, own, RectF(0f, 0f, side.toFloat(), side.toFloat()))
            return bmp
        }
        InvoiceTemplateKit.drawRecordSeal(c, side / 2f, side / 2f, side.toFloat(), mark.middle, mark.below, tilt = 0f, top = mark.top)
        // The drawing above remembered its words again; nothing else should play from them.
        SealMark.clear()
        return bmp
    }
}
