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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
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
fun BillsPane(dao: KhataDao, parties: List<PartyWithBalance>, words: Words, home: java.io.File, onLedgerChanged: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val bills by remember { dao.observeBills() }.collectAsState(emptyList())
    var open by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deletingBill by remember { mutableStateOf<BillSummary?>(null) }
    var editingBill by remember { mutableStateOf<BillSummary?>(null) }
    // A bill line being added (id 0 on a bill) or corrected, and a line whose buyers are asked for.
    var lineFor by remember { mutableStateOf<Pair<Long, com.innovation313.roshankhata.data.BillItem?>?>(null) }
    var deletingLine by remember { mutableStateOf<com.innovation313.roshankhata.data.BillItem?>(null) }
    var buyersOf by remember { mutableStateOf<Long?>(null) }
    // A bill's photo: on a credit bill it rides on its ledger entry (billPhotoPath), on a cash bill on the bill.
    var photoOf by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var photoVersion by remember { mutableStateOf(0) }
    photoOf?.let { (title, path) -> PhotoDialog(title, path) { photoOf = null } }
    suspend fun billPhoto(id: Long): String? = withContext(Dispatchers.IO) {
        dao.getBill(id)?.let { bill -> bill.photoPath ?: bill.ledgerEntryId?.let { dao.getEntry(it)?.billPhotoPath } }
    }
    val attachTitle = word(W.add_bill_photo)
    val billLabel = word(W.bill_photo_label)
    fun attach(b: BillSummary) {
        val src = Photos.pick(attachTitle) ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                val target = Photos.billFile(home)
                if (Photos.save(src, target)) {
                    val bill = dao.getBill(b.id) ?: return@withContext
                    val entryId = bill.ledgerEntryId
                    if (entryId != null) dao.setBillPhotoPath(entryId, target.absolutePath) else dao.setSupplierBillPhotoPath(b.id, target.absolutePath)
                }
            }
            photoVersion++
        }
    }
    editingBill?.let { b -> BillEditDialog(dao, words, b.id, b.partyName) { editingBill = null; onLedgerChanged() } }
    lineFor?.let { (billId, line) -> BillLineDialog(dao, billId, line) { lineFor = null } }
    buyersOf?.let { BatchBuyersDialog(dao, it) { buyersOf = null } }
    deletingLine?.let { l ->
        // Soft, like everywhere: the batch record stays the answer to "which batch was this sale from".
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingLine = null },
            title = { Text(word(W.delete_item)) },
            text = { Text(word(W.delete_item_confirm, l.productName)) },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    deletingLine = null
                    scope.launch { withContext(Dispatchers.IO) { dao.softDeleteBillItem(l.id) } }
                }) { Text(word(W.delete), color = c.owedToMe) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ deletingLine = null }) { Text(word(W.cancel)) } }
        )
    }
    deletingBill?.let { b ->
        // A credit bill's own "I owe them" entry goes to the Recycle Bin with it (the phone's rule).
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingBill = null },
            title = { Text(word(W.delete_bill)) },
            text = { Text(Format.name(b.partyName) + "  \u00b7  " + Format.money(b.totalAmount) + (b.billNumber?.let { "  \u00b7  #$it" } ?: "")) },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    deletingBill = null; open = null
                    scope.launch { withContext(Dispatchers.IO) { dao.deleteSupplierBill(b.id) }; onLedgerChanged() }
                }) { Text(word(W.delete), color = c.owedToMe) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ deletingBill = null }) { Text(word(W.cancel)) } }
        )
    }
    if (adding) {
        DialogWindow(onCloseRequest = { adding = false },onPreviewKeyEvent = escCloses { adding = false }, title = word(W.add_bill), state = rememberDialogState(width = 640.dp, height = 860.dp)) {
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
                    Row {
                        androidx.compose.material3.TextButton({ editingBill = b }) { Text(word(W.edit_bill), color = c.brandGreenText) }
                        androidx.compose.material3.TextButton({ lineFor = b.id to null }) { Text(word(W.add_item), color = c.brandGreenText) }
                        androidx.compose.material3.TextButton({ deletingBill = b }) { Text(word(W.delete_bill), color = c.owedToMe) }
                    }
                    var path by remember(b.id, photoVersion) { mutableStateOf<String?>(null) }
                    androidx.compose.runtime.LaunchedEffect(b.id, photoVersion) { path = billPhoto(b.id) }
                    Row {
                        if (path != null) androidx.compose.material3.TextButton({ photoOf = billLabel to path }) {
                            Text(word(W.view_bill_photo), color = c.brandGreenText)
                        }
                        androidx.compose.material3.TextButton({ attach(b) }) {
                            Text(word(if (path == null) W.add_bill_photo else W.bill_photo_attached), color = c.brandGreenText)
                        }
                    }
                    val lines by remember(b.id) { dao.observeBillItems(b.id) }.collectAsState(emptyList())
                    Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(c.pageBg, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        lines.forEach { l ->
                            val detail = listOfNotNull(
                                Format.qty(l.quantity, l.unit),
                                l.rate?.let { "@ " + Format.money(it) },
                                l.batchNumber?.takeIf { it.isNotBlank() }?.let { word(W.batch_label, it) },
                                l.expiryDate?.let { word(W.expiry_date) + ": " + Format.dateOnly(it) }
                            ).joinToString("  ·  ")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.productName, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(detail, color = c.inkSoft, fontSize = 12.sp)
                                }
                                androidx.compose.material3.TextButton({ buyersOf = l.id }) { Text(word(W.batch_buyers_action), color = c.brandGreenText, fontSize = 12.sp) }
                                androidx.compose.material3.TextButton({ lineFor = b.id to l }) { Text(word(W.edit), color = c.brandGreenText, fontSize = 12.sp) }
                                androidx.compose.material3.TextButton({ deletingLine = l }) { Text(word(W.delete), color = c.owedToMe, fontSize = 12.sp) }
                            }
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
fun ProductsPane(dao: KhataDao, words: Words) {
    val c = LocalRoshanColors.current
    val products by remember { dao.observeProducts() }.collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = products.filter { NameSearch.matches(it.name, it.company, q) }
    var buyersOf by remember { mutableStateOf<com.innovation313.roshankhata.data.Product?>(null) }
    buyersOf?.let { ProductBuyers(dao, words, it) { buyersOf = null } }
    var editing by remember { mutableStateOf<com.innovation313.roshankhata.data.Product?>(null) }
    editing?.let { ProductEditDialog(dao, it) { editing = null } }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(word(W.tile_products)) },
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)
        )
        RecordList(word(W.tile_products), null, shown.isEmpty(), word(W.no_products)) {
            items(shown, key = { it.id }) { p ->
                Column(Modifier.fillMaxWidth().clickable { buyersOf = p }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            val sub = listOfNotNull(p.company, p.category, p.defaultUnit).filter { it.isNotBlank() }
                            if (sub.isNotEmpty()) Text(sub.joinToString("  ·  "), color = c.textMuted, fontSize = 13.sp)
                        }
                        p.salePrice?.let { Text(Format.money(it), color = c.ink, fontSize = 14.sp) }
                        androidx.compose.material3.TextButton({ editing = p }) { Text(word(W.edit), color = c.brandGreenText) }
                    }
                }
                HorizontalDivider(color = c.pageBg)
            }
        }
    }
}

@Composable
fun InvoicesPane(
    dao: KhataDao,
    parties: List<PartyWithBalance>,
    words: Words,
    rtl: Boolean,
    shopName: String?,
    shopLine: String?,
    /** What the phone's ten designs draw with: the words, the shop and its pictures (Phase 10). */
    designs: () -> com.innovation313.roshankhata.desktop.invoice.Context,
    /** The optional invoice fields the shop uses, and the switches to change them (Invoice settings). */
    fields: InvoiceFields = InvoiceFields(),
    onFields: (InvoiceFields) -> Unit = {}
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val invoices by remember { dao.observeInvoices() }.collectAsState(emptyList())
    var settingFields by remember { mutableStateOf(false) }
    if (settingFields) {
        DialogWindow(onCloseRequest = { settingFields = false },onPreviewKeyEvent = escCloses { settingFields = false }, title = word(W.invoice_settings_title), state = rememberDialogState(width = 520.dp, height = 640.dp)) {
            InvoiceFieldsSettings(fields, onFields)
        }
    }
    var adding by remember { mutableStateOf(false) }
    // A saved invoice opened to correct it: the row and its live lines.
    var editingInvoice by remember { mutableStateOf<Pair<com.innovation313.roshankhata.data.Invoice, List<com.innovation313.roshankhata.data.InvoiceItem>>?>(null) }
    var deletingInvoice by remember { mutableStateOf<com.innovation313.roshankhata.data.InvoiceSummary?>(null) }
    deletingInvoice?.let { inv ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingInvoice = null },
            text = { Text(inv.invoiceNumber + "  \u00b7  " + word(W.delete_invoice_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    deletingInvoice = null
                    scope.launch { withContext(Dispatchers.IO) { dao.softDeleteInvoice(inv.id) } }
                }) { Text(word(W.delete), color = c.owedToMe) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ deletingInvoice = null }) { Text(word(W.cancel)) } }
        )
    }
    // Print an invoice by id: the saved row and its lines, as the customer will hold them.
    fun print(id: Long) {
        scope.launch {
            val pair = withContext(Dispatchers.IO) { dao.getInvoice(id)?.let { it to dao.invoiceItems(id) } } ?: return@launch
            // The invoice in the design it was made with — the phone's own drawing; the plain
            // layout only if a design could not be drawn.
            val doc = withContext(Dispatchers.IO) { com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), pair.first, pair.second) }
            StatementPrint.show(
                doc?.let { com.innovation313.roshankhata.desktop.invoice.DocumentPrint(it) }
                    ?: InvoicePrint(words, rtl, shopName ?: words.text(W.app_name), shopLine, pair.first, pair.second),
                pair.first.invoiceNumber
            )
        }
    }
    if (adding) {
        DialogWindow(onCloseRequest = { adding = false },onPreviewKeyEvent = escCloses { adding = false }, title = word(W.add_invoice), state = rememberDialogState(width = 720.dp, height = 860.dp)) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                InvoiceForm(
                    parties = parties,
                    preview = { inv, lines -> com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), inv, lines)?.picture(0, 0.8f) },
                    fields = fields,
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
    editingInvoice?.let { (inv, lines) ->
        DialogWindow(onCloseRequest = { editingInvoice = null },onPreviewKeyEvent = escCloses { editingInvoice = null }, title = word(W.edit), state = rememberDialogState(width = 720.dp, height = 860.dp)) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                InvoiceForm(
                    parties = parties,
                    preview = { i, l -> com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), i, l)?.picture(0, 0.8f) },
                    existing = inv,
                    existingLines = lines,
                    fields = fields,
                    onSave = { updated, newLines ->
                        editingInvoice = null
                        scope.launch {
                            // The phone's edit: the invoice and its whole line list, in one transaction.
                            withContext(Dispatchers.IO) { dao.updateInvoiceWithItems(updated.copy(id = inv.id), newLines) }
                            print(inv.id)
                        }
                    },
                    onCancel = { editingInvoice = null }
                )
            }
        }
    }
    // List and page side by side (20 Oct redesign): a click opens the invoice in
    // its own design beside the list, with its actions; it no longer prints at once.
    var chosen by remember { mutableStateOf<Long?>(null) }
    var monthOnly by remember { mutableStateOf(false) }
    val monthStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val thisMonth = invoices.filter { it.invoiceDate >= monthStart }
    val shown = if (monthOnly) thisMonth else invoices
    val open = shown.firstOrNull { it.id == chosen } ?: shown.firstOrNull()
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileBadge(Section.INVOICES, 48, 28)
            androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
            Text(word(W.nav_invoice), Modifier.weight(1f), color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            OutlineAction(onClick = { settingFields = true }) {
                Text(word(W.invoice_settings_title), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            FillButton("+  " + word(W.add_invoice), c.brandGreen) { adding = true }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SumCard(word(W.range_this_month), Format.money(thisMonth.sumOf { it.grandTotal }), thisMonth.size.toString(), Modifier.weight(1f))
            SumCard(word(W.range_all), Format.money(invoices.sumOf { it.grandTotal }), invoices.size.toString(), Modifier.weight(1f))
        }
        if (invoices.isEmpty()) {
            Text(word(W.no_invoices), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
            return@Column
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(
                Modifier.weight(0.55f).fillMaxSize().clip(RoundedCornerShape(20.dp)).background(c.surface)
                    .border(1.dp, Look.line, RoundedCornerShape(20.dp))
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(word(W.range_all) + " \u00b7 " + invoices.size, !monthOnly) { monthOnly = false }
                    Chip(word(W.range_this_month) + " \u00b7 " + thisMonth.size, monthOnly) { monthOnly = true }
                }
                HorizontalDivider(color = Look.line)
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { i ->
                        val on = i.id == open?.id
                        Row(
                            Modifier.fillMaxWidth().background(if (on) Look.chosen else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { chosen = i.id }.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Look.chosen), contentAlignment = Alignment.Center) {
                                Text(Format.initials(i.customerName), color = c.brandGreenText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Format.name(i.customerName), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(Format.ltr(i.invoiceNumber) + "  \u00b7  " + Format.dateOnly(i.invoiceDate), color = c.textMuted, fontSize = 12.sp)
                            }
                            Text(Format.money(i.grandTotal), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = Look.line)
                    }
                }
            }
            Column(
                Modifier.weight(0.45f).fillMaxSize().clip(RoundedCornerShape(20.dp)).background(c.surface)
                    .border(1.dp, Look.line, RoundedCornerShape(20.dp))
                    .verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (open != null) {
                    // The saved row (what was received, if the owner recorded it) and its picture in its own design.
                    var full by remember(open.id) { mutableStateOf<com.innovation313.roshankhata.data.Invoice?>(null) }
                    var picture by remember(open.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    LaunchedEffect(open.id, open.grandTotal, open.invoiceDate) {
                        withContext(Dispatchers.IO) {
                            val inv = dao.getInvoice(open.id)
                            full = inv
                            picture = inv?.let { row ->
                                runCatching {
                                    com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), row, dao.invoiceItems(open.id))
                                        ?.picture(0, 1f)?.toComposeImageBitmap()
                                }.getOrNull()
                            }
                        }
                    }
                    Text(Format.ltr(open.invoiceNumber) + "  \u00b7  " + Format.dateOnly(open.invoiceDate), color = c.textMuted, fontSize = 12.sp)
                    Text(Format.name(open.customerName), color = c.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    // Received is shown only when the owner recorded it on the invoice: nothing
                    // recorded is not the same as nothing paid (Invoice.receivedAmount).
                    full?.receivedAmount?.let { got ->
                        Text(word(W.invoice_received_line, Format.money(got)), color = c.iOwe, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val due = open.grandTotal - got
                        if (Money.isPositive(due)) Text(word(W.invoice_balance_due_line, Format.money(due)), color = c.owedToMe, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FillButton(word(W.ledger_pdf_button), c.brandGreen) { print(open.id) }
                        OutlineAction(onClick = {
                            scope.launch { editingInvoice = withContext(Dispatchers.IO) { dao.getInvoice(open.id)?.let { it to dao.invoiceItems(open.id) } } }
                        }) { Text(word(W.edit), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                        androidx.compose.material3.TextButton({ deletingInvoice = open }) { Text(word(W.delete), color = c.owedToMe) }
                    }
                    val pic = picture
                    if (pic == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        androidx.compose.foundation.Image(
                            pic, contentDescription = open.invoiceNumber,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Look.line, RoundedCornerShape(10.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                        )
                    }
                }
            }
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
    var buyersOf by remember { mutableStateOf<Long?>(null) }
    buyersOf?.let { BatchBuyersDialog(dao, it) { buyersOf = null } }
    RecordList(word(W.expiring_stock), word(W.expiring_subtitle), false, word(W.no_expiring)) {
        item(key = "trace") { BatchTraceCard(dao) { buyersOf = it } }
        if (batches.isEmpty()) item(key = "none") { Text(word(W.no_expiring), Modifier.padding(16.dp), color = c.textMuted, fontSize = 15.sp) }
        items(batches, key = { it.itemId }) { b ->
            Row(Modifier.fillMaxWidth().clickable { buyersOf = b.itemId }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
fun PlansPane(dao: KhataDao, parties: List<PartyWithBalance>, words: Words, onLedgerChanged: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val plans by remember { dao.observePlans() }.collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var acting by remember { mutableStateOf<com.innovation313.roshankhata.data.PlanProgress?>(null) }
    var paying by remember { mutableStateOf<com.innovation313.roshankhata.data.PlanProgress?>(null) }
    var deleting by remember { mutableStateOf<com.innovation313.roshankhata.data.PlanProgress?>(null) }
    if (adding) PlanAddDialog(parties, onSave = { plan ->
        adding = false
        scope.launch { withContext(Dispatchers.IO) { dao.insertPlan(plan) } }
    }, onCancel = { adding = false })
    acting?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { acting = null },
            title = { Text(if (p.isClosed) Format.name(p.partyName) else word(W.plan_actions)) },
            text = {
                Column {
                    val actions = buildList<Pair<String, () -> Unit>> {
                        if (!p.isClosed) {
                            add(word(W.record_payment) to { paying = p })
                            add(word(W.close_plan) to {
                                scope.launch { withContext(Dispatchers.IO) { dao.getPlan(p.id)?.let { dao.updatePlan(it.copy(isClosed = true, closedAt = System.currentTimeMillis())) } } }
                            })
                        }
                        add(word(W.delete_plan) to { deleting = p })
                    }
                    actions.forEach { (label, go) ->
                        Text(label, Modifier.fillMaxWidth().clickable { acting = null; go() }.padding(vertical = 12.dp), color = c.ink, fontSize = 16.sp)
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ acting = null }) { Text(word(W.cancel)) } }
        )
    }
    paying?.let { p ->
        var amountText by remember(p.id) { mutableStateOf(p.installmentAmount?.let { com.innovation313.roshankhata.ui.Calc.trim(it) }.orEmpty()) }
        var note by remember(p.id) { mutableStateOf("") }
        val amount = com.innovation313.roshankhata.ui.Calc.evalAmount(amountText)
        val over = amount != null && amount > p.remaining
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { paying = null },
            title = { Text(word(W.record_payment_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(word(W.record_payment_help, p.partyName), color = c.textMuted, fontSize = 13.sp)
                    androidx.compose.material3.OutlinedTextField(amountText, { amountText = it }, label = { Text(word(W.amount)) }, singleLine = true)
                    Text(amount?.let { Format.money(it) } ?: "", color = c.textMuted)
                    androidx.compose.material3.OutlinedTextField(note, { note = it }, label = { Text(word(W.note_optional)) }, singleLine = true)
                    // More than is left: said before Save, still posted in full if the owner goes ahead.
                    if (over) Text(word(W.plan_overpay_warning, Format.money(p.remaining)), color = c.owedToMe, fontSize = 13.sp)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = amount != null, onClick = {
                    val a = amount ?: return@TextButton
                    val entryNote = note.trim().ifEmpty { words.text(W.plan_note_prefix) }
                    paying = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.recordPlanPayment(p.id, p.partyId, a, p.paidSoFar, entryNote, note.trim().ifEmpty { null })
                        }
                        onLedgerChanged()
                    }
                }) { Text(word(if (over) W.proceed_anyway else W.save)) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ paying = null }) { Text(word(W.cancel)) } }
        )
    }
    deleting?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(word(W.delete_plan)) },
            text = { Text(word(W.delete_plan_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    deleting = null
                    scope.launch { withContext(Dispatchers.IO) { dao.softDeletePlan(p.id) } }
                }) { Text(word(W.delete), color = c.owedToMe) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ deleting = null }) { Text(word(W.cancel)) } }
        )
    }
    RecordList(word(W.payment_plans), word(W.plans_subtitle), plans.isEmpty(), word(W.no_plans), action = word(W.add_plan) to { adding = true }) {
        items(plans, key = { it.id }) { p ->
            Column(
                Modifier.fillMaxWidth().alpha(if (p.isClosed) 0.6f else 1f).clickable { acting = p }.padding(horizontal = 16.dp, vertical = 12.dp),
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

/**
 * Purchase rates (KMP Phase 7s), the phone's SupplierRatesActivity on the
 * shared SupplierRates: what each supplier charged, product by product, the
 * change on the bill before, and a cheaper supplier lately. "Needs a look"
 * first; All shows everything.
 */
@Composable
fun SupplierRatesPane(dao: KhataDao, reload: Int) {
    val c = LocalRoshanColors.current
    var rows by remember { mutableStateOf<List<com.innovation313.roshankhata.data.SupplierRates.Row>>(emptyList()) }
    var watchOnly by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(reload) {
        rows = withContext(Dispatchers.IO) {
            com.innovation313.roshankhata.data.SupplierRates.rows(dao.supplierRateLinesOnce(), System.currentTimeMillis())
        }
    }
    val shown = if (watchOnly) rows.filter { it.needsLook } else rows
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 20.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(true to W.sr_watch, false to W.filter_type_all).forEach { (watch, label) ->
                if (watch == watchOnly) FillButton(word(label), c.brandGreen) { watchOnly = watch }
                else androidx.compose.material3.TextButton({ watchOnly = watch }) { Text(word(label), color = c.brandGreenText) }
            }
        }
        RecordList(word(W.sr_title), word(W.sr_subtitle), shown.isEmpty(), word(if (rows.isEmpty()) W.sr_none else W.sr_nothing_to_watch)) {
            items(shown) { r ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val unit = com.innovation313.roshankhata.data.UnitWords.label(r.unit).takeIf { it.isNotEmpty() }
                    Text(listOfNotNull(r.name, unit).joinToString(" · "), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(word(W.sr_last, Format.money(r.last.rate), r.last.supplierName, com.innovation313.roshankhata.data.DateWords.format("d MMM yyyy", r.last.date)),
                        color = c.inkSoft, fontSize = 13.sp)
                    val pct = String.format(java.util.Locale.US, "%.1f%%", kotlin.math.abs(r.percent))
                    if (r.previous != null && r.change > 0.004) Text(word(W.sr_up, Format.money(r.change), pct), color = c.owedToMe, fontSize = 13.sp)
                    if (r.previous != null && r.change < -0.004) Text(word(W.sr_down, Format.money(-r.change), pct), color = c.iOwe, fontSize = 13.sp)
                    r.cheaperElsewhere?.let { q ->
                        Text(word(W.sr_cheaper, q.supplierName, Format.money(q.rate), com.innovation313.roshankhata.data.DateWords.format("d MMM yyyy", q.date)),
                            color = c.brandGreenText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = c.pageBg)
            }
        }
    }
}

/**
 * Who bought a product (KMP Phase 7w), the phone's ProductCustomersActivity:
 * the same season last year, the last twelve months, or ever (shared
 * SeasonWindow), and Message opens WhatsApp with the phone's "available
 * again" note for that customer, sent only when the owner sends it.
 */
@Composable
private fun ProductBuyers(dao: KhataDao, words: Words, product: com.innovation313.roshankhata.data.Product, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    var choice by remember(product.id) { mutableStateOf(0) }
    var buyers by remember(product.id) { mutableStateOf<List<PartyWithBalance>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(product.id, choice) {
        val now = System.currentTimeMillis()
        val w = when (choice) {
            0 -> com.innovation313.roshankhata.data.SeasonWindow.sameSeasonLastYear(now)
            1 -> com.innovation313.roshankhata.data.SeasonWindow.lastTwelveMonths(now)
            else -> com.innovation313.roshankhata.data.SeasonWindow.everything(now)
        }
        buyers = withContext(Dispatchers.IO) { dao.customersWhoBought(product.id, w.from, w.to) }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(product.name + "  \u00b7  " + word(W.promo_subtitle)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(W.window_same_season, W.window_last_year, W.window_ever).forEachIndexed { i, label ->
                        if (i == choice) FillButton(word(label), c.brandGreen) { choice = i }
                        else androidx.compose.material3.TextButton({ choice = i }) { Text(word(label), color = c.brandGreenText) }
                    }
                }
                if (buyers.isEmpty()) Text(word(W.no_promo_customers), color = c.textMuted)
                else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(buyers, key = { it.id }) { b ->
                        val number = b.phone?.let { com.innovation313.roshankhata.data.WhatsAppNumber.of(it) }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Format.name(b.name), color = c.ink, fontWeight = FontWeight.Bold)
                                b.phone?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 12.sp) }
                            }
                            androidx.compose.material3.TextButton(enabled = number != null, onClick = {
                                val text = words.text(W.promo_message, b.name, product.name)
                                runCatching {
                                    java.awt.Desktop.getDesktop().browse(java.net.URI("https://wa.me/$number?text=" +
                                        java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")))
                                }
                            }) { Text(word(W.promo_send), color = c.brandGreenText) }
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClose) { Text(word(W.ok)) } }
    )
}

/** A new plan: who (from the book), the total to clear, each instalment, the next date, a note. Adds nothing to the balance. */
@Composable
private fun PlanAddDialog(parties: List<PartyWithBalance>, onSave: (com.innovation313.roshankhata.data.PaymentPlan) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    var search by remember { mutableStateOf("") }
    var partyId by remember { mutableStateOf<Long?>(null) }
    var total by remember { mutableStateOf("") }
    var each by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val needParty = word(W.select_party_first)
    val needAmount = word(W.enter_valid_amount)
    DialogWindow(onCloseRequest = onCancel, onPreviewKeyEvent = escCloses(onCancel), title = word(W.add_plan), state = rememberDialogState(width = 520.dp, height = 680.dp)) {
        Column(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word(W.plan_dialog_help), color = c.textMuted, fontSize = 12.sp)
            val chosen = parties.firstOrNull { it.id == partyId }
            if (chosen != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.name(chosen.name) + "  \u00b7  " + Format.money(chosen.balance), Modifier.weight(1f), color = c.ink, fontWeight = FontWeight.Bold)
                    androidx.compose.material3.TextButton({ partyId = null }) { Text(word(W.edit), color = c.brandGreenText) }
                }
            } else {
                androidx.compose.material3.OutlinedTextField(search, { search = it }, label = { Text(word(W.cheque_party_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val q = search.trim().lowercase()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp).background(c.pageBg, RoundedCornerShape(10.dp))) {
                    items(parties.filter { NameSearch.matches(it.name, it.phone, q) }.take(40), key = { it.id }) { p ->
                        Text(Format.name(p.name) + "  \u00b7  " + Format.money(p.balance),
                            Modifier.fillMaxWidth().clickable { partyId = p.id; problem = null }.padding(horizontal = 12.dp, vertical = 8.dp), color = c.ink, fontSize = 14.sp)
                    }
                }
            }
            androidx.compose.material3.OutlinedTextField(total, { total = it; problem = null }, label = { Text(word(W.plan_total_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            androidx.compose.material3.OutlinedTextField(each, { each = it }, label = { Text(word(W.plan_installment_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton({ picking = true }) { Text(word(W.plan_pick_next_due), color = c.brandGreenText) }
                Text(due?.let { Format.dateOnly(it) } ?: "", color = c.ink, fontWeight = FontWeight.Bold)
            }
            androidx.compose.material3.OutlinedTextField(note, { note = it }, label = { Text(word(W.note_optional)) }, modifier = Modifier.fillMaxWidth())
            problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                androidx.compose.material3.TextButton(onCancel) { Text(word(W.cancel)) }
                FillButton(word(W.save), c.brandGreen) {
                    val pid = partyId
                    val t = com.innovation313.roshankhata.ui.Calc.evalAmount(total)
                    when {
                        pid == null -> problem = needParty
                        t == null -> problem = needAmount
                        else -> onSave(
                            com.innovation313.roshankhata.data.PaymentPlan(
                                partyId = pid, totalAmount = t,
                                installmentAmount = com.innovation313.roshankhata.ui.Calc.evalAmount(each),
                                nextDueDate = due, note = note.trim().ifEmpty { null }
                            )
                        )
                    }
                }
            }
        }
        if (picking) DayPicker(due, onPick = { due = it; picking = false }, onDismiss = { picking = false })
    }
}

/** A figure on a white card: what it is, the amount, and how many. */
@Composable
private fun SumCard(label: String, amount: String, count: String, modifier: Modifier) {
    val c = LocalRoshanColors.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, Look.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(label + "  \u00b7  " + count, color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(amount, Modifier.padding(top = 6.dp), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** A filter pill, filled when on. */
@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Text(
        text,
        Modifier.clip(RoundedCornerShape(17.dp))
            .background(if (on) c.brandGreen else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (on) c.brandGreen else Look.line, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (on) androidx.compose.ui.graphics.Color.White else c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold
    )
}
