package com.innovation313.roshankhata.ui

import kotlin.math.max
import kotlin.math.min

/**
 * Where the rows at the foot of a poster stand (13 Nov 2026).
 *
 * Four designs end in the same block: the sign-off (a seal beside "with love from" and the shop's name), the
 * phone under it, then the address and the email, and, when the app's mark is on, the mark at the very foot. Each
 * design placed those rows by numbers of its own. On 8 Nov (M18) the address went under the sign-off at y = 1852
 * while the sign-off's phone line already stood at about 1820, so on Eid ul-Fitr, Ramazan, Jumu'ah and 14 August the
 * phone number ran into the address, and an address too long for one line pushed its second row into the mark and
 * off the picture (the 13 Nov proofs).
 *
 * Here the rows are placed together, from the foot up: the address keeps the place it always had (y = 1852) or the
 * highest place that leaves room for its second row and the mark, the phone stands a fixed distance above it, and the
 * seal row above that, never lower than the design wanted it. What the owner chose to show can never overlap, and a
 * foot with nothing under the phone is exactly as it was.
 *
 * Pure arithmetic on the 1080 × 1920 picture; the drawing is PosterKit's.
 */
object PosterFoot {

    /** Where a one-line address has always stood. */
    const val ANCHOR = 1852f

    /** The app's mark: the centre of its line at the very foot. */
    const val MARK_CY = 1898f

    /** The lowest an address row's ink may reach with nothing, and with the mark, under it. */
    private const val LIMIT = 1896f
    private const val LIMIT_MARK = 1880f

    /** Half the height of an address row (its mark is 40 high, its words 36). */
    private const val HALF = 22f

    /** The second address row stands this far under the first (PosterKit.address): 48, and 58 for Arabic script. */
    const val LINE = 48f
    const val LINE_ARABIC = 58f

    /** The sign-off's own words reach this far either side of the seal's centre ("from" above, the name below). */
    private const val ROW_HALF = 52f

    /** From the seal row to the phone, and from the phone to the address; and the same when room is short. */
    private const val GAP_PHONE = 50f
    private const val GAP_ADDRESS = 54f
    private const val GAP_PHONE_TIGHT = 40f
    private const val GAP_ADDRESS_TIGHT = 48f

    /** From the seal row to an address with no phone between. */
    private const val GAP_BARE = 56f

    /** The phone's place under a seal row with nothing under it: the number the sign-off has always used. */
    private const val GAP_PHONE_ALONE = 62f

    /** The smallest seal a crowded foot may shrink to. */
    private const val SEAL_FLOOR = 104f

    /**
     * Where each row stands: [cy] the seal row's centre and [sealSize] its seal's diameter (both as the design
     * asked, or lifted and smaller when the foot is crowded), the centres of the phone and the address's first row
     * (its second is 48 lower) and of the mark.
     */
    class Plan(val cy: Float, val sealSize: Float, val phoneCy: Float, val addressCy: Float, val markCy: Float,
               val pitch: Float = LINE) {
        /** Where the lowest ink of the address stands, for a test that it stays on the picture. */
        fun addressBottom(lines: Int): Float = addressCy + (max(lines, 1) - 1) * pitch + HALF
    }

    /**
     * [cy] and [sealSize] as the design wants the seal row, [phone] whether a phone is shown, [addressLines] how
     * many rows the address and email take (0 for none, see PosterKit.addressLines), [mark] whether the app's mark
     * is on, [topLimit] the lowest the art or panel above the foot reaches plus its margin (0 = no limit), [pitch]
     * the distance between the address's two rows when it has two.
     */
    fun plan(cy: Float, sealSize: Float, phone: Boolean, addressLines: Int, mark: Boolean, topLimit: Float = 0f,
             pitch: Float = LINE): Plan {
        if (addressLines <= 0) {
            // Nothing under the phone: the sign-off is as it always was.
            return Plan(cy, sealSize, cy + sealSize / 2f + GAP_PHONE_ALONE, ANCHOR, MARK_CY, pitch)
        }
        val limit = if (mark) LIMIT_MARK else LIMIT
        // The address's first row: where it has always stood, or higher when its second row (and the mark) need the room.
        val addressAt = min(ANCHOR, limit - HALF - (addressLines - 1) * pitch)
        var size = sealSize
        var gapPhone = GAP_PHONE
        var gapAddress = GAP_ADDRESS
        fun half() = max(size / 2f, ROW_HALF)
        // The lowest the seal row may stand, so that the phone and the address fit under it.
        fun lowest(): Float =
            if (phone) addressAt - gapAddress - gapPhone - half() else addressAt - GAP_BARE - half()

        var top = min(cy, lowest())
        if (topLimit > 0f && top - half() < topLimit) {
            // Not enough room under the art above: close the gaps, then shrink the seal.
            gapPhone = GAP_PHONE_TIGHT
            gapAddress = GAP_ADDRESS_TIGHT
            top = min(cy, lowest())
            while (top - half() < topLimit && size > SEAL_FLOOR) {
                size = max(SEAL_FLOOR, size - 8f)
                top = min(cy, lowest())
            }
            // Still none: the rows keep their order and give up the foot's margin rather than overlap.
            if (top - half() < topLimit) top = topLimit + half()
        }
        val phoneAt = if (phone) top + half() + gapPhone else 0f
        val addressY = if (phone) max(addressAt, phoneAt + gapAddress) else max(addressAt, top + half() + GAP_BARE)
        return Plan(top, size, phoneAt, addressY, MARK_CY, pitch)
    }

    /**
     * Eid ul-Adha signs off as a column, not a row: the seal, "from", the name, the phone, then the address (and the
     * email), over the foot's gold rule at y = 1880. [sealCy] and [sealR] are the seal's centre and radius, the others
     * the centres of their rows; [showFrom] says whether "from" is drawn.
     *
     * With one address row the column is exactly the old one (the address at y = 1842, the phone 48 over it, the name
     * 58 over that, "from" 58 over that, the seal 92 over that), except that a Nastaliq name ([arabic], the Urdu
     * poster) stands 68 over the phone: its tails reached the phone's digits (13 Nov proofs). A second address row
     * stands under the first, so the whole column lifts by the row's pitch, and the seal shrinks (60 down to 44) before
     * it can touch the message above, [topLimit] being the lowest that reaches plus its margin. When even that leaves
     * a seal under 52, "from" (a courtesy line) is left out and the rest breathes.
     */
    class Column(val sealCy: Float, val sealR: Float, val fromCy: Float, val nameCy: Float, val phoneCy: Float,
                 val addressCy: Float, val showFrom: Boolean = true)

    private const val COLUMN_LAST = 1842f
    private const val COLUMN_SEAL = 60f
    private const val COLUMN_SEAL_FLOOR = 44f
    private const val COLUMN_SEAL_SMALL = 52f

    fun column(addressLines: Int, phone: Boolean, topLimit: Float = 0f, pitch: Float = LINE,
               arabic: Boolean = false): Column {
        if (addressLines <= 0) {
            // No address and no email: the column is as it always was.
            return Column(1586f, COLUMN_SEAL, 1688f, 1752f, 1818f, ANCHOR)
        }
        val gapAddress = 48f
        val gapName = if (arabic) 68f else 58f
        val gapFrom = 58f
        val addressCy = COLUMN_LAST - (addressLines - 1) * pitch
        val phoneCy = addressCy - gapAddress
        // With no phone the address takes its room, but the name keeps its distance from the first address row.
        val nameCy = if (phone) phoneCy - gapName else min(1736f, addressCy - gapName)
        val fromCy = nameCy - gapFrom

        /** The largest seal whose top clears [topLimit] when its bottom stands at [bottom]. */
        fun radius(bottom: Float): Float {
            var r = COLUMN_SEAL
            while (topLimit > 0f && bottom - 2f * r < topLimit && r > COLUMN_SEAL_FLOOR) r -= 4f
            return r
        }
        // The seal's bottom stands 32 over "from" (20 clear of the words, as before).
        val withFrom = fromCy - 32f
        val rFrom = radius(withFrom)
        if (rFrom >= COLUMN_SEAL_SMALL || topLimit <= 0f) {
            return Column(withFrom - rFrom, rFrom, fromCy, nameCy, phoneCy, addressCy)
        }
        // Too little room for a seal worth drawing: no "from", the seal comes down to the name.
        val noFrom = nameCy - (if (arabic) 63f else 45f)
        val rName = radius(noFrom)
        return if (rName > rFrom) Column(noFrom - rName, rName, fromCy, nameCy, phoneCy, addressCy, showFrom = false)
        else Column(withFrom - rFrom, rFrom, fromCy, nameCy, phoneCy, addressCy)
    }

    /**
     * Where a name, the phone under it and the address (and email) under that stand, for a design that signs off with
     * the name alone (the Thank-you card, 13 Nov 2026). It used to join the phone, the address and the email into one
     * line and shrink that to fit 880, and with an address of ordinary length the line ran past the picture's edges.
     * Now the rows stand as on every other poster: the address's last row keeps [bottom] (the lowest its ink may
     * reach), a second row stands [pitch] over it, the phone 50 over the first, and the name 60 over the phone — or
     * stays at [nameCy], where the design wants it, when that is higher. With nothing but a phone it stands 70 under
     * the name, as it always did; with no phone the address takes that place.
     *
     * [markOutside] says the app's mark has left the card's frame (see [stack]); the design then draws it under the frame.
     */
    class Stack(val nameCy: Float, val phoneCy: Float, val addressCy: Float, val markOutside: Boolean = false)

    /** How far the Thank-you card's name has its ink reach above its centre (Nastaliq's strokes stand much taller). */
    fun nameHalfOf(arabic: Boolean): Float = if (arabic) 42f else 24f

    /**
     * [bottom] is where the last address row's ink may end with the app's mark inside the card's frame; [bottomOutside]
     * where it may end when the mark stands under the frame instead (the same as [bottom] when no mark is drawn).
     *
     * Arabic-script rows stand taller (Nastaliq's strokes rise and fall): a little more air above them and under a
     * name drawn in it, as the Adha column does. When that leaves the name under the picture's own seal ([topLimit]),
     * the gaps close up, then the rows sink 8 toward the border. Urdu with two address rows and the app's mark inside
     * the frame is the one combination that still does not fit — no layout does: from the seal's shadow to the border
     * the name, the phone, two rows and the mark need about 30 more than there is (13 Nov 2026: the name's tails ran
     * 13 into the seal). The mark is the least important row, so it gives way, not the owner's own: it steps out of the
     * frame, under it, at the foot of the picture where every other design draws it, and the rows take the room it left
     * ([bottomOutside]). Layouts that fit with the mark inside are exactly as they were.
     */
    fun stack(
        nameCy: Float, phone: Boolean, addressLines: Int, pitch: Float, bottom: Float,
        arabic: Boolean = false, topLimit: Float = 0f, bottomOutside: Float = bottom
    ): Stack {
        if (addressLines <= 0) return Stack(nameCy, nameCy + 70f, bottom)
        val nameHalf = nameHalfOf(arabic)
        fun place(gapPhone: Float, gapName: Float, low: Float, outside: Boolean): Stack {
            var first = low - HALF - (addressLines - 1) * pitch
            if (!phone) first = min(first, nameCy + 70f)
            val phoneAt = first - gapPhone
            val nameAt = min(nameCy, (if (phone) phoneAt else first) - gapName)
            return Stack(nameAt, phoneAt, first, outside)
        }
        fun clears(s: Stack) = topLimit <= 0f || s.nameCy - nameHalf >= topLimit
        val gapPhone = if (pitch > LINE) 58f else 50f
        val gapName = if (arabic) 68f else 60f
        val gapNameTight = if (arabic) 58f else 52f
        // The rungs, in order: the roomy gaps, the gaps closed up, then the rows sunk 8 toward the border.
        var st = place(gapPhone, gapName, bottom, false)
        if (clears(st)) return st
        st = place(50f, gapNameTight, bottom, false)
        if (clears(st)) return st
        st = place(50f, gapNameTight, bottom + 8f, false)
        if (clears(st)) return st
        if (bottomOutside > bottom) {
            // The same three rungs with the mark under the frame.
            for (rung in listOf(
                Triple(gapPhone, gapName, bottomOutside),
                Triple(50f, gapNameTight, bottomOutside),
                Triple(50f, gapNameTight, bottomOutside + 8f)
            )) {
                val out = place(rung.first, rung.second, rung.third, true)
                if (clears(out)) return out
            }
        }
        // Nothing clears the seal (a combination no layout can give): the rows keep their order and their distances.
        return st
    }
}
