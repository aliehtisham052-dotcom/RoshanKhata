// GENERATED from app/src/main/java/com/innovation313/roshankhata/ui/PosterBody.kt
// by scripts/gen_desktop_poster.py. Do not edit: change the phone's file and re-run.
package com.innovation313.roshankhata.desktop.poster

import kotlin.math.max
import kotlin.math.min

/**
 * Where the body of a poster stands when its foot is not full (19 Nov 2026, the owner's photos).
 *
 * Five designs end in the gold call button and the address: New stock, Hours, Offer, Closed and the shop's own
 * announcement. Each was laid out for a shop that shows both, by fixed heights. A shop that shows no number (or has
 * none yet) got the same body in the same place and nothing under it: the lowest quarter to a third of the picture
 * was empty, the art sat in the upper part, and the poster read as unfinished. Whether a poster looked whole depended
 * on what the owner had ticked.
 *
 * Here the foot says how much room it does not need ([foot]), and each design takes that room into its body: the
 * hero grows a little (never past the picture's sides), and what is left opens the gaps between the parts, so the
 * margins above and below stay in balance. With the call button and the address both shown the room is nought and
 * every design is exactly as it was drawn before.
 *
 * Pure arithmetic on the 1080 x 1920 picture, as [PosterFoot]; the drawing is PosterKit's and the designs'.
 */
object PosterBody {

    /** The lowest a body may reach with nothing under it, and with only the app's mark under it. */
    const val FLOOR = 1790f
    const val FLOOR_MARK = 1770f

    /** From the body to the call button; how far a call button with no address under it sinks; from the body to a lone address. */
    private const val PILL_GAP = 30f
    private const val PILL_DROP = 56f
    private const val ADDRESS_GAP = 78f

    /** A rectangle on the picture (plain numbers: this file knows no graphics class). */
    class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    /** A square or oblong of [w] x [h] centred on x = 540 with its top at [top]. */
    private fun centred(top: Float, w: Float, h: Float) = Box(540f - w / 2f, top, 540f + w / 2f, top + h)

    /**
     * The foot of a design with the call button: [pillTop] is where the button stands, [addressCy] the address's
     * first row, [limit] the lowest the body may reach and [slack] how much lower that is than the design drew it.
     */
    class Foot(val pillTop: Float, val addressCy: Float, val limit: Float, val slack: Float)

    /**
     * [pill]: the poster shows a number; [addressLines]: 0, 1 or 2 rows of address and email; [mark]: the app's mark
     * is on. [pillTop] and [addressCy] are the design's own places for a full foot, which are kept when it is full.
     */
    fun foot(pill: Boolean, addressLines: Int, mark: Boolean, pillTop: Float, addressCy: Float): Foot {
        val designed = pillTop - PILL_GAP
        val limit = when {
            pill && addressLines > 0 -> designed
            pill -> designed + PILL_DROP
            addressLines > 0 -> addressCy - ADDRESS_GAP
            else -> if (mark) FLOOR_MARK else FLOOR
        }
        val top = if (pill && addressLines == 0) pillTop + PILL_DROP else pillTop
        return Foot(top, addressCy, limit, limit - designed)
    }

    // ------------------------------------------------------------------ New stock

    /** The crate's place as the design drew it (poster3d.Hero.CRATE.rect), and the light pool's place inside it. */
    val CRATE = Box(180f, 560f, 900f, 1230f)

    class NewStock(val kickerCy: Float, val headCy: Float, val hero: Box, val stripTop: Float, val messageCy: Float) {
        /** How much larger than drawn the hero is. */
        val scale: Float get() = hero.height / CRATE.height
    }

    fun newStock(slack: Float): NewStock {
        val s = max(0f, slack)
        val grow = min(0.40f * s, 124f)
        val r = s - grow
        val k = (CRATE.height + grow) / CRATE.height
        val hero = centred(CRATE.top + 0.30f * r, CRATE.width * k, CRATE.height + grow)
        return NewStock(
            kickerCy = 300f + 0.08f * r,
            headCy = 470f + 0.18f * r,
            hero = hero,
            stripTop = 1230f + grow + 0.50f * r,
            messageCy = 1460f + grow + 0.68f * r
        )
    }

    // ------------------------------------------------------------------ Hours

    val CLOCK = Box(240f, 126f, 840f, 726f)

    class Hours(val clock: Box, val headCy: Float, val panelTop: Float, val panelHeight: Float) {
        val scale: Float get() = clock.height / CLOCK.height
    }

    /** [rows]: the timing rows on the panel. */
    fun hours(slack: Float, rows: Int): Hours {
        val s = max(0f, slack)
        val full = 48f + rows * 112f
        val base = min(full, 540f)
        val grow = min(0.30f * s, 110f)
        var r = s - grow
        // Taller rows before wider gaps, unless the panel is already at its cap.
        val taller = if (full <= 540f) min(0.18f * r, rows * 22f) else 0f
        r -= taller
        return Hours(
            clock = centred(CLOCK.top + 0.14f * r, CLOCK.width + grow, CLOCK.height + grow),
            headCy = 812f + grow + 0.38f * r,
            panelTop = 1191f - base / 2f + grow + 0.80f * r,
            panelHeight = base + taller
        )
    }

    // ------------------------------------------------------------------ Offer

    val COIN = Box(180f, 430f, 900f, 1150f)

    class Offer(val headCy: Float, val coin: Box, val roomTop: Float, val roomBottom: Float, val tagTop: Float) {
        val scale: Float get() = coin.height / COIN.height
    }

    /** [days]: the "only for 3 days" tag is shown under the words. */
    fun offer(slack: Float, days: Boolean): Offer {
        val s = max(0f, slack)
        val grow = min(0.35f * s, 110f)
        val r = s - grow
        val tagTop = 1406f + grow + 0.76f * r
        return Offer(
            headCy = 345f + 0.12f * r,
            coin = centred(COIN.top + 0.30f * r, COIN.width + grow, COIN.height + grow),
            roomTop = 1170f + grow + 0.52f * r,
            roomBottom = if (days) tagTop - 20f else 1500f + grow + 0.76f * r,
            tagTop = tagTop
        )
    }

    // ------------------------------------------------------------------ Closed

    val SIGN = Box(126f, 276f, 954f, 906f)

    /** [sorryCy]: where the line of regret stands, or 0 when there is no room (or no need) for it. */
    class Closed(val sign: Box, val panelTop: Float, val panelHeight: Float, val sorryCy: Float) {
        val scale: Float get() = sign.height / SIGN.height
    }

    /**
     * [limit]: the lowest the body may reach (above the "call for urgent work" line, the address, or the floor);
     * [rows]: the reason and reopening rows, 0 when the owner's own line fills the panel instead; [sorry]: the design
     * may add its line of regret when there is room.
     */
    fun closed(limit: Float, rows: Int, sorry: Boolean): Closed {
        val top0 = if (rows == 0) 1008f else 1040f
        val h0 = if (rows == 0) 352f else 48f + rows * 112f
        // The design drew the owner's line panel to end 70 above the urgent line: that is nought.
        val s = max(0f, limit - 70f - (top0 + h0))
        val grow = min(0.20f * s, 96f)
        val taller = if (rows > 0) min(0.12f * s, rows * 36f) else 0f
        val line = if (sorry && s - grow - taller >= 260f) 104f else 0f
        val r = s - grow - taller - line
        val k = (SIGN.height + grow) / SIGN.height
        val panelTop = top0 + grow + 0.80f * r
        return Closed(
            sign = centred(SIGN.top + 0.50f * r, SIGN.width * k, SIGN.height + grow),
            panelTop = panelTop,
            panelHeight = h0 + taller,
            sorryCy = if (line > 0f) panelTop + h0 + taller + 70f else 0f
        )
    }

    // ------------------------------------------------------------------ The shop's own announcement

    /** The glass panel may start here (under the shop's name) and the call button stands here when the foot is full. */
    const val CUSTOM_TOP = 252f
    const val CUSTOM_PILL_TOP = 1500f
    const val CUSTOM_ADDRESS_CY = 1744f

    /** The panel's inner margins: above the heading (the tag stands there), and under the last line. */
    const val CUSTOM_HEAD = 170f
    const val CUSTOM_FOOT = 70f

    class Custom(val panelTop: Float, val panelBottom: Float, val wordsTop: Float)

    /**
     * [limit]: the lowest the panel may reach; [words]: the height of the heading, its rule and the message as they
     * were set. The panel is as tall as the words need and half of what is over, and stands a little above the
     * middle of its room; the words sink a third of the way into what the panel has over.
     */
    fun custom(limit: Float, words: Float): Custom {
        val room = max(0f, limit - CUSTOM_TOP)
        val need = CUSTOM_HEAD + words + CUSTOM_FOOT
        val height = min(room, need + 0.38f * max(0f, room - need))
        val top = CUSTOM_TOP + 0.42f * (room - height)
        return Custom(top, top + height, top + CUSTOM_HEAD + 0.42f * max(0f, height - need))
    }
}
