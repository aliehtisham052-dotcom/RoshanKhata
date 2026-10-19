package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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

/** The parts of the book the computer has, in the phone's Home order (KMP Phase 7). */
/** [icon] and [tile]: the phone's Home tile picture and its background colour for the same place. */
enum class Section(val word: String, val icon: String, val tile: String) {
    KHATA(W.nav_khata, "ic_tile_khata", "tile_khata_bg"),
    FOLLOWUP(W.followup_title, "ic_tile_followup", "tile_followup_bg"),
    CASHBOOK(W.nav_cashbook, "ic_tile_cashbook", "tile_cashbook_bg"),
    CHEQUES(W.nav_cheques, "ic_tile_cheques", "tile_cheques_bg"),
    PLANS(W.nav_plans, "ic_tile_plans", "tile_plans_bg"),
    STAFF(W.staff_title, "ic_tile_card", "tile_card_bg"),
    SCHEMES(W.schemes_title, "ic_tile_season", "tile_plans_bg"),
    BILLS(W.supplier_bills, "ic_tile_bills", "tile_bills_bg"),
    EXPIRY(W.tile_expiry, "ic_tile_expiring", "tile_stock_bg"),
    PRODUCTS(W.tile_products, "ic_tile_products", "tile_bills_bg"),
    RATES(W.rate_list_title, "ic_tile_price_list", "tile_calc_bg"),
    INVOICES(W.nav_invoice, "ic_tile_invoice", "tile_invoice_bg"),
    REPORT(W.ledger_report_title, "ic_tile_cashflow", "tile_insights_bg"),
    INSIGHTS(W.insights_title, "ic_tile_insights", "tile_insights_bg"),
    ZAKAT(W.tile_zakat, "ic_tile_zakat", "tile_zakat_bg"),
    CALC(W.calculator, "ic_tile_calc", "tile_calc_bg"),
    SHOP(W.business_profile, "ic_tile_settings", "tile_settings_bg")
}

/** The rail on the start side: one row per section, the open one filled. */
@Composable
fun SectionRail(current: Section, onPick: (Section) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoshanColors.current
    // Scrolls: fourteen sections can outgrow a 768-row laptop at 125% display scale.
    Column(modifier.width(210.dp).fillMaxHeight().background(c.surface).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
        val dark = isSystemInDarkTheme()
        Section.entries.forEach { s ->
            val on = s == current
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .background(if (on) c.brandGreen else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The phone's tile: its own picture on its own tint.
                Box(
                    Modifier.size(34.dp).background(TileIcons.color(s.tile, dark) ?: c.pageBg, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    tileIcon(s.icon)?.let { Image(it, contentDescription = null, modifier = Modifier.size(22.dp)) }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    word(s.word),
                    color = if (on) Color.White else c.ink,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2
                )
            }
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
