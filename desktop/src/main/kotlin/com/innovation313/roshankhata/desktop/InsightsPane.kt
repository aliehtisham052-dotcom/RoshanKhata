package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Insights
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.MonthSale
import com.innovation313.roshankhata.data.Profit
import com.innovation313.roshankhata.data.SaleInsights
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sale Insights on the computer (KMP Phase 7i; Marhala 4, part 7 — the page frame and the rest of the phone's
 * InsightsActivity): today's money in and out and its entries; this month's sale against last month's; the
 * profit card (this month's sale rate less purchase rate where both are known, after the expenses, last month's,
 * and how much of the month's sales it is worked out from — "no figure yet" rather than a zero); twelve months as
 * bars that carry shape only, the chosen month's figure written under them (the best month first, a click
 * chooses another — the bars no longer wear English "L" and "k"); the top products (the counter's walk-ins
 * counted too), the top profit and the top customers, each ranked with a bar. The same shared sums as the
 * phone's (Insights.thisMonth, Profit.thisMonth / lastMonth).
 */
@Composable
fun InsightsPane(dao: KhataDao, reload: Int) {
    val c = LocalRoshanColors.current
    var data by remember { mutableStateOf<SaleInsights?>(null) }
    var profit by remember { mutableStateOf<Pair<Profit.ProfitReport, Profit.ProfitReport>?>(null) }
    LaunchedEffect(reload) {
        data = withContext(Dispatchers.IO) { Insights.thisMonth(dao) }
        // Profit reads the sale and bill lines twice more; it comes after, so the first figures are not held up.
        profit = withContext(Dispatchers.IO) { runCatching { Profit.thisMonth(dao) to Profit.lastMonth(dao) }.getOrNull() }
    }
    FluentPage(word(W.insights_title), word(W.insights_subtitle)) {
        val d = data ?: return@FluentPage
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FigureCard(word(W.insights_today_received), Format.money(d.todayReceived), Look.accent, Modifier.weight(1f), icon = "ic_arrow_got", tint = Look.accentTint)
            FigureCard(word(W.insights_today_given), Format.money(d.todayGiven), c.owedToMe, Modifier.weight(1f), icon = "ic_arrow_gave", tint = Look.getTint)
            FigureCard(word(W.insights_today), plural(WP.entries_today, d.todayCount, d.todayCount), c.ink, Modifier.weight(1f))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 760.dp
            if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MonthSaleCard(d, Modifier.weight(1f))
                ProfitCard(profit?.first, profit?.second, Modifier.weight(1f))
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MonthSaleCard(d, Modifier.fillMaxWidth())
                ProfitCard(profit?.first, profit?.second, Modifier.fillMaxWidth())
            }
        }
        MonthsCard(d.monthlySales)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 760.dp
            val products: @Composable (Modifier) -> Unit = { m ->
                RankCard(word(W.insights_top_products), word(W.insights_no_products), d.topProducts.map { it.name to it.qty }, m) { i -> Format.qty(d.topProducts[i].qty, d.topProducts[i].unit) }
            }
            val gains = profit?.first?.products.orEmpty().filter { it.profit > 0.0 }.take(5)
            val earned: @Composable (Modifier) -> Unit = { m ->
                RankCard(word(W.insights_profit_top), word(W.insights_profit_top_none), gains.map { it.name to it.profit }, m) { i -> Format.money(gains[i].profit) }
            }
            val customers: @Composable (Modifier) -> Unit = { m ->
                RankCard(word(W.insights_top_customers), word(W.insights_no_sales), d.topCustomers.map { Format.name(it.name) to it.total }, m, ink = c.owedToMe) { i -> Format.money(d.topCustomers[i].total) }
            }
            if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                products(Modifier.weight(1f)); earned(Modifier.weight(1f)); customers(Modifier.weight(1f))
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                products(Modifier.fillMaxWidth()); earned(Modifier.fillMaxWidth()); customers(Modifier.fillMaxWidth())
            }
        }
        Text(word(W.insights_profit_note), color = c.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun InsightCard(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = LocalRoshanColors.current.inkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        content()
    }
}

/** This month's sale and the trend against last month: up green, down red, nothing to compare in muted words. */
@Composable
private fun MonthSaleCard(d: SaleInsights, modifier: Modifier) {
    val c = LocalRoshanColors.current
    InsightCard(word(W.insights_total_sale), modifier) {
        Text(Format.money(d.thisMonthTotal), color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        val pct = d.changePercent
        Text(
            when {
                pct == null -> word(W.insights_no_compare)
                pct >= 0 -> word(W.insights_up, pct, Format.money(d.lastMonthTotal))
                else -> word(W.insights_down, -pct, Format.money(d.lastMonthTotal))
            },
            color = when { pct == null -> c.textMuted; pct >= 0 -> Look.accent; else -> c.owedToMe }, fontSize = 13.sp
        )
    }
}

/**
 * The phone's profit card: the month's profit with its minus when it is a loss, after the expenses, last month's,
 * and how much of the month's sales it stands on; a dash and what it needs when nothing could be worked out.
 */
@Composable
private fun ProfitCard(now: Profit.ProfitReport?, last: Profit.ProfitReport?, modifier: Modifier) {
    val c = LocalRoshanColors.current
    fun signed(v: Double) = if (v < 0) "−" + Format.money(v) else Format.money(v)
    InsightCard(word(W.insights_profit_title), modifier) {
        if (now == null) {
            Text("…", color = c.textMuted, fontSize = 26.sp)
            return@InsightCard
        }
        if (now.isEmpty) {
            Text("—", color = c.textMuted, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(word(W.insights_profit_none), color = c.textMuted, fontSize = 13.sp)
        } else {
            Text(signed(now.grossProfit), color = if (now.grossProfit < 0) c.owedToMe else Look.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text(word(W.insights_profit_after_expenses, signed(now.netAfterExpenses), Format.money(now.expenses)), color = c.ink, fontSize = 13.sp)
            val pct = now.coveragePercent
            Text(if (pct == null) word(W.insights_profit_none) else word(W.insights_profit_coverage, pct), color = c.textMuted, fontSize = 12.sp)
        }
        if (last != null && !last.isEmpty) Text(word(W.insights_profit_last_month, signed(last.grossProfit)), color = c.inkSoft, fontSize = 12.sp)
    }
}

/**
 * Twelve months as bars that carry shape only (the phone's: twelve figures side by side do not read); the chosen
 * month's name and total under them, the best month first, a click on a bar choosing another. A month with no
 * sales keeps a sliver, so it is seen to be there and empty.
 */
@Composable
private fun MonthsCard(months: List<MonthSale>) {
    val c = LocalRoshanColors.current
    val best = months.withIndex().maxByOrNull { it.value.total }
    InsightCard(word(W.insights_monthly_title), Modifier.fillMaxWidth()) {
        if (best == null || best.value.total <= 0.0) {
            Text(word(W.insights_monthly_none), color = c.textMuted, fontSize = 13.sp)
            return@InsightCard
        }
        var chosen by remember(months) { mutableStateOf(best.index) }
        Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            months.forEachIndexed { i, m ->
                val share = (m.total / best.value.total).toFloat().coerceIn(0.015f, 1f)
                val on = i == chosen
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).press(RoundedCornerShape(6.dp)) { chosen = i }.padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(share)
                                .background(if (on) Look.accent else Look.accent.copy(alpha = 0.35f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                    }
                    Text(m.shortLabel, color = if (on) c.ink else c.textMuted, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false)
                }
            }
        }
        val m = months[chosen.coerceIn(months.indices)]
        Text(word(W.insights_monthly_pick, m.fullLabel, Format.money(m.total)), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A ranked list: the place, the name, a bar against the first, and its figure; [empty] when there is none. */
@Composable
private fun RankCard(title: String, empty: String, rows: List<Pair<String, Double>>, modifier: Modifier, ink: Color? = null, figure: (Int) -> String) {
    val c = LocalRoshanColors.current
    InsightCard(title, modifier) {
        if (rows.isEmpty()) {
            Text(empty, color = c.textMuted, fontSize = 13.sp)
            return@InsightCard
        }
        val max = rows.first().second.coerceAtLeast(1.0)
        rows.forEachIndexed { i, (name, value) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text((i + 1).toString(), Modifier.width(18.dp), color = c.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(name, Modifier.weight(1f), color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(figure(i), color = ink ?: c.inkSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(26.dp))
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Look.subtle)) {
                        Box(Modifier.fillMaxWidth((value / max).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(ink ?: Look.accent))
                    }
                }
            }
        }
    }
}
