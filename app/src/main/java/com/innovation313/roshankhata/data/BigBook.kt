package com.innovation313.roshankhata.data

import android.content.Context
import kotlin.random.Random

/**
 * A book the size of a wholesaler's, written into the live database so the
 * app can be measured against it (P0 of the scaling work, 7 Oct 2026).
 *
 * The owner's own book is 1,205 customers. A ledger that is quick at that
 * size tells nothing about one at ten thousand, and "it will never slow down"
 * is a claim that has to be MEASURED before it is made. So: [seed] writes
 * [CUSTOMERS] customers with [ENTRIES_EACH] lines each — 300,000 lines — in
 * batches, and the on-device perf test opens the screens against it with a
 * stopwatch. The About screen's version line seeds it too, by seven taps,
 * in a debug build only; a Play build has no such door.
 *
 * Every seeded name carries [MARK], so [clear] can take the whole book out
 * again and leave the owner's real customers exactly as they were.
 *
 * Names are built from the parts a Pakistani shop's book is made of — a given
 * name, often a village or a trade — so the search folds, the ranks and the
 * duplicates behave the way they do on real names rather than on "User 4817".
 */
object BigBook {
    const val CUSTOMERS = 10_000
    const val ENTRIES_EACH = 30
    /** The first seeded customer's lines: one account with years of dealings, the customer screen's hard case. */
    const val HEAVY_ENTRIES = 5_000
    const val MARK = " (bb)" // a thin space and a tag the owner never types

    private val FIRST = listOf(
        "Muhammad", "Ali", "Asghar", "Bilal", "Tousif", "Yaseen", "Abbas", "Amir", "Amin", "Aslam",
        "Nazeer", "Shahid", "Zahid", "Naeem", "Rana", "Chaudhry", "Malik", "Mian", "Imran", "Kamran",
        "Salim", "Khalid", "Tariq", "Umar", "Usman", "Waqas", "Faisal", "Saleem", "Riaz", "Ijaz"
    )
    private val SECOND = listOf(
        "", "Bhatti", "Butt", "Cheema", "Gujjar", "Ghala Mandi", "Spray Wala", "Lappay Wali", "Pasrur",
        "Dokandar", "Loader", "Manager", "College", "Matykay", "Bobi", "Steno", "Sahib", "Bhai", "Khan", "Ramky"
    )

    private fun name(i: Int, rnd: Random): String {
        val first = FIRST[rnd.nextInt(FIRST.size)]
        val second = SECOND[rnd.nextInt(SECOND.size)]
        val tail = if (second.isEmpty()) "" else " $second"
        return "$first$tail $i$MARK"
    }

    /** Writes the big book. Returns how many customers it added. Safe to call twice: it clears its own earlier seed first. */
    suspend fun seed(
        context: Context,
        customers: Int = CUSTOMERS,
        entriesEach: Int = ENTRIES_EACH,
        progress: ((done: Int) -> Unit)? = null
    ): Int {
        clear(context)
        val dao = KhataDatabase.get(context).khataDao()
        val rnd = Random(313)
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        var entryNo = 0
        var done = 0
        val batch = 500
        var start = 0
        while (start < customers) {
            val end = minOf(start + batch, customers)
            val parties = (start until end).map { i ->
                Party(
                    name = name(i, rnd),
                    phone = "03%02d%07d".format(rnd.nextInt(0, 50), rnd.nextInt(0, 10_000_000)),
                    isCustomer = rnd.nextInt(10) != 0,
                    createdAt = now - rnd.nextLong(0, 900 * day)
                )
            }
            val ids = dao.insertParties(parties)
            val entries = ArrayList<LedgerEntry>(ids.size * entriesEach)
            for ((k, id) in ids.withIndex()) {
                var t = now - rnd.nextLong(0, 700 * day)
                val lines = if (start == 0 && k == 0) HEAVY_ENTRIES else entriesEach
                repeat(lines) {
                    t += if (lines > entriesEach) rnd.nextLong(1, 4 * 60 * 60 * 1000) else rnd.nextLong(day / 4, 20 * day)
                    entryNo++
                    entries.add(
                        LedgerEntry(
                            partyId = id,
                            amount = (rnd.nextInt(1, 400) * 50).toDouble(),
                            isGiven = rnd.nextInt(5) != 0,
                            note = if (rnd.nextInt(4) == 0) "Urea 2 bori" else null,
                            entryNumber = "BB-%06d".format(entryNo),
                            timestamp = minOf(t, now)
                        )
                    )
                }
            }
            dao.restoreEntries(entries)
            done = end
            progress?.invoke(done)
            start = end
        }
        return done
    }

    /** Takes every seeded customer and their lines back out. The owner's own book is untouched. */
    suspend fun clear(context: Context) {
        val dao = KhataDatabase.get(context).khataDao()
        dao.purgeSeeded("%$MARK")
    }
}
