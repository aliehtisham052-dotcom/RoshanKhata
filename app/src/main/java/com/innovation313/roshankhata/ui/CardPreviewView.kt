package com.innovation313.roshankhata.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * The live business card on the Business Card screen (4 Oct 2026).
 *
 * Draws the chosen design straight onto the screen at the view's width — no
 * bitmap per frame — and drives its moving parts (a needle, flowing rays,
 * cells that light) through one looping phase. A new design arrives with a
 * short 3D turn. The image that is SHARED never comes from here: it is drawn
 * still, by the activity, so the owner always sends the resting card.
 *
 * Nothing moves when the phone's animations are switched off (a user choice,
 * and the setting the test emulator runs with).
 */
class CardPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var template: CardTemplates.Template? = null
    private var data: CardTemplates.CardData? = null
    private var phase = 0f
    private val clip = Path()
    private val corner = 12f * resources.displayMetrics.density

    private val loop = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private fun motionOn(): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } catch (e: Exception) {
        true
    }

    /** Show [t] filled with [d]. A change of design plays the turn-in. */
    fun show(t: CardTemplates.Template, d: CardTemplates.CardData) {
        val changed = template != null && template?.id != t.id
        template = t
        data = d
        if (changed && motionOn()) {
            cameraDistance = 9000f * resources.displayMetrics.density
            rotationY = 55f
            alpha = 0.2f
            animate().rotationY(0f).alpha(1f).setDuration(520L)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w * CardTemplates.H / CardTemplates.W)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        clip.reset()
        clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), corner, corner, Path.Direction.CW)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (motionOn()) loop.start()
    }

    override fun onDetachedFromWindow() {
        loop.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible && motionOn()) { if (!loop.isStarted) loop.start() } else loop.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val t = template ?: return
        val d = data ?: return
        val moving = loop.isRunning
        canvas.save()
        canvas.clipPath(clip)
        val s = width.toFloat() / CardTemplates.W
        canvas.scale(s, s)
        t.draw(canvas, d, CardTemplates.W, CardTemplates.H, if (moving) phase else -1f)
        if (t.shine && moving) shine(canvas)
        canvas.restore()
    }

    /** A soft diagonal band of light across the darker cards. */
    private fun shine(c: Canvas) {
        val x = -400f + (CardTemplates.W + 800f) * phase
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                x - 160f, 0f, x + 160f, 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(46, 255, 255, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
        c.save()
        c.skew(-0.35f, 0f)
        c.drawRect(x - 160f, 0f, x + 160f + CardTemplates.H, CardTemplates.H.toFloat(), p)
        c.restore()
    }
}
