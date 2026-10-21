package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Counter
import com.innovation313.roshankhata.data.CounterDraft
import com.innovation313.roshankhata.data.Currency
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.SaleMath
import com.innovation313.roshankhata.data.SaleStatus
import com.innovation313.roshankhata.data.SaleSummary
import com.innovation313.roshankhata.data.SaleWithLines
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The counter (POS Phase 1, 21 Oct 2026): one page to ring a sale up on the
 * computer. Scan or type an item on the start side, the ticket's lines under
 * it; the customer, the figures, the payments and the one accent button on
 * the end side, as a till is laid out. The ticket is a [CounterDraft] — the
 * same state the phone's counter draws — and ringing it up is
 * [Counter.post], the one transaction that writes it.
 *
 * Keys, while this page is open (the window hands them here first): F2 the
 * item box, F4 the last line's quantity, F6 the discount, F8 the customer,
 * F9 pays the rest in cash, F12 completes, Ctrl+H holds. Esc clears the box.
 */
@Composable
fun CounterSalePane(
    dao: KhataDao,
    parties: List<PartyWithBalance>,
    words: Words,
    rtl: Boolean,
    shopName: String?,
    currency: String,
    reload: Int,
    onChanged: () -> Unit,
    onReceipt: (List<ThermalReceipt.Line>) -> Unit,
    keys: KeyActions
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val draft = remember { CounterDraft() }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var onHand by remember { mutableStateOf<Map<Long, Double?>>(emptyMap()) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(reload, tick) {
        withContext(Dispatchers.IO) {
            products = dao.productsOnce()
            onHand = runCatching { Counter.onHand(dao) }.getOrDefault(emptyMap())
        }
    }
    val held by remember(tick) { dao.observeHeldSales() }.collectAsState(emptyList())
    val recent by remember(tick) { dao.observeSales() }.collectAsState(emptyList())

    var query by remember { mutableStateOf("") }
    var told by remember { mutableStateOf<String?>(null) }
    var choosingParty by remember { mutableStateOf(false) }
    var voiding by remember { mutableStateOf<SaleSummary?>(null) }
    var showHeld by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val discountFocus = remember { FocusRequester() }
    val qtyFocus = remember { FocusRequester() }

    val matches = remember(query, products) { if (query.isBlank()) emptyList() else CounterDraft.search(products, query) }

    fun ring(p: Product) {
        val line = draft.add(p, onHand[p.id])
        query = ""
        told = line.shortBy?.let { words.text(W.pos_stock_low, Calc.trim(onHand[p.id] ?: 0.0)) }
    }

    /** Enter in the item box: the one exact barcode, the one match, or a new free line. */
    fun enterQuery() {
        val q = query.trim()
        if (q.isEmpty()) return
        val exact = products.firstOrNull { it.barcode != null && it.barcode == q }
        when {
            exact != null -> ring(exact)
            matches.size == 1 -> ring(matches[0])
            matches.isEmpty() && q.any { it.isDigit() } && q.all { it.isDigit() || it == '-' } -> told = words.text(W.pos_barcode_unknown)
            matches.isEmpty() -> { draft.addFree(q); query = ""; told = words.text(W.pos_product_new, q) }
            else -> ring(matches[0])
        }
    }

    fun problemWords(p: SaleMath.Problem): String = words.text(
        when (p) {
            SaleMath.Problem.NO_LINES -> W.pos_err_no_lines
            SaleMath.Problem.BAD_QUANTITY -> W.pos_err_quantity
            SaleMath.Problem.BAD_RATE -> W.pos_err_rate
            SaleMath.Problem.BAD_DISCOUNT -> W.pos_err_discount
            SaleMath.Problem.BAD_PAYMENT -> W.pos_err_payment
            SaleMath.Problem.BAD_METHOD -> W.pos_err_payment
            SaleMath.Problem.OVERPAID -> W.pos_err_overpaid
            SaleMath.Problem.WALK_IN_UNPAID -> W.pos_err_walk_in_unpaid
            SaleMath.Problem.EMPTY_NAME -> W.pos_err_empty_name
        }
    )

    fun complete(print: Boolean) {
        val problem = draft.problem
        if (problem != null) { told = problemWords(problem); return }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val id = Counter.post(dao, draft)
                    val sale = dao.saleWithLines(id)!!
                    sale to sale.sale.partyId?.let { pid -> dao.partiesWithBalanceOnce().firstOrNull { it.id == pid } }
                }
            }
            result.onSuccess { (sale, party) ->
                told = words.text(W.pos_posted, sale.sale.saleNumber)
                if (print) onReceipt(SaleReceipt.lines(words, shopName ?: words.text(W.app_name), sale, currency, party))
                draft.clear()
                tick++
                onChanged()
            }.onFailure { told = it.message ?: words.text(W.backup_failed) }
        }
    }

    fun hold() {
        if (draft.lines.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { Counter.hold(dao, draft) } }
            draft.clear()
            tick++
        }
    }

    // The page's keys, handed over by the window while it is open.
    keys.page = { e: KeyEvent ->
        if (e.type != KeyEventType.KeyDown) false else when {
            e.key == Key.F2 -> { searchFocus.requestFocus(); true }
            e.key == Key.F4 -> { if (draft.lines.isNotEmpty()) qtyFocus.requestFocus(); true }
            e.key == Key.F6 -> { discountFocus.requestFocus(); true }
            e.key == Key.F8 -> { choosingParty = true; true }
            e.key == Key.F9 -> { draft.payRestInCash(); true }
            e.key == Key.F12 -> { complete(false); true }
            e.isCtrlPressed && e.key == Key.H -> { hold(); true }
            else -> false
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // ---- The start side: the item box and the ticket ----
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(word(W.pos_title), word(W.pos_keys_help), actions = {
                if (held.isNotEmpty()) {
                    FluentButton(words.text(W.pos_held_tickets, held.size), { showHeld = true }, kind = ButtonKind.STANDARD)
                }
                FluentButton(word(W.pos_hold), { hold() }, kind = ButtonKind.STANDARD, enabled = draft.lines.isNotEmpty())
                FluentButton(word(W.pos_clear), { draft.clear(); told = null }, kind = ButtonKind.SUBTLE, enabled = !draft.isEmpty)
            })
            Column(Modifier.fillMaxWidth()) {
                FluentField(
                    query, { query = it }, Modifier.fillMaxWidth().focusRequester(searchFocus)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) false
                            else if (e.key == Key.Enter) { enterQuery(); true }
                            else if (e.key == Key.Escape) { query = ""; true } else false
                        },
                    placeholder = word(W.pos_search_hint), leadingIcon = "ic_search",
                    trailing = { KeyCap("F2") }
                )
                if (matches.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(max = 260.dp).card().verticalScroll(rememberScrollState())) {
                        matches.forEachIndexed { i, p ->
                            val stock = onHand[p.id]
                            Row(
                                Modifier.fillMaxWidth().press { ring(p) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(p.company, p.barcode?.let { Format.ltr(it) }, stock?.let { "${Calc.trim(it)} ${p.defaultUnit.orEmpty()}".trim() }).joinToString(" · "),
                                        color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                (if (draft.hasParty) p.creditPrice ?: p.salePrice else p.salePrice)?.let {
                                    Text(Format.money(it), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                }
                            }
                            if (i < matches.lastIndex) Rule()
                        }
                    }
                }
            }
            ListCard(Modifier.fillMaxWidth().weight(1f)) {
                TableHead(listOf(word(W.pos_col_item) to 5, word(W.pos_col_qty) to 2, word(W.pos_col_rate) to 2, word(W.pos_discount) to 2, word(W.pos_col_total) to 2, "" to 1), endAligned = setOf(2, 4))
                if (draft.lines.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(word(W.pos_lines_empty), color = c.textMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                        itemsIndexed(draft.lines, key = { _, l -> System.identityHashCode(l) }) { i, line ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(5f)) {
                                    if (line.productId == null) {
                                        FluentField(line.name, { line.name = it }, Modifier.fillMaxWidth(), placeholder = word(W.item_name_hint))
                                    } else {
                                        Text(line.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val under = listOfNotNull(line.unit, line.shortBy?.let { words.text(W.pos_stock_low, Calc.trim(line.onHand ?: 0.0)) })
                                        if (under.isNotEmpty()) Text(under.joinToString(" · "), color = if (line.shortBy != null) c.owedToMe else c.textMuted, fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                                FluentField(line.qty, { line.qty = it }, Modifier.weight(2f).then(if (i == draft.lines.lastIndex) Modifier.focusRequester(qtyFocus) else Modifier))
                                FluentField(line.rate, { line.rate = it }, Modifier.weight(2f), placeholder = "0")
                                FluentField(line.discount, { line.discount = it }, Modifier.weight(2f), placeholder = "0")
                                Text(Format.money(line.total), Modifier.weight(2f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                    IconAction("ic_close", word(W.delete), { draft.remove(line) })
                                }
                            }
                            Rule()
                        }
                    }
                }
            }
            RecentSales(recent.take(8), words, onVoid = { voiding = it })
        }

        // ---- The end side: who, how much, how paid, the button ----
        Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FluentCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardTitle(word(W.customer), end = { KeyCap("F8") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluentPill(word(W.pos_walk_in), !draft.hasParty) { draft.setParty(null, null) }
                    FluentPill(draft.partyName?.let { Format.name(it) } ?: word(W.pos_choose_customer), draft.hasParty) { choosingParty = true }
                }
                draft.partyName?.let { name ->
                    parties.firstOrNull { it.id == draft.partyId }?.let { p ->
                        Text(Format.customerBalance(p.balance), color = c.inkSoft, fontSize = 12.sp)
                    }
                    if (draft.remaining > 0.005) Text(words.text(W.pos_customer_pays_later, Format.name(name)), color = c.inkSoft, fontSize = 12.sp)
                }
            } }
            FluentCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FigureRow(word(W.pos_subtotal), Format.money(draft.totals.subtotal))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluentField(draft.discount, { draft.discount = it }, Modifier.weight(1f).focusRequester(discountFocus), label = word(W.pos_discount), prefix = currency, placeholder = "0", trailing = { KeyCap("F6") })
                    FluentField(draft.taxPercent, { draft.taxPercent = it }, Modifier.weight(1f), label = word(W.pos_tax), placeholder = "0")
                }
                if (draft.totals.tax > 0.0) FigureRow(word(W.pos_tax), Format.money(draft.totals.tax))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(word(W.pos_round_off), Modifier.weight(1f), color = c.inkSoft, fontSize = 12.sp)
                    FluentPill(if (draft.totals.roundOff != 0.0) Format.money(draft.totals.roundOff) else "0", draft.roundToWhole) { draft.roundToWhole = !draft.roundToWhole }
                }
                Rule()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(word(W.pos_grand_total), Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(Format.money(draft.totals.grandTotal), color = c.ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            } }
            FluentCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CardTitle(word(W.pos_paid), end = { KeyCap("F9") })
                draft.payments.forEach { p ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PaymentMethod.ALL.forEach { m ->
                                FluentPill(methodWord(m), p.method == m) { p.method = m }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FluentField(p.amount, { p.amount = it }, Modifier.weight(1f), prefix = currency, placeholder = "0")
                            if (p.method != PaymentMethod.CASH) FluentField(p.reference, { p.reference = it }, Modifier.weight(1f), placeholder = word(W.pos_reference_hint))
                            IconAction("ic_close", word(W.delete), { draft.removePayment(p) })
                        }
                    }
                    Rule()
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluentButton(word(W.pos_pay_full_cash), { draft.payRestInCash() }, kind = ButtonKind.STANDARD, enabled = draft.remaining > 0.005)
                    FluentButton(word(W.pos_add_payment), { draft.addPayment(PaymentMethod.CASH) }, kind = ButtonKind.SUBTLE, icon = "ic_add_plain")
                }
                val rest = draft.remaining
                when {
                    rest > 0.005 && draft.hasParty -> FigureRow(word(W.pos_to_account), Format.money(rest), c.owedToMe)
                    rest > 0.005 -> FigureRow(word(W.pos_to_account), Format.money(rest), c.textMuted)
                    rest < -0.005 -> FigureRow(word(W.pos_change), Format.money(-rest), c.iOwe)
                }
            } }
            val problem = draft.problem
            told?.let { Text(it, color = if (problem == null) c.inkSoft else c.owedToMe, fontSize = 13.sp) }
            if (problem != null && draft.lines.isNotEmpty()) Text(problemWords(problem), color = c.textMuted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluentButton(word(W.pos_post), { complete(false) }, Modifier.weight(1f), kind = ButtonKind.ACCENT, icon = "ic_check_plain", enabled = problem == null, tall = true)
                FluentButton(word(W.pos_post_and_print), { complete(true) }, kind = ButtonKind.STANDARD, icon = "ic_print", enabled = problem == null, tall = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { KeyCap("F12") }
        }
    }

    if (choosingParty) {
        DialogWindow(onCloseRequest = { choosingParty = false }, onPreviewKeyEvent = escCloses { choosingParty = false }, title = word(W.pos_choose_customer), state = rememberDialogState(width = 520.dp, height = 480.dp)) {
            DialogFrame(word(W.pos_choose_customer), onClose = { choosingParty = false }, footer = {
                FluentButton(word(W.pos_walk_in), { draft.setParty(null, null); choosingParty = false }, kind = ButtonKind.STANDARD)
            }) {
                PartyPicker(parties.filter { it.isCustomer }, draft.partyId, onPick = { id ->
                    draft.setParty(id, parties.firstOrNull { it.id == id }?.name)
                    if (id != null) choosingParty = false
                }, label = word(W.customer), hint = word(W.search_parties))
            }
        }
    }
    if (showHeld) {
        DialogWindow(onCloseRequest = { showHeld = false }, onPreviewKeyEvent = escCloses { showHeld = false }, title = words.text(W.pos_held_tickets, held.size), state = rememberDialogState(width = 560.dp, height = 480.dp)) {
            DialogFrame(words.text(W.pos_held_tickets, held.size), onClose = { showHeld = false }, footer = {
                FluentButton(word(W.cancel), { showHeld = false }, kind = ButtonKind.STANDARD)
            }) {
                held.forEachIndexed { i, h ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(h.customerName ?: word(W.pos_walk_in), 32, i)
                        Column(Modifier.weight(1f)) {
                            Text(h.customerName?.let { Format.name(it) } ?: word(W.pos_walk_in), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Format.dateTime(h.timestamp) + " · " + Format.money(h.grandTotal), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        FluentButton(word(W.pos_resume), {
                            scope.launch {
                                val full = withContext(Dispatchers.IO) { dao.saleWithLines(h.id) } ?: return@launch
                                draft.load(full) { onHand[it] }
                                showHeld = false
                            }
                        }, kind = ButtonKind.ACCENT)
                        IconAction("ic_close", word(W.pos_drop_held), { scope.launch { withContext(Dispatchers.IO) { dao.dropHeldSale(h.id) }; tick++ } })
                    }
                    if (i < held.lastIndex) Rule()
                }
            }
        }
    }
    voiding?.let { s ->
        var reason by remember(s.id) { mutableStateOf("") }
        DialogWindow(onCloseRequest = { voiding = null }, onPreviewKeyEvent = escCloses { voiding = null }, title = word(W.pos_void), state = rememberDialogState(width = 480.dp, height = 300.dp)) {
            DialogFrame(word(W.pos_void), subtitle = words.text(W.pos_void_confirm, s.saleNumber), onClose = { voiding = null }, footer = {
                FluentButton(word(W.cancel), { voiding = null }, kind = ButtonKind.STANDARD)
                FluentButton(word(W.pos_void), {
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { dao.voidSale(s.id, reason) } }
                        voiding = null; tick++; onChanged()
                        told = words.text(W.pos_voided) + " " + s.saleNumber
                    }
                }, kind = ButtonKind.DANGER, enabled = reason.isNotBlank())
            }) {
                FluentField(reason, { reason = it }, Modifier.fillMaxWidth(), label = word(W.pos_void_reason))
            }
        }
    }
}

/** A label on the start side, a figure on the end side. */
@Composable
private fun FigureRow(label: String, figure: String, color: androidx.compose.ui.graphics.Color? = null) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = c.inkSoft, fontSize = 13.sp)
        Text(figure, color = color ?: c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun methodWord(m: String): String = word(
    when (m) {
        PaymentMethod.CASH -> W.payment_method_cash
        PaymentMethod.BANK -> W.payment_method_bank
        PaymentMethod.CHEQUE -> W.payment_method_cheque
        else -> W.payment_method_online
    }
)

/** Today's last few tickets under the counter, with the void. */
@Composable
private fun RecentSales(rows: List<SaleSummary>, words: Words, onVoid: (SaleSummary) -> Unit) {
    val c = LocalRoshanColors.current
    if (rows.isEmpty()) return
    ListCard(Modifier.fillMaxWidth()) {
        ListHead(word(W.pos_recent))
        rows.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Format.ltr(s.saleNumber), Modifier.width(96.dp), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Text(s.customerName?.let { Format.name(it) } ?: word(W.pos_walk_in), Modifier.weight(1f), color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.dateTime(s.timestamp), color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false)
                Text(Format.money(s.grandTotal), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                when (s.status) {
                    SaleStatus.VOID -> Tag(word(W.pos_status_void), c.owedToMe, Look.getTint)
                    else -> Tag(word(W.pos_status_done), Look.accent, Look.accentTint)
                }
                if (s.status == SaleStatus.DONE) FluentButton(word(W.pos_void), { onVoid(s) }, kind = ButtonKind.SUBTLE) else Spacer(Modifier.width(8.dp))
            }
            if (i < rows.lastIndex) Rule()
        }
    }
}

/** The thermal receipt for a ticket — the lines ThermalReceipt prints or saves as a picture. */
object SaleReceipt {
    fun lines(words: Words, shop: String, s: SaleWithLines, currency: String, party: PartyWithBalance?): List<ThermalReceipt.Line> {
        val sale = s.sale
        val out = ArrayList<ThermalReceipt.Line>()
        out += ThermalReceipt.Line(shop, 26f, bold = true, centre = true)
        out += ThermalReceipt.Line(Format.dateTime(sale.timestamp) + "  " + sale.saleNumber, 18f, centre = true)
        out += ThermalReceipt.Line("")
        out += ThermalReceipt.Line(sale.customerName?.let { Format.name(it) } ?: words.text(W.pos_walk_in), 22f, bold = true)
        for (i in s.items) {
            out += ThermalReceipt.Line(i.itemName, 20f)
            out += ThermalReceipt.Line(
                "  " + Calc.trim(i.quantity) + (i.unit?.let { " $it" } ?: "") + " × " + Format.money(i.rate) +
                    (if (i.discount > 0.0) "  −" + Format.money(i.discount) else "") + "  =  " + Format.money(i.lineTotal), 18f
            )
        }
        out += ThermalReceipt.Line("")
        if (sale.discountAmount > 0.0 || sale.taxAmount > 0.0 || sale.roundOff != 0.0) {
            out += ThermalReceipt.Line(words.text(W.pos_subtotal) + ": " + Format.money(sale.subtotal), 18f)
            if (sale.discountAmount > 0.0) out += ThermalReceipt.Line(words.text(W.pos_discount) + ": −" + Format.money(sale.discountAmount), 18f)
            if (sale.taxAmount > 0.0) out += ThermalReceipt.Line(words.text(W.pos_tax) + ": " + Format.money(sale.taxAmount), 18f)
            if (sale.roundOff != 0.0) out += ThermalReceipt.Line(words.text(W.pos_round_off) + ": " + Format.money(sale.roundOff), 18f)
        }
        out += ThermalReceipt.Line(words.text(W.pos_grand_total) + "  " + Format.money(sale.grandTotal), 30f, bold = true, centre = true)
        for (p in s.payments) {
            val m = words.text(
                when (p.method) {
                    PaymentMethod.CASH -> W.payment_method_cash
                    PaymentMethod.BANK -> W.payment_method_bank
                    PaymentMethod.CHEQUE -> W.payment_method_cheque
                    else -> W.payment_method_online
                }
            )
            out += ThermalReceipt.Line(m + ": " + Format.money(p.amount) + (p.reference?.let { "  $it" } ?: ""), 18f)
        }
        if (sale.creditAmount > 0.005) out += ThermalReceipt.Line(words.text(W.pos_to_account) + ": " + Format.money(sale.creditAmount), 20f, bold = true)
        party?.let { out += ThermalReceipt.Line(words.text(W.net_balance) + ": " + Format.money(it.balance), 18f) }
        out += ThermalReceipt.Line("")
        out += ThermalReceipt.Line(words.text(W.app_name), 16f, centre = true)
        return out
    }
}
