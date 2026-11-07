package com.innovation313.roshankhata.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The book seal's arithmetic (4 Nov 2026): the leaf text, the header and the digest, held to values worked out by hand
 * with `sha256sum` (printf of the leaf text; cat of the header and the leaf's 32 bytes). Made-up entry only. Version 2
 * (7 Nov 2026, the security review's M3): the goods lines, the way it was paid, the loan and bin marks are in the leaf,
 * every field with its length before it, and no entry id is in the header.
 */
class SealMathTest {

    private val line = SealLine(
        id = 7, uid = "u1", entryNumber = "RK-000007", amount = 1250.5, isGiven = true, timestamp = 1_700_000_000_000,
        note = null, itemName = "Urea", quantity = 2.5, unit = "bag", paymentRef = null, partyUid = "p1"
    )

    private val entry = SealEntry(
        id = 7, uid = "u1", entryNumber = "RK-000007", amount = 1250.5, isGiven = true, timestamp = 1_700_000_000_000,
        note = null, paymentMethod = null, paymentRef = null, isQarzeHasna = false, isDeleted = false, partyUid = "p1"
    )

    private val urea = SealGoods(
        entryId = 7, lineNo = 0, itemName = "Urea", quantity = 2.5, unit = "bori", rate = 500.2, isBonus = false, batchUid = "b1"
    )

    @Test
    fun anE2LeafIsEveryFieldWithItsLengthTheGoodsTheWayItWasPaidTheLoanAndTheBin() {
        val text = SealMath.leafTextV2(entry, listOf(urea))
        assertEquals(
            "e2|2:u1|9:RK-000007|8:1250.500|1:1|13:1700000000000|0:|0:|0:|1:0|1:0|2:p1|1:1|4:Urea|3:2.5|3:bag|5:500.2|1:0|2:b1",
            text
        )
        // printf '%s' '<the text above>' | sha256sum
        assertEquals("0893868c1debdae4c68ee5e5b0e2d5e70049ad22c878c18b0b3807c1eb102e4a", SealMath.hex(SealMath.leafHash(text)))
        // A length is in UTF-8 bytes: an Urdu note of five letters is ten.
        val got = entry.copy(
            isGiven = false, note = "یوریا", paymentMethod = "bank", paymentRef = "TID 77", isQarzeHasna = true, isDeleted = true, partyUid = null
        )
        val gotText = SealMath.leafTextV2(got, emptyList())
        assertEquals("e2|2:u1|9:RK-000007|8:1250.500|1:0|13:1700000000000|10:یوریا|4:bank|6:TID 77|1:1|1:1|0:|1:0", gotText)
        assertEquals("25cd5c6a0055c97e058bf50a3b2f42600f75a9f3a96302cfdf4a516e7f58e119", SealMath.hex(SealMath.leafHash(gotText)))
    }

    @Test
    fun everyFieldTheSealCoversChangesTheE2Leaf() {
        val base = SealMath.leafTextV2(entry, listOf(urea))
        val changed = listOf(
            SealMath.leafTextV2(entry.copy(paymentMethod = "cash"), listOf(urea)),
            SealMath.leafTextV2(entry.copy(paymentRef = "TID 1"), listOf(urea)),
            SealMath.leafTextV2(entry.copy(isQarzeHasna = true), listOf(urea)),
            SealMath.leafTextV2(entry.copy(isDeleted = true), listOf(urea)),
            SealMath.leafTextV2(entry.copy(amount = 1250.501), listOf(urea)),
            SealMath.leafTextV2(entry, listOf(urea.copy(itemName = "DAP"))),
            SealMath.leafTextV2(entry, listOf(urea.copy(quantity = 3.0))),
            SealMath.leafTextV2(entry, listOf(urea.copy(rate = 500.25))),
            SealMath.leafTextV2(entry, listOf(urea.copy(unit = "kg"))),
            SealMath.leafTextV2(entry, listOf(urea.copy(isBonus = true))),
            SealMath.leafTextV2(entry, listOf(urea.copy(batchUid = "b2"))),
            SealMath.leafTextV2(entry, listOf(urea.copy(batchUid = null))),
            SealMath.leafTextV2(entry, listOf(urea, urea.copy(lineNo = 1))),
            SealMath.leafTextV2(entry, emptyList())
        )
        for (c in changed) assertNotEquals(base, c)
        assertEquals(changed.size, changed.toSet().size)
        // The unit as its saved key, in whatever language it was typed; the lines in their own order.
        assertEquals(base, SealMath.leafTextV2(entry, listOf(urea.copy(unit = " بوری "))))
        val two = listOf(urea.copy(lineNo = 1, itemName = "DAP"), urea)
        assertEquals(SealMath.leafTextV2(entry, two), SealMath.leafTextV2(entry, two.reversed()))
    }

    @Test
    fun aLengthBeforeEachFieldEndsTheBarsAmbiguityOfE1() {
        // e1 joined its fields with "|": a note holding one could be read as the next field.
        assertEquals(SealMath.leafText(line.copy(note = "x|y", itemName = null)), SealMath.leafText(line.copy(note = "x", itemName = "y|")))
        assertNotEquals(
            SealMath.leafTextV2(entry.copy(note = "x|y", paymentMethod = null), emptyList()),
            SealMath.leafTextV2(entry.copy(note = "x", paymentMethod = "y|"), emptyList())
        )
    }

    @Test
    fun figuresAreReadFromTheDoubleItselfSoEveryJavaWritesThemAlike() {
        assertEquals("1250.500", SealMath.figure(1250.5, 3))
        assertEquals("0.300", SealMath.figure(0.1 + 0.2, 3))
        assertEquals("12.345", SealMath.figure(12.345, 3))
        assertEquals("2.5", SealMath.plain(2.5))
        assertEquals("10", SealMath.plain(10.0))
        assertEquals("0", SealMath.plain(0.0))
        assertEquals("0", SealMath.plain(-0.0))
        assertEquals("0.333333", SealMath.plain(1.0 / 3))
        assertEquals("1000000", SealMath.plain(1e6))
        assertEquals("0.0005", SealMath.plain(0.0005))
    }

    @Test
    fun theV2DigestIsItsHeaderThenTheLeavesWithNoEntryIdInIt() {
        val header = SealMath.headerV2("ab", 1_700_000_000_123, 1, null)
        assertEquals("rk-seal-v2\nab\n1700000000123\n1\n\n", header)
        val leaf = SealMath.leafHash(SealMath.leafTextV2(entry, listOf(urea)))
        // { printf 'rk-seal-v2\nab\n1700000000123\n1\n\n'; printf '<the leaf's 32 bytes>'; } | sha256sum
        assertEquals("872e84d6a0c7260f0535ee2bfdb38b1dfccfedcb070f291dd4803b279842212e", SealMath.digest(header, leaf))
        // The same leaves under a version-1 header are another digest: a seal's digest says which version it is.
        assertNotEquals(SealMath.digest(SealMath.header("ab", 1_700_000_000_123, 6, 7, 1, null), leaf), SealMath.digest(header, leaf))
    }

    @Test
    fun theLeafTextIsEveryFieldInOrderWithEmptyForNothing() {
        assertEquals("e1|u1|RK-000007|1250.500|1|1700000000000||Urea|2.5|bag||p1", SealMath.leafText(line))
        // printf 'e1|u1|RK-000007|1250.500|1|1700000000000||Urea|2.5|bag||p1' | sha256sum
        assertEquals("c3543bf80745578dcedb0758236730098abea0e1fe038eb21083d17d7a58d047", SealMath.hex(SealMath.leafHash(SealMath.leafText(line))))
        val got = line.copy(isGiven = false, amount = 3.0, quantity = null, unit = null, itemName = null, note = "cash", paymentRef = "TID 1", partyUid = null)
        assertEquals("e1|u1|RK-000007|3.000|0|1700000000000|cash||||TID 1|", SealMath.leafText(got))
        // A three-decimal currency (JOD, TND, LYD) is sealed whole: 12.345 and 12.346 are two leaves.
        assertEquals("e1|u1|RK-000007|12.345|1|1700000000000||Urea|2.5|bag||p1", SealMath.leafText(line.copy(amount = 12.345)))
    }

    @Test
    fun aUnitIsSealedAsTheBooksRepairWouldSaveItSoARepairedRowStillMatches() {
        // An old row saved with a label ("bori", "بوری") is turned into its key ("bag") by the repair on opening
        // (UnitWords.normalizeStatements); before or after, the leaf is the same.
        val key = SealMath.leafText(line.copy(unit = "bag"))
        assertEquals(key, SealMath.leafText(line.copy(unit = "bori")))
        assertEquals(key, SealMath.leafText(line.copy(unit = " بوری ")))
        assertEquals(key, SealMath.leafText(line.copy(unit = "Bag")))
        // The owner's own word is kept as written (trimmed), as the repair keeps it.
        assertEquals("e1|u1|RK-000007|1250.500|1|1700000000000||Urea|2.5|gaddi||p1", SealMath.leafText(line.copy(unit = "gaddi ")))
        // Every label the repair maps reads as the key it maps to.
        for ((label, k) in UnitWords.TO_KEY) assertEquals(label, k, UnitWords.canonical(label))
    }

    @Test
    fun theDigestIsTheHeaderThenTheLeaves() {
        val header = SealMath.header("ab", 1_700_000_000_123, 6, 7, 1, null)
        assertEquals("rk-seal-v1\nab\n1700000000123\n6-7\n1\n\n", header)
        val leaf = SealMath.leafHash(SealMath.leafText(line))
        // cat header.txt leaf.bin | sha256sum
        assertEquals("1f3e9699238c4bcef0c75808b29fce696f2dad72626573195ffc6917dae512f2", SealMath.digest(header, leaf))
        // The same again is the same: nothing in it reads the clock or a random.
        assertEquals(SealMath.digest(header, leaf), SealMath.digest(SealMath.header("ab", 1_700_000_000_123, 6, 7, 1, null), leaf.copyOf()))
    }

    @Test
    fun theChainAndTheAuditHeadAreInTheDigest() {
        val leaf = SealMath.leafHash(SealMath.leafText(line))
        val base = SealMath.digest(SealMath.header("ab", 1, 6, 7, 1, "h"), leaf)
        assertNotEquals(base, SealMath.digest(SealMath.header("ac", 1, 6, 7, 1, "h"), leaf))
        assertNotEquals(base, SealMath.digest(SealMath.header("ab", 1, 6, 7, 1, "i"), leaf))
        assertNotEquals(base, SealMath.digest(SealMath.header("ab", 1, 6, 7, 1, "h"), SealMath.leafHash(SealMath.leafText(line.copy(amount = 1250.501)))))
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
