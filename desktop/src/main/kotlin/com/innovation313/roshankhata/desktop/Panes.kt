package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
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
    COUNTER(W.pos_title, "ic_tile_invoice", "tile_cashbook_bg", Group.DAILY),
    CASHBOOK(W.nav_cashbook, "ic_tile_cashbook", "tile_cashbook_bg", Group.DAILY),
    CHEQUES(W.nav_cheques, "ic_tile_cheques", "tile_cheques_bg", Group.DAILY),
    BILLS(W.supplier_bills, "ic_tile_bills", "tile_bills_bg", Group.DAILY),
    PLANS(W.nav_plans, "ic_tile_plans", "tile_plans_bg", Group.DAILY),
    EXPIRY(W.tile_expiry, "ic_tile_expiring", "tile_stock_bg", Group.DAILY),
    PRODUCTS(W.tile_products, "ic_tile_products", "tile_bills_bg", Group.BUSINESS),
    INVENTORY(W.inventory_title, "ic_tile_expiring", "tile_stock_bg", Group.BUSINESS),
    ORDERS(W.po_title, "ic_tile_bills", "tile_bills_bg", Group.BUSINESS),
    INVOICES(W.nav_invoice, "ic_tile_invoice", "tile_invoice_bg", Group.BUSINESS),
    RATES(W.rate_list_title, "ic_tile_price_list", "tile_calc_bg", Group.BUSINESS),
    BUY_RATES(W.sr_title, "ic_tile_supplier_rates", "tile_zakat_bg", Group.BUSINESS),
    INSIGHTS(W.insights_title, "ic_tile_insights", "tile_insights_bg", Group.BUSINESS),
    SALES(W.sales_report_title, "ic_tile_cashflow", "tile_insights_bg", Group.BUSINESS),
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
    AUDIT(W.audit_title, "ic_tile_card", "tile_card_bg", Group.MORE),
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
    /** Dividers and card edges: quiet on purpose, they only group (no contrast rule for decoration). */
    val line: Color @Composable get() = if (isDark()) Color(0xFF3A3A3A) else Color(0xFFE6E6E6)
    /**
     * The edge of something to click (a pill, an outlined button): at least 3:1 on the
     * page, the card and the rail (WCAG 1.4.11; 1.28:1 before 1.8.3). LookContrastTest holds it.
     * Fluent's own control edge is lighter; this one keeps the rule.
     */
    val edge: Color @Composable get() = if (isDark()) LookPalette.EDGE_DARK else LookPalette.EDGE_LIGHT
    /** A chosen pill's fill and its words: brand green by day; by night the light green, 8.9:1 on the card. */
    val pickedFill: Color @Composable get() = if (isDark()) LookPalette.PICKED_DARK else LookPalette.PICKED_LIGHT
    val onPicked: Color @Composable get() = if (isDark()) LookPalette.ON_PICKED_DARK else Color.White
    /** What a hovered control is washed with (Motion.kt's [press]). */
    val hover: Color @Composable get() = if (isDark()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    /** The open section and the chosen row (brand_green_soft, day and night). */
    val chosen: Color @Composable get() = if (isDark()) Color(0xFF1E3A2C) else Color(0xFFE6F4EC)
    val rail: Color @Composable get() = if (isDark()) LookPalette.RAIL_DARK else LookPalette.RAIL_LIGHT
    /** The Net balance card: brand_green_dark with the gold label, as on the phone. */
    val hero = Color(0xFF134228)
    val heroLabel = Color(0xFFE9C46A)
    val gold = Color(0xFFD4A02A)

    // ---- Fluent surfaces (1.10.0, the Figma mockups of 20 Oct: Windows 11's layering) ----
    /** The page behind everything (Mica): a shade darker than the cards. */
    val base: Color @Composable get() = if (isDark()) Color(0xFF202020) else Color(0xFFF3F3F3)
    /** A card or dialog (layer). */
    val layer: Color @Composable get() = if (isDark()) Color(0xFF2B2B2B) else Color(0xFFFFFFFF)
    /** The content area behind the cards (layer-alt): between base and layer. */
    val layerAlt: Color @Composable get() = if (isDark()) Color(0xFF262626) else Color(0xFFFAFAFA)
    /** A quiet fill: an unchosen pill, a table's header row, the selected rail item. */
    val subtle: Color @Composable get() = if (isDark()) Color(0xFF353535) else Color(0xFFEDEDED)
    /** A text box's fill. */
    val control: Color @Composable get() = if (isDark()) Color(0xFF2E2E2E) else Color(0xFFFFFFFF)
    /** A card's 1 px edge (decoration, under the contrast rule's floor on purpose). */
    val strokeCard: Color @Composable get() = if (isDark()) Color(0xFF1A1A1A) else Color(0xFFE3E3E3)
    /** The accent: brand green by day, the light green by night (RoshanPalette's brandGreen / brandGreenText). */
    val accent: Color @Composable get() = if (isDark()) Color(0xFF5FD9A3) else Color(0xFF1A7F50)
    val onAccent: Color @Composable get() = if (isDark()) Color(0xFF0B2A1C) else Color.White
    /** Tints behind an icon or a figure: green, red (will get), blue (will give), amber (a warning). */
    val accentTint: Color @Composable get() = if (isDark()) Color(0xFF1E3A2C) else Color(0xFFE6F4EC)
    val getTint: Color @Composable get() = if (isDark()) Color(0xFF3A2824) else Color(0xFFFCEBEE)
    val giveTint: Color @Composable get() = if (isDark()) Color(0xFF1A3238) else Color(0xFFE8F1FA)
    val warnTint: Color @Composable get() = if (isDark()) Color(0xFF3A3222) else Color(0xFFFBF3DE)
    /** The blue of "you will give" on the mockups (RoshanPalette's iOwe is a teal; the figures keep that, the tints use this). */
    val give: Color @Composable get() = if (isDark()) Color(0xFF7CC9D8) else Color(0xFF0F6CBD)
    val warn: Color @Composable get() = if (isDark()) Color(0xFFF2C66D) else Color(0xFF9A6B00)
    /** Fluent's corners: 4 for a control, 8 for a card or dialog. */
    val ctl = RoundedCornerShape(4.dp)
    val card = RoundedCornerShape(8.dp)
}

/** Look's measured colours as plain values, for LookContrastTest. */
object LookPalette {
    val EDGE_LIGHT = Color(0xFF858585)
    val EDGE_DARK = Color(0xFF8C8C8C)
    val PICKED_LIGHT = Color(0xFF1A7F50)
    val PICKED_DARK = Color(0xFF7FD1A3)
    val ON_PICKED_DARK = Color(0xFF0B2A1A)
    val RAIL_LIGHT = Color(0xFFF3F3F3)
    val RAIL_DARK = Color(0xFF202020)
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
 * The rail on the start side (1.10.0: Windows 11's NavigationView), grouped as
 * the phone's Home: Every day, Business, Tools; the computer's own pages under
 * More, folded until opened. Each row is 36 dp with the phone's tile glyph in
 * one colour and, when open, a 3 dp accent bar on its start side. [badges]: a
 * count beside a section (Follow-up's late payers, Khata's parties).
 */
@Composable
fun SectionRail(current: Section, onPick: (Section) -> Unit, modifier: Modifier = Modifier, badges: Map<Section, Int> = emptyMap()) {
    val c = LocalRoshanColors.current
    var moreOpen by remember { mutableStateOf(false) }
    // Scrolls: twenty-eight sections outgrow a 768-row laptop at 125% display scale.
    Column(
        modifier.width(224.dp).fillMaxHeight().background(Look.base).verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Group.entries.forEach { g ->
            val trade = LocalTrade.current
            val inGroup = Section.entries.filter { it.group == g && trade.shows(it) }
            when (g) {
                Group.HOME -> {}
                Group.MORE -> {
                    val open = moreOpen || current.group == Group.MORE
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = Look.line)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).clip(Look.ctl)
                            .press(Look.ctl) { moreOpen = !open }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(word(g.word), Modifier.weight(1f), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (open) "\u25BE" else "${inGroup.size}  \u25B8", color = c.textMuted, fontSize = 12.sp)
                    }
                    if (!open) return@forEach
                }
                else -> Text(
                    word(g.word).uppercase(),
                    Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                    color = c.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp
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
            .padding(vertical = 1.dp)
            .clip(Look.ctl)
            .background(eased(if (on) Look.subtle else Color.Transparent))
            .press(Look.ctl, onClick = onClick)
            .height(36.dp)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The selection bar: 3 x 16 dp of accent on the start side, as every Windows 11 app.
        Box(Modifier.width(3.dp).height(16.dp).background(eased(if (on) Look.accent else Color.Transparent), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(9.dp))
        // The phone's tile picture, small, on its own tint (a tinted silhouette made a blob: the
        // tile vectors carry their rounded ground, so one colour fills the whole square).
        if (s == Section.HOME) {
            Box(Modifier.size(22.dp).background(Look.accent, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                tileIcon(s.icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            }
        } else TileBadge(s, 22, 14)
        Spacer(Modifier.width(12.dp))
        Text(
            word(s.word),
            Modifier.weight(1f),
            color = c.ink,
            fontSize = 14.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (badge > 0) {
            Text(
                if (badge > 9999) "9999+" else badge.toString(),
                color = if (s == Section.FOLLOWUP) c.owedToMe else c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** A total in a card: a label, and the figure in its colour (Fluent's card, 1.10.0). */
@Composable
fun TotalCard(label: String, amount: String, color: Color, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card).padding(14.dp)) {
        Text(label, color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(amount, color = color, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/**
 * A filled button in [fill] with white bold words: Fluent's 32 dp control with
 * 4 dp corners (Material's 40 dp pill before 1.10.0). [fill] is kept for the
 * red You gave / green You got pair.
 */
@Composable
fun FillButton(text: String, fill: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Look.ctl,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = Color.White)
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, softWrap = false) }
}

/** Two choices side by side, the chosen one filled — the phone's toggle, as a Fluent segmented control. */
@Composable
fun TwoWay(first: String, second: String, firstOn: Boolean, onPick: (Boolean) -> Unit) {
    val c = LocalRoshanColors.current
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(Look.subtle).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(first to true, second to false).forEach { (label, isFirst) ->
            val on = isFirst == firstOn
            Text(
                label,
                Modifier.clip(Look.ctl).background(eased(if (on) Look.pickedFill else Color.Transparent))
                    .press(Look.ctl, wash = if (on) Color.Black.copy(alpha = 0.12f) else Look.hover) { onPick(isFirst) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = if (on) Look.onPicked else c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
            )
        }
    }
}

/** A page's title and the line under it (Fluent's page head, 1.10.0). */
@Composable
fun PaneTitle(title: String, subtitle: String?) {
    val c = LocalRoshanColors.current
    Column {
        Text(title, color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, color = c.inkSoft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
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
