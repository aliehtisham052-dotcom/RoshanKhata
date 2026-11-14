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
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnLayout
import com.innovation313.roshankhata.R
import com.innovation313.roshankhata.data.Hudhud
import com.innovation313.roshankhata.data.HudhudMotion
import kotlin.math.abs

/**
 * Hudhud's little acts, in the empty sky at the top of his sheet (14 Nov 2026, the owner's video and words: "wo Hudhud
 * screen se bahar jaata hai aur doosri taraf se andar aata hai — ye nahi hona chahiye… sheet upar se khaali ho, aur
 * ek jagah se doosri jagah thodi movement ho, phir wo doosri jagah baithe ya pehli jagah").
 *
 * **Where.** He never leaves the sheet. The top of the sheet is a band of open sky with three perches (start, middle,
 * end) and his shadow; his stage stands on one of them. A tap plays one act, never the same twice running ([random]):
 *  - [Act.FLY]: he flies on an arc to another perch and STAYS there — that perch is home from then on;
 *  - [Act.VISIT]: he flies to another perch, looks round, and flies back to where he began;
 *  - [Act.FLAP], [Act.HOP], [Act.DANCE], [Act.SPIN]: in place, as before.
 * When the keyboard takes the room the band shrinks ([compact]) and he only hops and flutters, small.
 *
 * **How it moves** ([HudhudMotion] holds the arithmetic, tested): a crouch before take-off (anticipation), an arc with
 * a slow start and a slow end, a lean into the climb and into the descent, a squash and a small settle on touching
 * down, his shadow shrinking as he rises and growing as he lands; his wings beat only while he is in the air, and the
 * head is held still against the beat ([frame]). A light tap of the phone's haptics at take-off and landing.
 *
 * **What the owner's video of 13 Nov showed, and how each is kept from happening again:**
 *  - He flew off the screen and came in from the other side: he flies only inside the band, and every path is
 *    clamped to it ([HudhudMotion.arc]).
 *  - His head shook in the air: the two drawings are two poses; "happy" is drawn with its head where "idle" has it
 *    ([frame]).
 *  - He took off folded whatever the stage showed and landed in a pose the stage did not show: he takes off in the
 *    stage's own drawing and lands in the one it shows when he is back ([current]).
 *  - The sheet changed height while he was in the air: he is a child of the band, so he moves with the sheet.
 *
 * Rules kept: an act only plays when asked for (a tap on him, "urr ke dikhao"); one at a time; with the phone's
 * animations off nothing moves; everything is taken away when the sheet closes.
 */
class HudhudActs(
    /** His stage on the sheet: where he takes off from and lands. A child of [sky]. */
    private val stage: View,
    /** The stage's own drawings, hidden while he is in the air. */
    private val poses: List<View>,
    /** The drawing the stage shows now: he takes off in it and lands back in it, so neither end jumps. */
    private val current: () -> Int,
    /** The open band he moves in. */
    private val sky: FrameLayout,
    /** The three perches, start / middle / end (small bars); placed here. */
    private val perches: List<View>,
    /** His shadow, a soft oval under him; null for none. */
    private val shadow: View?
) {
    /** What he can do. */
    enum class Act {
        FLY, VISIT, FLAP, DANCE, HOP, SPIN;

        companion object {
            /** The act a question asked for ("urr ke dikhao", "naach ke dikhao", "uchhal ke dikhao"). */
            fun of(move: Hudhud.Move): Act = when (move) {
                Hudhud.Move.FLY -> FLY
                Hudhud.Move.DANCE -> DANCE
                Hudhud.Move.JUMP -> HOP
            }
        }
    }

    /** The bird in flight, a child of [sky] placed exactly over the stage while an act plays. */
    private var flier: ImageView? = null
    private var running: Animator? = null
    private var last: Act? = null
    /** The perch he flew to last: the next flight prefers another ([HudhudMotion.otherPerch]). */
    private var lastTarget: Int? = null
    private var onEnd: (() -> Unit)? = null
    private val drawing = Matrix()

    /** The perch the stage stands on (0 start, 1 middle, 2 end); a flight that lands elsewhere changes it. */
    var home = 0
        private set

    /** The band is small (the keyboard is up): he hops and flutters, and only a little. */
    var compact = false

    /** Whether an act is playing (or about to). */
    val busy: Boolean get() = running != null

    init {
        // The perches and the stage are laid out again whenever the band's width changes (rotation, split screen).
        sky.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) layoutPerches()
        }
        sky.doOnLayout { layoutPerches() }
    }

    // ------------------------------------------------------------------------------------------- his places

    private val dp: Float get() = stage.resources.displayMetrics.density

    /** The left edge of the stage on perch [i] (0 start, 1 middle, 2 end), as the band is drawn (mirrored in right-to-left). */
    private fun leftOf(i: Int): Float {
        val rtl = sky.layoutDirection == View.LAYOUT_DIRECTION_RTL
        return HudhudMotion.perchLeft(i, sky.width.toFloat(), stage.width.toFloat(), EDGE_DP * dp, rtl)
    }

    /** Puts the perches under their places, and the stage and his shadow on the perch that is home. */
    @SuppressLint("RtlHardcoded") // the band's left edge on purpose: places are measured from it in both directions
    fun layoutPerches() {
        if (sky.width == 0 || stage.width == 0) return
        val size = stage.width.toFloat()
        perches.forEachIndexed { i, bar -> bar.x = leftOf(i) + (size - bar.width) / 2f }
        if (running == null) {
            stage.x = leftOf(home)
            placeShadow(stage.x, 1f)
        }
    }

    private fun placeShadow(stageX: Float, grounded: Float) {
        val s = shadow ?: return
        s.x = stageX + (stage.width - s.width) / 2f
        // Smaller and fainter the higher he is (grounded 1 = on the perch).
        s.scaleX = 0.55f + 0.45f * grounded
        s.scaleY = 0.55f + 0.45f * grounded
        s.alpha = 0.5f + 0.5f * grounded
    }

    /** An act chosen at random, never the one just played: a tap on him should not do the same thing twice. */
    fun random(): Act {
        val pool = if (compact) listOf(Act.HOP, Act.FLAP) else listOf(Act.FLY, Act.FLY, Act.VISIT, Act.HOP, Act.FLAP, Act.DANCE, Act.SPIN)
        val all = pool.filter { it != last }
        return all[HudhudMotion.pick(all.size)]
    }

    /** A light touch of the phone's haptics, as its own buttons give. */
    fun tap(view: View = stage) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    /** Plays [act]; [done] runs when he is back on a perch (or at once when it cannot play). */
    @SuppressLint("RtlHardcoded") // the band's left edge on purpose: his place is measured from it in both directions
    fun play(act: Act, done: () -> Unit = {}) {
        if (busy || !Motion.enabled(stage.context) || stage.width == 0 || sky.width == 0) {
            done()
            return
        }
        // Small band: only what fits in it.
        val chosen = if (compact && act != Act.HOP && act != Act.FLAP) Act.HOP else act
        last = chosen
        onEnd = done
        val bird = ImageView(stage.context).apply {
            // Placed by [frame], as FIT_CENTER places the stage's own drawings, or with his head held still in the air.
            scaleType = ImageView.ScaleType.MATRIX
            setImageResource(current())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.INVISIBLE
        }
        // Hung from the band's left and top edges whatever the page's direction, and placed once laid out (lesson 110).
        sky.addView(bird, FrameLayout.LayoutParams(stage.width, stage.height, Gravity.TOP or Gravity.LEFT))
        flier = bird
        // A marker, so [busy] is true from this moment until the layout has placed the bird.
        running = IDLE
        bird.doOnLayout { if (flier === bird && running === IDLE) takeOff(chosen, bird) }
    }

    /** [bird], laid out in the band, is put over the stage and plays [act]. */
    private fun takeOff(act: Act, bird: ImageView) {
        val homeX = leftOf(home)
        val homeY = stage.top.toFloat()
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
        val k = if (compact) COMPACT_K else 1f
        val anim = when (act) {
            Act.FLY -> flight(bird, homeX, homeY, facing, nextPerch(), stay = true)
            Act.VISIT -> visit(bird, homeX, homeY, facing)
            Act.FLAP -> flap(bird, homeX, homeY, k)
            Act.DANCE -> dance(bird, homeX, homeY, facing)
            Act.HOP -> hop(bird, homeX, homeY, k)
            Act.SPIN -> spin(bird, homeX, homeY, facing)
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

    /** A perch other than the one he is on, and not the one he flew to last while another is left. */
    private fun nextPerch(): Int = HudhudMotion.otherPerch(home, lastTarget).also { lastTarget = it }

    /** Ends what is playing at once and puts him back on the perch he is on. */
    fun stop() {
        val a = running ?: return
        running = null
        if (a !== IDLE) a.cancel()
        finish(landed = false)
    }

    private fun finish(landed: Boolean) {
        running = null
        flier?.let { b -> (b.parent as? ViewGroup)?.removeView(b) }
        flier = null
        poses.forEach { it.visibility = View.VISIBLE }
        stage.x = leftOf(home)
        placeShadow(stage.x, 1f)
        if (landed) tap()
        val then = onEnd
        onEnd = null
        then?.invoke()
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

    // ------------------------------------------------------------------------------------------- the flights

    /**
     * A flight from where he stands to [target] (a perch other than home): a crouch and a turn to face the way he goes,
     * an arc inside the band with a slow start and a slow end, the lean of a climb and of a descent, his shadow under him,
     * and a squash and a settle on touching down. [stay]: the perch he lands on becomes home.
     */
    private fun flight(bird: ImageView, fromX: Float, y: Float, facing: Float, target: Int, stay: Boolean): Animator {
        val toX = leftOf(target)
        val dir = if (toX >= fromX) 1f else -1f
        // The drawings face right: bound for the right he looks as drawn, bound for the left he is turned.
        val face = if (dir > 0f) 1f else -1f
        val headroom = (y - EDGE_TOP_DP * dp).coerceAtLeast(0f)
        val arc = HudhudMotion.arc(fromX, y, toX, y, rise = headroom)
        val path = Path().apply {
            moveTo(arc.x0, arc.y0)
            cubicTo(arc.c1x, arc.c1y, arc.c2x, arc.c2y, arc.x1, arc.y1)
        }
        val millis = HudhudMotion.flightMillis(abs(toX - fromX), sky.width.toFloat())

        // Anticipation: down a little, and round to face the way he goes.
        val crouch = ObjectAnimator.ofFloat(bird, View.SCALE_Y, 1f, 0.86f).apply { duration = CROUCH_MS }
        val fly = ObjectAnimator.ofFloat(bird, View.X, View.Y, path).apply {
            duration = millis
            interpolator = PathInterpolator(0.35f, 0f, 0.15f, 1f)
            addUpdateListener {
                val h = if (headroom <= 0f) 0f else ((y - bird.y) / headroom).coerceIn(0f, 1f)
                placeShadow(bird.x, 1f - h)
            }
        }
        val lean = ObjectAnimator.ofFloat(bird, View.ROTATION, 0f, -dir * CLIMB_DEG, 0f, dir * DESCENT_DEG, 0f).apply {
            duration = millis
            interpolator = LinearInterpolator()
        }
        val beat = wings(bird, millis)
        val up = ObjectAnimator.ofFloat(bird, View.SCALE_Y, 0.86f, 1f).apply { duration = 120 }
        val air = AnimatorSet().apply { playTogether(fly, lean, beat, up) }

        // Touching down: a squash at his feet, a small rise, and still.
        val squash = ObjectAnimator.ofPropertyValuesHolder(
            bird, PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.88f, 1.03f, 1f)
        ).apply { duration = LAND_MS }
        val toFace = turn(bird, face, facing)
        val land = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    if (stay) home = target
                    frame(bird, current(), air = false)
                    tap()
                }
            })
        }
        return AnimatorSet().apply {
            playSequentially(
                AnimatorSet().apply { playTogether(crouch, turn(bird, facing, face)) },
                air, land, squash, toFace, settle(bird)
            )
        }
    }

    /** To another perch, a look round, and back to where he began (home does not change). */
    private fun visit(bird: ImageView, x: Float, y: Float, facing: Float): Animator {
        val target = nextPerch()
        val there = flight(bird, x, y, facing, target, stay = false)
        val look = AnimatorSet().apply {
            val away = ObjectAnimator.ofFloat(bird, View.SCALE_X, facing, -facing).apply { duration = TURN_MS; startDelay = LINGER_MS / 2 }
            val back = ObjectAnimator.ofFloat(bird, View.SCALE_X, -facing, facing).apply { duration = TURN_MS; startDelay = 80 }
            playSequentially(away, back)
        }
        val here = ValueAnimator.ofFloat(0f, 1f).apply { duration = LINGER_MS }
        val toX = leftOf(target)
        val backHome = flight(bird, toX, y, facing, home, stay = false)
        return AnimatorSet().apply { playSequentially(there, AnimatorSet().apply { playTogether(here, look) }, backHome) }
    }

    // ------------------------------------------------------------------------------------------- in place

    /** He rises on beating wings, hangs there a moment, and settles. */
    private fun flap(bird: ImageView, homeX: Float, homeY: Float, k: Float): Animator {
        val h = FLAP_RISE_DP * dp * k
        val up = ObjectAnimator.ofFloat(bird, View.Y, homeY, homeY - h).apply {
            duration = 520
            interpolator = DecelerateInterpolator()
        }
        val hover = ObjectAnimator.ofFloat(bird, View.Y, homeY - h, homeY - h * 0.77f, homeY - h).apply { duration = 420 }
        val down = ObjectAnimator.ofFloat(bird, View.Y, homeY - h, homeY).apply {
            duration = 380
            interpolator = AccelerateInterpolator()
        }
        val path = AnimatorSet().apply { playSequentially(up, hover, down) }
        val shade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1320
            addUpdateListener { placeShadow(homeX, 1f - ((homeY - bird.y) / h.coerceAtLeast(1f)).coerceIn(0f, 1f)) }
        }
        return AnimatorSet().apply {
            play(path).with(wings(bird, 1320)).with(shade)
            play(settle(bird)).after(path)
        }
    }

    /** A little dance: side to side, tilting, one wing out then both, with a hop on the third and the sixth beat. */
    private fun dance(bird: ImageView, homeX: Float, homeY: Float, facing: Float): Animator {
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
        val back = ObjectAnimator.ofPropertyValuesHolder(
            bird,
            PropertyValuesHolder.ofFloat(View.X, homeX),
            PropertyValuesHolder.ofFloat(View.ROTATION, 0f),
            PropertyValuesHolder.ofFloat(View.Y, homeY),
            PropertyValuesHolder.ofFloat(View.SCALE_X, facing)
        ).apply { duration = 220 }
        return AnimatorSet().apply { playSequentially(steps + back + settle(bird)) }
    }

    /** Three hops: a crouch, up with his wings raised, down with them folded, and a squash as he lands. */
    private fun hop(bird: ImageView, homeX: Float, homeY: Float, k: Float): Animator {
        val hops = ArrayList<Animator>()
        for (i in 0 until 3) {
            val height = (if (i == 2) 52 else 34) * dp * k
            val crouch = ObjectAnimator.ofFloat(bird, View.SCALE_Y, 1f, 0.86f).apply { duration = 110 }
            val up = ObjectAnimator.ofPropertyValuesHolder(
                bird,
                PropertyValuesHolder.ofFloat(View.Y, homeY, homeY - height),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.86f, 1.06f)
            ).apply {
                duration = 230
                interpolator = DecelerateInterpolator()
                addUpdateListener { placeShadow(homeX, 1f - ((homeY - bird.y) / height.coerceAtLeast(1f)).coerceIn(0f, 1f)) }
            }
            val wingsUp = pose(bird, R.drawable.hudhud_happy, air = true)
            val down = ObjectAnimator.ofPropertyValuesHolder(
                bird,
                PropertyValuesHolder.ofFloat(View.Y, homeY - height, homeY),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.06f, 1f)
            ).apply {
                duration = 210
                interpolator = AccelerateInterpolator()
                addUpdateListener { placeShadow(homeX, 1f - ((homeY - bird.y) / height.coerceAtLeast(1f)).coerceIn(0f, 1f)) }
            }
            val wingsDown = pose(bird, R.drawable.hudhud_idle, air = true)
            hops += AnimatorSet().apply {
                playSequentially(crouch, AnimatorSet().apply { playTogether(up, wingsUp) }, AnimatorSet().apply { playTogether(down, wingsDown) })
            }
        }
        return AnimatorSet().apply { playSequentially(hops + settle(bird)) }
    }

    /** Up a little and round: he turns about twice in the air (as a card turns), and lands facing as before. */
    private fun spin(bird: ImageView, homeX: Float, homeY: Float, facing: Float): Animator {
        val h = 30 * dp
        val up = ObjectAnimator.ofFloat(bird, View.Y, homeY, homeY - h).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
        }
        val turning = ObjectAnimator.ofFloat(bird, View.SCALE_X, facing, -facing, facing, -facing, facing).apply {
            duration = 820
            interpolator = LinearInterpolator()
        }
        val wingsUp = pose(bird, R.drawable.hudhud_happy, air = true)
        val down = ObjectAnimator.ofFloat(bird, View.Y, homeY - h, homeY).apply {
            duration = 260
            startDelay = 560
            interpolator = AccelerateInterpolator()
        }
        val shade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 820
            addUpdateListener { placeShadow(homeX, 1f - ((homeY - bird.y) / h).coerceIn(0f, 1f)) }
        }
        val aloft = AnimatorSet().apply { playTogether(up, turning, wingsUp, down, shade) }
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
        /** A marker for "an act is about to play": [busy] is true while the bird waits for its layout. */
        val IDLE: Animator = ValueAnimator.ofFloat(0f, 1f)

        /** The perches stand this far in from the band's sides. */
        const val EDGE_DP = 16f
        /** He keeps this far below the band's top edge. */
        const val EDGE_TOP_DP = 4f
        /** In a small band the in-place acts are this much of their size. */
        const val COMPACT_K = 0.35f
        const val FLAP_RISE_DP = 26f
        /** One wing beat (raised or folded): about nine changes a second. */
        const val BEAT_MS = 110L
        /** A turn to face the other way. */
        const val TURN_MS = 160L
        /** The crouch before take-off, the settle after touching down, and how long a visit stays. */
        const val CROUCH_MS = 120L
        const val LAND_MS = 260L
        const val LINGER_MS = 900L
        /** The lean of a climb and of a descent, in degrees. */
        const val CLIMB_DEG = 12f
        const val DESCENT_DEG = 9f

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
