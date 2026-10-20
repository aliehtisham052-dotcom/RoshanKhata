package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Payroll
import com.innovation313.roshankhata.data.PurchaseRegisterRow
import com.innovation313.roshankhata.data.SaleRegisterRow
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The sales and purchase register on the computer (KMP Phase 7x), the
 * phone's RegisterReport read the same way: sales are "I gave" entries that
 * carried goods, each item at quantity x rate plus one difference row after
 * a sale written at another amount, so the total is the ledger's; purchases
 * are supplier bill lines, totalled where a rate was written and the rest
 * counted. This month by default. Seen on screen, then printed or saved as PDF.
 */
@Composable
fun RegisterPane(dao: KhataDao, words: Words, rtl: Boolean, shopName: String?, reload: Int) {
    val c = LocalRoshanColors.current
    val (monthStart, monthEnd, _) = Payroll.monthRange(System.currentTimeMillis())
    var from by remember { mutableStateOf(monthStart) }
    var till by remember { mutableStateOf(monthEnd - 24L * 60 * 60 * 1000) }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    var sales by remember { mutableStateOf<List<SaleRegisterRow>>(emptyList()) }
    var purchases by remember { mutableStateOf<List<PurchaseRegisterRow>>(emptyList()) }
    val to = till + 24L * 60 * 60 * 1000 - 1
    val adjustment = word(W.register_adjustment)
    LaunchedEffect(from, till, reload) {
        withContext(Dispatchers.IO) {
            sales = (dao.salesRegister(from, to) + dao.salesAdjustments(from, to).map { it.copy(itemName = adjustment) })
                .sortedWith(compareBy({ it.date }, { it.refNumber }))
            purchases = dao.purchaseRegister(from, to)
        }
    }
    val salesTotal = sales.sumOf { it.amount }
    val purchaseTotal = purchases.sumOf { it.lineAmount ?: 0.0 }
    val missing = purchases.count { it.rate == null }

    fun saleLine(s: SaleRegisterRow) = listOfNotNull(s.partyName, s.itemName, s.batchNumber?.let { words.text(W.pdf_reg_batch, it) }).joinToString(" · ")
    fun buyLine(p: PurchaseRegisterRow) = listOfNotNull(p.supplierName, p.itemName, p.batchNumber?.let { words.text(W.pdf_reg_batch, it) }).joinToString(" · ")

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PaneTitle(word(W.register_title), word(W.register_subtitle)) }
            FluentButton(word(W.ledger_pdf_button), kind = ButtonKind.ACCENT, icon = "ic_pdf_doc", enabled = sales.isNotEmpty() || purchases.isNotEmpty(), onClick = {
                val rows = buildList {
                    add(listOf(words.text(W.pdf_reg_section_sales), "", ""))
                    sales.forEach { s -> add(listOf(Format.dateOnly(s.date) + "  " + saleLine(s), s.quantity?.let { Format.qty(it, s.unit) } ?: "", Format.money(s.amount))) }
                    add(listOf(words.text(W.pdf_reg_total_sales), "", Format.money(salesTotal)))
                    add(listOf(words.text(W.pdf_reg_section_purchases), "", ""))
                    purchases.forEach { p -> add(listOf(Format.dateOnly(p.date) + "  " + buyLine(p), Format.qty(p.quantity, p.unit), p.lineAmount?.let { Format.money(it) } ?: "–")) }
                    add(listOf(words.text(W.pdf_reg_total_purchases), "", Format.money(purchaseTotal)))
                    if (missing > 0) add(listOf(words.text(W.pdf_reg_missing_rate, missing), "", ""))
                }
                StatementPrint.show(
                    TablePrint(rtl, shopName ?: words.text(W.app_name),
                        words.text(W.register_title) + "  ·  " + Format.dateOnly(from) + " – " + Format.dateOnly(till),
                        listOf(words.text(W.pdf_reg_col_customer), words.text(W.qty_hint), words.text(W.amount)), rows
                    ) { p, of -> words.text(W.pdf_stmt_page, p, of) },
                    words.text(W.register_title)
                )
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton({ picking = true }) { Text(Format.dateOnly(from), color = c.ink) }
            Text("–", color = c.textMuted)
            OutlinedButton({ picking = false }) { Text(Format.dateOnly(till), color = c.ink) }
            FluentButton(word(W.range_this_month), { from = monthStart; till = monthEnd - 24L * 60 * 60 * 1000 }, kind = ButtonKind.SUBTLE)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TotalCard(word(W.pdf_reg_total_sales), Format.money(salesTotal), c.owedToMe, Modifier.weight(1f))
            TotalCard(word(W.pdf_reg_total_purchases), Format.money(purchaseTotal), c.iOwe, Modifier.weight(1f))
        }
        if (missing > 0) Text(word(W.pdf_reg_missing_rate, missing), color = c.textMuted, fontSize = 12.sp)
        if (sales.isEmpty() && purchases.isEmpty()) {
            Text(word(W.register_empty), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize().card()) {
                item { RegHeading(word(W.pdf_reg_section_sales)) }
                if (sales.isEmpty()) item { RegNote(word(W.pdf_reg_no_sales)) }
                items(sales) { s -> RegLine(Format.dateOnly(s.date), saleLine(s), s.quantity?.let { Format.qty(it, s.unit) }, Format.money(s.amount)) }
                item { RegHeading(word(W.pdf_reg_section_purchases)) }
                if (purchases.isEmpty()) item { RegNote(word(W.pdf_reg_no_purchases)) }
                items(purchases) { p -> RegLine(Format.dateOnly(p.date), buyLine(p), Format.qty(p.quantity, p.unit), p.lineAmount?.let { Format.money(it) } ?: "–") }
            }
        }
    }
    picking?.let { start ->
        DayPicker(if (start) from else till, onPick = { d -> if (start) from = d else till = d; picking = null }, onDismiss = { picking = null })
    }
}

@Composable
private fun RegHeading(text: String) =
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = LocalRoshanColors.current.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)

@Composable
private fun RegNote(text: String) =
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = LocalRoshanColors.current.textMuted, fontSize = 13.sp)

@Composable
private fun RegLine(date: String, what: String, qty: String?, amount: String) {
    val c = LocalRoshanColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(date, Modifier.padding(end = 12.dp), color = c.textMuted, fontSize = 13.sp)
        Text(what, Modifier.weight(1f), color = c.ink, fontSize = 14.sp)
        Text(qty ?: "", Modifier.padding(horizontal = 12.dp), color = c.inkSoft, fontSize = 13.sp)
        Text(amount, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
    Rule()
}
