package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.DateWords
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
 * A report's windows, the phone's chips (Marhala 4, part 7): today and yesterday whole, this week and this
 * month up to now (the shop's own week start), all, or days the owner picks. Each page offers its own list:
 * the Khata Report all of them but yesterday, the register no "all" (the phone's: a register is a fact about
 * a period).
 */
internal enum class ReportWindow(val word: String) {
    TODAY(W.range_today), YESTERDAY(W.range_yesterday), WEEK(W.range_this_week), MONTH(W.range_this_month),
    ALL(W.range_all), CUSTOM(W.range_custom);

    /** Its first and last moments now (an open end is null); null for all and for the owner's days. */
    fun window(now: Long = System.currentTimeMillis()): Pair<Long, Long?>? = when (this) {
        TODAY -> PartyWhen.TODAY.window(now)?.let { it.first to it.last }
        YESTERDAY -> PartyWhen.TODAY.window(now)?.let { dayBefore(it.first) to it.first - 1 }
        WEEK -> PartyWhen.THIS_WEEK.window(now)?.let { it.first to null }
        MONTH -> PartyWhen.THIS_MONTH.window(now)?.let { it.first to null }
        ALL, CUSTOM -> null
    }
}

/** The next day's first moment after the day [start] begins (by the calendar: a clock change makes a day 23 or 25 hours). */
internal fun dayAfter(start: Long): Long = java.util.Calendar.getInstance().apply { timeInMillis = start; add(java.util.Calendar.DAY_OF_YEAR, 1) }.timeInMillis

/** The day before's first moment, by the calendar. */
internal fun dayBefore(start: Long): Long = java.util.Calendar.getInstance().apply { timeInMillis = start; add(java.util.Calendar.DAY_OF_YEAR, -1) }.timeInMillis

/**
 * The owner's own days, asked as the phone asks them: the first day, then the last (its whole day). Leaving the
 * second keeps "from that day on" ([onWindow] with no end); a last day before the first is refused
 * ([onRefused]) and the window stays as it was. Leaving the first changes nothing.
 */
@Composable
internal fun TwoDayPicker(from: Long?, till: Long?, onWindow: (first: Long, last: Long?) -> Unit, onRefused: () -> Unit, onClose: () -> Unit) {
    var first by remember { mutableStateOf<Long?>(null) }
    val start = first
    if (start == null) {
        DayPicker(initial = from, title = word(W.pick_start_date), onPick = { first = it }, onDismiss = onClose)
    } else {
        DayPicker(
            initial = till ?: start, title = word(W.pick_end_date),
            onPick = { day ->
                val end = dayAfter(day) - 1
                if (end < start) onRefused() else onWindow(start, end)
                onClose()
            },
            onDismiss = { onWindow(start, null); onClose() }
        )
    }
}

/** The Khata Report's chips, the phone's: no "yesterday". */
private val KHATA_PERIODS = listOf(ReportWindow.TODAY, ReportWindow.WEEK, ReportWindow.MONTH, ReportWindow.ALL, ReportWindow.CUSTOM)

/**
 * The Khata Report on the computer (KMP Phase 7e; Marhala 4, part 7 — the phone's 24 Oct redesign, the owner:
 * "local si, generic si"): every entry across every party in a window chosen by one-tap chips (Today, This
 * week, This month, All, Custom — the first day, then the last; one bound alone is a window), opening on this
 * month; a summary card (the window, how many entries and parties, gave against got as a share bar, the two
 * totals); the entries grouped by day under a heading with that day's count. Deliberately no all-parties net,
 * for the window or a day: one man's sale and another man's payment belong to nobody's account. Then the PDF
 * (the preview, to save or print) or Excel (CSV, the accountant's copy, the phone's text).
 */
@Composable
fun ReportPane(dao: KhataDao, words: Words, rtl: Boolean, shopName: String?, reload: Int, parties: List<com.innovation313.roshankhata.data.PartyWithBalance> = emptyList()) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(ReportWindow.MONTH) }
    // The window: [from] its first moment, [till] its last (inclusive); null is open on that side.
    var from by remember { mutableStateOf(ReportWindow.MONTH.window()?.first) }
    var till by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<EntryWithParty>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var refused by remember { mutableStateOf(false) }

    LaunchedEffect(from, till, reload) {
        entries = withContext(Dispatchers.IO) { dao.entriesInRange(from ?: 0L, till ?: Long.MAX_VALUE) }
    }
    fun choose(p: ReportWindow) {
        when (p) {
            ReportWindow.CUSTOM -> { picking = true; return }
            ReportWindow.ALL -> { from = null; till = null }
            else -> p.window()?.let { (s, e) -> from = s; till = e }
        }
        period = p
    }
    // Asked each time (DateWords keeps one per thread): the language and calendar in use now are the ones printed.
    val dayFmt = DateWords.formatter("d MMM yyyy")
    // The pill's words: the window's two days, an open end read as today (the phone's windowLabel).
    val windowLabel = run {
        val s = from
        val e = till
        when {
            s == null && e == null -> word(W.range_label_all)
            s == null -> word(W.ledger_report_till, dayFmt.format(Date(e!!)))
            else -> {
                val first = dayFmt.format(Date(s))
                val last = dayFmt.format(Date(e ?: System.currentTimeMillis()))
                if (first == last) first else "$first – $last"
            }
        }
    }
    // What the PDF and CSV call this window (the phone's rangeLabel).
    val rangeLabel = run {
        val s = from
        val e = till
        when {
            s == null && e == null -> words.text(W.range_all)
            s != null && e != null -> dayFmt.format(Date(s)) + " – " + dayFmt.format(Date(e))
            s != null -> words.text(W.ledger_report_from, dayFmt.format(Date(s)))
            else -> words.text(W.ledger_report_till, dayFmt.format(Date(e!!)))
        }
    }

    // Every party's entries leave the book: EXPORT asked and the audit log told, as the Khata page's
    // own export does (25 Oct 2026, the audit: the page needed only REPORTS, so a cashier let in by a
    // manager's PIN could carry the whole book's entries off).
    fun exportCsv() = Gates.need(com.innovation313.roshankhata.data.Access.Permission.EXPORT) export@{
        val dialog = FileDialog(null as Frame?, words.text(W.export_excel), FileDialog.SAVE)
        startIn(dialog, "Documents")
        dialog.file = "RoshanKhata_Report_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH).format(Date()) + ".csv"
        dialog.isVisible = true
        val name = dialog.file ?: return@export
        val file = File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
        val rows = entries
        scope.launch {
            notice = runCatching {
                withContext(Dispatchers.IO) {
                    val headers = listOf(W.date_label, W.name, W.note_label, W.i_gave, W.i_got).map { words.text(it) }
                    file.writeText(LedgerCsv.text(rows, headers), Charsets.UTF_8)
                    runCatching { dao.log(com.innovation313.roshankhata.data.AuditAction.EXPORT, com.innovation313.roshankhata.data.Access.userName, detail = file.name) }
                }
                words.text(W.report_saved_to, file.absolutePath)
            }.getOrElse { words.text(W.ledger_report_failed) }
        }
    }
    val ready = entries.isNotEmpty()
    FluentPage(word(W.ledger_report_title), windowLabel, scroll = false, actions = {
        FluentButton(word(W.export_excel), { exportCsv() }, icon = "ic_download", enabled = ready)
        FluentButton(word(W.pdf_preview), {
            StatementPrint.show(ReportPrint(words, rtl, shopName ?: words.text(W.app_name), rangeLabel, entries), words.text(W.ledger_report_title))
        }, kind = ButtonKind.ACCENT, icon = "ic_pdf_doc", enabled = ready)
    }) {
        PillRow {
            KHATA_PERIODS.forEach { p -> FluentPill(word(p.word), p == period) { choose(p) } }
        }
        val gave = entries.filter { it.isGiven }.sumOf { it.amount }
        val got = entries.filter { !it.isGiven }.sumOf { it.amount }
        // The summary: the window (a click picks days), how many entries and parties, gave against got, the totals.
        Column(Modifier.fillMaxWidth().card().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Look.accentTint).press(RoundedCornerShape(16.dp)) { picking = true }.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Glyph("ic_calendar", Look.accent, 14)
                    Text(windowLabel, color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                Spacer(Modifier.weight(1f))
                val ids = entries.mapTo(HashSet()) { it.partyId }
                val suppliers = parties.filter { it.id in ids && !it.isCustomer }.size
                val customers = ids.size - suppliers
                Text(
                    plural(WP.pdf_stmt_entries, entries.size, entries.size) + " · " + plural(WP.customer_count, customers, customers) +
                        (if (suppliers > 0) " · " + plural(WP.supplier_count, suppliers, suppliers) else ""),
                    color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // Each side's share of the window's money; with no money there is nothing to share out.
            val total = gave + got
            if (total > 0.0) {
                Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Look.subtle)) {
                    if (gave > 0.0) Box(Modifier.weight((gave / total).toFloat()).fillMaxHeight().background(c.owedToMe))
                    if (got > 0.0) Box(Modifier.weight((got / total).toFloat()).fillMaxHeight().background(Look.accent))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(word(W.i_gave), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(Format.money(gave), color = c.owedToMe, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(word(W.i_got), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(Format.money(got), color = Look.accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
        }
        if (entries.isEmpty()) {
            EmptyState("ic_list_all", word(W.ledger_report_empty), null, Modifier.weight(1f))
        } else ListCard(Modifier.weight(1f)) {
            // Newest first on screen, for glancing; the files go oldest first, for working. Grouped by day.
            val byDay = remember(entries) {
                entries.sortedByDescending { it.timestamp }.groupBy { e ->
                    java.util.Calendar.getInstance().apply {
                        timeInMillis = e.timestamp; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }.toList()
            }
            val today = remember(entries) { PartyWhen.TODAY.window()?.first ?: 0L }
            val yesterday = dayBefore(today)
            val timeFmt = DateWords.formatter("h:mm a")
            LazyColumn(Modifier.fillMaxSize()) {
                byDay.forEach { (day, rows) ->
                    item(key = "d$day") {
                        Row(
                            Modifier.fillMaxWidth().background(Look.layerAlt).padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val name = when (day) {
                                today -> word(W.range_today)
                                yesterday -> word(W.range_yesterday)
                                else -> null
                            }
                            Text(listOfNotNull(name, dayFmt.format(Date(day))).joinToString(" · "), Modifier.weight(1f), color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(plural(WP.pdf_stmt_entries, rows.size, rows.size), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
                        }
                        Rule()
                    }
                    items(rows, key = { it.id }) { e ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Avatar(e.partyName, 34, id = e.partyId)
                            Column(Modifier.weight(1f)) {
                                Text(Format.name(e.partyName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOfNotNull(timeFmt.format(Date(e.timestamp)), e.entryNumber.takeIf { it.isNotBlank() }?.let { Format.ltr(it) }, e.note?.takeIf { it.isNotBlank() })
                                Text(sub.joinToString(" · "), color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Format.money(e.amount), color = if (e.isGiven) c.owedToMe else Look.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                if (e.isGiven) Tag(word(W.i_gave), c.owedToMe, Look.getTint) else Tag(word(W.i_got), Look.accent, Look.accentTint)
                            }
                        }
                        Rule()
                    }
                }
            }
        }
    }

    if (picking) TwoDayPicker(from, till, onWindow = { s, e -> from = s; till = e; period = ReportWindow.CUSTOM }, onRefused = { refused = true }, onClose = { picking = false })
    if (refused) TellDialog(word(W.ledger_report_title), word(W.invalid_range), { refused = false })
    notice?.let { text -> TellDialog(word(W.export_excel), text, { notice = null }) }
}
