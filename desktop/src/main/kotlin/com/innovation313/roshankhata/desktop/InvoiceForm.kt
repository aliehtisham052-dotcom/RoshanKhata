package com.innovation313.roshankhata.desktop

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.innovation313.roshankhata.data.Invoice
import com.innovation313.roshankhata.data.InvoiceItem
import com.innovation313.roshankhata.data.InvoiceMath
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.lineTotal
import com.innovation313.roshankhata.data.totals
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.print.PageFormat
import java.awt.print.Printable

/**
 * A new invoice on the computer (KMP Phase 7n): customer (picked from the
 * book or typed), phone, date, the priced lines, discount, tax, an extra
 * charge, what was received, a note — totals worked live by the phone's
 * shared InvoiceMath. An invoice never touches the ledger, as on the phone.
 * Numbered INV- on save (KhataDao.saveInvoiceWithItems).
 */
@Composable
fun InvoiceForm(
    parties: List<PartyWithBalance>,
    onSave: (Invoice, List<InvoiceItem>) -> Unit,
    onCancel: () -> Unit,
    /** A saved invoice to correct: its number, design and source stay; everything typed can change. */
    existing: Invoice? = null,
    existingLines: List<InvoiceItem> = emptyList(),
    /** The chosen design drawn with what is typed so far (its first page), or null. */
    preview: (suspend (Invoice, List<InvoiceItem>) -> java.awt.image.BufferedImage?)? = null,
    /** The optional fields the shop uses (Invoice settings); one already filled on this invoice always shows. */
    fields: InvoiceFields = InvoiceFields()
) {
    val c = LocalRoshanColors.current
    fun pad(v: Double?) = v?.takeIf { it != 0.0 }?.let { Calc.trim(it) }.orEmpty()
    var customer by remember { mutableStateOf(existing?.customerName.orEmpty()) }
    var phone by remember { mutableStateOf(existing?.customerPhone.orEmpty()) }
    var date by remember { mutableStateOf(existing?.invoiceDate ?: System.currentTimeMillis()) }
    var lines by remember { mutableStateOf(existingLines) }
    var discount by remember { mutableStateOf(pad(existing?.discountPercent)) }
    var tax by remember { mutableStateOf(pad(existing?.taxPercent)) }
    var chargeLabel by remember { mutableStateOf(existing?.additionalChargeLabel.orEmpty()) }
    var charge by remember { mutableStateOf(pad(existing?.additionalChargeAmount)) }
    var received by remember { mutableStateOf(pad(existing?.receivedAmount)) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var pickingDue by remember { mutableStateOf(false) }
    val showDiscount = fields.discount || existing?.discountPercent != null
    val showTax = fields.tax || existing?.taxPercent != null
    val showCharge = fields.charge || existing?.additionalChargeAmount != null
    val showReceived = fields.received || existing?.receivedAmount != null
    val showNote = fields.note || !existing?.note.isNullOrBlank()
    val showDue = fields.dueDate || existing?.dueDate != null
    var templateId by remember { mutableStateOf(existing?.templateId ?: 1) }
    var picture by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val needCustomer = word(W.invoice_customer_required)
    val needItem = word(W.invoice_needs_item)
    val t = InvoiceMath.totals(lines, Calc.evalPercent(discount), Calc.evalPercent(tax), Calc.evalPad(charge), Calc.evalPad(received))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(existing?.let { word(W.edit) + "  ·  " + it.invoiceNumber } ?: word(W.add_invoice), color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Outlined(customer, { customer = it; problem = null }, label = { Text(word(W.invoice_customer_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val q = customer.trim().lowercase()
        if (q.isNotEmpty()) {
            val hits = parties.filter { NameSearch.matches(it.name, it.phone, q) && it.name != customer }.take(30)
            if (hits.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 130.dp).background(Look.subtle, Look.ctl)) {
                    items(hits, key = { it.id }) { p ->
                        Text(
                            Format.name(p.name) + (p.phone?.let { "  ·  " + Format.ltr(it) } ?: ""),
                            Modifier.fillMaxWidth().press { customer = p.name; phone = p.phone.orEmpty() }.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = c.ink, fontSize = 14.sp
                        )
                    }
                }
            }
        }
        Outlined(phone, { phone = it }, label = { Text(word(W.invoice_phone_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluentButton(word(W.pick_invoice_date), { picking = true }, kind = ButtonKind.SUBTLE)
            Text(Format.dateOnly(date), color = c.ink, fontWeight = FontWeight.Bold)
        }
        // The phone's optional due date: printed on the designs that carry one.
        if (showDue) Row(verticalAlignment = Alignment.CenterVertically) {
            FluentButton(word(W.invoice_due_date_hint), { pickingDue = true }, kind = ButtonKind.SUBTLE)
            due?.let { d ->
                Text(Format.dateOnly(d), color = c.ink, fontWeight = FontWeight.Bold)
                TextButton({ due = null }) { Text("\u2715", color = c.textMuted) }
            }
        }

        // The design: the phone's ten, stepped through with ‹ ›, and the first page as it will print.
        DesignStepper(shown = templateId, saved = null, onShow = { templateId = it })
        if (preview != null) {
            androidx.compose.runtime.LaunchedEffect(templateId, customer, phone, date, due, lines, discount, tax, chargeLabel, charge, received, note) {
                kotlinx.coroutines.delay(400)
                val draft = (existing ?: Invoice(customerName = "")).copy(
                    templateId = templateId, customerName = customer.trim().ifEmpty { "\u2014" }, customerPhone = phone.trim().ifEmpty { null },
                    invoiceDate = date, taxPercent = Calc.evalPercent(tax)?.takeIf { it != 0.0 },
                    discountPercent = Calc.evalPercent(discount)?.takeIf { it != 0.0 },
                    additionalChargeLabel = chargeLabel.trim().ifEmpty { null }, additionalChargeAmount = Calc.evalPad(charge)?.takeIf { it != 0.0 },
                    receivedAmount = Calc.evalPad(received)?.takeIf { it != 0.0 }, note = note.trim().ifEmpty { null },
                    dueDate = due,
                    invoiceNumber = existing?.invoiceNumber ?: "INV-\u2026"
                )
                picture = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { preview(draft, lines) }.getOrNull()?.toComposeImageBitmap()
                }
            }
            picture?.let {
                androidx.compose.foundation.Image(
                    it, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).background(Look.subtle, Look.ctl).padding(6.dp),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                )
            }
        }

        lines.forEachIndexed { i, l ->
            Row(Modifier.fillMaxWidth().background(Look.subtle, Look.ctl).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.itemName, color = c.ink, fontWeight = FontWeight.Bold)
                    Text(word(W.bill_item_figures, Format.qty(l.quantity, l.unit), Format.money(l.rate), Format.money(l.lineTotal)), color = c.inkSoft, fontSize = 12.sp)
                }
                TextButton({ lines = lines.filterIndexed { j, _ -> j != i } }) { Text(word(W.delete), color = c.owedToMe) }
            }
        }
        InvoiceLineForm { lines = lines + it; problem = null }

        if (showDiscount || showTax) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showDiscount) Outlined(discount, { discount = it }, label = { Text(word(W.invoice_discount_label) + " %") }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.PERCENT)
            if (showTax) Outlined(tax, { tax = it }, label = { Text(word(W.invoice_tax_label) + " %") }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.PERCENT)
        }
        if (showCharge) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Outlined(chargeLabel, { chargeLabel = it }, label = { Text(word(W.invoice_feature_extra_charge)) }, singleLine = true, modifier = Modifier.weight(1f))
            Outlined(charge, { charge = it }, label = { Text(word(W.invoice_charge_amount_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
        }
        if (showReceived) Outlined(received, { received = it }, label = { Text(word(W.invoice_received_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
        if (showNote) Outlined(note, { note = it }, label = { Text(word(W.invoice_note_label)) }, modifier = Modifier.fillMaxWidth())

        Column(Modifier.fillMaxWidth().background(Look.subtle, Look.ctl).padding(12.dp)) {
            SumLine(word(W.invoice_sum_items), Format.money(t.subtotal))
            if (t.discountAmount > 0) SumLine(word(W.invoice_discount_label), "− " + Format.money(t.discountAmount))
            if (t.taxAmount > 0) SumLine(word(W.invoice_tax_label), Format.money(t.taxAmount))
            if (t.additionalCharge != 0.0) SumLine(chargeLabel.ifBlank { word(W.invoice_feature_extra_charge) }, Format.money(t.additionalCharge))
            SumLine(word(W.invoice_sum_total), Format.money(t.grandTotal), bold = true)
            if (t.received > 0) {
                SumLine(word(W.invoice_sum_received), Format.money(t.received))
                SumLine(word(W.invoice_sum_balance), Format.money(t.balanceDue), bold = true)
            }
        }
        problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
            FillButton(word(W.save), c.brandGreen) {
                when {
                    customer.isBlank() -> problem = needCustomer
                    lines.isEmpty() -> problem = needItem
                    else -> onSave(
                        (existing ?: Invoice(customerName = "")).copy(
                            templateId = templateId,
                            customerName = customer.trim(),
                            customerPhone = phone.trim().ifEmpty { null },
                            invoiceDate = date,
                            taxPercent = Calc.evalPercent(tax)?.takeIf { it != 0.0 },
                            discountPercent = Calc.evalPercent(discount)?.takeIf { it != 0.0 },
                            additionalChargeLabel = chargeLabel.trim().ifEmpty { null },
                            additionalChargeAmount = Calc.evalPad(charge)?.takeIf { it != 0.0 },
                            receivedAmount = Calc.evalPad(received)?.takeIf { it != 0.0 },
                            note = note.trim().ifEmpty { null },
                            dueDate = due
                        ),
                        lines
                    )
                }
            }
        }
    }
    if (picking) DayPicker(date, onPick = { date = it; picking = false }, onDismiss = { picking = false })
    if (pickingDue) DayPicker(due ?: date, onPick = { due = it; pickingDue = false }, onDismiss = { pickingDue = false })
}

/** The phone's Invoice settings: which optional fields the invoice form shows. Applies at once, no Save. */
@Composable
fun InvoiceFieldsSettings(fields: InvoiceFields, onChange: (InvoiceFields) -> Unit) {
    val c = LocalRoshanColors.current
    Column(Modifier.fillMaxSize().background(c.surface).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(word(W.invoice_settings_subtitle), color = c.textMuted, fontSize = 13.sp)
        listOf(
            Triple(W.invoice_feature_discount, W.invoice_feature_discount_desc, fields.discount) to { on: Boolean -> fields.copy(discount = on) },
            Triple(W.invoice_feature_tax, W.invoice_feature_tax_desc, fields.tax) to { on: Boolean -> fields.copy(tax = on) },
            Triple(W.invoice_feature_extra_charge, W.invoice_feature_extra_charge_desc, fields.charge) to { on: Boolean -> fields.copy(charge = on) },
            Triple(W.invoice_feature_received, W.invoice_feature_received_desc, fields.received) to { on: Boolean -> fields.copy(received = on) },
            Triple(W.invoice_feature_note, W.invoice_feature_note_desc, fields.note) to { on: Boolean -> fields.copy(note = on) },
            Triple(W.invoice_feature_due_date, W.invoice_feature_due_date_desc, fields.dueDate) to { on: Boolean -> fields.copy(dueDate = on) }
        ).forEach { (row, change) ->
            val (title, desc, on) = row
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(word(title), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(word(desc), color = c.textMuted, fontSize = 13.sp)
                }
                androidx.compose.material3.Switch(checked = on, onCheckedChange = { onChange(change(it)) })
            }
        }
    }
}

@Composable
private fun SumLine(label: String, value: String, bold: Boolean = false) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), color = c.inkSoft, fontSize = 14.sp)
        Text(value, color = c.ink, fontSize = if (bold) 16.sp else 14.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun InvoiceLineForm(onAdd: (InvoiceItem) -> Unit) {
    val c = LocalRoshanColors.current
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Outlined(name, { name = it }, label = { Text(word(W.invoice_item_name_hint)) }, singleLine = true, modifier = Modifier.weight(2f))
        Outlined(qty, { qty = it }, label = { Text(word(W.qty_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
        Outlined(unit, { unit = it }, label = { Text(LocalTrade.current.unitHint()) }, singleLine = true, modifier = Modifier.weight(1f))
        Outlined(rate, { rate = it }, label = { Text(word(W.invoice_item_rate_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
    }
    val q = Calc.evalAmount(qty)
    val r = Calc.evalPad(rate)
    FillButton(word(W.add_item), c.brandGreen, enabled = name.isNotBlank() && q != null && r != null && r >= 0) {
        onAdd(InvoiceItem(invoiceId = 0, itemName = name.trim(), quantity = q!!, unit = unit.trim().ifEmpty { null }, rate = r!!))
        name = ""; qty = "1"; unit = ""; rate = ""
    }
}

/**
 * An invoice on A4: the shop and the invoice's number and date in the band,
 * who it is for, the lines, then the totals (shared InvoiceMath). One plain
 * design; the phone's ten templates are its own drawing code.
 */
class InvoicePrint(
    private val words: Words,
    private val rtl: Boolean,
    private val shopName: String,
    private val shopLine: String?,
    private val invoice: Invoice,
    private val items: List<InvoiceItem>
) : Printable {
    private val pageW = 595.0
    private val margin = 36.0

    private fun Graphics2D.text(s: String, x: Double, y: Double, end: Boolean = false) {
        val w = fontMetrics.stringWidth(s)
        val left = when {
            !rtl && !end -> x
            !rtl && end -> x - w
            rtl && !end -> pageW - x - w
            else -> pageW - x
        }
        drawString(s, left.toFloat(), y.toFloat())
    }

    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        if (pageIndex > 0) return Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        g.translate(format.imageableX, format.imageableY)
        g.scale(format.imageableWidth / pageW, format.imageableWidth / pageW)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val navy = Color(0x1B, 0x2A, 0x4A)
        g.color = navy
        g.fillRect(0, 0, pageW.toInt(), 84)
        g.color = Color.WHITE
        g.font = Font(PrintFonts.family, Font.BOLD, 18)
        g.text(shopName, margin, 32.0)
        g.font = Font(PrintFonts.family, Font.PLAIN, 10)
        shopLine?.let { g.text(it, margin, 50.0) }
        g.font = Font(PrintFonts.family, Font.BOLD, 12)
        g.text(invoice.invoiceNumber, pageW - margin, 32.0, end = true)
        g.font = Font(PrintFonts.family, Font.PLAIN, 10)
        g.text(Format.dateOnly(invoice.invoiceDate), pageW - margin, 50.0, end = true)

        var y = 110.0
        g.color = Color.BLACK
        g.font = Font(PrintFonts.family, Font.BOLD, 13)
        g.text(invoice.customerName, margin, y)
        invoice.customerPhone?.let { g.font = Font(PrintFonts.family, Font.PLAIN, 10); g.text(Format.ltr(it), margin, y + 15) }
        y += 40
        val xQty = pageW - margin - 190
        val xRate = pageW - margin - 95
        val xAmt = pageW - margin
        g.font = Font(PrintFonts.family, Font.BOLD, 10)
        g.text(words.text(W.invoice_item_name_hint), margin, y)
        g.text(words.text(W.qty_hint), xQty, y, end = true)
        g.text(words.text(W.invoice_item_rate_hint), xRate, y, end = true)
        g.text(words.text(W.amount), xAmt, y, end = true)
        y += 6
        g.color = Color(0xE4, 0xE2, 0xDC)
        g.drawLine(margin.toInt(), y.toInt(), (pageW - margin).toInt(), y.toInt())
        y += 16
        for (it in items.take(28)) {
            g.color = Color.BLACK
            g.font = Font(PrintFonts.family, Font.PLAIN, 10)
            g.text(it.itemName, margin, y)
            g.text(Format.qty(it.quantity, it.unit), xQty, y, end = true)
            g.text(Format.money(it.rate), xRate, y, end = true)
            g.font = Font(PrintFonts.family, Font.BOLD, 10)
            g.text(Format.money(it.lineTotal), xAmt, y, end = true)
            y += 18
        }
        val t = invoice.totals(items)
        y += 10
        fun sum(label: String, value: String, bold: Boolean = false) {
            g.color = Color.BLACK
            g.font = Font(PrintFonts.family, if (bold) Font.BOLD else Font.PLAIN, if (bold) 12 else 10)
            g.text(label, xRate - 60, y, end = true)
            g.text(value, xAmt, y, end = true)
            y += if (bold) 20 else 16
        }
        sum(words.text(W.invoice_sum_items), Format.money(t.subtotal))
        if (t.discountAmount > 0) sum(words.text(W.invoice_discount_label), "− " + Format.money(t.discountAmount))
        if (t.taxAmount > 0) sum(words.text(W.invoice_tax_label), Format.money(t.taxAmount))
        if (t.additionalCharge != 0.0) sum(invoice.additionalChargeLabel ?: words.text(W.invoice_feature_extra_charge), Format.money(t.additionalCharge))
        sum(words.text(W.invoice_sum_total), Format.money(t.grandTotal), bold = true)
        if (t.received > 0) {
            sum(words.text(W.invoice_sum_received), Format.money(t.received))
            sum(words.text(W.invoice_sum_balance), Format.money(t.balanceDue), bold = true)
        }
        invoice.note?.let { g.color = Color.DARK_GRAY; g.font = Font(PrintFonts.family, Font.PLAIN, 10); g.text(it, margin, y + 20) }
        return Printable.PAGE_EXISTS
    }
}
