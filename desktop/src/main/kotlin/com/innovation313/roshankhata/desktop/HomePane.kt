package com.innovation313.roshankhata.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.EntryWithParty
import com.innovation313.roshankhata.data.KhataDao
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/** A Home tile that is not a page: the phone's Backup and Bin tiles open a window here. */
enum class HomeAction(val word: String, val icon: String, val tile: String) {
    BACKUP(W.tile_backup, "ic_tile_backup", "tile_backup_bg"),
    BIN(W.tile_bin, "ic_tile_bin", "tile_bin_bg")
}

/** What the Home's figures and table are read from, beyond the party list the window already holds. */
class HomeFigures(val cashIn: Double, val cashOut: Double, val today: List<EntryWithParty>)

/** The book's figures for Home: the cashbook's two sums and today's entries, newest first. */
suspend fun homeFigures(dao: KhataDao): HomeFigures = withContext(Dispatchers.IO) {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    HomeFigures(
        cashIn = runCatching { dao.cashIncomeOnce() }.getOrDefault(0.0),
        cashOut = runCatching { dao.cashExpenseOnce() }.getOrDefault(0.0),
        today = runCatching { dao.entriesInRange(start, start + 24L * 60 * 60 * 1000 - 1) }.getOrDefault(emptyList()).sortedByDescending { it.timestamp }
    )
}

/**
 * Home (1.10.0, the Figma mockup of 20 Oct, the colourful one): the day as the
 * page's title, three figure cards — what the shop will get, will give, and
 * the cash in hand — then today's entries as a table beside what needs a look
 * today and four quick actions. Every figure comes from the same book and the
 * same rules as the phone's MainActivity; the eye hides every figure at once.
 */
@Composable
fun HomePane(
    parties: List<PartyWithBalance>,
    late: Int,
    alerts: List<LedgerAlerts.Alert>,
    extra: List<ExtraAlert>,
    /** The cashbook's sums and today's entries (homeFigures); the proof test hands in made-up ones. */
    figuresOf: suspend () -> HomeFigures,
    reload: Int,
    shopName: String?,
    onOpen: (Section) -> Unit,
    onAction: (HomeAction) -> Unit,
    onCustomer: (Long) -> Unit,
    onAddParty: () -> Unit,
    onGot: () -> Unit
) {
    val c = LocalRoshanColors.current
    val get = parties.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
    val give = parties.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }
    val owing = parties.count { Money.isPositive(it.balance) }
    val owed = parties.count { Money.isNegative(it.balance) }
    var hidden by remember { mutableStateOf(false) }
    // The phone's own mask (BalancePrivacy.MASK): no figure, not even its length.
    val masked = "• • • • • •"
    fun shown(v: Double) = if (hidden) masked else Format.money(v)
    var figures by remember { mutableStateOf<HomeFigures?>(null) }
    LaunchedEffect(reload) { figures = figuresOf() }
    // Home rises into place once as it opens (Motion.PAGE ms, a few dp): a welcome, not a show.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.PAGE + 100)) }
    // The day as the title. The weekday's name comes from the JDK's locale, which has none of the
    // app's Urdu/Sindhi month tables: in a right-to-left language the day and month alone.
    val dayName = remember {
        val rtl = com.innovation313.roshankhata.ui.BidiPort.isRtlLocale(java.util.Locale.getDefault())
        DateWords.formatter(if (rtl) "d MMMM" else "EEEE, d MMMM").format(java.util.Date())
    }
    Column(
        Modifier.fillMaxSize().background(Look.layerAlt)
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 12.dp.toPx() }
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PageHeader(dayName, shopName ?: word(W.app_name)) {
            IconAction(if (hidden) "ic_eye_closed" else "ic_eye_open", word(W.net_balance), { hidden = !hidden })
        }

        // The three figures: will get (red), will give (blue), cash in hand (green).
        val cash = (figures?.cashIn ?: 0.0) - (figures?.cashOut ?: 0.0)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard(
                word(W.you_will_get), shown(get),
                if (late > 0) plural(WP.home_paying_late, late, late) else plural(WP.customer_count, owing, owing),
                c.owedToMe, Look.getTint, "ic_arrow_got", Modifier.weight(1f)
            ) { onOpen(if (late > 0) Section.FOLLOWUP else Section.KHATA) }
            StatCard(
                word(W.you_will_give), shown(give), plural(WP.supplier_count, owed, owed),
                Look.give, Look.giveTint, "ic_arrow_gave", Modifier.weight(1f)
            ) { onOpen(Section.KHATA) }
            StatCard(
                word(W.home_cash_in_hand), shown(cash),
                word(W.cash_in) + " " + shown(figures?.cashIn ?: 0.0) + " · " + word(W.cash_out) + " " + shown(figures?.cashOut ?: 0.0),
                Look.accent, Look.accentTint, "ic_tile_cashbook", Modifier.weight(1f)
            ) { onOpen(Section.CASHBOOK) }
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Beside each other when the window has the room, otherwise one under the other.
            val wide = maxWidth >= 760.dp
            val sideWidth = if (maxWidth >= 1000.dp) 330.dp else 290.dp
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TodayCard(figures?.today ?: emptyList(), parties, hidden, onCustomer, onOpen, Modifier.weight(1f))
                    Column(Modifier.width(sideWidth), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        NeedsALook(alerts, extra, late, onOpen)
                        QuickActions(onAddParty, onGot, onOpen, onAction)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TodayCard(figures?.today ?: emptyList(), parties, hidden, onCustomer, onOpen, Modifier.fillMaxWidth())
                    NeedsALook(alerts, extra, late, onOpen)
                    QuickActions(onAddParty, onGot, onOpen, onAction)
                }
            }
        }
    }
}

/**
 * Today's entries as a table (time, customer, note, got, gave); on a day with
 * none yet, the customers dealt with most recently, so the card is never bare.
 */
@Composable
private fun TodayCard(today: List<EntryWithParty>, parties: List<PartyWithBalance>, hidden: Boolean, onCustomer: (Long) -> Unit, onOpen: (Section) -> Unit, modifier: Modifier) {
    val c = LocalRoshanColors.current
    val masked = "• • •"
    Column(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(word(W.home_today), Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(plural(WP.entries_today, today.size, today.size), color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false)
        }
        Rule()
        if (today.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
          // On a laptop (the card about 640 dp) the note gives way so a customer's name is whole;
          // the note then sits under the name instead of in its own column.
          val roomy = maxWidth >= 720.dp
          Column {
            val time = remember { DateWords.formatter("h:mm a") }
            TableHead(
                if (roomy) listOf(word(W.home_col_time) to 78, word(W.customer) to 0, word(W.invoice_feature_note) to 0, word(W.you_got) to 92, word(W.you_gave) to 92)
                else listOf(word(W.home_col_time) to 70, word(W.customer) to 0, word(W.you_got) to 88, word(W.you_gave) to 88),
                endAligned = if (roomy) setOf(3, 4) else setOf(2, 3)
            )
            today.take(8).forEachIndexed { i, e ->
                Row(
                    Modifier.fillMaxWidth().press { onCustomer(e.partyId) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(time.format(java.util.Date(e.timestamp)), Modifier.width(if (roomy) 78.dp else 70.dp), color = c.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Avatar(e.partyName, 28, i)
                        Column {
                            Text(Format.name(e.partyName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!roomy) e.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    if (roomy) Text(e.note?.takeIf { it.isNotBlank() } ?: "—", Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val amount = if (hidden) masked else Format.money(e.amount)
                    Text(if (e.isGiven) "—" else amount, Modifier.width(if (roomy) 92.dp else 88.dp), color = if (e.isGiven) c.textMuted else Look.accent, fontSize = 14.sp, fontWeight = if (e.isGiven) FontWeight.Normal else FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                    Text(if (e.isGiven) amount else "—", Modifier.width(if (roomy) 92.dp else 88.dp), color = if (e.isGiven) c.owedToMe else c.textMuted, fontSize = 14.sp, fontWeight = if (e.isGiven) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
                }
                Rule()
            }
          }
        } else {
            // No entry yet today: the customers dealt with last, each opening its page.
            Text(word(W.sort_recent_activity), Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val recent = parties.filter { it.lastActivity > 0 }.sortedByDescending { it.lastActivity }.take(7)
            if (recent.isEmpty()) Text(word(W.no_parties_yet), Modifier.padding(16.dp), color = c.textMuted, fontSize = 14.sp)
            recent.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().press { onCustomer(p.id) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Avatar(p.name, 30, i)
                    Column(Modifier.weight(1f)) {
                        Text(Format.name(p.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Format.dateOnly(p.lastActivity), color = c.textMuted, fontSize = 12.sp)
                    }
                    Text(if (hidden) masked else Format.customerBalance(p.balance), color = balanceColour(p.balance), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                Rule()
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            FluentButton(word(W.nav_cashbook), { onOpen(Section.CASHBOOK) }, kind = ButtonKind.SUBTLE, icon = "ic_chevron_right")
        }
    }
}

/** The bell's rows on Home (the phone's AlertsActivity): late payers, the computer's own rows, then the ledger's. */
@Composable
private fun NeedsALook(alerts: List<LedgerAlerts.Alert>, extra: List<ExtraAlert>, late: Int, onOpen: (Section) -> Unit) {
    val c = LocalRoshanColors.current
    val count = alerts.size + extra.size + (if (late > 0) 1 else 0)
    Column(Modifier.fillMaxWidth().clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(word(W.na_title), Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (count > 0) Text(count.toString(), color = c.owedToMe, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Rule()
        if (count == 0) Text(word(W.na_none), Modifier.padding(16.dp), color = c.textMuted, fontSize = 13.sp)
        if (late > 0) {
            LookRow(plural(WP.home_paying_late, late, late), null, c.owedToMe, Look.getTint, "ic_tile_followup") { onOpen(Section.FOLLOWUP) }
        }
        extra.forEach { x -> LookRow(x.title, x.sub, Look.accent, Look.accentTint, x.section.icon, x.onClick) }
        alerts.forEach { a ->
            val s = alertSection(a.kind)
            LookRow(a.title, a.sub, Look.give, Look.giveTint, s.icon) { onOpen(s) }
        }
    }
}

@Composable
private fun LookRow(title: String, sub: String?, color: Color, tint: Color, icon: String, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.fillMaxWidth().press(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(32.dp).background(tint, Look.ctl), contentAlignment = Alignment.Center) { MonoTile(icon, color, 16) }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrEmpty()) Text(sub, color = c.inkSoft, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Glyph("ic_chevron_right", c.textMuted, 14)
    }
    Rule()
}

/** Four tinted tiles: Add party, You got, Invoice, Poster — the counter's most common moves. */
@Composable
private fun QuickActions(onAddParty: () -> Unit, onGot: () -> Unit, onOpen: (Section) -> Unit, onAction: (HomeAction) -> Unit) {
    val c = LocalRoshanColors.current
    Column(Modifier.fillMaxWidth().clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(W.home_quick_actions), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        val tiles = listOf(
            Quick(word(W.add_party), "ic_tile_khata", Look.accent, Look.accentTint, onAddParty),
            Quick(word(W.you_got), "ic_arrow_got", c.owedToMe, Look.getTint, onGot),
            Quick(word(W.pos_title), "ic_tile_invoice", Look.give, Look.giveTint) { onOpen(Section.COUNTER) },
            Quick(word(W.poster_title), "ic_tile_poster", Look.warn, Look.warnTint) { onOpen(Section.POSTER) },
            Quick(word(W.tile_backup), "ic_tile_backup", Look.accent, Look.accentTint) { onAction(HomeAction.BACKUP) },
            Quick(word(W.calculator), "ic_tile_calc", c.inkSoft, Look.subtle) { onOpen(Section.CALC) }
        )
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { q ->
                    Row(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(Look.ctl).background(q.tint).press(Look.ctl, onClick = q.onClick).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MonoTile(q.icon, q.color, 18)
                        Text(q.word, color = q.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private class Quick(val word: String, val icon: String, val color: Color, val tint: Color, val onClick: () -> Unit)

/** Where an alert opens: the page that holds it, as the phone's AlertsActivity. */
fun alertSection(kind: LedgerAlerts.Kind): Section = when (kind) {
    LedgerAlerts.Kind.CHEQUE -> Section.CHEQUES
    LedgerAlerts.Kind.PLAN -> Section.PLANS
    LedgerAlerts.Kind.BILL -> Section.BILLS
    LedgerAlerts.Kind.EXPIRING -> Section.EXPIRY
    LedgerAlerts.Kind.CASHFLOW -> Section.CASHFLOW
}

/** A bell row the computer adds itself (the backup's age, a newer version): its tile, words and what a click does. */
class ExtraAlert(val section: Section, val title: String, val sub: String, val onClick: () -> Unit)

/** The bell's list (the phone's AlertsActivity): each alert on its page's tile, a click opens the page. */
@Composable
fun AlertsList(alerts: List<LedgerAlerts.Alert>, extra: List<ExtraAlert> = emptyList(), onOpen: (Section) -> Unit) {
    val c = LocalRoshanColors.current
    Column(Modifier.fillMaxSize().background(Look.layer).verticalScroll(rememberScrollState()).padding(8.dp)) {
        if (alerts.isEmpty() && extra.isEmpty()) Text(word(W.na_none), Modifier.padding(16.dp), color = c.textMuted, fontSize = 14.sp)
        extra.forEach { x -> LookRow(x.title, x.sub, Look.accent, Look.accentTint, x.section.icon, x.onClick) }
        alerts.forEach { a ->
            val s = alertSection(a.kind)
            LookRow(a.title, listOfNotNull(a.sub.takeIf { it.isNotEmpty() }, a.tag).joinToString(" · "), Look.give, Look.giveTint, s.icon) { onOpen(s) }
        }
    }
}
