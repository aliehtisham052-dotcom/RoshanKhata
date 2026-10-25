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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.innovation313.roshankhata.data.DayClose
import com.innovation313.roshankhata.data.DayCloseMath
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/** What Home asks "whose?" for before it goes on: an entry either way, or a customer's statement. */
enum class HomePick(val word: String) { GAVE(W.you_gave), GOT(W.you_got), STATEMENT(W.statement_pdf) }

/** What the Home's figures and lists are read from, beyond the party list the window already holds. */
class HomeFigures(
    val cashIn: Double,
    val cashOut: Double,
    val today: List<EntryWithParty>,
    /**
     * What should be in the drawer, by Close the day's own rule: the last count plus
     * the cash that came in, less the cash that went out, since (cashbook rows in cash
     * or unmarked, khata entries marked Cash). Null when it could not be read.
     */
    val drawer: Double? = null,
    /** The last count of the drawer, or null when it was never counted. */
    val lastCount: DayClose? = null,
    /** The book's last entries, newest first (Recent dealings), today's or older. */
    val recent: List<EntryWithParty> = today,
    /** The parties whose earliest open promise (a plan's next instalment or the day they named) falls today. */
    val promisedToday: Set<Long> = emptySet()
)

/**
 * What should be in the drawer now (25 Oct 2026, the audit): Home's "Cash in
 * hand" was the cashbook's all-time net in every method, bank and online
 * included and khata cash left out, so it never matched the drawer.
 */
suspend fun drawerByTheBooks(dao: KhataDao, now: Long = System.currentTimeMillis()): Pair<Double, DayClose?> {
    val last = dao.lastDayClose()
    val from = last?.closedAt ?: 0L
    val expected = DayCloseMath.expected(
        last?.counted ?: 0.0,
        dao.cashbookCashBetween(true, from, now) + dao.khataCashBetween(false, from, now),
        dao.cashbookCashBetween(false, from, now) + dao.khataCashBetween(true, from, now)
    )
    return expected to last
}

/** How many of the book's last entries Home lists. */
const val HOME_RECENT = 8

/** The book's figures for Home: the cashbook's two sums, the drawer by the books, today's and the last entries, today's promises. */
suspend fun homeFigures(dao: KhataDao): HomeFigures = withContext(Dispatchers.IO) {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = start + 24L * 60 * 60 * 1000 - 1
    val drawer = runCatching { drawerByTheBooks(dao) }.getOrNull()
    HomeFigures(
        cashIn = runCatching { dao.cashIncomeOnce() }.getOrDefault(0.0),
        cashOut = runCatching { dao.cashExpenseOnce() }.getOrDefault(0.0),
        today = runCatching { dao.entriesInRange(start, end) }.getOrDefault(emptyList()).sortedByDescending { it.timestamp },
        drawer = drawer?.first,
        lastCount = drawer?.second,
        recent = runCatching { dao.recentEntries(HOME_RECENT) }.getOrDefault(emptyList()),
        promisedToday = runCatching { dao.observePromises().first() }.getOrDefault(emptyList())
            .filter { it.due in start..end }.map { it.partyId }.toSet()
    )
}

/** The phone's BalancePrivacy.MASK: hidden money shows as these dots, never its length. */
internal const val TOTALS_MASK = "• • • • • •"

/**
 * Home (Marhala 2, the owner's mockup of 25 Oct 2026): the day as the page's
 * title; the phone's deep green Net balance card with the eye, what the shop
 * has to get and to give, and three figures beside them — what should be in the
 * drawer, today's entries, and who promised to pay today; then the book's last
 * dealings, what needs a look, and six quick actions. Every figure comes from
 * the same book and the same rules as the phone's MainActivity; the eye hides
 * every figure at once.
 */
@Composable
fun HomePane(
    parties: List<PartyWithBalance>,
    late: Int,
    alerts: List<LedgerAlerts.Alert>,
    extra: List<ExtraAlert>,
    /** The cashbook's sums, today's and the last entries (homeFigures); the proof test hands in made-up ones. */
    figuresOf: suspend () -> HomeFigures,
    reload: Int,
    onOpen: (Section) -> Unit,
    onCustomer: (Long) -> Unit,
    /** I gave · F2 and I got · F3: whose, asked first, then the entry. */
    onGave: () -> Unit,
    onGot: () -> Unit,
    /** A customer's statement, asked whose first. */
    onStatement: () -> Unit,
    /** Supplier bills with a new bill's form open. */
    onNewBill: () -> Unit,
    /**
     * The eye (25 Oct 2026, the audit): the window keeps it, hidden until the owner opens it and
     * remembered after (AppPrefs.balancesHidden, the phone's BalancePrivacy). The proof test shows figures.
     */
    hidden: Boolean = false,
    onHidden: (Boolean) -> Unit = {}
) {
    val get = parties.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
    val give = parties.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }
    var figures by remember { mutableStateOf<HomeFigures?>(null) }
    LaunchedEffect(reload) { figures = figuresOf() }
    // Home rises into place once as it opens (Motion.PAGE ms, a few dp): a welcome, not a show.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.PAGE + 100)) }
    // The day as the title, weekday and month in the app's language (21 Oct 2026: DateWords has
    // every language's weekdays now; it read "Wednesday" on a Hindi screen before).
    // Read again after More → Language and past midnight (R55, 23 Oct 2026: kept from the first draw, it stayed
    // in the old language until Home was left).
    val appWords = com.innovation313.roshankhata.words.LocalWords.current
    val today = java.time.LocalDate.now()
    val dayName = remember(appWords, today) { DateWords.formatter("EEEE, d MMMM").format(java.util.Date()) }
    Column(
        Modifier.fillMaxSize().background(Look.layerAlt)
            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 12.dp.toPx() }
            .verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // The shop's name is on the rail's card now: the line under the day says what the page is.
        PageHeader(dayName, word(W.home_shop_today))

        HeroCard(get, give, late, parties, figures, hidden, onHidden, onOpen)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Three side by side when the window has the room (the mockup's laptop shows them so), else the
            // dealings above and the other two beside each other, else one under the other.
            val w = maxWidth
            val recent = figures?.recent ?: emptyList()
            when {
                w >= 760.dp -> Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RecentCard(recent, hidden, onCustomer, onOpen, Modifier.weight(1.2f).fillMaxHeight())
                    NeedsALook(alerts, extra, late, onOpen, Modifier.weight(1f).fillMaxHeight())
                    QuickActions(onGave, onGot, onStatement, onNewBill, onOpen, Modifier.weight(0.95f).fillMaxHeight())
                }
                w >= 560.dp -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RecentCard(recent, hidden, onCustomer, onOpen, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        NeedsALook(alerts, extra, late, onOpen, Modifier.weight(1f).fillMaxHeight())
                        QuickActions(onGave, onGot, onStatement, onNewBill, onOpen, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RecentCard(recent, hidden, onCustomer, onOpen, Modifier.fillMaxWidth())
                    NeedsALook(alerts, extra, late, onOpen, Modifier.fillMaxWidth())
                    QuickActions(onGave, onGot, onStatement, onNewBill, onOpen, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** The light words on the deep green card: the phone's white, and its softer second line. */
private val OnHero = Color.White
private val OnHeroSoft = Color.White.copy(alpha = 0.74f)
/** "I have to get" and "I have to give" on the green (LookContrastTest measures them on the pill). */
private val HeroGet = LookPalette.HERO_GET
private val HeroGive = LookPalette.HERO_GIVE

/**
 * The phone's Net balance card made wide (the mockup): NET BALANCE in gold, the
 * net with its sign and the eye, the two halves as pills under it; on the end
 * side three small figures — the drawer by the books, today's entries, and who
 * promised to pay today. Each opens the page that holds it.
 */
@Composable
private fun HeroCard(
    get: Double, give: Double, late: Int, parties: List<PartyWithBalance>, figures: HomeFigures?,
    hidden: Boolean, onHidden: (Boolean) -> Unit, onOpen: (Section) -> Unit
) {
    val net = get - give
    fun shown(v: Double) = if (hidden) TOTALS_MASK else Format.money(v)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(Look.card).background(Look.hero)
            .border(1.dp, Color.White.copy(alpha = 0.10f), Look.card)
    ) {
        val wide = maxWidth >= 720.dp
        val balance: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word(W.net_balance).uppercase(), color = Look.heroLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The net carries its sign (21 Oct 2026, the phone's rule): a shop that owes more than it is owed.
                    Text(
                        if (hidden) TOTALS_MASK else if (Money.isNegative(net)) Format.signedIfNegative(net) else Format.money(net),
                        color = OnHero, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                    )
                    HoverTip(word(W.net_balance)) {
                        Box(
                            Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color.White.copy(alpha = 0.12f))
                                .press(androidx.compose.foundation.shape.CircleShape, wash = Color.White.copy(alpha = 0.12f)) { onHidden(!hidden) },
                            contentAlignment = Alignment.Center
                        ) { Glyph(if (hidden) "ic_eye_closed" else "ic_eye_open", Look.heroLabel, 18) }
                    }
                }
                // Side by side where they fit, else one above the other (a laptop with long figures or Urdu words).
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val pills: @Composable () -> Unit = {
                        HeroPill("ic_arrow_got", word(W.i_have_to_get), shown(get), HeroGet) { onOpen(if (late > 0) Section.FOLLOWUP else Section.KHATA) }
                        HeroPill("ic_arrow_gave", word(W.i_have_to_give), shown(give), HeroGive) { onOpen(Section.KHATA) }
                    }
                    if (maxWidth >= 420.dp) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pills() }
                    else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { pills() }
                }
            }
        }
        val tiles: @Composable (Modifier, Boolean) -> Unit = { m, stretch ->
            val f = figures
            // A shortfall carries its minus (21 Oct 2026). The drawer by Close the day's rule (25 Oct 2026): cash only,
            // since the last count; "…" while reading, "—" if it failed.
            val drawer = f?.drawer
            val last = f?.lastCount
            val gave = f?.today?.count { it.isGiven } ?: 0
            val got = f?.today?.count { !it.isGiven } ?: 0
            val promised = parties.filter { it.id in (f?.promisedToday ?: emptySet()) && Money.isPositive(it.balance) }
            Row(m.height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val tm = if (stretch) Modifier.weight(1f).fillMaxHeight() else Modifier.width(132.dp).fillMaxHeight()
                HeroFigure(
                    word(W.z_expected),
                    when { hidden -> TOTALS_MASK; f == null -> "…"; drawer == null -> "—"; else -> Format.signedIfNegative(drawer) },
                    when {
                        f == null -> ""
                        last == null -> word(W.dayclose_never)
                        else -> word(W.dayclose_last, Format.dateOnly(last.closedAt), if (hidden) TOTALS_MASK else Format.signedTotal(last.difference))
                    },
                    tm
                ) { onOpen(Section.CASHBOOK) }
                HeroFigure(
                    word(W.home_todays_entries), if (f == null) "…" else (gave + got).toString(),
                    if (f == null) "" else word(W.home_gave_got_count, gave, got), tm
                ) { onOpen(Section.REPORT) }
                HeroFigure(
                    word(W.home_promised_today), if (f == null) "…" else shown(promised.sumOf { it.balance }),
                    if (f == null) "" else plural(WP.customer_count, promised.size, promised.size), tm
                ) { onOpen(Section.FOLLOWUP) }
            }
        }
        if (wide) Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            balance(Modifier.weight(1f))
            tiles(Modifier, false)
        } else Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            balance(Modifier.fillMaxWidth())
            tiles(Modifier.fillMaxWidth(), true)
        }
    }
}

/** One half of the net on the green card: its arrow, its words, its figure. */
@Composable
private fun HeroPill(icon: String, words: String, figure: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(Look.ctl).background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color.White.copy(alpha = 0.14f), Look.ctl)
            .press(Look.ctl, wash = Color.White.copy(alpha = 0.10f), onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Glyph(icon, color, 14)
        Text(words, color = OnHeroSoft, fontSize = 12.sp, maxLines = 1, softWrap = false)
        Text(figure, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** A small figure on the green card: what it is, the figure, a line of context. */
@Composable
private fun HeroFigure(label: String, figure: String, under: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(Look.ctl).background(Color.White.copy(alpha = 0.07f)).border(1.dp, Color.White.copy(alpha = 0.14f), Look.ctl)
            .press(Look.ctl, wash = Color.White.copy(alpha = 0.08f), onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, color = OnHeroSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(figure, color = OnHero, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        if (under.isNotEmpty()) Text(under, color = OnHeroSoft, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The book's last dealings (the mockup's left card): each with its party's
 * initials, name, when and the note, the amount in its side's colour with You
 * gave / You got under it; today's count beside the title and the Khata Report
 * a click away. A click opens the party.
 */
@Composable
private fun RecentCard(recent: List<EntryWithParty>, hidden: Boolean, onCustomer: (Long) -> Unit, onOpen: (Section) -> Unit, modifier: Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The title takes the row (today's count is on the green card above).
            Text(word(W.home_recent), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(Look.ctl).press(Look.ctl) { onOpen(Section.REPORT) }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(word(W.ledger_report_title), color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Glyph("ic_chevron_right", Look.accent, 12)
            }
        }
        Rule()
        if (recent.isEmpty()) Text(word(W.no_parties_yet), Modifier.padding(16.dp), color = c.textMuted, fontSize = 14.sp)
        recent.forEach { e ->
            Row(
                Modifier.fillMaxWidth().press { onCustomer(e.partyId) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The party's own colour, the same wherever it is drawn (it went by the row's place before).
                Avatar(e.partyName, 32, e.partyId.toInt(), rounded = true)
                Column(Modifier.weight(1f)) {
                    Text(Format.name(e.partyName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(whenWords(e.timestamp), e.note?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val colour = if (e.isGiven) c.owedToMe else Look.accent
                    Text(if (hidden) TOTALS_MASK else Format.money(e.amount), color = colour, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Text(word(if (e.isGiven) W.you_gave else W.you_got), color = colour, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
            Rule()
        }
    }
}

/** The bell's rows on Home (the phone's AlertsActivity): late payers, the computer's own rows, then the ledger's. */
@Composable
private fun NeedsALook(alerts: List<LedgerAlerts.Alert>, extra: List<ExtraAlert>, late: Int, onOpen: (Section) -> Unit, modifier: Modifier) {
    val c = LocalRoshanColors.current
    val count = alerts.size + extra.size + (if (late > 0) 1 else 0)
    Column(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word(W.na_title), Modifier.weight(1f, fill = false), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (count > 0) Text(
                count.toString(),
                Modifier.clip(RoundedCornerShape(10.dp)).background(c.gaveFill).padding(horizontal = 7.dp, vertical = 1.dp),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
        Rule()
        if (count == 0) Text(word(W.na_none), Modifier.padding(16.dp), color = c.textMuted, fontSize = 13.sp)
        if (late > 0) {
            LookRow(plural(WP.home_paying_late, late, late), null, Section.FOLLOWUP) { onOpen(Section.FOLLOWUP) }
        }
        extra.forEach { x -> LookRow(x.title, x.sub, x.section, x.onClick) }
        alerts.forEach { a ->
            val s = alertSection(a.kind)
            LookRow(a.title, a.sub, s) { onOpen(s) }
        }
    }
}

@Composable
private fun LookRow(title: String, sub: String?, section: Section, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.fillMaxWidth().press(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The page's own picture in its own colours, as the rail wears it (one colour made the phone's
        // two-tone tiles a blob: a red disc for Follow-up, a blue block for a cheque).
        TileBadge(section, 32, 18)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!sub.isNullOrEmpty()) Text(sub, color = c.inkSoft, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Glyph("ic_chevron_right", c.textMuted, 14)
    }
    Rule()
}

/**
 * Six tinted tiles (the mockup's Quick actions), each a picture on a white square
 * over its tint and its words under it: You gave · F2 and You got · F3 (whose,
 * asked first), the counter, a new supplier bill, the cashbook, and a customer's
 * statement.
 */
@Composable
private fun QuickActions(onGave: () -> Unit, onGot: () -> Unit, onStatement: () -> Unit, onNewBill: () -> Unit, onOpen: (Section) -> Unit, modifier: Modifier) {
    val c = LocalRoshanColors.current
    val dark = isDark()
    fun tile(name: String) = TileIcons.color(name, dark) ?: c.pageBg
    val tiles = listOf(
        // Money out of the shop in the ledger's red, money in in its green, as everywhere else.
        Quick(word(W.you_gave) + " · F2", "ic_arrow_gave", c.owedToMe, Look.getTint, onClick = onGave),
        Quick(word(W.you_got) + " · F3", "ic_arrow_got", Look.accent, Look.accentTint, onClick = onGot),
        Quick(word(W.pos_title), "ic_qr_scan", Look.give, Look.giveTint) { onOpen(Section.COUNTER) },
        Quick(word(W.add_bill), "ic_tile_bills", Look.warn, Look.warnTint, twoTone = true, onClick = onNewBill),
        Quick(word(W.cash_in) + " / " + word(W.cash_out), "ic_tile_cashbook", TileIcons.color("fi_green_b", dark) ?: Look.accent, tile("tile_cashbook_bg"), twoTone = true) { onOpen(Section.CASHBOOK) },
        Quick(word(W.statement_pdf), "ic_pdf_doc", TileIcons.color("fi_orange_b", dark) ?: c.ink, tile("tile_stock_bg"), onClick = onStatement)
    )
    Column(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(W.home_quick_actions), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { q ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().heightIn(min = 84.dp).clip(Look.card).background(q.tint)
                            .press(Look.card, onClick = q.onClick).padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        Box(Modifier.size(36.dp).background(Look.layer, Look.ctl), contentAlignment = Alignment.Center) {
                            if (q.twoTone) tileIcon(q.icon)?.let { androidx.compose.foundation.Image(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
                            else Glyph(q.icon, q.color, 19)
                        }
                        Text(q.word, color = q.color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private class Quick(val word: String, val icon: String, val color: Color, val tint: Color, val twoTone: Boolean = false, val onClick: () -> Unit)

/** Where an alert opens: the page that holds it, as the phone's AlertsActivity. */
fun alertSection(kind: LedgerAlerts.Kind): Section = when (kind) {
    LedgerAlerts.Kind.CHEQUE -> Section.CHEQUES
    LedgerAlerts.Kind.PLAN -> Section.PLANS
    LedgerAlerts.Kind.BILL -> Section.BILLS
    LedgerAlerts.Kind.EXPIRING -> Section.EXPIRY
    LedgerAlerts.Kind.STOCK -> Section.PRODUCTS
    LedgerAlerts.Kind.DRAWER -> Section.CASHBOOK
    LedgerAlerts.Kind.CASHFLOW -> Section.CASHFLOW
    LedgerAlerts.Kind.SYNC -> Section.DAILY
}

/** A bell row the computer adds itself (the backup's age, a newer version): its tile, words and what a click does. */
class ExtraAlert(val section: Section, val title: String, val sub: String, val onClick: () -> Unit)

/** The bell's list (the phone's AlertsActivity): each alert on its page's tile, a click opens the page. */
@Composable
fun AlertsList(alerts: List<LedgerAlerts.Alert>, extra: List<ExtraAlert> = emptyList(), onOpen: (Section) -> Unit) {
    val c = LocalRoshanColors.current
    Column(Modifier.fillMaxSize().background(Look.layer).verticalScroll(rememberScrollState()).padding(8.dp)) {
        if (alerts.isEmpty() && extra.isEmpty()) Text(word(W.na_none), Modifier.padding(16.dp), color = c.textMuted, fontSize = 14.sp)
        extra.forEach { x -> LookRow(x.title, x.sub, x.section, x.onClick) }
        alerts.forEach { a ->
            val s = alertSection(a.kind)
            LookRow(a.title, listOfNotNull(a.sub.takeIf { it.isNotEmpty() }, a.tag).joinToString(" · "), s) { onOpen(s) }
        }
    }
}
