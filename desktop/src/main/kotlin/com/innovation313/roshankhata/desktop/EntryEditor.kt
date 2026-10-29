package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import com.innovation313.roshankhata.data.BatchOption
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.RateType
import com.innovation313.roshankhata.data.SeasonBook
import kotlinx.coroutines.delay
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.PaymentRef
import com.innovation313.roshankhata.data.Recovery
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.util.Calendar
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.EntryLines
import com.innovation313.roshankhata.data.LineDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the entry form hands back: the entry (unsaved), its goods lines (every one: the form edits the whole
 * list since Marhala 4, part 6), whether the cash came with a cash-rate sale ([paidNow]: the matching "I got"
 * is written with it, paired), the payment day picked on the form ([due], only when [dueTouched]; null then
 * means "remove the promise"), a bill's picture chosen on the form ([billPhoto]: copied into the computer's
 * bill photos when the entry is written, so a cancelled form leaves nothing behind) and the customer's
 * signature ([signature], or taken off: [signatureChanged] with none).
 */
class EntryDraft(
    val entry: LedgerEntry,
    val items: List<EntryItem>,
    val paidNow: Boolean = false,
    val due: Long? = null,
    val dueTouched: Boolean = false,
    val billPhoto: java.io.File? = null,
    val signature: java.awt.image.BufferedImage? = null,
    val signatureChanged: Boolean = false
)

/**
 * The rate type an entry is saved with (25 Oct 2026, the audit). A sale's
 * goods rate says cash or credit, and a ticked "paid now" is a cash sale even
 * with no goods line, so its cash "I got" is written with it. It used to be
 * written only when a goods line had a rate: a customer who paid on the spot
 * then read as owing what he had paid. Anything else keeps what it had.
 */
fun entryRateType(isSale: Boolean, goodsHaveRate: Boolean, cashRate: Boolean, paidNow: Boolean, kept: String?): String? =
    if (isSale && (goodsHaveRate || paidNow)) (if (cashRate) RateType.CASH else RateType.CREDIT) else kept

/** One goods line as a short text: name, quantity with its unit, rate, and the free mark. */
fun goodsLineText(line: EntryItem, freeWord: String): String = listOfNotNull(
    line.itemName?.trim()?.takeIf { it.isNotEmpty() },
    line.quantity?.let { q -> Calc.trim(q) + com.innovation313.roshankhata.data.UnitWords.label(line.unit).let { u -> if (u.isEmpty()) "" else " $u" } },
    line.rate?.let { "@ " + Format.money(it) },
    freeWord.takeIf { line.isBonus }
).joinToString("  ·  ")

/**
 * A khata entry on the computer, with what the phone's entry dialog has (KMP Phase 8a; Marhala 4, part 6 the
 * rest): the amount (sums allowed) — following the goods' total until the owner writes his own (a discount,
 * a round-off: the difference said in words); the day and the time it happened (never ahead of now); the
 * goods as a list, each item from the shop's products (the name box suggests them) at the product's price for
 * Credit or Cash, this customer's last price beside it, its batch; "Last time's items" on a new sale; a note;
 * Qarz-e-Hasna and the chance of recovery; how money came in; the bill's photo and the customer's signature.
 * Editing starts from the entry and all its goods.
 */
@Composable
fun EntryEditor(
    partyId: Long,
    partyName: String,
    isGiven: Boolean,
    existing: LedgerEntry?,
    /** The entry's goods lines, in order (an edit). */
    existingItems: List<EntryItem> = emptyList(),
    onSave: (EntryDraft) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onPrint: (() -> Unit)? = null,
    /** A customer (not a supplier): sales get a rate type, prices, "paid now" and batches. */
    isCustomer: Boolean = true,
    /** The open book: products, batches and this customer's last prices and sale, as the goods are typed. */
    dao: com.innovation313.roshankhata.data.KhataDao? = null,
    /** The customer's promised payment day (Party.harvestPromise), shown on the due button. */
    promise: Long? = null,
    /** The entry's signature as kept on this computer (an edit), or null. */
    signature: java.awt.image.BufferedImage? = null,
    /** A new entry's amount brought from elsewhere (the calculator's "Add to a khata"), or null. */
    startAmount: Double? = null
) {
    val c = LocalRoshanColors.current
    val words = com.innovation313.roshankhata.words.LocalWords.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val isSale = isGiven && isCustomer
    val isReturn = !isGiven && isCustomer
    var amountText by remember { mutableStateOf((existing?.amount ?: startAmount)?.let { Calc.trim(it) }.orEmpty()) }
    var at by remember { mutableStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var qarz by remember { mutableStateOf(existing?.isQarzeHasna ?: false) }
    var doubtful by remember { mutableStateOf(existing?.recovery == Recovery.DOUBTFUL) }
    // A new "I got" starts as Cash, as on the phone (21 Oct 2026); an edited entry keeps its own.
    var method by remember { mutableStateOf(if (existing == null) PaymentMethod.CASH else existing.paymentMethod) }
    var paymentRef by remember { mutableStateOf(existing?.paymentRef.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var cashRate by remember { mutableStateOf(existing?.rateType == RateType.CASH) }
    val rateType = if (cashRate) RateType.CASH else RateType.CREDIT
    var paidNow by remember { mutableStateOf(false) }
    var due by remember { mutableStateOf(promise) }
    var dueTouched by remember { mutableStateOf(false) }
    var choosingDue by remember { mutableStateOf(false) }

    // ---- The goods: the list, and the item being written in the boxes ----
    val goods = remember { GoodsList.of(existingItems) }
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    // The figure the app itself last put in the Rate box: anything else there was typed by the owner.
    var autoRate by remember { mutableStateOf<Double?>(null) }
    var product by remember { mutableStateOf<com.innovation313.roshankhata.data.Product?>(null) }
    var batches by remember { mutableStateOf<List<BatchOption>>(emptyList()) }
    var batchId by remember { mutableStateOf<Long?>(null) }
    var batchMenu by remember { mutableStateOf(false) }
    var lastCredit by remember { mutableStateOf<com.innovation313.roshankhata.data.LastRate?>(null) }
    var lastCash by remember { mutableStateOf<com.innovation313.roshankhata.data.LastRate?>(null) }
    var lastAny by remember { mutableStateOf<com.innovation313.roshankhata.data.LastRate?>(null) }
    var lineProblem by remember { mutableStateOf<String?>(null) }
    val typedRate = Calc.evalAmount(rate)
    val rateTyped = typedRate != null && typedRate != autoRate
    val shelf = dao?.let { rememberProductShelf(it) }
    // The amount follows the items (the phone's two facts): [amountOwn] — the owner wrote the figure over
    // goods; [amountByItems] — the figure in the box is one the items wrote, so it goes when they go. A saved
    // entry at exactly its items' total keeps following them; one at a figure of its own keeps that figure.
    val savedAtTotal = remember {
        val t = EntryLines.total(goods.lines)
        t != null && existing != null && EntryLines.compare(t, existing.amount) is EntryLines.AmountCheck.Equal
    }
    var amountOwn by remember { mutableStateOf(goods.lines.isNotEmpty() && !savedAtTotal) }
    var amountByItems by remember { mutableStateOf(savedAtTotal) }

    fun boxes(): LineDraft? = boxesLine(
        name, Calc.evalAmount(qty), com.innovation313.roshankhata.data.UnitWords.canonical(unit), typedRate, rateTyped,
        product, batchId?.takeIf { isSale && (batches.any { b -> b.id == it } || it == goods.pulled?.billItemId) }, goods.pulled
    )
    val editor = boxes()
    val all = goods.all(editor)
    val total = EntryLines.total(all)

    // What the name says, looked up once it settles (the phone's refreshBatchButton): the product (its unit
    // when none is typed), the batches a sale may be drawn from, and this customer's last prices.
    LaunchedEffect(name, dao) {
        val typed = name.trim()
        val book = dao
        if (typed.isEmpty() || book == null || !isCustomer) {
            product = null; batches = emptyList(); lastCredit = null; lastCash = null; lastAny = null
            if (typed.isEmpty()) batchId = null
            return@LaunchedEffect
        }
        delay(300)
        val p = withContext(Dispatchers.IO) { runCatching { book.productByKey(com.innovation313.roshankhata.data.ProductName.key(typed)) }.getOrNull() }
        val options = if (isSale && p != null) withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.Fefo.options(book, p) }.getOrDefault(emptyList()) } else emptyList()
        val (cr, ca, any) = if (p == null) Triple(null, null, null) else withContext(Dispatchers.IO) {
            runCatching {
                Triple(
                    if (isSale) book.lastSaleRate(partyId, p.id, RateType.CREDIT) else null,
                    if (isSale) book.lastSaleRate(partyId, p.id, RateType.CASH) else null,
                    if (isReturn) book.lastSaleRate(partyId, p.id) else null
                )
            }.getOrDefault(Triple(null, null, null))
        }
        product = p
        lastCredit = cr; lastCash = ca; lastAny = any
        // The product's own unit when none is typed, so the same goods are not "bori" one day and "bag" the next.
        val pUnit = p?.defaultUnit?.trim()
        if (!pUnit.isNullOrEmpty() && unit.isBlank()) unit = com.innovation313.roshankhata.data.UnitWords.label(pUnit)
        batches = options
        if (batchId != null && options.none { it.id == batchId } && batchId != goods.pulled?.billItemId) batchId = null
    }
    // The Rate box offered the product's price (or a return's last price) — never over a rate the owner typed.
    LaunchedEffect(product, rateType, unit, lastAny) {
        if (rateTyped) return@LaunchedEffect
        val offered = offeredRate(isSale, isReturn, product, rateType, com.innovation313.roshankhata.data.UnitWords.canonical(unit).orEmpty(), lastAny)
        val text = offered?.let { Calc.trim(it) } ?: ""
        if (rate != text) rate = text
        autoRate = offered
    }
    // The amount box following the items.
    LaunchedEffect(all, amountOwn) {
        followedAmount(all, amountText, amountOwn, amountByItems)?.let { amountText = it; amountByItems = it.isNotEmpty() }
    }
    // "Last time's items" (a new sale, nothing on the form yet): this customer's last sale with goods.
    var lastSale by remember { mutableStateOf<com.innovation313.roshankhata.data.EntryWithItems?>(null) }
    LaunchedEffect(dao) {
        val book = dao ?: return@LaunchedEffect
        if (isSale && existing == null) lastSale = withContext(Dispatchers.IO) { runCatching { book.lastSaleWithItems(partyId) }.getOrNull() }
    }
    fun clearBoxes() {
        name = ""; qty = ""; unit = ""; rate = ""; autoRate = null
        product = null; batchId = null; batches = emptyList(); lastCredit = null; lastCash = null; lastAny = null
        lineProblem = null
    }
    fun addLine() {
        val d = boxes()
        when {
            d == null || d.itemName.isNullOrBlank() -> lineProblem = words.text(W.line_need_name)
            (d.quantity ?: 0.0) <= 0.0 -> lineProblem = words.text(W.line_need_qty)
            else -> { goods.add(d); clearBoxes() }
        }
    }
    fun pullBack(i: Int) {
        // One item in the boxes at a time: the owner adds or clears what is there first.
        if (boxes() != null) { lineProblem = words.text(W.line_editor_busy); return }
        val d = goods.pullBack(i) ?: return
        name = d.itemName.orEmpty()
        qty = d.quantity?.let { Calc.trim(it) }.orEmpty()
        unit = com.innovation313.roshankhata.data.UnitWords.label(d.unit)
        // An old free item comes back a plain one with no rate (the phone retired Free); a rate the owner typed
        // stays his, one that came from the product may be refreshed by the lookup.
        rate = if (d.isBonus) "" else d.rate?.let { Calc.trim(it) }.orEmpty()
        autoRate = if (d.rateEdited || d.isBonus) null else d.rate
        batchId = d.billItemId
        lineProblem = null
    }
    fun repeatLast(source: com.innovation313.roshankhata.data.EntryWithItems) {
        val book = dao ?: return
        scope.launch {
            // The same goods at TODAY's price for the chip (an item with no product keeps its old rate, as the
            // owner's); no batches — last time's may be finished; a free item comes plain, with no rate.
            val drafts = withContext(Dispatchers.IO) {
                source.orderedItems().map { item ->
                    val p = item.productId?.let { runCatching { book.productById(it) }.getOrNull() }
                    when {
                        item.isBonus -> LineDraft(itemName = item.itemName, quantity = item.quantity, unit = item.unit, rate = null, productId = p?.id)
                        p != null -> LineDraft(
                            itemName = item.itemName, quantity = item.quantity, unit = item.unit,
                            rate = (com.innovation313.roshankhata.data.RateOffer.price(true, p.creditPrice, p.salePrice, p.defaultUnit, rateType, item.unit.orEmpty())
                                as? com.innovation313.roshankhata.data.RateOffer.Result.Offer)?.rate,
                            productId = p.id, creditPrice = p.creditPrice, cashPrice = p.salePrice, productUnit = p.defaultUnit
                        )
                        else -> LineDraft(itemName = item.itemName, quantity = item.quantity, unit = item.unit, rate = item.rate, rateEdited = true)
                    }
                }
            }
            drafts.forEach { goods.add(it) }
        }
    }

    // ---- The bill's photo and the signature: held here, written with the entry ----
    var photoPath by remember { mutableStateOf(existing?.billPhotoPath) }
    var photoSource by remember { mutableStateOf<java.io.File?>(null) }
    var photoProblem by remember { mutableStateOf<String?>(null) }
    var sig by remember { mutableStateOf(signature) }
    var sigChanged by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    // A return of more than this customer holds on the book: asked first, never refused (goods may predate the app).
    var overReturn by remember { mutableStateOf<Pair<EntryDraft, String>?>(null) }

    val amount = Calc.evalAmount(amountText)
    fun draft(): EntryDraft? {
        val a = amount ?: return null
        // With a list, the boxes hold a whole item or nothing: half an item is not saved in silence.
        val inBoxes = boxes()
        if (goods.lines.isNotEmpty() && inBoxes != null && !inBoxes.isComplete()) {
            lineProblem = words.text(if (inBoxes.itemName.isNullOrBlank()) W.line_need_name else W.line_need_qty)
            return null
        }
        val items = goods.all(inBoxes).mapNotNull { it.toItem() }
        val base = existing ?: LedgerEntry(partyId = partyId, amount = a, isGiven = isGiven, entryNumber = "")
        // Ticked "paid now" (a new cash sale): its "I got" is written even when no goods line has a rate.
        val tickedPaid = existing == null && isSale && cashRate && paidNow
        val kept = entryRateType(isSale, items.any { it.rate != null }, cashRate, tickedPaid, base.rateType)
        return EntryDraft(
            base.copy(
                amount = a,
                timestamp = at,
                note = note.trim().ifEmpty { null },
                // A loan of money cannot carry a list of goods (the phone's rule).
                isQarzeHasna = isGiven && qarz && items.isEmpty(),
                recovery = if (isGiven && doubtful) Recovery.DOUBTFUL else Recovery.CERTAIN,
                // Goods coming back are not money in any way (the phone's rule): no method.
                paymentMethod = if (isGiven) base.paymentMethod else if (items.isNotEmpty()) null else method,
                paymentRef = if (isGiven) base.paymentRef else if (items.isNotEmpty()) null else PaymentRef.clean(method, paymentRef),
                rateType = kept,
                billPhotoPath = photoPath
            ),
            items,
            paidNow = tickedPaid,
            due = due,
            dueTouched = dueTouched,
            billPhoto = photoSource,
            signature = sig,
            signatureChanged = sigChanged
        )
    }
    fun trySave() {
        val d = draft() ?: return
        val book = dao
        if (!isReturn || book == null || d.items.none { it.productId != null && it.quantity != null }) { onSave(d); return }
        scope.launch {
            val over = withContext(Dispatchers.IO) {
                d.items.filter { it.productId != null && it.quantity != null }.groupBy { (it.productId ?: 0L) to it.unit }.mapNotNull { (key, group) ->
                    val returning = group.sumOf { it.quantity ?: 0.0 }
                    val held = runCatching { book.netGoodsWithParty(partyId, key.first, key.second, excludeEntryId = existing?.id ?: 0L) }.getOrDefault(Double.MAX_VALUE)
                    if (returning > held + 1e-9) words.text(W.return_more_than_taken, partyName, Calc.trim(maxOf(held, 0.0)),
                        com.innovation313.roshankhata.data.UnitWords.label(key.second), group.first().itemName.orEmpty()) else null
                }
            }
            if (over.isEmpty()) onSave(d) else overReturn = d to over.joinToString("\n\n")
        }
    }

    // The form scrolls; its buttons stay in sight under it (25 Oct 2026, the audit: Save sat below the fold on a laptop).
    // Its head stays on top (the owner's mockup, Marhala 2): which way on its tint, the party under it, the ✕ —
    // the dialog's one title, so the dialog draws none of its own.
    OwnDialogHead()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val way = if (isGiven) c.owedToMe else Look.accent
            Box(Modifier.size(40.dp).background(if (isGiven) Look.getTint else Look.accentTint, Look.ctl), contentAlignment = Alignment.Center) {
                Glyph(if (isGiven) "ic_arrow_gave" else "ic_arrow_got", way, 20)
            }
            Column(Modifier.weight(1f)) {
                Text(word(if (isGiven) W.you_gave else W.you_got), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(Format.name(partyName), color = c.inkSoft, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Box(Modifier.size(32.dp).clip(Look.ctl).press(Look.ctl, onClick = onCancel), contentAlignment = Alignment.Center) {
                Glyph("ic_close", c.inkSoft, 14)
            }
        }
        Rule()
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // The amount big with its figure in money under it, the day and the time beside it.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                FluentField(
                    amountText,
                    {
                        amountText = it
                        // The owner's own figure: his only when goods are already on the form (a discount, a round-off).
                        amountByItems = false
                        amountOwn = it.isNotBlank() && all.isNotEmpty()
                    },
                    Modifier.weight(1.3f), label = word(W.amount), prefix = com.innovation313.roshankhata.data.Currency.symbol, big = true,
                    help = amount?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                DateBox(word(W.date_label), Format.dateOnly(at), word(W.pick_date), { picking = true }, Modifier.weight(1f), tall = true)
                DateBox(word(W.home_col_time), remember(at) { com.innovation313.roshankhata.data.DateWords.formatter("h:mm a").format(java.util.Date(at)) }, word(W.pick_time), { pickingTime = true }, Modifier.weight(0.75f), tall = true)
            }
            // The items against the written amount (the phone's line under the amount): what is missing for a
            // total, or how far the figure is from it, and the items' total offered back.
            if (all.isNotEmpty()) {
                val t = total
                when {
                    t == null -> if (goods.lines.isNotEmpty() || amountText.isBlank()) Text(
                        word(if (all.any { it.quantity == null }) W.lines_total_need_qty else W.lines_total_unknown), color = c.textMuted, fontSize = 12.sp)
                    t > 0.0 && amount != null && EntryLines.compare(t, amount) !is EntryLines.AmountCheck.Equal -> {
                        when (val check = EntryLines.compare(t, amount)) {
                            is EntryLines.AmountCheck.Less -> Text(word(W.lines_amount_less, Format.money(t), Format.money(amount), Format.money(check.by)), color = c.inkSoft, fontSize = 12.sp)
                            is EntryLines.AmountCheck.More -> Text(word(W.lines_amount_more, Format.money(t), Format.money(amount), Format.money(check.by)), color = c.owedToMe, fontSize = 12.sp)
                            else -> {}
                        }
                        FluentButton(word(W.lines_total_fill, Format.money(t)), { amountOwn = false; amountText = Calc.trim(t); amountByItems = true }, kind = ButtonKind.SUBTLE)
                    }
                    t > 0.0 && amount == null -> FluentButton(word(W.lines_total_fill, Format.money(t)), { amountOwn = false; amountText = Calc.trim(t); amountByItems = true }, kind = ButtonKind.SUBTLE)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(word(W.goods_section), Modifier.weight(1f), color = c.ink, fontWeight = FontWeight.Bold)
                // The same goods as last time, at today's prices, to change or remove before saving.
                val last = lastSale
                if (last != null && goods.lines.isEmpty() && editor == null) {
                    FluentButton(word(W.repeat_last_sale, Format.dateOnly(last.entry.timestamp)), { repeatLast(last) }, kind = ButtonKind.SUBTLE, icon = "ic_list_all")
                }
            }
            // The shop's own words (TradeVocab): a dairy's form says milk and litre, not urea and bag.
            val trade = LocalTrade.current
            if (goods.lines.isEmpty()) Text(trade.goodsHelp(), color = c.textMuted, fontSize = 12.sp)
            goods.lines.forEachIndexed { i, d ->
                Row(
                    Modifier.fillMaxWidth().clip(Look.ctl).background(Look.subtle).press(Look.ctl) { pullBack(i) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        val rateText = if (d.isBonus) "  (" + word(W.bonus_tag) + ")" else d.rate?.let { " × " + Format.money(it) }.orEmpty()
                        val totalText = if (d.isBonus) "" else d.total()?.let { " = " + Format.money(it) }.orEmpty()
                        Text("${i + 1}. " + Format.goods(d.itemName, d.quantity, d.unit).orEmpty() + rateText + totalText, color = c.ink, fontSize = 14.sp)
                        // The dealer's spray advice rides with the item (shown, never lost by an edit).
                        val advice = listOfNotNull(d.crop, d.pest, d.dose).filter { it.isNotBlank() }
                        if (advice.isNotEmpty()) Text(advice.joinToString("  ·  "), color = c.textMuted, fontSize = 12.sp)
                    }
                    IconAction("ic_close", word(W.remove_line), { goods.remove(i) })
                }
            }
            SuggestBox(name, { name = it; lineProblem = null }, shelf, trade.itemHint(), Modifier.fillMaxWidth())
            if (name.isBlank() && goods.lines.isEmpty() && shelf == null) SuggestionRow(trade.starters(), name) { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluentField(qty, { qty = it; lineProblem = null }, Modifier.weight(1f), placeholder = word(W.quantity_hint), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                FluentField(unit, { unit = it }, Modifier.weight(1f), placeholder = trade.unitHint())
                FluentField(rate, { rate = it }, Modifier.weight(1f), placeholder = word(W.entry_rate_hint), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            }
            SuggestionRow(trade.units(), unit) { unit = it }
            // What the product's price says for the chosen chip, and what this customer paid last time.
            val p = product
            if (isSale && p != null) {
                when (val offer = com.innovation313.roshankhata.data.RateOffer.price(true, p.creditPrice, p.salePrice, p.defaultUnit, rateType, com.innovation313.roshankhata.data.UnitWords.canonical(unit).orEmpty())) {
                    is com.innovation313.roshankhata.data.RateOffer.Result.NotSet -> Text(word(if (cashRate) W.rate_not_set_cash else W.rate_not_set_credit), color = c.textMuted, fontSize = 12.sp)
                    is com.innovation313.roshankhata.data.RateOffer.Result.UnitMismatch -> Text(
                        word(W.rate_unit_mismatch, com.innovation313.roshankhata.data.UnitWords.label(offer.productUnit), com.innovation313.roshankhata.data.UnitWords.label(offer.typedUnit)), color = c.textMuted, fontSize = 12.sp)
                    else -> {}
                }
                val typedUnit = com.innovation313.roshankhata.data.UnitWords.canonical(unit).orEmpty()
                (if (cashRate) lastCash else lastCredit)?.takeIf { r -> typedUnit.isEmpty() || r.unit == null || r.unit.equals(typedUnit, ignoreCase = true) }?.let { r ->
                    Text(word(W.last_rate_hint, Format.money(r.rate)), color = c.brandGreenText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (isSale && batches.isNotEmpty()) {
                Box {
                    val chosen = batches.firstOrNull { it.id == batchId }
                    TextButton({ batchMenu = true }) {
                        Text(
                            chosen?.let { word(W.batch_chosen, it.batchNumber?.takeIf { b -> b.isNotBlank() } ?: word(W.batch_none)) } ?: word(W.pick_batch),
                            color = c.brandGreenText
                        )
                    }
                    DropdownMenu(batchMenu, { batchMenu = false }) {
                        batches.forEach { b ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        listOfNotNull(
                                            b.batchNumber?.takeIf { it.isNotBlank() } ?: word(W.batch_none),
                                            b.expiryDate?.let { word(W.expiry_date) + ": " + Format.dateOnly(it) },
                                            word(W.batch_remaining, Format.qty(b.remaining, b.unit))
                                        ).joinToString("  ·  ")
                                    )
                                },
                                onClick = { batchId = b.id; batchMenu = false }
                            )
                        }
                        DropdownMenuItem(text = { Text(word(W.pick_batch_clear)) }, onClick = { batchId = null; batchMenu = false })
                    }
                }
            }
            lineProblem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FluentButton(word(W.add_line), { addLine() }, enabled = editor != null)
                // An item pulled back to correct goes back as it was; new typing is emptied.
                if (goods.pulled != null) FluentButton(word(W.cancel), { goods.putBack(); clearBoxes() }, kind = ButtonKind.SUBTLE)
                else if (editor != null) FluentButton(word(W.sig_clear), { clearBoxes() }, kind = ButtonKind.SUBTLE)
            }
            if (isSale) {
                // Fixed at the sale and never recalculated (LedgerEntry.rateType); a switch re-prices the items whose
                // rate came from the product (a rate the owner typed stays his).
                TwoWay(word(W.rate_type_credit), word(W.rate_type_cash), !cashRate) { credit ->
                    cashRate = !credit
                    if (credit) paidNow = false
                    goods.reprice(if (credit) RateType.CREDIT else RateType.CASH, isCustomer)
                }
                if (existing == null && cashRate) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(paidNow, { paidNow = it })
                        Text(word(W.cash_paid_now), color = c.ink, fontSize = 14.sp)
                    }
                }
            }
            FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.note_optional), singleLine = false)
            if (isSale && !paidNow) {
                // When the money is due: a week, a fortnight, a month, the next harvest ends, or any day.
                TextButton({ choosingDue = true }) {
                    Text(due?.let { word(W.entry_due_set, Format.dateOnly(it)) } ?: word(W.entry_due_none), color = c.brandGreenText)
                }
            }

            if (!isGiven && !(isReturn && all.isNotEmpty())) {
                // How it was paid: null unless chosen, as on the phone. The day's cash count
                // (Cashbook, Close the day) counts only khata money marked Cash. Goods coming back were not paid.
                Text(word(W.payment_method_label), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMethod.ALL.forEach { m ->
                        FluentPill(PaymentMethod.label(words, m), m == method) { method = if (m == method) null else m }
                    }
                }
                // The payment's own id (schema 39): a transfer's transaction id, a wallet's TID, a cheque's
                // number — what answers "I sent it on Tuesday". Only beside a way that has one: cash has none.
                if (PaymentRef.applies(method)) {
                    FluentField(paymentRef, { paymentRef = it }, Modifier.fillMaxWidth(), label = word(W.pos_reference_hint), ltr = true)
                }
            }
            if (isGiven) {
                // A loan of money cannot carry a list of goods (the phone's rule).
                val loanAllowed = all.isEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(qarz && loanAllowed, { qarz = it }, enabled = loanAllowed)
                    Text(word(W.qarze_hasna), color = if (loanAllowed) c.ink else c.textMuted, fontSize = 14.sp)
                }
                Text(word(W.qarze_hasna_help), color = c.textMuted, fontSize = 12.sp)
                Text(word(W.recovery_label), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                TwoWay(word(W.recovery_certain), word(W.recovery_doubtful), !doubtful) { certain -> doubtful = !certain }
            }

            // The bill this entry came from: its picture, kept on this computer, left off statements (the phone's rule).
            val shown = photoSource?.absolutePath ?: photoPath
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val thumb = Photos.bitmap(shown, 160)
                if (thumb != null) androidx.compose.foundation.Image(thumb, contentDescription = word(W.bill_photo_label), modifier = Modifier.size(48.dp).clip(Look.ctl), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                Text(word(if (shown != null) W.entry_bill_chip_done else W.entry_bill_chip), Modifier.weight(1f), color = if (shown != null) c.brandGreenText else c.inkSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                FluentButton(word(if (shown != null) W.change_photo else W.add_bill_photo), {
                    val src = Photos.pick(words.text(W.add_bill_photo)) ?: return@FluentButton
                    // Read now, so a file that is not a picture is said at once (it is copied in on Save).
                    if (Photos.bitmap(src.absolutePath, 64) == null) photoProblem = words.text(W.bill_photo_failed)
                    else { photoSource = src; photoProblem = null }
                }, kind = ButtonKind.SUBTLE, icon = "ic_add_plain")
                if (shown != null) IconAction("ic_delete", word(W.remove_photo), { photoSource = null; photoPath = null; photoProblem = null }, tint = c.owedToMe)
            }
            photoProblem?.let { Text(it, color = c.owedToMe, fontSize = 12.sp) }
            // The customer's signature (the phone's row, 18 Oct): drawn with the mouse, kept with the entry.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val drawn = sig
                if (drawn != null) {
                    val pic = remember(drawn) { drawn.toComposeImageBitmap() }
                    androidx.compose.foundation.Image(pic, contentDescription = word(W.sig_label), modifier = Modifier.height(44.dp).width(110.dp).clip(Look.ctl).background(Color.White))
                }
                Column(Modifier.weight(1f)) {
                    Text(word(W.sig_label), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(word(if (drawn != null) W.sig_done else W.sig_none), color = if (drawn != null) c.brandGreenText else c.textMuted, fontSize = 12.sp)
                }
                FluentButton(word(if (drawn != null) W.sig_change else W.sig_sign), { signing = true }, kind = ButtonKind.SUBTLE, icon = "ic_edit")
                if (drawn != null) IconAction("ic_delete", word(W.sig_remove), { sig = null; sigChanged = true }, tint = c.owedToMe)
            }
        }
        Rule()
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) FluentButton(word(W.delete), onDelete, kind = ButtonKind.DANGER)
            // The saved entry on the thermal printer (Phase 9e); a new one is printed once saved.
            if (onPrint != null) FluentButton(word(W.print_receipt), onPrint, icon = "ic_print")
            Spacer(Modifier.weight(1f))
            FluentButton(word(W.cancel), onCancel)
            FillButton(word(W.save), if (isGiven) c.gaveFill else Look.accent, enabled = amount != null) { trySave() }
        }
    }
    overReturn?.let { (d, text) ->
        AskDialog(word(W.pos_return_title), text, word(W.proceed_anyway), onConfirm = { overReturn = null; onSave(d) }, onDismiss = { overReturn = null }, height = 320)
    }
    if (signing) SignatureDialog(
        title = word(W.sig_title),
        subtitle = Format.name(partyName) + (amount?.let { "  ·  " + word(if (isGiven) W.you_gave else W.you_got) + " " + Format.money(it) } ?: ""),
        onDone = { picture -> sig = picture; sigChanged = true; signing = false },
        onCancel = { signing = false }
    )
    if (choosingDue) DueDialog(partyName, due, onPick = { day -> due = day; dueTouched = true; choosingDue = false }, onDismiss = { choosingDue = false })
    if (picking) {
        // The day changes; the time of day the entry was written stays.
        DayPicker(at, onPick = { day ->
            val keep = Calendar.getInstance().apply { timeInMillis = at }
            // Never ahead of now (the phone's rule): today at a later hour than this one is now.
            at = Calendar.getInstance().apply {
                timeInMillis = day
                set(Calendar.HOUR_OF_DAY, keep.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, keep.get(Calendar.MINUTE))
            }.timeInMillis.coerceAtMost(System.currentTimeMillis())
            picking = false
        }, onDismiss = { picking = false }, latest = System.currentTimeMillis())
    }
    if (pickingTime) TimeOfDayDialog(at, onPick = { picked -> at = picked; pickingTime = false }, onDismiss = { pickingTime = false })
}

/**
 * The time of day an entry happened (the phone's DateTimeField): the day stays, the clock as the shop writes it
 * (ShopCountry.clock24); a time later than now on today is brought back to now — an entry is never ahead of it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TimeOfDayDialog(at: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val cal = remember(at) { Calendar.getInstance().apply { timeInMillis = at } }
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY), initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = com.innovation313.roshankhata.data.ShopCountry.current.clock24
    )
    AppDialog(onCloseRequest = onDismiss, title = word(W.pick_time), state = rememberDialogState(width = fitW(380.dp), height = fitH(560.dp))) {
        DialogFrame(word(W.pick_time), onClose = onDismiss, footer = {
            FluentButton(word(W.cancel), onDismiss, kind = ButtonKind.STANDARD)
            FluentButton(word(W.ok), {
                val picked = Calendar.getInstance().apply {
                    timeInMillis = at
                    set(Calendar.HOUR_OF_DAY, state.hour); set(Calendar.MINUTE, state.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onPick(picked.coerceAtMost(System.currentTimeMillis()))
            }, kind = ButtonKind.ACCENT)
        }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { androidx.compose.material3.TimePicker(state) }
        }
    }
}

/**
 * The customer signs on a pad (the phone's SignatureSheet, on the computer: the mouse or a touch screen), dark
 * ink on white; Clear starts again; Done keeps the strokes as a picture of the pad's size. Nothing drawn says so.
 */
@Composable
fun SignatureDialog(title: String, subtitle: String?, onDone: (java.awt.image.BufferedImage) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    val strokes = remember { androidx.compose.runtime.mutableStateListOf<List<androidx.compose.ui.geometry.Offset>>() }
    var current by remember { mutableStateOf<List<androidx.compose.ui.geometry.Offset>>(emptyList()) }
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var empty by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    AppDialog(onCloseRequest = onCancel, title = title, state = rememberDialogState(width = fitW(640.dp), height = fitH(460.dp))) {
        DialogFrame(title, subtitle = subtitle, onClose = onCancel, scroll = false, footerStart = {
            FluentButton(word(W.sig_clear), { strokes.clear(); current = emptyList() }, kind = ButtonKind.SUBTLE)
        }, footer = {
            FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
            FluentButton(word(W.sig_done), {
                if (strokes.isEmpty()) { empty = true; return@FluentButton }
                onDone(EntrySignatures.picture(strokes.map { s -> s.map { it.x to it.y } }, size.width, size.height, ink = 2.6f * density))
            }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
        }) {
            Text(word(W.sig_hint_pc), color = c.textMuted, fontSize = 12.sp)
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(Look.ctl).background(Color.White).border(1.dp, Look.edge, Look.ctl)
                    .onSizeChanged { size = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { o -> current = listOf(o); empty = false },
                            onDrag = { change, _ -> current = current + change.position },
                            onDragEnd = { if (current.isNotEmpty()) strokes.add(current); current = emptyList() },
                            onDragCancel = { current = emptyList() }
                        )
                    }
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val ink = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.6f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    (strokes + listOf(current)).forEach { s ->
                        if (s.size == 1) drawCircle(Color(0xFF1B1B1B), radius = 1.3f * density, center = s[0])
                        else if (s.size > 1) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(s[0].x, s[0].y)
                                for (k in 1 until s.size) lineTo(s[k].x, s[k].y)
                            }
                            drawPath(path, Color(0xFF1B1B1B), style = ink)
                        }
                    }
                }
                // Where to sign, faint, until the first stroke.
                if (strokes.isEmpty() && current.isEmpty()) Text(word(W.sig_title), Modifier.align(Alignment.Center), color = Color(0xFFB8B8B8), fontSize = 18.sp)
            }
            if (empty) Text(word(W.sig_empty), color = c.owedToMe, fontSize = 13.sp)
        }
    }
}

/**
 * "When will he pay?" (the phone's chooseDueDate): 7, 15 or 30 days from
 * today, the next two harvest ends, any other day, or taking the promise
 * back. A day is kept as its last minute. No late fee or markup goes with it.
 */
@Composable
internal fun DueDialog(partyName: String, current: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val c = LocalRoshanColors.current
    var other by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    fun endOfDay(base: Long, days: Int = 0) = Calendar.getInstance().apply {
        timeInMillis = base; add(Calendar.DAY_OF_YEAR, days)
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val quick = listOf(7, 15, 30).map { d -> word(W.entry_due_in_days, d.toString(), Format.dateOnly(endOfDay(now, d))) to endOfDay(now, d) }
    val y = Calendar.getInstance().get(Calendar.YEAR)
    // Harvest days only for a shop that sells into crop seasons (TradeFeatures.seasons).
    val harvests = if (!LocalTrade.current.seasons) emptyList<Pair<String, Long>>() else listOf(
        SeasonBook.Season(SeasonBook.Crop.RABI, y - 1), SeasonBook.Season(SeasonBook.Crop.KHARIF, y),
        SeasonBook.Season(SeasonBook.Crop.RABI, y), SeasonBook.Season(SeasonBook.Crop.KHARIF, y + 1)
    ).map { it to SeasonBook.harvestEnd(it) }.filter { it.second > now }.sortedBy { it.second }.take(2)
        .map { (s, at) -> word(W.harvest_promise_option, seasonName(s), Format.dateOnly(at)) to at }
    // In the app's frame (Marhala 4, the audit: Material's dialog): each day a row, the chosen one marked.
    val title = word(W.harvest_promise_title, Format.name(partyName))
    val options = quick + harvests
    AppDialog(onCloseRequest = onDismiss, title = title, state = rememberDialogState(width = fitW(460.dp), height = fitH((230 + 49 * options.size + if (current != null) 40 else 0).dp))) {
        DialogFrame(title, onClose = onDismiss, footerStart = {
            if (current != null) FluentButton(word(W.harvest_promise_remove), { onPick(null) }, kind = ButtonKind.DANGER)
        }, footer = {
            FluentButton(word(W.cancel), onDismiss, kind = ButtonKind.STANDARD)
            FluentButton(word(W.harvest_promise_other_date), { other = true }, icon = "ic_calendar")
        }) {
            Column {
                options.forEachIndexed { i, (label, at) ->
                    val on = current != null && kotlin.math.abs(current - at) < 60_000L
                    Row(
                        Modifier.fillMaxWidth().clip(Look.ctl).background(if (on) Look.accentTint else Color.Transparent).press(Look.ctl) { onPick(at) }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, Modifier.weight(1f), color = c.ink, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        if (on) Glyph("ic_check_plain", Look.accent, 16)
                    }
                    if (i < options.lastIndex) Rule()
                }
            }
        }
    }
    if (other) DayPicker(current ?: now, onPick = { day -> other = false; onPick(endOfDay(day)) }, onDismiss = { other = false })
}
