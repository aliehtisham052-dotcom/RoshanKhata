package com.innovation313.roshankhata.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.Path
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
 * What the owner's video of 13 Nov 2026 showed, and how each is kept from happening again:
 *  - The sheet grew shorter while he was in the air ("I didn't catch that" after the greeting), and he landed where
 *    his stage had been — over the sheet's top edge — then jumped 50 dp down into it. He now flies in a layer over
 *    the window that follows his stage every frame ([follow]): the sheet may move, he lands on his stage.
 *  - In the air his head shook: the two drawings are two poses, not two frames of one beat — "happy" holds his head
 *    27 of 512 lower and 12 to the left of "idle" — so changing them shook his head instead of his wings. In the air
 *    "happy" is drawn with his head where "idle" has it ([frame]); on the ground both stand on the same feet.
 *  - He took off folded whatever pose the stage showed, and landed in a pose the stage did not show: he now takes
 *    off in the stage's own drawing and lands in the one it shows when he comes back ([current]).
 *  - The landing started from a hover at full speed; it now starts gently and slows onto the stage, and he turns to
 *    face the room in a quick turn rather than a flip.
 *
 * Rules kept: an act only plays when asked for (a tap on him, "urr ke dikhao"); one at a time; with the phone's
 * animations off nothing moves (the sheet shows him glad instead); everything is taken away when the sheet closes.
 */
class HudhudActs(
    /** His stage on the sheet: where he takes off from and lands. */
    private val stage: View,
    /** The stage's own drawings, hidden while he is away. */
    private val poses: List<View>,
    /** The drawing the stage shows now: he takes off in it and lands back in it, so neither end jumps. */
    private val current: () -> Int,
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

    /** The layer he flies in: the whole window, moved with his stage while he is away. */
    private var layer: FrameLayout? = null
    private var flier: ImageView? = null
    private var running: Animator? = null
    private var last: Act? = null
    private var onEnd: (() -> Unit)? = null
    /** He has been put in the sky and waits for its layout before he is placed and takes off. */
    private var placing = false
    /** Where the stage rested when he took off, in the sky's own place: the layer is moved by how far it has gone since. */
    private var baseX = 0f
    private var baseY = 0f
    private var follower: ViewTreeObserver.OnPreDrawListener? = null
    private var followed: ViewTreeObserver? = null
    private var followSky: ViewGroup? = null
    private val drawing = Matrix()

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
        val overlay = FrameLayout(stage.context).apply {
            // He may lean past the layer's edge as he leaves the screen; the window itself is the frame.
            clipChildren = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val bird = ImageView(stage.context).apply {
            // Placed by [frame], as FIT_CENTER places the stage's own drawings, or with his head held still in the air.
            scaleType = ImageView.ScaleType.MATRIX
            setImageResource(current())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.INVISIBLE
        }
        // Hung from the layer's left and top edges whatever the page's direction (a start gravity put him at the right
        // edge of a right-to-left page), and placed only once laid out: setX and setY measure from where the layout
        // put him, and a place set before it was lost in Urdu and Arabic. The layer takes no touch: everything under
        // it (the sheet, the dimmed page that closes it) answers as before.
        overlay.addView(bird, FrameLayout.LayoutParams(stage.width, stage.height, Gravity.TOP or Gravity.LEFT))
        sky.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        layer = overlay
        flier = bird
        placing = true
        bird.doOnLayout {
            if (placing && flier === bird) {
                placing = false
                takeOff(act, bird, overlay, sky)
            }
        }
    }

    /** [bird], laid out in [overlay] over [sky], is put where the stage stands and plays [act]. */
    private fun takeOff(act: Act, bird: ImageView, overlay: View, sky: ViewGroup) {
        // Where the stage rests, in the sky's own place: less a hop or a nod it is in the middle of, so he lands where
        // the stage stands still.
        val home = restOf(sky)
        val homeX = home[0]
        val homeY = home[1]
        baseX = homeX
        baseY = homeY
        follow(overlay, sky)
        bird.x = homeX
        bird.y = homeY
        bird.pivotX = stage.width / 2f
        bird.pivotY = stage.height.toFloat()
        // He looks the way the stage shows him (turned in a right-to-left page), in the stage's own drawing.
        val facing = if (stage.layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
        bird.scaleX = facing
        frame(bird, current(), air = false)
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
        unfollow()
        layer?.let { l -> (l.parent as? ViewGroup)?.removeView(l) }
        layer = null
        flier = null
        poses.forEach { it.visibility = View.VISIBLE }
        if (landed) tap()
        val then = onEnd
        onEnd = null
        then?.invoke()
    }

    // ------------------------------------------------------------------------------------------- his place

    /** Where the stage stands now, still (less a hop or a nod it is in the middle of), in [sky]'s own place. */
    private fun restOf(sky: ViewGroup): FloatArray {
        val at = IntArray(2)
        val skyAt = IntArray(2)
        stage.getLocationInWindow(at)
        sky.getLocationInWindow(skyAt)
        return floatArrayOf(at[0] - skyAt[0] - stage.translationX, at[1] - skyAt[1] - stage.translationY)
    }

    /**
     * Before every frame the layer he flies in is moved by how far his stage has gone since he took off (the sheet
     * grew, shrank, or was pulled), so every act ends on the stage as it stands then.
     */
    private fun follow(overlay: View, sky: ViewGroup) {
        unfollow()
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (stage.isAttachedToWindow) {
                val now = restOf(sky)
                overlay.translationX = now[0] - baseX
                overlay.translationY = now[1] - baseY
            }
            true
        }
        val observer = sky.viewTreeObserver
        observer.addOnPreDrawListener(listener)
        follower = listener
        followed = observer
        followSky = sky
    }

    private fun unfollow() {
        val listener = follower ?: return
        val observer = followed
        if (observer != null && observer.isAlive) {
            observer.removeOnPreDrawListener(listener)
        } else {
            followSky?.viewTreeObserver?.let { if (it.isAlive) it.removeOnPreDrawListener(listener) }
        }
        follower = null
        followed = null
        followSky = null
    }

    /**
     * [res] on [bird], fitted as FIT_CENTER fits the stage's own drawings. [air]: his head is held where the folded
     * drawing has it, so a wing beat moves his wings and not his head; on the ground the drawings share their feet.
     */
    private fun frame(bird: ImageView, res: Int, air: Boolean) {
        bird.setImageResource(res)
        val d = bird.drawable ?: return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        val vw = bird.width.toFloat()
        val vh = bird.height.toFloat()
        if (dw <= 0f || dh <= 0f || vw <= 0f || vh <= 0f) return
        val s = minOf(vw / dw, vh / dh)
        var tx = (vw - dw * s) / 2f
        var ty = (vh - dh * s) / 2f
        if (air && res == R.drawable.hudhud_happy) {
            tx += HAPPY_HEAD_X * dw * s
            ty += HAPPY_HEAD_Y * dh * s
        }
        drawing.setScale(s, s)
        drawing.postTranslate(tx, ty)
        bird.imageMatrix = drawing
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
                    frame(bird, if (beat % 2 == 0) R.drawable.hudhud_happy else R.drawable.hudhud_idle, air = true)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    frame(bird, if (up) R.drawable.hudhud_happy else R.drawable.hudhud_idle, air = true)
                }
            })
        }

    /** At [at] into its part, [bird] shows [res] ([air]: his head held still). */
    private fun pose(bird: ImageView, res: Int, air: Boolean, at: Long = 0): ValueAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1
            startDelay = at
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    frame(bird, res, air)
                }
            })
        }

    /** A quick turn to face [to] (a card turning, not a flip); nothing to see when he already faces that way. */
    private fun turn(bird: ImageView, from: Float, to: Float): Animator =
        ObjectAnimator.ofFloat(bird, View.SCALE_X, from, to).apply {
            duration = if (from == to) 0L else TURN_MS
            interpolator = MotionKit.STANDARD
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

        // Back in from the other side, a gentle wave across the open page (turned while out of sight).
        val across = Path().apply {
            moveTo(nearEdge, top * 0.7f)
            cubicTo(
                w * 0.3f, top * 0.2f + h * 0.04f,
                w * 0.7f, top * 1.2f,
                if (out > 0) w * 0.62f else w * 0.38f - size, top * 0.9f
            )
        }
        val unseen = ValueAnimator.ofFloat(0f, 1f).apply {
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

        // Down onto the stage. He slowed almost to a hover at the end of the wave, so the way down starts gently
        // (it started at full speed, a dart out of a hover), turns him toward the stage as he sets off, and slows
        // onto it.
        val from = if (out > 0) w * 0.62f else w * 0.38f - size
        val downFacing = if (homeX < from) -1f else 1f
        val down = Path().apply {
            moveTo(from, top * 0.9f)
            quadTo(homeX + out * w * 0.25f, (top + homeY) * 0.45f, homeX, homeY)
        }
        val land = ObjectAnimator.ofFloat(bird, View.X, View.Y, down).apply {
            duration = 900
            interpolator = MotionKit.STANDARD
        }
        val landTilt = ObjectAnimator.ofFloat(bird, View.ROTATION, out * 8f, 0f).apply { duration = 900 }
        val landWings = wings(bird, 760)
        val landing = AnimatorSet().apply { playTogether(land, landTilt, landWings, turn(bird, facing, downFacing)) }

        // On the stage he turns to face the room again, then settles.
        val home = AnimatorSet().apply { playSequentially(turn(bird, downFacing, facing), settle(bird)) }

        return AnimatorSet().apply {
            playSequentially(takeoff, goAway, unseen, comeBack, landing, home)
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
            play(settle(bird)).after(path)
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
            // On his feet: the drawings stand where they stand.
            val look = pose(bird, if (i % 2 == 0) R.drawable.hudhud_talking else R.drawable.hudhud_happy, air = false)
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
        return AnimatorSet().apply { playSequentially(steps + home + settle(bird)) }
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
            val wingsUp = pose(bird, R.drawable.hudhud_happy, air = true)
            val down = ObjectAnimator.ofPropertyValuesHolder(
                bird,
                PropertyValuesHolder.ofFloat(View.Y, homeY - height, homeY),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.06f, 1f)
            ).apply {
                duration = 210
                interpolator = AccelerateInterpolator()
            }
            val wingsDown = pose(bird, R.drawable.hudhud_idle, air = true)
            hops += AnimatorSet().apply {
                playSequentially(crouch, AnimatorSet().apply { playTogether(up, wingsUp) }, AnimatorSet().apply { playTogether(down, wingsDown) })
            }
        }
        return AnimatorSet().apply { playSequentially(hops + settle(bird)) }
    }

    /** Up a little and round: he turns about twice in the air (as a card turns), and lands facing as before. */
    private fun spin(bird: ImageView, homeY: Float, facing: Float, dp: Float): Animator {
        val up = ObjectAnimator.ofFloat(bird, View.Y, homeY, homeY - 30 * dp).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
        }
        val turning = ObjectAnimator.ofFloat(bird, View.SCALE_X, facing, -facing, facing, -facing, facing).apply {
            duration = 820
            interpolator = LinearInterpolator()
        }
        val wingsUp = pose(bird, R.drawable.hudhud_happy, air = true)
        val down = ObjectAnimator.ofFloat(bird, View.Y, homeY - 30 * dp, homeY).apply {
            duration = 260
            startDelay = 560
            interpolator = AccelerateInterpolator()
        }
        val aloft = AnimatorSet().apply { playTogether(up, turning, wingsUp, down) }
        return AnimatorSet().apply { playSequentially(aloft, settle(bird)) }
    }

    /**
     * The landing: back in the drawing the stage shows now (an answer may have come while he was away), with a quick
     * squash at his feet — so when the stage's own drawings come back, nothing changes under the eye.
     */
    private fun settle(bird: ImageView): Animator {
        val home = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    frame(bird, current(), air = false)
                }
            })
        }
        val squash = ObjectAnimator.ofPropertyValuesHolder(
            bird,
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.9f, 1f)
        ).apply { duration = 220 }
        return AnimatorSet().apply { playSequentially(home, squash) }
    }

    private companion object {
        /** One wing beat (raised or folded): about nine changes a second. */
        const val BEAT_MS = 110L
        /** A turn to face the other way. */
        const val TURN_MS = 160L

        /**
         * Where "happy" is drawn in the air, as a share of the drawing, so his head sits where "idle" holds it: its head
         * is 12 of 512 to the right and 27 of 512 lower (measured 13 Nov 2026 by matching the two heads, the glasses
         * and beak on top of each other; the bodies then differ by about 3 dp, which reads as the body lifting with a
         * wing beat). A drawing redrawn later is measured again.
         */
        const val HAPPY_HEAD_X = -12f / 512f
        const val HAPPY_HEAD_Y = -27f / 512f
    }
}
