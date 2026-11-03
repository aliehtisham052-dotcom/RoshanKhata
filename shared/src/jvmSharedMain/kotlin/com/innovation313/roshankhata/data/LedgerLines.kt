package com.innovation313.roshankhata.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest

/**
 * Every customer's lines, read a slice of customers at a time (3 Nov 2026, the speed work).
 *
 * Home's late count, Follow-up, the morning list, the Payment Radar and Dues by age each read every line of
 * many customers — 275,000 rows on a wholesaler's book. In one answer that cost 10.8 seconds on CI's Android
 * emulator against 0.7 on the computer: Android hands a query's answer to the app 2 MB at a time and runs the
 * query again from its first row for every 2 MB. [SLICE] customers' lines fit one such window, and each slice
 * is read off schema 42's covering index alone. The rows are the ones the single queries gave, in the same
 * order within each customer.
 *
 * The slices are not one transaction: on the phone a read transaction holds every save for as long as it
 * lasts. A save between two slices makes the screens' flows read again, so what they show settles on one book.
 */
object LedgerLines {

    /** Customers per read: under SQLite's 999 values before 3.32 (Android 10 and older), and one 2 MB window. */
    const val SLICE = 400

    /** Who owes, by the rule the single query had: a live customer whose balance is at least 0.005. */
    fun owing(parties: List<PartyWithBalance>): List<Long> =
        parties.filter { it.isCustomer && it.balance >= 0.005 }.map { it.id }

    /** [partyIds]' live lines, by customer and time. */
    suspend fun of(dao: KhataDao, partyIds: List<Long>): List<LedgerPoint> {
        val out = ArrayList<LedgerPoint>()
        for (slice in partyIds.sorted().chunked(SLICE)) out += dao.ledgerPointsOf(slice)
        return out
    }

    /** The owing customers' lines, as [parties] stand. */
    suspend fun owingOf(dao: KhataDao, parties: List<PartyWithBalance>): List<LedgerPoint> = of(dao, owing(parties))

    /** The owing customers' lines, read again whenever a party or a line changes (the single query's own trigger). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeOwing(dao: KhataDao): Flow<List<LedgerPoint>> = dao.observePartiesWithBalance().mapLatest { owingOf(dao, it) }

    /** Every live customer's lines: the shop's own pace for the Payment Radar ([PaymentRadar.shopTypicalDays]). */
    suspend fun customers(dao: KhataDao): List<LedgerPoint> = of(dao, dao.liveCustomerIds())

    /** [parties]' entries as [Aging] needs them, each party's newest first; only parties with something to age. */
    suspend fun agingMoves(dao: KhataDao, parties: List<PartyWithBalance>): List<AgingMove> {
        val out = ArrayList<AgingMove>()
        val ids = parties.filter { kotlin.math.abs(it.balance) > 0.005 }.map { it.id }.sorted()
        for (slice in ids.chunked(SLICE)) out += dao.agingMovesOf(slice)
        return out
    }
}
