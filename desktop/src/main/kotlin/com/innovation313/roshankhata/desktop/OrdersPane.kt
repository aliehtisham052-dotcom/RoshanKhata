package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.CounterDraft
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.OrderStatus
import com.innovation313.roshankhata.data.OrderSummary
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.PurchaseOrder
import com.innovation313.roshankhata.data.PurchaseOrderItem
import com.innovation313.roshankhata.data.PurchaseOrderWithLines
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.data.Valuation
import com.innovation313.roshankhata.data.OrderDraft
import com.innovation313.roshankhata.data.orderText
import com.innovation313.roshankhata.data.WhatsAppNumber
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder

/**
 * Purchase orders (POS Phase 6): what the shop asked a supplier for, sent as
 * a WhatsApp message or a sheet, marked sent, and turned into a supplier
 * bill when the goods arrive (receiveOrder — the same write a bill typed by
 * hand makes, so stock and the supplier's account follow as they always
 * have). "Order the low stock" starts an order from the products under
 * their warning level.
 */
@Composable
fun OrdersPane(dao: KhataDao, parties: List<PartyWithBalance>, words: Words, rtl: Boolean, shopName: String?, onChanged: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val orders by remember { dao.observeOrders() }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<OrderDraft?>(null) }
    var receiving by remember { mutableStateOf<PurchaseOrderWithLines?>(null) }
    var told by remember { mutableStateOf<String?>(null) }
    val suppliers = parties.filter { !it.isCustomer }

    // A cashier's order waits for a manager's PIN, as the counter's other stock work does (23 Oct 2026,
    // the review: it was refused flatly). The buttons show for everyone; each asks when it is pressed.
    fun newOrder(lowOnly: Boolean) = Gates.need(Access.Permission.STOCK_ADJUST) {
        scope.launch {
            val products = withContext(Dispatchers.IO) { dao.productsOnce() }
            val draft = OrderDraft(null, products)
            if (lowOnly) {
                val low = withContext(Dispatchers.IO) { runCatching { Valuation.compute(dao).low }.getOrDefault(emptyList()) }
                low.forEach { r -> products.firstOrNull { it.id == r.productId }?.let { p ->
                    val want = ((r.lowStockLevel ?: 0.0) * 2 - (r.onHand ?: 0.0)).coerceAtLeast(1.0)
                    draft.add(p, kotlin.math.ceil(want))
                } }
            }
            editing = draft
        }
    }

    editing?.let { draft ->
        OrderDialog(dao, draft, suppliers, onClose = { editing = null }) { id ->
            editing = null; onChanged()
            told = words.text(W.po_title) + " " + id
        }
    }
    receiving?.let { o -> ReceiveDialog(dao, o, words, onClose = { receiving = null }) { told = it; receiving = null; onChanged() } }

    FluentPage(word(W.po_title), told ?: word(W.po_subtitle), scroll = false, actions = {
        FluentButton(word(W.po_from_low), { newOrder(true) }, kind = ButtonKind.STANDARD)
        FluentButton(word(W.po_new), { newOrder(false) }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
    }) {
        if (orders.isEmpty()) EmptyState("ic_tile_bills", word(W.po_none), null, Modifier.weight(1f)) {
            FluentButton(word(W.po_new), { newOrder(false) }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
        } else ListCard(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(orders, key = { it.id }) { o ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(Format.ltr(o.orderNumber), Modifier.width(100.dp), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Column(Modifier.weight(1f)) {
                            Text(Format.name(o.partyName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Format.dateTime(o.timestamp) + " · " + o.itemCount, color = c.textMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        Tag(statusWord(o.status), if (o.status == OrderStatus.RECEIVED) Look.accent else c.inkSoft, if (o.status == OrderStatus.RECEIVED) Look.accentTint else Look.subtle)
                        OrderActions(dao, o, suppliers, words, rtl, shopName,
                            onEdit = { Gates.need(Access.Permission.STOCK_ADJUST) { scope.launch { val full = withContext(Dispatchers.IO) { dao.orderWithLines(o.id) } ?: return@launch; editing = OrderDraft(full, withContext(Dispatchers.IO) { dao.productsOnce() }) } } },
                            onReceive = { Gates.need(Access.Permission.STOCK_ADJUST) { scope.launch { receiving = withContext(Dispatchers.IO) { dao.orderWithLines(o.id) } } } },
                            onChanged = { onChanged() }, onTell = { told = it })
                    }
                    Rule()
                }
            }
        }
    }
}

@Composable
private fun statusWord(s: String): String = word(
    when (s) {
        OrderStatus.SENT -> W.po_status_sent
        OrderStatus.RECEIVED -> W.po_status_received
        OrderStatus.CLOSED -> W.po_status_closed
        else -> W.po_status_draft
    }
)

@Composable
private fun OrderActions(
    dao: KhataDao, o: OrderSummary, suppliers: List<PartyWithBalance>, words: Words, rtl: Boolean, shopName: String?,
    onEdit: () -> Unit, onReceive: () -> Unit, onChanged: () -> Unit,
    /** What happened, said in the page's line (a send that did not open, an order copied for a supplier with no number). */
    onTell: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    fun markSent() { scope.launch { withContext(Dispatchers.IO) { dao.setOrderStatus(o.id, OrderStatus.SENT) }; onChanged() } }
    fun text(): String? {
        var out: String? = null
        kotlinx.coroutines.runBlocking { out = withContext(Dispatchers.IO) { dao.orderWithLines(o.id)?.let { orderText(words, shopName, it, o.partyName) } } }
        return out
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (o.status == OrderStatus.DRAFT || o.status == OrderStatus.SENT) {
            val number = suppliers.firstOrNull { it.id == o.partyId }?.phone?.let { WhatsAppNumber.of(it) }
            // Marked sent only once WhatsApp opened with it (25 Oct 2026, the audit: with no number the order was
            // copied and still marked sent). No number: copied, said, and left for Mark sent.
            FluentButton(word(W.po_send), {
                val t = text() ?: return@FluentButton
                if (number != null) {
                    runCatching { java.awt.Desktop.getDesktop().browse(URI("https://wa.me/$number?text=" + URLEncoder.encode(t, "UTF-8").replace("+", "%20"))) }
                        .onSuccess { markSent() }.onFailure { onTell(failText(words, it)) }
                } else {
                    runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(t), null) }
                        .onSuccess { onTell(words.text(W.po_copied_no_number)) }.onFailure { onTell(failText(words, it)) }
                }
            }, kind = ButtonKind.SUBTLE, icon = "ic_share")
            if (o.status == OrderStatus.DRAFT) FluentButton(word(W.po_mark_sent), { markSent() }, kind = ButtonKind.SUBTLE)
            IconAction("ic_print", word(W.pdf_preview), {
                scope.launch {
                    val full = withContext(Dispatchers.IO) { dao.orderWithLines(o.id) } ?: return@launch
                    StatementPrint.show(
                        TablePrint(rtl, shopName ?: words.text(W.app_name), o.orderNumber + "  ·  " + o.partyName + "  ·  " + Format.dateOnly(o.timestamp),
                            listOf(words.text(W.pos_col_item), words.text(W.pos_col_qty), words.text(W.pos_col_rate)),
                            full.items.map { listOf(it.itemName, Calc.trim(it.quantity) + (com.innovation313.roshankhata.data.UnitWords.label(it.unit).let { u -> if (u.isEmpty()) "" else " $u" }), it.rate?.let { r -> Format.money(r) } ?: "") }
                        ) { p, of -> words.text(W.pdf_stmt_page, p, of) }, o.orderNumber)
                }
            })
            if (o.status == OrderStatus.DRAFT) FluentButton(word(W.edit), onEdit, kind = ButtonKind.SUBTLE)
            FluentButton(word(W.po_receive), onReceive, kind = ButtonKind.ACCENT)
            IconAction("ic_close", word(W.po_close), { Gates.need(Access.Permission.STOCK_ADJUST) { scope.launch { withContext(Dispatchers.IO) { dao.setOrderStatus(o.id, OrderStatus.CLOSED) }; onChanged() } } })
        }
    }
}

@Composable
private fun OrderDialog(dao: KhataDao, draft: OrderDraft, suppliers: List<PartyWithBalance>, onClose: () -> Unit, saved: (String) -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val title = word(if (draft.existing == null) W.po_new else W.edit)
    AppDialog(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = fitW(720.dp), height = fitH(760.dp))) {
        DialogFrame(title, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.save), {
                scope.launch {
                    val order = PurchaseOrder(id = draft.existing?.order?.id ?: 0, partyId = draft.partyId!!, note = draft.note.trim().ifBlank { null }, createdBy = Access.userName)
                    val id = withContext(Dispatchers.IO) { runCatching { dao.saveOrder(order, draft.toItems()) }.getOrNull() } ?: return@launch
                    saved(withContext(Dispatchers.IO) { dao.getOrder(id)?.orderNumber }.orEmpty())
                }
            }, kind = ButtonKind.ACCENT, enabled = draft.valid)
        }) {
            PartyPicker(suppliers, draft.partyId, onPick = { draft.partyId = it }, label = word(W.po_supplier), hint = word(W.search_parties))
            FluentField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = word(W.pos_search_hint), leadingIcon = "ic_search")
            if (query.isNotBlank()) Column(Modifier.fillMaxWidth().heightIn(max = 180.dp).card().verticalScroll(rememberScrollState())) {
                CounterDraft.search(draft.products, query, 8).forEach { p ->
                    Row(Modifier.fillMaxWidth().press { draft.add(p); query = "" }.padding(8.dp)) { Text(p.name, color = c.ink, fontSize = 14.sp) }
                    Rule()
                }
            }
            if (draft.lines.isEmpty()) Text(word(W.po_lines_empty), Modifier.padding(12.dp), color = c.textMuted)
            draft.lines.forEach { l ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (l.productId == null) FluentField(l.name, { l.name = it }, Modifier.weight(3f), placeholder = word(W.item_name_hint))
                    else Text(l.name, Modifier.weight(3f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FluentField(l.qty, { l.qty = it }, Modifier.weight(1f), label = word(W.pos_col_qty) + (com.innovation313.roshankhata.data.UnitWords.label(l.unit).let { u -> if (u.isEmpty()) "" else " ($u)" }), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                    FluentField(l.rate, { l.rate = it }, Modifier.weight(1f), label = word(W.pos_col_rate), placeholder = "–", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                    IconAction("ic_close", word(W.delete), { draft.lines.remove(l) })
                }
            }
            FluentButton(word(W.item_name_hint), { draft.lines += OrderDraft.Line(null, "", "1", null, "") }, Modifier.padding(top = 6.dp), kind = ButtonKind.SUBTLE, icon = "ic_add_plain")
            FluentField(draft.note, { draft.note = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = word(W.note_optional))
        }
    }
}

/** The goods arrived: each line as received (quantity, rate, batch, expiry), the bill's number and total. */
@Composable
private fun ReceiveDialog(dao: KhataDao, o: PurchaseOrderWithLines, words: Words, onClose: () -> Unit, done: (String) -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    class Got(val item: PurchaseOrderItem) {
        var qty by mutableStateOf(Calc.trim(item.quantity))
        var rate by mutableStateOf(item.rate?.let { Calc.trim(it) }.orEmpty())
        var batch by mutableStateOf("")
        // The batch's expiry as it arrived (25 Oct 2026, the audit: received goods could not carry one).
        var expiry by mutableStateOf<Long?>(null)
    }
    val got = remember { o.items.map { Got(it) } }
    var pickingFor by remember { mutableStateOf<Got?>(null) }
    var billNumber by remember { mutableStateOf("") }
    val total = got.sumOf { (Calc.evalAmount(it.qty) ?: 0.0) * (Calc.evalAmount(it.rate) ?: 0.0) }
    val title = word(W.po_receive)
    AppDialog(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = fitW(720.dp), height = fitH(640.dp))) {
        DialogFrame(title, subtitle = o.order.orderNumber, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.save), {
                scope.launch {
                    val items = got.mapNotNull { g ->
                        val q = Calc.evalAmount(g.qty) ?: return@mapNotNull null
                        if (q <= 0.0) null else BillItem(billId = 0, productName = g.item.itemName, quantity = q, unit = g.item.unit, rate = Calc.evalAmount(g.rate),
                            batchNumber = g.batch.trim().ifBlank { null }, expiryDate = g.expiry, productId = g.item.productId)
                    }
                    val bill = SupplierBill(partyId = o.order.partyId, billNumber = billNumber.trim().ifBlank { null }, totalAmount = com.innovation313.roshankhata.data.LineMath.round(total), note = o.order.orderNumber)
                    val entry = if (total > 0.0) com.innovation313.roshankhata.data.LedgerEntry(partyId = o.order.partyId, amount = com.innovation313.roshankhata.data.LineMath.round(total), isGiven = false, note = o.order.orderNumber, entryNumber = "") else null
                    val r = withContext(Dispatchers.IO) { runCatching { dao.receiveOrder(o.order.id, bill, items, entry) } }
                    r.onSuccess { done(words.text(W.po_status_received) + " " + o.order.orderNumber) }.onFailure { done(failText(words, it)) }
                }
            }, kind = ButtonKind.ACCENT, enabled = got.any { (Calc.evalAmount(it.qty) ?: 0.0) > 0.0 })
        }) {
            FluentField(billNumber, { billNumber = it }, Modifier.fillMaxWidth(), label = word(W.bill_number_hint))
            got.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(g.item.itemName, Modifier.weight(3f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FluentField(g.qty, { g.qty = it }, Modifier.weight(1f), label = word(W.pos_col_qty), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                    FluentField(g.rate, { g.rate = it }, Modifier.weight(1f), label = word(W.pos_col_rate), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                    FluentField(g.batch, { g.batch = it }, Modifier.weight(1f), label = word(W.pdf_reg_batch, ""))
                    DateBox(word(W.expiry_date), g.expiry?.let { Format.dateOnly(it) }, word(W.pick_date), { pickingFor = g }, Modifier.weight(1.2f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(word(W.pos_grand_total), Modifier.weight(1f), color = c.inkSoft)
                Text(Format.money(total), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    pickingFor?.let { g -> DayPicker(g.expiry, onPick = { g.expiry = it; pickingFor = null }, onDismiss = { pickingFor = null }, title = g.item.itemName) }
}
