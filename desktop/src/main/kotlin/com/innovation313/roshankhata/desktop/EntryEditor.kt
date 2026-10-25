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
import com.innovation313.roshankhata.data.Recovery
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.util.Calendar

/**
 * What the entry form hands back: the entry (unsaved), its one goods line,
 * whether the cash came with a cash-rate sale ([paidNow]: the matching "I got"
 * is written with it, paired), and the payment day picked on the form
 * ([due], only when [dueTouched]; null then means "remove the promise").
 */
class EntryDraft(
    val entry: LedgerEntry,
    val item: EntryItem?,
    val paidNow: Boolean = false,
    val due: Long? = null,
    val dueTouched: Boolean = false
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

/**
 * A khata entry on the computer, with what the phone's entry dialog has
 * (KMP Phase 8a): the amount (sums allowed), the date it happened (today
 * by default; an entry written the next morning keeps its real day), the
 * goods that moved with it (item, quantity, unit, rate: quantity x rate is
 * offered as the amount), a note, Qarz-e-Hasna for an interest-free loan,
 * and the chance of recovery (doubtful debts are what Zakat may leave out).
 * Editing starts from the entry and its goods line.
 */
@Composable
fun EntryEditor(
    partyId: Long,
    partyName: String,
    isGiven: Boolean,
    existing: LedgerEntry?,
    existingItem: EntryItem?,
    onSave: (EntryDraft) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onPrint: (() -> Unit)? = null,
    /** A customer (not a supplier): sales get a rate type, "paid now" and batches. */
    isCustomer: Boolean = true,
    /** The batches a sale of this item may be drawn from (bill lines with stock), soonest-expiring first. */
    batchesFor: (suspend (String) -> List<BatchOption>)? = null,
    /** The customer's promised payment day (Party.harvestPromise), shown on the due button. */
    promise: Long? = null
) {
    val c = LocalRoshanColors.current
    var amountText by remember { mutableStateOf(existing?.amount?.let { Calc.trim(it) }.orEmpty()) }
    var at by remember { mutableStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    var item by remember { mutableStateOf(existingItem?.itemName.orEmpty()) }
    var qty by remember { mutableStateOf(existingItem?.quantity?.let { Calc.trim(it) }.orEmpty()) }
    var unit by remember { mutableStateOf(com.innovation313.roshankhata.data.UnitWords.label(existingItem?.unit)) }
    var rate by remember { mutableStateOf(existingItem?.rate?.let { Calc.trim(it) }.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var qarz by remember { mutableStateOf(existing?.isQarzeHasna ?: false) }
    var doubtful by remember { mutableStateOf(existing?.recovery == Recovery.DOUBTFUL) }
    // A new "I got" starts as Cash, as on the phone (21 Oct 2026); an edited entry keeps its own.
    var method by remember { mutableStateOf(if (existing == null) PaymentMethod.CASH else existing.paymentMethod) }
    var picking by remember { mutableStateOf(false) }
    val isSale = isGiven && isCustomer
    var cashRate by remember { mutableStateOf(existing?.rateType == RateType.CASH) }
    var paidNow by remember { mutableStateOf(false) }
    var due by remember { mutableStateOf(promise) }
    var dueTouched by remember { mutableStateOf(false) }
    var choosingDue by remember { mutableStateOf(false) }
    // Batches: looked up when the item name settles, as on the phone (a sale only).
    var batches by remember { mutableStateOf<List<BatchOption>>(emptyList()) }
    var batchId by remember { mutableStateOf(existingItem?.billItemId) }
    var batchMenu by remember { mutableStateOf(false) }
    LaunchedEffect(item) {
        val name = item.trim()
        if (!isSale || batchesFor == null || name.isEmpty()) { batches = emptyList(); return@LaunchedEffect }
        delay(350)
        batches = runCatching { batchesFor(name) }.getOrDefault(emptyList())
        if (batches.none { it.id == batchId } && batchId != existingItem?.billItemId) batchId = null
    }
    val amount = Calc.evalAmount(amountText)
    val q = Calc.evalAmount(qty)
    val r = Calc.evalAmount(rate)
    val goodsTotal = if (q != null && r != null) q * r else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The head (1.10.0, the New entry mockup): which way, the party under it, the amount big with
        // its figure in words-of-money under it, the day beside it as a Fluent date box.
        Text(word(if (isGiven) W.you_gave else W.you_got), color = if (isGiven) c.owedToMe else Look.accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(Format.name(partyName), color = c.inkSoft, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            FluentField(
                amountText, { amountText = it }, Modifier.weight(1.4f), label = word(W.amount), prefix = com.innovation313.roshankhata.data.Currency.symbol, big = true,
                help = amount?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(word(W.date_label), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(Look.ctl).background(Look.control).border(1.dp, Look.edge, Look.ctl)
                        .press(Look.ctl) { picking = true }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(Format.dateOnly(at), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, maxLines = 1)
                    Glyph("ic_calendar", c.inkSoft, 16)
                }
            }
        }

        Text(word(W.goods_section), color = c.ink, fontWeight = FontWeight.Bold)
        // The shop's own words (TradeVocab): a dairy's form says milk and litre, not urea and bag.
        val trade = LocalTrade.current
        Text(trade.goodsHelp(), color = c.textMuted, fontSize = 12.sp)
        FluentField(item, { item = it }, Modifier.fillMaxWidth(), placeholder = trade.itemHint())
        if (item.isBlank()) SuggestionRow(trade.starters(), item) { item = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentField(qty, { qty = it }, Modifier.weight(1f), placeholder = word(W.quantity_hint), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
            FluentField(unit, { unit = it }, Modifier.weight(1f), placeholder = trade.unitHint())
            FluentField(rate, { rate = it }, Modifier.weight(1f), placeholder = word(W.entry_rate_hint), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
        }
        SuggestionRow(trade.units(), unit) { unit = it }
        // Quantity x rate is offered, never forced: a discount is the owner's to write.
        if (goodsTotal != null && goodsTotal > 0 && goodsTotal != amount) {
            TextButton({ amountText = Calc.trim(goodsTotal) }) {
                Text(word(W.amount) + ": " + Format.money(goodsTotal), color = c.brandGreenText)
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
        if (isSale) {
            // Fixed at the sale and never recalculated (LedgerEntry.rateType).
            TwoWay(word(W.rate_type_credit), word(W.rate_type_cash), !cashRate) { credit -> cashRate = !credit; if (credit) paidNow = false }
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

        if (!isGiven) {
            // How it was paid: null unless chosen, as on the phone. The day's cash count
            // (Cashbook, Close the day) counts only khata money marked Cash.
            Text(word(W.payment_method_label), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    PaymentMethod.CASH to W.payment_method_cash, PaymentMethod.BANK to W.payment_method_bank,
                    PaymentMethod.CHEQUE to W.payment_method_cheque, PaymentMethod.ONLINE to W.payment_method_online
                ).forEach { (m, label) ->
                    FluentPill(word(label), m == method) { method = if (m == method) null else m }
                }
            }
        }
        if (isGiven) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(qarz, { qarz = it })
                Text(word(W.qarze_hasna), color = c.ink, fontSize = 14.sp)
            }
            Text(word(W.qarze_hasna_help), color = c.textMuted, fontSize = 12.sp)
            Text(word(W.recovery_label), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            TwoWay(word(W.recovery_certain), word(W.recovery_doubtful), !doubtful) { certain -> doubtful = !certain }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) FluentButton(word(W.delete), onDelete, kind = ButtonKind.DANGER)
            // The saved entry on the thermal printer (Phase 9e); a new one is printed once saved.
            if (onPrint != null) FluentButton(word(W.print_receipt), onPrint, icon = "ic_print")
            Spacer(Modifier.weight(1f))
            FluentButton(word(W.cancel), onCancel)
            FillButton(word(W.save), if (isGiven) c.gaveFill else Look.accent, enabled = amount != null) {
                val a = amount ?: return@FillButton
                val base = existing ?: LedgerEntry(partyId = partyId, amount = a, isGiven = isGiven, entryNumber = "")
                val goods = item.trim().takeIf { it.isNotEmpty() }?.let {
                    EntryItem(
                        id = 0, entryId = existing?.id ?: 0, itemName = it, quantity = q,
                        unit = com.innovation313.roshankhata.data.UnitWords.canonical(unit), rate = r, productId = existingItem?.takeIf { e -> e.itemName == it }?.productId,
                        billItemId = if (isSale) batchId?.takeIf { id -> batches.any { b -> b.id == id } || id == existingItem?.billItemId } else null
                    )
                }
                // Ticked "paid now" (a new cash sale): its "I got" is written even when no goods line has a rate.
                val tickedPaid = existing == null && isSale && cashRate && paidNow
                val rateType = entryRateType(isSale, goods?.rate != null, cashRate, tickedPaid, base.rateType)
                onSave(
                    EntryDraft(
                        base.copy(
                            amount = a,
                            timestamp = at,
                            note = note.trim().ifEmpty { null },
                            isQarzeHasna = isGiven && qarz,
                            recovery = if (isGiven && doubtful) Recovery.DOUBTFUL else Recovery.CERTAIN,
                            // Goods coming back are not money in any way (the phone's rule): no method.
                            paymentMethod = if (isGiven) base.paymentMethod else if (goods != null) null else method,
                            rateType = rateType
                        ),
                        goods,
                        paidNow = tickedPaid,
                        due = due,
                        dueTouched = dueTouched
                    )
                )
            }
        }
    }
    if (choosingDue) DueDialog(partyName, due, onPick = { at -> due = at; dueTouched = true; choosingDue = false }, onDismiss = { choosingDue = false })
    if (picking) {
        // The day changes; the time of day the entry was written stays.
        DayPicker(at, onPick = { day ->
            val keep = Calendar.getInstance().apply { timeInMillis = at }
            at = Calendar.getInstance().apply {
                timeInMillis = day
                set(Calendar.HOUR_OF_DAY, keep.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, keep.get(Calendar.MINUTE))
            }.timeInMillis
            picking = false
        }, onDismiss = { picking = false })
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word(W.harvest_promise_title, Format.name(partyName))) },
        text = {
            Column {
                (quick + harvests).forEach { (label, at) ->
                    TextButton({ onPick(at) }) { Text(label, color = c.ink) }
                }
                FluentButton(word(W.harvest_promise_other_date), { other = true }, kind = ButtonKind.SUBTLE)
                if (current != null) FluentButton(word(W.harvest_promise_remove), { onPick(null) }, kind = ButtonKind.DANGER)
            }
        },
        confirmButton = {},
        dismissButton = { FluentButton(word(W.cancel), onDismiss, kind = ButtonKind.STANDARD) }
    )
    if (other) DayPicker(current ?: now, onPick = { day -> other = false; onPick(endOfDay(day)) }, onDismiss = { other = false })
}
