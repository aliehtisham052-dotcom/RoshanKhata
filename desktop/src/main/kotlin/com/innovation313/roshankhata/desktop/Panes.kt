package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.innovation313.roshankhata.screens.RoshanPalette
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The rail's groups: the phone's Home sections in the phone's order (Every day,
 * Business, Tools), then what only the computer has, folded away under More.
 */
enum class Group(val word: String) {
    HOME(W.nav_home),
    DAILY(W.home_sec_daily),
    BUSINESS(W.home_sec_business),
    TOOLS(W.home_section_tools),
    MORE(W.more_options)
}

/** The parts of the book the computer has, in the phone's Home order (MainActivity's three grids). */
/** [icon] and [tile]: the phone's Home tile picture and its background colour for the same place. */
enum class Section(val word: String, val icon: String, val tile: String, val group: Group) {
    HOME(W.nav_home, "ic_storefront", "brand_green", Group.HOME),
    KHATA(W.nav_khata, "ic_tile_khata", "tile_khata_bg", Group.DAILY),
    CASHBOOK(W.nav_cashbook, "ic_tile_cashbook", "tile_cashbook_bg", Group.DAILY),
    CHEQUES(W.nav_cheques, "ic_tile_cheques", "tile_cheques_bg", Group.DAILY),
    BILLS(W.supplier_bills, "ic_tile_bills", "tile_bills_bg", Group.DAILY),
    PLANS(W.nav_plans, "ic_tile_plans", "tile_plans_bg", Group.DAILY),
    EXPIRY(W.tile_expiry, "ic_tile_expiring", "tile_stock_bg", Group.DAILY),
    PRODUCTS(W.tile_products, "ic_tile_products", "tile_bills_bg", Group.BUSINESS),
    INVOICES(W.nav_invoice, "ic_tile_invoice", "tile_invoice_bg", Group.BUSINESS),
    RATES(W.rate_list_title, "ic_tile_price_list", "tile_calc_bg", Group.BUSINESS),
    BUY_RATES(W.sr_title, "ic_tile_supplier_rates", "tile_zakat_bg", Group.BUSINESS),
    INSIGHTS(W.insights_title, "ic_tile_insights", "tile_insights_bg", Group.BUSINESS),
    FOLLOWUP(W.followup_title, "ic_tile_followup", "tile_followup_bg", Group.BUSINESS),
    CASHFLOW(W.cf_title, "ic_tile_cashflow", "tile_insights_bg", Group.BUSINESS),
    SEASONS(W.season_title, "ic_tile_season", "tile_plans_bg", Group.BUSINESS),
    CALC(W.calculator, "ic_tile_calc", "tile_calc_bg", Group.TOOLS),
    CARD(W.biz_card, "ic_tile_card", "tile_card_bg", Group.TOOLS),
    ZAKAT(W.tile_zakat, "ic_tile_zakat", "tile_zakat_bg", Group.TOOLS),
    POSTER(W.poster_title, "ic_tile_poster", "tile_plans_bg", Group.TOOLS),
    STAFF(W.staff_title, "ic_tile_card", "tile_card_bg", Group.MORE),
    SCHEMES(W.schemes_title, "ic_tile_products", "tile_plans_bg", Group.MORE),
    STOCK(W.inspector_title, "ic_tile_expiring", "tile_stock_bg", Group.MORE),
    REPORT(W.ledger_report_title, "ic_tile_backup", "tile_backup_bg", Group.MORE),
    REGISTER(W.register_title, "ic_tile_price_list", "tile_invoice_bg", Group.MORE),
    DUPLICATES(W.duplicate_customers, "ic_tile_bin", "tile_bin_bg", Group.MORE),
    DAILY(W.snapshots_title, "ic_tile_backup", "tile_backup_bg", Group.MORE),
    HELP(W.help_support, "ic_tile_insights", "tile_calc_bg", Group.MORE),
    SHOP(W.business_profile, "ic_tile_settings", "tile_settings_bg", Group.MORE)
}

/** True when the app is drawn dark: the owner's Theme choice, not only the computer's. */
@Composable
fun isDark(): Boolean = LocalRoshanColors.current.pageBg == RoshanPalette.dark.pageBg

/**
 * The computer's few colours beyond the shared palette (the mockup of 20 Oct):
 * card edges, the open row's fill, the Home balance card. Light and dark, as
 * the phone's values and values-night.
 */
object Look {
    val line: Color @Composable get() = if (isDark()) Color(0xFF343B36) else Color(0xFFE5E3DC)
    /** The open section and the chosen row (brand_green_soft, day and night). */
    val chosen: Color @Composable get() = if (isDark()) Color(0xFF1F3528) else Color(0xFFE7F2EA)
    val rail: Color @Composable get() = if (isDark()) Color(0xFF171A18) else Color(0xFFFBFAF7)
    /** The Net balance card: brand_green_dark with the gold label, as on the phone. */
    val hero = Color(0xFF134228)
    val heroLabel = Color(0xFFE9C46A)
    val gold = Color(0xFFD4A02A)
}

/** The phone's tile picture on its own tint, [size] across. */
@Composable
fun TileBadge(s: Section, size: Int = 34, glyph: Int = 22) {
    val c = LocalRoshanColors.current
    Box(
        Modifier.size(size.dp).background(TileIcons.color(s.tile, isDark()) ?: c.pageBg, RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center
    ) {
        tileIcon(s.icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(glyph.dp)) }
    }
}

/**
 * The rail on the start side, grouped as the phone's Home: Every day, Business,
 * Tools; the computer's own pages under More, folded until opened. [badges]: a
 * count beside a section (Follow-up's late payers).
 */
@Composable
fun SectionRail(current: Section, onPick: (Section) -> Unit, modifier: Modifier = Modifier, badges: Map<Section, Int> = emptyMap()) {
    val c = LocalRoshanColors.current
    var moreOpen by remember { mutableStateOf(false) }
    // Scrolls: twenty-eight sections outgrow a 768-row laptop at 125% display scale.
    Column(
        modifier.width(240.dp).fillMaxHeight().background(Look.rail).verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Group.entries.forEach { g ->
            val trade = LocalTrade.current
            val inGroup = Section.entries.filter { it.group == g && trade.shows(it) }
            when (g) {
                Group.HOME -> {}
                Group.MORE -> {
                    val open = moreOpen || current.group == Group.MORE
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Look.line)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                            .clickable { moreOpen = !open }.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(word(g.word), Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(if (open) "\u25BE" else "${inGroup.size}  \u25B8", color = c.textMuted, fontSize = 13.sp)
                    }
                    if (!open) return@forEach
                }
                else -> Text(
                    word(g.word),
                    Modifier.padding(start = 10.dp, top = 16.dp, bottom = 6.dp),
                    color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp
                )
            }
            inGroup.forEach { s -> RailRow(s, s == current, badges[s] ?: 0) { onPick(s) } }
        }
    }
}

@Composable
private fun RailRow(s: Section, on: Boolean, badge: Int, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Look.chosen else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (s == Section.HOME) {
            // Home is the shop itself: the phone's storefront on brand green.
            Box(Modifier.size(32.dp).background(c.brandGreen, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                tileIcon(s.icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            }
        } else {
            TileBadge(s, 32, 20)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            word(s.word),
            Modifier.weight(1f),
            color = if (on) c.brandGreenText else c.ink,
            fontSize = 14.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2
        )
        if (badge > 0) {
            Text(
                badge.toString(),
                Modifier.background(c.gaveFill, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

/** A total in a rounded card: a label, and the figure in its colour. */
@Composable
fun TotalCard(label: String, amount: String, color: Color, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier.background(c.surface, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text(label, color = c.textMuted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(amount, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** A filled button in [fill] with white bold text. */
@Composable
fun FillButton(text: String, fill: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

/** Two choices side by side, the chosen one filled — the phone's toggle. */
@Composable
fun TwoWay(first: String, second: String, firstOn: Boolean, onPick: (Boolean) -> Unit) {
    val c = LocalRoshanColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(first to true, second to false).forEach { (label, isFirst) ->
            if (isFirst == firstOn) {
                FillButton(label, c.brandGreen) { onPick(isFirst) }
            } else {
                TextButton({ onPick(isFirst) }) { Text(label, color = c.brandGreenText) }
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** A page's title and the line under it. */
@Composable
fun PaneTitle(title: String, subtitle: String?) {
    val c = LocalRoshanColors.current
    Column {
        Text(title, color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = c.textMuted, fontSize = 13.sp) }
    }
}

/**
 * A day picked on Material's calendar. The picker speaks UTC midnights; the
 * book keeps the start of that day on this computer's clock, so "today" in
 * Pakistan stays today. [initial] is any moment on the day to start from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPicker(initial: Long?, onPick: (startOfDay: Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                val utc = state.selectedDateMillis
                if (utc == null) onDismiss()
                else onPick(Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli())
            }) { Text(word(W.ok)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(word(W.cancel)) } }
    ) { DatePicker(state) }
}
