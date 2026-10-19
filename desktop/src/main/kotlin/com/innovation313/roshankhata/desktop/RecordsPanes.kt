package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.words.Words
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.BillSummary
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word

/**
 * The phone's records on the computer, to read (KMP Phase 7g): Supplier
 * Bills with what each delivery held, Products, and Invoices — so a book
 * restored from the phone shows all of it. Making new bills and invoices
 * stays on the phone for now: both write stock and numbers, and get their
 * own step with tests.
 */
@Composable
fun BillsPane(dao: KhataDao, parties: List<PartyWithBalance>, words: Words, onLedgerChanged: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val bills by remember { dao.observeBills() }.collectAsState(emptyList())
    var open by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        DialogWindow(onCloseRequest = { adding = false }, title = word(W.add_bill), state = rememberDialogState(width = 640.dp, height = 860.dp)) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                BillForm(
                    parties = parties,
                    onSave = { b ->
                        adding = false
                        scope.launch {
                            withContext(Dispatchers.IO) { saveBill(dao, b, words) }
                            onLedgerChanged()
                        }
                    },
                    onCancel = { adding = false }
                )
            }
        }
    }
    RecordList(word(W.supplier_bills), word(W.bill_ledger_note), bills.isEmpty(), word(W.no_bills), action = word(W.add_bill) to { adding = true }) {
        items(bills, key = { it.id }) { b ->
            Column(Modifier.fillMaxWidth().clickable { open = if (open == b.id) null else b.id }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.name(b.partyName), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(b.billNumber?.takeIf { it.isNotBlank() }?.let { "#$it" }, Format.dateOnly(b.billDate)).joinToString("  ·  "),
                            color = c.textMuted, fontSize = 13.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Format.money(b.totalAmount), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        BillState(b)
                    }
                }
                if (open == b.id) {
                    val lines by remember(b.id) { dao.observeBillItems(b.id) }.collectAsState(emptyList())
                    Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(c.pageBg, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        lines.forEach { l ->
                            val detail = listOfNotNull(
                                Format.qty(l.quantity, l.unit),
                                l.rate?.let { "@ " + Format.money(it) },
                                l.batchNumber?.takeIf { it.isNotBlank() }?.let { word(W.batch_label, it) },
                                l.expiryDate?.let { word(W.expiry_date) + ": " + Format.dateOnly(it) }
                            ).joinToString("  ·  ")
                            Text(l.productName, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(detail, color = c.inkSoft, fontSize = 12.sp)
                        }
                    }
                }
            }
            HorizontalDivider(color = c.pageBg)
        }
    }
}

/** The supplier's live balance, not a stored flag: a payment in the khata shows here at once. */
@Composable
private fun BillState(b: BillSummary) {
    val c = LocalRoshanColors.current
    if (Money.isNegative(b.supplierBalance)) {
        val overdue = b.dueDate?.let { it < System.currentTimeMillis() } == true
        Text(
            word(if (overdue) W.bill_owe_overdue else W.bill_owe_amount, Format.money(-b.supplierBalance)),
            color = c.owedToMe, fontSize = 13.sp
        )
    } else {
        Text(word(W.bill_account_settled), color = c.iOwe, fontSize = 13.sp)
    }
}

@Composable
fun ProductsPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val products by remember { dao.observeProducts() }.collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = products.filter { NameSearch.matches(it.name, it.company, q) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(word(W.tile_products)) },
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)
        )
        RecordList(word(W.tile_products), null, shown.isEmpty(), word(W.no_products)) {
            items(shown, key = { it.id }) { p ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(p.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val sub = listOfNotNull(p.company, p.category, p.defaultUnit).filter { it.isNotBlank() }
                    if (sub.isNotEmpty()) Text(sub.joinToString("  ·  "), color = c.textMuted, fontSize = 13.sp)
                }
                HorizontalDivider(color = c.pageBg)
            }
        }
    }
}

@Composable
fun InvoicesPane(dao: KhataDao, parties: List<PartyWithBalance>, words: Words, rtl: Boolean, shopName: String?, shopLine: String?) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val invoices by remember { dao.observeInvoices() }.collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    // Print an invoice by id: the saved row and its lines, as the customer will hold them.
    fun print(id: Long) {
        scope.launch {
            val pair = withContext(Dispatchers.IO) { dao.getInvoice(id)?.let { it to dao.invoiceItems(id) } } ?: return@launch
            StatementPrint.show(
                InvoicePrint(words, rtl, shopName ?: words.text(W.app_name), shopLine, pair.first, pair.second),
                pair.first.invoiceNumber
            )
        }
    }
    if (adding) {
        DialogWindow(onCloseRequest = { adding = false }, title = word(W.add_invoice), state = rememberDialogState(width = 720.dp, height = 860.dp)) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                InvoiceForm(
                    parties = parties,
                    onSave = { inv, lines ->
                        adding = false
                        scope.launch {
                            val id = withContext(Dispatchers.IO) { dao.saveInvoiceWithItems(inv, lines) }
                            print(id)
                        }
                    },
                    onCancel = { adding = false }
                )
            }
        }
    }
    RecordList(word(W.nav_invoice), null, invoices.isEmpty(), word(W.no_invoices), action = word(W.add_invoice) to { adding = true }) {
        items(invoices, key = { it.id }) { i ->
            Row(Modifier.fillMaxWidth().clickable { print(i.id) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Format.name(i.customerName), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(i.invoiceNumber + "  \u00b7  " + Format.dateOnly(i.invoiceDate), color = c.textMuted, fontSize = 13.sp)
                }
                Text(Format.money(i.grandTotal), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("  " + word(W.ledger_pdf_button), color = c.brandGreenText, fontSize = 13.sp)
            }
            HorizontalDivider(color = c.pageBg)
        }
    }
}

/** A titled list on a rounded card, or the empty line when there is nothing yet. */
@Composable
private fun RecordList(
    title: String,
    subtitle: String?,
    empty: Boolean,
    emptyText: String,
    action: Pair<String, () -> Unit>? = null,
    rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val c = LocalRoshanColors.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PaneTitle(title, subtitle) }
            action?.let { (label, go) -> FillButton(label, c.brandGreen, onClick = go) }
        }
        if (empty) {
            Text(emptyText, Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(14.dp)), content = rows)
        }
    }
}

/** Stock expiring within the phone's warning window (ExpiryWindow), soonest first; expired in red. */
@Composable
fun ExpiryPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val cutoff = remember { System.currentTimeMillis() + com.innovation313.roshankhata.data.ExpiryWindow.WARN_MS }
    val batches by remember { dao.observeExpiringBatches(cutoff) }.collectAsState(emptyList())
    RecordList(word(W.expiring_stock), word(W.expiring_subtitle), batches.isEmpty(), word(W.no_expiring)) {
        items(batches, key = { it.itemId }) { b ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.productName, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        (b.batchNumber?.takeIf { it.isNotBlank() }?.let { word(W.batch_label, it) } ?: word(W.batch_none)) +
                            "  ·  " + Format.qty(b.quantity, b.unit),
                        color = c.inkSoft, fontSize = 13.sp
                    )
                    Text(
                        b.billNumber?.takeIf { it.isNotBlank() }?.let { word(W.from_supplier_bill, b.partyName, it) }
                            ?: word(W.from_supplier, b.partyName),
                        color = c.textMuted, fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        when {
                            b.hasExpired -> plural(WP.expired_days, -b.daysLeft, -b.daysLeft)
                            b.daysLeft == 0 -> word(W.expires_today)
                            else -> plural(WP.expires_days, b.daysLeft, b.daysLeft)
                        },
                        color = if (b.daysLeft <= 0) c.owedToMe else c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                    Text(Format.dateOnly(b.expiryDate), color = c.textMuted, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = c.pageBg)
        }
    }
}

/** Payment plans: paid of total, what is left, the next date (overdue in red); complete ones faded. */
@Composable
fun PlansPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val plans by remember { dao.observePlans() }.collectAsState(emptyList())
    RecordList(word(W.payment_plans), word(W.plans_subtitle), plans.isEmpty(), word(W.no_plans)) {
        items(plans, key = { it.id }) { p ->
            Column(
                Modifier.fillMaxWidth().alpha(if (p.isClosed) 0.6f else 1f).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.name(p.partyName), Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(word(if (p.isClosed) W.plan_closed else W.plan_open), color = if (p.isClosed) c.iOwe else c.ink, fontSize = 13.sp)
                }
                LinearProgressIndicator(
                    progress = { p.percentPaid / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = c.brandGreen,
                    trackColor = c.pageBg
                )
                Text(
                    word(W.plan_paid, Format.money(p.paidSoFar), Format.money(p.totalAmount)) + "  ·  " +
                        word(W.plan_remaining, Format.money(p.remaining)),
                    color = c.inkSoft, fontSize = 13.sp
                )
                if (!p.isClosed) {
                    val due = p.nextDueDate
                    val overdue = due != null && due < System.currentTimeMillis()
                    Text(
                        when {
                            due == null -> word(W.plan_next_due_none)
                            overdue -> word(W.plan_overdue, Format.dateOnly(due))
                            else -> word(W.plan_next_due, Format.dateOnly(due))
                        },
                        color = if (overdue) c.owedToMe else c.textMuted, fontSize = 12.sp
                    )
                }
            }
            HorizontalDivider(color = c.pageBg)
        }
    }
}

/**
 * The phone's bill save (BillsActivity.dispatchSaveBill) on the computer: a
 * new supplier is added first (or one of the same name reused), then the
 * debt, the bill and its lines go in as ONE transaction
 * (KhataDao.insertSupplierBill). On credit the khata gets one "I got" entry
 * (isGiven = false: the shop owes the supplier); paid in cash, nothing.
 */
suspend fun saveBill(dao: KhataDao, b: NewBill, words: Words) {
    val partyId = b.supplierId
        ?: dao.findPartyByName(b.newSupplierName.orEmpty())?.id
        ?: dao.insertParty(Party(name = b.newSupplierName.orEmpty().trim(), phone = null, isCustomer = false))
    dao.insertSupplierBill(
        entry = if (b.paidCash) null else LedgerEntry(
            partyId = partyId,
            amount = b.total,
            isGiven = false,
            note = b.billNumber?.let { words.text(W.bill_entry_note, it) } ?: b.note,
            entryNumber = ""
        ),
        bill = SupplierBill(
            partyId = partyId,
            billNumber = b.billNumber,
            totalAmount = b.total,
            billDate = b.billDate,
            dueDate = b.dueDate,
            ledgerEntryId = null,
            isPaidInFull = b.paidCash,
            note = b.note
        ),
        items = b.items
    )
}
