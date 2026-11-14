package com.innovation313.roshankhata.data

import kotlin.math.abs
import kotlin.random.Random

/**
 * The arithmetic of Hudhud's flights inside the open sky at the top of his sheet (14 Nov 2026, the owner: "wo screen se
 * bahar na jaye… ek jagah se doosri jagah thodi movement ho, phir wo doosri jagah baithe ya pehli jagah"). Pure, so a test
 * holds it: where the three perches are (mirrored in right-to-left), which perch he goes to, the arc he follows (it never
 * rises above the room there is, so he can never leave the sheet), and how long a flight takes.
 */
object HudhudMotion {

    /** Perches: 0 at the start side, 1 in the middle, 2 at the end side. */
    const val PERCHES = 3

    /**
     * The left edge of the stage on perch [index], inside a sky [skyWidth] wide: [edge] in from each side, the middle exactly
     * in the middle; [rtl] mirrors the order (the start side is the right).
     */
    fun perchLeft(index: Int, skyWidth: Float, stageWidth: Float, edge: Float, rtl: Boolean): Float {
        val span = (skyWidth - 2f * edge - stageWidth).coerceAtLeast(0f)
        val share = when (index) {
            0 -> 0f
            1 -> 0.5f
            else -> 1f
        }
        return edge + (if (rtl) 1f - share else share) * span
    }

    /** A perch other than [home] (a flight always goes somewhere); [last], the one he flew to before, is avoided when another is left. */
    fun otherPerch(home: Int, last: Int?, random: Random = Random): Int {
        val others = (0 until PERCHES).filter { it != home }
        val fresh = others.filter { it != last }.ifEmpty { others }
        return fresh[random.nextInt(fresh.size)]
    }

    /** A number in 0 until [n]. */
    fun pick(n: Int, random: Random = Random): Int = random.nextInt(n.coerceAtLeast(1))

    /** A cubic arc from ([x0], [y0]) to ([x1], [y1]) with its two control points. */
    data class Arc(
        val x0: Float, val y0: Float, val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x1: Float, val y1: Float
    ) {
        /** The arc's point at [t] in 0..1. */
        fun at(t: Float): Pair<Float, Float> {
            val u = 1f - t
            val x = u * u * u * x0 + 3f * u * u * t * c1x + 3f * u * t * t * c2x + t * t * t * x1
            val y = u * u * u * y0 + 3f * u * u * t * c1y + 3f * u * t * t * c2y + t * t * t * y1
            return x to y
        }
    }

    /**
     * The arc of a flight between two places on the same level, rising [rise] at its highest and no more (a cubic with both
     * controls at four thirds of the rise reaches exactly the rise in the middle and never passes it): he starts and ends
     * slowly and the arc keeps him inside the room he has above his perch. [rise] below nothing is nothing: a flat path.
     */
    fun arc(x0: Float, y0: Float, x1: Float, y1: Float, rise: Float): Arc {
        val up = rise.coerceAtLeast(0f)
        val level = minOf(y0, y1)
        val control = level - up * 4f / 3f
        val dx = x1 - x0
        return Arc(x0, y0, x0 + dx * 0.2f, control, x1 - dx * 0.2f, control, x1, y1)
    }

    /** How long a flight of [distance] takes in a sky [width] wide: a short hop across is quick, the whole width a little under a second and a half. */
    fun flightMillis(distance: Float, width: Float): Long {
        val share = if (width <= 0f) 0f else (abs(distance) / width).coerceIn(0f, 1f)
        return (MIN_FLIGHT_MS + (MAX_FLIGHT_MS - MIN_FLIGHT_MS) * share).toLong()
    }

    const val MIN_FLIGHT_MS = 700f
    const val MAX_FLIGHT_MS = 1400f
}
