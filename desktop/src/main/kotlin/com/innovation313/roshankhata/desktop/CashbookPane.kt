package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Cashbook on the computer (KMP Phase 7b): money in and out of the
 * business with no customer attached — rent, wages, a walk-in sale. A
 * separate table from the ledger, as on the phone, so it never changes what
 * anyone owes.
 */
@Composable
fun CashbookPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val entries by remember { dao.observeCashEntries() }.collectAsState(emptyList())
    val income by remember { dao.observeCashIncome() }.collectAsState(0.0)
    val expense by remember { dao.observeCashExpense() }.collectAsState(0.0)
    var adding by remember { mutableStateOf<Boolean?>(null) }
    var deleting by remember { mutableStateOf<CashEntry?>(null) }
    var closing by remember { mutableStateOf(false) }
    if (closing) DayCloseDialog(dao) { closing = false }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PaneTitle(word(W.cashbook), word(W.cashbook_subtitle)) }
            FillButton(word(W.cash_income), c.iOwe) { adding = true }
            Spacer(Modifier.width(10.dp))
            FillButton(word(W.cash_expense), c.gaveFill) { adding = false }
            Spacer(Modifier.width(10.dp))
            TextButton({ closing = true }) { Text(word(W.dayclose_button), color = c.brandGreenText) }
        }
        val last by remember { dao.observeLastDayClose() }.collectAsState(null)
        Text(
            last?.let { word(W.dayclose_last, Format.dateOnly(it.closedAt), Format.signedTotal(it.difference)) } ?: word(W.dayclose_never),
            color = c.textMuted, fontSize = 12.sp
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TotalCard(word(W.cash_in), Format.money(income), c.iOwe, Modifier.weight(1f))
            TotalCard(word(W.cash_out), Format.money(expense), c.owedToMe, Modifier.weight(1f))
            val net = income - expense
            TotalCard(word(W.cash_net), Format.money(net), if (net < 0) c.owedToMe else c.iOwe, Modifier.weight(1f))
        }
        Text(word(W.cashbook_separate_note), color = c.textMuted, fontSize = 12.sp)
        if (entries.isEmpty()) {
            Text(word(W.no_cash_entries), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(14.dp))) {
                items(entries, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.category, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            e.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.inkSoft, fontSize = 13.sp) }
                            Text(Format.dateTime(e.timestamp), color = c.textMuted, fontSize = 12.sp)
                        }
                        // A sign as well as a colour: colour alone fails anyone who cannot tell red from green.
                        Text(
                            (if (e.isIncome) "+ " else "− ") + Format.money(e.amount),
                            color = if (e.isIncome) c.iOwe else c.owedToMe,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(12.dp))
                        TextButton({ deleting = e }) { Text(word(W.delete), color = c.owedToMe) }
                    }
                    HorizontalDivider(color = c.pageBg)
                }
            }
        }
    }

    adding?.let { way ->
        DialogWindow(
            onCloseRequest = { adding = null },
            onPreviewKeyEvent = escCloses { adding = null },
            title = word(if (way) W.cash_income else W.cash_expense),
            state = rememberDialogState(width = 480.dp, height = 520.dp)
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                CashForm(
                    dao = dao,
                    income = way,
                    onSave = { entry ->
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.insertCashEntry(entry) }
                            adding = null
                        }
                    },
                    onCancel = { adding = null }
                )
            }
        }
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(word(W.delete_cash_entry)) },
            text = { Text(word(W.delete_cash_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch { withContext(Dispatchers.IO) { dao.softDeleteCashEntry(e.id) } }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.delete)) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(word(W.cancel)) } }
        )
    }
}

/** Money in or out: an amount (sums allowed, as on the phone's pad), a category, a note. */
@Composable
private fun CashForm(dao: KhataDao, income: Boolean, onSave: (CashEntry) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    var isIncome by remember { mutableStateOf(income) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var used by remember { mutableStateOf<List<String>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { used = withContext(Dispatchers.IO) { dao.cashCategories() } }
    val amount = Calc.evalAmount(amountText)
    val needAmount = word(W.enter_valid_amount)
    val needCategory = word(W.enter_category)

    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TwoWay(word(W.cash_income), word(W.cash_expense), isIncome) { isIncome = it }
        OutlinedTextField(
            value = amountText, onValueChange = { amountText = it; problem = null },
            label = { Text(word(W.amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text(amount?.let { Format.money(it) } ?: "", color = c.textMuted, fontSize = 14.sp)
        OutlinedTextField(
            value = category, onValueChange = { category = it; problem = null },
            label = { Text(word(W.cash_category_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        // The owner's own earlier categories, one click each, so they need not retype them.
        if (used.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(used) { u ->
                    Text(
                        u,
                        Modifier.background(c.pageBg, RoundedCornerShape(16.dp))
                            .press { category = u; problem = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = c.ink, fontSize = 13.sp
                    )
                }
            }
        }
        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text(word(W.note_optional)) }, modifier = Modifier.fillMaxWidth()
        )
        problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text(word(W.cancel)) }
            FillButton(word(W.save), if (isIncome) c.brandGreen else c.gaveFill) {
                when {
                    amount == null -> problem = needAmount
                    category.isBlank() -> problem = needCategory
                    else -> onSave(
                        CashEntry(
                            amount = amount,
                            isIncome = isIncome,
                            category = category.trim(),
                            note = note.trim().ifEmpty { null }
                        )
                    )
                }
            }
        }
    }
}

/**
 * Close the day (galla), the phone's day close: since the last count, the
 * cashbook's money in and out plus khata money marked Cash, what should be
 * in the drawer, and what was counted — over, short or matching. The last
 * count's figure is the next opening.
 */
@Composable
fun DayCloseDialog(dao: KhataDao, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var figures by remember { mutableStateOf<DayFigures?>(null) }
    var opening by remember { mutableStateOf("") }
    var counted by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val needCount = word(W.dayclose_need_count)
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val (fig, lastCounted) = withContext(Dispatchers.IO) {
            val last = dao.lastDayClose()
            val now = System.currentTimeMillis()
            val from = last?.closedAt ?: 0L
            DayFigures(
                now = now,
                cbIn = dao.cashbookTotalBetween(true, from, now),
                cbOut = dao.cashbookTotalBetween(false, from, now),
                kIn = dao.khataCashBetween(false, from, now),
                kOut = dao.khataCashBetween(true, from, now)
            ) to last?.counted
        }
        // The last count is this stretch's opening, as on the phone.
        lastCounted?.let { opening = Calc.trim(it) }
        figures = fig
    }
    val f = figures ?: return
    val open = Calc.evalPad(opening) ?: 0.0
    val expected = com.innovation313.roshankhata.data.DayCloseMath.expected(open, f.cbIn + f.kIn, f.cbOut + f.kOut)
    val count = Calc.evalPad(counted)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(word(W.dayclose_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(opening, { opening = it }, label = { Text(word(W.dayclose_opening)) }, singleLine = true)
                Text(word(W.dayclose_breakdown, Format.money(f.cbIn), Format.money(f.kIn), Format.money(f.cbOut), Format.money(f.kOut), Format.money(expected)),
                    color = c.inkSoft, fontSize = 13.sp)
                Text(word(W.dayclose_help), color = c.textMuted, fontSize = 12.sp)
                OutlinedTextField(counted, { counted = it; problem = null }, label = { Text(word(W.dayclose_counted)) }, singleLine = true)
                if (count != null) {
                    val d = com.innovation313.roshankhata.data.DayCloseMath.difference(count, expected)
                    Text(
                        when {
                            com.innovation313.roshankhata.data.Money.isPositive(d) -> word(W.dayclose_over, Format.money(d))
                            com.innovation313.roshankhata.data.Money.isPositive(-d) -> word(W.dayclose_short, Format.money(-d))
                            else -> word(W.dayclose_match)
                        },
                        color = if (com.innovation313.roshankhata.data.Money.isPositive(-d)) c.owedToMe else c.iOwe, fontWeight = FontWeight.Bold
                    )
                }
                OutlinedTextField(note, { note = it }, label = { Text(word(W.dayclose_note_hint)) }, singleLine = true)
                problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton({
                val n = count ?: run { problem = needCount; return@TextButton }
                val close = com.innovation313.roshankhata.data.DayClose(
                    day = Math.floorDiv(f.now + java.util.TimeZone.getDefault().getOffset(f.now), 24L * 60 * 60 * 1000),
                    opening = open,
                    cashIn = com.innovation313.roshankhata.data.LineMath.round(f.cbIn + f.kIn),
                    cashOut = com.innovation313.roshankhata.data.LineMath.round(f.cbOut + f.kOut),
                    counted = n,
                    note = note.trim().ifEmpty { null },
                    closedAt = f.now
                )
                scope.launch { withContext(Dispatchers.IO) { dao.saveDayClose(close) } }
                onClose()
            }) { Text(word(W.save)) }
        },
        dismissButton = { TextButton(onClose) { Text(word(W.cancel)) } }
    )
}

private class DayFigures(val now: Long, val cbIn: Double, val cbOut: Double, val kIn: Double, val kOut: Double)
