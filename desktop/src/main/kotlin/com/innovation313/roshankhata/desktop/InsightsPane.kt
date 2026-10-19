package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Insights
import com.innovation313.roshankhata.data.KhataDao
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
 * Sale Insights on the computer (KMP Phase 7i): today, this month against
 * last, twelve months as bars, top products and customers — the phone's
 * shared Insights.thisMonth, the same sums from the same book.
 */
@Composable
fun InsightsPane(dao: KhataDao, reload: Int) {
    val c = LocalRoshanColors.current
    var data by remember { mutableStateOf<SaleInsights?>(null) }
    LaunchedEffect(reload) { data = withContext(Dispatchers.IO) { Insights.thisMonth(dao) } }
    val d = data ?: return

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PaneTitle(word(W.insights_title), word(W.insights_subtitle))
        Text(word(W.insights_today) + "  ·  " + plural(WP.entries_today, d.todayCount, d.todayCount), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TotalCard(word(W.insights_today_given), Format.money(d.todayGiven), c.owedToMe, Modifier.weight(1f))
            TotalCard(word(W.insights_today_received), Format.money(d.todayReceived), c.iOwe, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(16.dp)) {
            Text(word(W.insights_total_sale), color = c.textMuted, fontSize = 13.sp)
            Text(Format.money(d.thisMonthTotal), color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val pct = d.changePercent
            Text(
                when {
                    pct == null -> word(W.insights_no_compare)
                    pct >= 0 -> word(W.insights_up, pct, Format.money(d.lastMonthTotal))
                    else -> word(W.insights_down, -pct, Format.money(d.lastMonthTotal))
                },
                color = when { pct == null -> c.textMuted; pct >= 0 -> c.iOwe; else -> c.owedToMe }, fontSize = 13.sp
            )
        }
        // Twelve months as bars, the month just gone at the end; each bar wears its figure.
        Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(14.dp)).padding(16.dp)) {
            Text(word(W.insights_monthly_title), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val max = d.monthlySales.maxOfOrNull { it.total } ?: 0.0
            if (max <= 0.0) {
                Text(word(W.insights_monthly_none), color = c.textMuted, fontSize = 13.sp)
            } else {
                Row(Modifier.fillMaxWidth().height(190.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.monthlySales.forEach { m ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            if (m.total > 0) Text(barFigure(m.total), color = c.inkSoft, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
                            Box(
                                Modifier.fillMaxWidth().height((130 * (m.total / max)).coerceAtLeast(2.0).dp)
                                    .background(c.brandGreen, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                            Text(m.shortLabel, color = c.textMuted, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).background(c.surface, RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(word(W.insights_top_products), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (d.topProducts.isEmpty()) Text(word(W.insights_no_products), color = c.textMuted, fontSize = 13.sp)
                d.topProducts.forEach { p ->
                    Row { Text(p.name, Modifier.weight(1f), color = c.ink, fontSize = 14.sp); Text(Format.qty(p.qty, p.unit), color = c.inkSoft, fontSize = 14.sp) }
                }
            }
            Column(Modifier.weight(1f).background(c.surface, RoundedCornerShape(14.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(word(W.insights_top_customers), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (d.topCustomers.isEmpty()) Text(word(W.insights_no_sales), color = c.textMuted, fontSize = 13.sp)
                d.topCustomers.forEach { cs ->
                    Row { Text(Format.name(cs.name), Modifier.weight(1f), color = c.ink, fontSize = 14.sp); Text(Format.money(cs.total), color = c.owedToMe, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** A bar's figure in the shop's own way of counting: 1.2L (lakh), 45k, 800. */
private fun barFigure(v: Double): String = when {
    v >= 100_000 -> "%.1fL".format(java.util.Locale.US, v / 100_000)
    v >= 1_000 -> "%.0fk".format(java.util.Locale.US, v / 1_000)
    else -> "%.0f".format(java.util.Locale.US, v)
}
