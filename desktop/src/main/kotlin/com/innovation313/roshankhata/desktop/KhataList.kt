package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.balanceColour
import com.innovation313.roshankhata.screens.balanceSideWord
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word

/*
 * The Khata list as the owner's mockup draws it (Marhala 4, 25 Oct 2026, Design canvas "Roshan Khata Windows
 * — Naya Design", Khata): the three tabs with their counts, the two totals that are also the side filter (the
 * phone's), the search with Filter and Order beside it, the line that says the order and who needs a reminder
 * with the page's four tools, and the parties as cards. Every piece the 1.10.0 page had is still here; only
 * where it sits changed.
 */

/** All · Customers · Suppliers, each with its count (25 Oct 2026, the audit: every count said "customers"). */
@Composable
fun PartyTabs(type: PartyType, counts: Map<PartyType, Int>, onType: (PartyType) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Look.tabTrack).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PartyType.entries.forEach { t ->
            val on = t == type
            Row(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(11.dp))
                    .background(eased(if (on) Look.pickedFill else Color.Transparent))
                    .press(RoundedCornerShape(11.dp)) { onType(t) }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
            ) {
                Text(word(t.word), color = if (on) Look.onPicked else c.ink, fontSize = 12.5.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(6.dp))
                Text((counts[t] ?: 0).toString(), color = if (on) Look.onPicked.copy(alpha = 0.78f) else c.inkSoft, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * What the open tab will get and give, as two cards that also choose the side (the phone's: a tap shows only
 * those who owe, a second tap all again). The chosen one wears its colour's edge.
 */
@Composable
fun KhataTotals(get: Double, give: Double, hidden: Boolean, side: PartySide, onSide: (PartySide) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(PartySide.TO_GET, get, W.i_have_to_get) to Triple("ic_arrow_got", c.owedToMe, Look.getTint),
            Triple(PartySide.TO_GIVE, give, W.i_have_to_give) to Triple("ic_arrow_gave", c.iOwe, Look.chosen)
        ).forEach { (what, look) ->
            val (s, amount, label) = what
            val (icon, ink, tint) = look
            val on = side == s
            Row(
                Modifier.weight(1f).clip(Look.card).background(Look.layer)
                    .border(if (on) 1.5.dp else 1.dp, if (on) ink else Look.strokeCard, Look.card)
                    .press(Look.card) { onSide(if (on) PartySide.ALL else s) }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(34.dp).background(tint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Glyph(icon, ink, 18) }
                Column(Modifier.weight(1f)) {
                    // A long figure is set smaller rather than cut (Rs 1,234,567 on the laptop's 340 dp list).
                    val figure = if (hidden) TOTALS_MASK else Format.money(amount)
                    Text(figure, color = ink, fontSize = if (figure.length > 10) 13.sp else 16.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    Text(word(label), color = c.textMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Beside the search: Filter (which side, and by last dealing) and Order (the phone's five orders), each a menu;
 * a button wears its colour's edge while its choice is not the plain one.
 */
@Composable
fun KhataFindTools(
    side: PartySide, onSide: (PartySide) -> Unit,
    whenFilter: PartyWhen, onWhen: (PartyWhen) -> Unit,
    sort: PartySort, onSort: (PartySort) -> Unit
) {
    Box {
        var open by remember { mutableStateOf(false) }
        TintAction("ic_filter_sliders", word(W.filter_title), Look.violet, Look.violetTint, size = 38,
            active = side != PartySide.ALL || whenFilter != PartyWhen.ALL) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Look.layer) {
            MenuHeading(word(W.filter_title))
            PartySide.entries.forEach { s -> DropdownMenuItem(text = { MenuChoice(word(s.word), s == side) }, onClick = { open = false; onSide(s) }) }
            MenuHeading(word(W.filter_by_date))
            PartyWhen.entries.forEach { w -> DropdownMenuItem(text = { MenuChoice(word(w.word), w == whenFilter) }, onClick = { open = false; onWhen(w) }) }
        }
    }
    Box {
        var open by remember { mutableStateOf(false) }
        TintAction("ic_sort_arrows", word(W.sort_by), Look.slate, Look.slateTint, size = 38, active = sort != PartySort.RECENT) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Look.layer) {
            MenuHeading(word(W.sort_by))
            PartySort.entries.forEach { s -> DropdownMenuItem(text = { MenuChoice(word(s.word), s == sort) }, onClick = { open = false; onSort(s) }) }
        }
    }
}

/**
 * The line under the search: the order the list is in, who needs a reminder and how many are settled (the
 * phone's summary line, KhataActivity.renderPartySummary), then the page's four tools.
 */
@Composable
fun KhataInfoRow(
    shown: List<PartyWithBalance>,
    sort: PartySort,
    canStatements: Boolean,
    canExport: Boolean,
    onStatements: () -> Unit,
    onExcel: () -> Unit,
    onImport: () -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val overdue = shown.count { Money.isPositive(it.balance) && it.lastActivity in 1 until cutoff }
    val settled = shown.count { Money.isZero(it.balance) }
    val order = word(sort.word)
    val line = (if (overdue > 0) plural(WP.summary_with_overdue, overdue, order, overdue) else order) + when {
        settled == 0 -> ""
        settled >= shown.size -> "  ·  " + word(W.all_settled)
        else -> "  ·  " + word(W.settled_count, settled)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // The whole line on hover when the list is too narrow for it.
        Box(Modifier.weight(1f)) {
            HoverTip(line) { Text(line, color = c.textMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        TintAction("ic_pdf_doc", word(W.statement_pdf) + " · " + word(W.filter_type_all), Look.clay, Look.clayTint, enabled = canStatements, onClick = onStatements)
        TintAction("ic_download", word(W.export_excel), Look.leaf, Look.leafTint, enabled = canExport, onClick = onExcel)
        TintAction("ic_people", word(W.import_csv), Look.warn, Look.warnTint, onClick = onImport)
        TintAction("ic_check_circle", word(W.khata_choose_several), Look.rose, Look.roseTint, onClick = onChoose)
    }
}

/**
 * Choosing several to delete (the phone's long-press): how many, Select all, Delete and Cancel — Delete in red,
 * Cancel quiet (25 Oct 2026, the audit: "Delete" sat among the filter pills and a green "Cancel" read as a
 * chosen filter).
 */
@Composable
fun KhataSelectBar(count: Int, onAll: () -> Unit, onDelete: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    Row(
        modifier.fillMaxWidth().clip(Look.card).background(Look.chosen).padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(word(W.selected_count, count), Modifier.weight(1f), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        FluentButton(word(W.select_all), onAll, kind = ButtonKind.SUBTLE)
        FluentButton(word(W.delete), onDelete, kind = ButtonKind.DANGER, enabled = count > 0)
        FluentButton(word(W.cancel), onCancel, kind = ButtonKind.SUBTLE)
    }
}

/**
 * One party as a card: the face or the initials in a colour that is the party's own (its id, never its place in
 * the list — 25 Oct 2026, the audit: the colours moved as the list was sorted), a supplier's van, the name, the
 * phone or "Supplier" with the last dealing, and the balance in the words of its side.
 */
@Composable
fun PartyCard(
    p: PartyWithBalance,
    face: ImageBitmap?,
    open: Boolean,
    selecting: Boolean,
    ticked: Boolean,
    onClick: () -> Unit,
    onTick: (Boolean) -> Unit
) {
    val c = LocalRoshanColors.current
    val lit = open || ticked
    Row(
        Modifier.fillMaxWidth().clip(Look.card)
            .background(eased(if (lit) Look.chosen else Look.layer))
            .border(1.5.dp, eased(if (lit) Look.accent else Look.strokeCard), Look.card)
            .press(Look.card, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selecting) androidx.compose.material3.Checkbox(checked = ticked, onCheckedChange = onTick)
        PartyFace(p, face, 36, lit)
        Column(Modifier.weight(1f)) {
            Text(Format.name(p.name), color = c.ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (p.isCustomer) p.phone?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) } else word(W.supplier),
                    p.lastActivity.takeIf { it > 0 }?.let { dayWords(it) }
                ).joinToString(" · "),
                color = c.textMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.money(kotlin.math.abs(p.balance)), color = balanceColour(p.balance), fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false)
            Text(word(sideOf(p.balance)), color = if (Money.isZero(p.balance)) c.textMuted else balanceColour(p.balance), fontSize = 11.sp,
                maxLines = 1, softWrap = false)
        }
    }
}

/** The shop's way of saying a party's side: I have to get / I have to give / Settled (the mockup's words). */
fun sideOf(balance: Double): String = when {
    Money.isPositive(balance) -> W.i_have_to_get
    Money.isNegative(balance) -> W.i_have_to_give
    else -> balanceSideWord(balance)
}

/** A party's face: their photo when the computer has it, a supplier's van, else their initials in their own colour. */
@Composable
fun PartyFace(p: PartyWithBalance, face: ImageBitmap?, size: Int, lit: Boolean = false) {
    val shape = RoundedCornerShape((size * 0.28f).dp)
    when {
        face != null -> androidx.compose.foundation.Image(
            face, contentDescription = null, modifier = Modifier.size(size.dp).clip(shape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        !p.isCustomer -> Box(Modifier.size(size.dp).background(Look.warnTint, shape), contentAlignment = Alignment.Center) {
            Glyph("ic_truck", Look.warn, (size * 0.5f).toInt())
        }
        else -> {
            val (fill, ink) = Look.avatar(p.id)
            Box(Modifier.size(size.dp).background(if (lit) Look.layer else fill, shape), contentAlignment = Alignment.Center) {
                Text(Format.initials(p.name), color = if (lit) Look.accent else ink, fontSize = (size * 0.35f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** A day said shortly: Today, Yesterday, a weekday this week, else the date (the mockup's "Thu, 22 Oct"). */
@Composable
fun dayWords(at: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    return when {
        day == today -> word(W.range_today)
        day == today.minusDays(1) -> word(W.range_yesterday)
        // Asked each time: DateWords keeps its own formatters, and follows the shop's calendar as it changes.
        else -> com.innovation313.roshankhata.data.DateWords.formatter("EEE, d MMM").format(java.util.Date(at))
    }
}

/**
 * An icon-only tool on a tint of its own colour (the mockup's tool buttons), its words as the hover tip.
 * [active]: its choice is not the plain one — it wears its colour's edge.
 */
@Composable
fun TintAction(
    icon: String, tip: String, ink: Color, tint: Color, size: Int = 32, enabled: Boolean = true, active: Boolean = false,
    onClick: () -> Unit
) {
    HoverTip(tip) {
        Box(
            Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp))
                .background(if (enabled) tint else Look.subtle)
                .border(if (active) 1.5.dp else 0.dp, if (active) ink else Color.Transparent, RoundedCornerShape((size * 0.3f).dp))
                .press(RoundedCornerShape((size * 0.3f).dp), enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Glyph(icon, if (enabled) ink else LocalRoshanColors.current.textMuted, (size * 0.5f).toInt()) }
    }
}

/** A quiet heading inside a menu. */
@Composable
private fun MenuHeading(text: String) {
    Text(text.uppercase(), Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
        color = Look.groupText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
}

/** A menu line, ticked when it is the current choice. */
@Composable
private fun MenuChoice(text: String, on: Boolean) {
    val c = LocalRoshanColors.current
    Text((if (on) "✓  " else "     ") + text, color = if (on) c.brandGreenText else c.ink, fontSize = 14.sp,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
}
