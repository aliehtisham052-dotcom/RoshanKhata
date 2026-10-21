package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AdjustReason
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Product
import com.innovation313.roshankhata.data.Stock
import com.innovation313.roshankhata.data.Valuation
import com.innovation313.roshankhata.screens.CountDraft
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.StockCountForm
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stock on hand (POS Phase 6): every product with what the shelf holds and
 * what it is worth (Valuation — cost price, else the last purchase rate;
 * nothing guessed), the low ones first; Count stock (the shared form, only
 * the differences written) and Adjust stock (one correction with its
 * reason); the corrections under it. The batch-level register for
 * inspectors stays on its own page.
 */
@Composable
fun InventoryPane(
    dao: KhataDao, words: Words, reload: Int,
    /** The owner's other shops (21 Oct 2026): stock can be sent to one of them. */
    businesses: Businesses? = null, activeBusiness: Long = 1L,
    onChanged: () -> Unit
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var valuation by remember { mutableStateOf<Valuation?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(reload, tick) { valuation = withContext(Dispatchers.IO) { runCatching { Valuation.compute(dao) }.getOrNull() } }
    val corrections by remember(tick) { dao.observeAdjustments(50) }.collectAsState(emptyList())
    var counting by remember { mutableStateOf<CountDraft?>(null) }
    var adjusting by remember { mutableStateOf(false) }
    var told by remember { mutableStateOf<String?>(null) }
    var lowOnly by remember { mutableStateOf(false) }
    val may = Access.can(Access.Permission.STOCK_ADJUST)

    counting?.let { draft ->
        DialogWindow(onCloseRequest = { counting = null }, onPreviewKeyEvent = escCloses { counting = null }, title = word(W.stock_count_title), state = rememberDialogState(width = fitW(640.dp), height = fitH(760.dp))) {
            DialogFrame(word(W.stock_count_title), onClose = { counting = null }, scroll = false, footer = {}) {
                StockCountForm(draft, onCancel = { counting = null }, onSave = {
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { runCatching { dao.recordCount(draft.counts(), draft.note.trim().ifBlank { null }) }.getOrDefault(0) }
                        told = words.text(W.stock_count_saved, n)
                        counting = null; tick++; onChanged()
                    }
                }, modifier = Modifier.fillMaxSize())
            }
        }
    }
    if (adjusting) AdjustDialog(dao, words, onClose = { adjusting = false }) { told = it; tick++; onChanged() }
    if (sending && businesses != null) TransferDialog(dao, words, businesses, activeBusiness, onClose = { sending = false }) { told = it; tick++; onChanged() }

    FluentPage(word(W.inventory_title), told ?: word(W.products_subtitle), scroll = false, actions = {
        FluentButton(word(W.stock_count_title), {
            if (!may) { told = words.text(W.access_denied); return@FluentButton }
            scope.launch { counting = CountDraft(withContext(Dispatchers.IO) { Stock.of(dao) }) }
        }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
        FluentButton(word(W.stock_adjust), { if (!may) told = words.text(W.access_denied) else adjusting = true }, kind = ButtonKind.STANDARD, icon = "ic_edit")
        if (businesses != null) FluentButton(word(W.transfer_title), {
            when {
                !may -> told = words.text(W.access_denied)
                businesses.list().none { it.id != activeBusiness } -> told = words.text(W.transfer_none)
                else -> sending = true
            }
        }, kind = ButtonKind.STANDARD)
    }) {
        val v = valuation
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FigureCard(word(W.stock_valuation), v?.let { Format.money(it.total) } ?: "…", c.ink, Modifier.weight(1f),
                under = v?.let { words.text(W.stock_valuation_known, it.known.toString(), it.rows.size.toString()) })
            FigureCard(word(W.stock_low_title), v?.low?.size?.toString() ?: "…", c.owedToMe, Modifier.weight(1f))
            FigureCard(word(W.tile_products), v?.rows?.size?.toString() ?: "…", c.inkSoft, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentPill(word(W.filter_type_all), !lowOnly) { lowOnly = false }
            FluentPill(word(W.stock_low_title), lowOnly) { lowOnly = true }
        }
        ListCard(Modifier.weight(1f)) {
            WeightedHead(listOf(word(W.pos_col_item) to 5f, word(W.inspector_col_left) to 2f, word(W.product_cost_hint) to 2f, word(W.stock_valuation) to 2f), endAligned = setOf(1, 2, 3))
            val rows = (v?.rows ?: emptyList()).let { r -> if (lowOnly) r.filter { it.isLow } else r }.sortedWith(compareBy({ !it.isLow }, { it.name.lowercase() }))
            if (rows.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(word(W.no_products), color = c.textMuted) }
            else LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(rows, key = { it.productId }) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.weight(5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(r.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.isLow) Tag(word(W.stock_low_title), c.owedToMe, Look.getTint)
                        }
                        Text(r.onHand?.let { Calc.trim(it) + (r.unit?.let { u -> " $u" } ?: "") } ?: "?", Modifier.weight(2f), color = if (r.isLow) c.owedToMe else c.ink, fontSize = 14.sp, textAlign = TextAlign.End, maxLines = 1)
                        Text(r.cost?.let { Format.money(it) } ?: "–", Modifier.weight(2f), color = c.inkSoft, fontSize = 13.sp, textAlign = TextAlign.End, maxLines = 1)
                        Text(r.value?.let { Format.money(it) } ?: "–", Modifier.weight(2f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, maxLines = 1)
                    }
                    Rule()
                }
            }
        }
        if (corrections.isNotEmpty()) ListCard(Modifier.fillMaxWidth()) {
            ListHead(word(W.stock_adjustments))
            val names = (v?.rows ?: emptyList()).associate { it.productId to it.name }
            corrections.take(6).forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(Format.dateTime(a.timestamp), Modifier.width(130.dp), color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    Text(names[a.productId] ?: a.productId.toString(), Modifier.weight(1f), color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(reasonWord(a.reason) + (a.note?.let { " · $it" } ?: ""), Modifier.weight(1f), color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (a.quantity > 0) "+" else "") + Calc.trim(a.quantity) + (a.unit?.let { " $it" } ?: ""), color = if (a.quantity < 0) c.owedToMe else c.iOwe, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                if (i < minOf(corrections.size, 6) - 1) Rule()
            }
        }
    }
}

@Composable
fun reasonWord(r: String): String = word(
    when (r) {
        AdjustReason.COUNT -> W.adjust_reason_count
        AdjustReason.DAMAGE -> W.adjust_reason_damage
        AdjustReason.EXPIRED -> W.adjust_reason_expired
        AdjustReason.THEFT -> W.adjust_reason_theft
        AdjustReason.TRANSFER -> W.adjust_reason_transfer
        else -> W.adjust_reason_other
    }
)

/** One correction: the product, the signed quantity, the reason, a note. */
@Composable
private fun AdjustDialog(dao: KhataDao, words: Words, onClose: () -> Unit, done: (String) -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    LaunchedEffect(Unit) { products = withContext(Dispatchers.IO) { dao.productsOnce() } }
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Product?>(null) }
    var qty by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(AdjustReason.DAMAGE) }
    var note by remember { mutableStateOf("") }
    val title = word(W.stock_adjust)
    // Signed (21 Oct 2026): "+ in, − out", as the box says. Read as an amount, "−5" for damaged
    // goods came out as +5 and the shelf went UP. evalPad keeps the sign; zero is nothing to adjust.
    val amount = Calc.evalPad(qty)?.takeIf { it != 0.0 }
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = fitW(520.dp), height = fitH(560.dp))) {
        DialogFrame(title, onClose = onClose, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.save), {
                val p = chosen ?: return@FluentButton
                val a = amount ?: return@FluentButton
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { dao.adjustStock(p.id, a, reason, note.trim().ifBlank { null }) } }
                    onClose(); done(p.name + " " + (if (a > 0) "+" else "") + Calc.trim(a))
                }
            }, kind = ButtonKind.ACCENT, enabled = chosen != null && amount != null && amount != 0.0)
        }) {
            val p = chosen
            if (p == null) {
                FluentField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = word(W.pos_search_hint), leadingIcon = "ic_search")
                Column(Modifier.fillMaxWidth()) {
                    com.innovation313.roshankhata.data.CounterDraft.search(products, query, 8).forEachIndexed { i, pr ->
                        Row(Modifier.fillMaxWidth().press { chosen = pr }.padding(8.dp)) { Text(pr.name, color = c.ink, fontSize = 14.sp) }
                        Rule()
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    FluentButton(word(W.edit), { chosen = null }, kind = ButtonKind.SUBTLE)
                }
                FluentField(qty, { qty = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = word(W.adjust_qty_hint), big = true, calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
                Text(word(W.adjust_reason), Modifier.padding(top = 8.dp), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AdjustReason.DAMAGE, AdjustReason.EXPIRED, AdjustReason.THEFT, AdjustReason.OTHER).forEach { r ->
                        FluentPill(reasonWord(r), reason == r) { reason = r }
                    }
                }
                FluentField(note, { note = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = word(W.note_optional))
            }
        }
    }
}

/**
 * Goods to another of the owner's shops (21 Oct 2026): the shop, then the
 * products and how many of each; Send writes both books ([StockTransfer]),
 * the other one opened for the moment and closed again.
 */
@Composable
private fun TransferDialog(dao: KhataDao, words: Words, businesses: Businesses, activeBusiness: Long, onClose: () -> Unit, done: (String) -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val others = remember { businesses.list().filter { it.id != activeBusiness } }
    fun nameOf(b: Businesses.Business) = b.name?.takeIf { it.isNotBlank() } ?: words.text(W.app_name) + " " + b.id
    var to by remember { mutableStateOf(others.firstOrNull()) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var onHand by remember { mutableStateOf<Map<Long, Double?>>(emptyMap()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            products = dao.productsOnce()
            onHand = runCatching { com.innovation313.roshankhata.data.Counter.onHand(dao) }.getOrDefault(emptyMap())
        }
    }
    var query by remember { mutableStateOf("") }
    val lines = remember { androidx.compose.runtime.mutableStateListOf<Pair<Product, androidx.compose.runtime.MutableState<String>>>() }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val ready = to != null && lines.isNotEmpty() && lines.all { (Calc.evalAmount(it.second.value) ?: 0.0) > 0.0 } && !busy
    val title = word(W.transfer_title)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = fitW(620.dp), height = fitH(680.dp))) {
        DialogFrame(title, onClose = onClose, footerStart = { problem?.let { Text(it, color = c.owedToMe, fontSize = 12.sp) } }, footer = {
            FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
            FluentButton(word(W.transfer_title), {
                val target = to ?: return@FluentButton
                busy = true
                scope.launch {
                    val shopHere = businesses.list().firstOrNull { it.id == activeBusiness }?.let { nameOf(it) } ?: words.text(W.app_name)
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val other = com.innovation313.roshankhata.data.DesktopDatabase.open(businesses.dbFile(target.id))
                            try {
                                com.innovation313.roshankhata.data.StockTransfer.send(
                                    dao, other.khataDao(),
                                    lines.map { com.innovation313.roshankhata.data.StockTransfer.Line(it.first, Calc.evalAmount(it.second.value) ?: 0.0) },
                                    fromShop = shopHere, toShop = nameOf(target)
                                )
                            } finally { other.close() }
                        }
                    }
                    busy = false
                    result.onSuccess { number -> onClose(); done(words.text(W.transfer_done, number, nameOf(target))) }
                        .onFailure { problem = it.message ?: it.toString() }
                }
            }, kind = ButtonKind.ACCENT, enabled = ready)
        }) {
            Text(word(W.transfer_help), color = c.textMuted, fontSize = 12.sp)
            Text(word(W.transfer_to), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                others.forEach { b -> FluentPill(nameOf(b), to?.id == b.id) { to = b } }
            }
            FluentField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = word(W.pos_search_hint), leadingIcon = "ic_search")
            if (query.isNotBlank()) Column(Modifier.fillMaxWidth()) {
                com.innovation313.roshankhata.data.CounterDraft.search(products, query, 6).forEach { pr ->
                    Row(Modifier.fillMaxWidth().press {
                        if (lines.none { it.first.id == pr.id }) lines += pr to mutableStateOf("1")
                        query = ""
                    }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(pr.name, Modifier.weight(1f), color = c.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        onHand[pr.id]?.let { Text(Calc.trim(it) + (pr.defaultUnit?.let { u -> " $u" } ?: ""), color = c.textMuted, fontSize = 12.sp) }
                    }
                    Rule()
                }
            }
            lines.toList().forEach { (p, qty) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        onHand[p.id]?.let { Text(word(W.inspector_col_left) + ": " + Calc.trim(it) + (p.defaultUnit?.let { u -> " $u" } ?: ""), color = c.textMuted, fontSize = 12.sp) }
                    }
                    FluentField(qty.value, { qty.value = it }, Modifier.width(110.dp), placeholder = word(W.pos_col_qty))
                    IconAction("ic_close", word(W.delete), { lines.removeAll { it.first.id == p.id } })
                }
            }
        }
    }
}
