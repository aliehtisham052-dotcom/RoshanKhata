package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

enum class ReportPeriod(val wordKey: String) { TODAY(W.srep_period_today), WEEK(W.srep_period_week), MONTH(W.srep_period_month) }

/** [from, to) for a period, ending now. */
fun periodWindow(p: ReportPeriod, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val day = DayCloseMath.dayOf(now)
    val start = when (p) {
        ReportPeriod.TODAY -> DayCloseMath.startOf(day)
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
        listOf(row.key.ifEmpty { blank }, row.count.toString(), Calc.trim(row.quantity), Format.money(row.revenue), row.profit?.let { Format.money(it) } ?: "–")
    }
    val head = listOf("", word(W.srep_col_count), word(W.pos_col_qty), word(W.srep_col_revenue), word(W.srep_col_profit))
    return when (view) {
        ReportView.DAY -> head to rows(r.byDay, "")
        ReportView.ITEM -> head to rows(r.byItem, "")
        ReportView.CATEGORY -> head to rows(r.byCategory, word(W.srep_no_category))
        ReportView.CUSTOMER -> head to rows(r.byCustomer, word(W.pos_walk_in))
        ReportView.CASHIER -> head to rows(r.byCashier, word(W.access_owner))
        ReportView.HOUR -> head to rows(r.byHour, "")
        ReportView.TAX -> listOf(word(W.srep_tax_rate), word(W.srep_tax_base), word(W.srep_tax_amount)) to
            r.tax.map { (rate, base, tax) -> listOf(Calc.trim(rate) + "%", Format.money(base), Format.money(tax)) }
        ReportView.PROFIT -> listOf("", "") to listOf(
            listOf(word(W.srep_revenue), Format.money(r.revenue)),
            listOf(word(W.srep_returns), "−" + Format.money(r.returnsTotal)),
            listOf(word(W.srep_cost) + " (" + word(W.srep_cost_known, Format.money(r.costKnownRevenue)) + ")", "−" + Format.money(r.cost)),
            listOf(word(W.srep_gross), Format.money(r.grossProfit)),
            listOf(word(W.srep_expenses), "−" + Format.money(r.expenses)),
            listOf(word(W.srep_net), Format.money(r.netProfit))
        )
        ReportView.DEAD -> listOf(word(W.srep_dead_help, SalesReport.DEAD_DAYS.toString())) to r.deadStock.map { listOf(it) }
    }
}

/** The report on the phone: period chips, view chips, the rows. */
@Composable
fun SalesReportScreen(dao: KhataDao, reload: Int = 0, modifier: Modifier = Modifier, onReport: ((SalesReport) -> Unit)? = null) {
    val c = LocalRoshanColors.current
    var period by remember { mutableStateOf(ReportPeriod.TODAY) }
    var view by remember { mutableStateOf(ReportView.ITEM) }
    var report by remember { mutableStateOf<SalesReport?>(null) }
    LaunchedEffect(period, reload) {
        val (from, to) = periodWindow(period)
        report = withContext(Dispatchers.IO) { runCatching { SalesReport.compute(dao, from, to) }.getOrNull() }
        report?.let { onReport?.invoke(it) }
    }
    Column(modifier.fillMaxSize().background(c.pageBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReportPeriod.values().forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(word(p.wordKey), fontSize = 12.sp) }) }
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
