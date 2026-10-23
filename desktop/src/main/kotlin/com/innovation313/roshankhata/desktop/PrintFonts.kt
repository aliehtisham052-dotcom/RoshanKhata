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

    private val faces = java.util.concurrent.ConcurrentHashMap<String, Font>()

    private fun face(family: String, like: Font): Font =
        faces.getOrPut("$family|${like.style}|${like.size2D}") { Font(family, like.style, 1).deriveFont(like.size2D) }

    /**
     * A face that can draw [codePoint] when [first] cannot: Nirmala UI for Indic scripts, then Dialog (whose
     * Windows fallbacks draw Arabic script, which Nirmala UI does not: an Urdu name on a Hindi sheet), else [first].
     */
    fun forCodePoint(first: Font, codePoint: Int): Font {
        if (first.canDisplay(codePoint)) return first
        if (hasIndic && first.family != INDIC) face(INDIC, first).takeIf { it.canDisplay(codePoint) }?.let { return it }
        if (first.family != Font.DIALOG) face(Font.DIALOG, first).takeIf { it.canDisplay(codePoint) }?.let { return it }
        return first
    }

    /**
     * [s] with a face per run (R56, 23 Oct 2026): [base] wherever it draws, else the first face that does.
     * A printed sheet used one face for everything, so a Hindi or Bengali name on an English or Urdu
     * statement, report, price list or receipt printed as boxes. [rtl] sets the run direction when given.
     */
    fun attributed(s: String, base: Font, rtl: Boolean? = null): java.text.AttributedString {
        val a = java.text.AttributedString(s)
        if (s.isEmpty()) return a
        a.addAttribute(java.awt.font.TextAttribute.FONT, base)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            // Direction marks (Format.ltr's) are no glyphs: they stay with the face around them.
            if (Character.getType(cp) != Character.FORMAT.toInt() && !base.canDisplay(cp)) {
                val f = forCodePoint(base, cp)
                if (f !== base) a.addAttribute(java.awt.font.TextAttribute.FONT, f, i, i + n)
            }
            i += n
        }
        rtl?.let {
            a.addAttribute(java.awt.font.TextAttribute.RUN_DIRECTION,
                if (it) java.awt.font.TextAttribute.RUN_DIRECTION_RTL else java.awt.font.TextAttribute.RUN_DIRECTION_LTR)
        }
        return a
    }

    /** Whether [font] alone draws [s] (direction marks aside): then the plain one-face calls are used, as before. */
    private fun drawsAll(font: Font, s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (Character.getType(cp) != Character.FORMAT.toInt() && !font.canDisplay(cp)) return false
            i += Character.charCount(cp)
        }
        return true
    }

    /** [s]'s width in [g]'s font, with the faces [attributed] gives it. */
    fun width(g: java.awt.Graphics2D, s: String): Double =
        if (s.isEmpty() || drawsAll(g.font, s)) g.fontMetrics.stringWidth(s).toDouble()
        else java.awt.font.TextLayout(attributed(s, g.font).iterator, g.fontRenderContext).advance.toDouble()

    /** [s] drawn at [x], [y] in [g]'s font, with the faces [attributed] gives it. */
    fun draw(g: java.awt.Graphics2D, s: String, x: Float, y: Float) {
        if (s.isEmpty() || drawsAll(g.font, s)) g.drawString(s, x, y)
        else g.drawString(attributed(s, g.font).iterator, x, y)
    }
}
