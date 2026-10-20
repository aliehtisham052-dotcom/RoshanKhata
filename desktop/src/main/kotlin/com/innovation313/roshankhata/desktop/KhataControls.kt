package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
fun List<PartyWithBalance>.shownBy(query: String, side: PartySide, type: PartyType, sort: PartySort): List<PartyWithBalance> {
    val q = query.trim().lowercase()
    val found = if (q.isEmpty()) this else filter { NameSearch.matches(it.name, it.phone, q) }
    val bySide = when (side) {
        PartySide.ALL -> found
        PartySide.TO_GET -> found.filter { Money.isPositive(it.balance) }
        PartySide.TO_GIVE -> found.filter { Money.isNegative(it.balance) }
        PartySide.SETTLED -> found.filter { Money.isZero(it.balance) }
    }
    val byType = byType(bySide, type)
    return if (q.isNotEmpty()) {
        byType.sortedWith(compareBy<PartyWithBalance> { NameSearch.rank(it.name, q) }.thenBy { it.name.lowercase() })
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

/** A pill that is filled when chosen: the phone's tabs and filter chips. */
@Composable
fun FilterPill(text: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Text(
        text,
        Modifier.clip(RoundedCornerShape(17.dp))
            .background(if (on) c.brandGreen else Color.Transparent)
            .border(1.dp, if (on) c.brandGreen else Look.line, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (on) Color.White else c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold
    )
}

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
    selecting: Boolean,
    onSelecting: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PartyType.entries.forEach { t -> FilterPill(word(t.word), t == type) { onType(t) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // A second click on the chosen side clears it, as the phone's chips do.
            PartySide.entries.filter { it != PartySide.ALL }.forEach { s ->
                FilterPill(word(s.word), s == side) { onSide(if (s == side) PartySide.ALL else s) }
            }
            Spacer(Modifier.weight(1f))
            Box {
                var open by remember { mutableStateOf(false) }
                Text(
                    word(W.sort_by) + " ▾",
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { open = true }.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = c.brandGreenText, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                DropdownMenu(open, onDismissRequest = { open = false }) {
                    PartySort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (s == sort) "✓  " else "     ") + word(s.word),
                                    color = if (s == sort) c.brandGreenText else c.ink, fontSize = 14.sp,
                                    fontWeight = if (s == sort) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = { open = false; onSort(s) }
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            FilterPill(word(if (selecting) W.cancel else W.delete), selecting) { onSelecting(!selecting) }
        }
    }
}
