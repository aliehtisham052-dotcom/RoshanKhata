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
    DialogWindow(onCloseRequest = onDone, onPreviewKeyEvent = escCloses(onDone), title = word(W.edit_bill), state = rememberDialogState(width = 520.dp, height = 640.dp)) {
        Column(
            Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(word(W.edit_bill), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(Format.name(supplierName), color = c.ink, fontSize = 15.sp)
            Outlined(number, { number = it }, label = { Text(word(W.bill_number_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Outlined(total, { total = it }, label = { Text(word(W.bill_total_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(amount?.let { Format.money(it) } ?: word(W.enter_valid_amount), color = if (amount == null) c.owedToMe else c.textMuted, fontSize = 13.sp)
            TextButton({ picking = 0 }) { Text(word(W.bill_date_set, Format.dateOnly(billDate)), color = c.brandGreenText) }
            TextButton({ picking = 1 }) { Text(dueDate?.let { word(W.due_date_set, Format.dateOnly(it)) } ?: word(W.pick_due_date), color = c.brandGreenText) }
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
    DialogWindow(
        onCloseRequest = onDone, onPreviewKeyEvent = escCloses(onDone),
        title = word(if (existing == null) W.add_item else W.edit_item),
        state = rememberDialogState(width = 560.dp, height = 560.dp)
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
                                b.phone?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textMuted, fontSize = 12.sp) }
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
 * bill, how much came in — the ORIGINAL quantity, as an inspector asks it.
 * A row opens who bought from that batch.
 */
@Composable
fun BatchTraceCard(dao: KhataDao, onBuyers: (Long) -> Unit) {
    val c = LocalRoshanColors.current
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<ExpiringBatch>?>(null) }
    LaunchedEffect(query) {
        val q = query.trim()
        found = if (q.length < 2) null else {
            kotlinx.coroutines.delay(300)
            withContext(Dispatchers.IO) { runCatching { dao.findByBatch(q) }.getOrDefault(emptyList()) }
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
                                "  ·  " + Format.qty(b.quantity, b.unit),
                            color = c.textMuted, fontSize = 12.sp
                        )
                    }
                    Text(word(W.batch_buyers_action), color = c.brandGreenText, fontSize = 13.sp)
                }
            }
        }
    }
}
