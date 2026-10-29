package com.innovation313.roshankhata.desktop

import androidx.compose.runtime.Composable
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.W

/** The Khata list's three tabs, as the phone's typeTabs. */
enum class PartyType(val word: String) {
    ALL(W.filter_type_all), CUSTOMERS(W.filter_type_customers), SUPPLIERS(W.filter_type_suppliers)
}

/** Which side of the book (the phone's SideFilter): settled is a choice of its own. */
enum class PartySide(val word: String) {
    ALL(W.range_all), TO_GET(W.you_will_get), TO_GIVE(W.you_will_give), SETTLED(W.settled)
}

/** The phone's five orders for the Khata list (SortMode); most recent dealing is its default. */
enum class PartySort(val word: String) {
    RECENT(W.sort_recent_activity), NAME_AZ(W.sort_name_az), NAME_ZA(W.sort_name_za),
    OWES_MOST(W.sort_owes_most), I_OWE_MOST(W.sort_i_owe_most)
}

/**
 * The phone's KhataActivity.rank, step for step: the search, then the side,
 * then customer or supplier; while searching the chosen order steps aside for
 * the best match first, otherwise the chosen order.
 */
fun List<PartyWithBalance>.shownBy(
    query: String, side: PartySide, type: PartyType, sort: PartySort, window: LongRange? = null
): List<PartyWithBalance> {
    val q = query.trim().lowercase()
    val searched = if (q.isEmpty()) this else filter { NameSearch.matches(it.name, it.phone, q) }
    // Days first, as the phone's: "who did I deal with today" is about their last dealing.
    val found = if (window == null) searched else searched.filter { it.lastActivity in window }
    val bySide = when (side) {
        PartySide.ALL -> found
        PartySide.TO_GET -> found.filter { Money.isPositive(it.balance) }
        PartySide.TO_GIVE -> found.filter { Money.isNegative(it.balance) }
        PartySide.SETTLED -> found.filter { Money.isZero(it.balance) }
    }
    val byType = byType(bySide, type)
    return if (q.isNotEmpty()) {
        // Each name ranked once, then sorted (the comparator ranked both names at every comparison).
        byType.map { Triple(it, NameSearch.rank(it.name, q), it.name.lowercase()) }
            .sortedWith(compareBy<Triple<PartyWithBalance, Int, String>> { it.second }.thenBy { it.third })
            .map { it.first }
    } else when (sort) {
        PartySort.NAME_AZ -> com.innovation313.roshankhata.data.NameOrder.sortedBy(byType) { it.name }
        PartySort.NAME_ZA -> com.innovation313.roshankhata.data.NameOrder.sortedByDescending(byType) { it.name }
        PartySort.OWES_MOST -> byType.sortedByDescending { it.balance }
        PartySort.I_OWE_MOST -> byType.sortedBy { it.balance }
        PartySort.RECENT -> byType.sortedByDescending { it.lastActivity }
    }
}

/** Customers, suppliers or both: the tab alone, for the totals above the list. */
fun byType(parties: List<PartyWithBalance>, type: PartyType): List<PartyWithBalance> = when (type) {
    PartyType.ALL -> parties
    PartyType.CUSTOMERS -> parties.filter { it.isCustomer }
    PartyType.SUPPLIERS -> parties.filter { !it.isCustomer }
}

/** A pill that is filled when chosen: the phone's tabs and filter chips (Fluent's, 1.10.0). */
@Composable
fun FilterPill(text: String, on: Boolean, onClick: () -> Unit) = FluentPill(text, on, onClick = onClick)
