package com.innovation313.roshankhata.desktop

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
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.Recovery
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.util.Calendar

/** What the entry form hands back: the entry (unsaved) and its one goods line, if any. */
class EntryDraft(val entry: LedgerEntry, val item: EntryItem?)

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
    onPrint: (() -> Unit)? = null
) {
    val c = LocalRoshanColors.current
    var amountText by remember { mutableStateOf(existing?.amount?.let { Calc.trim(it) }.orEmpty()) }
    var at by remember { mutableStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    var item by remember { mutableStateOf(existingItem?.itemName.orEmpty()) }
    var qty by remember { mutableStateOf(existingItem?.quantity?.let { Calc.trim(it) }.orEmpty()) }
    var unit by remember { mutableStateOf(existingItem?.unit.orEmpty()) }
    var rate by remember { mutableStateOf(existingItem?.rate?.let { Calc.trim(it) }.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var qarz by remember { mutableStateOf(existing?.isQarzeHasna ?: false) }
    var doubtful by remember { mutableStateOf(existing?.recovery == Recovery.DOUBTFUL) }
    var method by remember { mutableStateOf(existing?.paymentMethod) }
    var picking by remember { mutableStateOf(false) }
    val amount = Calc.evalAmount(amountText)
    val q = Calc.evalAmount(qty)
    val r = Calc.evalAmount(rate)
    val goodsTotal = if (q != null && r != null) q * r else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(if (isGiven) W.you_gave else W.you_got), color = if (isGiven) c.owedToMe else c.iOwe, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(Format.name(partyName), color = c.ink, fontSize = 15.sp)
        OutlinedTextField(amountText, { amountText = it }, label = { Text(word(W.amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(amount?.let { Format.money(it) } ?: "", color = c.textMuted, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ picking = true }) { Text(word(W.entry_date, Format.dateOnly(at)), color = c.brandGreenText) }
        }

        Text(word(W.goods_section), color = c.ink, fontWeight = FontWeight.Bold)
        Text(word(W.goods_help), color = c.textMuted, fontSize = 12.sp)
        OutlinedTextField(item, { item = it }, label = { Text(word(W.item_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(qty, { qty = it }, label = { Text(word(W.quantity_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(unit, { unit = it }, label = { Text(word(W.unit_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(rate, { rate = it }, label = { Text(word(W.entry_rate_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        // Quantity x rate is offered, never forced: a discount is the owner's to write.
        if (goodsTotal != null && goodsTotal > 0 && goodsTotal != amount) {
            TextButton({ amountText = Calc.trim(goodsTotal) }) {
                Text(word(W.amount) + ": " + Format.money(goodsTotal), color = c.brandGreenText)
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text(word(W.note_optional)) }, modifier = Modifier.fillMaxWidth())

        if (!isGiven) {
            // How it was paid: null unless chosen, as on the phone. The day's cash count
            // (Cashbook, Close the day) counts only khata money marked Cash.
            Text(word(W.payment_method_label), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    PaymentMethod.CASH to W.payment_method_cash, PaymentMethod.BANK to W.payment_method_bank,
                    PaymentMethod.CHEQUE to W.payment_method_cheque, PaymentMethod.ONLINE to W.payment_method_online
                ).forEach { (m, label) ->
                    if (m == method) FillButton(word(label), c.brandGreen) { method = null }
                    else TextButton({ method = m }) { Text(word(label), color = c.brandGreenText) }
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
            if (onDelete != null) TextButton(onDelete) { Text(word(W.delete), color = c.owedToMe) }
            // The saved entry on the thermal printer (Phase 9e); a new one is printed once saved.
            if (onPrint != null) TextButton(onPrint) { Text(word(W.print_receipt), color = c.brandGreenText) }
            Spacer(Modifier.weight(1f))
            TextButton(onCancel) { Text(word(W.cancel)) }
            FillButton(word(W.save), if (isGiven) c.gaveFill else c.brandGreen, enabled = amount != null) {
                val a = amount ?: return@FillButton
                val base = existing ?: LedgerEntry(partyId = partyId, amount = a, isGiven = isGiven, entryNumber = "")
                val goods = item.trim().takeIf { it.isNotEmpty() }?.let {
                    EntryItem(
                        id = 0, entryId = existing?.id ?: 0, itemName = it, quantity = q,
                        unit = unit.trim().ifEmpty { null }, rate = r, productId = existingItem?.takeIf { e -> e.itemName == it }?.productId
                    )
                }
                onSave(
                    EntryDraft(
                        base.copy(
                            amount = a,
                            timestamp = at,
                            note = note.trim().ifEmpty { null },
                            isQarzeHasna = isGiven && qarz,
                            recovery = if (isGiven && doubtful) Recovery.DOUBTFUL else Recovery.CERTAIN,
                            paymentMethod = if (isGiven) base.paymentMethod else method
                        ),
                        goods
                    )
                )
            }
        }
    }
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
