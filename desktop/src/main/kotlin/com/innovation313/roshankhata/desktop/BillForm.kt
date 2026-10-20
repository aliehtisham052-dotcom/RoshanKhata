package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(word(W.add_bill), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        val chosen = parties.firstOrNull { it.id == supplierId }
        if (chosen != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.name(chosen.name), Modifier.weight(1f), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton({ supplierId = null }) { Text(word(W.edit), color = c.brandGreenText) }
            }
            if (chosen.isCustomer) Text(word(W.bill_to_customer_warn, chosen.name), color = c.owedToMe, fontSize = 12.sp)
        } else {
            OutlinedTextField(
                search, { search = it; problem = null }, label = { Text(word(W.bill_supplier_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            val q = search.trim().lowercase()
            // Suppliers first: a bill is almost always from one.
            val shown = parties.filter { NameSearch.matches(it.name, it.phone, q) }.sortedBy { it.isCustomer }.take(50)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp).background(c.pageBg, RoundedCornerShape(10.dp))) {
                items(shown, key = { it.id }) { p ->
                    Text(
                        Format.name(p.name) + (p.phone?.let { "  ·  " + Format.ltr(it) } ?: ""),
                        Modifier.fillMaxWidth().clickable { supplierId = p.id; problem = null }.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = c.ink, fontSize = 14.sp
                    )
                }
            }
            val typed = search.trim()
            if (typed.isNotEmpty() && parties.none { it.name.trim().equals(typed, ignoreCase = true) }) {
                Text(word(W.bill_new_supplier_msg, typed), color = c.textMuted, fontSize = 12.sp)
            }
        }
        OutlinedTextField(number, { number = it }, label = { Text(word(W.bill_number_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(totalText, { totalText = it; problem = null }, label = { Text(word(W.bill_total_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(total?.let { Format.money(it) } ?: "", color = c.textMuted, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ picking = 0 }) { Text(word(W.pick_bill_date), color = c.brandGreenText) }
            Text(Format.dateOnly(billDate), color = c.ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            TextButton({ picking = 1 }) { Text(word(W.bill_due_date), color = c.brandGreenText) }
            Text(dueDate?.let { Format.dateOnly(it) } ?: "", color = c.ink, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(paidCash, { paidCash = it })
            Text(word(W.bill_paid_cash), color = c.ink, fontSize = 14.sp)
        }
        Text(word(W.bill_paid_cash_help), color = c.textMuted, fontSize = 12.sp)
        // What Save will do to the khata, said before it is done.
        val who = chosen?.name ?: search.trim()
        if (!paidCash && total != null && who.isNotEmpty()) {
            Text(word(W.bill_credit_note, Format.money(total), who), color = c.owedToMe, fontSize = 13.sp)
        }
        OutlinedTextField(note, { note = it }, label = { Text(word(W.note_optional)) }, modifier = Modifier.fillMaxWidth())

        Text(word(W.bill_items_title), Modifier.padding(top = 6.dp), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        items.forEachIndexed { i, it ->
            Row(Modifier.fillMaxWidth().background(c.pageBg, RoundedCornerShape(8.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                TextButton({ items = items.filterIndexed { j, _ -> j != i } }) { Text(word(W.delete), color = c.owedToMe) }
            }
        }
        ItemForm { items = items + it }

        problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TextButton(onCancel) { Text(word(W.cancel)) }
            FillButton(word(W.save), c.brandGreen) {
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
        }
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
    var unit by remember { mutableStateOf(existing?.unit.orEmpty()) }
    var rate by remember { mutableStateOf(existing?.rate?.let { Calc.trim(it) }.orEmpty()) }
    var batch by remember { mutableStateOf(existing?.batchNumber.orEmpty()) }
    var expiry by remember { mutableStateOf(existing?.expiryDate) }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val needProduct = word(W.enter_product)
    val needQty = word(W.enter_valid_amount)
    Column(Modifier.fillMaxWidth().background(c.pageBg, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(name, { name = it; problem = null }, label = { Text(word(W.product_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(qty, { qty = it; problem = null }, label = { Text(word(W.qty_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(unit, { unit = it }, label = { Text(LocalTrade.current.unitHint()) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(rate, { rate = it }, label = { Text(word(W.rate_hint)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(batch, { batch = it }, label = { Text(word(W.batch_number_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(word(W.batch_why), color = c.textMuted, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ picking = true }) { Text(word(W.pick_expiry), color = c.brandGreenText) }
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
                        expiryDate = expiry, quantity = q, unit = unit.trim().ifEmpty { null },
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
