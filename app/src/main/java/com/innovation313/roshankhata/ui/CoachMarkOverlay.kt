package com.innovation313.roshankhata.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

/**
 * The dimmed backdrop for a coach mark: a translucent scrim over the whole
 * screen with a rounded-rect "hole" punched out around the current target
 * view, so that one control stays lit while everything else steps back.
 *
 * Drawing, not touch handling, is this view's whole job — MainActivity adds
 * it as the top child of the root layout and positions the explanation
 * bubble separately, next to the hole this view reports via [holeRect].
 */
class CoachMarkOverlay(context: Context) : View(context) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC0F1A14")
    }

    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    /** Screen-space bounds of the highlighted control, or null to dim everything. */
    var holeRect: RectF? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Extra breathing room, in px, drawn around the target view's real bounds. */
    var holePadding: Float = 0f

    /** Corner radius of the punched hole, in px. Ignored when [circular]. */
    var holeRadius: Float = 0f

    /**
     * Draw the hole as a circle around the target's centre rather than a
     * rounded rectangle. A tile is mostly empty space around a small icon, so
     * a rectangle the size of the whole tile lights far more than the thing
     * being pointed at; a circle sized to the icon reads as a spotlight.
     */
    var circular: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Radius of the circular hole, in px. Used only when [circular]. */
    var circleRadius: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        // This view paints its own hole with CLEAR; it must not be flattened
        // into an opaque layer or the clear would show as black instead of see-through.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        holeRect?.let { rect ->
            if (circular) {
                canvas.drawCircle(rect.centerX(), rect.centerY(), circleRadius, holePaint)
            } else {
                val padded = RectF(
                    rect.left - holePadding,
                    rect.top - holePadding,
                    rect.right + holePadding,
                    rect.bottom + holePadding
                )
                canvas.drawRoundRect(padded, holeRadius, holeRadius, holePaint)
            }
        }
    }

    companion object {
        /** The target view's bounds, in the coordinate space of [root]. */
        fun boundsWithin(target: View, root: View): RectF {
            val targetLoc = IntArray(2)
            val rootLoc = IntArray(2)
            target.getLocationOnScreen(targetLoc)
            root.getLocationOnScreen(rootLoc)
            val left = (targetLoc[0] - rootLoc[0]).toFloat()
            val top = (targetLoc[1] - rootLoc[1]).toFloat()
            return RectF(left, top, left + target.width, top + target.height)
        }

        /** Screen-space bounds of the target, e.g. for a BottomNavigationView item. */
        fun screenBounds(target: View): Rect {
            val out = Rect()
            target.getGlobalVisibleRect(out)
            return out
        }
    }
}
