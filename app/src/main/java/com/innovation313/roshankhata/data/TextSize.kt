package com.innovation313.roshankhata.data

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt

/**
 * The owner's own text size for this app, on top of whatever the phone is set to.
 *
 * Every text size in the layouts is in sp, so the phone's own Settings → Font
 * size already reaches this app. Most shopkeepers never find that setting,
 * and it enlarges every app on the phone at once. This is the same dial,
 * inside Roshan Khata, touching nothing else.
 *
 * It multiplies the phone's scale rather than replacing it: someone who set
 * their phone large and then picks "Large" here wanted larger still, not a
 * reset to our idea of large.
 *
 * Normal means untouched — not "1.0". An owner who never opens this setting
 * gets exactly the phone's own behaviour, byte for byte, as before it existed.
 */
object TextSize {

    const val SMALL = 0
    const val NORMAL = 1
    const val LARGE = 2
    const val LARGEST = 3

    /** Index = level. 90 / 100 / 115 / 130 percent of the phone's scale. */
    private val FACTORS = floatArrayOf(0.90f, 1.00f, 1.15f, 1.30f)

    /**
     * Enlarging stops here. Past 150% the ledger rows, the two-step entry
     * dialog and the report headers stop fitting a 5-inch screen at all. If
     * the phone itself is already set above this, the phone's own choice is
     * kept — this setting never makes text smaller than the owner asked the
     * phone for when they are asking for bigger.
     */
    const val CEILING = 1.50f

    /** Shrinking stops here, for the same reason in the other direction: legibility. */
    const val FLOOR = 0.85f

    private const val PREFS = "text_size"
    private const val KEY_LEVEL = "level"

    fun level(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LEVEL, NORMAL)
            .coerceIn(SMALL, LARGEST)

    fun setLevel(context: Context, level: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LEVEL, level.coerceIn(SMALL, LARGEST)).apply()
    }

    /**
     * The font scale a screen should use, given the phone's own scale and the
     * owner's level here. Pure, so it is tested without a device.
     *
     * Rounded to two places: Configuration compares fontScale exactly, and
     * 1.0f * 1.15f is not quite 1.15f in binary. Without rounding two equal
     * choices could look different and rebuild a screen for nothing.
     */
    fun effective(system: Float, level: Int): Float {
        val safeLevel = level.coerceIn(SMALL, LARGEST)
        if (safeLevel == NORMAL) return system
        val scaled = system * FACTORS[safeLevel]
        val top = maxOf(CEILING, system)
        val bottom = minOf(FLOOR, system)
        return (scaled.coerceIn(bottom, top) * 100f).roundToInt() / 100f
    }

    /**
     * [base] with the owner's text size applied, for Activity.attachBaseContext.
     *
     * Only fontScale is overridden. A Configuration built from scratch leaves
     * every other field undefined, so the locale AppCompat applies, the
     * orientation, and (later) the night mode all still come from where they
     * always did. Copying the whole current configuration instead would
     * freeze those at the moment of launch.
     */
    fun wrap(base: Context): Context {
        val level = level(base)
        if (level == NORMAL) return base
        val system = base.resources.configuration.fontScale
        val target = effective(system, level)
        if (target == system) return base
        val override = Configuration().apply { fontScale = target }
        return base.createConfigurationContext(override)
    }
}
