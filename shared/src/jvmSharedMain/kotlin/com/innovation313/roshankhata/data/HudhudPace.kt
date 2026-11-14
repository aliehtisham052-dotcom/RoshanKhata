package com.innovation313.roshankhata.data

import java.math.BigDecimal
import java.util.Locale
import kotlin.math.roundToInt

/**
 * How fast Hudhud speaks, as the owner sets it (14 Nov 2026, the voice settings' slider): a step of a tenth between slow
 * (0.6) and quick (1.5) of his style's own pace, 1.0 being the style's own. One place for the range, so the slider, what
 * is kept ([com.innovation313.roshankhata.ui.HudhudPrefs.pace]) and the "slower" and "faster" he is asked for all agree.
 *
 * A Material slider refuses a value that is off its steps (it throws when it is drawn), and a pace kept as a float can
 * be a hair off one (1.0 less a tenth, twice, is not exactly 0.8): [snap] puts any pace on the nearest step, worked out
 * in decimals so the step it lands on is exact. Pure arithmetic, tested on the computer ([HudhudPaceTest]).
 */
object HudhudPace {

    const val MIN = 0.6f
    const val MAX = 1.5f
    const val STEP = 0.1f

    /** [pace] on the nearest step of the slider, kept inside the range; the style's own pace for a number that is none. */
    fun snap(pace: Float): Float {
        if (pace.isNaN()) return 1f
        val inside = pace.coerceIn(MIN, MAX)
        val steps = ((inside - MIN) / STEP).roundToInt()
        return BigDecimal("0.6").add(BigDecimal("0.1").multiply(BigDecimal(steps))).toFloat().coerceIn(MIN, MAX)
    }

    /** "0.9×": the figure in 0-9 with a plain point in every language, as every screen writes a figure. */
    fun label(pace: Float): String = String.format(Locale.ROOT, "%.1f×", pace)
}
