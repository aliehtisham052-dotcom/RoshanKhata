package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The book seal's arithmetic (4 Nov 2026): the leaf text, the header and the digest, held to values worked out by hand
 * with `sha256sum` (printf of the leaf text; cat of the header and the leaf's 32 bytes). Made-up entry only.
 */
class SealMathTest {

    private val line = SealLine(
        id = 7, uid = "u1", entryNumber = "RK-000007", amount = 1250.5, isGiven = true, timestamp = 1_700_000_000_000,
        note = null, itemName = "Urea", quantity = 2.5, unit = "bag", paymentRef = null, partyUid = "p1"
    )

    @Test
    fun theLeafTextIsEveryFieldInOrderWithEmptyForNothing() {
        assertEquals("e1|u1|RK-000007|1250.50|1|1700000000000||Urea|2.5|bag||p1", SealMath.leafText(line))
        // printf 'e1|u1|RK-000007|1250.50|1|1700000000000||Urea|2.5|bag||p1' | sha256sum
        assertEquals("70199e88b9461c40a982d8dbfa86de50b964d011a62d0b421ee2a48ee8827d76", SealMath.hex(SealMath.leafHash(SealMath.leafText(line))))
        val got = line.copy(isGiven = false, amount = 3.0, quantity = null, unit = null, itemName = null, note = "cash", paymentRef = "TID 1", partyUid = null)
        assertEquals("e1|u1|RK-000007|3.00|0|1700000000000|cash||||TID 1|", SealMath.leafText(got))
    }

    @Test
    fun theDigestIsTheHeaderThenTheLeaves() {
        val header = SealMath.header("ab", 1_700_000_000_123, 6, 7, 1, null)
        assertEquals("rk-seal-v1\nab\n1700000000123\n6-7\n1\n\n", header)
        val leaf = SealMath.leafHash(SealMath.leafText(line))
        // cat header.txt leaf.bin | sha256sum
        assertEquals("2ac30e39ef184ab582b5cf629d3669485604822bb6cef3767c25133ba90f1f49", SealMath.digest(header, leaf))
        // The same again is the same: nothing in it reads the clock or a random.
        assertEquals(SealMath.digest(header, leaf), SealMath.digest(SealMath.header("ab", 1_700_000_000_123, 6, 7, 1, null), leaf.copyOf()))
    }

    @Test
    fun theChainAndTheAuditHeadAreInTheDigest() {
        val leaf = SealMath.leafHash(SealMath.leafText(line))
        val base = SealMath.digest(SealMath.header("ab", 1, 6, 7, 1, "h"), leaf)
        assertNotEquals(base, SealMath.digest(SealMath.header("ac", 1, 6, 7, 1, "h"), leaf))
        assertNotEquals(base, SealMath.digest(SealMath.header("ab", 1, 6, 7, 1, "i"), leaf))
        assertNotEquals(base, SealMath.digest(SealMath.header("ab", 1, 6, 7, 1, "h"), SealMath.leafHash(SealMath.leafText(line.copy(amount = 1250.51)))))
        // The first seal: no previous digest, no leaves.
        assertEquals(64, SealMath.digest(SealMath.header(null, 1, 9, 9, 0, null), null).length)
    }

    @Test
    fun aLeafIsFoundOnlyAtA32ByteStep() {
        val a = SealMath.leafHash("a"); val b = SealMath.leafHash("b"); val c = SealMath.leafHash("c")
        val leaves = a + b
        assertEquals(0, SealMath.indexOf(leaves, a))
        assertEquals(1, SealMath.indexOf(leaves, b))
        assertEquals(-1, SealMath.indexOf(leaves, c))
        assertEquals(-1, SealMath.indexOf(null, a))
        // Bytes that straddle two leaves are no leaf.
        assertEquals(-1, SealMath.indexOf(leaves, leaves.copyOfRange(16, 48)))
        assertEquals("00ff10", SealMath.hex(SealMath.unhex("00FF10")))
    }
}
