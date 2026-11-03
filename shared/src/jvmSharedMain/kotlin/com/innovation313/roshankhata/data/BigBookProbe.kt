package com.innovation313.roshankhata.data

import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.Words
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.TimeZone

/**
 * The reads a big book makes slow, timed one by one (3 Nov 2026, the speed work): the same calls the
 * screens make, on the phone and on the computer alike, against a book [BigBookSeed] wrote. The phone's
 * on-device test and the computer's test both run it, so the two platforms are measured on one list.
 *
 * Each read runs [RUNS] times; the first is what a page opening cold waits for, the best is the read
 * itself without a garbage collection or a cold disk in the way. Read only, except [AFTER_SAVE], which
 * writes one entry (with one goods line) to a seeded customer, as the owner does after every sale: the
 * seed takes it out again with the rest.
 */
object BigBookProbe {

    const val RUNS = 3
    const val AFTER_SAVE = "After a save: the reads that follow it"

    /** One read: what it is, the first run's and the best run's milliseconds, and how much it read. */
    data class Timing(val what: String, val firstMs: Long, val bestMs: Long, val size: String = "") {
        override fun toString(): String =
            "$what: ${bestMs} ms" + (if (firstMs > bestMs) " (first $firstMs)" else "") + (if (size.isNotEmpty()) " [$size]" else "")
    }

    private const val DAY = 24L * 60 * 60 * 1000

    /** Everything, in the order a shop's day meets it. [money] words a figure for the bell. */
    suspend fun run(db: KhataDatabase, words: Words, money: (Double) -> String = { "%.2f".format(java.util.Locale.ROOT, it) }): List<Timing> {
        val dao = db.khataDao()
        val out = ArrayList<Timing>()
        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val dayStart = DayMath.startOfDay(now, tz)
        val monthStart = Calendar.getInstance(tz).apply {
            timeInMillis = dayStart; set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val heavy = dao.seededPartyId("%${BigBookSeed.MARK}")
        check(heavy > 0) { "no big book in this book" }

        suspend fun <T> time(what: String, size: (T) -> String = { "" }, block: suspend () -> T): T {
            var first = -1L
            var best = Long.MAX_VALUE
            var result: T? = null
            repeat(RUNS) {
                val t = System.nanoTime()
                val r = block()
                val ms = (System.nanoTime() - t) / 1_000_000
                if (first < 0) first = ms
                if (ms < best) best = ms
                result = r
            }
            @Suppress("UNCHECKED_CAST")
            val r = result as T
            out += Timing(what, first, best, size(r))
            return r
        }

        // Khata: every party's balance, their order, a search.
        val parties = time("Khata list: every party's balance", { "${it.size} parties" }) { dao.partiesWithBalanceOnce() }
        time("Khata list: names in order") { NameOrder.sortedBy(parties) { it.name } }
        time("Khata search: one word over every name", { "${it.size} found" }) {
            parties.filter { NameSearch.matches(it.name, it.phone, "bil") }
        }
        // One customer with years of dealings.
        time("Customer (${BigBookSeed.HEAVY_ENTRIES} lines): the newest 50") { dao.latestEntriesWithItems(heavy, 50) }
        time("Customer (${BigBookSeed.HEAVY_ENTRIES} lines): every line with its goods", { "${it.size} lines" }) {
            dao.latestEntriesWithItems(heavy, Int.MAX_VALUE)
        }

        // Home and the bell.
        time("Home: today's entries, the last 8, today's promises") {
            Triple(dao.entriesInRange(dayStart, dayStart + DAY - 1), dao.recentEntries(8), dao.observePromises().first())
        }
        time("Home: the drawer by the books") { DayCloseMath.drawerByTheBooks(dao, now) }
        time("Bell: every alert", { "${it.size} rows" }) { LedgerAlerts.compute(words, dao, money) }

        // Who owes and how they pay: Home's late count, Follow-up, the Payment Radar.
        val points = time("Owing customers' lines", { "${it.size} lines" }) { LedgerLines.owingOf(dao, parties) }
        val habits = time("Paying habit of every customer", { "${it.size} customers" }) { PaymentHabit.forAll(points) }
        val promises = dao.observePromises().first().associate { it.partyId to it.due }
        val shop = time("Payment Radar: the shop's own pace") { PaymentRadar.shopTypicalDays(LedgerLines.customers(dao)) }
        val likely = time("Payment Radar: who usually pays now", { "${it.size} customers" }) {
            PaymentRadar.likelyNow(points, shop, promises, now, tz)
        }
        time("Follow-up: the order of everyone who owes", { "${it.size} customers" }) {
            FollowUpRank.order(parties.filter { Money.isPositive(it.balance) }, habits, promises, now, tz, emptySet(), likely)
        }

        // Reports.
        time("Sale Insights") { Insights.thisMonth(dao) }
        time("Profit: this month and last") { Profit.thisMonth(dao) to Profit.lastMonth(dao) }
        time("Sales report: this month") { SalesReport.compute(dao, monthStart, now) }
        time("Sales report: a year") { SalesReport.compute(dao, now - 365 * DAY, now) }
        time("Khata Report: this month", { "${it.size} entries" }) { dao.entriesInRange(monthStart, now) }
        time("Dues by age") { Aging.compute(dao, now) }
        time("Day report") { ZReport.compute(dao, dayStart, now) }

        // Stock and the counter.
        time("Stock of every product", { "${it.size} products" }) { Stock.of(dao) }
        // Its six sums apart, so a slow one is named (Stock.of is read by the bell, the counter's every ticket,
        // the stock, expiry and orders pages and the sales report).
        time("  stock: bought") { dao.boughtPerProductUnit() }
        time("  stock: sold on the khata") { dao.soldPerProductUnit() }
        time("  stock: back on the khata") { dao.returnedPerProductUnit() }
        time("  stock: sold over the counter") { dao.walkInSoldPerProductUnit() }
        time("  stock: back over the counter") { dao.walkInReturnedPerProductUnit() }
        time("  stock: counted and adjusted") { dao.adjustedPerProductUnit() }
        time("Stock value") { Valuation.compute(dao) }
        time("Expiring batches", { "${it.size} batches" }) { Fefo.expiring(dao, now + ExpiryWindow.WARN_MS) }
        time("Stock register", { "${it.size} batches" }) { Fefo.register(dao) }
        time("Reorder list") { Automations.reorder(dao, now) }
        time("Counter: tickets of 60 days", { "${it.size} tickets" }) { dao.salesBetween(now - 60 * DAY, now + 60_000) }
        time("Cashbook: the first page") { dao.observeCashPage(50).first() }

        // A sale written, and what the screens read again because of it.
        val customer = parties.firstOrNull { it.id != heavy && it.isCustomer && it.name.endsWith(BigBookSeed.MARK) }?.id ?: heavy
        val product = dao.productsOnce().firstOrNull { it.name.endsWith(BigBookSeed.MARK) }
        time("Save one sale with its goods") {
            dao.insertEntryWithItems(
                LedgerEntry(partyId = customer, amount = 1_000.0, isGiven = true, entryNumber = ""),
                listOf(EntryItem(entryId = 0, itemName = product?.name ?: "Test", quantity = 1.0, rate = 1_000.0, productId = product?.id))
            )
        }
        time(AFTER_SAVE) {
            val fresh = dao.partiesWithBalanceOnce()
            LedgerAlerts.compute(words, dao, money)
            PaymentHabit.forAll(LedgerLines.owingOf(dao, fresh))
            dao.latestEntriesWithItems(customer, Int.MAX_VALUE)
        }
        return out
    }
}
