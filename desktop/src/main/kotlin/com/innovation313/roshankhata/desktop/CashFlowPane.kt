package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.CashFlow
import com.innovation313.roshankhata.data.ChequeStatus
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Payroll
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cash flow on the computer (KMP Phase 7r; Marhala 4, part 7 — the page frame), the phone's CashFlowActivity on
 * the same shared CashFlow: what is due in (plan instalments, cheques, harvest promises) and out (supplier bills,
 * cheques issued) over the next 30, 60 or 90 days, week by week, and the first week the shop runs short — said
 * on a red card, or a green one when no week does.
 */
@Composable
fun CashFlowPane(dao: KhataDao, reload: Int) {
    val c = LocalRoshanColors.current
    var days by remember { mutableStateOf(30) }
    var data by remember { mutableStateOf<Pair<List<CashFlow.Item>, Int>?>(null) }
    LaunchedEffect(reload) {
        data = withContext(Dispatchers.IO) {
            val parties = dao.partiesWithBalanceOnce()
            val names = parties.associate { it.id to it.name }
            val balance = parties.associate { it.id to it.balance }
            val plans = dao.openPlansOnce().map {
                CashFlow.Plan(it.partyId, it.partyName, it.totalAmount - it.paidSoFar, it.installmentAmount, it.nextDueDate)
            }
            val cheques = dao.pendingChequesOnce().filter { it.status == ChequeStatus.PENDING && names.containsKey(it.partyId) }.map {
                CashFlow.Cheque(it.partyId, names.getValue(it.partyId), it.amount, it.dueDate, it.isReceived)
            }
            val harvests = dao.cashFlowHarvestsOnce().map { it.copy(balance = balance[it.partyId] ?: 0.0) }
            // Negative balance = the shop owes that party.
            val owed = parties.filter { it.balance < 0 }.associate { it.id to -it.balance }
            CashFlow.items(plans, cheques, harvests, dao.cashFlowBillsOnce(), owed)
        }
    }
    FluentPage(word(W.cf_title), word(W.cf_subtitle)) {
        val (all, undated) = data ?: return@FluentPage
        val o = CashFlow.outlook(all, undated, Payroll.dayOf(System.currentTimeMillis()), days)
        PillRow {
            listOf(30, 60, 90).forEach { d -> FluentPill(word(W.cf_days, d.toString()), d == days) { days = d } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FigureCard(word(W.cf_in), Format.money(o.coming), Look.accent, Modifier.weight(1f), icon = "ic_arrow_got", tint = Look.accentTint)
            FigureCard(word(W.cf_out), Format.money(o.going), c.owedToMe, Modifier.weight(1f), icon = "ic_arrow_gave", tint = Look.getTint)
        }
        // The first week that runs short, on red; none, on green.
        val short = o.gapWeek != null
        Text(
            o.gapWeek?.let { word(W.cf_gap, DateWords.format("d MMM", it.from), Format.money(o.gap)) } ?: word(W.cf_no_gap),
            Modifier.fillMaxWidth().background(if (short) Look.getTint else Look.accentTint, Look.card).padding(horizontal = 16.dp, vertical = 12.dp),
            color = if (short) c.owedToMe else Look.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
        ListCard(Modifier.fillMaxWidth()) {
            o.weeks.forEach { w ->
                Line(
                    word(W.cf_week, DateWords.format("d MMM", w.from), DateWords.format("d MMM", w.to - 1)),
                    word(W.cf_in) + " " + Format.money(w.coming),
                    word(W.cf_out) + " " + Format.money(w.going),
                    if (w == o.gapWeek) c.owedToMe else c.ink
                )
            }
        }
        val seasons = LocalTrade.current.seasons
        ListCard(Modifier.fillMaxWidth()) {
            fun kind(k: CashFlow.Kind) = when (k) {
                CashFlow.Kind.PLAN -> W.cf_kind_plan
                CashFlow.Kind.CHEQUE_IN -> W.cf_kind_cheque_in
                // "After harvest" only where the shop has crop seasons (31 Oct 2026): any shop can promise a day.
                CashFlow.Kind.HARVEST -> if (seasons) W.cf_kind_harvest else W.cf_kind_promised
                CashFlow.Kind.BILL -> W.cf_kind_bill
                CashFlow.Kind.CHEQUE_OUT -> W.cf_kind_cheque_out
            }
            listOf(W.cf_overdue to o.overdue, W.cf_ahead to o.items).forEach { (head, list) ->
                if (list.isNotEmpty()) Heading(word(head))
                list.forEach { i ->
                    Line(
                        Format.name(i.name), word(kind(i.kind)) + " \u00b7 " + DateWords.format("d MMM yyyy", i.date),
                        (if (i.kind.isIn) "+" else "\u2212") + Format.money(i.amount), if (i.kind.isIn) Look.accent else c.owedToMe
                    )
                }
            }
            if (all.isEmpty()) Text(word(W.cf_empty), Modifier.padding(16.dp), color = c.textMuted)
        }
        Text(
            word(W.cf_note) + if (o.undatedBills > 0) "\n" + word(W.cf_undated, o.undatedBills.toString()) else "",
            color = c.textMuted, fontSize = 12.sp
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.fillMaxWidth().background(Look.layerAlt).padding(horizontal = 16.dp, vertical = 8.dp), color = LocalRoshanColors.current.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Rule()
}

@Composable
private fun Line(title: String, sub: String?, amount: String, colour: Color) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            sub?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text(amount, color = colour, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
    Rule()
}
