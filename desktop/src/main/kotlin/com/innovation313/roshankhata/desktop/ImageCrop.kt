package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.awt.Rectangle
import java.awt.image.BufferedImage

/**
 * The first guess at a crop, as the phone's ImageCrop starts from one: the
 * ink on a signature, stamp or QR photo (everything not near-white and not
 * transparent), with a little margin; the whole picture when nothing stands
 * out. The owner then corrects it — a guess is never trusted outright.
 */
object InkBounds {
    fun of(img: BufferedImage): Rectangle {
        var minX = img.width; var minY = img.height; var maxX = -1; var maxY = -1
        val step = maxOf(1, maxOf(img.width, img.height) / 800)
        for (y in 0 until img.height step step) for (x in 0 until img.width step step) {
            val p = img.getRGB(x, y)
            val a = (p ushr 24) and 0xFF
            val lum = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            if (a > 32 && lum < 180) {
                if (x < minX) minX = x; if (y < minY) minY = y
                if (x > maxX) maxX = x; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return Rectangle(0, 0, img.width, img.height)
        val pad = (maxOf(maxX - minX, maxY - minY) * 0.04).toInt() + step
        val l = (minX - pad).coerceAtLeast(0); val t = (minY - pad).coerceAtLeast(0)
        val r = (maxX + pad).coerceAtMost(img.width); val b = (maxY + pad).coerceAtMost(img.height)
        return Rectangle(l, t, maxOf(1, r - l), maxOf(1, b - t))
    }
}

/**
 * The phone's Adjust crop screen: the picture with a box over it; drag a
 * corner to resize, drag inside to move; outside the box is dimmed.
 * [start]: where the box begins (InkBounds, or the whole picture for a logo).
 */
@Composable
fun CropWindowContent(source: BufferedImage, start: Rectangle, onUse: (BufferedImage) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    val bitmap = remember(source) { source.toComposeImageBitmap() }
    val maxW = source.width.toFloat()
    val maxH = source.height.toFloat()
    val min = 16f
    var l by remember { mutableFloatStateOf(start.x.toFloat()) }
    var t by remember { mutableFloatStateOf(start.y.toFloat()) }
    var r by remember { mutableFloatStateOf((start.x + start.width).toFloat()) }
    var b by remember { mutableFloatStateOf((start.y + start.height).toFloat()) }
    // 0..3: a corner (top-start, top-end, bottom-start, bottom-end); 4: the whole box; -1: nothing.
    var handle by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().background(c.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(W.adjust_crop_title), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(word(W.adjust_crop_hint), color = c.textMuted, fontSize = 13.sp)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val scale = minOf(constraints.maxWidth / maxW, constraints.maxHeight / maxH)
            val w = maxW * scale
            val h = maxH * scale
            Canvas(
                Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() }).pointerInput(scale) {
                    detectDragGestures(
                        onDragStart = { p ->
                            val corners = listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).map { it * scale }
                            val hit = corners.indexOfFirst { (it - p).getDistance() < 28f }
                            handle = when {
                                hit >= 0 -> hit
                                p.x in l * scale..r * scale && p.y in t * scale..b * scale -> 4
                                else -> -1
                            }
                        },
                        onDragEnd = { handle = -1 },
                        onDragCancel = { handle = -1 }
                    ) { change, d ->
                        change.consume()
                        val dx = d.x / scale
                        val dy = d.y / scale
                        when (handle) {
                            0 -> { l = (l + dx).coerceIn(0f, r - min); t = (t + dy).coerceIn(0f, b - min) }
                            1 -> { r = (r + dx).coerceIn(l + min, maxW); t = (t + dy).coerceIn(0f, b - min) }
                            2 -> { l = (l + dx).coerceIn(0f, r - min); b = (b + dy).coerceIn(t + min, maxH) }
                            3 -> { r = (r + dx).coerceIn(l + min, maxW); b = (b + dy).coerceIn(t + min, maxH) }
                            4 -> {
                                val mx = dx.coerceIn(-l, maxW - r)
                                val my = dy.coerceIn(-t, maxH - b)
                                l += mx; r += mx; t += my; b += my
                            }
                        }
                    }
                }
            ) {
                drawImage(bitmap, dstSize = IntSize(w.toInt(), h.toInt()))
                val dim = Color.Black.copy(alpha = 0.45f)
                val (x0, y0, x1, y1) = listOf(l * scale, t * scale, r * scale, b * scale)
                drawRect(dim, Offset.Zero, Size(w, y0))
                drawRect(dim, Offset(0f, y1), Size(w, h - y1))
                drawRect(dim, Offset(0f, y0), Size(x0, y1 - y0))
                drawRect(dim, Offset(x1, y0), Size(w - x1, y1 - y0))
                drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(2f))
                listOf(Offset(x0, y0), Offset(x1, y0), Offset(x0, y1), Offset(x1, y1)).forEach {
                    drawCircle(Color.White, 9f, it)
                    drawCircle(Color(0xFF1B5E3A), 9f, it, style = Stroke(2f))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
            FillButton(word(W.adjust_crop_use), c.brandGreen) {
                val x = l.toInt().coerceIn(0, source.width - 1)
                val y = t.toInt().coerceIn(0, source.height - 1)
                val cw = (r.toInt() - x).coerceIn(1, source.width - x)
                val ch = (b.toInt() - y).coerceIn(1, source.height - y)
                onUse(source.getSubimage(x, y, cw, ch))
            }
        }
    }
}
