package com.innovation313.roshankhata.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.LedgerAlerts
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.balanceColour
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.delay

/** A Home tile that is not a page: the phone's Backup and Bin tiles open a window here. */
enum class HomeAction(val word: String, val icon: String, val tile: String) {
    BACKUP(W.tile_backup, "ic_tile_backup", "tile_backup_bg"),
    BIN(W.tile_bin, "ic_tile_bin", "tile_bin_bg")
}

/**
 * The phone's Home on the computer (20 Oct redesign): the Net balance card
 * with its eye, what the shop will get and give, who is paying late, today's
 * chips, the four slides, and the three grids in the phone's order. On the
 * end side, the customers dealt with most recently. Every figure comes from
 * the same book and the same rules as the phone's MainActivity.
 */
@Composable
fun HomePane(
    parties: List<PartyWithBalance>,
    late: Int,
    alerts: List<LedgerAlerts.Alert>,
    onOpen: (Section) -> Unit,
    onAction: (HomeAction) -> Unit,
    onCustomer: (Long) -> Unit
) {
    val c = LocalRoshanColors.current
    val get = parties.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
    val give = parties.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }
    var hidden by remember { mutableStateOf(false) }
    // The phone's own mask (BalancePrivacy.MASK): no figure, not even its length.
    val masked = "• • • • • •"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(Format.dateOnly(System.currentTimeMillis()), color = c.inkSoft, fontSize = 13.sp)

        // The balance row: Net balance on the phone's dark-green card, then get and give.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.weight(2f).heightIn(min = 108.dp).background(Look.hero, RoundedCornerShape(18.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(word(W.net_balance).uppercase(), Modifier.weight(1f), color = Look.heroLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable { hidden = !hidden },
                        contentAlignment = Alignment.Center
                    ) { Glyph(if (hidden) "ic_eye_closed" else "ic_eye_open", Color.White, 20) }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (hidden) masked else Format.money(get - give), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            Figure(word(W.you_will_get), if (hidden) masked else Format.money(get), c.owedToMe,
                plural(WP.customer_count, parties.count { Money.isPositive(it.balance) }, parties.count { Money.isPositive(it.balance) }),
                Modifier.weight(1f)) { onOpen(Section.KHATA) }
            Figure(word(W.you_will_give), if (hidden) masked else Format.money(give), c.iOwe, null, Modifier.weight(1f)) { onOpen(Section.KHATA) }
        }

        // Who is paying later than their habit (the phone's tvHomeLate): a count, never an amount.
        if (late > 0) {
            Text(
                plural(WP.home_paying_late, late, late),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.gaveFill.copy(alpha = 0.12f))
                    .clickable { onOpen(Section.FOLLOWUP) }.padding(horizontal = 16.dp, vertical = 12.dp),
                color = c.owedToMe, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
        }

        // Today: the phone's todayStrip (MainActivity.paintToday), one chip per kind of alert.
        if (alerts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(word(W.home_today), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                alerts.groupBy { it.kind }.forEach { (kind, list) ->
                    val s = alertSection(kind)
                    TodayChip(word(W.today_chip, word(alertChipWord(kind)), list.size), s) { onOpen(s) }
                }
            }
        }

        Slides(onOpen)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Heading(word(W.home_sec_daily))
                val trade = LocalTrade.current
                TileGrid(Section.entries.filter { it.group == Group.DAILY && trade.shows(it) }.map { s -> Tile(s.word, s.icon, s.tile) { onOpen(s) } }, minWidth = 120, big = true)
                Spacer(Modifier.height(2.dp))
                Heading(word(W.home_sec_business))
                TileGrid(Section.entries.filter { it.group == Group.BUSINESS && trade.shows(it) }.map { s -> Tile(s.word, s.icon, s.tile) { onOpen(s) } }, minWidth = 110, big = false)
                Spacer(Modifier.height(2.dp))
                Heading(word(W.home_section_tools))
                TileGrid(
                    Section.entries.filter { it.group == Group.TOOLS && trade.shows(it) }.map { s -> Tile(s.word, s.icon, s.tile) { onOpen(s) } } +
                        HomeAction.entries.map { a -> Tile(a.word, a.icon, a.tile) { onAction(a) } },
                    minWidth = 110, big = false
                )
            }
            Recent(parties, hidden, onCustomer, Modifier.weight(2f))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = LocalRoshanColors.current.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

/** A total on a white card: its label, the figure in its colour, a line under it. */
@Composable
private fun Figure(label: String, amount: String, color: Color, under: String?, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, Look.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(amount, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        under?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun TodayChip(text: String, s: Section, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, Look.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TileBadge(s, 26, 16)
        Spacer(Modifier.width(8.dp))
        Text(text, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** The phone's four Home slides (HomeSlides): a banner each, turning by itself, the dots to pick one. */
/** The phone's HomeSlides.TURN_MS: a new banner every three seconds. */
private const val TURN_MS = 3000L

private class Slide(val section: Section, val title: String, val sub: String, val ground: Color)

@Composable
private fun Slides(onOpen: (Section) -> Unit) {
    // Grounds deep enough for white text at 4.5:1 or better.
    val slides = listOf(
        Slide(Section.CASHFLOW, W.cf_title, W.home_slide_cf, Color(0xFF2E6DB4)),
        Slide(Section.RATES, W.rate_list_title, W.home_slide_rates, Color(0xFF1F7A4D)),
        Slide(Section.BUY_RATES, W.sr_title, W.home_slide_cheaper, Color(0xFF8A5F12)),
        Slide(Section.POSTER, W.poster_title, W.home_slide_poster, Color(0xFF5B4A9E))
    )
    var at by remember { mutableIntStateOf(0) }
    // Keyed on [at]: a click on a dot starts the wait again, as the phone's pager does.
    LaunchedEffect(at) {
        delay(TURN_MS)
        at = (at + 1) % slides.size
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Crossfade(at, animationSpec = tween(450)) { shown ->
            val s = slides[shown]
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(s.ground)
                    .clickable { onOpen(s.section) }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(42.dp).background(Color.White, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    tileIcon(s.section.icon)?.let { androidx.compose.foundation.Image(it, contentDescription = null, modifier = Modifier.size(24.dp)) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(word(s.sub), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    word(s.title) + "  →",
                    Modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
                    color = s.ground, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            slides.indices.forEach { i ->
                Box(
                    Modifier.size(width = if (i == at) 22.dp else 8.dp, height = 8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (i == at) LocalRoshanColors.current.brandGreen else Look.line)
                        .clickable { at = i }
                )
            }
        }
    }
}

private class Tile(val word: String, val icon: String, val tint: String, val onClick: () -> Unit)

/**
 * Tiles in rows as many as fit [minWidth] each, every tile one width and the
 * last row padded so it lines up (the phone's own rule for its grids).
 */
@Composable
private fun TileGrid(tiles: List<Tile>, minWidth: Int, big: Boolean) {
    val c = LocalRoshanColors.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = maxOf(1, ((maxWidth.value + 10) / (minWidth + 10)).toInt())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t ->
                        Column(
                            Modifier.weight(1f).heightIn(min = if (big) 100.dp else 88.dp).clip(RoundedCornerShape(16.dp))
                                .background(c.surface).border(1.dp, Look.line, RoundedCornerShape(18.dp))
                                .clickable(onClick = t.onClick).padding(horizontal = 6.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically)
                        ) {
                            val disc = if (big) 46 else 38
                            Box(
                                Modifier.size(disc.dp).background(TileIcons.color(t.tint, isDark()) ?: c.pageBg, RoundedCornerShape((disc / 4).dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                tileIcon(t.icon)?.let { androidx.compose.foundation.Image(it, contentDescription = null, modifier = Modifier.size((disc / 2).dp)) }
                            }
                            Text(word(t.word), color = c.ink, fontSize = if (big) 13.sp else 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The customers dealt with last (the Khata list's own default order), each opening its page. */
@Composable
private fun Recent(parties: List<PartyWithBalance>, hidden: Boolean, onCustomer: (Long) -> Unit, modifier: Modifier) {
    val c = LocalRoshanColors.current
    val recent = parties.filter { it.lastActivity > 0 }.sortedByDescending { it.lastActivity }.take(8)
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp, Look.line, RoundedCornerShape(20.dp))) {
        Text(word(W.sort_recent_activity), Modifier.padding(horizontal = 18.dp, vertical = 16.dp), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = Look.line)
        if (recent.isEmpty()) Text(word(W.no_parties_yet), Modifier.padding(18.dp), color = c.textMuted, fontSize = 14.sp)
        recent.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onCustomer(p.id) }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Look.chosen), contentAlignment = Alignment.Center) {
                    Text(Format.initials(p.name), color = c.brandGreenText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Format.name(p.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Format.dateOnly(p.lastActivity), color = c.textMuted, fontSize = 12.sp)
                }
                Text(
                    if (hidden) "• • •" else Format.customerBalance(p.balance),
                    color = balanceColour(p.balance), fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
            }
            HorizontalDivider(color = Look.line)
        }
    }
}

/** Where an alert opens: the page that holds it, as the phone's AlertsActivity. */
fun alertSection(kind: LedgerAlerts.Kind): Section = when (kind) {
    LedgerAlerts.Kind.CHEQUE -> Section.CHEQUES
    LedgerAlerts.Kind.PLAN -> Section.PLANS
    LedgerAlerts.Kind.BILL -> Section.BILLS
    LedgerAlerts.Kind.EXPIRING -> Section.EXPIRY
    LedgerAlerts.Kind.CASHFLOW -> Section.CASHFLOW
}

/** The chip's word for each kind, the phone's (paintToday). */
private fun alertChipWord(kind: LedgerAlerts.Kind): String = when (kind) {
    LedgerAlerts.Kind.CHEQUE -> W.nav_cheques
    LedgerAlerts.Kind.PLAN -> W.na_tag_late
    LedgerAlerts.Kind.BILL -> W.tile_bills
    LedgerAlerts.Kind.EXPIRING -> W.tile_expiry
    LedgerAlerts.Kind.CASHFLOW -> W.today_gap
}

/** The bell's list (the phone's AlertsActivity): each alert on its page's tile, a click opens the page. */
@Composable
fun AlertsList(alerts: List<LedgerAlerts.Alert>, onOpen: (Section) -> Unit) {
    val c = LocalRoshanColors.current
    Column(Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (alerts.isEmpty()) Text(word(W.na_none), Modifier.padding(16.dp), color = c.textMuted, fontSize = 15.sp)
        alerts.forEach { a ->
            val s = alertSection(a.kind)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(s) }.padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TileBadge(s, 40, 24)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    if (a.sub.isNotEmpty()) Text(a.sub, color = c.textMuted, fontSize = 13.sp)
                }
                a.tag?.let {
                    Text(
                        it,
                        Modifier.background(c.gaveFill.copy(alpha = 0.14f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        color = c.owedToMe, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider(color = Look.line)
        }
    }
}
