package com.innovation313.roshankhata.ui

import com.innovation313.roshankhata.data.ProductName
import com.innovation313.roshankhata.ui.DuplicateDetector.Candidate
import com.innovation313.roshankhata.ui.DuplicateDetector.Caution
import com.innovation313.roshankhata.ui.DuplicateDetector.Group
import com.innovation313.roshankhata.ui.DuplicateDetector.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [DuplicateDetector.find] and [DuplicateDetector.caution] read a book by buckets since 25 Nov 2026 (the ANR audit,
 * item 6). The groups and the warnings must be exactly what the old, quadratic reading gave: the old code is copied
 * here ([OldDetector]) and both are run on random invented books — names that fold the same, numbers typed several
 * ways, numbers one digit apart (in other scripts' digits too), short numbers, blank names, a party id listed twice,
 * groups the owner dismissed. Then a 10,000-customer book is timed both ways.
 *
 * Every name and number here is invented (lesson 80).
 */
class DuplicateDetectorBucketTest {

    /** The reading before 25 Nov 2026, word for word, so the new one is held to it. */
    private object OldDetector {
        fun find(all: List<Candidate>, dismissedKeys: Set<String> = emptySet()): List<Group> {
            val nameGroups = all.groupBy { ProductName.normalised(it.name) }
                .filterKeys { it.isNotBlank() }
                .values
                .filter { it.size >= 2 }

            val phoneGroups = all.groupBy { DuplicateDetector.normalisedPhone(it.phone) }
                .filterKeys { it != null }
                .values
                .filter { it.size >= 2 }

            val byIds = LinkedHashMap<Set<Long>, Reason>()
            for (members in nameGroups) {
                byIds[members.map { it.partyId }.toSet()] = Reason.NAME
            }
            for (members in phoneGroups) {
                val ids = members.map { it.partyId }.toSet()
                byIds[ids] = if (byIds.containsKey(ids)) Reason.BOTH else Reason.PHONE
            }

            return byIds.map { (ids, reason) ->
                Group(all.filter { it.partyId in ids }, reason)
            }.filter { DuplicateDetector.groupKey(it.members) !in dismissedKeys }
                .sortedByDescending { it.members.size }
        }

        fun caution(group: Group): Caution {
            val phones = group.members.mapNotNull { DuplicateDetector.normalisedPhone(it.phone) }
            for (i in phones.indices) for (j in i + 1 until phones.size) {
                if (DuplicateDetector.oneDigitApart(phones[i], phones[j])) return Caution.ONE_DIGIT
            }
            if (group.reason == Reason.NAME && phones.size >= 2 && phones.toSet().size == phones.size) return Caution.NUMBERS_DIFFER
            return Caution.NONE
        }
    }

    private val first = listOf("Akram", "Bilal", "Kashif", "Waseem", "Naveed", "Tariq", "Imran", "Sajid", "Zubair", "Faisal",
        "Asghar", "Asgar", "Bavistin", "Bavisteen", "Rashid", "Rasheed", "Yousaf", "Yusuf", "Haroon", "Haroun")
    private val last = listOf("", "", "Khan", "Bhai", "Store", "Traders", "Ali", "Butt")

    /** A name, sometimes written another way: case, spaces, a variant spelling, nothing at all, letters of another script. */
    private fun name(r: Random): String = when (r.nextInt(20)) {
        0 -> ""
        1 -> "   "
        2 -> "بشیر"
        3 -> "(" + first.random(r) + ")"
        else -> {
            val f = first.random(r)
            val l = last.random(r)
            val base = if (l.isEmpty()) f else "$f $l"
            when (r.nextInt(4)) { 0 -> base.uppercase(); 1 -> "  $base "; 2 -> base.replace(" ", "  "); else -> base }
        }
    }

    /** A number from a small pool, typed one of several ways, sometimes one digit off, short, blank or in Arabic-Indic digits. */
    private fun phone(r: Random, pool: List<String>): String? {
        val core = pool.random(r)                        // ten digits, "300…"
        val typed = when (r.nextInt(12)) {
            0 -> return null
            1 -> return ""
            2 -> return "12345"                          // too short to group on
            3 -> core.substring(0, 6) + ((core[6] - '0' + 1 + r.nextInt(8)) % 10) + core.substring(7)   // one digit off
            4 -> core.map { if (it.isDigit()) '٠' + (it - '0') else it }.joinToString("")         // Arabic-Indic digits
            else -> core
        }
        return when (r.nextInt(4)) {
            0 -> "0" + typed.take(3) + "-" + typed.drop(3)
            1 -> "+92 " + typed.take(3) + " " + typed.drop(3)
            2 -> "0092$typed"
            else -> "0$typed"
        }
    }

    private fun book(r: Random, size: Int, phones: Int): List<Candidate> {
        val pool = List(phones) { "300" + (1_000_000 + r.nextInt(8_999_999)).toString() }
        val out = ArrayList<Candidate>(size)
        var id = 1L
        repeat(size) {
            // Now and then the same party id twice (a list put together from two reads): both records are a member.
            val pid = if (out.isNotEmpty() && r.nextInt(40) == 0) out.random(r).partyId else id++
            out += Candidate(pid, name(r), phone(r, pool), isCustomer = r.nextBoolean(), balance = r.nextInt(-5000, 5000).toDouble())
        }
        return out
    }

    @Test
    fun `the buckets give exactly the old groups, order, reasons and warnings, on random invented books`() {
        var groupsSeen = 0
        val cautionsSeen = HashSet<Caution>()
        val reasonsSeen = HashSet<Reason>()
        for (seed in 0 until 400) {
            val r = Random(seed)
            val all = book(r, size = r.nextInt(0, 120), phones = r.nextInt(1, 40))
            val old = OldDetector.find(all)
            // Groups the owner dismissed: some of the old answer's own keys, and one that matches nothing.
            val dismissed = old.filter { r.nextInt(3) == 0 }.map { DuplicateDetector.groupKey(it.members) }.toSet() + "999999"
            for (keys in listOf(emptySet(), dismissed)) {
                val want = OldDetector.find(all, keys)
                val got = DuplicateDetector.find(all, keys)
                assertEquals("seed $seed, dismissed ${keys.size}", want, got)
                for (g in got) {
                    assertEquals("seed $seed, caution of ${DuplicateDetector.groupKey(g.members)}", OldDetector.caution(g), DuplicateDetector.caution(g))
                    cautionsSeen += DuplicateDetector.caution(g)
                    reasonsSeen += g.reason
                }
                groupsSeen += got.size
            }
        }
        // The books really reached every rule: otherwise equality would prove little.
        assertTrue("groups compared: $groupsSeen", groupsSeen > 2000)
        assertEquals(Caution.values().toSet(), cautionsSeen)
        assertEquals(Reason.values().toSet(), reasonsSeen)
    }

    @Test
    fun `the one-digit rule is the old pairwise rule on any list of numbers`() {
        val r = Random(313)
        repeat(5000) { n ->
            val size = r.nextInt(0, 8)
            val digits = if (r.nextBoolean()) "0123456789" else "٠١٢٣"
            val phones = List(size) {
                val len = 7 + r.nextInt(4)
                String(CharArray(len) { digits[r.nextInt(if (r.nextInt(3) == 0) 2 else digits.length)] })
            }
            var pairwise = false
            for (i in phones.indices) for (j in i + 1 until phones.size) if (DuplicateDetector.oneDigitApart(phones[i], phones[j])) pairwise = true
            assertEquals("case $n: $phones", pairwise, DuplicateDetector.anyOneDigitApart(phones))
        }
        // The edges by name: the same number twice is not one digit apart; one place off is; two places off is not;
        // a different length never is.
        assertEquals(false, DuplicateDetector.anyOneDigitApart(listOf("3001234567", "3001234567")))
        assertEquals(true, DuplicateDetector.anyOneDigitApart(listOf("3001234567", "3001234567", "3001244567")))
        assertEquals(false, DuplicateDetector.anyOneDigitApart(listOf("3001234567", "3001244568")))
        assertEquals(false, DuplicateDetector.anyOneDigitApart(listOf("300123456", "3001234567")))
    }

    @Test
    fun `a 10,000-customer book is grouped no slower than before`() {
        val r = Random(2026)
        // A big shop's book: 10,000 parties, a few thousand names shared, numbers mostly their own, a few shared.
        val bases = List(3000) { i -> first[i % first.size] + " " + (if (i % 7 == 0) "" else last[i % last.size] + " ") + "w${i / first.size}" }
        val all = List(10_000) { i ->
            val pid = i + 1L
            val phone = if (r.nextInt(5) == 0) null else "03" + (100_000_000 + r.nextInt(899_999_999)).toString().take(9)
            Candidate(pid, bases[r.nextInt(bases.size)], phone, isCustomer = true, balance = 0.0)
        } + List(400) { i -> Candidate(20_000L + i, "Shared Number $i", "0300 0000001", isCustomer = true, balance = 0.0) }

        fun best(times: Int, f: () -> Any): Long = (1..times).minOf {
            val t0 = System.nanoTime(); f(); (System.nanoTime() - t0) / 1_000_000
        }
        // Warm both up first, so neither pays the other's class loading.
        val want = OldDetector.find(all)
        val got = DuplicateDetector.find(all)
        assertEquals(want, got)
        val oldFind = best(3) { OldDetector.find(all) }
        val newFind = best(3) { DuplicateDetector.find(all) }
        val oldCaution = best(3) { want.forEach { OldDetector.caution(it) } }
        val newCaution = best(3) { got.forEach { DuplicateDetector.caution(it) } }
        println("DuplicateDetector on ${all.size} parties, ${got.size} groups: find old $oldFind ms, new $newFind ms; " +
            "caution of every group old $oldCaution ms, new $newCaution ms")
        // Only that it is not slower: a CI machine's clock is too noisy for a ratio. 50 ms of noise allowed.
        assertTrue("find: old $oldFind ms, new $newFind ms", newFind <= oldFind + 50)
        assertTrue("caution: old $oldCaution ms, new $newCaution ms", newCaution <= oldCaution + 50)
    }
}
