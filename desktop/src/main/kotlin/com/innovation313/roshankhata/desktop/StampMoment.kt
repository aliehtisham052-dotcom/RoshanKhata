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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.innovation313.roshankhata.data.SealMark
import com.innovation313.roshankhata.desktop.gfx.Bitmap
import com.innovation313.roshankhata.desktop.gfx.Canvas
import com.innovation313.roshankhata.desktop.invoice.InvoiceTemplateKit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.AlphaComposite
import java.awt.geom.Ellipse2D
import kotlin.random.Random

/**
 * The phone's StampMoment on the computer (10 Nov 2026, the owner): the Roshan Khata seal pressed onto a paper as its
 * preview opens, with the date and word printed on it. The stamp comes down large and faint, strikes in 200 ms, is
 * pressed a touch flat, an ink ring spreads from its rim, the paper dips, it eases back at the seal's 8 degrees, rests
 * a moment and the cover lifts. A click skips it. Nothing bounces.
 */
@Composable
fun StampMoment(mark: SealMark.Mark, ownStamp: ImageBitmap? = null, onDone: () -> Unit) {
    // The shop's own stamp when that is the paper's seal (as kept, no grain), else the Roshan Khata seal.
    val picture = remember(mark, ownStamp) { ownStamp?.takeIf { mark.own } ?: sealPicture(560, mark) }
    val cover = remember { Animatable(0f) }
    val scaleX = remember { Animatable(2.3f) }
    val scaleY = remember { Animatable(2.3f) }
    val turn = remember { Animatable(-22f) }
    val drop = remember { Animatable(-36f) }
    val seen = remember { Animatable(0f) }
    val ring = remember { Animatable(0.96f) }
    val ringAlpha = remember { Animatable(0f) }
    val paper = remember { Animatable(1f) }
    val decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val accelerate = CubicBezierEasing(0.55f, 0f, 1f, 0.45f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    LaunchedEffect(mark) {
        cover.animateTo(1f, tween(160))
        coroutineScope {
            launch { scaleX.animateTo(0.93f, tween(200, easing = accelerate)) }
            launch { scaleY.animateTo(0.9f, tween(200, easing = accelerate)) }
            launch { turn.animateTo(InvoiceTemplateKit.SEAL_TILT, tween(200, easing = accelerate)) }
            launch { drop.animateTo(0f, tween(200, easing = accelerate)) }
            launch { seen.animateTo(1f, tween(200, easing = accelerate)) }
        }
        coroutineScope {
            launch { scaleX.animateTo(1f, tween(220, easing = decelerate)) }
            launch { scaleY.animateTo(1f, tween(220, easing = decelerate)) }
            launch {
                ringAlpha.snapTo(0.55f)
                launch { ring.animateTo(1.42f, tween(460, easing = decelerate)) }
                ringAlpha.animateTo(0f, tween(460, easing = decelerate))
            }
            launch {
                paper.animateTo(0.975f, tween(110, easing = standard))
                paper.animateTo(1f, tween(130, easing = standard))
            }
        }
        delay(620)
        cover.animateTo(0f, tween(240, easing = standard))
        onDone()
    }

    Box(
        Modifier.fillMaxSize().alpha(cover.value).background(Color(0x8C0B1A12))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(300.dp).graphicsLayer { this.scaleX = paper.value; this.scaleY = paper.value }
                .shadow(12.dp, RoundedCornerShape(20.dp)).background(Color.White, RoundedCornerShape(20.dp))
        )
        Box(
            Modifier.size(214.dp).graphicsLayer { this.scaleX = ring.value; this.scaleY = ring.value; alpha = ringAlpha.value }
                .border(5.dp, Color(InvoiceTemplateKit.SEAL_INK), CircleShape)
        )
        Image(
            picture, contentDescription = null,
            modifier = Modifier.size(228.dp).graphicsLayer {
                this.scaleX = scaleX.value; this.scaleY = scaleY.value
                rotationZ = turn.value; translationY = drop.value * density; alpha = seen.value
            }
        )
    }
}

/** The seal as printed, with a rubber stamp's grain (the same specks every time). */
private fun sealPicture(side: Int, mark: SealMark.Mark): ImageBitmap {
    val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    InvoiceTemplateKit.drawRecordSeal(Canvas(bmp), side / 2f, side / 2f, side * 0.96f, mark.middle, mark.below, tilt = 0f)
    SealMark.clear()
    val img = bmp.image
    val g = img.createGraphics()
    val rnd = Random(313)
    repeat(420) {
        g.composite = AlphaComposite.getInstance(AlphaComposite.DST_OUT, (90 + rnd.nextInt(140)) / 255f)
        val r = side * (0.002f + rnd.nextFloat() * 0.005f)
        g.fill(Ellipse2D.Float(rnd.nextFloat() * side - r, rnd.nextFloat() * side - r, 2 * r, 2 * r))
    }
    g.dispose()
    return img.toComposeImageBitmap()
}
