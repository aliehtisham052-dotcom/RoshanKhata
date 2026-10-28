package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.DayCloseMath
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.SalesReport
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The report's views, in the order the chips show them. */
enum class ReportView(val wordKey: String) {
    DAY(W.srep_by_day), ITEM(W.srep_by_item), CATEGORY(W.srep_by_category), CUSTOMER(W.srep_by_customer),
    CASHIER(W.srep_by_cashier), HOUR(W.srep_by_hour), TAX(W.srep_tax), PROFIT(W.srep_profit), DEAD(W.srep_dead)
}

enum class ReportPeriod(val wordKey: String) {
    TODAY(W.srep_period_today), WEEK(W.srep_period_week), MONTH(W.srep_period_month),
    /** The owner's own first and last day (21 Oct 2026). */
    CUSTOM(W.srep_period_custom)
}

/**
 * [from, to) for a period, ending now; for [ReportPeriod.CUSTOM] the chosen
 * days ([custom]: the first day's start and the last day's start, both
 * whole days), or today while none is chosen.
 */
fun periodWindow(p: ReportPeriod, now: Long = System.currentTimeMillis(), custom: Pair<Long, Long>? = null): Pair<Long, Long> {
    val day = DayCloseMath.dayOf(now)
    if (p == ReportPeriod.CUSTOM && custom != null) {
        val first = DayCloseMath.dayOf(minOf(custom.first, custom.second))
        val last = DayCloseMath.dayOf(maxOf(custom.first, custom.second))
        return DayCloseMath.startOf(first) to DayCloseMath.startOf(last + 1)
    }
    val start = when (p) {
        ReportPeriod.TODAY, ReportPeriod.CUSTOM -> DayCloseMath.startOf(day)
        ReportPeriod.WEEK -> DayCloseMath.startOf(day - 6)
        ReportPeriod.MONTH -> java.util.Calendar.getInstance().apply {
            timeInMillis = now; set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    return start to now + 1
}

/** The rows a view shows, as (label, columns) — the same on both platforms and in the CSV. */
@Composable
fun reportRows(r: SalesReport, view: ReportView): Pair<List<String>, List<List<String>>> {
    fun rows(list: List<SalesReport.Row>, blank: String) = list.map { row ->
        // An item sold at a loss shows its minus (25 Oct 2026, the audit), as the P&L view's totals do.
        listOf(row.key.ifEmpty { blank }, row.count.toString(), Calc.trim(row.quantity), Format.money(row.revenue), row.profit?.let { Format.signedIfNegative(it) } ?: "–")
    }
    val head = listOf("", word(W.srep_col_count), word(W.pos_col_qty), word(W.srep_col_revenue), word(W.srep_col_profit))
    return when (view) {
        ReportView.DAY -> head to rows(r.byDay, "")
        // A khata sale written without its goods (Marhala 3: the khata counts now) says so.
        ReportView.ITEM -> head to rows(r.byItem, word(W.srep_no_goods))
        ReportView.CATEGORY -> head to rows(r.byCategory, word(W.srep_no_category))
        ReportView.CUSTOMER -> head to rows(r.byCustomer, word(W.pos_walk_in))
        ReportView.CASHIER -> head to rows(r.byCashier, word(W.access_owner))
        ReportView.HOUR -> head to rows(r.byHour, "")
        ReportView.TAX -> listOf(word(W.srep_tax_rate), word(W.srep_tax_base), word(W.srep_tax_amount)) to
            r.tax.map { (rate, base, tax) -> listOf(Calc.trim(rate) + "%", Format.money(base), Format.money(tax)) }
        // A profit is shown only where the costs behind it are known (25 Oct 2026, the audit: with no cost
        // known, "Net profit" was the expenses alone, shown as a loss).
        ReportView.PROFIT -> listOf("", "") to listOfNotNull(
            listOf(word(W.srep_revenue), Format.money(r.revenue)),
            listOf(word(W.srep_returns), "−" + Format.money(r.returnsTotal)),
            listOf(word(W.srep_cost) + " (" + word(W.srep_cost_known, Format.money(r.costKnownRevenue)) + ")", "−" + Format.money(r.cost)),
            listOf(word(W.srep_gross), r.grossOrNull?.let { Format.signedIfNegative(it) } ?: "–"),
            listOf(word(W.srep_expenses), "−" + Format.money(r.expenses)),
            listOf(word(W.srep_net), r.netOrNull?.let { Format.signedIfNegative(it) } ?: "–"),
            if (r.costComplete) null else listOf(word(W.srep_net_unknown), "")
        )
        ReportView.DEAD -> listOf(word(W.srep_dead_help, SalesReport.DEAD_DAYS.toString())) to r.deadStock.map { listOf(it) }
    }
}

/** The words on a period's chip: the custom one wears its dates once they are chosen. */
@Composable
fun periodLabel(p: ReportPeriod, custom: Pair<Long, Long>?): String =
    if (p == ReportPeriod.CUSTOM && custom != null) Format.dateOnly(minOf(custom.first, custom.second)) + " – " + Format.dateOnly(maxOf(custom.first, custom.second))
    else word(p.wordKey)

/**
 * The report on the phone: period chips, view chips, the chart, the rows.
 * [pickRange] asks the platform for the custom period's two days (the
 * phone's date range picker), handing back the current choice to start from.
 */
@Composable
fun SalesReportScreen(
    dao: KhataDao, reload: Int = 0, modifier: Modifier = Modifier, onReport: ((SalesReport) -> Unit)? = null,
    pickRange: ((current: Pair<Long, Long>?, onPicked: (Pair<Long, Long>) -> Unit) -> Unit)? = null
) {
    val c = LocalRoshanColors.current
    var period by remember { mutableStateOf(ReportPeriod.TODAY) }
    var custom by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var view by remember { mutableStateOf(ReportView.ITEM) }
    var report by remember { mutableStateOf<SalesReport?>(null) }
    LaunchedEffect(period, custom, reload) {
        val (from, to) = periodWindow(period, custom = custom)
        report = withContext(Dispatchers.IO) { runCatching { SalesReport.compute(dao, from, to) }.getOrNull() }
        report?.let { onReport?.invoke(it) }
    }
    Column(modifier.fillMaxSize().background(c.pageBg)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReportPeriod.values().filter { it != ReportPeriod.CUSTOM || pickRange != null }.forEach { p ->
                FilterChip(selected = period == p, onClick = {
                    if (p == ReportPeriod.CUSTOM) {
                        pickRange?.invoke(custom) { range -> custom = range; period = ReportPeriod.CUSTOM }
                    } else {
                        period = p
                    }
                }, label = { Text(periodLabel(p, custom), fontSize = 12.sp, maxLines = 1) })
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReportView.values().forEach { v -> FilterChip(selected = view == v, onClick = { view = v }, label = { Text(word(v.wordKey), fontSize = 12.sp) }) }
        }
        val r = report
        if (r == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("…", color = c.textMuted) }; return }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text(word(W.srep_revenue), color = c.textMuted, fontSize = 12.sp)
                Text(Format.money(r.revenue), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text(word(W.srep_col_count), color = c.textMuted, fontSize = 12.sp)
                Text(r.saleCount.toString(), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        val (head, rows) = reportRows(r, view)
        if (rows.isEmpty()) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(word(W.srep_none), color = c.textMuted) }; return }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            item(key = "chart") {
                SalesChart(r.bars(), Modifier.fillMaxWidth().padding(bottom = 8.dp).background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp))
            }
            if (head.any { it.isNotEmpty() }) item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    head.forEachIndexed { i, h -> Text(h, Modifier.weight(if (i == 0) 2f else 1f), color = c.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                }
            }
            items(rows) { cells ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    cells.forEachIndexed { i, v ->
                        Text(v, Modifier.weight(if (i == 0) 2f else 1f), color = c.ink, fontSize = if (i == 0) 14.sp else 13.sp,
                            fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = c.surface)
            }
        }
    }
}

/**
 * The report's chart (21 Oct 2026): a bar per hour, day or month of the
 * window ([SalesReport.bars]), the tallest the highest sale, the first,
 * middle and last slices named under it. Time runs from the reading side:
 * in Urdu or Arabic the first bar stands on the right, under its label.
 */
@Composable
fun SalesChart(bars: List<SalesReport.Bar>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 120.dp) {
    val c = LocalRoshanColors.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val top = bars.maxOfOrNull { it.revenue } ?: 0.0
    val fill = c.brandGreen
    val empty = c.textMuted.copy(alpha = 0.35f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(word(W.srep_chart), Modifier.weight(1f), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (top > 0.0) Text(word(W.srep_chart_top, Format.money(top)), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = bars.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val w = (slot * 0.72f).coerceAtLeast(1f)
            bars.forEachIndexed { i, b ->
                val h = if (top > 0.0) (b.revenue / top * size.height).toFloat().coerceAtLeast(if (b.revenue > 0.0) 2f else 0f) else 0f
                val bar = if (h > 0f) h else 2f
                val x = if (rtl) size.width - (i + 1) * slot + (slot - w) / 2 else i * slot + (slot - w) / 2
                drawRect(if (h > 0f) fill else empty, topLeft = androidx.compose.ui.geometry.Offset(x, size.height - bar), size = androidx.compose.ui.geometry.Size(w, bar))
            }
        }
        if (bars.isNotEmpty()) Row(Modifier.fillMaxWidth()) {
            Text(bars.first().label, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (bars.size > 2) Text(bars[bars.size / 2].label, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (bars.size > 1) Text(bars.last().label, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
        }
    }
}
