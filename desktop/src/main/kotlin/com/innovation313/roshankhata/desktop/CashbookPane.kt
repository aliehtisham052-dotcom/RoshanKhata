package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.audit
import com.innovation313.roshankhata.data.auditAmount
import com.innovation313.roshankhata.data.AuditAction as AuditActs

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.CashEntry
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Cashbook on the computer (KMP Phase 7b): money in and out of the
 * business with no customer attached — rent, wages, a walk-in sale. A
 * separate table from the ledger, as on the phone, so it never changes what
 * anyone owes.
 */
@Composable
fun CashbookPane(dao: KhataDao) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val entries by remember { dao.observeCashEntries() }.collectAsState(emptyList())
    val income by remember { dao.observeCashIncome() }.collectAsState(0.0)
    val expense by remember { dao.observeCashExpense() }.collectAsState(0.0)
    var adding by remember { mutableStateOf<Boolean?>(null) }
    var deleting by remember { mutableStateOf<CashEntry?>(null) }
    var closing by remember { mutableStateOf(false) }
    if (closing) DayCloseDialog(dao) { closing = false }
    var reporting by remember { mutableStateOf(false) }
    if (reporting) ZReportDialog(dao) { reporting = false }

    val last by remember { dao.observeLastDayClose() }.collectAsState(null)
    FluentPage(
        word(W.cashbook),
        last?.let { word(W.dayclose_last, Format.dateOnly(it.closedAt), Format.signedTotal(it.difference)) } ?: word(W.cashbook_subtitle),
        scroll = false,
        actions = {
            FluentButton(word(W.z_title), { reporting = true }, kind = ButtonKind.STANDARD, icon = "ic_print")
            FluentButton(word(W.dayclose_button), { closing = true }, icon = "ic_check_plain")
            FluentButton(word(W.cash_expense), { adding = false }, icon = "ic_arrow_gave", kind = ButtonKind.DANGER)
            FluentButton(word(W.cash_income), { adding = true }, icon = "ic_arrow_got", kind = ButtonKind.ACCENT)
        }
    ) {
        val net = income - expense
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FigureCard(word(W.cash_in), Format.money(income), Look.accent, Modifier.weight(1f), icon = "ic_arrow_got", tint = Look.accentTint)
            FigureCard(word(W.cash_out), Format.money(expense), c.owedToMe, Modifier.weight(1f), icon = "ic_arrow_gave", tint = Look.getTint)
            // With its sign (21 Oct 2026): more out than in printed as a plain, positive figure.
            FigureCard(word(W.cash_net), Format.signedTotal(net), if (net < 0) c.owedToMe else c.ink, Modifier.weight(1f), icon = "ic_tile_cashbook", tint = Look.subtle)
        }
        if (entries.isEmpty()) {
            EmptyState("ic_tile_cashbook", word(W.no_cash_entries), word(W.cashbook_separate_note), Modifier.weight(1f)) {
                FluentButton(word(W.cash_income), { adding = true }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
            }
        } else ListCard(Modifier.weight(1f)) {
            ListHead(word(W.cashbook), entries.size.toString())
            TableHead(listOf(word(W.date_label) to 150, word(W.cash_category_hint) to 0, word(W.amount) to 120, "" to 32), endAligned = setOf(2))
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(Format.dateTime(e.timestamp), Modifier.width(150.dp), color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Column(Modifier.weight(1f)) {
                            Text(e.category, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            e.note?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                        }
                        // A sign as well as a colour: colour alone fails anyone who cannot tell red from green.
                        Text(
                            (if (e.isIncome) "+ " else "− ") + Format.money(e.amount),
                            Modifier.width(120.dp),
                            color = if (e.isIncome) Look.accent else c.owedToMe,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, softWrap = false
                        )
                        IconAction("ic_close", word(W.delete), { Gates.edit { deleting = e } }, Modifier.width(32.dp))
                    }
                    Rule()
                }
            }
        }
    }

    adding?.let { way ->
        DialogWindow(
            onCloseRequest = { adding = null },
            onPreviewKeyEvent = escCloses { adding = null },
            title = word(if (way) W.cash_income else W.cash_expense),
            state = rememberDialogState(width = fitW(480.dp), height = fitH(520.dp))
        ) {
            Box(Modifier.fillMaxSize()) {
                CashForm(
                    dao = dao,
                    income = way,
                    onSave = { entry ->
                        scope.launch {
                            withContext(Dispatchers.IO) { val id = dao.insertCashEntry(entry); dao.audit(AuditActs.CASH_ADDED, "cashbook", id, auditAmount(entry.amount)) }
                            adding = null
                        }
                    },
                    onCancel = { adding = null }
                )
            }
        }
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(word(W.delete_cash_entry)) },
            text = { Text(word(W.delete_cash_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch { withContext(Dispatchers.IO) { dao.softDeleteCashEntry(e.id); dao.audit(AuditActs.CASH_DELETED, "cashbook", e.id, auditAmount(e.amount)) } }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.delete)) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(word(W.cancel)) } }
        )
    }
}

/** Money in or out: an amount (sums allowed, as on the phone's pad), a category, a note. */
@Composable
private fun CashForm(dao: KhataDao, income: Boolean, onSave: (CashEntry) -> Unit, onCancel: () -> Unit) {
    val c = LocalRoshanColors.current
    var isIncome by remember { mutableStateOf(income) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var used by remember { mutableStateOf<List<String>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { used = withContext(Dispatchers.IO) { dao.cashCategories() } }
    val amount = Calc.evalAmount(amountText)
    val needAmount = word(W.enter_valid_amount)
    val needCategory = word(W.enter_category)

    DialogFrame(
        word(if (isIncome) W.cash_income else W.cash_expense), word(W.cashbook),
        onClose = onCancel,
        footerStart = { problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) } },
        footer = {
            FluentButton(word(W.cancel), onCancel)
            FluentButton(word(W.save), kind = ButtonKind.ACCENT, icon = "ic_check_plain", onClick = {
                when {
                    amount == null -> problem = needAmount
                    category.isBlank() -> problem = needCategory
                    else -> onSave(
                        CashEntry(
                            amount = amount,
                            isIncome = isIncome,
                            category = category.trim(),
                            note = note.trim().ifEmpty { null }
                        )
                    )
                }
            })
        }
    ) {
        TwoWay(word(W.cash_income), word(W.cash_expense), isIncome) { isIncome = it }
        FluentField(amountText, { amountText = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.amount), prefix = com.innovation313.roshankhata.data.Currency.symbol, big = true, help = amount?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
        FluentField(category, { category = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.cash_category_hint))
        // The owner's own earlier categories, one click each, so they need not retype them.
        if (used.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(used) { u ->
                    FluentPill(u, u == category) { category = u; problem = null }
                }
            }
        }
        FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.note_optional), singleLine = false)
    }
}

/**
 * Close the day (galla), the phone's day close: since the last count, the
 * cashbook's money in and out plus khata money marked Cash, what should be
 * in the drawer, and what was counted — over, short or matching. The last
 * count's figure is the next opening.
 */
@Composable
fun DayCloseDialog(dao: KhataDao, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var figures by remember { mutableStateOf<DayFigures?>(null) }
    var opening by remember { mutableStateOf("") }
    var counted by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val needCount = word(W.dayclose_need_count)
    val needReason = word(W.z_variance_reason)
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val (fig, lastCounted) = withContext(Dispatchers.IO) {
            val last = dao.lastDayClose()
            val now = System.currentTimeMillis()
            val from = last?.closedAt ?: 0L
            DayFigures(
                now = now,
                cbIn = dao.cashbookCashBetween(true, from, now),
                cbOut = dao.cashbookCashBetween(false, from, now),
                kIn = dao.khataCashBetween(false, from, now),
                kOut = dao.khataCashBetween(true, from, now)
            ) to last?.counted
        }
        // The last count is this stretch's opening, as on the phone.
        lastCounted?.let { opening = Calc.trim(it) }
        figures = fig
    }
    val f = figures ?: return
    val open = Calc.evalPad(opening) ?: 0.0
    val expected = com.innovation313.roshankhata.data.DayCloseMath.expected(open, f.cbIn + f.kIn, f.cbOut + f.kOut)
    val count = Calc.evalPad(counted)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(word(W.dayclose_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FluentField(opening, { opening = it }, Modifier.fillMaxWidth(), label = word(W.dayclose_opening), calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                Text(word(W.dayclose_breakdown, Format.money(f.cbIn), Format.money(f.kIn), Format.money(f.cbOut), Format.money(f.kOut), Format.money(expected)),
                    color = c.inkSoft, fontSize = 13.sp)
                Text(word(W.dayclose_help), color = c.textMuted, fontSize = 12.sp)
                FluentField(counted, { counted = it; problem = null }, Modifier.fillMaxWidth(), label = word(W.dayclose_counted), big = true, calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
                if (count != null) {
                    val d = com.innovation313.roshankhata.data.DayCloseMath.difference(count, expected)
                    Text(
                        when {
                            com.innovation313.roshankhata.data.Money.isPositive(d) -> word(W.dayclose_over, Format.money(d))
                            com.innovation313.roshankhata.data.Money.isPositive(-d) -> word(W.dayclose_short, Format.money(-d))
                            else -> word(W.dayclose_match)
                        },
                        color = if (com.innovation313.roshankhata.data.Money.isPositive(-d)) c.owedToMe else c.iOwe, fontWeight = FontWeight.Bold
                    )
                }
                FluentField(note, { note = it }, Modifier.fillMaxWidth(), placeholder = word(W.dayclose_note_hint))
                problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton({
                val n = count ?: run { problem = needCount; return@TextButton }
                // A drawer that does not match the books needs its reason written down (POS Phase 4).
                if (kotlin.math.abs(com.innovation313.roshankhata.data.DayCloseMath.difference(n, expected)) >= 0.005 && note.isBlank()) {
                    problem = needReason; return@TextButton
                }
                val close = com.innovation313.roshankhata.data.DayClose(
                    day = Math.floorDiv(f.now + java.util.TimeZone.getDefault().getOffset(f.now), 24L * 60 * 60 * 1000),
                    opening = open,
                    cashIn = com.innovation313.roshankhata.data.LineMath.round(f.cbIn + f.kIn),
                    cashOut = com.innovation313.roshankhata.data.LineMath.round(f.cbOut + f.kOut),
                    counted = n,
                    note = note.trim().ifEmpty { null },
                    closedAt = f.now
                )
                scope.launch { withContext(Dispatchers.IO) { dao.saveDayClose(close); dao.audit(AuditActs.DAY_CLOSED, "day_close", null, auditAmount(close.counted)) } }
                onClose()
            }) { Text(word(W.save)) }
        },
        dismissButton = { FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD) }
    )
}

private class DayFigures(val now: Long, val cbIn: Double, val cbOut: Double, val kIn: Double, val kOut: Double)

/**
 * The day's figures (POS Phase 4, the Z report): a day picked with ‹ ›,
 * every line summed from the rows (ZReport), printed as an A4 sheet through
 * the app's preview or as a thermal receipt.
 */
@Composable
fun ZReportDialog(dao: KhataDao, onReceipt: ((List<ThermalReceipt.Line>) -> Unit)? = null, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val words = com.innovation313.roshankhata.words.LocalWords.current
    var day by remember { mutableStateOf(com.innovation313.roshankhata.data.DayCloseMath.dayOf(System.currentTimeMillis())) }
    var report by remember { mutableStateOf<com.innovation313.roshankhata.data.ZReport?>(null) }
    LaunchedEffect(day) {
        val from = com.innovation313.roshankhata.data.DayCloseMath.startOf(day)
        report = withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.ZReport.compute(dao, from, from + 24L * 60 * 60 * 1000) }.getOrNull() }
    }
    val title = word(W.z_title)
    val dateLine = Format.dateOnly(com.innovation313.roshankhata.data.DayCloseMath.startOf(day) + 12L * 60 * 60 * 1000)
    DialogWindow(onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose), title = title, state = rememberDialogState(width = fitW(520.dp), height = fitH(640.dp))) {
        DialogFrame(title, subtitle = dateLine, onClose = onClose, footerStart = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconAction("ic_chevron_right", "‹", { day -= 1 })
                IconAction("ic_chevron_right", "›", { if (day < com.innovation313.roshankhata.data.DayCloseMath.dayOf(System.currentTimeMillis())) day += 1 })
            }
        }, footer = {
            val r = report
            if (onReceipt != null && r != null) {
                FluentButton(word(W.print_receipt), {
                    onReceipt(listOf(ThermalReceipt.Line(title, 24f, bold = true, centre = true), ThermalReceipt.Line(dateLine, 18f, centre = true), ThermalReceipt.Line("")) +
                        r.lines(words) { Format.money(it) }.map { (k, v) -> ThermalReceipt.Line("$k: $v", 18f) })
                }, kind = ButtonKind.STANDARD)
            }
            FluentButton(word(W.pdf_preview), {
                val r2 = report ?: return@FluentButton
                StatementPrint.show(LinesPrint(title, dateLine, r2.lines(words) { Format.money(it) }), title)
            }, kind = ButtonKind.ACCENT, icon = "ic_print", enabled = report != null)
        }) {
            val r = report
            if (r == null) Text("…", color = c.textMuted)
            else {
                r.lines(words) { Format.money(it) }.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, Modifier.weight(1f), color = if (k.startsWith("  ")) c.inkSoft else c.ink, fontSize = 14.sp)
                        Text(v, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                    Rule()
                }
                if (r.counted == null) Text(word(W.z_no_close), Modifier.padding(top = 8.dp), color = c.textMuted, fontSize = 12.sp)
            }
        }
    }
}

/**
 * A sheet of label: figure lines, A4, for a report with no design of its own (the day report).
 *
 * In the app's print face (21 Oct 2026, the nine-language review: it was Java's SANS_SERIF, which
 * stock Windows draws as boxes for Hindi and Bengali), mirrored for a right-to-left language (the
 * label on the reading side, the figure at the far end), and on as many pages as the lines need
 * (it stopped at the foot of the first page and dropped the rest).
 */
class LinesPrint(
    private val title: String,
    private val subtitle: String,
    private val lines: List<Pair<String, String>>,
    private val rtl: Boolean = com.innovation313.roshankhata.ui.BidiPort.isRtlLocale(java.util.Locale.getDefault())
) : java.awt.print.Printable {
    private val pageW = 595.0
    private val left = 60.0
    private val right = 535.0
    private val firstTop = 134.0
    private val step = 20.0
    private val bottom = 790.0
    private val perFirst = ((bottom - firstTop) / step).toInt() + 1
    private val perNext = ((bottom - 80.0) / step).toInt() + 1

    private fun pages(): Int = if (lines.size <= perFirst) 1 else 1 + (lines.size - perFirst + perNext - 1) / perNext

    /** [s] starting at [x] from the reading side, or ending there when [end]. */
    private fun java.awt.Graphics2D.text(s: String, x: Double, y: Double, end: Boolean = false) {
        val w = fontMetrics.stringWidth(s)
        val at = when {
            !rtl && !end -> x
            !rtl && end -> x - w
            rtl && !end -> pageW - x - w
            else -> pageW - x
        }
        drawString(s, at.toFloat(), y.toFloat())
    }

    override fun print(g: java.awt.Graphics, pf: java.awt.print.PageFormat, pageIndex: Int): Int {
        if (pageIndex >= pages()) return java.awt.print.Printable.NO_SUCH_PAGE
        val g2 = g as java.awt.Graphics2D
        g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.color = java.awt.Color.BLACK
        val face = PrintFonts.family
        var y: Double
        val from: Int
        val count: Int
        if (pageIndex == 0) {
            y = 80.0
            g2.font = java.awt.Font(face, java.awt.Font.BOLD, 20); g2.text(title, left, y); y += 24
            g2.font = java.awt.Font(face, java.awt.Font.PLAIN, 12); g2.text(subtitle, left, y)
            y = firstTop
            from = 0
            count = perFirst
        } else {
            y = 80.0
            from = perFirst + (pageIndex - 1) * perNext
            count = perNext
        }
        g2.font = java.awt.Font(face, java.awt.Font.PLAIN, 12)
        for ((k, v) in lines.subList(minOf(from, lines.size), minOf(lines.size, from + count))) {
            g2.text(k, left, y)
            g2.text(v, right, y, end = true)
            y += step
        }
        return java.awt.print.Printable.PAGE_EXISTS
    }
}
