package com.innovation313.roshankhata.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * A white pad the customer signs on with a finger (6 Oct). Smooth strokes
 * (each move curves through the midpoint), dark ink on white whatever the
 * app's theme — the picture is a document, so it looks the same everywhere.
 */
class SignaturePad @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF14213D.toInt(); style = Paint.Style.STROKE
        strokeWidth = 3.2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9D6CE.toInt(); strokeWidth = resources.displayMetrics.density }
    private val path = Path()
    private var lastX = 0f
    private var lastY = 0f
    private val bounds = RectF()
    var hasInk = false
        private set

    fun clear() { path.reset(); hasInk = false; bounds.setEmpty(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val y = height * 0.78f
        canvas.drawLine(width * 0.06f, y, width * 0.94f, y, line)
        canvas.drawPath(path, ink)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { path.moveTo(e.x, e.y); lastX = e.x; lastY = e.y; grow(e.x, e.y) }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.historySize) step(e.getHistoricalX(i), e.getHistoricalY(i))
                step(e.x, e.y)
                hasInk = true
            }
            MotionEvent.ACTION_UP -> { path.lineTo(e.x, e.y); grow(e.x, e.y); hasInk = true }
        }
        invalidate()
        return true
    }

    private fun step(x: Float, y: Float) {
        path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
        lastX = x; lastY = y
        grow(x, y)
    }

    private fun grow(x: Float, y: Float) {
        if (bounds.isEmpty) bounds.set(x, y, x + 1, y + 1) else bounds.union(x, y)
    }

    /** The signature alone, cropped with a margin, on white. */
    fun toBitmap(): Bitmap {
        val pad = 16 * resources.displayMetrics.density
        val l = max(0f, bounds.left - pad); val t = max(0f, bounds.top - pad)
        val r = min(width.toFloat(), bounds.right + pad); val b = min(height.toFloat(), bounds.bottom + pad)
        val w = max(1, (r - l).toInt()); val h = max(1, (b - t).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.translate(-l, -t)
        c.drawPath(path, ink)
        return bmp
    }
}
