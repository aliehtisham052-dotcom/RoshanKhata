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
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.innovation313.roshankhata.data.InvoiceTemplateKit
import com.innovation313.roshankhata.data.SealMark
import kotlin.random.Random

/**
 * The Roshan Khata seal pressed onto a paper the moment it is shown (10 Nov 2026, the owner: "jaise payment apps ka
 * thappa"). Played once over [activity] when a PDF just made is opened, with the very date and word printed on it:
 *
 *  - the stamp comes down from above, large and faint, turned a little further than it will rest;
 *  - it strikes in 200 ms, speeding up as a hand does, and is pressed a touch flat;
 *  - at the strike the phone gives one tick, an ink ring spreads from the rim and fades, and the paper behind dips;
 *  - the stamp eases back to its size at the seal's own 8 degrees, rests a moment, and the cover lifts.
 *
 * Feedback for something real: it plays only for a paper that carries this seal, made moments ago. A tap skips it.
 * Nothing bounces. With the phone's animations off it is not played at all.
 */
object StampMoment {

    fun play(activity: Activity, mark: SealMark.Mark) {
        if (!Motion.enabled(activity)) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val d = activity.resources.displayMetrics.density
        val side = (minOf(root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels,
            activity.resources.displayMetrics.widthPixels) * 0.56f).toInt().coerceIn((180 * d).toInt(), (300 * d).toInt())

        val cover = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(0x8C, 0x0B, 0x1A, 0x12))
            alpha = 0f
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // The paper the stamp lands on: a white card that dips at the strike.
        val paper = View(activity).apply {
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 20 * d }
            elevation = 12 * d
        }
        val paperSide = (side * 1.32f).toInt()
        cover.addView(paper, FrameLayout.LayoutParams(paperSide, paperSide, Gravity.CENTER))
        // The ink that spreads from the rim.
        val ring = View(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke((5 * d).toInt(), InvoiceTemplateKit.SEAL_INK)
            }
            alpha = 0f
            elevation = 13 * d
        }
        cover.addView(ring, FrameLayout.LayoutParams((side * 0.94f).toInt(), (side * 0.94f).toInt(), Gravity.CENTER))
        val stamp = ImageView(activity).apply {
            setImageBitmap(sealPicture(activity, side, mark))
            elevation = 14 * d
            alpha = 0f
            scaleX = 2.3f; scaleY = 2.3f
            rotation = -22f
            translationY = -36 * d
        }
        cover.addView(stamp, FrameLayout.LayoutParams(side, side, Gravity.CENTER))
        root.addView(cover, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val decelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val standard = PathInterpolator(0.2f, 0f, 0f, 1f)

        val show = ObjectAnimator.ofFloat(cover, View.ALPHA, 0f, 1f).setDuration(160)
        val strike = ObjectAnimator.ofPropertyValuesHolder(
            stamp,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 2.3f, 0.93f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 2.3f, 0.9f),
            PropertyValuesHolder.ofFloat(View.ROTATION, -22f, InvoiceTemplateKit.SEAL_TILT),
            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -36 * d, 0f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
        ).apply { duration = 200; interpolator = AccelerateInterpolator(1.8f) }
        val settle = ObjectAnimator.ofPropertyValuesHolder(
            stamp,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.93f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1f)
        ).apply { duration = 220; interpolator = decelerate }
        val ink = ObjectAnimator.ofPropertyValuesHolder(
            ring,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.96f, 1.42f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.96f, 1.42f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 0f)
        ).apply { duration = 460; interpolator = decelerate }
        val dip = ObjectAnimator.ofPropertyValuesHolder(
            paper,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.975f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.975f, 1f)
        ).apply { duration = 240; interpolator = standard }
        // The stamp rests a moment once it has settled, then the cover lifts.
        val lift = ObjectAnimator.ofFloat(cover, View.ALPHA, 1f, 0f).apply { duration = 240; startDelay = 620; interpolator = standard }

        strike.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                stamp.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        })
        val all = AnimatorSet().apply {
            play(show)
            play(strike).after(show)
            play(settle).after(strike)
            play(ink).after(strike)
            play(dip).after(strike)
            play(lift).after(settle)
        }
        var gone = false
        fun end() {
            if (gone) return
            gone = true
            root.removeView(cover)
        }
        lift.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = end()
        })
        cover.setOnClickListener {
            all.cancel()
            cover.animate().alpha(0f).setDuration(120).withEndAction { end() }.start()
        }
        all.start()
    }

    /**
     * The seal as printed, drawn for the screen, with a rubber stamp's grain: a few hundred tiny specks where the ink
     * did not take (the same pattern every time, so the stamp never shimmers).
     */
    private fun sealPicture(activity: Activity, side: Int, mark: SealMark.Mark): Bitmap {
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // The shop's own stamp when that is the paper's seal (its picture as kept, no grain added), else ours.
        if (mark.own) {
            com.innovation313.roshankhata.data.BusinessProfile.loadStamp(activity)?.let { stamp ->
                InvoiceTemplateKit.drawBitmapFit(c, stamp, android.graphics.RectF(0f, 0f, side.toFloat(), side.toFloat()))
                return bmp
            }
        }
        InvoiceTemplateKit.drawRecordSeal(c, side / 2f, side / 2f, side * 0.96f, mark.middle, mark.below, tilt = 0f, top = mark.top)
        // The drawing above remembered its words again; nothing else should play from them.
        SealMark.clear()
        val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); isAntiAlias = true }
        val rnd = Random(313)
        repeat(420) {
            clear.alpha = 90 + rnd.nextInt(140)
            c.drawCircle(rnd.nextFloat() * side, rnd.nextFloat() * side, side * (0.002f + rnd.nextFloat() * 0.005f), clear)
        }
        return bmp
    }
}
