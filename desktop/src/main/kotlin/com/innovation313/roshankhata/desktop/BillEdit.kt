package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.innovation313.roshankhata.data.BatchBuyer
import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.ExpiringBatch
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Correcting a bill (the phone's Edit Bill): its number, total, dates and
 * note. The supplier and cash/credit are shown locked, with the phone's
 * reason — moving a debt or erasing one is not a typo fix. A credit bill's
 * ledger entry follows in the same transaction (KhataDao.editSupplierBill).
 */
@Composable
fun BillEditDialog(dao: KhataDao, words: Words, billId: Long, supplierName: String, onDone: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var bill by remember { mutableStateOf<SupplierBill?>(null) }
    LaunchedEffect(billId) { bill = withContext(Dispatchers.IO) { dao.getBill(billId) } }
    val b = bill ?: return
    var number by remember(b.id) { mutableStateOf(b.billNumber.orEmpty()) }
    var total by remember(b.id) { mutableStateOf(Calc.trim(b.totalAmount)) }
    var note by remember(b.id) { mutableStateOf(b.note.orEmpty()) }
    var billDate by remember(b.id) { mutableStateOf(b.billDate) }
    var dueDate by remember(b.id) { mutableStateOf(b.dueDate) }
    var picking by remember { mutableStateOf<Int?>(null) }
    val amount = Calc.evalAmount(total)
    AppDialog(onCloseRequest = onDone, onPreviewKeyEvent = escCloses(onDone), title = word(W.edit_bill), state = rememberDialogState(width = fitW(520.dp), height = fitH(640.dp))) {
        Column(
            Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(Format.name(supplierName), color = c.ink, fontSize = 15.sp)
            Outlined(number, { number = it }, label = { Text(word(W.bill_number_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Outlined(total, { total = it }, label = { Text(word(W.bill_total_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            Text(amount?.let { Format.money(it) } ?: word(W.enter_valid_amount), color = if (amount == null) c.owedToMe else c.textMuted, fontSize = 13.sp)
            TextButton({ picking = 0 }) { Text(word(W.bill_date_set, Format.dateOnly(billDate)), color = c.brandGreenText) }
            // The bill's own words (25 Oct 2026, the audit: the button read "Pick the date on the cheque").
            TextButton({ picking = 1 }) { Text(dueDate?.let { word(W.due_date_set, Format.dateOnly(it)) } ?: word(W.bill_due_date), color = c.brandGreenText) }
            Outlined(note, { note = it }, label = { Text(word(W.note_optional)) }, modifier = Modifier.fillMaxWidth())
            Text(word(W.edit_bill_cash_locked), color = c.textMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.weight(1f))
                FluentButton(word(W.cancel), onDone, kind = ButtonKind.STANDARD)
                FillButton(word(W.save), c.brandGreen, enabled = amount != null && amount > 0) {
                    val a = amount ?: return@FillButton
                    val num = number.trim().ifEmpty { null }
                    val n = note.trim().ifEmpty { null }
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.editSupplierBill(
                                b.copy(billNumber = num, totalAmount = a, billDate = billDate, dueDate = dueDate, note = n),
                                entryNote = num?.let { words.text(W.bill_entry_note, it) } ?: n
                            )
                        }
                        onDone()
                    }
                }
            }
        }
        picking?.let { which ->
            DayPicker(if (which == 0) billDate else dueDate, onPick = { if (which == 0) billDate = it else dueDate = it; picking = null }, onDismiss = { picking = null })
        }
    }
}

/** A bill line added or corrected (KhataDao.saveBillLine); the bill's total and ledger entry are not touched. */
@Composable
fun BillLineDialog(dao: KhataDao, billId: Long, existing: BillItem?, onDone: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    AppDialog(
        onCloseRequest = onDone, onPreviewKeyEvent = escCloses(onDone),
        title = word(if (existing == null) W.add_item else W.edit_item),
        state = rememberDialogState(width = fitW(560.dp), height = fitH(560.dp))
    ) {
        Column(Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp)) {
            ItemForm(existing) { line ->
                scope.launch {
                    withContext(Dispatchers.IO) { dao.saveBillLine(billId, line) }
                    onDone()
                }
            }
            FluentButton(word(W.cancel), onDone, kind = ButtonKind.STANDARD)
        }
    }
}

/**
 * "Who bought this batch?" (the phone's BatchBuyersDialog): each customer
 * with how much and when, biggest first. When some sales of the product were
 * saved with no batch, it says so — a recall list that looks complete but is
 * not is worse than none.
 */
@Composable
fun BatchBuyersDialog(dao: KhataDao, billItemId: Long, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    var loaded by remember { mutableStateOf<Triple<BillItem, List<BatchBuyer>, Int>?>(null) }
    LaunchedEffect(billItemId) {
        loaded = withContext(Dispatchers.IO) {
            dao.getBillItem(billItemId)?.let { item ->
                Triple(item, dao.buyersOfBatch(billItemId), item.productId?.let { dao.untaggedSalesOfProduct(it) } ?: 0)
            }
        }
    }
    val (item, buyers, untagged) = loaded ?: return
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(word(W.batch_buyers_title, item.productName, item.batchNumber?.takeIf { it.isNotBlank() } ?: "—")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (buyers.isEmpty()) Text(word(W.batch_buyers_none), color = c.textMuted)
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(buyers, key = { it.partyId }) { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Format.name(b.partyName), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                b.phone?.takeIf { it.isNotBlank() }?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 12.sp) }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(b.quantity?.let { Format.qty(it, b.unit) } ?: "—", color = c.ink, fontSize = 14.sp)
                                Text(Format.dateOnly(b.lastAt), color = c.textMuted, fontSize = 12.sp)
                            }
                        }
                        Rule()
                    }
                }
                if (untagged > 0) Text(plural(WP.batch_buyers_untagged, untagged, untagged), color = c.owedToMe, fontSize = 13.sp)
            }
        },
        confirmButton = { FluentButton(word(W.close), onClose, kind = ButtonKind.STANDARD) }
    )
}

/**
 * Trace a batch (the phone's Expiring screen search): which supplier, which
 * bill, how much came in — the ORIGINAL quantity, as an inspector asks it —
 * said as what came, beside what is left by the one rule (Marhala 4, the
 * audit: one bare figure here and another in the list below read as two
 * answers to one question). A row opens who bought from that batch.
 */
@Composable
fun BatchTraceCard(dao: KhataDao, onBuyers: (Long) -> Unit) {
    val c = LocalRoshanColors.current
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<ExpiringBatch>?>(null) }
    var left by remember { mutableStateOf<Map<Long, com.innovation313.roshankhata.data.Fefo.Lot>>(emptyMap()) }
    LaunchedEffect(query) {
        val q = query.trim()
        found = if (q.length < 2) null else {
            kotlinx.coroutines.delay(300)
            withContext(Dispatchers.IO) {
                runCatching { dao.findByBatch(q) }.getOrDefault(emptyList()).also { rows ->
                    left = if (rows.isEmpty()) emptyMap() else runCatching { com.innovation313.roshankhata.data.Fefo.allLots(dao) }.getOrDefault(emptyMap())
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().card().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(word(W.trace_batch), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(word(W.trace_batch_help), color = c.textMuted, fontSize = 12.sp)
        Outlined(query, { query = it }, placeholder = { Text(word(W.trace_batch_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        found?.let { list ->
            if (list.isEmpty()) Text(word(W.trace_no_results), color = c.textMuted, fontSize = 13.sp)
            else Text(plural(WP.trace_results, list.size, list.size), color = c.textMuted, fontSize = 13.sp)
            list.take(20).forEach { b ->
                Row(Modifier.fillMaxWidth().press { onBuyers(b.itemId) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.productName + "  ·  " + word(W.batch_label, b.batchNumber.orEmpty()), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            (b.billNumber?.takeIf { it.isNotBlank() }?.let { word(W.from_supplier_bill, b.partyName, it) } ?: word(W.from_supplier, b.partyName)) +
                                "  ·  " + word(W.trace_came, Format.qty(b.quantity, b.unit)) +
                                (left[b.itemId]?.let { lot -> "  ·  " + word(W.expiry_left, Format.qty(lot.left, lot.unit ?: b.unit)) } ?: ""),
                            color = c.textMuted, fontSize = 12.sp
                        )
                    }
                    Text(word(W.batch_buyers_action), color = c.brandGreenText, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Send part of a batch back to its supplier (the phone's BatchReturnDialog, Marhala 4): written as what it is in
 * the books — goods given on the supplier's own khata, the line tagged to this batch — so the batch's stock drops
 * and what the shop owes him drops by the credit, with no second stock rule. Up to what is left of the batch by
 * the one rule, in the bill line's own unit, at the bill's rate unless the owner changes it.
 */
@Composable
fun BatchReturnDialog(dao: KhataDao, billItemId: Long, onClose: () -> Unit, onDone: (String) -> Unit) {
    val c = LocalRoshanColors.current
    val words = com.innovation313.roshankhata.words.LocalWords.current
    val scope = rememberCoroutineScope()
    var facts by remember(billItemId) { mutableStateOf<ReturnFacts?>(null) }
    var read by remember(billItemId) { mutableStateOf(false) }
    var qty by remember(billItemId) { mutableStateOf("") }
    var rate by remember(billItemId) { mutableStateOf("") }
    var problem by remember(billItemId) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(billItemId) {
        val f = withContext(Dispatchers.IO) {
            runCatching {
                val item = dao.getBillItem(billItemId) ?: return@runCatching null
                val bill = dao.getBill(item.billId) ?: return@runCatching null
                ReturnFacts(item, bill, dao.getParty(bill.partyId)?.name,
                    com.innovation313.roshankhata.data.Fefo.leftInBillUnit(dao, item.id) ?: item.quantity)
            }.getOrNull()
        }
        facts = f
        read = true
        if (f != null) { qty = Calc.trim(f.left); rate = f.item.rate?.let { Calc.trim(it) }.orEmpty() }
    }
    val q = Calc.evalAmount(qty)
    val r = Calc.evalAmount(rate)
    val total = com.innovation313.roshankhata.data.LineMath.lineTotal(q, r)
    val title = word(W.return_title)
    AppDialog(onCloseRequest = onClose, title = title, state = rememberDialogState(width = fitW(500.dp), height = fitH(520.dp))) {
        val f = facts
        DialogFrame(
            title,
            subtitle = f?.let { it.item.productName + (it.item.batchNumber?.takeIf { b -> b.isNotBlank() }?.let { b -> "  ·  " + b } ?: "") },
            onClose = onClose,
            footerStart = { problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) } },
            footer = {
                FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
                if (f != null && f.left > 0.0) FluentButton(word(W.save), {
                    when {
                        q == null || q <= 0.0 || q > f.left + 1e-9 -> problem = words.text(W.return_bad_qty)
                        r == null || r <= 0.0 || total == null || total <= 0.0 -> problem = words.text(W.return_bad_rate)
                        else -> {
                            saving = true
                            scope.launch {
                                val line = com.innovation313.roshankhata.data.EntryItem.ofGoods(f.item.productName, q, f.item.unit, f.item.productId, f.item.id, r)
                                val done = withContext(Dispatchers.IO) {
                                    runCatching {
                                        dao.insertEntryWithItems(
                                            com.innovation313.roshankhata.data.LedgerEntry(
                                                partyId = f.bill.partyId, amount = total, isGiven = true,
                                                note = words.text(W.return_note, f.item.productName, f.item.batchNumber ?: "—"), entryNumber = ""
                                            ),
                                            listOfNotNull(line)
                                        )
                                    }
                                }
                                saving = false
                                done.onSuccess { onDone(words.text(W.return_saved)) }.onFailure { problem = failText(words, it) }
                            }
                        }
                    }
                }, kind = ButtonKind.ACCENT, icon = "ic_check_plain", enabled = !saving)
            }
        ) {
            when {
                !read -> Text("…", color = c.textMuted)
                f == null || f.left <= 0.0 -> Text(word(W.return_nothing_left), color = c.ink, fontSize = 14.sp)
                else -> {
                    Text(word(W.return_info, f.item.productName, f.item.batchNumber ?: "—", Format.qty(f.left, f.item.unit), f.supplier ?: "—"), color = c.inkSoft, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FluentField(qty, { qty = it; problem = null }, Modifier.weight(1f), label = word(W.return_qty_hint), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                        FluentField(rate, { rate = it; problem = null }, Modifier.weight(1f), label = word(W.return_rate_hint),
                            prefix = com.innovation313.roshankhata.data.Currency.symbol, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                    }
                    total?.let { Text(word(W.return_total, Format.money(it)), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    Text(word(W.return_help), color = c.textMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

/** What the return dialog read: the bill line, its bill, the supplier's name and what is left of it. */
private class ReturnFacts(val item: BillItem, val bill: SupplierBill, val supplier: String?, val left: Double)
