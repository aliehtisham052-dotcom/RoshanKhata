package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.innovation313.roshankhata.data.EntryWithParty
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerCsv
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Khata Report on the computer (KMP Phase 7e), the phone's
 * LedgerReportActivity: every entry across every customer in a window set by
 * two date buttons and "All", read on screen before anything is made. Then
 * Excel (CSV, the accountant's copy — the same text as the phone's) or PDF
 * through Windows' print dialog.
 */
@Composable
fun ReportPane(dao: KhataDao, words: Words, rtl: Boolean, shopName: String?, reload: Int) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf<Long?>(null) }
    var till by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true: start, false: end
    var entries by remember { mutableStateOf<List<EntryWithParty>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    val dayMs = 24L * 60 * 60 * 1000
    // "Till" includes the whole of its day.
    val tillEnd = till?.let { it + dayMs - 1 }
    val start = from
    val invalid = start != null && tillEnd != null && tillEnd < start

    LaunchedEffect(from, till, reload) {
        if (!invalid) entries = withContext(Dispatchers.IO) { dao.entriesInRange(from ?: 0L, tillEnd ?: Long.MAX_VALUE) }
    }
    val period = when {
        from == null && till == null -> words.text(W.range_all)
        else -> listOfNotNull(
            from?.let { words.text(W.ledger_report_from, Format.dateOnly(it)) },
            till?.let { words.text(W.ledger_report_till, Format.dateOnly(it)) }
        ).joinToString("  ")
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PaneTitle(word(W.ledger_report_title), period) }
            FillButton(word(W.export_excel), c.brandGreen, enabled = !invalid && entries.isNotEmpty()) {
                val dialog = FileDialog(null as Frame?, words.text(W.export_excel), FileDialog.SAVE)
                dialog.file = "RoshanKhata_Report_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH).format(Date()) + ".csv"
                dialog.isVisible = true
                val name = dialog.file ?: return@FillButton
                val file = File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
                val rows = entries
                scope.launch {
                    notice = runCatching {
                        withContext(Dispatchers.IO) {
                            val headers = listOf(W.date_label, W.name, W.note_label, W.i_gave, W.i_got).map { words.text(it) }
                            file.writeText(LedgerCsv.text(rows, headers), Charsets.UTF_8)
                        }
                        words.text(W.report_saved_to, file.absolutePath)
                    }.getOrElse { words.text(W.ledger_report_failed) }
                }
            }
            Spacer(Modifier.width(10.dp))
            FillButton(word(W.ledger_pdf_button), c.gaveFill, enabled = !invalid && entries.isNotEmpty()) {
                StatementPrint.show(
                    ReportPrint(words, rtl, shopName ?: words.text(W.app_name), period, entries),
                    words.text(W.ledger_report_title)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Each button wears the date it holds, so the window is read off the controls.
            OutlinedButton({ picking = true }) {
                Text(from?.let { Format.dateOnly(it) } ?: word(W.report_start_date), color = c.ink)
            }
            Text("–", color = c.textMuted)
            OutlinedButton({ picking = false }) {
                Text(till?.let { Format.dateOnly(it) } ?: word(W.report_end_date), color = c.ink)
            }
            TextButton({ from = null; till = null }) { Text(word(W.range_all), color = c.brandGreenText) }
        }
        if (invalid) {
            Text(word(W.invalid_range), color = c.owedToMe, fontSize = 14.sp)
        } else {
            val gave = entries.filter { it.isGiven }.sumOf { it.amount }
            val got = entries.filter { !it.isGiven }.sumOf { it.amount }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TotalCard(word(W.i_gave), Format.money(gave), c.owedToMe, Modifier.weight(1f))
                TotalCard(word(W.i_got), Format.money(got), c.iOwe, Modifier.weight(1f))
            }
            Text(plural(WP.pdf_stmt_entries, entries.size, entries.size), color = c.textMuted, fontSize = 13.sp)
            if (entries.isEmpty()) {
                Text(word(W.ledger_report_empty), color = c.textMuted, fontSize = 15.sp)
            } else {
                // Newest first on screen, for glancing; the files go oldest first, for working.
                LazyColumn(Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(14.dp))) {
                    items(entries.sortedByDescending { it.timestamp }, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(Format.dateTime(e.timestamp), Modifier.width(150.dp), color = c.textMuted, fontSize = 13.sp)
                            Column(Modifier.weight(1f)) {
                                Text(Format.name(e.partyName), color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                val sub = listOfNotNull(e.entryNumber.takeIf { it.isNotBlank() }, e.note?.takeIf { it.isNotBlank() })
                                if (sub.isNotEmpty()) {
                                    Text(sub.joinToString("  ·  "), color = c.inkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(
                                if (e.isGiven) Format.money(e.amount) else "",
                                Modifier.width(130.dp), color = c.owedToMe, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (!e.isGiven) Format.money(e.amount) else "",
                                Modifier.width(130.dp), color = c.iOwe, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = c.pageBg)
                    }
                }
            }
        }
    }

    picking?.let { start ->
        DayPicker(
            initial = if (start) from else till,
            onPick = { day -> if (start) from = day else till = day; picking = null },
            onDismiss = { picking = null }
        )
    }
    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { notice = null },
            confirmButton = { TextButton({ notice = null }) { Text(word(W.ok)) } },
            text = { Text(text) }
        )
    }
}
