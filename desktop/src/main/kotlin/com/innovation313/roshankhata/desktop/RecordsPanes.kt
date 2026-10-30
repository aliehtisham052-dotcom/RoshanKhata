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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
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
fun BillsPane(
    dao: KhataDao, parties: List<PartyWithBalance>, words: Words, home: java.io.File,
    /** Open with a new bill's form (Home's Add bill); [onStarted] says it was opened, so it opens once. */
    startAdding: Boolean = false, onStarted: () -> Unit = {},
    /** The expiring figure's click: Stock & expiry's Expiry tab. */
    onExpiry: () -> Unit = {},
    onLedgerChanged: () -> Unit
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val bills by remember { dao.observeBills() }.collectAsState(emptyList())
    // The phone's figures above the list (Marhala 4): how many bills, what is owed to the suppliers — each
    // supplier's live balance counted once, as the phone counts it — and the batches expiring in the window.
    val expiring by remember { com.innovation313.roshankhata.data.Fefo.observeExpiringCount(dao, System.currentTimeMillis() + com.innovation313.roshankhata.data.ExpiryWindow.WARN_MS) }.collectAsState(0)
    var open by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(startAdding) { if (startAdding) { adding = true; onStarted() } }
    var deletingBill by remember { mutableStateOf<BillSummary?>(null) }
    var editingBill by remember { mutableStateOf<BillSummary?>(null) }
    // A bill line being added (id 0 on a bill) or corrected, and a line whose buyers are asked for.
    var lineFor by remember { mutableStateOf<Pair<Long, com.innovation313.roshankhata.data.BillItem?>?>(null) }
    var deletingLine by remember { mutableStateOf<com.innovation313.roshankhata.data.BillItem?>(null) }
    var buyersOf by remember { mutableStateOf<Long?>(null) }
    // A line going back to its supplier (the phone's BatchReturnDialog), and what was said after.
    var returningLine by remember { mutableStateOf<Long?>(null) }
    var said by remember { mutableStateOf<String?>(null) }
    returningLine?.let { id -> BatchReturnDialog(dao, id, onClose = { returningLine = null }) { msg -> returningLine = null; said = msg; onLedgerChanged() } }
    said?.let { TellDialog(word(W.return_title), it, onClose = { said = null }) }
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
        AskDialog(
            title = word(W.delete_item), text = word(W.delete_item_confirm, l.productName), confirm = word(W.delete), danger = true,
            onConfirm = {
                deletingLine = null
                scope.launch { withContext(Dispatchers.IO) { dao.softDeleteBillItem(l.id) } }
            },
            onDismiss = { deletingLine = null }
        )
    }
    deletingBill?.let { b ->
        // A credit bill's own "I owe them" entry goes to the Recycle Bin with it (the phone's rule).
        AskDialog(
            title = word(W.delete_bill),
            text = Format.name(b.partyName) + "  \u00b7  " + Format.money(b.totalAmount) + (b.billNumber?.let { "  \u00b7  #$it" } ?: ""),
            confirm = word(W.delete), danger = true,
            onConfirm = {
                deletingBill = null; open = null
                scope.launch { withContext(Dispatchers.IO) { dao.deleteSupplierBill(b.id) }; onLedgerChanged() }
            },
            onDismiss = { deletingBill = null }
        )
    }
    if (adding) {
        AppDialog(onCloseRequest = { adding = false },onPreviewKeyEvent = escCloses { adding = false }, title = word(W.add_bill), state = rememberDialogState(width = fitW(700.dp), height = fitH(820.dp))) {
            Box(Modifier.fillMaxSize()) {
                BillForm(
                    parties = parties,
                    products = rememberProductShelf(dao),
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
    RecordList(word(W.supplier_bills), word(W.bill_ledger_note), bills.isEmpty(), word(W.no_bills), action = word(W.add_bill) to { adding = true }, emptyIcon = "ic_tile_bills",
        head = {
            val owed = bills.distinctBy { it.partyId }.sumOf { (-it.supplierBalance).coerceAtLeast(0.0) }
            if (bills.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FigureCard(word(W.stat_bills), "%,d".format(com.innovation313.roshankhata.data.Digits.FIGURES, bills.size), c.ink, Modifier.weight(1f), icon = "ic_tile_bills", tint = Look.subtle)
                FigureCard(word(W.stat_you_owe), Format.money(owed), if (owed > 0.005) c.owedToMe else c.ink, Modifier.weight(1f), icon = "ic_arrow_gave", tint = Look.getTint)
                FigureCard(word(W.stat_expiring), "%,d".format(com.innovation313.roshankhata.data.Digits.FIGURES, expiring), if (expiring > 0) Look.warn else c.ink,
                    Modifier.weight(1f).clip(Look.card).press(Look.card, onClick = onExpiry), icon = "ic_calendar", tint = Look.warnTint)
            }
        }) {
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
                                FluentButton(word(W.return_action), { Gates.edit { returningLine = l.id } }, kind = ButtonKind.SUBTLE)
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
    // A cashier is asked for a manager's PIN, as every other gated action asks (Marhala 4, part 9 — the audit:
    // these three said "access denied" and stopped).
    fun printLabels(list: List<com.innovation313.roshankhata.data.Product>) = Gates.need(com.innovation313.roshankhata.data.Access.Permission.EXPORT) labels@{
        if (list.isEmpty()) { told = words.text(W.products_labels_none); return@labels }
        StatementPrint.show(LabelSheet(list), words.text(W.label_sheet_title))
    }
    fun exportCsv() = Gates.need(com.innovation313.roshankhata.data.Access.Permission.EXPORT) export@{
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, words.text(W.products_export), java.awt.FileDialog.SAVE)
        startIn(dialog, "Documents")
        dialog.file = "RoshanKhata_Products_" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date()) + ".csv"
        dialog.isVisible = true
        val name = dialog.file ?: return@export
        val file = java.io.File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
        scope.launch {
            told = runCatching {
                withContext(Dispatchers.IO) {
                    file.writeText(com.innovation313.roshankhata.data.ProductCsv.text(products, com.innovation313.roshankhata.data.ProductCsv.headers(words)), Charsets.UTF_8)
                    runCatching { dao.log(com.innovation313.roshankhata.data.AuditAction.EXPORT, com.innovation313.roshankhata.data.Access.userName, detail = file.name) }
                }
            }.map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { failText(words, it) }
        }
    }
    fun importCsv() = Gates.need(com.innovation313.roshankhata.data.Access.Permission.PRICE_EDIT) pick@{
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, words.text(W.products_import), java.awt.FileDialog.LOAD)
        startIn(dialog, "Downloads")
        dialog.isVisible = true
        val name = dialog.file ?: return@pick
        val file = java.io.File(dialog.directory, name)
        scope.launch {
            val rows = withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.ProductCsv.parse(file.readText(Charsets.UTF_8)) }.getOrDefault(emptyList()) }
            if (rows.isEmpty()) told = words.text(W.products_import_none)
            else importPlan = com.innovation313.roshankhata.data.ProductCsv.plan(rows, products)
        }
    }
    importPlan?.let { plan ->
        // In the app's frame (Marhala 4, the audit: Material's dialogs remained on this page).
        AppDialog(onCloseRequest = { importPlan = null }, title = word(W.products_import), state = rememberDialogState(width = fitW(480.dp), height = fitH(300.dp))) {
            DialogFrame(word(W.products_import), onClose = { importPlan = null }, footer = {
                FluentButton(word(W.cancel), { importPlan = null }, kind = ButtonKind.STANDARD)
                FluentButton(word(W.save), {
                    importPlan = null
                    scope.launch {
                        val (added, updated) = withContext(Dispatchers.IO) { dao.importProducts(plan) }
                        // Written down as the import it was (the audit: it read "Price changed").
                        withContext(Dispatchers.IO) { dao.log(com.innovation313.roshankhata.data.AuditAction.IMPORT, com.innovation313.roshankhata.data.Access.userName, detail = words.text(W.products_imported, added, updated)) }
                        told = words.text(W.products_imported, added, updated)
                    }
                }, kind = ButtonKind.ACCENT)
            }) {
                Text(words.text(W.products_import_plan, plan.toAdd.size, plan.toUpdate.size), color = c.ink, fontSize = 14.sp)
                if (plan.barcodeClashes > 0) Text(words.text(W.products_import_clash, plan.barcodeClashes), color = c.owedToMe, fontSize = 13.sp)
            }
        }
    }
    assignCodes?.let { missing ->
        AskDialog(
            title = word(W.products_labels), text = words.text(W.products_labels_assign, missing.size), confirm = word(W.products_labels),
            onConfirm = {
                assignCodes = null
                scope.launch {
                    val coded = withContext(Dispatchers.IO) { dao.assignInternalBarcodes(missing.map { it.id }) }
                    printLabels(shown.map { p -> coded.firstOrNull { it.id == p.id } ?: p })
                }
            },
            onDismiss = { assignCodes = null }
        )
    }
    Column(Modifier.fillMaxSize()) {
        // An empty book says how products come; a search with nothing found says so (25 Oct 2026, the audit: the
        // two were the other way round, and the phone's words pointed "below" at nothing on the computer).
        RecordList(word(W.tile_products), told ?: word(W.products_subtitle), products.isEmpty(), word(W.no_products_pc), emptyIcon = "ic_tile_products",
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
            if (shown.isEmpty()) item(key = "none") { Text(word(W.no_matching_products), Modifier.padding(16.dp), color = c.textMuted, fontSize = 14.sp) }
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

/**
 * The Documents page (29 Oct 2026, schema 40; the Invoices page before): every
 * customer-facing document — quotation, proforma, sales order, delivery challan,
 * invoice — in one list with kind pills, each row its number, its status tag and
 * its total; the open document beside it in its own design with the actions its
 * status allows (DocActions): Accept, Convert to the next kind, Mark as
 * Cancelled, Edit, Delete, and Print and WhatsApp always. A converted or
 * cancelled document is read, printed and sent, never changed (W.doc_locked).
 * Add asks the kind first (Invoice by default) and opens the one form with it.
 */
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
    /** A document to open first (one just made from a counter sale, or the one a conversion made). */
    openId: Long? = null
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    // The kind pill: null is every kind. A document opened from elsewhere (a counter sale, a conversion, a
    // "From …" link) may be of another kind than the pill shows, so those openings put the pill back on All.
    var kind by remember { mutableStateOf<String?>(null) }
    // The newest page of documents, more as the owner scrolls (23 Oct 2026, the review: every invoice was
    // read, three subqueries each, after any change); the count and this month's figure have their own reads.
    var invoiceLimit by remember { mutableStateOf(com.innovation313.roshankhata.data.ListWindow.PAGE) }
    var invoices by remember { mutableStateOf(emptyList<com.innovation313.roshankhata.data.InvoiceSummary>()) }
    LaunchedEffect(invoiceLimit, kind) { dao.observeDocPage(kind, invoiceLimit).collect { invoices = it } }
    val invoiceCount by remember { dao.observeDocCount(null) }.collectAsState(0)
    var settingFields by remember { mutableStateOf(false) }
    if (settingFields) {
        AppDialog(onCloseRequest = { settingFields = false },onPreviewKeyEvent = escCloses { settingFields = false }, title = word(W.invoice_settings_title), state = rememberDialogState(width = fitW(520.dp), height = fitH(640.dp))) {
            InvoiceFieldsSettings(fields, onFields)
        }
    }
    // Add: the kind is asked first (Invoice by default), then the form opens with it.
    var pickingKind by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<String?>(null) }
    if (pickingKind) {
        PickDialog(
            word(W.doc_pick_kind),
            com.innovation313.roshankhata.data.DocKind.saved.map { docKindWord(it) },
            chosen = com.innovation313.roshankhata.data.DocKind.saved.indexOf(com.innovation313.roshankhata.data.DocKind.INVOICE),
            onPick = { i -> pickingKind = false; adding = com.innovation313.roshankhata.data.DocKind.saved[i] },
            onDismiss = { pickingKind = false }
        )
    }
    // A saved document opened to correct it: the row and its live lines.
    var editingInvoice by remember { mutableStateOf<Pair<com.innovation313.roshankhata.data.Invoice, List<com.innovation313.roshankhata.data.InvoiceItem>>?>(null) }
    var deletingInvoice by remember { mutableStateOf<com.innovation313.roshankhata.data.InvoiceSummary?>(null) }
    var cancelling by remember { mutableStateOf<com.innovation313.roshankhata.data.InvoiceSummary?>(null) }
    // What a refused or failed write said, in the owner's words (doc_locked for a document no longer live).
    var refused by remember { mutableStateOf<String?>(null) }
    var chosen by remember(openId) { mutableStateOf(openId) }
    deletingInvoice?.let { inv ->
        // The app's question, titled (Marhala 4, the audit: it was Material's, with no title).
        AskDialog(
            title = word(W.delete) + "  ·  " + inv.invoiceNumber,
            subtitle = Format.name(inv.customerName),
            text = word(W.delete_invoice_confirm),
            confirm = word(W.delete),
            danger = true,
            onConfirm = {
                deletingInvoice = null
                scope.launch { withContext(Dispatchers.IO) { dao.softDeleteInvoice(inv.id) } }
            },
            onDismiss = { deletingInvoice = null }
        )
    }
    cancelling?.let { inv ->
        // Cancel keeps the document on record under its number (delete nothing, reverse it), red: it ends it.
        AskDialog(
            title = word(W.mark_cancelled) + "  ·  " + inv.invoiceNumber,
            subtitle = Format.name(inv.customerName),
            text = word(W.doc_cancel_confirm, Format.ltr(inv.invoiceNumber)),
            confirm = word(W.mark_cancelled),
            danger = true,
            onConfirm = {
                cancelling = null
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { dao.cancelDocument(inv.id) } }
                        .onFailure { refused = docFailText(words, it) }
                }
            },
            onDismiss = { cancelling = null }
        )
    }
    refused?.let { TellDialog(word(W.documents_title), it, { refused = null }) }
    var told by remember { mutableStateOf<String?>(null) }
    told?.let { TellDialog(word(W.invoice_send_whatsapp), it, { told = null }, height = 320) }
    // The document as the customer will hold it: the phone's design it was made with — the plain layout only
    // if a design could not be drawn.
    fun sheetOf(inv: com.innovation313.roshankhata.data.Invoice, lines: List<com.innovation313.roshankhata.data.InvoiceItem>): java.awt.print.Printable =
        com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), inv, lines)?.let { com.innovation313.roshankhata.desktop.invoice.DocumentPrint(it) }
            ?: InvoicePrint(words, rtl, shopName ?: words.text(W.app_name), shopLine, inv, lines)
    // Print a document by id: the saved row and its lines, as the customer will hold them.
    fun print(id: Long) {
        scope.launch {
            val pair = withContext(Dispatchers.IO) { dao.getInvoice(id)?.let { it to dao.invoiceItems(id) } } ?: return@launch
            val sheet = withContext(Dispatchers.IO) { sheetOf(pair.first, pair.second) }
            StatementPrint.show(sheet, pair.first.invoiceNumber)
        }
    }
    // The phone's "Send on WhatsApp" (InvoicesActivity.sendPdfToCustomer) as far as a computer goes: nothing
    // here can attach a file to a chat, so the PDF is saved and shown chosen in its folder, to be dragged into
    // the customer's chat — opened by his number alone, so no name or amount goes through the browser.
    var sending by remember { mutableStateOf(false) }
    fun sendOnWhatsApp(id: Long) {
        if (sending) return
        scope.launch {
            val pair = withContext(Dispatchers.IO) { dao.getInvoice(id)?.let { it to dao.invoiceItems(id) } } ?: return@launch
            val number = pair.first.customerPhone?.let { com.innovation313.roshankhata.data.WhatsAppNumber.of(it) }
            if (number == null) {
                told = words.text(W.no_phone_number)
                return@launch
            }
            sending = true
            val saved = runCatching {
                withContext(Preview.drawing) {
                    val file = PdfFiles.next(pair.first.invoiceNumber)
                    PdfFiles.write(Sheets.of(sheetOf(pair.first, pair.second)), file)
                    file
                }
            }
            sending = false
            saved.onSuccess { file ->
                showInFolder(file)
                val chat = withContext(Dispatchers.IO) { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://wa.me/$number")) } }
                told = words.text(W.invoice_whatsapp_pc, file.absolutePath) + (chat.exceptionOrNull()?.let { "\n\n" + failText(words, it) } ?: "")
            }.onFailure { told = words.text(W.pc_pdf_failed, it.message ?: it.toString()) }
        }
    }
    // The step on: the DAO copies the row and its lines into the next kind and marks this one converted
    // (one transaction, an audit row); the new document opens, whatever pill was on.
    fun convert(id: Long, to: String) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { dao.convertDocument(id, to) } }
                .onSuccess { made -> kind = null; chosen = made }
                .onFailure { refused = docFailText(words, it) }
        }
    }
    fun accept(id: Long) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { dao.acceptDocument(id) } }
                .onFailure { refused = docFailText(words, it) }
        }
    }
    /** Edit: a live document opens in the form; one converted or cancelled is told so (the DAO refuses it too). */
    fun edit(id: Long) {
        scope.launch {
            val pair = withContext(Dispatchers.IO) { dao.getInvoice(id)?.let { it to dao.invoiceItems(id) } } ?: return@launch
            if (!com.innovation313.roshankhata.data.DocActions.canEdit(pair.first.status)) refused = words.text(W.doc_locked)
            else editingInvoice = pair
        }
    }
    adding?.let { newKind ->
        val title = if (newKind == com.innovation313.roshankhata.data.DocKind.INVOICE) word(W.add_invoice) else docKindWord(newKind)
        AppDialog(onCloseRequest = { adding = null },onPreviewKeyEvent = escCloses { adding = null }, title = title, state = rememberDialogState(width = fitW(1100.dp), height = fitH(900.dp))) {
            Box(Modifier.fillMaxSize()) {
                InvoiceForm(
                    parties = parties,
                    preview = { inv, lines -> com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), inv, lines)?.picture(0, SHARP) },
                    fields = fields,
                    products = rememberProductShelf(dao),
                    kind = newKind,
                    nextNumber = { dao.nextDocNumber(newKind) },
                    onSave = { inv, lines ->
                        adding = null
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) { runCatching { dao.saveInvoiceWithItems(inv, lines) } }
                            saved.onSuccess { id -> kind = null; chosen = id; print(id) }
                                .onFailure { refused = docFailText(words, it) }
                        }
                    },
                    onCancel = { adding = null }
                )
            }
        }
    }
    editingInvoice?.let { (inv, lines) ->
        AppDialog(onCloseRequest = { editingInvoice = null },onPreviewKeyEvent = escCloses { editingInvoice = null }, title = word(W.edit) + "  ·  " + inv.invoiceNumber, state = rememberDialogState(width = fitW(1100.dp), height = fitH(900.dp))) {
            Box(Modifier.fillMaxSize()) {
                InvoiceForm(
                    parties = parties,
                    preview = { i, l -> com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.pages(designs(), i, l)?.picture(0, SHARP) },
                    existing = inv,
                    existingLines = lines,
                    fields = fields,
                    products = rememberProductShelf(dao),
                    onSave = { updated, newLines ->
                        editingInvoice = null
                        scope.launch {
                            // The phone's edit: the document and its whole line list, in one transaction; the DAO
                            // keeps its kind, status, source, uid and refuses one no longer live.
                            withContext(Dispatchers.IO) { runCatching { dao.updateInvoiceWithItems(updated.copy(id = inv.id), newLines) } }
                                .onSuccess { print(inv.id) }
                                .onFailure { refused = docFailText(words, it) }
                        }
                    },
                    onCancel = { editingInvoice = null }
                )
            }
        }
    }
    // List and page side by side (20 Oct redesign): a click opens the document in
    // its own design beside the list, with its actions; it no longer prints at once.
    val monthStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    // This month's money is the invoices' alone — a quotation, an order or a challan is not a sale, and a
    // cancelled invoice was never one.
    val thisMonth by remember(monthStart) { dao.observeInvoicesFrom(monthStart) }.collectAsState(emptyList())
    val monthMoney = thisMonth.filter { it.kind == com.innovation313.roshankhata.data.DocKind.INVOICE && it.status != com.innovation313.roshankhata.data.DocStatus.CANCELLED }.sumOf { it.grandTotal }
    val shown = invoices
    val invoiceListState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(invoiceListState, invoices.size, kind) {
        androidx.compose.runtime.snapshotFlow { invoiceListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> invoiceLimit = com.innovation313.roshankhata.data.ListWindow.grown(invoiceLimit, invoices.size, last) }
    }
    val open = shown.firstOrNull { it.id == chosen } ?: shown.firstOrNull()
    // A customer's face wears his khata's colours when the book has one party of that name (a document keeps
    // only the name), else colours of its own from the name — never by the row's place (Marhala 4, the audit).
    val faceIds = remember(parties) { parties.groupBy { it.name.trim().lowercase() }.mapValues { (_, same) -> same.singleOrNull()?.id } }
    fun faceId(name: String): Long = faceIds[name.trim().lowercase()] ?: name.trim().lowercase().hashCode().toLong()
    FluentPage(
        word(W.documents_title),
        // This month's invoice money; the count is on the All pill (Marhala 4, the audit: they were said twice).
        word(W.range_this_month) + " " + Format.money(monthMoney),
        scroll = false,
        actions = {
            // Invoice settings as an icon with its words as the hover tip (30 Oct 2026, the proof picture): as a
            // worded button it took the title's room and "Documents" read "Docume…" on the laptop.
            IconAction("ic_tile_settings", word(W.invoice_settings_title), { settingFields = true })
            FluentButton(word(W.doc_add), { pickingKind = true }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
        }
    ) {
        if (invoices.isEmpty() && kind == null) {
            // The head's Add is the one way in (it was offered twice).
            EmptyState("ic_tile_invoice", word(W.no_invoices), null, Modifier.weight(1f))
            return@FluentPage
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // The list as narrow as its rows allow; the document gets the rest (Marhala 4, the audit: small and low).
            val listWidth = (maxWidth * 0.36f).coerceIn(280.dp, 380.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ListCard(Modifier.width(listWidth).fillMaxHeight()) {
                    // The kind pills: All with its count, then the chain's kinds (the row scrolls on a narrow list).
                    PillRow(Modifier.padding(12.dp)) {
                        for (k in DOC_PILLS) {
                            val label = if (k == null) word(W.range_all) + " · " + invoiceCount else docKindWord(k)
                            Chip(label, kind == k) { kind = k }
                        }
                    }
                    Rule()
                    // A kind pill with nothing under it (the book has documents of other kinds).
                    if (shown.isEmpty()) Text(word(W.no_entries_yet), Modifier.padding(16.dp), color = c.textMuted, fontSize = 13.sp)
                    LazyColumn(Modifier.fillMaxSize(), state = invoiceListState) {
                        itemsIndexed(shown, key = { _, it -> it.id }) { _, i ->
                            val on = i.id == open?.id
                            val cancelled = i.status == com.innovation313.roshankhata.data.DocStatus.CANCELLED
                            Row(
                                Modifier.fillMaxWidth().background(eased(if (on) Look.accentTint else androidx.compose.ui.graphics.Color.Transparent))
                                    .press { chosen = i.id }.padding(end = 12.dp, top = 9.dp, bottom = 9.dp)
                                    // A cancelled document is read as a record, not a bill: muted and struck.
                                    .alpha(if (cancelled) 0.55f else 1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(3.dp).height(22.dp).background(eased(if (on) Look.accent else androidx.compose.ui.graphics.Color.Transparent), Look.ctl))
                                androidx.compose.foundation.layout.Spacer(Modifier.width(9.dp))
                                Avatar(i.customerName, 34, id = faceId(i.customerName))
                                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(Format.name(i.customerName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    // The number alone says the kind (Q-, SO-, DC-, INV-).
                                    Text(Format.ltr(i.invoiceNumber) + "  ·  " + Format.dateOnly(i.invoiceDate), color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                // The amount, and under it the tag that says where the document stands (30 Oct 2026, the
                                // proof picture: beside the number the tag ran under the amount and read "Con").
                                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        Format.money(i.grandTotal), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                                        textDecoration = if (cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )
                                    DocStatusTag(i.status)
                                }
                            }
                            Rule()
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (open != null) {
                        // The saved row (what was received, if the owner recorded it; the document it came from)
                        // and its pages in its own design.
                        var full by remember(open.id) { mutableStateOf<com.innovation313.roshankhata.data.Invoice?>(null) }
                        var source by remember(open.id) { mutableStateOf<com.innovation313.roshankhata.data.Invoice?>(null) }
                        var sheets by remember(open.id) { mutableStateOf<Sheets?>(null) }
                        // The design on show: the saved one until the owner steps through the
                        // phone's ten (only one could be seen before, 20 Oct); Apply keeps it.
                        var look by remember(open.id) { mutableStateOf<Int?>(null) }
                        LaunchedEffect(open.id, open.grandTotal, open.invoiceDate, open.status, look) {
                            sheets = null
                            val row = withContext(Dispatchers.IO) { dao.getInvoice(open.id)?.let { it to dao.invoiceItems(open.id) } }
                            full = row?.first
                            source = row?.first?.sourceDocId?.let { withContext(Dispatchers.IO) { dao.getInvoice(it) } }
                            // What Print and WhatsApp send: the design, else the plain layout (sheetOf).
                            sheets = row?.let { (inv, lines) ->
                                withContext(Preview.drawing) { runCatching { Sheets.of(sheetOf(inv.copy(templateId = look ?: inv.templateId), lines)) }.getOrNull() }
                            }
                        }
                        val status = full?.status ?: open.status
                        val kindNow = full?.kind ?: open.kind
                        // The head and every action stay in sight; the pages take the height left (Marhala 4: the
                        // picture began under a head, a row of buttons and the stepper, its top third in view).
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(open.customerName, 40, id = faceId(open.customerName))
                            Column(Modifier.weight(1f)) {
                                Text(Format.name(open.customerName), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        listOfNotNull(docKindWord(kindNow), Format.ltr(open.invoiceNumber), Format.dateOnly(open.invoiceDate), full?.dueDate?.let { word(W.due_date_set, Format.dateOnly(it)) }).joinToString("  ·  "),
                                        color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    DocStatusTag(status)
                                }
                                // The document this one was made from: a click opens it (whatever pill is on).
                                source?.let { from ->
                                    Text(
                                        word(W.doc_converted_from, Format.ltr(from.invoiceNumber)),
                                        Modifier.press { kind = null; chosen = from.id },
                                        color = Look.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Format.money(open.grandTotal), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                // Received is shown only when the owner recorded it on the invoice: nothing
                                // recorded is not the same as nothing paid (Invoice.receivedAmount).
                                full?.receivedAmount?.let { got ->
                                    Text(word(W.invoice_received_line, Format.money(got)), color = c.iOwe, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                    val due = open.grandTotal - got
                                    if (Money.isPositive(due)) Text(word(W.invoice_balance_due_line, Format.money(due)), color = c.owedToMe, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                        val live = com.innovation313.roshankhata.data.DocActions.canEdit(status)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FluentButton(word(W.ledger_pdf_button), { print(open.id) }, kind = ButtonKind.ACCENT, icon = "ic_print")
                            IconAction("ic_action_whatsapp", word(W.invoice_send_whatsapp), { sendOnWhatsApp(open.id) })
                            if (live) {
                                IconAction("ic_edit", word(W.edit), { Gates.edit { edit(open.id) } })
                                IconAction("ic_delete", word(W.delete), { Gates.edit { deletingInvoice = open } }, tint = c.owedToMe)
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
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        // Where the document goes next, by its status (DocActions): the same three words on the phone.
                        val target = com.innovation313.roshankhata.data.DocActions.convertTarget(kindNow, status)
                        val mayAccept = com.innovation313.roshankhata.data.DocActions.canAccept(kindNow, status)
                        if (target != null || mayAccept || com.innovation313.roshankhata.data.DocActions.canCancel(status)) {
                            PillRow(Modifier.fillMaxWidth()) {
                                if (mayAccept) FluentButton(word(W.doc_accept), { Gates.edit { accept(open.id) } }, icon = "ic_check_plain")
                                if (target != null) FluentButton(word(W.doc_convert_to, docKindWord(target)), { Gates.edit { convert(open.id, target) } }, icon = "ic_chevron_right")
                                if (com.innovation313.roshankhata.data.DocActions.canCancel(status)) FluentButton(word(W.mark_cancelled), { Gates.edit { cancelling = open } }, kind = ButtonKind.DANGER, icon = "ic_close")
                            }
                        }
                        if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Box(Modifier.weight(1f).fillMaxWidth().clip(Look.ctl).background(Look.subtle).border(1.dp, Look.strokeCard, Look.ctl)) {
                            val pages = sheets
                            if (pages != null) InvoicePages(pages, open.invoiceNumber)
                            else LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                        }
                    }
                }
            }
        }
    }
}

/**
 * An invoice's pages, as many as its design drew (a long invoice's totals are on its second; only the first
 * was shown), one under another, each drawn as sharp as the pane is wide when it scrolls into sight — 1.5 to
 * 3 pixels a point (it was 3 whatever the pane, 18 MB a page). [sheets] is what Print and WhatsApp send.
 */
@Composable
private fun InvoicePages(sheets: Sheets, title: String) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val across = constraints.maxWidth - 16 * density
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sheets.count) { i ->
                val w = sheets.width(i)
                val h = sheets.height(i)
                val scale = (across / w).coerceIn(1.5f, 3f)
                var picture by remember(sheets, i, scale) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                LaunchedEffect(sheets, i, scale) {
                    picture = withContext(Preview.drawing) { runCatching { sheets.picture(i, scale).toComposeImageBitmap() }.getOrNull() }
                }
                val shape = Modifier.fillMaxWidth().aspectRatio(w / h)
                val pic = picture
                if (pic == null) Box(shape.background(androidx.compose.ui.graphics.Color.White))
                else androidx.compose.foundation.Image(
                    pic, contentDescription = title, modifier = shape,
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                )
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
fun DesignStepper(shown: Int, saved: Int?, onShow: (Int) -> Unit, onApply: ((Int) -> Unit)? = null, modifier: Modifier = Modifier.fillMaxWidth()) {
    val c = LocalRoshanColors.current
    val names = com.innovation313.roshankhata.desktop.invoice.InvoiceDesigns.NAMES
    val at = names.indexOfFirst { it.first == shown }.coerceAtLeast(0)
    fun step(by: Int) = onShow(names[(at + by + names.size) % names.size].first)
    Row(
        modifier.clip(Look.ctl).background(Look.subtle).padding(horizontal = 6.dp, vertical = 4.dp),
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

/**
 * Stock expiring within the phone's warning window (ExpiryWindow), soonest first; expired in red. Each batch
 * with what is left of it by the one rule (Fefo.expiring, Marhala 3: sold over the counter and the khata,
 * back, written off, never more than the shelf) — the list, its figure and the write-off say the same, and a
 * batch with nothing left is not on it.
 */
@Composable
fun ExpiryPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val words = com.innovation313.roshankhata.words.LocalWords.current
    val cutoff = remember { System.currentTimeMillis() + com.innovation313.roshankhata.data.ExpiryWindow.WARN_MS }
    var buyersOf by remember { mutableStateOf<Long?>(null) }
    buyersOf?.let { BatchBuyersDialog(dao, it) { buyersOf = null } }
    val scope = rememberCoroutineScope()
    // Read again whenever stock moves anywhere (a sale here or on the phone, a return, a count) and after a write-off.
    var moved by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { dao.observeBatchMoves().collect { moved++ } }
    var tick by remember { mutableStateOf(0) }
    var batches by remember { mutableStateOf<List<com.innovation313.roshankhata.data.ExpiringBatch>>(emptyList()) }
    LaunchedEffect(moved, tick) {
        batches = withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.Fefo.expiring(dao, cutoff) }.getOrNull() } ?: batches
    }
    var writingOff by remember { mutableStateOf<com.innovation313.roshankhata.data.ExpiringBatch?>(null) }
    // Who gave their PIN for the write-off (null: the user's own right); the DAO refuses one nobody allowed.
    var writingOffBy by remember { mutableStateOf<String?>(null) }
    // A lot going back to its supplier (the phone's chooser: who bought it, send it back, write it off), and what
    // was said after.
    var returning by remember { mutableStateOf<Long?>(null) }
    var told by remember { mutableStateOf<String?>(null) }
    returning?.let { id -> BatchReturnDialog(dao, id, onClose = { returning = null }) { said -> returning = null; told = said; tick++ } }
    fun leftWords(b: com.innovation313.roshankhata.data.ExpiringBatch): String =
        com.innovation313.roshankhata.ui.Calc.trim(b.quantity) + com.innovation313.roshankhata.data.UnitWords.label(b.unit).let { u -> if (u.isEmpty()) "" else " $u" }
    writingOff?.let { b ->
        AskDialog(
            word(W.expiry_write_off), word(W.expiry_write_off_confirm, leftWords(b), b.batchNumber?.takeIf { it.isNotBlank() } ?: b.productName),
            word(W.expiry_write_off), danger = true, subtitle = b.productName,
            onConfirm = {
                writingOff = null
                val by = writingOffBy
                // Fefo.writeOff reads the batch again as it writes: what is left at that moment goes, nothing more.
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.Fefo.writeOff(dao, b.itemId, b.batchNumber, allowedBy = by) } }
                        .onFailure { told = failText(words, it) }
                    tick++
                }
            },
            onDismiss = { writingOff = null }
        )
    }
    // The trace above the list, never a card inside it, and the page's own empty state when nothing expires
    // (25 Oct 2026, the audit: the empty state never showed, and the trace sat in the list as a card in a card).
    RecordList(word(W.expiring_stock), told ?: word(W.expiring_subtitle), batches.isEmpty(), word(W.no_expiring), emptyIcon = "ic_tile_expiring",
        head = { BatchTraceCard(dao) { buyersOf = it } }) {
        items(batches, key = { it.itemId }) { b ->
            Row(Modifier.fillMaxWidth().press { buyersOf = b.itemId }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.productName, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (b.batchNumber?.takeIf { it.isNotBlank() }?.let { word(W.batch_label, it) } ?: word(W.batch_none)) +
                            "  ·  " + word(W.expiry_left, leftWords(b)),
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
                    // Every lot can go back to its supplier or be written off, as the phone offers it; expired ones
                    // say so in red above.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FluentButton(word(W.return_action), { Gates.edit { returning = b.itemId } }, kind = ButtonKind.SUBTLE)
                        FluentButton(word(W.expiry_write_off), {
                            Gates.needBy(com.innovation313.roshankhata.data.Access.Permission.STOCK_ADJUST) { by -> writingOffBy = by; writingOff = b }
                        }, kind = ButtonKind.DANGER)
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
    // Marking a plan complete changes what is written, so it asks the owner's rule and then the owner (Marhala
    // 4, the audit: neither was asked).
    var closing by remember { mutableStateOf<com.innovation313.roshankhata.data.PlanProgress?>(null) }
    acting?.let { p ->
        // In the app's frame (Marhala 4, the audit: these were Material's dialogs).
        val actions = buildList<Triple<String, Boolean, () -> Unit>> {
            if (!p.isClosed) {
                add(Triple(word(W.record_payment), false) { paying = p })
                add(Triple(word(W.plan_next_change), false) { dating = p to (p.nextDueDate ?: com.innovation313.roshankhata.data.PlanSchedule.suggestNext(null)) })
                if (p.nextDueDate != null) add(Triple(word(W.plan_next_clear), false) {
                    scope.launch { withContext(Dispatchers.IO) { dao.setPlanNextDue(p.id, null) } }
                })
                add(Triple(word(W.close_plan), false) { Gates.edit { closing = p } })
            }
            add(Triple(word(W.delete_plan), true) { Gates.edit { deleting = p } })
        }
        AppDialog(onCloseRequest = { acting = null }, title = word(W.plan_actions), state = rememberDialogState(width = fitW(440.dp), height = fitH((190 + 49 * actions.size).dp))) {
            DialogFrame(word(W.plan_actions), subtitle = Format.name(p.partyName), onClose = { acting = null }, footer = {
                FluentButton(word(W.cancel), { acting = null }, kind = ButtonKind.STANDARD)
            }) {
                Column {
                    actions.forEachIndexed { i, (label, removes, go) ->
                        Text(
                            label,
                            Modifier.fillMaxWidth().press(Look.ctl) { acting = null; go() }.padding(horizontal = 8.dp, vertical = 13.dp),
                            color = if (removes) c.owedToMe else c.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium
                        )
                        if (i < actions.lastIndex) Rule()
                    }
                }
            }
        }
    }
    closing?.let { p ->
        AskDialog(
            title = word(W.close_plan),
            subtitle = Format.name(p.partyName),
            // What is being closed, said in figures: a plan with money left can still be marked complete.
            text = word(W.plan_paid, Format.money(p.paidSoFar), Format.money(p.totalAmount)) + "  \u00b7  " + word(W.plan_remaining, Format.money(p.remaining)),
            confirm = word(W.close_plan),
            onConfirm = {
                closing = null
                scope.launch { withContext(Dispatchers.IO) { dao.getPlan(p.id)?.let { dao.updatePlan(it.copy(isClosed = true, closedAt = System.currentTimeMillis())) } } }
            },
            onDismiss = { closing = null }
        )
    }
    paying?.let { p ->
        var amountText by remember(p.id) { mutableStateOf(p.installmentAmount?.let { com.innovation313.roshankhata.ui.Calc.trim(it) }.orEmpty()) }
        var note by remember(p.id) { mutableStateOf("") }
        val amount = com.innovation313.roshankhata.ui.Calc.evalAmount(amountText)
        val over = amount != null && amount > p.remaining
        fun pay() {
            val a = amount ?: return
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
        }
        AppDialog(onCloseRequest = { paying = null }, title = word(W.record_payment_title), state = rememberDialogState(width = fitW(480.dp), height = fitH(440.dp))) {
            DialogFrame(word(W.record_payment_title), subtitle = Format.name(p.partyName), onClose = { paying = null }, footer = {
                FluentButton(word(W.cancel), { paying = null }, kind = ButtonKind.STANDARD)
                FluentButton(word(if (over) W.proceed_anyway else W.save), { pay() }, kind = ButtonKind.ACCENT, icon = "ic_check_plain", enabled = amount != null)
            }) {
                Text(word(W.record_payment_help, p.partyName), color = c.textMuted, fontSize = 13.sp)
                FluentField(amountText, { amountText = it }, Modifier.fillMaxWidth(), label = word(W.amount), prefix = com.innovation313.roshankhata.data.Currency.symbol, big = true,
                    help = amount?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.note_optional))
                // More than is left: said before Save, still posted in full if the owner goes ahead.
                if (over) Text(word(W.plan_overpay_warning, Format.money(p.remaining)), color = c.owedToMe, fontSize = 13.sp)
            }
        }
    }
    deleting?.let { p ->
        AskDialog(
            title = word(W.delete_plan), subtitle = Format.name(p.partyName), text = word(W.delete_plan_confirm), confirm = word(W.delete), danger = true, height = 320,
            onConfirm = {
                deleting = null
                scope.launch { withContext(Dispatchers.IO) { dao.softDeletePlan(p.id) } }
            },
            onDismiss = { deleting = null }
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
    // In the app's frame (Marhala 4, the audit): the list takes the dialog's height, faces in the party's colours.
    val title = product.name + "  \u00b7  " + word(W.promo_subtitle)
    AppDialog(onCloseRequest = onClose, title = title, state = rememberDialogState(width = fitW(560.dp), height = fitH(620.dp))) {
        DialogFrame(title, onClose = onClose, scroll = false, footer = { FluentButton(word(W.ok), onClose, kind = ButtonKind.STANDARD) }) {
            PillRow {
                listOf(W.window_same_season, W.window_last_year, W.window_ever).forEachIndexed { i, label ->
                    FluentPill(word(label), i == choice) { choice = i }
                }
            }
            if (buyers.isEmpty()) Text(word(W.no_promo_customers), color = c.textMuted)
            else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(buyers, key = { it.id }) { b ->
                    val number = b.phone?.let { com.innovation313.roshankhata.data.WhatsAppNumber.of(it) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(b.name, 32, id = b.id)
                        Column(Modifier.weight(1f)) {
                            Text(Format.name(b.name), color = c.ink, fontWeight = FontWeight.Bold)
                            b.phone?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 12.sp) }
                        }
                        FluentButton(word(W.promo_send), {
                            val text = words.text(W.promo_message, b.name, product.name)
                            runCatching {
                                java.awt.Desktop.getDesktop().browse(java.net.URI("https://wa.me/$number?text=" +
                                    java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")))
                            }
                        }, kind = ButtonKind.SUBTLE, icon = "ic_action_whatsapp", enabled = number != null)
                    }
                    Rule()
                }
            }
        }
    }
}

/** A new plan: who (from the book), the total to clear, each instalment, the next date, a note. Adds nothing to the balance. */
@Composable
private fun PlanAddDialog(parties: List<PartyWithBalance>, onSave: (com.innovation313.roshankhata.data.PaymentPlan) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    var partyId by remember { mutableStateOf<Long?>(null) }
    var total by remember { mutableStateOf("") }
    var each by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val needParty = word(W.select_party_first)
    val needAmount = word(W.enter_valid_amount)
    fun save() {
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
    // In the app's frame with Save in its foot (Marhala 4, the audit); the party is picked as on every form,
    // his balance with its side's words (it was a bare figure, which way it ran unsaid).
    AppDialog(onCloseRequest = onCancel, title = word(W.add_plan), state = rememberDialogState(width = fitW(560.dp), height = fitH(700.dp))) {
        DialogFrame(word(W.add_plan), onClose = onCancel, footerStart = { problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) } }, footer = {
            FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
            FluentButton(word(W.save), { save() }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
        }) {
            Text(word(W.plan_dialog_help), color = c.textMuted, fontSize = 12.sp)
            PartyPicker(parties, partyId, { partyId = it; problem = null }, word(W.cheque_party_hint), word(W.cheque_party_hint), Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FluentField(total, { total = it; problem = null }, Modifier.weight(1f), label = word(W.plan_total_hint), prefix = com.innovation313.roshankhata.data.Currency.symbol,
                    help = com.innovation313.roshankhata.ui.Calc.evalAmount(total)?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                FluentField(each, { each = it }, Modifier.weight(1f), label = word(W.plan_installment_hint), prefix = com.innovation313.roshankhata.data.Currency.symbol,
                    help = com.innovation313.roshankhata.ui.Calc.evalAmount(each)?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            }
            DateBox(word(W.plan_pick_next_due), due?.let { Format.dateOnly(it) }, word(W.pick_date), { picking = true }, Modifier.fillMaxWidth())
            FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.note_optional), singleLine = false)
        }
    }
    if (picking) DayPicker(due, onPick = { due = it; picking = false }, onDismiss = { picking = false })
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
