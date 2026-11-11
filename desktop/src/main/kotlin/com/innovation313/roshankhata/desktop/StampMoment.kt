package com.innovation313.roshankhata.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.innovation313.roshankhata.data.SealMark
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.invoice.InvoiceTemplateKit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The phone's StampMoment on the computer, on the page itself (11 Nov 2026, the owner: no card of its own — the stamp
 * lands on the PDF where it is printed, with the shop's name it carries there). Drawn over the last page, which is
 * [pageW] x [pageH]: the printed seal's spot is covered with paper, the stamp comes down onto it large and faint,
 * strikes in 200 ms, the cover goes and an ink ring spreads, it eases back to the seal's size and angle exactly on the
 * printed seal, and fades into it. A click ends it. Nothing bounces. Nothing is drawn when the place is not known.
 */
@Composable
fun StampMoment(mark: SealMark.Mark, ownStamp: ImageBitmap?, pageW: Dp, pageH: Dp, onDone: () -> Unit) {
    if (!mark.placed || (mark.own && ownStamp == null)) {
        LaunchedEffect(mark) { onDone() }
        return
    }
    val side = pageW * mark.size
    val left = pageW * mark.x - side / 2
    val top = pageH * mark.y - side / 2
    val picture = remember(mark, ownStamp) { if (mark.own) ownStamp!! else sealPicture(560, mark) }
    var covered by remember { mutableStateOf(true) }
    val seen = remember { Animatable(0f) }
    val scaleX = remember { Animatable(1.9f) }
    val scaleY = remember { Animatable(1.9f) }
    val turn = remember { Animatable(InvoiceTemplateKit.SEAL_TILT - 14f) }
    val drop = remember { Animatable(-48f) }
    val lifted = remember { Animatable(10f) }
    val ring = remember { Animatable(0.98f) }
    val ringAlpha = remember { Animatable(0f) }
    val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val accelerate = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    LaunchedEffect(mark) {
        coroutineScope {
            launch { seen.animateTo(0.85f, tween(180, easing = decelerate)) }
            launch { scaleX.animateTo(1.7f, tween(180, easing = decelerate)) }
            launch { scaleY.animateTo(1.7f, tween(180, easing = decelerate)) }
        }
        coroutineScope {
            launch { scaleX.animateTo(0.95f, tween(200, easing = accelerate)) }
            launch { scaleY.animateTo(0.92f, tween(200, easing = accelerate)) }
            launch { turn.animateTo(InvoiceTemplateKit.SEAL_TILT, tween(200, easing = accelerate)) }
            launch { drop.animateTo(0f, tween(200, easing = accelerate)) }
            launch { seen.animateTo(1f, tween(200, easing = accelerate)) }
        }
        covered = false
        coroutineScope {
            launch { scaleX.animateTo(1f, tween(220, easing = decelerate)) }
            launch { scaleY.animateTo(1f, tween(220, easing = decelerate)) }
            launch { lifted.animateTo(0f, tween(220, easing = decelerate)) }
            launch {
                ringAlpha.snapTo(0.5f)
                launch { ring.animateTo(1.45f, tween(460, easing = decelerate)) }
                ringAlpha.animateTo(0f, tween(460, easing = decelerate))
            }
        }
        delay(300)
        seen.animateTo(0f, tween(220, easing = standard))
        onDone()
    }

    Box(
        Modifier.size(pageW, pageH)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() }
    ) {
        if (covered) Box(Modifier.offset(left - side * 0.02f, top - side * 0.02f).size(side * 1.04f).background(Color.White, CircleShape))
        Box(
            Modifier.offset(left, top).size(side).graphicsLayer { this.scaleX = ring.value; this.scaleY = ring.value; alpha = ringAlpha.value }
                .border(3.dp, Color(InvoiceTemplateKit.SEAL_INK), CircleShape)
        )
        Image(
            picture, contentDescription = null,
            modifier = Modifier.offset(left, top).size(side).graphicsLayer {
                this.scaleX = scaleX.value; this.scaleY = scaleY.value
                rotationZ = turn.value; translationY = drop.value * density; alpha = seen.value
                shadowElevation = lifted.value * density; shape = CircleShape; clip = false
            }
        )
    }
}

/** The seal exactly as printed, unturned (the layer turns it to the printed angle). */
private fun sealPicture(side: Int, mark: SealMark.Mark): ImageBitmap {
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    InvoiceTemplateKit.drawRecordSeal(Canvas(bmp), side / 2f, side / 2f, side.toFloat(), mark.middle, mark.below, tilt = 0f, top = mark.top)
    SealMark.clear()
    return bmp.image.toComposeImageBitmap()
}
