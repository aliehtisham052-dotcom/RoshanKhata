package com.innovation313.roshankhata.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Path
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnLayout
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Hudhud
import kotlin.random.Random

/**
 * Hudhud's little acts (13 Nov 2026, the owner: "urr ke dikhao", and "Hudhud ko touch karne par… kabhi urre, kabhi
 * pankh maare, kabhi alag alag activities… khaali screen par khoobsurat animation ke saath"): he flies off the
 * screen and back, beats his wings, dances, hops, or turns round — over the whole window above his sheet, not inside
 * his little stage.
 *
 * His two drawings make the wings: "happy" has both wings raised, "idle" has them folded, so changing between them
 * about nine times a second is a wing beat; he faces the way he flies (the drawings face right). A light tap of the
 * phone's haptics when he takes off and when he lands, as the phone's own buttons answer a touch (the owner's
 * settings for touch feedback are the phone's: performHapticFeedback keeps to them).
 *
 * Rules kept: an act only plays when asked for (a tap on him, "urr ke dikhao"); one at a time; with the phone's
 * animations off nothing moves (the sheet shows him glad instead); everything is taken away when the sheet closes.
 */
class HudhudActs(
    /** His stage on the sheet: where he takes off from and lands. */
    private val stage: View,
    /** The stage's own drawings, hidden while he is away. */
    private val poses: List<View>,
    /** The window's whole area he may fly over (the sheet's decor). */
    private val sky: () -> ViewGroup?
) {
    /** What he can do. */
    enum class Act {
        FLY, FLAP, DANCE, HOP, SPIN;

        companion object {
            /** The act a question asked for ("urr ke dikhao", "naach ke dikhao", "uchhal ke dikhao"). */
            fun of(move: Hudhud.Move): Act = when (move) {
                Hudhud.Move.FLY -> FLY
                Hudhud.Move.DANCE -> DANCE
                Hudhud.Move.JUMP -> HOP
            }
        }
    }

    private var flier: ImageView? = null
    private var running: Animator? = null
    private var beats: ValueAnimator? = null
    private var last: Act? = null
    private var onEnd: (() -> Unit)? = null
    /** He has been put in the sky and waits for its layout before he is placed and takes off. */
    private var placing = false

    /** Whether an act is playing (or about to). */
    val busy: Boolean get() = running != null || placing

    /** An act chosen at random, never the one just played: a tap on him should not do the same thing twice. */
    fun random(): Act {
        val all = Act.values().filter { it != last }
        return all[Random.nextInt(all.size)]
    }

    /** A light touch of the phone's haptics, as its own buttons give. */
    fun tap(view: View = stage) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    /** Plays [act]; [done] runs when he is back on his stage (or at once when it cannot play). */
    @SuppressLint("RtlHardcoded") // the sky's left edge on purpose: his place is measured from it in both directions
    fun play(act: Act, done: () -> Unit = {}) {
        if (busy || !Motion.enabled(stage.context)) {
            done()
            return
        }
        val sky = sky() ?: run { done(); return }
        if (stage.width == 0 || sky.width == 0) {
            done()
            return
        }
        last = act
        onEnd = done
        val bird = ImageView(stage.context).apply {
            setImageResource(R.drawable.hudhud_idle)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // He casts a little shadow while he is off the ground, as he would over the page.
            elevation = 6f * resources.displayMetrics.density
            visibility = View.INVISIBLE
        }
        // Hung from the sky's left and top edges whatever the page's direction (a start gravity put him at the right
        // edge of a right-to-left page), and placed only once laid out: setX and setY measure from where the layout
        // put him, and a place set before it was lost in Urdu and Arabic.
        sky.addView(bird, FrameLayout.LayoutParams(stage.width, stage.height, Gravity.TOP or Gravity.LEFT))
        flier = bird
        placing = true
        bird.doOnLayout {
            if (placing && flier === bird) {
                placing = false
                takeOff(act, bird, sky)
            }
        }
    }

    /** [bird], laid out in [sky], is put where the stage stands and plays [act]. */
    private fun takeOff(act: Act, bird: ImageView, sky: ViewGroup) {
        // Where the stage rests, in the sky's own place: less a hop or a nod it is in the middle of, so he lands where
        // the stage stands still.
        val at = IntArray(2)
        val skyAt = IntArray(2)
        stage.getLocationInWindow(at)
        sky.getLocationInWindow(skyAt)
        val homeX = at[0] - skyAt[0] - stage.translationX
        val homeY = at[1] - skyAt[1] - stage.translationY
        bird.x = homeX
        bird.y = homeY
        bird.pivotX = stage.width / 2f
        bird.pivotY = stage.height.toFloat()
        // He looks the way the stage shows him (turned in a right-to-left page).
        val facing = if (stage.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
        bird.scaleX = facing
        bird.visibility = View.VISIBLE
        poses.forEach { it.visibility = View.INVISIBLE }
        val dp = stage.resources.displayMetrics.density
        val anim = when (act) {
            Act.FLY -> fly(bird, sky, homeX, homeY, facing, dp)
            Act.FLAP -> flap(bird, homeY, dp)
            Act.DANCE -> dance(bird, homeX, homeY, facing, dp)
            Act.HOP -> hop(bird, homeY, dp)
            Act.SPIN -> spin(bird, homeY, facing, dp)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (running === animation) finish(landed = !cancelled)
            }
        })
        running = anim
        tap()
        anim.start()
    }

    /** Ends what is playing at once and puts him back on his stage. */
    fun stop() {
        if (placing) {
            placing = false
            finish(landed = false)
            return
        }
        val a = running ?: return
        running = null
        a.cancel()
        finish(landed = false)
    }

    private fun finish(landed: Boolean) {
        running = null
        beats?.cancel()
        beats = null
        flier?.let { f -> (f.parent as? ViewGroup)?.removeView(f) }
        flier = null
        poses.forEach { it.visibility = View.VISIBLE }
        if (landed) tap()
        val then = onEnd
        onEnd = null
        then?.invoke()
    }

    // ------------------------------------------------------------------------------------------- the wings

    /** His wings beat (raised, folded) for as long as it runs; [up] leaves them raised when it stops. */
    private fun wings(bird: ImageView, millis: Long, up: Boolean = false): ValueAnimator =
        ValueAnimator.ofInt(0, (millis / BEAT_MS).toInt().coerceAtLeast(1)).apply {
            duration = millis
            interpolator = LinearInterpolator()
            var shown = -1
            addUpdateListener {
                val beat = it.animatedValue as Int
                if (beat != shown) {
                    shown = beat
                    bird.setImageResource(if (beat % 2 == 0) R.drawable.hudhud_happy else R.drawable.hudhud_idle)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bird.setImageResource(if (up) R.drawable.hudhud_happy else R.drawable.hudhud_idle)
                }
            })
        }

    private fun pose(bird: ImageView, drawing: Int, at: Long): ValueAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1
            startDelay = at
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    bird.setImageResource(drawing)
                }
            })
        }

    // ------------------------------------------------------------------------------------------- the acts

    /**
     * Off he goes: up from the stage with a few beats, a curve out past the screen's edge (the far edge first, the way
     * he faces), back in from the other side higher up, across the open page, and down onto his stage again.
     */
    private fun fly(bird: ImageView, sky: ViewGroup, homeX: Float, homeY: Float, facing: Float, dp: Float): Animator {
        val w = sky.width.toFloat()
        val h = sky.height.toFloat()
        val size = bird.layoutParams.width.toFloat()
        val out = facing // 1: off to the right first; -1: off to the left (a right-to-left page)
        val farEdge = if (out > 0) w + size * 0.2f else -size * 1.2f
        val nearEdge = if (out > 0) -size * 1.2f else w + size * 0.2f
        val top = (homeY - h * 0.55f).coerceAtLeast(h * 0.06f)

        // Takeoff: a little rise, wings beating.
        val lift = ObjectAnimator.ofFloat(bird, View.Y, homeY, homeY - 34 * dp).apply {
            duration = 420
            interpolator = DecelerateInterpolator()
        }
        val liftWings = wings(bird, 420)
        val takeoff = AnimatorSet().apply { playTogether(lift, liftWings) }

        // Out past the edge on a curve, nose up.
        val leave = Path().apply {
            moveTo(homeX, homeY - 34 * dp)
            quadTo(homeX + out * w * 0.35f, top - h * 0.05f, farEdge, top)
        }
        val away = ObjectAnimator.ofFloat(bird, View.X, View.Y, leave).apply {
            duration = 950
            interpolator = PathInterpolator(0.3f, 0f, 0.7f, 1f)
        }
        val awayTilt = ObjectAnimator.ofFloat(bird, View.ROTATION, 0f, -out * 12f).apply { duration = 950 }
        val awayWings = wings(bird, 950)
        val goAway = AnimatorSet().apply { playTogether(away, awayTilt, awayWings) }

        // Back in from the other side, a gentle wave across the open page, turned the other way.
        val across = Path().apply {
            moveTo(nearEdge, top * 0.7f)
            cubicTo(
                w * 0.3f, top * 0.2f + h * 0.04f,
                w * 0.7f, top * 1.2f,
                if (out > 0) w * 0.62f else w * 0.38f - size, top * 0.9f
            )
        }
        val turn = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    bird.scaleX = facing
                    bird.rotation = 0f
                }
            })
        }
        val back = ObjectAnimator.ofFloat(bird, View.X, View.Y, across).apply {
            duration = 1250
            interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
        }
        val backWings = wings(bird, 1250)
        val comeBack = AnimatorSet().apply { playTogether(back, backWings) }

        // Down onto the stage, slowing, wings out, and a soft landing — turned to face the way he comes down.
        val from = if (out > 0) w * 0.62f else w * 0.38f - size
        val faceDown = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    bird.scaleX = if (homeX < from) -1f else 1f
                }
            })
        }
        val down = Path().apply {
            moveTo(from, top * 0.9f)
            quadTo(homeX + out * w * 0.25f, (top + homeY) * 0.45f, homeX, homeY)
        }
        val land = ObjectAnimator.ofFloat(bird, View.X, View.Y, down).apply {
            duration = 900
            interpolator = DecelerateInterpolator(1.6f)
        }
        val landTilt = ObjectAnimator.ofFloat(bird, View.ROTATION, out * 8f, 0f).apply { duration = 900 }
        val landWings = wings(bird, 760)
        val landing = AnimatorSet().apply { playTogether(land, landTilt, landWings) }
        val faceHome = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    bird.scaleX = facing
                }
            })
        }
        val squash = AnimatorSet().apply { playSequentially(faceHome, squash(bird)) }

        return AnimatorSet().apply {
            playSequentially(takeoff, goAway, turn, comeBack, faceDown, landing, squash)
        }
    }

    /** He rises on beating wings, hangs there a moment, and settles. */
    private fun flap(bird: ImageView, homeY: Float, dp: Float): Animator {
        val up = ObjectAnimator.ofFloat(bird, View.Y, homeY, homeY - 26 * dp).apply {
            duration = 520
            interpolator = DecelerateInterpolator()
        }
        val hover = ObjectAnimator.ofFloat(bird, View.Y, homeY - 26 * dp, homeY - 20 * dp, homeY - 26 * dp).apply {
            duration = 420
        }
        val down = ObjectAnimator.ofFloat(bird, View.Y, homeY - 26 * dp, homeY).apply {
            duration = 380
            interpolator = AccelerateInterpolator()
        }
        val path = AnimatorSet().apply { playSequentially(up, hover, down) }
        return AnimatorSet().apply {
            play(path).with(wings(bird, 1320))
            play(squash(bird)).after(path)
        }
    }

    /** A little dance: side to side, tilting, one wing out then both, with a hop on the third and the sixth beat. */
    private fun dance(bird: ImageView, homeX: Float, homeY: Float, facing: Float, dp: Float): Animator {
        val steps = ArrayList<Animator>()
        for (i in 0 until 6) {
            val side = if (i % 2 == 0) 1f else -1f
            val step = ObjectAnimator.ofPropertyValuesHolder(
                bird,
                PropertyValuesHolder.ofFloat(View.X, homeX + side * 10 * dp),
                PropertyValuesHolder.ofFloat(View.ROTATION, side * 11f),
                PropertyValuesHolder.ofFloat(View.Y, if (i == 2 || i == 5) homeY - 18 * dp else homeY)
            ).apply {
                duration = 260
                interpolator = DecelerateInterpolator()
            }
            val look = pose(bird, if (i % 2 == 0) R.drawable.hudhud_talking else R.drawable.hudhud_happy, 0)
            // Turned to face each step's way.
            val face = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        bird.scaleX = facing * (if (i % 2 == 0) 1f else -1f)
                    }
                })
            }
            steps += AnimatorSet().apply { playTogether(step, look, face) }
        }
        val home = ObjectAnimator.ofPropertyValuesHolder(
            bird,
            PropertyValuesHolder.ofFloat(View.X, homeX),
            PropertyValuesHolder.ofFloat(View.ROTATION, 0f),
            PropertyValuesHolder.ofFloat(View.Y, homeY),
            PropertyValuesHolder.ofFloat(View.SCALE_X, facing)
        ).apply { duration = 220 }
        return AnimatorSet().apply { playSequentially(steps + home + pose(bird, R.drawable.hudhud_happy, 0) + squash(bird)) }
    }

    /** Three hops: a crouch, up with his wings raised, down with them folded, and a squash as he lands. */
    private fun hop(bird: ImageView, homeY: Float, dp: Float): Animator {
        val hops = ArrayList<Animator>()
        for (i in 0 until 3) {
            val height = (if (i == 2) 52 else 34) * dp
            val crouch = ObjectAnimator.ofFloat(bird, View.SCALE_Y, 1f, 0.86f).apply { duration = 110 }
            val up = ObjectAnimator.ofPropertyValuesHolder(
                bird,
                PropertyValuesHolder.ofFloat(View.Y, homeY, homeY - height),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.86f, 1.06f)
            ).apply {
                duration = 230
                interpolator = DecelerateInterpolator()
            }
            val wingsUp = pose(bird, R.drawable.hudhud_happy, 0)
            val down = ObjectAnimator.ofPropertyValuesHolder(
                bird,
                PropertyValuesHolder.ofFloat(View.Y, homeY - height, homeY),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.06f, 1f)
            ).apply {
                duration = 210
                interpolator = AccelerateInterpolator()
            }
            val wingsDown = pose(bird, R.drawable.hudhud_idle, 0)
            hops += AnimatorSet().apply {
                playSequentially(crouch, AnimatorSet().apply { playTogether(up, wingsUp) }, AnimatorSet().apply { playTogether(down, wingsDown) })
            }
        }
        return AnimatorSet().apply { playSequentially(hops + squash(bird)) }
    }

    /** Up a little and round: he turns about twice in the air (as a card turns), and lands facing as before. */
    private fun spin(bird: ImageView, homeY: Float, facing: Float, dp: Float): Animator {
        val up = ObjectAnimator.ofFloat(bird, View.Y, homeY, homeY - 30 * dp).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
        }
        val turn = ObjectAnimator.ofFloat(bird, View.SCALE_X, facing, -facing, facing, -facing, facing).apply {
            duration = 820
            interpolator = LinearInterpolator()
        }
        val wingsUp = pose(bird, R.drawable.hudhud_happy, 0)
        val down = ObjectAnimator.ofFloat(bird, View.Y, homeY - 30 * dp, homeY).apply {
            duration = 260
            startDelay = 560
            interpolator = AccelerateInterpolator()
        }
        val air = AnimatorSet().apply { playTogether(up, turn, wingsUp, down) }
        return AnimatorSet().apply { playSequentially(air, pose(bird, R.drawable.hudhud_idle, 0), squash(bird)) }
    }

    /** The landing: a quick squash at his feet and back. */
    private fun squash(bird: ImageView): Animator =
        ObjectAnimator.ofPropertyValuesHolder(
            bird,
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f, 1f)
        ).apply { duration = 220 }

    private companion object {
        /** One wing beat (raised or folded): about nine changes a second. */
        const val BEAT_MS = 110L
    }
}
