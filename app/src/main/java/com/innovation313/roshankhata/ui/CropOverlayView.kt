package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * A rectangle the person can drag by its corners or move as a whole,
 * drawn over a photo.
 *
 * This exists because automatic cropping (finding the QR, or the ink on a
 * signature/stamp photo) is a best-effort guess — it can mistake another
 * dark thing in the frame (a table, a shadow) for the real content, and no
 * amount of threshold-tuning made that reliably tell the two apart. Rather
 * than trust the guess outright, it becomes the STARTING rectangle here,
 * and the person confirms or fixes it before anything is saved — the same
 * pattern a profile-photo or ID-scan cropper uses, which works regardless
 * of what is in the background because the person is the one deciding, not
 * a pixel threshold.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** The current rectangle, in this view's own pixel coordinates. */
    var rect: RectF = RectF()
        set(value) {
            field = value
            invalidate()
        }

    private val scrimPaint = Paint().apply { color = 0x99000000.toInt() }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /** dp, converted once in init — how close a touch must land to a corner to grab it. */
    private val handleTouchRadius = 28f * resources.displayMetrics.density
    private val handleDrawRadius = 5f * resources.displayMetrics.density
    private val minRectSize = 40f * resources.displayMetrics.density

    private enum class Drag { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private var dragging = Drag.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rect.isEmpty) return

        // Scrim everywhere except the crop rect, drawn as four bands rather
        // than a punch-through, since a plain View canvas has no easy "cut a
        // hole in this" primitive without an extra layer.
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, scrimPaint)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, scrimPaint)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, scrimPaint)

        canvas.drawRect(rect, borderPaint)
        for ((cx, cy) in listOf(
            rect.left to rect.top, rect.right to rect.top,
            rect.left to rect.bottom, rect.right to rect.bottom
        )) {
            canvas.drawCircle(cx, cy, handleDrawRadius, handlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragging = nearestHandle(x, y)
                lastTouchX = x
                lastTouchY = y
                return dragging != Drag.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging == Drag.NONE) return false
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                lastTouchX = x
                lastTouchY = y
                applyDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = Drag.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** A corner within reach wins over moving the whole rect, since a corner is the smaller target. */
    private fun nearestHandle(x: Float, y: Float): Drag {
        val corners = listOf(
            Drag.TOP_LEFT to (rect.left to rect.top),
            Drag.TOP_RIGHT to (rect.right to rect.top),
            Drag.BOTTOM_LEFT to (rect.left to rect.bottom),
            Drag.BOTTOM_RIGHT to (rect.right to rect.bottom)
        )
        for ((which, point) in corners) {
            val (cx, cy) = point
            if (abs(x - cx) <= handleTouchRadius && abs(y - cy) <= handleTouchRadius) return which
        }
        return if (rect.contains(x, y)) Drag.MOVE else Drag.NONE
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val r = RectF(rect)
        when (dragging) {
            Drag.MOVE -> r.offset(dx, dy)
            Drag.TOP_LEFT -> { r.left += dx; r.top += dy }
            Drag.TOP_RIGHT -> { r.right += dx; r.top += dy }
            Drag.BOTTOM_LEFT -> { r.left += dx; r.bottom += dy }
            Drag.BOTTOM_RIGHT -> { r.right += dx; r.bottom += dy }
            Drag.NONE -> return
        }

        // Never invert (a corner dragged past its opposite one) and never
        // shrink below a size a finger could still usefully adjust.
        if (r.width() < minRectSize || r.height() < minRectSize) return

        // Keep the whole rect inside the view — clamp by nudging back rather
        // than rejecting the move outright, so a drag that overshoots the
        // edge still tracks the finger right up to the boundary.
        if (r.left < 0) r.offset(-r.left, 0f)
        if (r.top < 0) r.offset(0f, -r.top)
        if (r.right > width) r.offset(width - r.right, 0f)
        if (r.bottom > height) r.offset(0f, height - r.bottom)

        rect = r
    }
}
