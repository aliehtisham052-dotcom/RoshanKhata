package com.innovation313.roshankhata.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.innovation313.roshankhata.screens.RoshanPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The computer's control colours measured, not eyeballed (1.8.3): a pill's or
 * outlined button's edge at least 3:1 on every ground it sits on (WCAG 1.4.11;
 * the old edge was 1.28:1), a chosen pill's fill 3:1 on its ground and its
 * words 4.5:1 on the fill, by day and by night.
 */
class LookContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val x = a.luminance() + 0.05
        val y = b.luminance() + 0.05
        return maxOf(x, y).toDouble() / minOf(x, y)
    }

    private fun atLeast(min: Double, what: String, a: Color, b: Color) {
        val r = ratio(a, b)
        assertTrue("$what: %.2f:1, needs %.1f".format(r, min), r >= min)
    }

    @Test
    fun `control edges stand out by day and by night`() {
        val light = RoshanPalette.light
        val dark = RoshanPalette.dark
        for ((name, ground) in listOf("page" to light.pageBg, "card" to light.surface, "rail" to LookPalette.RAIL_LIGHT)) {
            atLeast(3.0, "light edge on $name", LookPalette.EDGE_LIGHT, ground)
            atLeast(3.0, "light chosen fill on $name", LookPalette.PICKED_LIGHT, ground)
        }
        for ((name, ground) in listOf("page" to dark.pageBg, "card" to dark.surface, "rail" to LookPalette.RAIL_DARK)) {
            atLeast(3.0, "dark edge on $name", LookPalette.EDGE_DARK, ground)
            atLeast(3.0, "dark chosen fill on $name", LookPalette.PICKED_DARK, ground)
        }
        atLeast(4.5, "words on the light chosen fill", Color.White, LookPalette.PICKED_LIGHT)
        atLeast(4.5, "words on the dark chosen fill", LookPalette.ON_PICKED_DARK, LookPalette.PICKED_DARK)
    }

    @Test
    fun `text keeps its contrast under the hover wash`() {
        // The wash (Look.hover) laid over the card: ink must still read at 4.5:1 or better.
        fun over(base: Color, wash: Color) = Color(
            red = base.red * (1 - wash.alpha) + wash.red * wash.alpha,
            green = base.green * (1 - wash.alpha) + wash.green * wash.alpha,
            blue = base.blue * (1 - wash.alpha) + wash.blue * wash.alpha
        )
        val light = RoshanPalette.light
        val dark = RoshanPalette.dark
        atLeast(4.5, "light ink on hovered card", light.ink, over(light.surface, Color.Black.copy(alpha = 0.05f)))
        atLeast(4.5, "light muted on hovered card", light.textMuted, over(light.surface, Color.Black.copy(alpha = 0.05f)))
        atLeast(4.5, "dark ink on hovered card", dark.ink, over(dark.surface, Color.White.copy(alpha = 0.08f)))
        atLeast(4.5, "dark muted on hovered card", dark.textMuted, over(dark.surface, Color.White.copy(alpha = 0.08f)))
    }
}
