package com.innovation313.roshankhata.ui.poster3d

import android.graphics.RectF
import kotlin.math.atan

/**
 * One way a part of a hero moves. Every moving part is its own node in the
 * model whose origin is its pivot (store/poster3d/make_heroes.py), so a
 * motion is one rotation (or lift) applied on top of the node's rest pose.
 *
 * Angles in degrees, periods in seconds. At t = 0 every part is at [rest]:
 * that is the pose the shared picture shows.
 */
data class Motion(
    val node: String,
    val kind: Kind,
    val axis: Axis = Axis.Y,
    /** Degrees (or units for [Kind.BOB]) either side of rest; degrees per second for [Kind.SPIN]. */
    val amount: Float = 0f,
    val period: Float = 1f,
    /** Seconds of offset, so neighbours do not move in step. */
    val phase: Float = 0f,
    /** Degrees the part is turned at rest. */
    val rest: Float = 0f
) {
    enum class Kind {
        /** Turns steadily: rest + amount × t. */
        SPIN,
        /** Rocks either side of rest: rest + amount × sin(2πt / period). Lanterns swing like this about Z. */
        SWAY,
        /** Rises and falls by amount units. */
        BOB,
        /** Clock hands about −Z (clockwise seen from the front), from the phone's time when live. */
        HOUR, MINUTE, SECOND
    }

    enum class Axis(val x: Float, val y: Float, val z: Float) { X(1f, 0f, 0f), Y(0f, 1f, 0f), Z(0f, 0f, 1f) }
}

/**
 * A poster's 3D hero: its model, where it sits on the 1080 × 1920 picture,
 * how the camera frames it and how its parts move.
 */
enum class Hero(
    val asset: String,
    /** Its place on the poster; the live TextureView lies over the same fractions. */
    val rect: RectF,
    val eye: FloatArray,
    val target: FloatArray,
    /** Vertical field of view, degrees. */
    val fov: Double,
    val sunLux: Float,
    val motions: List<Motion>,
    /** The material whose base colour the shop's mark replaces (see SealFace), if any. */
    val face: String? = null
) {
    CRATE(
        asset = "poster3d/naya_maal.glb",
        rect = RectF(180f, 560f, 900f, 1230f),
        eye = floatArrayOf(0f, 0.95f, 4.2f),
        target = floatArrayOf(0f, -0.28f, 0f),
        fov = 30.0,
        sunLux = 70_000f,
        motions = listOf(
            Motion("box", Motion.Kind.SPIN, Motion.Axis.Y, amount = 36f, rest = 35f),
            Motion("box", Motion.Kind.BOB, amount = 0.06f, period = 3.6f)
        )
    ),
    EID_FITR(
        asset = "poster3d/eid_fitr.glb",
        rect = RectF(0f, 0f, 1080f, 690f),
        eye = FRAMED_EYE, target = ORIGIN, fov = framed(690f), sunLux = 50_000f,
        motions = listOf(
            Motion("crescent", Motion.Kind.SWAY, Motion.Axis.Y, amount = 22f, period = 7f, rest = 14f),
            Motion("crescent", Motion.Kind.BOB, amount = 0.035f, period = 5f),
            Motion("lantern_l", Motion.Kind.SWAY, Motion.Axis.Z, amount = 4f, period = 3.8f),
            Motion("lantern_r", Motion.Kind.SWAY, Motion.Axis.Z, amount = 4f, period = 4.4f, phase = 0.6f),
            Motion("lantern_m", Motion.Kind.SWAY, Motion.Axis.Z, amount = 5f, period = 3.2f, phase = 1.1f),
            Motion("lantern_l", Motion.Kind.SPIN, Motion.Axis.Y, amount = 10f, rest = 15f),
            Motion("lantern_r", Motion.Kind.SPIN, Motion.Axis.Y, amount = -8f, rest = 5f)
        )
    ),
    RAMAZAN(
        asset = "poster3d/ramazan.glb",
        rect = RectF(0f, 0f, 1080f, 700f),
        eye = FRAMED_EYE, target = ORIGIN, fov = framed(700f), sunLux = 45_000f,
        motions = listOf(
            Motion("crescent", Motion.Kind.SWAY, Motion.Axis.Y, amount = 18f, period = 8f, rest = 12f),
            Motion("crescent", Motion.Kind.BOB, amount = 0.04f, period = 5.5f),
            Motion("lantern_r", Motion.Kind.SWAY, Motion.Axis.Z, amount = 4f, period = 3.8f),
            Motion("lantern_r", Motion.Kind.SPIN, Motion.Axis.Y, amount = 9f, rest = 10f)
        )
    ),
    EID_ADHA(
        asset = "poster3d/eid_adha.glb",
        rect = RectF(90f, 150f, 990f, 930f),
        eye = FRAMED_EYE, target = ORIGIN, fov = framed(780f), sunLux = 60_000f,
        motions = listOf(
            Motion("mosque", Motion.Kind.SWAY, Motion.Axis.Y, amount = 16f, period = 9f, rest = -10f)
        )
    ),
    HOURS(
        asset = "poster3d/hours.glb",
        rect = RectF(240f, 126f, 840f, 726f),
        eye = FRAMED_EYE, target = ORIGIN, fov = framed(600f), sunLux = 50_000f,
        motions = listOf(
            Motion("clock", Motion.Kind.SWAY, Motion.Axis.Y, amount = 9f, period = 8f, rest = -8f),
            Motion("clock", Motion.Kind.SWAY, Motion.Axis.X, amount = 4f, period = 11f, rest = -4f),
            Motion("hand_hour", Motion.Kind.HOUR),
            Motion("hand_minute", Motion.Kind.MINUTE),
            Motion("hand_second", Motion.Kind.SECOND)
        )
    ),
    THANKS(
        asset = "poster3d/thanks.glb",
        rect = RectF(360f, 1296f, 720f, 1656f),
        eye = FRAMED_EYE, target = ORIGIN, fov = framed(360f), sunLux = 75_000f,
        motions = listOf(
            Motion("seal", Motion.Kind.SWAY, Motion.Axis.Y, amount = 14f, period = 6f, rest = -6f),
            Motion("seal", Motion.Kind.SWAY, Motion.Axis.X, amount = 8f, period = 7.5f, phase = 1f, rest = 10f)
        ),
        face = "seal_face"
    )
}

private val FRAMED_EYE = floatArrayOf(0f, 0f, 6f)
private val ORIGIN = floatArrayOf(0f, 0f, 0f)

/**
 * The field of view that shows a hero's rectangle at 200 poster pixels per
 * unit with the camera 6 units away — the scale make_heroes.py places
 * every part at.
 */
private fun framed(heightPx: Float): Double =
    Math.toDegrees(2 * atan(heightPx / 200.0 / 2 / 6.0))
