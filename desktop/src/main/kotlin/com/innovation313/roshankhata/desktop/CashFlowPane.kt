package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
 * Cash flow on the computer (KMP Phase 7r), the phone's CashFlowActivity on
 * the same shared CashFlow: what is due in (plan instalments, cheques,
 * harvest promises) and out (supplier bills, cheques issued) over the next
 * 30, 60 or 90 days, week by week, and the first week the shop runs short.
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
    val (all, undated) = data ?: return
    val o = CashFlow.outlook(all, undated, Payroll.dayOf(System.currentTimeMillis()), days)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PaneTitle(word(W.cf_title), word(W.cf_subtitle))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 60, 90).forEach { d ->
                if (d == days) FillButton(word(W.cf_days, d.toString()), c.brandGreen) { days = d }
                else TextButton({ days = d }) { Text(word(W.cf_days, d.toString()), color = c.brandGreenText) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TotalCard(word(W.cf_in), Format.money(o.coming), c.iOwe, Modifier.weight(1f))
            TotalCard(word(W.cf_out), Format.money(o.going), c.owedToMe, Modifier.weight(1f))
        }
        Text(
            o.gapWeek?.let { word(W.cf_gap, DateWords.format("d MMM", it.from), Format.money(o.gap)) } ?: word(W.cf_no_gap),
            color = if (o.gapWeek != null) c.owedToMe else c.iOwe, fontSize = 14.sp, fontWeight = FontWeight.Bold
        )
        Card {
            o.weeks.forEach { w ->
                Line(
                    word(W.cf_week, DateWords.format("d MMM", w.from), DateWords.format("d MMM", w.to - 1)),
                    word(W.cf_in) + " " + Format.money(w.coming),
                    word(W.cf_out) + " " + Format.money(w.going),
                    if (w == o.gapWeek) c.owedToMe else c.ink
                )
            }
        }
        Card {
            fun kind(k: CashFlow.Kind) = when (k) {
                CashFlow.Kind.PLAN -> W.cf_kind_plan
                CashFlow.Kind.CHEQUE_IN -> W.cf_kind_cheque_in
                CashFlow.Kind.HARVEST -> W.cf_kind_harvest
                CashFlow.Kind.BILL -> W.cf_kind_bill
                CashFlow.Kind.CHEQUE_OUT -> W.cf_kind_cheque_out
            }
            listOf(W.cf_overdue to o.overdue, W.cf_ahead to o.items).forEach { (head, list) ->
                if (list.isNotEmpty()) Heading(word(head))
                list.forEach { i ->
                    Line(
                        Format.name(i.name), word(kind(i.kind)) + " \u00b7 " + DateWords.format("d MMM yyyy", i.date),
                        (if (i.kind.isIn) "+" else "\u2212") + Format.money(i.amount), if (i.kind.isIn) c.iOwe else c.owedToMe
                    )
                }
            }
            if (all.isEmpty()) Text(word(W.cf_empty), Modifier.padding(12.dp), color = c.textMuted)
        }
        Text(
            word(W.cf_note) + if (o.undatedBills > 0) "\n" + word(W.cf_undated, o.undatedBills.toString()) else "",
            color = c.textMuted, fontSize = 12.sp
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(LocalRoshanColors.current.surface, RoundedCornerShape(14.dp)).padding(6.dp)) { content() }
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = LocalRoshanColors.current.ink, fontWeight = FontWeight.Bold)
}

@Composable
private fun Line(title: String, sub: String?, amount: String, colour: Color) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            sub?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
        }
        Text(amount, color = colour, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = c.pageBg)
}
