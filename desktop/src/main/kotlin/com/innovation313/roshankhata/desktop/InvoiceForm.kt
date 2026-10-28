package com.innovation313.roshankhata.desktop

import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
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
 *
 * Marhala 4 (25 Oct 2026, the audit): in the app's frame — Save, Cancel and the total in its foot, always
 * in sight (they were at the end of a long scroll) — with the design and its live picture beside the form
 * when the dialog is wide, and the line's name box suggesting the shop's products as the phone's does.
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
    fields: InvoiceFields = InvoiceFields(),
    /** The shop's products, offered in the line's name box (the phone's SmartSuggest); null offers none. */
    products: ProductShelf? = null,
    /**
     * A NEW invoice started from something (a khata sale, the phone's EntryInvoice): its customer, phone, day and
     * the sale it came from, and its lines — everything still the owner's to change until Save.
     */
    start: Invoice? = null,
    startLines: List<InvoiceItem> = emptyList()
) {
    val c = LocalRoshanColors.current
    fun pad(v: Double?) = v?.takeIf { it != 0.0 }?.let { Calc.trim(it) }.orEmpty()
    val from = existing ?: start
    var customer by remember { mutableStateOf(from?.customerName.orEmpty()) }
    var phone by remember { mutableStateOf(from?.customerPhone.orEmpty()) }
    var date by remember { mutableStateOf(from?.invoiceDate ?: System.currentTimeMillis()) }
    var lines by remember { mutableStateOf(if (existing != null) existingLines else startLines) }
    // The discount as a % or in money, as on the phone (25 Oct 2026, the audit): a phone invoice's
    // Rs discount was left out of this form's total while the list and the print took it off.
    var discountInMoney by remember { mutableStateOf(existing?.discountAmount != null) }
    var discount by remember { mutableStateOf(pad(existing?.discountAmount ?: existing?.discountPercent)) }
    var tax by remember { mutableStateOf(pad(existing?.taxPercent)) }
    var chargeLabel by remember { mutableStateOf(existing?.additionalChargeLabel.orEmpty()) }
    var charge by remember { mutableStateOf(pad(existing?.additionalChargeAmount)) }
    var received by remember { mutableStateOf(pad(existing?.receivedAmount)) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var pickingDue by remember { mutableStateOf(false) }
    val showDiscount = fields.discount || existing?.discountPercent != null || existing?.discountAmount != null
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
    val needLine = word(W.line_editor_busy)
    var lineBusy by remember { mutableStateOf(false) }
    val (discPercent, discMoney) = invoiceDiscount(discount, discountInMoney, lines.sumOf { it.lineTotal })
    val t = InvoiceMath.totals(lines, discPercent, Calc.evalPercent(tax), Calc.evalPad(charge), Calc.evalPad(received), discMoney)

    // The invoice as typed: what Save keeps, and (with a stand-in name and number) what the picture shows.
    fun built(forPicture: Boolean): Invoice {
        val inv = (existing ?: start ?: Invoice(customerName = "")).copy(
            templateId = templateId,
            customerName = customer.trim().let { if (forPicture) it.ifEmpty { "\u2014" } else it },
            customerPhone = phone.trim().ifEmpty { null },
            invoiceDate = date,
            taxPercent = Calc.evalPercent(tax)?.takeIf { it != 0.0 },
            // One kind of discount is kept: the other is cleared (the phone's save rule).
            discountPercent = discPercent,
            discountAmount = discMoney,
            additionalChargeLabel = chargeLabel.trim().ifEmpty { null },
            additionalChargeAmount = Calc.evalPad(charge)?.takeIf { it != 0.0 },
            receivedAmount = Calc.evalPad(received)?.takeIf { it != 0.0 },
            note = note.trim().ifEmpty { null },
            dueDate = due
        )
        return if (forPicture) inv.copy(invoiceNumber = existing?.invoiceNumber ?: "INV-\u2026") else inv
    }
    fun save() {
        when {
            customer.isBlank() -> problem = needCustomer
            lineBusy -> problem = needLine
            lines.isEmpty() -> problem = needItem
            else -> onSave(built(forPicture = false), lines)
        }
    }
    if (preview != null) {
        androidx.compose.runtime.LaunchedEffect(templateId, customer, phone, date, due, lines, discount, discountInMoney, tax, chargeLabel, charge, received, note) {
            kotlinx.coroutines.delay(400)
            val draft = built(forPicture = true)
            picture = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { preview(draft, lines) }.getOrNull()?.toComposeImageBitmap()
            }
        }
    }

    // Everything typed, top to bottom; the frame's foot keeps Save and the total in sight.
    val form: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Outlined(customer, { customer = it; problem = null }, label = { Text(word(W.invoice_customer_hint)) }, singleLine = true, modifier = Modifier.weight(1.4f))
            Outlined(phone, { phone = it }, label = { Text(word(W.invoice_phone_hint)) }, singleLine = true, modifier = Modifier.weight(1f),
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
        }
        val q = customer.trim().lowercase()
        if (q.isNotEmpty()) {
            val hits = parties.filter { NameSearch.matches(it.name, it.phone, q) && it.name != customer }.take(30)
            if (hits.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 130.dp).background(Look.subtle, Look.ctl)) {
                    items(hits, key = { it.id }) { p ->
                        Text(
                            Format.name(p.name) + (p.phone?.takeIf { !samePhone(it, p.name) }?.let { "  ·  " + Format.ltr(it) } ?: ""),
                            Modifier.fillMaxWidth().press { customer = p.name; phone = p.phone.orEmpty() }.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = c.ink, fontSize = 14.sp
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            DateBox(word(W.date_label), Format.dateOnly(date), word(W.pick_date), { picking = true }, Modifier.weight(1f))
            // The phone's optional due date: printed on the designs that carry one.
            if (showDue) {
                DateBox(word(W.invoice_due_date_hint), due?.let { Format.dateOnly(it) }, word(W.pick_date), { pickingDue = true }, Modifier.weight(1f))
                if (due != null) IconAction("ic_close", word(W.filter_clear), { due = null })
            }
        }

        Text(word(W.invoice_items_label), Modifier.padding(top = 6.dp), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        lines.forEachIndexed { i, l ->
            Row(Modifier.fillMaxWidth().background(Look.subtle, Look.ctl).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.itemName, color = c.ink, fontWeight = FontWeight.Bold)
                    Text(word(W.bill_item_figures, Format.qty(l.quantity, l.unit), Format.money(l.rate), Format.money(l.lineTotal)), color = c.inkSoft, fontSize = 12.sp)
                }
                FluentButton(word(W.delete), { lines = lines.filterIndexed { j, _ -> j != i } }, kind = ButtonKind.DANGER)
            }
        }
        InvoiceLineForm(products, onBusy = { lineBusy = it; if (!it && problem == needLine) problem = null }) { lines = lines + it; problem = null }

        if (showDiscount) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(word(W.invoice_discount_label), color = c.inkSoft, fontSize = 13.sp)
            FluentPill("%", !discountInMoney) { discountInMoney = false }
            FluentPill(com.innovation313.roshankhata.data.Currency.symbol, discountInMoney) { discountInMoney = true }
        }
        if (showDiscount || showTax) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showDiscount) Outlined(
                discount, { discount = it },
                label = { Text(word(W.invoice_discount_label) + if (discountInMoney) " (" + com.innovation313.roshankhata.data.Currency.symbol + ")" else " %") },
                singleLine = true, modifier = Modifier.weight(1f),
                calc = if (discountInMoney) com.innovation313.roshankhata.screens.CalcKind.MONEY else com.innovation313.roshankhata.screens.CalcKind.PERCENT
            )
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
    }
    // The design (the phone's ten, stepped through with ‹ ›) and its first page as it will print. [fill]: the
    // page beside the form, scrolling in the height left; else under the form, its top shown.
    val design: @Composable ColumnScope.(Boolean) -> Unit = { fill ->
        Text(word(W.invoice_preview_label), color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        DesignStepper(shown = templateId, saved = null, onShow = { templateId = it })
        if (preview != null) {
            val pic = picture
            Box((if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth().heightIn(max = 520.dp)).clip(Look.ctl).background(Look.subtle).border(1.dp, Look.strokeCard, Look.ctl)) {
                if (pic == null) {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                } else {
                    Column(if (fill) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.Image(
                            pic, contentDescription = null,
                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                        )
                    }
                }
            }
        }
    }

    DialogFrame(
        title = existing?.let { word(W.edit) + "  ·  " + it.invoiceNumber } ?: word(W.add_invoice),
        onClose = onCancel,
        scroll = false,
        footerStart = {
            val said = problem
            if (said != null) Text(said, color = c.owedToMe, fontSize = 13.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            else Text(word(W.invoice_sum_total) + "  " + Format.money(t.grandTotal), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        },
        footer = {
            FluentButton(word(W.cancel), onCancel)
            FluentButton(word(W.save), { save() }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Beside the form once the dialog has room for both (the laptop's 1093 dp window has), else below it.
            val wide = maxWidth >= 860.dp
            val sideWidth = maxWidth * 0.42f
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = form)
                    Column(Modifier.width(sideWidth).fillMaxHeight().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { design(true) }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    form()
                    design(false)
                }
            }
        }
    }
    if (picking) DayPicker(date, onPick = { date = it; picking = false }, onDismiss = { picking = false })
    if (pickingDue) DayPicker(due ?: date, onPick = { due = it; pickingDue = false }, onDismiss = { pickingDue = false })
}

/**
 * The form's discount as the invoice keeps it, (percent, money), never both
 * (25 Oct 2026, the audit; the phone's InvoiceEditor rule). A money discount
 * may be written as a share of the items ("10%" off 2,500 is 250). Empty or
 * zero is no discount.
 */
fun invoiceDiscount(typed: String, inMoney: Boolean, subtotal: Double): Pair<Double?, Double?> =
    if (inMoney) null to Calc.evalDiscount(typed, subtotal)?.takeIf { it != 0.0 }
    else Calc.evalPercent(typed)?.takeIf { it != 0.0 } to null

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
private fun InvoiceLineForm(products: ProductShelf?, onBusy: (Boolean) -> Unit = {}, onAdd: (InvoiceItem) -> Unit) {
    val c = LocalRoshanColors.current
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    // A line typed but not added yet: the invoice's Save waits for it (25 Oct 2026, the audit).
    val busy = name.isNotBlank() || rate.isNotBlank()
    androidx.compose.runtime.LaunchedEffect(busy) { onBusy(busy) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        // The shop's products as the name is typed (the phone's SmartSuggest). A pick fills the name, and the
        // product's own unit when none is typed; its two prices (cash, udhaar) stay the owner's to choose.
        SuggestBox(name, { name = it }, products, word(W.invoice_item_name_hint), Modifier.weight(2f), onPick = { item ->
            name = item.value
            if (unit.isBlank()) products?.product(item.value)?.defaultUnit?.takeIf { it.isNotBlank() }?.let { unit = com.innovation313.roshankhata.data.UnitWords.label(it) }
        })
        Outlined(qty, { qty = it }, label = { Text(word(W.qty_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.QUANTITY)
        Outlined(unit, { unit = it }, label = { Text(LocalTrade.current.unitHint()) }, singleLine = true, modifier = Modifier.weight(1f))
        Outlined(rate, { rate = it }, label = { Text(word(W.invoice_item_rate_hint)) }, singleLine = true, modifier = Modifier.weight(1f), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
    }
    val q = Calc.evalAmount(qty)
    val r = Calc.evalPad(rate)
    FillButton(word(W.add_item), c.brandGreen, enabled = name.isNotBlank() && q != null && r != null && r >= 0) {
        onAdd(InvoiceItem(invoiceId = 0, itemName = name.trim(), quantity = q!!, unit = com.innovation313.roshankhata.data.UnitWords.canonical(unit), rate = r!!))
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

    private fun Graphics2D.text(raw: String, x: Double, y: Double, end: Boolean = false) {
        // The language's own digits when the owner chose them for printed sheets (21 Oct 2026).
        val s = com.innovation313.roshankhata.data.Digits.forPrint(raw)
        val w = PrintFonts.width(this, s)
        val left = when {
            !rtl && !end -> x
            !rtl && end -> x - w
            rtl && !end -> pageW - x - w
            else -> pageW - x
        }
        PrintFonts.draw(this, s, left.toFloat(), y.toFloat())
    }

    override fun print(graphics: Graphics, format: PageFormat, pageIndex: Int): Int {
        if (pageIndex > 0) return Printable.NO_SUCH_PAGE
        val g = graphics as Graphics2D
        g.translate(format.imageableX, format.imageableY)
        g.scale(format.imageableWidth / pageW, format.imageableWidth / pageW)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        // The phone's documents' green (Marhala 4, part 7: it was navy here).
        g.color = PhoneSheet.BAND
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
