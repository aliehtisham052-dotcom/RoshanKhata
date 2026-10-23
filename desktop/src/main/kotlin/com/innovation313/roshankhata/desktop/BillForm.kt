package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.innovation313.roshankhata.data.BillItem
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/** What the bill form hands back: who, the bill's own details, and its lines. */
class NewBill(
    val supplierId: Long?,
    val newSupplierName: String?,
    val billNumber: String?,
    val total: Double,
    val billDate: Long,
    val dueDate: Long?,
    val paidCash: Boolean,
    val note: String?,
    val items: List<BillItem>
)

/**
 * A new supplier bill (KMP Phase 7m), the phone's dialog_add_bill and
 * dialog_add_bill_item on one page: the supplier picked from the book (or a
 * new name, added as a supplier), bill number, total, bill and due dates,
 * paid in cash or on credit (and what that does to the khata, said before
 * Save), a note, and the delivery's lines with batch and expiry.
 */
@Composable
fun BillForm(parties: List<PartyWithBalance>, onSave: (NewBill) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    var supplierId by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var billDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var dueDate by remember { mutableStateOf<Long?>(null) }
    var paidCash by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var picking by remember { mutableStateOf<Int?>(null) } // 0 bill date, 1 due date
    var problem by remember { mutableStateOf<String?>(null) }
    val total = Calc.evalAmount(totalText)
    val needSupplier = word(W.enter_supplier)
    val needAmount = word(W.enter_valid_amount)

    val chosen = parties.firstOrNull { it.id == supplierId }
    fun saveNow() {
        val typed = search.trim()
        when {
            supplierId == null && typed.isEmpty() -> problem = needSupplier
            total == null -> problem = needAmount
            else -> {
                // A typed name that is already in the book is that party, not a twin.
                val existing = supplierId ?: parties.firstOrNull { it.name.trim().equals(typed, ignoreCase = true) }?.id
                onSave(
                    NewBill(
                        supplierId = existing,
                        newSupplierName = if (existing == null) typed else null,
                        billNumber = number.trim().ifEmpty { null },
                        total = total,
                        billDate = billDate,
                        dueDate = dueDate,
                        paidCash = paidCash,
                        note = note.trim().ifEmpty { null },
                        items = items
                    )
                )
            }
        }
    }
    DialogFrame(
        word(W.add_bill), word(W.bill_ledger_note).take(90),
        onClose = onCancel,
        footerStart = { problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) } },
        footer = {
            FluentButton(word(W.cancel), onCancel)
            FluentButton(word(W.save), { saveNow() }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
        }
    ) {
        if (chosen != null) {
            PartyPicker(parties, supplierId, { supplierId = it; problem = null }, word(W.supplier), word(W.bill_supplier_hint), Modifier.fillMaxWidth())
            if (chosen.isCustomer) Text(word(W.bill_to_customer_warn, chosen.name), color = c.owedToMe, fontSize = 12.sp)
        } else {
            // Typed, not only picked: a supplier not yet in the book is made on save (NewBill.newSupplierName).
            FluentField(search, { search = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.supplier), placeholder = word(W.bill_supplier_hint), leadingIcon = "ic_search")
            val q = search.trim().lowercase()
            // Suppliers first: a bill is almost always from one.
            val shown = parties.filter { NameSearch.matches(it.name, it.phone, q) }.sortedBy { it.isCustomer }.take(50)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp).card()) {
                items(shown, key = { it.id }) { p ->
                    Text(
                        Format.name(p.name) + (p.phone?.let { "  ·  " + Format.ltr(it) } ?: ""),
                        Modifier.fillMaxWidth().press { supplierId = p.id; problem = null }.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = c.ink, fontSize = 14.sp
                    )
                    Rule()
                }
            }
            val typed = search.trim()
            if (typed.isNotEmpty() && parties.none { it.name.trim().equals(typed, ignoreCase = true) }) {
                Text(word(W.bill_new_supplier_msg, typed), color = c.textMuted, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            FluentField(totalText, { totalText = it; problem = null }, Modifier.weight(1.3f), label = word(W.bill_total_hint), prefix = com.innovation313.roshankhata.data.Currency.symbol, big = true, help = total?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            FluentField(number, { number = it }, Modifier.weight(1f), label = word(W.bill_number_hint))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DateBox(word(W.pick_bill_date), Format.dateOnly(billDate), word(W.pick_bill_date), { picking = 0 }, Modifier.weight(1f))
            DateBox(word(W.bill_due_date), dueDate?.let { Format.dateOnly(it) }, word(W.bill_due_date), { picking = 1 }, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(paidCash, { paidCash = it })
            Column {
                Text(word(W.bill_paid_cash), color = c.ink, fontSize = 14.sp)
                Text(word(W.bill_paid_cash_help), color = c.textMuted, fontSize = 12.sp)
            }
        }
        // What Save will do to the khata, said before it is done.
        val who = chosen?.name ?: search.trim()
        if (!paidCash && total != null && who.isNotEmpty()) {
            Text(word(W.bill_credit_note, Format.money(total), who), color = c.owedToMe, fontSize = 13.sp)
        }
        FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.note_optional), singleLine = false)

        Text(word(W.bill_items_title), Modifier.padding(top = 6.dp), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        items.forEachIndexed { i, it ->
            Row(Modifier.fillMaxWidth().background(Look.subtle, Look.ctl).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(it.productName, color = c.ink, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            Format.qty(it.quantity, it.unit),
                            it.rate?.let { r -> "@ " + Format.money(r) },
                            it.batchNumber?.let { b -> word(W.batch_label, b) },
                            it.expiryDate?.let { e -> word(W.expiry_date) + ": " + Format.dateOnly(e) }
                        ).joinToString("  ·  "),
                        color = c.inkSoft, fontSize = 12.sp
                    )
                }
                FluentButton(word(W.delete), { items = items.filterIndexed { j, _ -> j != i } }, kind = ButtonKind.DANGER)
            }
        }
        ItemForm { items = items + it }
    }
    picking?.let { which ->
        DayPicker(
            if (which == 0) billDate else dueDate,
            onPick = { if (which == 0) billDate = it else dueDate = it; picking = null },
            onDismiss = { picking = null }
        )
    }
}

/** One line of the delivery: product, quantity, unit, rate, batch and expiry, with why the last two matter. */
@Composable
internal fun ItemForm(existing: BillItem? = null, onAdd: (BillItem) -> Unit) {
    val c = LocalRoshanColors.current
    var name by remember { mutableStateOf(existing?.productName.orEmpty()) }
    var qty by remember { mutableStateOf(existing?.quantity?.let { Calc.trim(it) }.orEmpty()) }
    var unit by remember { mutableStateOf(com.innovation313.roshankhata.data.UnitWords.label(existing?.unit)) }
    var rate by remember { mutableStateOf(existing?.rate?.let { Calc.trim(it) }.orEmpty()) }
    var batch by remember { mutableStateOf(existing?.batchNumber.orEmpty()) }
    var expiry by remember { mutableStateOf(existing?.expiryDate) }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val needProduct = word(W.enter_product)
    val needQty = word(W.enter_valid_amount)
    Column(Modifier.fillMaxWidth().background(Look.subtle, Look.ctl).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Outlined(name, { name = it; problem = null }, label = { Text(word(W.product_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Outlined(qty, { qty = it; problem = null }, label = { Text(word(W.qty_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
            Outlined(unit, { unit = it }, label = { Text(LocalTrade.current.unitHint()) }, singleLine = true, modifier = Modifier.weight(1f))
            Outlined(rate, { rate = it }, label = { Text(word(W.rate_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
        }
        Outlined(batch, { batch = it }, label = { Text(word(W.batch_number_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(word(W.batch_why), color = c.textMuted, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluentButton(word(W.pick_expiry), { picking = true }, kind = ButtonKind.SUBTLE)
            Text(expiry?.let { Format.dateOnly(it) } ?: "", color = c.ink, fontWeight = FontWeight.Bold)
        }
        Text(word(W.expiry_why), color = c.textMuted, fontSize = 11.sp)
        problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        FillButton(word(if (existing == null) W.add_item else W.save), c.brandGreen) {
            val q = Calc.evalAmount(qty)
            when {
                name.isBlank() -> problem = needProduct
                q == null -> problem = needQty
                else -> {
                    val line = BillItem(
                        billId = 0, productName = name.trim(), batchNumber = batch.trim().ifEmpty { null },
                        expiryDate = expiry, quantity = q, unit = com.innovation313.roshankhata.data.UnitWords.canonical(unit),
                        rate = Calc.evalAmount(rate)
                    )
                    onAdd(existing?.let { line.copy(id = it.id, billId = it.billId, note = it.note) } ?: line)
                    name = ""; qty = ""; unit = ""; rate = ""; batch = ""; expiry = null
                }
            }
        }
    }
    if (picking) DayPicker(expiry, onPick = { expiry = it; picking = false }, onDismiss = { picking = false })
}
