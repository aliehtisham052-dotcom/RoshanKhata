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
 *
 * 18 Oct: kept stroke by stroke, so a slip can be taken back ([undo]) without
 * starting over, and [onInkChanged] tells the screen when there is something
 * to save. A small "✕" at the start of the line shows where to sign, as on
 * a paper form.
 */
class SignaturePad @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF14213D.toInt(); style = Paint.Style.STROKE
        strokeWidth = 3.2f * density
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9C5B8.toInt(); strokeWidth = density }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A877E.toInt(); textSize = 18f * density; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    /** Every finished stroke, oldest first; [current] is the one being drawn. */
    private val strokes = mutableListOf<Path>()
    private var current: Path? = null
    private var lastX = 0f
    private var lastY = 0f
    private val bounds = RectF()

    /** Told true when the first ink goes down, false when the pad is empty again. */
    var onInkChanged: ((Boolean) -> Unit)? = null

    var hasInk = false
        private set(value) {
            if (field != value) { field = value; onInkChanged?.invoke(value) }
        }

    fun clear() { strokes.clear(); current = null; hasInk = false; bounds.setEmpty(); invalidate() }

    /** Take back the last stroke. */
    fun undo() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.size - 1)
        recomputeBounds()
        hasInk = strokes.isNotEmpty()
        invalidate()
    }

    val canUndo get() = strokes.isNotEmpty()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val y = height * 0.74f
        val start = width * 0.06f
        canvas.drawLine(start, y, width * 0.94f, y, line)
        canvas.drawText("\u2715", start, y - 8 * density, mark)
        for (p in strokes) canvas.drawPath(p, ink)
        current?.let { canvas.drawPath(it, ink) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                current = Path().apply { moveTo(e.x, e.y) }
                lastX = e.x; lastY = e.y; grow(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.historySize) step(e.getHistoricalX(i), e.getHistoricalY(i))
                step(e.x, e.y)
            }
            MotionEvent.ACTION_UP -> {
                current?.let { it.lineTo(e.x, e.y); grow(e.x, e.y); strokes.add(it) }
                current = null
                hasInk = true
            }
            MotionEvent.ACTION_CANCEL -> {
                current?.let { strokes.add(it) }
                current = null
                hasInk = strokes.isNotEmpty()
            }
        }
        invalidate()
        return true
    }

    private fun step(x: Float, y: Float) {
        current?.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
        lastX = x; lastY = y
        grow(x, y)
    }

    private fun grow(x: Float, y: Float) {
        if (bounds.isEmpty) bounds.set(x, y, x + 1, y + 1) else bounds.union(x, y)
    }

    private fun recomputeBounds() {
        bounds.setEmpty()
        val r = RectF()
        for (p in strokes) {
            p.computeBounds(r, true)
            if (bounds.isEmpty) bounds.set(r) else bounds.union(r)
        }
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
        for (p in strokes) c.drawPath(p, ink)
        return bmp
    }
}
