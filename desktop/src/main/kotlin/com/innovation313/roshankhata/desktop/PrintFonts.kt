package com.innovation313.roshankhata.desktop

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.Locale

/**
 * The face printed pages are written in (21 Oct 2026, the critical review).
 * Java's "Dialog" font on Windows looks for Mangal and Vrinda for Hindi and
 * Bengali, and stock Windows 10 and 11 no longer install them, so a Hindi or
 * Bengali statement printed boxes. Nirmala UI, which every Windows since 8.1
 * installs, draws both scripts and Latin; it is used when the app speaks one
 * of them and is there. Everything else keeps Dialog, as before.
 */
object PrintFonts {
    const val INDIC = "Nirmala UI"

    private val installed: Set<String> by lazy {
        runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet() }.getOrDefault(emptySet())
    }

    /** Whether Windows has the face that draws Hindi and Bengali. */
    val hasIndic: Boolean get() = INDIC in installed

    /** The family for the app's language now. */
    val family: String get() =
        if (Locale.getDefault().language in setOf("hi", "bn") && hasIndic) INDIC else Font.DIALOG

    /** A face that can draw [codePoint] when [first] cannot: Nirmala UI for Indic scripts, else [first]. */
    fun forCodePoint(first: Font, codePoint: Int): Font =
        if (first.canDisplay(codePoint) || !hasIndic) first
        else Font(INDIC, first.style, 1).deriveFont(first.size2D).takeIf { it.canDisplay(codePoint) } ?: first
}
