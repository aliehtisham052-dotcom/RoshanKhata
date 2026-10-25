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
import androidx.compose.foundation.layout.fillMaxSize
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
 * The rail's groups (Marhala 2, the owner's mockup of 25 Oct 2026): Home on its
 * own, Every day and Sales & stock always open, then Reports, Tools and More
 * folded ([folds]) behind one row each — their picture ([icon] on [tile], painted
 * in [ink] when it is one of the phone's one-colour glyphs), name and count.
 */
enum class Group(val word: String, val folds: Boolean = false, val icon: String? = null, val tile: String? = null, val ink: String? = null) {
    HOME(W.nav_home),
    DAILY(W.home_sec_daily),
    SALES(W.rail_sales_stock),
    REPORTS(W.rail_reports, folds = true, icon = "ic_pdf_doc", tile = "tile_insights_bg", ink = "fi_blue_b"),
    TOOLS(W.home_section_tools, folds = true, icon = "ic_tune", tile = "tile_card_bg", ink = "fi_violet_b"),
    MORE(W.more_options, folds = true, icon = "ic_tile_settings", tile = "tile_settings_bg")
}

/**
 * The parts of the book the computer has, in the rail's order (the owner's mockup of 25 Oct 2026).
 * [icon] and [tile]: the phone's picture for the place and the tint behind it — its two-tone Home tile
 * where it has one, else one of its one-colour glyphs painted in [ink] (the tile's own hue), so no two
 * places wear the same picture (25 Oct 2026, the audit: eleven shared one).
 */
enum class Section(val word: String, val icon: String, val tile: String, val group: Group, val ink: String? = null) {
    HOME(W.nav_home, "ic_menu_home", "tile_khata_bg", Group.HOME),
    KHATA(W.nav_khata, "ic_tile_khata", "tile_khata_bg", Group.DAILY),
    CASHBOOK(W.nav_cashbook, "ic_tile_cashbook", "tile_cashbook_bg", Group.DAILY),
    CHEQUES(W.nav_cheques, "ic_tile_cheques", "tile_cheques_bg", Group.DAILY),
    BILLS(W.supplier_bills, "ic_tile_bills", "tile_bills_bg", Group.DAILY),
    FOLLOWUP(W.followup_title, "ic_tile_followup", "tile_followup_bg", Group.DAILY),
    PLANS(W.nav_plans, "ic_tile_plans", "tile_plans_bg", Group.DAILY),
    COUNTER(W.pos_title, "ic_qr_scan", "tile_cashbook_bg", Group.SALES, ink = "fi_green_b"),
    PRODUCTS(W.tile_products, "ic_tile_products", "tile_bills_bg", Group.SALES),
    RATES(W.rate_list_title, "ic_tile_price_list", "tile_calc_bg", Group.SALES),
    BUY_RATES(W.sr_title, "ic_tile_supplier_rates", "tile_zakat_bg", Group.SALES),
    INVENTORY(W.inventory_title, "ic_feature_stock", "tile_stock_bg", Group.SALES, ink = "fi_orange_b"),
    EXPIRY(W.tile_expiry, "ic_tile_expiring", "tile_stock_bg", Group.SALES),
    STOCK(W.inspector_title, "ic_register_stock", "tile_backup_bg", Group.SALES, ink = "fi_teal_b"),
    ORDERS(W.po_title, "ic_truck", "tile_bills_bg", Group.SALES, ink = "fi_gold_b"),
    INVOICES(W.nav_invoice, "ic_tile_invoice", "tile_invoice_bg", Group.SALES),
    REPORT(W.ledger_report_title, "ic_list_all", "tile_khata_bg", Group.REPORTS, ink = "fi_green_b"),
    REGISTER(W.register_title, "ic_register_sales", "tile_invoice_bg", Group.REPORTS, ink = "fi_pink_b"),
    SALES(W.sales_report_title, "ic_feature_insights", "tile_insights_bg", Group.REPORTS, ink = "fi_blue_b"),
    INSIGHTS(W.insights_title, "ic_tile_insights", "tile_insights_bg", Group.REPORTS),
    AGING(W.aging_title, "ic_calendar", "tile_followup_bg", Group.REPORTS, ink = "fi_green_b"),
    CASHFLOW(W.cf_title, "ic_tile_cashflow", "tile_insights_bg", Group.REPORTS),
    CALC(W.calculator, "ic_tile_calc", "tile_calc_bg", Group.TOOLS),
    ZAKAT(W.tile_zakat, "ic_tile_zakat", "tile_zakat_bg", Group.TOOLS),
    CARD(W.biz_card, "ic_tile_card", "tile_card_bg", Group.TOOLS),
    POSTER(W.poster_title, "ic_tile_poster", "tile_plans_bg", Group.TOOLS),
    STAFF(W.staff_title, "ic_people", "tile_card_bg", Group.MORE, ink = "fi_violet_b"),
    SCHEMES(W.schemes_title, "ic_seal_stamp", "tile_zakat_bg", Group.MORE, ink = "fi_gold_b"),
    SEASONS(W.season_title, "ic_tile_season", "tile_plans_bg", Group.MORE),
    DUPLICATES(W.duplicate_customers, "ic_menu_duplicate", "tile_card_bg", Group.MORE),
    DAILY(W.snapshots_title, "ic_tile_backup", "tile_backup_bg", Group.MORE),
    AUDIT(W.audit_title, "ic_shield_check", "tile_cheques_bg", Group.MORE, ink = "fi_teal_b"),
    HELP(W.help_support, "ic_menu_help", "tile_insights_bg", Group.MORE),
    SHOP(W.business_profile, "ic_storefront", "tile_settings_bg", Group.MORE, ink = "fi_green_b")
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
    val line: Color @Composable get() = if (isDark()) Color(0xFF2E3431) else Color(0xFFECEAE4)
    /**
     * The edge of something to click (a pill, an outlined button): at least 3:1 on the
     * page, the card and the rail (WCAG 1.4.11; 1.28:1 before 1.8.3). LookContrastTest holds it.
     */
    val edge: Color @Composable get() = if (isDark()) LookPalette.EDGE_DARK else LookPalette.EDGE_LIGHT
    /** A chosen pill's fill and its words: the phone's deep green by day; by night the light green, 8.9:1 on the card. */
    val pickedFill: Color @Composable get() = if (isDark()) LookPalette.PICKED_DARK else LookPalette.PICKED_LIGHT
    val onPicked: Color @Composable get() = if (isDark()) LookPalette.ON_PICKED_DARK else Color.White
    /** What a hovered control is washed with (Motion.kt's [press]). */
    val hover: Color @Composable get() = if (isDark()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    /** The open section and the chosen row (brand_green_soft, day and night). */
    val chosen: Color @Composable get() = if (isDark()) Color(0xFF1E3A2C) else Color(0xFFE6F0EA)
    val rail: Color @Composable get() = if (isDark()) LookPalette.RAIL_DARK else LookPalette.RAIL_LIGHT
    /** The rail's edge against the page (the mockup's pale green line). */
    val railEdge: Color @Composable get() = if (isDark()) Color(0xFF26302A) else Color(0xFFD3E1D7)
    /** The rail's group names: a dark olive by day, a pale green by night (6.5:1 and 9:1 on the rail). */
    val groupText: Color @Composable get() = if (isDark()) LookPalette.GROUP_TEXT_DARK else LookPalette.GROUP_TEXT_LIGHT
    /** The Net balance card: brand_green_dark with the gold label, as on the phone. */
    val hero = Color(0xFF134228)
    val heroLabel = Color(0xFFE9C46A)
    val gold = Color(0xFFD4A02A)
    /** The phone's gold edge round its chosen and primary controls (the approved mockup of 25 Oct 2026). */
    val goldEdge = Color(0xFFF3C552)

    // ---- Surfaces: the phone's own (25 Oct 2026, the owner's chosen mockup; RoshanPalette's page and card) ----
    /** The page behind everything: the phone's warm off-white by day. */
    val base: Color @Composable get() = if (isDark()) Color(0xFF121513) else Color(0xFFF7F6F2)
    /** A card or dialog (layer). */
    val layer: Color @Composable get() = if (isDark()) Color(0xFF1E2220) else Color(0xFFFFFFFF)
    /** The content area behind the cards: the page colour, as on the phone. */
    val layerAlt: Color @Composable get() = if (isDark()) Color(0xFF171A18) else Color(0xFFF7F6F2)
    /** A quiet fill: an unchosen pill, a table's header row. */
    val subtle: Color @Composable get() = if (isDark()) Color(0xFF2A2F2C) else Color(0xFFF1EFE9)
    /** A text box's fill. */
    val control: Color @Composable get() = if (isDark()) Color(0xFF232825) else Color(0xFFFFFFFF)
    /** A card's 1 px edge (decoration, under the contrast rule's floor on purpose). */
    val strokeCard: Color @Composable get() = if (isDark()) Color(0xFF2E3431) else Color(0xFFE5E3DC)
    /** The accent: the phone's deep green by day, the light green by night. */
    val accent: Color @Composable get() = if (isDark()) Color(0xFF7FD1A3) else Color(0xFF1B5E3A)
    val onAccent: Color @Composable get() = if (isDark()) Color(0xFF0B2A1A) else Color.White
    /** Tints behind an icon or a figure: green, red (will get), blue (will give), amber (a warning). */
    val accentTint: Color @Composable get() = if (isDark()) Color(0xFF1E3A2C) else Color(0xFFE6F0EA)
    val getTint: Color @Composable get() = if (isDark()) Color(0xFF3A2824) else Color(0xFFFBEAE6)
    val giveTint: Color @Composable get() = if (isDark()) Color(0xFF1A3238) else Color(0xFFE8EDF2)
    val warnTint: Color @Composable get() = if (isDark()) Color(0xFF3A3222) else Color(0xFFF3EEDD)
    /** The blue of "you will give" on the mockups (RoshanPalette's iOwe is a teal; the figures keep that, the tints use this). */
    val give: Color @Composable get() = if (isDark()) Color(0xFF7CC9D8) else Color(0xFF0F6CBD)
    val warn: Color @Composable get() = if (isDark()) Color(0xFFF2C66D) else Color(0xFF7A5C10)
    /** The phone's corners: 8 for a control, 12 for a card or dialog. */
    val ctl = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(12.dp)
}

/** Look's measured colours as plain values, for LookContrastTest. */
object LookPalette {
    val EDGE_LIGHT = Color(0xFF858585)
    val EDGE_DARK = Color(0xFF8C8C8C)
    val PICKED_LIGHT = Color(0xFF1B5E3A)
    val PICKED_DARK = Color(0xFF7FD1A3)
    val ON_PICKED_DARK = Color(0xFF0B2A1A)
    val RAIL_LIGHT = Color(0xFFF2F4EF)
    val RAIL_DARK = Color(0xFF161917)
    val GROUP_TEXT_LIGHT = Color(0xFF3F6032)
    val GROUP_TEXT_DARK = Color(0xFF9CC5A8)
    /** Home's green card's halves: a soft red and a soft green, 5:1 and 5.9:1 on their pills (Marhala 2). */
    val HERO_GET = Color(0xFFFFB4AD)
    val HERO_GIVE = Color(0xFF9EE6B8)
}

/** The phone's tile picture on its own tint, [size] across. */
@Composable
fun TileBadge(s: Section, size: Int = 34, glyph: Int = 22) = PictureBadge(s.icon, s.tile, s.ink, size, glyph)

/**
 * A picture on its tint, [size] across: one of the phone's two-tone tiles as drawn, or, with [ink],
 * one of its one-colour (white) glyphs painted in that colour (white on a pale tint would vanish).
 */
@Composable
fun PictureBadge(icon: String, tile: String, ink: String?, size: Int, glyph: Int) {
    val c = LocalRoshanColors.current
    val dark = isDark()
    Box(
        Modifier.size(size.dp).background(TileIcons.color(tile, dark) ?: c.pageBg, RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center
    ) {
        if (ink != null) Glyph(icon, TileIcons.color(ink, dark) ?: c.ink, glyph)
        else tileIcon(icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(glyph.dp)) }
    }
}

/**
 * The rail on the start side (Marhala 2, the owner's mockup of 25 Oct 2026):
 * Home, then Every day and Sales & stock under their names, then Reports, Tools
 * and More folded behind one row each (their picture, name and count). A folded
 * group opens when one of its pages is chosen from anywhere (the bell, Home's
 * actions) and the owner may fold it again; folded, it is marked while it holds
 * the open page. Each row is 36 dp with the place's own picture; the open one is
 * deep green with a gold edge. [badges]: a count beside a section.
 */
@Composable
fun SectionRail(
    current: Section, onPick: (Section) -> Unit, modifier: Modifier = Modifier, badges: Map<Section, Int> = emptyMap(),
    /** Fixed above the groups: the shop's card (Marhala 2). */
    top: @Composable () -> Unit = {}
) {
    val opened = remember { androidx.compose.runtime.mutableStateMapOf<Group, Boolean>() }
    androidx.compose.runtime.LaunchedEffect(current) { if (current.group.folds) opened[current.group] = true }
    // Scrolls: thirty-four places outgrow a 768-row laptop at 125% display scale.
    // The phone's rail (the owner's mockup, 25 Oct 2026): its own pale green ground with an edge, a scrollbar
    // that says there is more below, the chosen row deep green with a gold edge.
    val railScroll = rememberScrollState()
    Box(modifier.width(212.dp).fillMaxHeight().background(Look.rail)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(start = 8.dp, end = 9.dp, top = 8.dp, bottom = 4.dp)) { top() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().verticalScroll(railScroll).padding(horizontal = 8.dp, vertical = 4.dp)) {
                val trade = LocalTrade.current
                var firstFold = true
                Group.entries.forEach { g ->
                    val inGroup = Section.entries.filter { it.group == g && trade.shows(it) }
                    if (inGroup.isEmpty()) return@forEach
                    when {
                        g == Group.HOME -> {}
                        g.folds -> {
                            // One quiet line above the folded groups, as the mockup.
                            if (firstFold) { Spacer(Modifier.height(6.dp)); HorizontalDivider(color = Look.line); Spacer(Modifier.height(4.dp)); firstFold = false }
                            val open = opened[g] == true
                            GroupRow(g, inGroup.size, open, holdsOpenPage = !open && current.group == g) { opened[g] = !open }
                            if (!open) return@forEach
                        }
                        else -> Text(
                            word(g.word).uppercase(),
                            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                            color = Look.groupText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    inGroup.forEach { s ->
                        // A folded group's pages sit a step in from its row.
                        Box(if (g.folds) Modifier.padding(start = 10.dp) else Modifier) { RailRow(s, s == current, badges[s] ?: 0) { onPick(s) } }
                    }
                }
                }
                androidx.compose.foundation.VerticalScrollbar(
                    androidx.compose.foundation.rememberScrollbarAdapter(railScroll),
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 1.dp)
                )
            }
        }
        // The rail's edge against the page, the whole height.
        Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(Look.railEdge))
    }
}

@Composable
private fun RailRow(s: Section, on: Boolean, badge: Int, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(Look.ctl)
            .background(eased(if (on) Look.pickedFill else Color.Transparent))
            .then(if (on) Modifier.border(1.5.dp, Look.goldEdge, Look.ctl) else Modifier)
            .press(Look.ctl, wash = if (on) Color.Black.copy(alpha = 0.12f) else Look.hover, onClick = onClick)
            .height(36.dp)
            .padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The place's own picture, small, on its own tint.
        TileBadge(s, 22, 14)
        Spacer(Modifier.width(12.dp))
        Text(
            word(s.word),
            Modifier.weight(1f),
            color = if (on) Look.onPicked else c.ink,
            fontSize = 14.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (badge > 0) {
            Text(
                if (badge > 9999) "9999+" else badge.toString(),
                color = if (on) Look.onPicked else if (s == Section.FOLLOWUP) c.owedToMe else c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * A folded group's row (the mockup's Reports · Tools · More): its picture, its name, how many pages it
 * holds and a chevron that turns down while open. [holdsOpenPage]: folded over the open page, it wears
 * the chosen tint so the owner still sees where he is.
 */
@Composable
private fun GroupRow(g: Group, count: Int, open: Boolean, holdsOpenPage: Boolean, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(Look.ctl)
            .background(eased(if (holdsOpenPage) Look.chosen else Color.Transparent))
            .press(Look.ctl, onClick = onClick)
            .height(36.dp)
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PictureBadge(g.icon ?: "ic_tile_settings", g.tile ?: "tile_settings_bg", g.ink, 22, 14)
        Spacer(Modifier.width(12.dp))
        Text(
            word(g.word), Modifier.weight(1f), color = c.ink, fontSize = 14.sp,
            fontWeight = if (holdsOpenPage) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            count.toString(),
            Modifier.clip(RoundedCornerShape(10.dp)).background(Look.subtle).padding(horizontal = 7.dp, vertical = 1.dp),
            color = c.inkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Glyph(if (open) "ic_chevron_down" else "ic_chevron_right", c.inkSoft, 14)
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
    // Inside the window its top row draws the title (Marhala 2).
    val head = LocalPageHead.current
    if (head != null) {
        androidx.compose.runtime.SideEffect { head.title = title; head.subtitle = subtitle; head.actions = null }
        return
    }
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
fun DayPicker(initial: Long?, onPick: (startOfDay: Long) -> Unit, onDismiss: () -> Unit, title: String? = null) {
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
    ) {
        if (title == null) DatePicker(state)
        else DatePicker(state, title = { Text(title, Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) })
    }
}

/**
 * A failed save in the user's own language, the technical reason kept in brackets for support
 * (21 Oct 2026, the nine-language review: "sale refused: ..." reached Hindi and Arabic screens raw).
 */
fun failText(words: com.innovation313.roshankhata.words.Words, e: Throwable): String =
    words.text(W.action_failed_detail, e.message ?: e.javaClass.simpleName)
