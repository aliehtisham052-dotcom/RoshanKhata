package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.PurchaseRegisterRow
import com.innovation313.roshankhata.data.ReportWindow
import com.innovation313.roshankhata.data.SaleRegisterRow
import com.innovation313.roshankhata.data.ShopSettings
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/** The register's chips, the phone's DateRangeFilter without "all" (a register is a fact about a period). */
private val REGISTER_PERIODS = listOf(ReportWindow.TODAY, ReportWindow.YESTERDAY, ReportWindow.WEEK, ReportWindow.MONTH, ReportWindow.CUSTOM)

/**
 * The sales and purchase register on the computer (KMP Phase 7x; Marhala 4, part 7 — the page frame and the
 * phone's document): sales are the "I gave" entries that carried goods and the counter's tickets, each item at
 * quantity x rate with one difference row after a sale written at another amount, so the total is the
 * ledger's; purchases are supplier bill lines, totalled where a rate was written and the rest counted. The
 * window is a chip (this month by default, up to now, as the phone's) or the owner's two days. Each line says
 * what it was as the paper does — the item, how much, the batch, the company and the number; a purchase's
 * rate, expiry and bill — and the PDF is the phone's register ([RegisterPrint]) with the shop's address and tax
 * number from [settings].
 */
@Composable
fun RegisterPane(dao: KhataDao, words: Words, rtl: Boolean, shopName: String?, reload: Int, settings: ShopSettings? = null) {
    val c = LocalRoshanColors.current
    var period by remember { mutableStateOf(ReportWindow.MONTH) }
    var from by remember { mutableStateOf(ReportWindow.MONTH.window()?.first ?: 0L) }
    // The last moment; null runs to now (read when the rows are read and when the paper is made).
    var till by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    var sales by remember { mutableStateOf<List<SaleRegisterRow>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<PurchaseRegisterRow>>(emptyList()) }
    var readTo by remember { mutableStateOf(System.currentTimeMillis()) }
    val adjustment = word(W.register_adjustment)
    val walkIn = word(W.pos_walk_in)
    LaunchedEffect(from, till, reload) {
        val to = till ?: System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            // Walk-in counter tickets too (22 Oct 2026, the review), each with its batch.
            sales = dao.registerSales(from, to, adjustment, walkIn)
            purchases = dao.purchaseRegister(from, to)
        }
        readTo = to
    }
    fun choose(p: ReportWindow) {
        if (p == ReportWindow.CUSTOM) { picking = true; return }
        p.window()?.let { (s, e) -> from = s; till = e }
        period = p
    }
    val salesTotal = sales.sumOf { it.amount }
    val purchaseTotal = purchases.sumOf { it.lineAmount ?: 0.0 }
    val missing = purchases.count { it.rate == null }
    val dayFmt = DateWords.formatter("d MMM yyyy")
    val windowLabel = dayFmt.format(Date(from)).let { first ->
        val last = dayFmt.format(Date(till ?: readTo))
        if (first == last) first else "$first – $last"
    }

    fun saleSub(s: SaleRegisterRow) = listOfNotNull(
        s.itemName?.takeIf { it.isNotBlank() }, s.quantity?.let { Format.qty(it, s.unit) },
        s.batchNumber?.takeIf { it.isNotBlank() }?.let { words.text(W.pdf_reg_batch, it) },
        s.company?.takeIf { it.isNotBlank() }, s.refNumber?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) }
    ).joinToString(" · ")
    fun buySub(p: PurchaseRegisterRow) = listOfNotNull(
        p.itemName, Format.qty(p.quantity, p.unit), p.rate?.let { "@ " + Format.money(it) },
        p.batchNumber?.takeIf { it.isNotBlank() }?.let { words.text(W.pdf_reg_batch, it) },
        p.expiryDate?.let { words.text(W.pdf_reg_expiry, DateWords.formatter("d MMM yyyy").format(Date(it))) },
        p.billNumber?.takeIf { it.isNotBlank() }?.let { words.text(W.pdf_reg_bill, Format.ltr(it)) }
    ).joinToString(" · ")

    val has = sales.isNotEmpty() || purchases.isNotEmpty()
    val style = com.innovation313.roshankhata.data.RegisterStyle.of(LocalTrade.current.key)
    FluentPage(word(W.register_title), windowLabel, scroll = false, actions = {
        FluentButton(word(W.pdf_preview), {
            val to = till ?: System.currentTimeMillis()
            val identity = settings?.let { RegisterPrint.identityLine(words, it.businessAddress(), it.strn(), it.country()) }
            StatementPrint.show(
                RegisterPrint(words, rtl, shopName ?: words.text(W.app_name), identity, from, to, sales, purchases, style = style),
                words.text(W.register_title)
            )
        }, kind = ButtonKind.ACCENT, icon = "ic_pdf_doc", enabled = has)
    }) {
        PillRow {
            REGISTER_PERIODS.forEach { p -> FluentPill(word(p.word), p == period) { choose(p) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FigureCard(word(W.pdf_reg_total_sales), Format.money(salesTotal), c.owedToMe, Modifier.weight(1f),
                under = word(W.pdf_reg_sales_lines) + ": " + sales.size, icon = "ic_arrow_gave", tint = Look.getTint)
            FigureCard(word(W.pdf_reg_total_purchases), Format.money(purchaseTotal), Look.accent, Modifier.weight(1f),
                under = if (missing > 0) plural(WP.register_missing_rate, missing, missing) else word(W.pdf_reg_purchase_lines) + ": " + purchases.size,
                icon = "ic_arrow_got", tint = Look.accentTint)
        }
        if (!has) {
            EmptyState("ic_register_sales", word(W.register_empty), null, Modifier.weight(1f))
        } else ListCard(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "sales") { RegHeading(word(W.pdf_reg_section_sales), word(W.pdf_reg_col_customer)) }
                if (sales.isEmpty()) item(key = "no-sales") { RegNote(word(W.pdf_reg_no_sales)) }
                items(sales) { s -> RegLine(s.date, s.partyName, saleSub(s), Format.money(s.amount)) }
                if (sales.isNotEmpty()) item(key = "sales-total") { RegTotal(word(W.pdf_reg_total_sales), Format.money(salesTotal)) }
                item(key = "purchases") { RegHeading(word(W.pdf_reg_section_purchases), word(W.pdf_reg_col_supplier)) }
                if (purchases.isEmpty()) item(key = "no-purchases") { RegNote(word(W.pdf_reg_no_purchases)) }
                items(purchases) { p -> RegLine(p.date, p.supplierName, buySub(p), p.lineAmount?.let { Format.money(it) }) }
                if (purchases.isNotEmpty()) item(key = "purchase-total") {
                    RegTotal(word(W.pdf_reg_total_purchases), Format.money(purchaseTotal))
                    if (missing > 0) RegNote(word(W.pdf_reg_missing_rate, missing))
                }
            }
        }
    }
    if (picking) TwoDayPicker(from, till, onWindow = { s, e -> from = s; till = e; period = ReportWindow.CUSTOM }, onRefused = { refused = true }, onClose = { picking = false })
    if (refused) TellDialog(word(W.register_title), word(W.invalid_range), { refused = false })
}

/** A section's head: its name and what its rows hold (the paper's column words). */
@Composable
private fun RegHeading(text: String, columns: String) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().background(Look.layerAlt).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(columns, color = c.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Rule()
}

@Composable
private fun RegNote(text: String) =
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = LocalRoshanColors.current.textMuted, fontSize = 13.sp)

/** One line: the day, who, what it was on the line under, the amount (a dash where no rate was written). */
@Composable
private fun RegLine(date: Long, who: String, what: String, amount: String?) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(DateWords.formatter("d MMM").format(Date(date)), color = c.textMuted, fontSize = 12.sp, maxLines = 1, softWrap = false)
        Column(Modifier.weight(1f)) {
            Text(Format.name(who), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (what.isNotEmpty()) Text(what, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(amount ?: "—", color = if (amount != null) c.ink else c.textMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
    Rule()
}

@Composable
private fun RegTotal(label: String, value: String) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
    Rule()
}
