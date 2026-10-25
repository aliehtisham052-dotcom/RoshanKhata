package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.Aging
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.screens.AGING_WORDS
import com.innovation313.roshankhata.screens.AgingBar
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.agingColor
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dues by age on the computer (21 Oct 2026): who owes me — or whom I owe —
 * split into 0–30, 31–60, 61–90 and over 90 days ([Aging], the phone's same
 * figures), a figure card per bucket, then a table with the oldest money
 * first. A row opens the party in Khata; the table goes to Excel or a sheet.
 */
@Composable
fun AgingPane(dao: KhataDao, words: Words, rtl: Boolean, shopName: String?, reload: Int, onParty: (Long) -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<Aging.Report?>(null) }
    var owedToMe by remember { mutableStateOf(true) }
    var told by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reload) { report = withContext(Dispatchers.IO) { runCatching { Aging.compute(dao) }.getOrNull() } }
    val r = report
    val rows = r?.let { if (owedToMe) it.receivable else it.payable }.orEmpty()
    val totals = r?.let { if (owedToMe) it.receivableTotals else it.payableTotals } ?: List(Aging.BUCKETS) { 0.0 }
    val bucketWords = AGING_WORDS.map { word(it) }
    val head = listOf(word(W.name)) + bucketWords + word(W.aging_total)
    val cells = rows.map { row -> listOf(Format.name(row.name)) + row.buckets.map { if (it > 0.0) Format.money(it) else "–" } + Format.money(row.balance) }
    val side = word(if (owedToMe) W.i_have_to_get else W.i_have_to_give)

    FluentPage(word(W.aging_title), told ?: side, scroll = false, actions = {
        FluentButton(word(W.export_excel), {
            if (!Access.can(Access.Permission.EXPORT)) { told = words.text(W.access_denied); return@FluentButton }
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, words.text(W.export_excel), java.awt.FileDialog.SAVE)
            startIn(dialog, "Documents")
            dialog.file = "RoshanKhata_Dues_" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date()) + ".csv"
            dialog.isVisible = true
            val name = dialog.file ?: return@FluentButton
            val file = java.io.File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
            // Figures plain in the file (no currency sign), so Excel adds them up.
            val plain = rows.map { row -> listOf(row.name) + row.buckets.map { "%.2f".format(java.util.Locale.US, it) } + "%.2f".format(java.util.Locale.US, row.balance) }
            val csv = StringBuilder("﻿")
            (listOf(head) + plain).forEach { line -> com.innovation313.roshankhata.data.CsvCell.appendRow(csv, line) }
            scope.launch {
                told = runCatching { withContext(Dispatchers.IO) { file.writeText(csv.toString(), Charsets.UTF_8) } }
                    .map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { failText(words, it) }
            }
        }, kind = ButtonKind.STANDARD, enabled = rows.isNotEmpty())
        FluentButton(word(W.ledger_pdf_button), {
            StatementPrint.show(
                // With the totals under the names, as the page's cards show them (25 Oct 2026, the audit).
                TablePrint(rtl, shopName ?: words.text(W.app_name), words.text(W.aging_title) + "  ·  " + side, head, cells,
                    totals = listOf(words.text(W.aging_total)) + totals.map { if (it > 0.0) Format.money(it) else "–" } + Format.money(totals.sum()),
                    made = madeLine(words)
                ) { p, of -> words.text(W.pdf_stmt_page, p, of) },
                words.text(W.aging_title)
            )
        }, kind = ButtonKind.ACCENT, icon = "ic_pdf_doc", enabled = rows.isNotEmpty())
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentPill(word(W.i_have_to_get), owedToMe) { owedToMe = true }
            FluentPill(word(W.i_have_to_give), !owedToMe) { owedToMe = false }
        }
        Text(word(W.aging_help), color = c.textMuted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            totals.forEachIndexed { i, t ->
                FigureCard(bucketWords[i], Format.money(t), if (i == Aging.BUCKETS - 1 && t > 0.0) c.owedToMe else c.ink, Modifier.weight(1f), tint = agingColor(i).copy(alpha = 0.18f))
            }
        }
        if (totals.sum() > 0.0) AgingBar(totals, Modifier.fillMaxWidth())
        ListCard(Modifier.weight(1f)) {
            if (rows.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(if (r == null) "…" else word(W.aging_none), color = c.textMuted) }
            else {
                val widths = listOf(4f, 2f, 2f, 2f, 2f, 2f)
                WeightedHead(head.zip(widths), endAligned = (1 until head.size).toSet())
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(rows, key = { it.partyId }) { row ->
                        Column(Modifier.fillMaxWidth().press { onParty(row.partyId) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(widths[0])) {
                                    Text(Format.name(row.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    row.oldest?.let { Text(word(W.aging_oldest, Format.dateOnly(it)), color = c.textMuted, fontSize = 12.sp, maxLines = 1) }
                                }
                                row.buckets.forEachIndexed { i, b ->
                                    Text(if (b > 0.0) Format.money(b) else "–", Modifier.weight(widths[i + 1]),
                                        color = if (i == Aging.BUCKETS - 1 && b > 0.0) c.owedToMe else if (b > 0.0) c.ink else c.textMuted,
                                        fontSize = 13.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End)
                                }
                                Text(Format.money(row.balance), Modifier.weight(widths.last()), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, softWrap = false, textAlign = TextAlign.End)
                            }
                            AgingBar(row.buckets, Modifier.fillMaxWidth())
                        }
                        Rule()
                    }
                }
            }
        }
    }
}
