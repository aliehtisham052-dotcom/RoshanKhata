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
    onReceipt: (ThermalReceipt.Job) -> Unit,
    keys: KeyActions,
    /** A sale's A4 invoice was made (or found): open it on the Invoices page. */
    onInvoice: (Long) -> Unit = {},
    /** A cash sale rung up without a receipt: the drawer still opens (when it is set to). */
    onDrawer: () -> Unit = {},
    /** This computer's receipt printer (80 mm, logo, drawer), changed in the counter's settings. */
    printer: ThermalReceipt.Settings = ThermalReceipt.Settings(),
    hasLogo: Boolean = false,
    onPrinter: (ThermalReceipt.Settings) -> Unit = {},
    /** The shop's payment QR picture (Business Profile), for the customer's screen. */
    paymentQr: java.io.File? = null
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val draft = remember { CounterDraft() }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var onHand by remember { mutableStateOf<Map<Long, Double?>>(emptyMap()) }
    var tick by remember { mutableStateOf(0) }
    var settingUp by remember { mutableStateOf(false) }
    // The weighing scale's labels and the item grid (21 Oct 2026).
    var scaleRule by remember { mutableStateOf(com.innovation313.roshankhata.data.ScaleCode.Rule()) }
    var grid by remember { mutableStateOf(false) }
    var showingCustomer by remember { mutableStateOf(false) }
    LaunchedEffect(reload, tick, settingUp) {
        withContext(Dispatchers.IO) {
            products = dao.productsOnce()
            onHand = runCatching { Counter.onHand(dao) }.getOrDefault(emptyMap())
            // The book's setting (schema 36): prices with their tax inside or not.
            draft.pricesIncludeTax = com.innovation313.roshankhata.data.BookSettings.isOn(
                dao.settingValue(com.innovation313.roshankhata.data.BookSettings.PRICES_INCLUDE_TAX)
            )
            draft.loyalty = com.innovation313.roshankhata.data.Loyalty.rule(dao)
            scaleRule = com.innovation313.roshankhata.data.ScaleCode.rule(dao)
        }
    }
    // The chosen customer's loyalty points, read when they are chosen.
    LaunchedEffect(draft.partyId, draft.loyalty, tick) {
        val id = draft.partyId
        draft.pointsBalance = if (id != null && draft.loyalty != null) withContext(Dispatchers.IO) { dao.loyaltyBalance(id) } else 0.0
    }
    if (showingCustomer) CustomerDisplay(draft, shopName ?: words.text(W.app_name), paymentQr) { showingCustomer = false }
    if (settingUp) CounterSettingsDialog(dao, printer, hasLogo, onPrinter) { settingUp = false }
    // Shifts (21 Oct 2026): the person at the counter opens theirs with the float and closes it counted.
    var shifting by remember { mutableStateOf(false) }
    val openShifts by remember { dao.observeOpenShifts() }.collectAsState(emptyList())
    val myShift = openShifts.firstOrNull { it.user == com.innovation313.roshankhata.data.Access.userName }
    if (shifting) {
        DialogWindow(onCloseRequest = { shifting = false }, onPreviewKeyEvent = escCloses { shifting = false }, title = word(W.shift_title), state = rememberDialogState(width = fitW(560.dp), height = fitH(720.dp))) {
            DialogFrame(word(W.shift_title), onClose = { shifting = false }, scroll = false, footer = {
                FluentButton(word(W.close), { shifting = false }, kind = ButtonKind.STANDARD)
            }) {
                com.innovation313.roshankhata.screens.ShiftScreen(dao, Modifier.fillMaxSize(), onChanged = { tick++ })
            }
        }
    }
    val held by remember(tick) { dao.observeHeldSales() }.collectAsState(emptyList())
    val recent by remember(tick) { dao.observeSales() }.collectAsState(emptyList())

    var query by remember { mutableStateOf("") }
    var told by remember { mutableStateOf<String?>(null) }
    var choosingParty by remember { mutableStateOf(false) }
    var voiding by remember { mutableStateOf<SaleSummary?>(null) }
    var returning by remember { mutableStateOf<com.innovation313.roshankhata.screens.ReturnDraft?>(null) }
    var voidingReturn by remember { mutableStateOf<com.innovation313.roshankhata.data.ReturnSummary?>(null) }
    val returns by remember(tick) { dao.observeReturns() }.collectAsState(emptyList())
    var showHeld by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    if (reporting) ZReportDialog(dao, onReceipt = { onReceipt(ThermalReceipt.Job(it)) }) { reporting = false }
    // Who is at the counter (POS Phase 5), and the manager's PIN for what a cashier may not do alone.
    var signingIn by remember { mutableStateOf(false) }
    if (signingIn) SignInDialog(dao) { signingIn = false }
    var override by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    override?.let { (what, then) -> OverrideDialog(dao, what, onClose = { override = null }) { by -> then(by) } }
    fun allowed(p: com.innovation313.roshankhata.data.Access.Permission, what: String, then: (String?) -> Unit) {
        if (com.innovation313.roshankhata.data.Access.can(p)) then(null) else override = what to { by -> then(by) }
    }
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
        // A weighing scale's label: the item and its weight (or price) in one code, a line of its own.
        val weighed = if (exact == null) com.innovation313.roshankhata.data.ScaleCode.line(q, scaleRule, products) { draft.unitRate(it) } else null
        if (weighed != null) {
            val line = draft.addWeighed(weighed, onHand[weighed.product.id])
            query = ""
            told = line.shortBy?.let { words.text(W.pos_stock_low, Calc.trim(onHand[weighed.product.id] ?: 0.0)) }
            return
        }
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
            SaleMath.Problem.RATE_MISSING -> W.pos_err_rate_missing
            SaleMath.Problem.UNREADABLE_DISCOUNT -> W.pos_err_discount_unread
            SaleMath.Problem.BAD_TAX -> W.pos_err_tax
            SaleMath.Problem.UNREADABLE_PAYMENT -> W.pos_err_payment_unread
        }
    )

    /**
     * Ring the ticket up. Each check that a cashier may not pass alone waits
     * for its own manager's PIN (21 Oct 2026: one PIN given for a discount
     * also let the ticket past the customer's credit limit unseen): a price
     * changed on the ticket or an item typed in by hand, a discount past the
     * cap (measured on the goods before any discount), the credit limit.
     * While a ticket is being written the buttons and F12 wait, so a double
     * click or a held-down key never posts it twice.
     */
    fun complete(print: Boolean, priceBy: String? = null, discountBy: String? = null, creditBy: String? = null) {
        if (draft.posting) return
        val problem = draft.problem
        if (problem != null) { told = problemWords(problem); return }
        val access = com.innovation313.roshankhata.data.Access
        val repriced = draft.repriced
        if (priceBy == null && repriced.isNotEmpty() && !access.can(com.innovation313.roshankhata.data.Access.Permission.PRICE_EDIT)) {
            told = words.text(W.pos_price_needs_pin)
            override = words.text(W.pos_price_needs_pin) to { by -> complete(print, by, discountBy, creditBy) }
            return
        }
        // A cashier's discount past the cap waits for a manager's PIN (POS Phase 5).
        val discounts = draft.allDiscounts
        if (discountBy == null && access.discountNeedsOverride(discounts, draft.grossTotal)) {
            told = words.text(W.access_discount_cap, Format.money(draft.grossTotal * com.innovation313.roshankhata.data.Access.CASHIER_DISCOUNT_CAP))
            override = words.text(W.pos_discount) to { by -> complete(print, priceBy, by, creditBy) }
            return
        }
        // Past the customer's credit limit (POS Phase 9): a manager's PIN, and the log says so.
        val party = parties.firstOrNull { it.id == draft.partyId }
        val over = draft.creditOver(party?.balance, party?.creditLimit)
        if (over != null && creditBy == null && !access.can(com.innovation313.roshankhata.data.Access.Permission.CREDIT_OVER)) {
            told = words.text(W.pos_credit_limit, Format.money(over.first), Format.money(over.second))
            override = words.text(W.pos_credit_limit, Format.money(over.first), Format.money(over.second)) to { by -> complete(print, priceBy, discountBy, by) }
            return
        }
        if (over != null && creditBy == null) told = words.text(W.pos_credit_limit, Format.money(over.first), Format.money(over.second))
        val change = draft.change
        val priceDetail = if (repriced.isNotEmpty()) draft.repricedDetail() else null
        draft.posting = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (discountBy != null) dao.log(com.innovation313.roshankhata.data.AuditAction.DISCOUNT_OVERRIDE, discountBy, detail = Format.money(discounts))
                    // Every price that is not the product's own is written down, with who allowed it.
                    if (priceDetail != null) dao.log(com.innovation313.roshankhata.data.AuditAction.PRICE_CHANGED, priceBy ?: access.userName, detail = priceDetail)
                }
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val id = Counter.post(dao, draft)
                        val sale = dao.saleWithLines(id)!!
                        Triple(sale, sale.sale.partyId?.let { pid -> dao.partiesWithBalanceOnce().firstOrNull { it.id == pid } },
                            com.innovation313.roshankhata.data.Loyalty.onReceipt(dao, sale.sale))
                    }
                }
                result.onSuccess { (sale, party, points) ->
                    // The change to hand back, said with the sale (21 Oct 2026).
                    told = words.text(W.pos_posted, sale.sale.saleNumber) +
                        (if (change > 0.005) "  ·  " + words.text(W.pos_change) + " " + Format.money(change) else "")
                    // Cash in the drawer: the drawer opens with the receipt, or alone when none is printed.
                    val cash = sale.payments.any { it.method == PaymentMethod.CASH && it.amount > 0.0 }
                    if (print) onReceipt(ThermalReceipt.Job(SaleReceipt.lines(words, shopName ?: words.text(W.app_name), sale, currency, party, points), cash))
                    else if (cash) onDrawer()
                    draft.clear()
                    tick++
                    onChanged()
                }.onFailure { e ->
                    told = if (e is Counter.ExpiredStock) words.text(W.pos_expired_stock, e.item, Calc.trim(e.fresh))
                        else failText(words, e)
                }
            } finally {
                draft.posting = false
            }
        }
    }

    /** Park the ticket; it is cleared only once it is safely held (21 Oct 2026: a failed hold lost it). */
    fun hold() {
        if (draft.lines.isEmpty() || draft.posting) return
        draft.posting = true
        scope.launch {
            try {
                val held = withContext(Dispatchers.IO) { runCatching { Counter.hold(dao, draft) } }
                held.onSuccess { draft.clear(); tick++ }
                    .onFailure { e -> told = failText(words, e) }
            } finally {
                draft.posting = false
            }
        }
    }

    // The item box takes the keys as soon as the counter opens, and again after each ticket
    // (21 Oct 2026): a barcode scanner types into whatever has focus, and on opening nothing had it.
    LaunchedEffect(tick) {
        kotlinx.coroutines.delay(150)
        runCatching { searchFocus.requestFocus() }
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

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    val narrow = maxWidth < 1000.dp
    Row(Modifier.fillMaxSize().padding(horizontal = if (narrow) 16.dp else 24.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp)) {
        // ---- The start side: the item box and the ticket ----
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(word(W.pos_title), word(W.pos_keys_help), actions = {
                FluentButton(word(W.pos_hold), { hold() }, kind = ButtonKind.STANDARD, enabled = draft.lines.isNotEmpty())
                FluentButton(word(W.pos_clear), { draft.clear(); told = null }, kind = ButtonKind.SUBTLE, enabled = !draft.isEmpty)
            })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FluentPill(com.innovation313.roshankhata.data.Access.current?.let { Format.name(it.name) } ?: word(W.access_owner), com.innovation313.roshankhata.data.Access.current != null) { signingIn = true }
                if (held.isNotEmpty()) FluentButton(words.text(W.pos_held_tickets, held.size), { showHeld = true }, kind = ButtonKind.STANDARD)
                FluentButton(word(W.z_title), { reporting = true }, kind = ButtonKind.SUBTLE)
                // The shift on this counter: who has it open, or that none is (a counter in counter mode should have one).
                FluentButton(
                    if (myShift != null) word(W.shift_title) else word(W.shift_none), { shifting = true },
                    kind = if (myShift == null && com.innovation313.roshankhata.data.Access.counterMode) ButtonKind.STANDARD else ButtonKind.SUBTLE
                )
                FluentButton(word(W.counter_settings), { Gates.need(com.innovation313.roshankhata.data.Access.Permission.SETTINGS) { settingUp = true } }, kind = ButtonKind.SUBTLE)
                FluentButton(word(W.cd_title), { showingCustomer = !showingCustomer }, kind = if (showingCustomer) ButtonKind.STANDARD else ButtonKind.SUBTLE)
            }
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
            if (products.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluentPill(word(W.pos_ticket) + if (draft.lines.isNotEmpty()) " (" + draft.lines.size + ")" else "", !grid) { grid = false }
                FluentPill(word(W.pos_items), grid) { grid = true }
            }
            // The item grid over the ticket: tiles to tap, the lines still in sight under them.
            if (grid) ItemGridPane(products, onHand, draft, onPick = { ring(it) }, modifier = Modifier.fillMaxWidth().weight(0.55f))
            ListCard(Modifier.fillMaxWidth().weight(if (grid) 0.45f else 1f)) {
                WeightedHead(listOf(word(W.pos_col_item) to 5f, word(W.pos_col_qty) to 2f, word(W.pos_col_rate) to 2f, word(W.pos_discount) to 2f, word(W.pos_col_total) to 2f, "" to 1f), endAligned = setOf(4))
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
                                        val under = listOfNotNull(
                                            line.unit, line.shortBy?.let { words.text(W.pos_stock_low, Calc.trim(line.onHand ?: 0.0)) },
                                            line.taxRate?.takeIf { it > 0.0 }?.let { word(W.pos_tax) + " " + Calc.trim(it) + "%" }
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (under.isNotEmpty()) Text(under.joinToString(" · "), color = if (line.shortBy != null) c.owedToMe else c.textMuted, fontSize = 12.sp, maxLines = 1)
                                            // A product also sold by the pack (schema 36): the line switches between the two.
                                            if (line.canSwitchUnit) Text(
                                                "⇄ " + words.text(W.pos_sell_by, line.otherUnit.orEmpty()),
                                                Modifier.press { line.product?.let { line.switchUnit(draft.unitRate(it)) } }.padding(horizontal = 4.dp),
                                                color = Look.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                                            )
                                        }
                                    }
                                }
                                FluentField(line.qty, { line.qty = it }, Modifier.weight(2f).then(if (i == draft.lines.lastIndex) Modifier.focusRequester(qtyFocus) else Modifier), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                                FluentField(line.rate, { line.rate = it }, Modifier.weight(2f), placeholder = "0", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                                FluentField(line.discount, { line.discount = it }, Modifier.weight(2f), placeholder = "0", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
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
            // The day's tickets and returns in their own scrolling strip, so on a laptop they never
            // squeeze the ticket being rung up (21 Oct 2026, the critical review).
            Column(Modifier.fillMaxWidth().heightIn(max = if (narrow) 150.dp else 230.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RecentSales(recent.take(8), words,
                onVoid = { s -> allowed(com.innovation313.roshankhata.data.Access.Permission.VOID, words.text(W.pos_void) + " " + s.saleNumber) { voiding = s } },
                onReturn = { s ->
                    allowed(com.innovation313.roshankhata.data.Access.Permission.RETURN, words.text(W.pos_return) + " " + s.saleNumber) {
                        scope.launch {
                            val full = withContext(Dispatchers.IO) { dao.saleWithLines(s.id) } ?: return@launch
                            val back = withContext(Dispatchers.IO) { dao.returnedOfSale(s.id) }.associate { it.saleItemId to it.qty }
                            returning = com.innovation313.roshankhata.screens.ReturnDraft(full, back)
                        }
                    }
                },
                // The ticket as an A4 invoice for the customer's own books (21 Oct 2026).
                onInvoice = { s ->
                    scope.launch {
                        val id = withContext(Dispatchers.IO) { com.innovation313.roshankhata.data.SaleInvoice.of(dao, s.id, words) }
                        if (id != null) onInvoice(id)
                    }
                })
            RecentReturns(returns.take(6), words, onVoid = { r -> allowed(com.innovation313.roshankhata.data.Access.Permission.VOID, words.text(W.pos_void_return) + " " + r.returnNumber) { voidingReturn = r } })
            }
        }

        // ---- The end side: who, how much, how paid, the button ----
        Column(Modifier.width(if (narrow) 300.dp else 360.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    // Loyalty (21 Oct 2026): the customer's points, and spending them on this ticket.
                    val rule = draft.loyalty
                    if (rule != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(words.text(W.loyalty_points, Calc.trim(draft.pointsBalance), Format.money(rule.value(draft.pointsBalance))), Modifier.weight(1f), color = c.inkSoft, fontSize = 12.sp)
                        if (draft.pointsUsed > 0.0 || draft.pointsMax > 0.0) FluentPill(word(W.loyalty_use), draft.pointsUsed > 0.0) { draft.usePoints(draft.pointsUsed <= 0.0) }
                    }
                }
            } }
            FluentCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FigureRow(word(W.pos_subtotal), Format.money(draft.totals.subtotal))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FluentField(draft.discount, { draft.discount = it }, Modifier.weight(1f).focusRequester(discountFocus), label = word(W.pos_discount), prefix = currency, placeholder = "0", trailing = { KeyCap("F6") }, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                    FluentField(draft.taxPercent, { draft.taxPercent = it }, Modifier.weight(1f), label = word(W.pos_tax), placeholder = "0", calc = com.innovation313.roshankhata.screens.CalcKind.PERCENT)
                }
                if (draft.pointsValue > 0.0) FigureRow(words.text(W.loyalty_used, Calc.trim(draft.pointsUsed), Format.money(draft.pointsValue)), "−" + Format.money(draft.pointsValue), Look.accent)
                if (draft.totals.tax > 0.0) FigureRow(word(W.pos_tax), Format.money(draft.totals.tax))
                if (draft.totals.taxIncluded > 0.0) FigureRow(word(W.pos_tax_included), Format.money(draft.totals.taxIncluded))
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
                            FluentField(p.amount, { p.amount = it }, Modifier.weight(1f), prefix = currency, placeholder = "0", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
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
            // Two wide buttons side by side on a desktop; one under the other on a laptop (1093 dp cut "Complete s").
            if (narrow) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FluentButton(word(W.pos_post), { complete(false) }, Modifier.fillMaxWidth(), kind = ButtonKind.ACCENT, icon = "ic_check_plain", enabled = problem == null && !draft.posting, tall = true)
                FluentButton(word(W.pos_post_and_print), { complete(true) }, Modifier.fillMaxWidth(), kind = ButtonKind.STANDARD, icon = "ic_print", enabled = problem == null && !draft.posting, tall = true)
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluentButton(word(W.pos_post), { complete(false) }, Modifier.weight(1f), kind = ButtonKind.ACCENT, icon = "ic_check_plain", enabled = problem == null && !draft.posting, tall = true)
                FluentButton(word(W.pos_post_and_print), { complete(true) }, kind = ButtonKind.STANDARD, icon = "ic_print", enabled = problem == null && !draft.posting, tall = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { KeyCap("F12") }
        }
    }
    }

    if (choosingParty) {
        DialogWindow(onCloseRequest = { choosingParty = false }, onPreviewKeyEvent = escCloses { choosingParty = false }, title = word(W.pos_choose_customer), state = rememberDialogState(width = fitW(520.dp), height = fitH(480.dp))) {
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
        DialogWindow(onCloseRequest = { showHeld = false }, onPreviewKeyEvent = escCloses { showHeld = false }, title = words.text(W.pos_held_tickets, held.size), state = rememberDialogState(width = fitW(560.dp), height = fitH(480.dp))) {
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
                                draft.load(full, onHandOf = { onHand[it] }, productOf = { id -> products.firstOrNull { it.id == id } })
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
    returning?.let { draft ->
        var problem by remember { mutableStateOf<String?>(null) }
        DialogWindow(onCloseRequest = { returning = null }, onPreviewKeyEvent = escCloses { returning = null }, title = word(W.pos_return_title), state = rememberDialogState(width = fitW(620.dp), height = fitH(720.dp))) {
            DialogFrame(word(W.pos_return_title), subtitle = draft.sale.sale.saleNumber, onClose = { returning = null }, scroll = false, footer = {}) {
                com.innovation313.roshankhata.screens.ReturnForm(draft, onCancel = { returning = null }, message = problem, onPost = {
                    // The form calls this only while nothing is being posted (ReturnDraft.posting).
                    draft.posting = true
                    scope.launch {
                        try {
                            val r = withContext(Dispatchers.IO) { runCatching { dao.postReturn(draft.toReturn(), draft.toItems()) } }
                            r.onSuccess { id ->
                                val number = withContext(Dispatchers.IO) { dao.getReturn(id)?.returnNumber }.orEmpty()
                                told = words.text(W.pos_return_posted, number)
                                returning = null; tick++; onChanged()
                            }.onFailure { problem = failText(words, it) }
                        } finally { draft.posting = false }
                    }
                })
            }
        }
    }
    voidingReturn?.let { r ->
        var reason by remember(r.id) { mutableStateOf("") }
        DialogWindow(onCloseRequest = { voidingReturn = null }, onPreviewKeyEvent = escCloses { voidingReturn = null }, title = word(W.pos_void_return), state = rememberDialogState(width = fitW(480.dp), height = fitH(300.dp))) {
            DialogFrame(word(W.pos_void_return), subtitle = r.returnNumber, onClose = { voidingReturn = null }, footer = {
                FluentButton(word(W.cancel), { voidingReturn = null }, kind = ButtonKind.STANDARD)
                FluentButton(word(W.pos_void_return), {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { dao.voidReturn(r.id, reason) }.getOrDefault(false) }
                        voidingReturn = null; tick++; onChanged()
                        // Said only when it happened (21 Oct 2026: the screen said "voided" either way).
                        told = if (ok) words.text(W.pos_voided) + " " + r.returnNumber else words.text(W.backup_failed)
                    }
                }, kind = ButtonKind.DANGER, enabled = reason.isNotBlank())
            }) {
                FluentField(reason, { reason = it }, Modifier.fillMaxWidth(), label = word(W.pos_void_reason))
            }
        }
    }
    voiding?.let { s ->
        var reason by remember(s.id) { mutableStateOf("") }
        DialogWindow(onCloseRequest = { voiding = null }, onPreviewKeyEvent = escCloses { voiding = null }, title = word(W.pos_void), state = rememberDialogState(width = fitW(480.dp), height = fitH(300.dp))) {
            DialogFrame(word(W.pos_void), subtitle = words.text(W.pos_void_confirm, s.saleNumber), onClose = { voiding = null }, footer = {
                FluentButton(word(W.cancel), { voiding = null }, kind = ButtonKind.STANDARD)
                FluentButton(word(W.pos_void), {
                    scope.launch {
                        // A ticket with goods already back, or from a day that is closed, is not voided:
                        // a return is the way (21 Oct 2026), and the screen says which.
                        val refusal = withContext(Dispatchers.IO) { runCatching { dao.voidRefusal(s.id) }.getOrNull() }
                        val ok = refusal == null && withContext(Dispatchers.IO) { runCatching { dao.voidSale(s.id, reason) }.getOrDefault(false) }
                        voiding = null; tick++; onChanged()
                        told = when {
                            ok -> words.text(W.pos_voided) + " " + s.saleNumber
                            refusal == SaleMath.VoidRefusal.HAS_RETURNS -> words.text(W.pos_void_has_returns)
                            refusal == SaleMath.VoidRefusal.DAY_CLOSED -> words.text(W.pos_void_day_closed)
                            else -> words.text(W.backup_failed)
                        }
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
private fun RecentSales(rows: List<SaleSummary>, words: Words, onVoid: (SaleSummary) -> Unit, onReturn: (SaleSummary) -> Unit, onInvoice: (SaleSummary) -> Unit) {
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
                if (s.status == SaleStatus.DONE) {
                    IconAction("ic_tile_invoice", word(W.pos_make_invoice), { onInvoice(s) })
                    IconAction("ic_arrow_got", word(W.pos_return), { onReturn(s) })
                    IconAction("ic_close", word(W.pos_void), { onVoid(s) })
                } else Spacer(Modifier.width(8.dp))
            }
            if (i < rows.lastIndex) Rule()
        }
    }
}

/** The last few returns, with their void. */
@Composable
private fun RecentReturns(rows: List<com.innovation313.roshankhata.data.ReturnSummary>, words: Words, onVoid: (com.innovation313.roshankhata.data.ReturnSummary) -> Unit) {
    val c = LocalRoshanColors.current
    if (rows.isEmpty()) return
    ListCard(Modifier.fillMaxWidth()) {
        ListHead(word(W.pos_returns))
        rows.forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Format.ltr(r.returnNumber), Modifier.width(96.dp), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                Text(listOfNotNull(r.saleNumber?.let { Format.ltr(it) }, r.customerName?.let { Format.name(it) } ?: word(W.pos_walk_in)).joinToString(" · "), Modifier.weight(1f), color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.dateTime(r.timestamp), color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false)
                Text(Format.money(r.total), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                if (r.status == SaleStatus.VOID) Tag(word(W.pos_status_void), c.owedToMe, Look.getTint)
                else FluentButton(word(W.pos_void), { onVoid(r) }, kind = ButtonKind.SUBTLE)
            }
            if (i < rows.lastIndex) Rule()
        }
    }
}

/** The thermal receipt for a ticket — the lines ThermalReceipt prints or saves as a picture. */
object SaleReceipt {
    fun lines(words: Words, shop: String, s: SaleWithLines, currency: String, party: PartyWithBalance?,
              points: com.innovation313.roshankhata.data.Loyalty.OnReceipt? = null): List<ThermalReceipt.Line> {
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
        // Tax that was inside the prices (schema 36): said, not added.
        val inside = s.items.filter { it.taxIncluded == true }.sumOf { it.taxAmount ?: 0.0 }
        if (inside > 0.004) out += ThermalReceipt.Line(words.text(W.pos_tax_included) + ": " + Format.money(inside), 18f, centre = true)
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
        // Loyalty (21 Oct 2026): spent, earned, held now.
        points?.let { pt ->
            if (pt.used > 0.0) out += ThermalReceipt.Line(words.text(W.loyalty_used, Calc.trim(pt.used), Format.money(pt.usedValue)), 18f)
            out += ThermalReceipt.Line(words.text(W.loyalty_earned, Calc.trim(pt.earned), Calc.trim(pt.balance)), 18f)
        }
        out += ThermalReceipt.Line("")
        out += ThermalReceipt.Line(words.text(W.app_name), 16f, centre = true)
        return out
    }
}
