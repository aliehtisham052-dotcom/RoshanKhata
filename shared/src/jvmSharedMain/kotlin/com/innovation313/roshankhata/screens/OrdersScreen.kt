package com.innovation313.roshankhata.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.CounterDraft
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.LineMath
import com.innovation313.roshankhata.data.OrderDraft
import com.innovation313.roshankhata.data.OrderStatus
import com.innovation313.roshankhata.data.OrderSummary
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PurchaseOrder
import com.innovation313.roshankhata.data.PurchaseOrderItem
import com.innovation313.roshankhata.data.PurchaseOrderWithLines
import com.innovation313.roshankhata.data.SupplierBill
import com.innovation313.roshankhata.data.Valuation
import com.innovation313.roshankhata.data.orderText
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Purchase orders on the phone (POS Phase 6, the phone's half): the list,
 * a new order (from the low stock or empty), the order sent as a message
 * ([onSend] — the phone opens WhatsApp or the share sheet), and the goods
 * received as a supplier bill (receiveOrder). The same OrderDraft and
 * orderText as the computer. [onNeed] asks for the PIN of someone who may
 * (the phone's AccessUi.allowed) and runs the action after; without it a
 * cashier is told no, as before (23 Oct 2026, the review).
 */
@Composable
fun OrdersScreen(
    dao: KhataDao, shopName: String?, onSend: (phone: String?, text: String) -> Unit, modifier: Modifier = Modifier,
    onNeed: ((Access.Permission, () -> Unit) -> Unit)? = null,
    /**
     * The platform's day picker, for a received batch's expiry (Marhala 4: the phone's receive form had none, so a
     * lot that came in through an order never reached Expiry). Null hides the box.
     */
    pickDay: ((startAt: Long, onPicked: (Long) -> Unit) -> Unit)? = null
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val words = LocalWords.current
    val orders by remember { dao.observeOrders() }.collectAsState(emptyList())
    var suppliers by remember { mutableStateOf<List<PartyWithBalance>>(emptyList()) }
    var editing by remember { mutableStateOf<OrderDraft?>(null) }
    var receiving by remember { mutableStateOf<PurchaseOrderWithLines?>(null) }
    var told by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(orders) { suppliers = withContext(Dispatchers.IO) { dao.partiesWithBalanceOnce().filter { !it.isCustomer } } }
    val may = Access.can(Access.Permission.STOCK_ADJUST)
    // The actions show when the user may, or when a manager's PIN can be asked for.
    val offered = may || onNeed != null
    fun gated(then: () -> Unit) {
        val ask = onNeed
        when {
            Access.can(Access.Permission.STOCK_ADJUST) -> then()
            ask != null -> ask(Access.Permission.STOCK_ADJUST, then)
            else -> told = words.text(W.access_denied)
        }
    }

    fun newOrder(lowOnly: Boolean) = gated {
        scope.launch {
            val products = withContext(Dispatchers.IO) { dao.productsOnce() }
            val draft = OrderDraft(null, products)
            if (lowOnly) {
                val low = withContext(Dispatchers.IO) { runCatching { Valuation.compute(dao).low }.getOrDefault(emptyList()) }
                low.forEach { r -> products.firstOrNull { it.id == r.productId }?.let { p ->
                    draft.add(p, kotlin.math.ceil(((r.lowStockLevel ?: 0.0) * 2 - (r.onHand ?: 0.0)).coerceAtLeast(1.0)))
                } }
            }
            editing = draft
        }
    }

    editing?.let { draft ->
        OrderEditor(draft, suppliers, onCancel = { editing = null }) {
            scope.launch {
                val order = PurchaseOrder(id = draft.existing?.order?.id ?: 0, partyId = draft.partyId!!, note = draft.note.trim().ifBlank { null }, createdBy = Access.userName)
                val id = withContext(Dispatchers.IO) { runCatching { dao.saveOrder(order, draft.toItems()) }.getOrNull() }
                told = id?.let { withContext(Dispatchers.IO) { dao.getOrder(it)?.orderNumber } }
                editing = null
            }
        }
        return
    }
    receiving?.let { o ->
        ReceiveEditor(o, pickDay, onCancel = { receiving = null }) { items, billNumber, total ->
            scope.launch {
                val bill = SupplierBill(partyId = o.order.partyId, billNumber = billNumber, totalAmount = LineMath.round(total), note = o.order.orderNumber)
                val entry = if (total > 0.0) LedgerEntry(partyId = o.order.partyId, amount = LineMath.round(total), isGiven = false, note = o.order.orderNumber, entryNumber = "") else null
                val r = withContext(Dispatchers.IO) { runCatching { dao.receiveOrder(o.order.id, bill, items, entry) } }
                told = r.fold({ words.text(W.po_status_received) + " " + o.order.orderNumber }, { it.message })
                receiving = null
            }
        }
        return
    }

    Column(modifier.fillMaxSize().background(c.pageBg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { newOrder(false) }, colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen)) { Text(word(W.po_new), color = Color.White, fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { newOrder(true) }) { Text(word(W.po_from_low), color = c.ink) }
        }
        told?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = c.inkSoft, fontSize = 13.sp) }
        if (orders.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(word(W.po_none), color = c.textMuted) }; return }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orders, key = { it.id }) { o ->
                Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.orderNumber + " · " + Format.name(o.partyName), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Format.dateTime(o.timestamp) + " · " + o.itemCount, color = c.textMuted, fontSize = 12.sp)
                        }
                        Text(orderStatusWord(o.status), color = if (o.status == OrderStatus.RECEIVED) c.brandGreenText else c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (o.status == OrderStatus.DRAFT || o.status == OrderStatus.SENT) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                val full = withContext(Dispatchers.IO) { dao.orderWithLines(o.id) } ?: return@launch
                                onSend(suppliers.firstOrNull { it.id == o.partyId }?.phone, orderText(words, shopName, full, o.partyName))
                                withContext(Dispatchers.IO) { dao.setOrderStatus(o.id, OrderStatus.SENT) }
                            }
                        }) { Text(word(W.po_send), color = c.brandGreenText) }
                        if (o.status == OrderStatus.DRAFT && offered) TextButton(onClick = { gated {
                            scope.launch { val full = withContext(Dispatchers.IO) { dao.orderWithLines(o.id) } ?: return@launch; editing = OrderDraft(full, withContext(Dispatchers.IO) { dao.productsOnce() }) }
                        } }) { Text(word(W.edit), color = c.ink) }
                        if (offered) TextButton(onClick = { gated { scope.launch { receiving = withContext(Dispatchers.IO) { dao.orderWithLines(o.id) } } } }) { Text(word(W.po_receive), color = c.brandGreenText, fontWeight = FontWeight.Bold) }
                        if (offered) TextButton(onClick = { gated { scope.launch { withContext(Dispatchers.IO) { dao.setOrderStatus(o.id, OrderStatus.CLOSED) } } } }) { Text(word(W.po_close), color = c.textMuted) }
                    }
                }
            }
        }
    }
}

@Composable
fun orderStatusWord(s: String): String = word(
    when (s) {
        OrderStatus.SENT -> W.po_status_sent
        OrderStatus.RECEIVED -> W.po_status_received
        OrderStatus.CLOSED -> W.po_status_closed
        else -> W.po_status_draft
    }
)

@Composable
private fun OrderEditor(draft: OrderDraft, suppliers: List<PartyWithBalance>, onCancel: () -> Unit, onSave: () -> Unit) {
    val c = LocalRoshanColors.current
    var query by remember { mutableStateOf("") }
    var partyQuery by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.pageBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(if (draft.existing == null) W.po_new else W.edit), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val chosen = suppliers.firstOrNull { it.id == draft.partyId }
        if (chosen != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(word(W.po_supplier) + ": " + Format.name(chosen.name), Modifier.weight(1f), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { draft.partyId = null }) { Text(word(W.edit), color = c.brandGreenText) }
        } else {
            OutlinedTextField(partyQuery, { partyQuery = it }, Modifier.fillMaxWidth(), label = { Text(word(W.po_supplier)) }, singleLine = true)
            Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).background(c.surface, RoundedCornerShape(12.dp))) {
                suppliers.filter { NameSearch.matches(it.name, it.phone, partyQuery.trim().lowercase()) }.take(20).forEach { p ->
                    Text(Format.name(p.name), Modifier.fillMaxWidth().clickable { draft.partyId = p.id }.padding(12.dp), color = c.ink, fontSize = 15.sp)
                    HorizontalDivider(color = c.pageBg)
                }
            }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text(word(W.pos_search_hint)) }, singleLine = true)
        if (query.isNotBlank()) Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp))) {
            CounterDraft.search(draft.products, query, 8).forEach { p ->
                Text(p.name, Modifier.fillMaxWidth().clickable { draft.add(p); query = "" }.padding(12.dp), color = c.ink, fontSize = 15.sp)
                HorizontalDivider(color = c.pageBg)
            }
        }
        if (draft.lines.isEmpty()) Text(word(W.po_lines_empty), color = c.textMuted)
        draft.lines.forEach { l ->
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (l.productId == null) OutlinedTextField(l.name, { l.name = it }, Modifier.weight(1f), placeholder = { Text(word(W.item_name_hint)) }, singleLine = true)
                    else Text(l.name, Modifier.weight(1f), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { draft.lines.remove(l) }) { Text("✕", color = c.textMuted) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcField(l.qty, { l.qty = it }, Modifier.weight(1f), kind = CalcKind.QUANTITY, label = word(W.pos_col_qty) + (com.innovation313.roshankhata.data.UnitWords.label(l.unit).let { u -> if (u.isEmpty()) "" else " ($u)" }))
                    CalcField(l.rate, { l.rate = it }, Modifier.weight(1f), label = word(W.pos_col_rate))
                }
            }
        }
        OutlinedButton(onClick = { draft.lines += OrderDraft.Line(null, "", "1", null, "") }) { Text(word(W.item_name_hint), color = c.ink) }
        OutlinedTextField(draft.note, { draft.note = it }, Modifier.fillMaxWidth(), label = { Text(word(W.note_optional)) }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text(word(W.cancel), color = c.ink) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSave, enabled = draft.valid, colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen)) { Text(word(W.save), color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ReceiveEditor(
    o: PurchaseOrderWithLines, pickDay: ((Long, (Long) -> Unit) -> Unit)?, onCancel: () -> Unit,
    onSave: (items: List<BillItem>, billNumber: String?, total: Double) -> Unit
) {
    val c = LocalRoshanColors.current
    class Got(val item: PurchaseOrderItem) {
        var qty by mutableStateOf(Calc.trim(item.quantity))
        var rate by mutableStateOf(item.rate?.let { Calc.trim(it) }.orEmpty())
        var batch by mutableStateOf("")
        /** The batch's expiry as it arrived; none for goods that do not expire. */
        var expiry by mutableStateOf<Long?>(null)
    }
    val got = remember { o.items.map { Got(it) } }
    var billNumber by remember { mutableStateOf("") }
    val total = got.sumOf { (Calc.evalAmount(it.qty) ?: 0.0) * (Calc.evalAmount(it.rate) ?: 0.0) }
    Column(Modifier.fillMaxSize().background(c.pageBg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(W.po_receive) + " · " + o.order.orderNumber, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(billNumber, { billNumber = it }, Modifier.fillMaxWidth(), label = { Text(word(W.bill_number_hint)) }, singleLine = true)
        got.forEach { g ->
            Column(Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(g.item.itemName, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalcField(g.qty, { g.qty = it }, Modifier.weight(1f), kind = CalcKind.QUANTITY, label = word(W.pos_col_qty))
                    CalcField(g.rate, { g.rate = it }, Modifier.weight(1f), label = word(W.pos_col_rate))
                    OutlinedTextField(g.batch, { g.batch = it }, Modifier.weight(1f), label = { Text(word(W.pdf_reg_batch, "")) }, singleLine = true)
                }
                if (pickDay != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The expiry opens on today (the phone's bill line rule: the urgent lot is the one two months out,
                    // not a year away); a chosen day can be taken back.
                    OutlinedButton(onClick = { pickDay(g.expiry ?: System.currentTimeMillis()) { g.expiry = it } }) {
                        Text(g.expiry?.let { word(W.bill_scan_expiry, Format.dateOnly(it)) } ?: word(W.expiry_date), color = c.ink)
                    }
                    if (g.expiry != null) TextButton(onClick = { g.expiry = null }) { Text(word(W.filter_clear), color = c.inkSoft) }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(word(W.pos_grand_total), Modifier.weight(1f), color = c.inkSoft)
            Text(Format.money(total), color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text(word(W.cancel), color = c.ink) }
            Spacer(Modifier.weight(1f))
            Button(onClick = {
                val items = got.mapNotNull { g ->
                    val q = Calc.evalAmount(g.qty) ?: return@mapNotNull null
                    if (q <= 0.0) null else BillItem(billId = 0, productName = g.item.itemName, quantity = q, unit = g.item.unit, rate = Calc.evalAmount(g.rate), batchNumber = g.batch.trim().ifBlank { null }, expiryDate = g.expiry, productId = g.item.productId)
                }
                onSave(items, billNumber.trim().ifBlank { null }, total)
            }, enabled = got.any { (Calc.evalAmount(it.qty) ?: 0.0) > 0.0 }, colors = ButtonDefaults.buttonColors(containerColor = c.brandGreen)) { Text(word(W.save), color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}
