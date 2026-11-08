package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.AuditAction
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.audit
import com.innovation313.roshankhata.data.totals
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the Recycle Bin lists besides customers and entries (8 Nov 2026, the owner's phone test, point 20): cashbook
 * lines, cheques, supplier bills, documents and company schemes. They were binned by their own pages and then
 * nowhere to be seen — no way back, nothing that ever sent them off. The phone's bin lists them; so does this page,
 * with the same DAO rules (KhataDao's bin section: each restore undoes exactly what its delete did, a cheque or bill
 * whose money is gone for good says why it cannot come back). Restore and Delete forever ask for the manager's PIN
 * like the rest of the bin ([Gates.edit]) and are written in Who did what (BIN_RESTORED / BIN_PURGED).
 */
private class BinRow(
    val key: String,
    val kind: String,
    val title: String,
    val detail: String,
    val at: Long,
    val table: String,
    val id: Long,
    val restoreAsk: String,
    /** Brings it back; null when it did, else why it could not. */
    val restore: suspend () -> String?,
    val purge: suspend () -> Unit
)

@Composable
fun OtherBinKinds(
    dao: KhataDao,
    reload: Int,
    onChanged: () -> Unit,
    /** The "Delete forever" question: its words and what runs on yes (the page's own, picture sweep included). */
    askPurge: (String, suspend () -> String?) -> Unit,
    onTell: (String, String) -> Unit
) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<BinRow>?>(null) }
    var asking by remember { mutableStateOf<BinRow?>(null) }
    LaunchedEffect(reload) {
        val older: Flow<Triple<List<com.innovation313.roshankhata.data.CashEntry>, List<com.innovation313.roshankhata.data.Cheque>, List<com.innovation313.roshankhata.data.SupplierBill>>> =
            combine(dao.observeDeletedCash(), dao.observeDeletedCheques(), dao.observeDeletedBills()) { a, b, d -> Triple(a, b, d) }
        val newer: Flow<Pair<List<com.innovation313.roshankhata.data.Invoice>, List<com.innovation313.roshankhata.data.Scheme>>> =
            combine(dao.observeDeletedInvoices(), dao.observeDeletedSchemes()) { a, b -> a to b }
        combine(older, newer) { a, b -> a to b }.collect { (first, second) ->
            val (cash, cheques, bills) = first
            val (docs, schemes) = second
            rows = withContext(Dispatchers.IO) {
                val out = ArrayList<BinRow>()
                cash.forEach { e ->
                    out += BinRow(
                        "c" + e.id, words.text(if (e.isIncome) W.cash_income else W.cash_expense),
                        listOf(e.category, Format.money(e.amount)).filter { it.isNotBlank() }.joinToString(" · "),
                        e.note.orEmpty(), e.deletedAt ?: 0L, "cashbook", e.id, words.text(W.bin_restore_cash_confirm),
                        { if (dao.restoreCashRow(e.id) == 1) null else words.text(W.bin_restore_gone) },
                        { dao.purgeCashRow(e.id) }
                    )
                }
                cheques.forEach { q ->
                    val party = dao.getPartyName(q.partyId).orEmpty()
                    out += BinRow(
                        "q" + q.id, words.text(W.cheques),
                        listOf(q.chequeNumber?.takeIf { it.isNotBlank() }, party.takeIf { it.isNotBlank() }?.let { Format.name(it) }, Format.money(q.amount))
                            .filterNotNull().joinToString(" · "),
                        words.text(if (q.isReceived) W.cheque_received else W.cheque_issued), q.deletedAt ?: 0L, "cheques", q.id,
                        words.text(W.bin_restore_cheque_confirm),
                        { if (dao.restoreCheque(q.id)) null else words.text(W.bin_restore_cheque_blocked) },
                        { dao.purgeChequeRow(q.id) }
                    )
                }
                bills.forEach { b ->
                    val party = dao.getPartyName(b.partyId).orEmpty()
                    out += BinRow(
                        "b" + b.id, words.text(W.supplier_bills),
                        listOf(b.billNumber?.takeIf { it.isNotBlank() }, party.takeIf { it.isNotBlank() }?.let { Format.name(it) }, Format.money(b.totalAmount))
                            .filterNotNull().joinToString(" · "),
                        "", b.deletedAt ?: 0L, "supplier_bills", b.id, words.text(W.bin_restore_bill_confirm),
                        { if (dao.restoreSupplierBill(b.id)) null else words.text(W.bin_restore_bill_blocked) },
                        { dao.purgeSupplierBill(b.id) }
                    )
                }
                docs.forEach { d ->
                    val total = d.totals(dao.invoiceItems(d.id)).grandTotal
                    out += BinRow(
                        "d" + d.id, words.text(com.innovation313.roshankhata.data.DocKind.wordKey(d.kind)),
                        listOf(d.invoiceNumber.takeIf { it.isNotBlank() }, d.customerName.takeIf { it.isNotBlank() }?.let { Format.name(it) }, Format.money(total))
                            .filterNotNull().joinToString(" · "),
                        "", d.deletedAt ?: 0L, "invoices", d.id, words.text(W.bin_restore_doc_confirm),
                        { if (dao.restoreDocument(d.id)) null else words.text(W.bin_restore_gone) },
                        { dao.purgeDocument(d.id) }
                    )
                }
                schemes.forEach { s ->
                    out += BinRow(
                        "s" + s.id, words.text(W.bin_kind_scheme), Format.name(s.name), s.company.orEmpty(),
                        0L, "schemes", s.id, words.text(W.bin_restore_scheme_confirm),
                        { if (dao.restoreScheme(s.id) == 1) null else words.text(W.bin_restore_gone) },
                        { dao.purgeScheme(s.id) }
                    )
                }
                out.sortedByDescending { it.at }
            }
        }
    }
    val list = rows
    Column(Modifier.fillMaxSize()) {
        when {
            list == null -> Text("…", Modifier.padding(16.dp), color = c.textMuted)
            list.isEmpty() -> Text(word(W.bin_empty), Modifier.padding(16.dp), color = c.ink, fontSize = 15.sp)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.key }) { r ->
                    Row(
                        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.kind, color = c.textMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(r.title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (r.detail.isNotBlank()) Text(r.detail, color = c.textMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (r.at > 0L) Text(Format.dateTime(r.at), color = c.textMuted, fontSize = 12.sp)
                        }
                        FluentButton(word(W.delete_forever), {
                            Gates.edit {
                                askPurge(words.text(W.delete_entry_forever_confirm, r.title)) {
                                    r.purge()
                                    dao.audit(AuditAction.BIN_PURGED, r.table, r.id)
                                    null
                                }
                            }
                        }, kind = ButtonKind.DANGER)
                        Gap(6)
                        FluentButton(word(W.restore), { Gates.edit { asking = r } }, kind = ButtonKind.ACCENT)
                    }
                    Rule()
                }
            }
        }
    }
    asking?.let { r ->
        AskDialog(
            word(W.restore), r.restoreAsk, word(W.restore),
            onDismiss = { asking = null },
            onConfirm = {
                asking = null
                scope.launch {
                    val blocked = withContext(Dispatchers.IO) {
                        runCatching { r.restore() }.getOrElse { failText(words, it) }
                    }
                    if (blocked == null) withContext(Dispatchers.IO) { dao.audit(AuditAction.BIN_RESTORED, r.table, r.id) }
                    onChanged()
                    if (blocked != null) onTell(words.text(W.restore), blocked)
                }
            }
        )
    }
}
