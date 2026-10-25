package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.onSizeChanged
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
    // The owner's own days (21 Oct 2026): the first and the last, whole days.
    var first by remember { mutableStateOf<Long?>(null) }
    var last by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: the first day, false: the last
    val custom = first?.let { a -> a to (last ?: a) }
    var view by remember { mutableStateOf(ReportView.ITEM) }
    var report by remember { mutableStateOf<SalesReport?>(null) }
    var told by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(period, custom, reload) {
        val (from, to) = periodWindow(period, custom = custom)
        report = withContext(Dispatchers.IO) { runCatching { SalesReport.compute(dao, from, to) }.getOrNull() }
    }
    picking?.let { start ->
        DayPicker(if (start) first else last ?: first, onPick = { day ->
            if (start) first = day else last = day
            if (first == null) first = day
            period = ReportPeriod.CUSTOM
            picking = null
        }, onDismiss = { picking = null })
    }
    val r = report
    var chartShown by remember { mutableStateOf(true) }
    var chartTouched by remember { mutableStateOf(false) }
    val table = r?.let { reportRows(it, view) }
    val heading = word(view.wordKey) + "  ·  " + com.innovation313.roshankhata.screens.periodLabel(period, custom)
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
            (listOf(t.first) + t.second).forEach { row -> com.innovation313.roshankhata.data.CsvCell.appendRow(csv, row) }
            scope.launch {
                told = runCatching { withContext(Dispatchers.IO) { file.writeText(csv.toString(), Charsets.UTF_8) } }
                    .map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { failText(words, it) }
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
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPeriod.values().forEach { p ->
                FluentPill(word(p.wordKey), period == p) { if (p == ReportPeriod.CUSTOM && first == null) picking = true else period = p }
            }
            if (period == ReportPeriod.CUSTOM) {
                DateBox(null, first?.let { Format.dateOnly(it) }, word(W.report_start_date), { picking = true }, Modifier.width(170.dp))
                DateBox(null, (last ?: first)?.let { Format.dateOnly(it) }, word(W.report_end_date), { picking = false }, Modifier.width(170.dp))
            }
        }
        PillRow(Modifier.fillMaxWidth()) {
            ReportView.values().forEach { v -> FluentPill(word(v.wordKey), view == v) { view = v } }
            // The chart is the owner's to show or hide; it steps aside by itself only until he chooses (25 Oct 2026,
            // the audit: once aside on a laptop it never came back).
            if (r != null && r.revenue > 0.0) FluentPill(word(W.srep_chart), chartShown) { chartTouched = true; chartShown = !chartShown }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FigureCard(word(W.srep_revenue), r?.let { Format.money(it.revenue) } ?: "…", c.ink, Modifier.weight(1f))
            FigureCard(word(W.srep_col_count), r?.saleCount?.toString() ?: "…", c.inkSoft, Modifier.weight(1f))
            // A loss carries its minus (21 Oct 2026: it printed as a red positive figure).
            // A net only when every item sold has its cost (25 Oct 2026, the audit: with none known it was the
            // expenses alone, shown as a loss); else a dash and what it needs.
            val net = r?.netOrNull
            FigureCard(word(W.srep_net), if (r == null) "…" else net?.let { Format.signedIfNegative(it) } ?: "–", if ((net ?: 0.0) < 0) c.owedToMe else Look.accent, Modifier.weight(1f),
                under = r?.let { if (it.costComplete) words.text(W.srep_cost_known, Format.money(it.costKnownRevenue)) else words.text(W.srep_net_unknown) })
        }
        // The chart (21 Oct 2026): a bar per hour, day or month of the window, from the same lines.
        // On a laptop's short window the chart steps aside once the table would get too little room
        // (21 Oct 2026: at 1366x768 the table was squeezed to nothing). Once aside, it stays aside.
        if (r != null && r.revenue > 0.0 && chartShown) {
            com.innovation313.roshankhata.screens.SalesChart(r.bars(), Modifier.fillMaxWidth().card().padding(14.dp), height = 96.dp)
        }
        val density = androidx.compose.ui.platform.LocalDensity.current
        ListCard(Modifier.weight(1f).onSizeChanged { if (!chartTouched && chartShown && with(density) { it.height.toDp() } < 220.dp) chartShown = false }) {
            if (table == null || table.second.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(word(W.srep_none), color = c.textMuted) }
            else {
                val (head, rows) = table
                val widths = head.mapIndexed { i, _ -> if (i == 0) 4 else 2 }
                if (head.any { it.isNotEmpty() }) WeightedHead(head.zip(widths.map { it.toFloat() }), endAligned = head.indices.filter { it > 0 }.toSet())
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
