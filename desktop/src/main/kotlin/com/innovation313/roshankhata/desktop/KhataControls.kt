package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/** The Khata list's three tabs, as the phone's typeTabs. */
enum class PartyType(val word: String) {
    ALL(W.filter_type_all), CUSTOMERS(W.filter_type_customers), SUPPLIERS(W.filter_type_suppliers)
}

/** Which side of the book (the phone's SideFilter): settled is a choice of its own. */
enum class PartySide(val word: String) {
    ALL(W.range_all), TO_GET(W.you_will_get), TO_GIVE(W.you_will_give), SETTLED(W.settled)
}

/**
 * The phone's date filter (DateRangeFilter): who was dealt with today,
 * yesterday, this week or this month, by their last dealing. The week starts
 * on the computer's own first day, as the phone's on its calendar.
 */
enum class PartyWhen(val word: String) {
    ALL(W.range_all), TODAY(W.range_today), YESTERDAY(W.range_yesterday), THIS_WEEK(W.range_this_week), THIS_MONTH(W.range_this_month);

    /** The window as from..to, or null for everything. */
    fun window(now: Long = System.currentTimeMillis()): LongRange? {
        fun start(c: java.util.Calendar): Long = c.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = 24L * 60 * 60 * 1000
        val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
        return when (this) {
            ALL -> null
            TODAY -> start(c).let { it until it + day }
            YESTERDAY -> start(c.apply { add(java.util.Calendar.DAY_OF_MONTH, -1) }).let { it until it + day }
            // The week starts where the shop's country starts it (21 Oct 2026): Saturday in Iran, Sunday in the Gulf.
            THIS_WEEK -> start(c.apply { firstDayOfWeek = com.innovation313.roshankhata.data.ShopCountry.current.weekStart; set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek) })..now
            THIS_MONTH -> start(c.apply { set(java.util.Calendar.DAY_OF_MONTH, 1) })..now
        }
    }
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
        PartySort.NAME_AZ -> byType.sortedBy { it.name.lowercase() }
        PartySort.NAME_ZA -> byType.sortedByDescending { it.name.lowercase() }
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

/**
 * Above the Khata list: the type tabs, the side pills, the sort menu and the
 * Select switch for deleting several at once (the phone's long-press).
 */
@Composable
fun KhataControls(
    type: PartyType,
    onType: (PartyType) -> Unit,
    side: PartySide,
    onSide: (PartySide) -> Unit,
    sort: PartySort,
    onSort: (PartySort) -> Unit,
    whenFilter: PartyWhen,
    onWhen: (PartyWhen) -> Unit,
    selecting: Boolean,
    onSelecting: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Three short lines, never a wrapped word and nothing hidden off the edge (the owner's
    // laptop, 20 Oct; the list column there is about 370 dp): the tabs and delete, the side
    // pills, then date and order. A line still too narrow scrolls sideways rather than
    // breaking a label into letters.
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            PartyType.entries.forEach { t -> FilterPill(word(t.word), t == type) { onType(t) } }
            Spacer(Modifier.width(10.dp))
            FilterPill(word(if (selecting) W.cancel else W.delete), selecting) { onSelecting(!selecting) }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            // A second click on the chosen side clears it, as the phone's chips do.
            PartySide.entries.filter { it != PartySide.ALL }.forEach { s ->
                FilterPill(word(s.word), s == side) { onSide(if (s == side) PartySide.ALL else s) }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                var open by remember { mutableStateOf(false) }
                MenuChip((if (whenFilter == PartyWhen.ALL) word(W.filter_by_date) else word(whenFilter.word)) + " \u25BE", whenFilter != PartyWhen.ALL) { open = true }
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    PartyWhen.entries.forEach { w ->
                        DropdownMenuItem(text = { Choice(word(w.word), w == whenFilter) }, onClick = { open = false; onWhen(w) })
                    }
                }
            }
            Box {
                var open by remember { mutableStateOf(false) }
                MenuChip(word(W.sort_by) + " \u25BE", sort != PartySort.RECENT) { open = true }
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    PartySort.entries.forEach { s ->
                        DropdownMenuItem(text = { Choice(word(s.word), s == sort) }, onClick = { open = false; onSort(s) })
                    }
                }
            }
        }
    }
}

/** A menu opener in the pills' row: a quiet pill, tinted when a choice is active. */
@Composable
private fun MenuChip(text: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Text(
        text,
        Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(eased(if (active) Look.accentTint else Look.subtle))
            .press(androidx.compose.foundation.shape.CircleShape, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (active) c.brandGreenText else c.ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
    )
}

/** A menu line, ticked when it is the current choice. */
@Composable
private fun Choice(text: String, on: Boolean) {
    val c = LocalRoshanColors.current
    Text(
        (if (on) "\u2713  " else "     ") + text,
        color = if (on) c.brandGreenText else c.ink, fontSize = 14.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
    )
}
