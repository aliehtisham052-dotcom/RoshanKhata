package com.innovation313.roshankhata.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the rows at the foot of a poster stand (13 Nov 2026, the poster review). On 8 Nov the address was put under the
 * sign-off at a fixed height while the sign-off's phone line stood about there, so on four designs the phone ran into
 * the address and a second address row ran into the app's mark and off the picture. These hold the arithmetic that
 * places the rows together: the old foot is unchanged, the rows keep their order, and what the owner chose to show never
 * overlaps or leaves the picture. Pure numbers, no drawing.
 */
class PosterFootTest {

    private val delta = 0.01f

    /** The four designs that sign off with a seal row, as built: where the row stands, its seal, the art above it. */
    private class Design(val cy: Float, val seal: Float, val topLimit: Float)

    private val designs = listOf(
        Design(1689f, 138f, 1484f), // Eid ul-Fitr, under its note panel
        Design(1700f, 138f, 1484f), // Ramazan
        Design(1690f, 120f, 1604f), // Jumu'ah
        Design(1680f, 138f, 1470f), // 14 August
    )

    // ------------------------------------------------------------------------------------------ the seal row, plan

    @Test
    fun `a foot with nothing under the phone is the old one`() {
        val p = PosterFoot.plan(1689f, 138f, phone = true, addressLines = 0, mark = false)
        assertEquals(1689f, p.cy, delta)
        assertEquals(138f, p.sealSize, delta)
        assertEquals(1689f + 69f + 62f, p.phoneCy, delta)
        assertEquals(1852f, p.addressCy, delta)
        assertEquals(1898f, p.markCy, delta)
    }

    @Test
    fun `a one row address keeps the place it always had`() {
        for (mark in listOf(false, true)) {
            val p = PosterFoot.plan(1500f, 100f, phone = true, addressLines = 1, mark = mark)
            assertEquals(1852f, p.addressCy, delta)
        }
    }

    @Test
    fun `a second address row lifts the address clear of the mark and the picture edge`() {
        val plain = PosterFoot.plan(1500f, 100f, phone = true, addressLines = 2, mark = false)
        assertEquals(1826f, plain.addressCy, delta)
        assertTrue(plain.addressBottom(2) <= 1896f + delta)
        val marked = PosterFoot.plan(1500f, 100f, phone = true, addressLines = 2, mark = true)
        assertEquals(1810f, marked.addressCy, delta)
        assertTrue(marked.addressBottom(2) <= 1880f + delta)
        // An Arabic script address stands 58 between its rows, not 48, so it lifts further.
        val arabic = PosterFoot.plan(1500f, 100f, phone = true, addressLines = 2, mark = true, pitch = PosterFoot.LINE_ARABIC)
        assertEquals(1800f, arabic.addressCy, delta)
        assertTrue(arabic.addressBottom(2) <= 1880f + delta)
    }

    @Test
    fun `the sign off lifts to leave room for the phone and the address`() {
        // The 8 Nov flaw: a seal row at 1760 put its phone at about 1850, on the address.
        val p = PosterFoot.plan(1760f, 100f, phone = true, addressLines = 1, mark = false)
        assertEquals(1696f, p.cy, delta)
        assertEquals(1798f, p.phoneCy, delta)
        assertEquals(1852f, p.addressCy, delta)
    }

    @Test
    fun `a seal row that does not fit under the art closes its gaps and shrinks the seal`() {
        // Jumu'ah as built: the art ends at 1604, the row asked for 1690 with a 120 seal; two address rows and the mark.
        val roomy = PosterFoot.plan(1690f, 120f, phone = true, addressLines = 1, mark = false, topLimit = 1604f)
        assertEquals(120f, roomy.sealSize, delta)
        assertEquals(1688f, roomy.cy, delta)
        val crowded = PosterFoot.plan(1690f, 120f, phone = true, addressLines = 2, mark = true, topLimit = 1604f)
        assertEquals(112f, crowded.sealSize, delta)
        assertEquals(1666f, crowded.cy, delta)
        assertEquals(1762f, crowded.phoneCy, delta)
        assertEquals(1810f, crowded.addressCy, delta)
        assertTrue(crowded.cy - crowded.sealSize / 2f >= 1604f)
    }

    @Test
    fun `the rows keep their order and the address stays on the picture in every design`() {
        for (d in designs) for (phone in listOf(true, false)) for (lines in 1..2) for (mark in listOf(false, true)) {
            for (pitch in listOf(PosterFoot.LINE, PosterFoot.LINE_ARABIC)) {
                val label = "cy ${d.cy} phone $phone lines $lines mark $mark pitch $pitch"
                val p = PosterFoot.plan(d.cy, d.seal, phone, lines, mark, d.topLimit, pitch)
                val half = maxOf(p.sealSize / 2f, 52f)
                assertTrue(label, p.sealSize >= 104f)
                // The seal row stays under the art above it.
                assertTrue(label, p.cy - half >= d.topLimit - delta)
                // The seal row, then the phone (when shown), then the address, each clear of the next.
                if (phone) {
                    assertTrue(label, p.cy + half < p.phoneCy - 22f)
                    assertTrue(label, p.phoneCy + 22f <= p.addressCy)
                } else {
                    assertTrue(label, p.cy + half <= p.addressCy - 22f)
                }
                // The address ends above the mark (when on) or the picture's edge, and the mark has its own line.
                assertTrue(label, p.addressBottom(lines) <= (if (mark) 1880f else 1896f) + delta)
                assertTrue(label, p.addressBottom(lines) < p.markCy)
            }
        }
    }

    // ------------------------------------------------------------------------------------------ Eid ul-Adha, column

    @Test
    fun `a column with no address is the old one`() {
        val c = PosterFoot.column(addressLines = 0, phone = true)
        assertEquals(1586f, c.sealCy, delta)
        assertEquals(60f, c.sealR, delta)
        assertEquals(1688f, c.fromCy, delta)
        assertEquals(1752f, c.nameCy, delta)
        assertEquals(1818f, c.phoneCy, delta)
        assertEquals(1852f, c.addressCy, delta)
        assertTrue(c.showFrom)
    }

    @Test
    fun `a column with one address row stands as it always did`() {
        val c = PosterFoot.column(addressLines = 1, phone = true)
        assertEquals(1842f, c.addressCy, delta)
        assertEquals(1794f, c.phoneCy, delta)
        assertEquals(1736f, c.nameCy, delta)
        assertEquals(1678f, c.fromCy, delta)
        assertEquals(60f, c.sealR, delta)
        assertEquals(1586f, c.sealCy, delta)
    }

    @Test
    fun `a second row lifts the whole column and a Nastaliq name gets more air`() {
        val latin = PosterFoot.column(addressLines = 2, phone = true)
        assertEquals(1794f, latin.addressCy, delta)
        assertEquals(1746f, latin.phoneCy, delta)
        assertEquals(1688f, latin.nameCy, delta)
        val urdu = PosterFoot.column(addressLines = 2, phone = true, pitch = PosterFoot.LINE_ARABIC, arabic = true)
        assertEquals(1784f, urdu.addressCy, delta)
        assertEquals(1736f, urdu.phoneCy, delta)
        assertEquals(1668f, urdu.nameCy, delta)
    }

    @Test
    fun `the seal shrinks before it touches the message and from is left out when too little is left`() {
        // 1480: the seal gives up 4 and keeps its courtesy line.
        val mild = PosterFoot.column(addressLines = 2, phone = true, topLimit = 1480f)
        assertEquals(56f, mild.sealR, delta)
        assertTrue(mild.showFrom)
        assertTrue(mild.sealCy - mild.sealR >= 1480f - delta)
        // 1500: a seal under 52 is not worth drawing, so "from" goes and the seal comes down to the name.
        val tight = PosterFoot.column(addressLines = 2, phone = true, topLimit = 1500f)
        assertFalse(tight.showFrom)
        assertEquals(60f, tight.sealR, delta)
        assertTrue(tight.sealCy - tight.sealR >= 1500f - delta)
        assertTrue(tight.sealCy + tight.sealR < tight.nameCy)
    }

    @Test
    fun `a column with no phone keeps the name its distance from the first address row`() {
        val latin = PosterFoot.column(addressLines = 1, phone = false)
        assertEquals(1736f, latin.nameCy, delta)
        val urdu = PosterFoot.column(addressLines = 2, phone = false, pitch = PosterFoot.LINE_ARABIC, arabic = true)
        assertEquals(1784f, urdu.addressCy, delta)
        assertEquals(1716f, urdu.nameCy, delta)
    }

    @Test
    fun `the column rows keep their order whatever the owner chose to show`() {
        for (lines in 1..2) for (phone in listOf(true, false)) for (arabic in listOf(false, true)) {
            val pitch = if (arabic) PosterFoot.LINE_ARABIC else PosterFoot.LINE
            val c = PosterFoot.column(lines, phone, topLimit = 1300f, pitch = pitch, arabic = arabic)
            val label = "lines $lines phone $phone arabic $arabic"
            assertTrue(label, c.sealCy + c.sealR < c.nameCy)
            assertTrue(label, c.nameCy < c.addressCy)
            if (phone) assertTrue(label, c.nameCy < c.phoneCy && c.phoneCy < c.addressCy)
            // The last address row ends above the foot's gold rule at 1880.
            assertTrue(label, c.addressCy + (lines - 1) * pitch + 22f <= 1880f)
        }
    }

    // ------------------------------------------------------------------------------------------ Thank you, stack

    @Test
    fun `a stack with no address keeps the name and puts the phone seventy under it`() {
        val s = PosterFoot.stack(1694f, phone = true, addressLines = 0, pitch = PosterFoot.LINE, bottom = 1838f)
        assertEquals(1694f, s.nameCy, delta)
        assertEquals(1764f, s.phoneCy, delta)
        assertEquals(1838f, s.addressCy, delta)
    }

    @Test
    fun `a stack with one address row leaves the name where the design wants it`() {
        val s = PosterFoot.stack(1694f, phone = true, addressLines = 1, pitch = PosterFoot.LINE, bottom = 1838f)
        assertEquals(1694f, s.nameCy, delta)
        assertEquals(1766f, s.phoneCy, delta)
        assertEquals(1816f, s.addressCy, delta)
    }

    @Test
    fun `a second address row lifts the stack and the name goes up with it`() {
        val s = PosterFoot.stack(1694f, phone = true, addressLines = 2, pitch = PosterFoot.LINE, bottom = 1838f)
        assertEquals(1768f, s.addressCy, delta)
        assertEquals(1718f, s.phoneCy, delta)
        assertEquals(1658f, s.nameCy, delta)
    }

    @Test
    fun `with no phone the address takes the phone's place under the name`() {
        val s = PosterFoot.stack(1694f, phone = false, addressLines = 1, pitch = PosterFoot.LINE, bottom = 1838f)
        assertEquals(1694f, s.nameCy, delta)
        assertEquals(1764f, s.addressCy, delta)
    }

    @Test
    fun `a name that would touch the seal closes the gaps`() {
        // The Thank-you card as built: the seal's shadow ends about 1610 and the brand mark is on.
        val s = PosterFoot.stack(1694f, phone = true, addressLines = 2, pitch = PosterFoot.LINE, bottom = 1808f,
            arabic = false, topLimit = 1610f)
        assertEquals(1636f, s.nameCy, delta)
        assertEquals(1688f, s.phoneCy, delta)
        assertEquals(1738f, s.addressCy, delta)
        assertTrue(s.nameCy - 24f >= 1610f)
    }

    @Test
    fun `a Nastaliq name stands further above its phone than a Latin one`() {
        // Urdu with one address row: the phone 58 over the address, the name 68 over the phone (a Latin name stands 60).
        val urdu = PosterFoot.stack(1694f, phone = true, addressLines = 1, pitch = PosterFoot.LINE_ARABIC, bottom = 1838f,
            arabic = true, topLimit = 1610f)
        assertEquals(1816f, urdu.addressCy, delta)
        assertEquals(1758f, urdu.phoneCy, delta)
        assertEquals(1690f, urdu.nameCy, delta)
        val latin = PosterFoot.stack(1694f, phone = true, addressLines = 1, pitch = PosterFoot.LINE, bottom = 1838f,
            arabic = false, topLimit = 1610f)
        assertEquals(1694f, latin.nameCy, delta)
    }

    @Test
    fun `Urdu with two address rows and the mark on is the one combination that cannot clear the seal`() {
        // Said in the code and the notes: the rows keep their order and their distances, and the name's tails may touch the
        // seal's shadow (the mark is off in the app as shipped, so the owner does not meet it).
        val s = PosterFoot.stack(1694f, phone = true, addressLines = 2, pitch = PosterFoot.LINE_ARABIC, bottom = 1808f,
            arabic = true, topLimit = 1610f)
        assertEquals(1628f, s.nameCy, delta)
        assertEquals(1686f, s.phoneCy, delta)
        assertEquals(1736f, s.addressCy, delta)
        assertTrue(s.nameCy - 42f < 1610f)
    }

    @Test
    fun `the stack rows keep their order and stay on the card in every combination`() {
        for (bottom in listOf(1808f, 1838f)) for (lines in 0..2) for (phone in listOf(true, false)) for (arabic in listOf(false, true)) {
            val pitch = if (arabic) PosterFoot.LINE_ARABIC else PosterFoot.LINE
            val s = PosterFoot.stack(1694f, phone, lines, pitch, bottom, arabic, topLimit = 1610f)
            val label = "bottom $bottom lines $lines phone $phone arabic $arabic"
            if (phone) assertTrue(label, s.nameCy < s.phoneCy)
            if (lines > 0) {
                assertTrue(label, s.nameCy < s.addressCy)
                if (phone) assertTrue(label, s.phoneCy < s.addressCy)
                // The last row's ink ends at the design's bottom, or 8 under it when the stack had to sink to clear the seal.
                assertTrue(label, s.addressCy + (lines - 1) * pitch + 22f <= bottom + 8f + delta)
            }
        }
    }
}
