package com.innovation313.roshankhata.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * The computer's motion (1.8.3, the owner asked for a professional feel): short
 * and quiet, as Windows' own controls. A colour change takes [QUICK] ms, a page
 * [PAGE] ms; nothing bounces or spins, so a slow laptop stays quick.
 */
object Motion {
    const val QUICK = 160
    const val PAGE = 200
}

/** A colour that eases to its new value instead of jumping (a pill chosen, a row opened). */
@Composable
fun eased(target: Color): Color = animateColorAsState(target, tween(Motion.QUICK), label = "eased").value

/**
 * Something to click, as a mouse expects it: the hand pointer, a soft wash while
 * the pointer is over it, the ripple on a click. Put it after the control's own
 * fill so the wash lies over the fill and under the words. [wash]: the hover
 * colour, darker for a filled button.
 */
@Composable
fun Modifier.press(shape: Shape = RectangleShape, wash: Color = Look.hover, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val over = animateColorAsState(if (hovered && enabled) wash else Color.Transparent, tween(Motion.QUICK), label = "hover").value
    return this.clip(shape)
        .hoverable(source, enabled)
        .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
        .background(over, shape)
        .clickable(interactionSource = source, indication = ripple(), enabled = enabled, onClick = onClick)
}
