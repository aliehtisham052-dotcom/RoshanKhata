package com.innovation313.roshankhata.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colours for the shared Compose screens (KMP Phase 5), the same
 * values as app/src/main/res/values/colors.xml and values-night/colors.xml.
 * A screen asks for a role (ink, surface...), never a hex code, so light and
 * dark stay in one place — as the XML screens do.
 */
@Immutable
data class RoshanColors(
    val pageBg: Color,
    val surface: Color,
    val ink: Color,
    val inkSoft: Color,
    val textMuted: Color,
    val brandGreen: Color,
    val brandGreenText: Color,
    val sectionKhata: Color,
    val onDark: Color,
    /** A balance the customer owes (bal_owed_to_me) and one the shop owes (bal_i_owe). */
    val owedToMe: Color,
    val iOwe: Color,
    /** The You gave button and pill (red_gave; the same in dark). */
    val gaveFill: Color
)

object RoshanPalette {
    val light = RoshanColors(
        pageBg = Color(0xFFF7F6F2),
        surface = Color(0xFFFFFFFF),
        ink = Color(0xFF1A1A18),
        inkSoft = Color(0xFF5F5E5A),
        textMuted = Color(0xFF6B6B66),
        brandGreen = Color(0xFF1B5E3A),
        brandGreenText = Color(0xFF1B5E3A),
        sectionKhata = Color(0xFF1B5E3A),
        onDark = Color(0xFFFFFFFF),
        owedToMe = Color(0xFF8E2226),
        iOwe = Color(0xFF0A5A44),
        gaveFill = Color(0xFFA32D2D)
    )
    val dark = light.copy(
        pageBg = Color(0xFF121513),
        surface = Color(0xFF1E2220),
        ink = Color(0xFFECEAE4),
        inkSoft = Color(0xFFBDBBB4),
        textMuted = Color(0xFFA7A69F),
        brandGreenText = Color(0xFF7FD1A3),
        owedToMe = Color(0xFFFF9C9C),
        iOwe = Color(0xFF7FE3C4)
    )
}

val LocalRoshanColors = staticCompositionLocalOf { RoshanPalette.light }

/** The colours for the theme the phone or computer is in (or the owner's choice, [dark]). */
@Composable
fun RoshanTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRoshanColors provides if (dark) RoshanPalette.dark else RoshanPalette.light, content = content)
}
