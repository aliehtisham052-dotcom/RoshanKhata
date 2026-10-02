package com.innovation313.roshankhata.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Fasal ka Hisaab (2 Oct): credit read the way an agri dealer thinks of it —
 * by crop season, not as one running balance.
 *
 * A season is where the credit was GIVEN, by date unless the owner set it on
 * the entry (LedgerEntry.season):
 *   Oct–Dec → Rabi of that year (wheat sown now, cut in Apr–May next year)
 *   Jan–Mar → Rabi of the year before
 *   Apr–Sep → Kharif of that year (cotton/rice, cut in Oct–Nov)
 *
 * Payments are not tagged to a season — the farmer just pays. They settle the
 * OLDEST credit first (first in, first out), the way every dealer counts it:
 * the money that came in after the wheat paid for the wheat's inputs. So a
 * season's credit is "cleared" on the day the payment reaching its last rupee
 * arrived. Money counted in paisa (Long), so sums never drift.
 */
object SeasonBook {

    enum class Crop { RABI, KHARIF }

    /** Rabi [year] = sown in [year], cut in [year]+1 ("Rabi 2026-27"). */
    data class Season(val crop: Crop, val year: Int) : Comparable<Season> {
        /** Stored on an entry: "R2026" / "K2026". */
        val key: String get() = (if (crop == Crop.RABI) "R" else "K") + year

        /** Chronological: Kharif 2026 (Apr) comes before Rabi 2026-27 (Oct). */
        override fun compareTo(other: Season): Int =
            compareValuesBy(this, other, { it.year }, { if (it.crop == Crop.KHARIF) 0 else 1 })

        fun previous(): Season = if (crop == Crop.RABI) Season(Crop.KHARIF, year) else Season(Crop.RABI, year - 1)
        fun next(): Season = if (crop == Crop.KHARIF) Season(Crop.RABI, year) else Season(Crop.KHARIF, year + 1)

        companion object {
            fun fromKey(key: String?): Season? {
                if (key == null || key.length != 5) return null
                val year = key.substring(1).toIntOrNull() ?: return null
                return when (key[0]) {
                    'R' -> Season(Crop.RABI, year)
                    'K' -> Season(Crop.KHARIF, year)
                    else -> null
                }
            }
        }
    }

    fun seasonOf(millis: Long, tz: TimeZone = TimeZone.getDefault()): Season {
        val c = Calendar.getInstance(tz).apply { timeInMillis = millis }
        val y = c.get(Calendar.YEAR)
        return when (c.get(Calendar.MONTH) + 1) {
            in 10..12 -> Season(Crop.RABI, y)
            in 1..3 -> Season(Crop.RABI, y - 1)
            else -> Season(Crop.KHARIF, y)
        }
    }

    /** End of the harvest window: 31 May (Rabi) / 30 Nov (Kharif), end of day. */
    fun harvestEnd(s: Season, tz: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(tz).apply {
            clear()
            if (s.crop == Crop.RABI) set(s.year + 1, Calendar.MAY, 31, 23, 59, 59)
            else set(s.year, Calendar.NOVEMBER, 30, 23, 59, 59)
        }
        return c.timeInMillis
    }

    /** One live ledger line of a customer. */
    data class Line(
        val partyId: Long,
        val timestamp: Long,
        val amount: Double,
        val isGiven: Boolean,
        /** The owner's own season for this entry, or null = by date. */
        val seasonKey: String? = null
    )

    /** One customer in one season. Amounts in rupees, rounded to the paisa. */
    data class PartySeason(
        val partyId: Long,
        val season: Season,
        val given: Double,
        val cleared: Double,
        /** When the last rupee of this season was paid; null while any is left. */
        val clearedAt: Long?
    ) {
        val outstanding: Double get() = given - cleared
    }

    fun book(lines: List<Line>, tz: TimeZone = TimeZone.getDefault()): List<PartySeason> {
        val out = mutableListOf<PartySeason>()
        for ((partyId, own) in lines.groupBy { it.partyId }) {
            // Same moment: the credit is on the books before the payment.
            val sorted = own.sortedWith(compareBy({ it.timestamp }, { if (it.isGiven) 0 else 1 }))
            class Chunk(val season: Season, var left: Long)
            val open = ArrayDeque<Chunk>()
            val given = LinkedHashMap<Season, Long>()
            val cleared = HashMap<Season, Long>()
            val lastPaid = HashMap<Season, Long>()
            var advance = 0L // paid before there was anything to pay

            fun settle(season: Season, paisa: Long, at: Long) {
                cleared[season] = (cleared[season] ?: 0L) + paisa
                lastPaid[season] = at
            }

            for (l in sorted) {
                val paisa = Math.round(l.amount * 100)
                if (paisa <= 0) continue
                if (l.isGiven) {
                    val s = Season.fromKey(l.seasonKey) ?: seasonOf(l.timestamp, tz)
                    given[s] = (given[s] ?: 0L) + paisa
                    val fromAdvance = minOf(advance, paisa)
                    if (fromAdvance > 0) { advance -= fromAdvance; settle(s, fromAdvance, l.timestamp) }
                    if (paisa - fromAdvance > 0) open.addLast(Chunk(s, paisa - fromAdvance))
                } else {
                    var pay = paisa
                    while (pay > 0 && open.isNotEmpty()) {
                        val head = open.first()
                        val take = minOf(pay, head.left)
                        head.left -= take
                        pay -= take
                        settle(head.season, take, l.timestamp)
                        if (head.left == 0L) open.removeFirst()
                    }
                    advance += pay
                }
            }
            val stillOpen = open.map { it.season }.toSet()
            for ((s, g) in given) {
                val c = cleared[s] ?: 0L
                out += PartySeason(
                    partyId = partyId,
                    season = s,
                    given = g / 100.0,
                    cleared = c / 100.0,
                    clearedAt = if (s !in stillOpen && c >= g) lastPaid[s] else null
                )
            }
        }
        return out
    }

    /** A season across all customers. */
    data class Summary(
        val season: Season,
        val customers: Int,
        val given: Double,
        val cleared: Double,
        val clearedCustomers: Int,
        /** Median days after the harvest window that a cleared customer finished; null with none. */
        val medianDaysAfterHarvest: Int?
    ) {
        val outstanding: Double get() = given - cleared
    }

    fun summaries(rows: List<PartySeason>, tz: TimeZone = TimeZone.getDefault()): List<Summary> =
        rows.groupBy { it.season }.map { (s, list) ->
            val end = harvestEnd(s, tz)
            val days = list.mapNotNull { r -> r.clearedAt?.let { daysBetween(end, it, tz) } }.sorted()
            Summary(
                season = s,
                customers = list.size,
                given = LineMath.round(list.sumOf { it.given }),
                cleared = LineMath.round(list.sumOf { it.cleared }),
                clearedCustomers = days.size,
                medianDaysAfterHarvest = if (days.isEmpty()) null else days[days.size / 2]
            )
        }.newestFirst()

    private fun List<Summary>.newestFirst() = sortedWith(compareByDescending { it.season })

    /** Calendar days from [from]'s date to [to]'s, in [tz]; negative when [to] is earlier. */
    fun daysBetween(from: Long, to: Long, tz: TimeZone = TimeZone.getDefault()): Int {
        val day = 24L * 60 * 60 * 1000
        fun d(m: Long) = Math.floorDiv(m + tz.getOffset(m), day)
        return (d(to) - d(from)).toInt()
    }
}
