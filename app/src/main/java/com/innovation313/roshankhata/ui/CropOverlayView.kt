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
    /** dp, converted once in init — how close a touch must land to a corner or an edge to grab it. */
    private val handleTouchRadius = 32f * resources.displayMetrics.density
    private val bracketLength = 22f * resources.displayMetrics.density
    private val bracketPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }
    private val thirdsPaint = Paint().apply {
        color = 0x66FFFFFF
        strokeWidth = 1f * resources.displayMetrics.density
    }
    private val minRectSize = 40f * resources.displayMetrics.density

    /**
     * Where the picture is drawn in this view (10 Nov 2026): the box stays on the picture, never on the
     * letterbox around it. Empty means the whole view.
     */
    var bounds: RectF = RectF()
        set(value) { field = value; invalidate() }

    private enum class Drag { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, TOP, RIGHT, BOTTOM }
    private var dragging = Drag.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (rect.isEmpty) return

        // Scrim everywhere except the crop rect, drawn as four bands.
        canvas.drawRect(0f, 0f, width.toFloat(), rect.top, scrimPaint)
        canvas.drawRect(0f, rect.bottom, width.toFloat(), height.toFloat(), scrimPaint)
        canvas.drawRect(0f, rect.top, rect.left, rect.bottom, scrimPaint)
        canvas.drawRect(rect.right, rect.top, width.toFloat(), rect.bottom, scrimPaint)

        // Thirds while a finger is on it, the way a phone's own crop shows them.
        if (dragging != Drag.NONE) {
            for (k in 1..2) {
                val x = rect.left + rect.width() * k / 3f
                val y = rect.top + rect.height() * k / 3f
                canvas.drawLine(x, rect.top, x, rect.bottom, thirdsPaint)
                canvas.drawLine(rect.left, y, rect.right, y, thirdsPaint)
            }
        }
        canvas.drawRect(rect, borderPaint)
        // Thick L-shaped corners (10 Nov 2026, the owner: five-pixel dots read as nothing to hold).
        val l = minOf(bracketLength, rect.width() / 3f, rect.height() / 3f)
        canvas.drawLine(rect.left, rect.top, rect.left + l, rect.top, bracketPaint)
        canvas.drawLine(rect.left, rect.top, rect.left, rect.top + l, bracketPaint)
        canvas.drawLine(rect.right, rect.top, rect.right - l, rect.top, bracketPaint)
        canvas.drawLine(rect.right, rect.top, rect.right, rect.top + l, bracketPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left + l, rect.bottom, bracketPaint)
        canvas.drawLine(rect.left, rect.bottom, rect.left, rect.bottom - l, bracketPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right - l, rect.bottom, bracketPaint)
        canvas.drawLine(rect.right, rect.bottom, rect.right, rect.bottom - l, bracketPaint)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        keepCornersFromBackGesture()
    }

    /**
     * The phone's back gesture owns the screen's side edges; a corner sitting there could not be dragged (10 Nov
     * 2026, the owner). The corners' squares are asked back from it (Android 10+; the system allows them).
     */
    private fun keepCornersFromBackGesture() {
        if (android.os.Build.VERSION.SDK_INT < 29 || rect.isEmpty) return
        val r = handleTouchRadius.toInt()
        systemGestureExclusionRects = listOf(
            rect.left to rect.top, rect.right to rect.top, rect.left to rect.bottom, rect.right to rect.bottom
        ).map { (x, y) -> android.graphics.Rect(x.toInt() - r, y.toInt() - r, x.toInt() + r, y.toInt() + r) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = nearestHandle(x, y)
                lastTouchX = x
                lastTouchY = y
                if (dragging != Drag.NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                }
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
                keepCornersFromBackGesture()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** A corner wins over an edge, an edge over moving the whole box: the smaller target first. */
    private fun nearestHandle(x: Float, y: Float): Drag {
        val t = handleTouchRadius
        val corners = listOf(
            Drag.TOP_LEFT to (rect.left to rect.top),
            Drag.TOP_RIGHT to (rect.right to rect.top),
            Drag.BOTTOM_LEFT to (rect.left to rect.bottom),
            Drag.BOTTOM_RIGHT to (rect.right to rect.bottom)
        )
        for ((which, point) in corners) {
            val (cx, cy) = point
            if (abs(x - cx) <= t && abs(y - cy) <= t) return which
        }
        val withinX = x in (rect.left - t)..(rect.right + t)
        val withinY = y in (rect.top - t)..(rect.bottom + t)
        return when {
            withinY && abs(x - rect.left) <= t -> Drag.LEFT
            withinY && abs(x - rect.right) <= t -> Drag.RIGHT
            withinX && abs(y - rect.top) <= t -> Drag.TOP
            withinX && abs(y - rect.bottom) <= t -> Drag.BOTTOM
            rect.contains(x, y) -> Drag.MOVE
            else -> Drag.NONE
        }
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val area = if (bounds.isEmpty) RectF(0f, 0f, width.toFloat(), height.toFloat()) else bounds
        val r = RectF(rect)
        if (dragging == Drag.MOVE) {
            // The whole box moves, held inside the picture.
            r.offset(
                clamp(dx, area.left - r.left, area.right - r.right),
                clamp(dy, area.top - r.top, area.bottom - r.bottom)
            )
            rect = r
            return
        }
        // One corner or edge moves; the opposite sides stay put. Each side is held inside the picture and at
        // least minRectSize from its opposite (it used to shift the whole box at the screen's edge).
        if (dragging == Drag.LEFT || dragging == Drag.TOP_LEFT || dragging == Drag.BOTTOM_LEFT)
            r.left = clamp(r.left + dx, area.left, r.right - minRectSize)
        if (dragging == Drag.RIGHT || dragging == Drag.TOP_RIGHT || dragging == Drag.BOTTOM_RIGHT)
            r.right = clamp(r.right + dx, r.left + minRectSize, area.right)
        if (dragging == Drag.TOP || dragging == Drag.TOP_LEFT || dragging == Drag.TOP_RIGHT)
            r.top = clamp(r.top + dy, area.top, r.bottom - minRectSize)
        if (dragging == Drag.BOTTOM || dragging == Drag.BOTTOM_LEFT || dragging == Drag.BOTTOM_RIGHT)
            r.bottom = clamp(r.bottom + dy, r.top + minRectSize, area.bottom)
        rect = r
    }

    /** coerceIn that never throws: a picture smaller than the box's least size leaves the value as it was. */
    private fun clamp(v: Float, lo: Float, hi: Float): Float = if (lo > hi) v else v.coerceIn(lo, hi)
}
