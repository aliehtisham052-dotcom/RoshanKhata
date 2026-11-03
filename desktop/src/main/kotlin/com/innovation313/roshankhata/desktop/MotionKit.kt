package com.innovation313.roshankhata.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The computer's side of the 3 Nov 2026 animations (the owner: "implement all animations carefully"): the
 * moments the phone now animates, drawn here with Compose — a sale saved, a backup done, a restore's steps,
 * a cheque's stamp, the drawer's difference, a document's chain, live sync's traffic. As Motion.kt says for
 * this window: short and quiet, nothing bounces or spins. The curves are Material's own
 * (material-components-android, docs/theming/Motion.md).
 */
object DeskMotion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
}

internal fun motionSeg(v: Float, a: Float, b: Float): Float = ((v - a) / (b - a)).coerceIn(0f, 1f)

/**
 * A check in a ring that draws itself (3 Nov 2026): the ring sweeps round, the check is traced from its
 * half-way. A new [key] draws it again (the next sale, the next backup). [fill], when given, grows in under
 * both — a done step's disc.
 */
@Composable
fun DrawnCheck(
    key: Any?,
    ring: Color,
    check: Color,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    size: Dp = 28.dp,
    stroke: Dp = 2.5.dp
) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(key) {
        p.snapTo(0f)
        p.animateTo(1f, tween(560, easing = DeskMotion.Standard))
    }
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension
        val sw = stroke.toPx()
        val v = p.value
        fill?.let {
            val k = motionSeg(v, 0f, 0.35f)
            drawCircle(it, radius = w / 2f * (0.6f + 0.4f * k), alpha = k)
        }
        drawArc(
            ring, startAngle = -90f, sweepAngle = 360f * motionSeg(v, 0f, 0.55f), useCenter = false,
            topLeft = Offset(sw / 2f, sw / 2f), size = Size(w - sw, w - sw), style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        val q = motionSeg(v, 0.35f, 1f)
        val a = Offset(w * 0.28f, w * 0.52f)
        val b = Offset(w * 0.44f, w * 0.68f)
        val c = Offset(w * 0.74f, w * 0.36f)
        val ab = (b - a).getDistance()
        val bc = (c - b).getDistance()
        val d = (ab + bc) * q
        if (d > 0f) {
            drawLine(check, a, if (d < ab) a + (b - a) * (d / ab) else b, strokeWidth = sw, cap = StrokeCap.Round)
            if (d > ab) drawLine(check, b, b + (c - b) * ((d - ab) / bc), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}
