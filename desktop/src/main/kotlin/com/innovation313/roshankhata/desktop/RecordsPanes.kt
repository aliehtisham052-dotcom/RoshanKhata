package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.layout.height
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
        DialogWindow(onCloseRequest = { adding = false },onPreviewKeyEvent = escCloses { adding = false }, title = word(W.add_bill), state = rememberDialogState(width = fitW(700.dp), height = fitH(820.dp))) {
            Box(Modifier.fillMaxSize()) {
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
    RecordList(word(W.supplier_bills), word(W.bill_ledger_note), bills.isEmpty(), word(W.no_bills), action = word(W.add_bill) to { adding = true }, emptyIcon = "ic_tile_bills") {
        items(bills, key = { it.id }) { b ->
            Column(Modifier.fillMaxWidth().press { open = if (open == b.id) null else b.id }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.name(b.partyName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(b.billNumber?.takeIf { it.isNotBlank() }?.let { "#$it" }, Format.dateOnly(b.billDate)).joinToString("  ·  "),
                            color = c.textMuted, fontSize = 13.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Format.money(b.totalAmount), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        BillState(b)
                    }
                }
                if (open == b.id) {
                    Row {
                        FluentButton(word(W.edit_bill), { Gates.edit { editingBill = b } }, kind = ButtonKind.SUBTLE)
                        // A bill's lines and the bill itself change the book: a cashier waits for a manager's PIN,
                        // as on the phone (22 Oct 2026, the review: these asked nobody).
                        FluentButton(word(W.add_item), { Gates.edit { lineFor = b.id to null } }, kind = ButtonKind.SUBTLE)
                        FluentButton(word(W.delete_bill), { Gates.edit { deletingBill = b } }, kind = ButtonKind.DANGER)
                    }
                    var path by remember(b.id, photoVersion) { mutableStateOf<String?>(null) }
                    androidx.compose.runtime.LaunchedEffect(b.id, photoVersion) { path = billPhoto(b.id) }
                    Row {
                        if (path != null) FluentButton(word(W.view_bill_photo), { photoOf = billLabel to path }, kind = ButtonKind.SUBTLE)
                        FluentButton(word(if (path == null) W.add_bill_photo else W.bill_photo_attached), { attach(b) }, kind = ButtonKind.SUBTLE)
                    }
                    val lines by remember(b.id) { dao.observeBillItems(b.id) }.collectAsState(emptyList())
                    Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(Look.subtle, Look.ctl).padding(10.dp)) {
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
                                FluentButton(word(W.batch_buyers_action), { buyersOf = l.id }, kind = ButtonKind.SUBTLE)
                                FluentButton(word(W.edit), { Gates.edit { lineFor = b.id to l } }, kind = ButtonKind.SUBTLE)
                                FluentButton(word(W.delete), { Gates.edit { deletingLine = l } }, kind = ButtonKind.DANGER)
                            }
                        }
                    }
                }
            }
            Rule()
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
    // The list as a spreadsheet and back, and the labels (POS Phase 2).
    val scope = rememberCoroutineScope()
    var told by remember { mutableStateOf<String?>(null) }
    var importPlan by remember { mutableStateOf<com.innovation313.roshankhata.data.ProductCsv.Plan?>(null) }
    var assignCodes by remember { mutableStateOf<List<com.innovation313.roshankhata.data.Product>?>(null) }
    fun printLabels(list: List<com.innovation313.roshankhata.data.Product>) {
        if (!com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.EXPORT)) { told = words.text(W.access_denied); return }
        if (list.isEmpty()) { told = words.text(W.products_labels_none); return }
        StatementPrint.show(LabelSheet(list), words.text(W.label_sheet_title))
    }
    fun exportCsv() {
        if (!com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.EXPORT)) { told = words.text(W.access_denied); return }
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, words.text(W.products_export), java.awt.FileDialog.SAVE)
        startIn(dialog, "Documents")
        dialog.file = "RoshanKhata_Products_" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date()) + ".csv"
        dialog.isVisible = true
        val name = dialog.file ?: return
        val file = java.io.File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
        scope.launch {
            told = runCatching { withContext(Dispatchers.IO) { file.writeText(com.innovation313.roshankhata.data.ProductCsv.text(products, com.innovation313.roshankhata.data.ProductCsv.headers(words)), Charsets.UTF_8) } }
                .map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { it.message ?: it.toString() }
        }
    }
    fun importCsv() {
        if (!com.innovation313.roshankhata.data.Access.can(com.innovation313.roshankhata.data.Access.Permission.PRICE_EDIT)) { told = words.text(W.access_denied); return }
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, words.text(W.products_import), java.awt.FileDialog.LOAD)
        startIn(dialog, "Downloads")
        dialog.isVisible = true
        val name = dialog.file ?: return
        val file = java.io.File(dialog.directory, name)
        scope.launch {
            val rows = withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.ProductCsv.parse(file.readText(Charsets.UTF_8)) }.getOrDefault(emptyList()) }
            if (rows.isEmpty()) told = words.text(W.products_import_none)
            else importPlan = com.innovation313.roshankhata.data.ProductCsv.plan(rows, products)
        }
    }
    importPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { importPlan = null },
            title = { Text(word(W.products_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(words.text(W.products_import_plan, plan.toAdd.size, plan.toUpdate.size))
                    if (plan.barcodeClashes > 0) Text(words.text(W.products_import_clash, plan.barcodeClashes), color = c.owedToMe, fontSize = 13.sp)
                }
            },
            confirmButton = {
                FluentButton(word(W.save), {
                    importPlan = null
                    scope.launch {
                        val (added, updated) = withContext(Dispatchers.IO) { dao.importProducts(plan) }
                        withContext(Dispatchers.IO) { dao.log(com.innovation313.roshankhata.data.AuditAction.PRICE_CHANGED, com.innovation313.roshankhata.data.Access.userName, detail = words.text(W.products_imported, added, updated)) }
                        told = words.text(W.products_imported, added, updated)
                    }
                }, kind = ButtonKind.ACCENT)
            },
            dismissButton = { FluentButton(word(W.cancel), { importPlan = null }, kind = ButtonKind.STANDARD) }
        )
    }
    assignCodes?.let { missing ->
        AlertDialog(
            onDismissRequest = { assignCodes = null },
            title = { Text(word(W.products_labels)) },
            text = { Text(words.text(W.products_labels_assign, missing.size)) },
            confirmButton = {
                FluentButton(word(W.products_labels), {
                    assignCodes = null
                    scope.launch {
                        val coded = withContext(Dispatchers.IO) { dao.assignInternalBarcodes(missing.map { it.id }) }
                        printLabels(shown.map { p -> coded.firstOrNull { it.id == p.id } ?: p })
                    }
                }, kind = ButtonKind.ACCENT)
            },
            dismissButton = { FluentButton(word(W.cancel), { assignCodes = null }, kind = ButtonKind.STANDARD) }
        )
    }
    Column(Modifier.fillMaxSize()) {
        RecordList(word(W.tile_products), told ?: word(W.products_subtitle), shown.isEmpty() && q.isNotEmpty(), word(W.no_products), emptyIcon = "ic_tile_products",
            head = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FluentField(query, { query = it }, Modifier.weight(1f), placeholder = word(W.tile_products), leadingIcon = "ic_search")
                    FluentButton(word(W.products_labels), {
                        val missing = shown.filter { it.barcode.isNullOrBlank() }
                        if (missing.isEmpty()) printLabels(shown) else assignCodes = missing
                    }, kind = ButtonKind.STANDARD, icon = "ic_print", enabled = shown.isNotEmpty())
                    FluentButton(word(W.products_export), { exportCsv() }, kind = ButtonKind.STANDARD, enabled = products.isNotEmpty())
                    FluentButton(word(W.products_import), { importCsv() }, kind = ButtonKind.STANDARD)
                }
            }) {
            if (shown.isEmpty()) item(key = "none") { Text(word(W.no_products), Modifier.padding(16.dp), color = c.textMuted, fontSize = 14.sp) }
            items(shown, key = { it.id }) { p ->
                Column(Modifier.fillMaxWidth().press { buyersOf = p }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            val sub = listOfNotNull(p.company, p.category, p.defaultUnit?.let { com.innovation313.roshankhata.data.UnitWords.label(it) }).filter { it.isNotBlank() }
                            if (sub.isNotEmpty()) Text(sub.joinToString("  ·  "), color = c.textMuted, fontSize = 13.sp)
                        }
                        p.barcode?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false) }
                        Spacer(Modifier.width(12.dp))
                        p.salePrice?.let { Text(Format.money(it), color = c.ink, fontSize = 14.sp) }
                        FluentButton(word(W.edit), { editing = p }, kind = ButtonKind.SUBTLE)
                    }
                }
                Rule()
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
    onFields: (InvoiceFields) -> Unit = {},
    /** An invoice to open first (one just made from a counter sale). */
    openId: Long? = null
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val invoices by remember { dao.observeInvoices() }.collectAsState(emptyList())
    var settingFields by remember { mutableStateOf(false) }
    if (settingFields) {
        DialogWindow(onCloseRequest = { settingFields = false },onPreviewKeyEvent = escCloses { settingFields = false }, title = word(W.invoice_settings_title), state = rememberDialogState(width = fitW(520.dp), height = fitH(640.dp))) {
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
        DialogWindow(onCloseRequest = { adding = false },onPreviewKeyEvent = escCloses { adding = false }, title = word(W.add_invoice), state = rememberDialogState(width = fitW(720.dp), height = fitH(860.dp))) {
            Box(Modifier.fillMaxSize()) {
                InvoiceForm(
                    parties = parties,
                    preview = { inv, lines -> com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), inv, lines)?.picture(0, SHARP) },
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
        DialogWindow(onCloseRequest = { editingInvoice = null },onPreviewKeyEvent = escCloses { editingInvoice = null }, title = word(W.edit), state = rememberDialogState(width = fitW(720.dp), height = fitH(860.dp))) {
            Box(Modifier.fillMaxSize()) {
                InvoiceForm(
                    parties = parties,
                    preview = { i, l -> com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), i, l)?.picture(0, SHARP) },
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
    var chosen by remember(openId) { mutableStateOf(openId) }
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
    FluentPage(
        word(W.nav_invoice),
        word(W.range_this_month) + " " + Format.money(thisMonth.sumOf { it.grandTotal }) + " · " + thisMonth.size + "  ·  " + word(W.range_all) + " " + invoices.size,
        scroll = false,
        actions = {
            FluentButton(word(W.invoice_settings_title), { settingFields = true }, icon = "ic_tile_settings")
            FluentButton(word(W.add_invoice), { adding = true }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
        }
    ) {
        if (invoices.isEmpty()) {
            EmptyState("ic_tile_invoice", word(W.no_invoices), null, Modifier.weight(1f)) {
                FluentButton(word(W.add_invoice), { adding = true }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
            }
            return@FluentPage
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ListCard(Modifier.weight(0.46f).fillMaxSize()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(word(W.range_all) + " \u00b7 " + invoices.size, !monthOnly) { monthOnly = false }
                    Chip(word(W.range_this_month) + " \u00b7 " + thisMonth.size, monthOnly) { monthOnly = true }
                }
                Rule()
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(shown, key = { _, it -> it.id }) { idx, i ->
                        val on = i.id == open?.id
                        Row(
                            Modifier.fillMaxWidth().background(eased(if (on) Look.accentTint else androidx.compose.ui.graphics.Color.Transparent))
                                .press { chosen = i.id }.padding(end = 12.dp, top = 9.dp, bottom = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(3.dp).height(22.dp).background(eased(if (on) Look.accent else androidx.compose.ui.graphics.Color.Transparent), Look.ctl))
                            androidx.compose.foundation.layout.Spacer(Modifier.width(9.dp))
                            Avatar(i.customerName, 34, idx)
                            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(Format.name(i.customerName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(Format.ltr(i.invoiceNumber) + "  \u00b7  " + Format.dateOnly(i.invoiceDate), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
                            }
                            Text(Format.money(i.grandTotal), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                        Rule()
                    }
                }
            }
            Column(
                Modifier.weight(0.54f).fillMaxSize().card()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (open != null) {
                    // The saved row (what was received, if the owner recorded it) and its picture in its own design.
                    var full by remember(open.id) { mutableStateOf<com.innovation313.roshankhata.data.Invoice?>(null) }
                    var picture by remember(open.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    // The design on show: the saved one until the owner steps through the
                    // phone's ten (only one could be seen before, 20 Oct); Apply keeps it.
                    var look by remember(open.id) { mutableStateOf<Int?>(null) }
                    LaunchedEffect(open.id, open.grandTotal, open.invoiceDate, look) {
                        picture = null
                        withContext(Dispatchers.IO) {
                            val inv = dao.getInvoice(open.id)
                            full = inv
                            picture = inv?.let { row ->
                                runCatching {
                                    com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), row.copy(templateId = look ?: row.templateId), dao.invoiceItems(open.id))
                                        // Drawn sharp for the screen (21 Oct 2026, the owner: "very blurry"): it was
                                        // one pixel per point, 595 px across, stretched over a pane twice that wide.
                                        ?.picture(0, SHARP)?.toComposeImageBitmap()
                                }.getOrNull()
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(open.customerName, 44, 0)
                        Column(Modifier.weight(1f)) {
                            Text(Format.name(open.customerName), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(Format.ltr(open.invoiceNumber) + "  \u00b7  " + Format.dateOnly(open.invoiceDate), color = c.textMuted, fontSize = 12.sp)
                        }
                        Text(Format.money(open.grandTotal), color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                    // Received is shown only when the owner recorded it on the invoice: nothing
                    // recorded is not the same as nothing paid (Invoice.receivedAmount).
                    full?.receivedAmount?.let { got ->
                        Text(word(W.invoice_received_line, Format.money(got)), color = c.iOwe, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val due = open.grandTotal - got
                        if (Money.isPositive(due)) Text(word(W.invoice_balance_due_line, Format.money(due)), color = c.owedToMe, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FluentButton(word(W.ledger_pdf_button), { print(open.id) }, kind = ButtonKind.ACCENT, icon = "ic_print")
                        FluentButton(word(W.edit), {
                            Gates.edit { scope.launch { editingInvoice = withContext(Dispatchers.IO) { dao.getInvoice(open.id)?.let { it to dao.invoiceItems(open.id) } } } }
                        }, icon = "ic_edit")
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        FluentButton(word(W.delete), { Gates.edit { deletingInvoice = open } }, kind = ButtonKind.DANGER)
                    }
                    full?.let { saved ->
                        DesignStepper(
                            shown = look ?: saved.templateId,
                            saved = saved.templateId,
                            onShow = { look = it },
                            onApply = { t ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { dao.updateInvoice(saved.copy(templateId = t)) }
                                    full = saved.copy(templateId = t)
                                }
                            }
                        )
                    }
                    val pic = picture
                    if (pic == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        androidx.compose.foundation.Image(
                            pic, contentDescription = open.invoiceNumber,
                            modifier = Modifier.fillMaxWidth().clip(Look.ctl).border(1.dp, Look.strokeCard, Look.ctl),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                        )
                    }
                }
            }
        }
    }
}

/**
 * The phone's ten invoice designs, one at a time beside an invoice: ‹ and ›
 * step through them (wrapping round), the name and "3 of 10" between, and
 * Apply when the one on show is not the saved one.
 */
@Composable
fun DesignStepper(shown: Int, saved: Int?, onShow: (Int) -> Unit, onApply: ((Int) -> Unit)? = null) {
    val c = LocalRoshanColors.current
    val names = com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.NAMES
    val at = names.indexOfFirst { it.first == shown }.coerceAtLeast(0)
    fun step(by: Int) = onShow(names[(at + by + names.size) % names.size].first)
    Row(
        Modifier.fillMaxWidth().clip(Look.ctl).background(Look.subtle).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.TextButton({ step(-1) }) { Text("‹", color = c.brandGreenText, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(word(names[at].second), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(word(W.invoice_design_position, at + 1, names.size), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
        }
        if (onApply != null && shown != saved) {
            androidx.compose.material3.TextButton({ onApply(shown) }) { Text(word(W.invoice_more_apply), color = c.brandGreenText, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
        androidx.compose.material3.TextButton({ step(1) }) { Text("›", color = c.brandGreenText, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}

/** A titled list on a Fluent card (the Figma rhythm), or the empty state with its one action when there is nothing yet. */
@Composable
private fun RecordList(
    title: String,
    subtitle: String?,
    empty: Boolean,
    emptyText: String,
    action: Pair<String, () -> Unit>? = null,
    emptyIcon: String = "ic_tile_bills",
    head: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
    rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    FluentPage(title, subtitle, scroll = false, actions = {
        action?.let { (label, go) -> FluentButton(label, go, kind = ButtonKind.ACCENT, icon = "ic_add_plain") }
    }) {
        head?.invoke(this)
        if (empty) {
            EmptyState(emptyIcon, emptyText, null, Modifier.weight(1f)) {
                action?.let { (label, go) -> FluentButton(label, go, kind = ButtonKind.ACCENT, icon = "ic_add_plain") }
            }
        } else ListCard(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), content = rows)
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
    // What is left of each batch (Fefo: sold over the counter and the khata, back, written off),
    // and the write-off of an expired one (21 Oct 2026): the counter never sells it.
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var lots by remember { mutableStateOf<Map<Long, Pair<com.innovation313.roshankhata.data.Product, com.innovation313.roshankhata.data.Fefo.Lot>>>(emptyMap()) }
    LaunchedEffect(batches, tick) {
        lots = withContext(Dispatchers.IO) {
            batches.mapNotNull { b -> com.innovation313.roshankhata.data.Fefo.lotOf(dao, b.itemId)?.let { b.itemId to it } }.toMap()
        }
    }
    var writingOff by remember { mutableStateOf<com.innovation313.roshankhata.data.ExpiringBatch?>(null) }
    writingOff?.let { b ->
        val found = lots[b.itemId]
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { writingOff = null },
            title = { Text(word(W.expiry_write_off)) },
            text = {
                Text(found?.let { (p, lot) -> word(W.expiry_write_off_confirm, com.innovation313.roshankhata.ui.Calc.trim(lot.left) + (com.innovation313.roshankhata.data.UnitWords.label(p.defaultUnit).let { u -> if (u.isEmpty()) "" else " $u" }), lot.batchNumber ?: p.name) }
                    ?: word(W.expiry_nothing_left))
            },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    writingOff = null
                    scope.launch { withContext(Dispatchers.IO) { com.innovation313.roshankhata.data.Fefo.writeOff(dao, b.itemId, b.batchNumber) }; tick++ }
                }, enabled = (found?.second?.left ?: 0.0) > 0.0) { Text(word(W.expiry_write_off), color = c.owedToMe) }
            },
            dismissButton = { androidx.compose.material3.TextButton({ writingOff = null }) { Text(word(W.cancel)) } }
        )
    }
    RecordList(word(W.expiring_stock), word(W.expiring_subtitle), false, word(W.no_expiring), emptyIcon = "ic_tile_expiring") {
        item(key = "trace") { BatchTraceCard(dao) { buyersOf = it } }
        if (batches.isEmpty()) item(key = "none") { Text(word(W.no_expiring), Modifier.padding(16.dp), color = c.textMuted, fontSize = 15.sp) }
        items(batches, key = { it.itemId }) { b ->
            Row(Modifier.fillMaxWidth().press { buyersOf = b.itemId }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.productName, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                    lots[b.itemId]?.let { (p, lot) ->
                        Text(
                            if (lot.left > 0.0) word(W.expiry_left, com.innovation313.roshankhata.ui.Calc.trim(lot.left) + (com.innovation313.roshankhata.data.UnitWords.label(p.defaultUnit).let { u -> if (u.isEmpty()) "" else " $u" })) else word(W.expiry_nothing_left),
                            color = c.textMuted, fontSize = 12.sp
                        )
                        if (b.hasExpired && lot.left > 0.0) {
                            FluentButton(word(W.expiry_write_off), {
                                Gates.need(com.innovation313.roshankhata.data.Access.Permission.STOCK_ADJUST) { writingOff = b }
                            }, kind = ButtonKind.DANGER)
                        }
                    }
                }
            }
            Rule()
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
    // The next payment day being picked (21 Oct 2026): from the plan's actions, or after a payment
    // with the suggested day already chosen. The plan and the day the picker opens on.
    var dating by remember { mutableStateOf<Pair<com.innovation313.roshankhata.data.PlanProgress, Long>?>(null) }
    dating?.let { (p, start) ->
        DayPicker(start, onPick = { day ->
            dating = null
            scope.launch { withContext(Dispatchers.IO) { dao.setPlanNextDue(p.id, day) } }
        }, onDismiss = { dating = null }, title = word(W.plan_next_ask, Format.name(p.partyName)))
    }
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
                            add(word(W.plan_next_change) to { dating = p to (p.nextDueDate ?: com.innovation313.roshankhata.data.PlanSchedule.suggestNext(null)) })
                            if (p.nextDueDate != null) add(word(W.plan_next_clear) to {
                                scope.launch { withContext(Dispatchers.IO) { dao.setPlanNextDue(p.id, null) } }
                            })
                            add(word(W.close_plan) to {
                                scope.launch { withContext(Dispatchers.IO) { dao.getPlan(p.id)?.let { dao.updatePlan(it.copy(isClosed = true, closedAt = System.currentTimeMillis())) } } }
                            })
                        }
                        add(word(W.delete_plan) to { Gates.edit { deleting = p } })
                    }
                    actions.forEach { (label, go) ->
                        Text(label, Modifier.fillMaxWidth().press { acting = null; go() }.padding(vertical = 12.dp), color = c.ink, fontSize = 16.sp)
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
                    Outlined(amountText, { amountText = it }, label = { Text(word(W.amount)) }, singleLine = true, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                    Text(amount?.let { Format.money(it) } ?: "", color = c.textMuted)
                    Outlined(note, { note = it }, label = { Text(word(W.note_optional)) }, singleLine = true)
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
                        // Still open: the next payment day, the suggestion already chosen (the phone's way).
                        if (p.paidSoFar + a < p.totalAmount) dating = p to com.innovation313.roshankhata.data.PlanSchedule.suggestNext(p.nextDueDate)
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
    RecordList(word(W.payment_plans), word(W.plans_subtitle), plans.isEmpty(), word(W.no_plans), action = word(W.add_plan) to { adding = true }, emptyIcon = "ic_tile_plans") {
        items(plans, key = { it.id }) { p ->
            Column(
                Modifier.fillMaxWidth().alpha(if (p.isClosed) 0.6f else 1f).press { acting = p }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.name(p.partyName), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
            Rule()
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
        RecordList(word(W.sr_title), word(W.sr_subtitle), shown.isEmpty(), word(if (rows.isEmpty()) W.sr_none else W.sr_nothing_to_watch), emptyIcon = "ic_tile_supplier_rates",
            head = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(true to W.sr_watch, false to W.filter_type_all).forEach { (watch, label) -> FluentPill(word(label), watch == watchOnly) { watchOnly = watch } }
                }
            }) {
            items(shown) { r ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val unit = com.innovation313.roshankhata.data.UnitWords.label(r.unit).takeIf { it.isNotEmpty() }
                    Text(listOfNotNull(r.name, unit).joinToString(" · "), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                Rule()
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
                        FluentPill(word(label), i == choice) { choice = i }
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
        confirmButton = { FluentButton(word(W.ok), onClose, kind = ButtonKind.STANDARD) }
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
    DialogWindow(onCloseRequest = onCancel, onPreviewKeyEvent = escCloses(onCancel), title = word(W.add_plan), state = rememberDialogState(width = fitW(520.dp), height = fitH(680.dp))) {
        Column(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(word(W.plan_dialog_help), color = c.textMuted, fontSize = 12.sp)
            val chosen = parties.firstOrNull { it.id == partyId }
            if (chosen != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.name(chosen.name) + "  \u00b7  " + Format.money(chosen.balance), Modifier.weight(1f), color = c.ink, fontWeight = FontWeight.Bold)
                    FluentButton(word(W.edit), { partyId = null }, kind = ButtonKind.SUBTLE)
                }
            } else {
                Outlined(search, { search = it }, label = { Text(word(W.cheque_party_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val q = search.trim().lowercase()
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp).background(Look.subtle, Look.ctl)) {
                    items(parties.filter { NameSearch.matches(it.name, it.phone, q) }.take(40), key = { it.id }) { p ->
                        Text(Format.name(p.name) + "  \u00b7  " + Format.money(p.balance),
                            Modifier.fillMaxWidth().press { partyId = p.id; problem = null }.padding(horizontal = 12.dp, vertical = 8.dp), color = c.ink, fontSize = 14.sp)
                    }
                }
            }
            Outlined(total, { total = it; problem = null }, label = { Text(word(W.plan_total_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            Outlined(each, { each = it }, label = { Text(word(W.plan_installment_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FluentButton(word(W.plan_pick_next_due), { picking = true }, kind = ButtonKind.SUBTLE)
                Text(due?.let { Format.dateOnly(it) } ?: "", color = c.ink, fontWeight = FontWeight.Bold)
            }
            Outlined(note, { note = it }, label = { Text(word(W.note_optional)) }, modifier = Modifier.fillMaxWidth())
            problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
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
    FigureCard(label, amount, LocalRoshanColors.current.ink, modifier, under = count)
}

/** A filter pill, filled when on. */
@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) = FluentPill(text, on, onClick = onClick)

/**
 * Pixels per point an invoice is drawn at for the screen: 3, about 1,800 px
 * across an A4 page, so it stays sharp on a 125-150% laptop screen and a wide
 * pane, and is only ever scaled down (the phone draws its preview at 2).
 */
private const val SHARP = 3f
