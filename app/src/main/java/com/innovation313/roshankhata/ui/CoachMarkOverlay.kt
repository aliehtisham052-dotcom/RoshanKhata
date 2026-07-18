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
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Corner radius of the punched hole, in px.
     *
     * The shape is always the target's own rectangle plus [holePadding], so
     * the lit area is the size of the thing being pointed at and nothing
     * more. Fixed radii were tried and did not work: a circle sized by hand
     * fitted the tile icons and then ran off the edge of the screen on the
     * balance row, because a number is far wider than it is tall. Whatever is
     * highlighted, the hole is measured from it.
     */
    var holeRadius: Float = 0f
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
            val padded = RectF(
                rect.left - holePadding,
                rect.top - holePadding,
                rect.right + holePadding,
                rect.bottom + holePadding
            )
            // Never wider than the hole is round: clamping to half the shorter
            // side keeps a squat shape from bulging into a lens.
            val maxRadius = minOf(padded.width(), padded.height()) / 2f
            val radius = holeRadius.coerceAtMost(maxRadius)
            canvas.drawRoundRect(padded, radius, radius, holePaint)
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
