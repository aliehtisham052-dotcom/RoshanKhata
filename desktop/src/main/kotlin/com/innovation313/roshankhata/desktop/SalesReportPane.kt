package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.SalesReport
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.ReportPeriod
import com.innovation313.roshankhata.screens.ReportView
import com.innovation313.roshankhata.screens.periodWindow
import com.innovation313.roshankhata.screens.reportRows
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The sales report (POS Phase 7): a period, a view (by day / item / category /
 * customer / cashier / hour, tax, profit and loss, not selling), the rows
 * (the same rows the phone shows — reportRows), to Excel or a sheet.
 */
@Composable
fun SalesReportPane(dao: KhataDao, words: Words, rtl: Boolean, shopName: String?, reload: Int) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(ReportPeriod.TODAY) }
    var view by remember { mutableStateOf(ReportView.ITEM) }
    var report by remember { mutableStateOf<SalesReport?>(null) }
    var told by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(period, reload) {
        val (from, to) = periodWindow(period)
        report = withContext(Dispatchers.IO) { runCatching { SalesReport.compute(dao, from, to) }.getOrNull() }
    }
    val r = report
    val table = r?.let { reportRows(it, view) }
    val heading = word(view.wordKey) + "  ·  " + word(period.wordKey)
    FluentPage(word(W.sales_report_title), told ?: heading, scroll = false, actions = {
        FluentButton(word(W.export_excel), {
            if (!Access.can(Access.Permission.EXPORT)) { told = words.text(W.access_denied); return@FluentButton }
            val t = table ?: return@FluentButton
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, words.text(W.export_excel), java.awt.FileDialog.SAVE)
            startIn(dialog, "Documents")
            dialog.file = "RoshanKhata_Sales_" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ENGLISH).format(java.util.Date()) + ".csv"
            dialog.isVisible = true
            val name = dialog.file ?: return@FluentButton
            val file = java.io.File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
            val csv = StringBuilder("﻿")
            (listOf(t.first) + t.second).forEach { row -> row.joinTo(csv, ",") { "\"" + it.replace("\"", "\"\"") + "\"" }; csv.append("\r\n") }
            scope.launch {
                told = runCatching { withContext(Dispatchers.IO) { file.writeText(csv.toString(), Charsets.UTF_8) } }
                    .map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { it.message ?: it.toString() }
            }
        }, kind = ButtonKind.STANDARD, enabled = table != null)
        FluentButton(word(W.ledger_pdf_button), {
            val t = table ?: return@FluentButton
            StatementPrint.show(
                TablePrint(rtl, shopName ?: words.text(W.app_name), words.text(W.sales_report_title) + "  ·  " + heading, t.first, t.second) { p, of -> words.text(W.pdf_stmt_page, p, of) },
                words.text(W.sales_report_title)
            )
        }, kind = ButtonKind.ACCENT, icon = "ic_pdf_doc", enabled = table != null)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPeriod.values().forEach { p -> FluentPill(word(p.wordKey), period == p) { period = p } }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportView.values().forEach { v -> FluentPill(word(v.wordKey), view == v) { view = v } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FigureCard(word(W.srep_revenue), r?.let { Format.money(it.revenue) } ?: "…", c.ink, Modifier.weight(1f))
            FigureCard(word(W.srep_col_count), r?.saleCount?.toString() ?: "…", c.inkSoft, Modifier.weight(1f))
            FigureCard(word(W.srep_net), r?.let { Format.money(it.netProfit) } ?: "…", if ((r?.netProfit ?: 0.0) < 0) c.owedToMe else Look.accent, Modifier.weight(1f),
                under = r?.let { words.text(W.srep_cost_known, Format.money(it.costKnownRevenue)) })
        }
        ListCard(Modifier.weight(1f)) {
            if (table == null || table.second.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(word(W.srep_none), color = c.textMuted) }
            else {
                val (head, rows) = table
                val widths = head.mapIndexed { i, _ -> if (i == 0) 4 else 2 }
                if (head.any { it.isNotEmpty() }) TableHead(head.zip(widths), endAligned = head.indices.filter { it > 0 }.toSet())
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(rows) { cells ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cells.forEachIndexed { i, v ->
                                Text(v, Modifier.weight(widths.getOrElse(i) { 2 }.toFloat()), color = c.ink, fontSize = if (i == 0) 14.sp else 13.sp,
                                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End)
                            }
                        }
                        Rule()
                    }
                }
            }
        }
    }
}
