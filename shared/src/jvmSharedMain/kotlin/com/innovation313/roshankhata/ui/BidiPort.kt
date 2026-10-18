package com.innovation313.roshankhata.ui

import java.util.Locale

/**
 * A plain-JVM copy of androidx BidiFormatter's unicodeWrap (default
 * instance: stereo reset on, context direction from the app's language),
 * so a phone number or a name in an Urdu sentence gets the same marks on
 * the computer (BidiWrap there) as on the phone. BidiPortTest in app
 * compares it with the real BidiFormatter, string by string. Plain text only (no HTML), as the app uses it.
 */
object BidiPort {

    private const val LRE = '‪'
    private const val RLE = '‫'
    private const val PDF = '‬'
    private const val LRM = "‎"
    private const val RLM = "‏"

    private const val DIR_LTR = -1
    private const val DIR_UNKNOWN = 0
    private const val DIR_RTL = 1

    // Languages written right to left. A script tag wins: ur-Latn (Roman Urdu) is left to right.
    private val RTL_LANGUAGES = setOf("ar", "fa", "ur", "sd", "he", "iw", "ps", "ug", "yi", "dv", "ckb")

    fun isRtlLocale(locale: Locale): Boolean = when (locale.script) {
        "" -> locale.language in RTL_LANGUAGES
        "Arab", "Hebr", "Thaa", "Syrc", "Nkoo", "Adlm", "Rohg" -> true
        else -> false
    }

    fun ltr(text: String): String = wrap(text, rtlText = false)

    fun auto(text: String): String = wrap(text, rtlText = firstStrongIsRtl(text))

    private fun wrap(text: String, rtlText: Boolean): String {
        val rtlContext = isRtlLocale(Locale.getDefault())
        val out = StringBuilder()
        out.append(markBefore(text, rtlText, rtlContext))
        if (rtlText != rtlContext) {
            out.append(if (rtlText) RLE else LRE).append(text).append(PDF)
        } else {
            out.append(text)
        }
        out.append(markAfter(text, rtlText, rtlContext))
        return out.toString()
    }

    private fun markAfter(text: String, rtlText: Boolean, rtlContext: Boolean): String {
        if (!rtlContext && (rtlText || exitDir(text) == DIR_RTL)) return LRM
        if (rtlContext && (!rtlText || exitDir(text) == DIR_LTR)) return RLM
        return ""
    }

    private fun markBefore(text: String, rtlText: Boolean, rtlContext: Boolean): String {
        if (!rtlContext && (rtlText || entryDir(text) == DIR_RTL)) return LRM
        if (rtlContext && (!rtlText || entryDir(text) == DIR_LTR)) return RLM
        return ""
    }

    /** TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR: letters and embedding marks decide; none means left to right. */
    internal fun firstStrongIsRtl(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when (Character.getDirectionality(cp)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true
                Character.DIRECTIONALITY_LEFT_TO_RIGHT,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false
            }
            i += Character.charCount(cp)
        }
        return false
    }

    private fun types(text: String): List<Byte> {
        val list = ArrayList<Byte>(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            list.add(Character.getDirectionality(cp))
            i += Character.charCount(cp)
        }
        return list
    }

    /** DirectionalityEstimator.getEntryDir. */
    internal fun entryDir(text: String): Int {
        val t = types(text)
        var index = 0
        var level = 0
        var levelDir = DIR_UNKNOWN
        var firstNonEmptyLevel = 0
        while (index < t.size && firstNonEmptyLevel == 0) {
            when (t[index++]) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> { level++; levelDir = DIR_LTR }
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> { level++; levelDir = DIR_RTL }
                Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT -> { level--; levelDir = DIR_UNKNOWN }
                Character.DIRECTIONALITY_BOUNDARY_NEUTRAL -> {}
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> {
                    if (level == 0) return DIR_LTR
                    firstNonEmptyLevel = level
                }
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> {
                    if (level == 0) return DIR_RTL
                    firstNonEmptyLevel = level
                }
                else -> firstNonEmptyLevel = level
            }
        }
        if (firstNonEmptyLevel == 0) return DIR_UNKNOWN
        if (levelDir != DIR_UNKNOWN) return levelDir
        while (index > 0) {
            when (t[--index]) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> {
                    if (firstNonEmptyLevel == level) return DIR_LTR
                    level--
                }
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> {
                    if (firstNonEmptyLevel == level) return DIR_RTL
                    level--
                }
                Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT -> level++
            }
        }
        return DIR_UNKNOWN
    }

    /** DirectionalityEstimator.getExitDir. */
    internal fun exitDir(text: String): Int {
        val t = types(text)
        var index = t.size
        var level = 0
        var lastNonEmptyLevel = 0
        while (index > 0) {
            when (t[--index]) {
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> {
                    if (level == 0) return DIR_LTR
                    if (lastNonEmptyLevel == 0) lastNonEmptyLevel = level
                }
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
                Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> {
                    if (lastNonEmptyLevel == level) return DIR_LTR
                    level--
                }
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> {
                    if (level == 0) return DIR_RTL
                    if (lastNonEmptyLevel == 0) lastNonEmptyLevel = level
                }
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> {
                    if (lastNonEmptyLevel == level) return DIR_RTL
                    level--
                }
                Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT -> level++
                Character.DIRECTIONALITY_BOUNDARY_NEUTRAL -> {}
                else -> if (lastNonEmptyLevel == 0) lastNonEmptyLevel = level
            }
        }
        return DIR_UNKNOWN
    }
}
