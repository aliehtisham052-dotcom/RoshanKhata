package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.BillSummary
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word

/**
 * The phone's records on the computer, to read (KMP Phase 7g): Supplier
 * Bills with what each delivery held, Products, and Invoices — so a book
 * restored from the phone shows all of it. Making new bills and invoices
 * stays on the phone for now: both write stock and numbers, and get their
 * own step with tests.
 */
@Composable
fun BillsPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val bills by remember { dao.observeBills() }.collectAsState(emptyList())
    var open by remember { mutableStateOf<Long?>(null) }
    RecordList(word(W.supplier_bills), word(W.bill_ledger_note), bills.isEmpty(), word(W.no_bills)) {
        items(bills, key = { it.id }) { b ->
            Column(Modifier.fillMaxWidth().clickable { open = if (open == b.id) null else b.id }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.name(b.partyName), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(b.billNumber?.takeIf { it.isNotBlank() }?.let { "#$it" }, Format.dateOnly(b.billDate)).joinToString("  ·  "),
                            color = c.textMuted, fontSize = 13.sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Format.money(b.totalAmount), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        BillState(b)
                    }
                }
                if (open == b.id) {
                    val lines by remember(b.id) { dao.observeBillItems(b.id) }.collectAsState(emptyList())
                    Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(c.pageBg, RoundedCornerShape(10.dp)).padding(10.dp)) {
                        lines.forEach { l ->
                            val detail = listOfNotNull(
                                Format.qty(l.quantity, l.unit),
                                l.rate?.let { "@ " + Format.money(it) },
                                l.batchNumber?.takeIf { it.isNotBlank() }?.let { word(W.batch_label, it) },
                                l.expiryDate?.let { word(W.expiry_date) + ": " + Format.dateOnly(it) }
                            ).joinToString("  ·  ")
                            Text(l.productName, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(detail, color = c.inkSoft, fontSize = 12.sp)
                        }
                    }
                }
            }
            HorizontalDivider(color = c.pageBg)
        }
    }
}

/** The supplier's live balance, not a stored flag: a payment in the khata shows here at once. */
@Composable
private fun BillState(b: BillSummary) {
    val c = LocalRoshanColors.current
    if (Money.isNegative(b.supplierBalance)) {
        val overdue = b.dueDate?.let { it < System.currentTimeMillis() } == true
        Text(
            word(if (overdue) W.bill_owe_overdue else W.bill_owe_amount, Format.money(-b.supplierBalance)),
            color = c.owedToMe, fontSize = 13.sp
        )
    } else {
        Text(word(W.bill_account_settled), color = c.iOwe, fontSize = 13.sp)
    }
}

@Composable
fun ProductsPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val products by remember { dao.observeProducts() }.collectAsState(emptyList())
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = products.filter { NameSearch.matches(it.name, it.company, q) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(word(W.tile_products)) },
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)
        )
        RecordList(word(W.tile_products), null, shown.isEmpty(), word(W.no_products)) {
            items(shown, key = { it.id }) { p ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(p.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    val sub = listOfNotNull(p.company, p.category, p.defaultUnit).filter { it.isNotBlank() }
                    if (sub.isNotEmpty()) Text(sub.joinToString("  ·  "), color = c.textMuted, fontSize = 13.sp)
                }
                HorizontalDivider(color = c.pageBg)
            }
        }
    }
}

@Composable
fun InvoicesPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val invoices by remember { dao.observeInvoices() }.collectAsState(emptyList())
    RecordList(word(W.nav_invoice), null, invoices.isEmpty(), word(W.no_invoices)) {
        items(invoices, key = { it.id }) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Format.name(i.customerName), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(i.invoiceNumber + "  ·  " + Format.dateOnly(i.invoiceDate), color = c.textMuted, fontSize = 13.sp)
                }
                Text(Format.money(i.grandTotal), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = c.pageBg)
        }
    }
}

/** A titled list on a rounded card, or the empty line when there is nothing yet. */
@Composable
private fun RecordList(
    title: String,
    subtitle: String?,
    empty: Boolean,
    emptyText: String,
    rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val c = LocalRoshanColors.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PaneTitle(title, subtitle)
        if (empty) {
            Text(emptyText, Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(14.dp)), content = rows)
        }
    }
}
