package com.innovation313.roshankhata.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import kotlinx.coroutines.launch

/** Which road the window draws as in use. */
enum class LiveRoad { WIFI, RELAY, DOWN, IDLE }

/**
 * Live sync's two roads on the computer (3 Nov 2026, the phone's LiveTrafficView and the owner's mockup): this
 * computer at the start, the phone at the end, the shop's WiFi straight between and the relay over a cloud. The
 * road in use is green; the other a grey dash; with neither answering both are dashes and the cloud wears a red
 * cross. Three gold dots travel only when a round really carried changes ([LiveDesk.traffic]).
 */
@Composable
fun LiveRoads(road: LiveRoad, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    val green = Look.accent
    val grey = c.textMuted.copy(alpha = 0.55f)
    val red = c.owedToMe
    val ink = c.ink
    val card = Look.layer
    val gold = Color(0xFFD4A02A)
    val t = remember { Animatable(-1f) }
    // The run on show: by which road and which way (true: from this computer).
    var runRelay by remember { mutableStateOf(false) }
    var runOut by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        // One run after another: a round that carried both ways shows out, then in. Quiet rounds never come here.
        val waiting = kotlinx.coroutines.channels.Channel<Pair<Boolean, Boolean>>(capacity = 2, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
        kotlinx.coroutines.coroutineScope {
            launch { LiveDesk.traffic.collect { tr -> if (tr.out) waiting.trySend(tr.relay to true); if (tr.came) waiting.trySend(tr.relay to false) } }
            for ((relay, out) in waiting) {
                runRelay = relay
                runOut = out
                t.snapTo(0f)
                t.animateTo(RUN_MS, tween(RUN_MS.toInt(), easing = LinearEasing))
                t.snapTo(-1f)
            }
        }
    }
    Canvas(modifier.fillMaxWidth().height(80.dp)) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val w = size.width
        val dp1 = 1.dp.toPx()
        val roadY = 52 * dp1
        val pcX = if (rtl) w - 40 * dp1 else 40 * dp1
        val phoneX = if (rtl) 28 * dp1 else w - 28 * dp1
        val dir = if (phoneX > pcX) 1f else -1f
        val wifi = Path().apply { moveTo(pcX + dir * 30 * dp1, roadY); lineTo(phoneX - dir * 16 * dp1, roadY) }
        val relayPath = Path().apply {
            moveTo(pcX, roadY - 18 * dp1)
            cubicTo(pcX + dir * w * 0.22f, 4 * dp1, phoneX - dir * w * 0.22f, 4 * dp1, phoneX, roadY - 22 * dp1)
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(5 * dp1, 6 * dp1))
        val sw = 2.4f * dp1
        drawPath(relayPath, if (road == LiveRoad.RELAY) green else grey, style = Stroke(sw, cap = StrokeCap.Round, pathEffect = if (road == LiveRoad.RELAY) null else dash))
        drawPath(wifi, if (road == LiveRoad.WIFI) green else grey, style = Stroke(sw, cap = StrokeCap.Round, pathEffect = if (road == LiveRoad.WIFI) null else dash))

        // The cloud: three puffs as one outline, on the card's own colour.
        val cx = w / 2f
        val cy = 14 * dp1
        fun puff(x: Float, y: Float, r: Float) = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), r)) }
        val two = Path().apply { op(puff(cx - 7 * dp1, cy + 2 * dp1, 6 * dp1), puff(cx + 1 * dp1, cy - 2 * dp1, 8 * dp1), androidx.compose.ui.graphics.PathOperation.Union) }
        val cloud = Path().apply { op(two, puff(cx + 9 * dp1, cy + 3 * dp1, 5 * dp1), androidx.compose.ui.graphics.PathOperation.Union) }
        drawPath(cloud, card)
        drawPath(cloud, if (road == LiveRoad.RELAY) green else grey, style = Stroke(2 * dp1))
        if (road == LiveRoad.DOWN) {
            drawLine(red, Offset(cx - 4 * dp1, cy - 3 * dp1), Offset(cx + 4 * dp1, cy + 5 * dp1), sw, StrokeCap.Round)
            drawLine(red, Offset(cx + 4 * dp1, cy - 3 * dp1), Offset(cx - 4 * dp1, cy + 5 * dp1), sw, StrokeCap.Round)
        }

        device(pcX, roadY, dp1, ink, card, computer = true)
        device(phoneX, roadY, dp1, ink, card, computer = false)

        val now = t.value
        if (now < 0f) return@Canvas
        val measure = PathMeasure().apply { setPath(if (runRelay) relayPath else wifi, false) }
        val length = measure.length
        for (k in 0 until 3) {
            val q = DeskMotion.Standard.transform(motionSeg(now, k * 90f, k * 90f + 560f))
            if (q <= 0f || q >= 1f) continue
            val at = measure.getPosition(length * (if (runOut) q else 1f - q))
            val o = minOf(1f, q / 0.1f, (1f - q) / 0.1f)
            drawCircle(card, 6.5f * dp1, at, alpha = o)
            drawCircle(gold, 5 * dp1, at, alpha = o)
        }
        val pk = motionSeg(now, 740f, 1040f)
        if (pk > 0f && pk < 1f) {
            val x = if (runOut) phoneX else pcX
            drawCircle(green, (28 + 10 * pk) * dp1, Offset(x, roadY - 2 * dp1), alpha = 0.35f * (1f - pk), style = Stroke(2 * dp1))
        }
    }
}

/** A device: the phone a tall rounded slab, the computer a screen on a stand. */
private fun DrawScope.device(x: Float, y: Float, dp1: Float, ink: Color, card: Color, computer: Boolean) {
    val line = Stroke(2.2f * dp1)
    if (computer) {
        val topLeft = Offset(x - 26 * dp1, y - 18 * dp1)
        val size = Size(52 * dp1, 32 * dp1)
        drawRoundRect(card, topLeft, size, CornerRadius(3 * dp1))
        drawRoundRect(ink, topLeft, size, CornerRadius(3 * dp1), style = line)
        drawLine(ink, Offset(x, y + 14 * dp1), Offset(x, y + 21 * dp1), 2.2f * dp1)
        drawLine(ink, Offset(x - 9 * dp1, y + 21 * dp1), Offset(x + 9 * dp1, y + 21 * dp1), 2.2f * dp1, StrokeCap.Round)
    } else {
        val topLeft = Offset(x - 12 * dp1, y - 22 * dp1)
        val size = Size(24 * dp1, 42 * dp1)
        drawRoundRect(card, topLeft, size, CornerRadius(5 * dp1))
        drawRoundRect(ink, topLeft, size, CornerRadius(5 * dp1), style = line)
        drawLine(ink, Offset(x - 4 * dp1, y + 14 * dp1), Offset(x + 4 * dp1, y + 14 * dp1), 2.2f * dp1, StrokeCap.Round)
    }
}

private const val RUN_MS = 1_100f
