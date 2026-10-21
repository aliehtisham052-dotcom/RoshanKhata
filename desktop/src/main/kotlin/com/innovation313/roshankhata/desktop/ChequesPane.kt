package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Cheque
import com.innovation313.roshankhata.data.ChequeStatus
import com.innovation313.roshankhata.data.ChequeWithParty
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Calc
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Cheques on the computer (KMP Phase 7c), the phone's rules: a cheque is not
 * money until it clears. Pending ones first, soonest due on top. Clearing
 * posts the payment to the party's ledger in one transaction
 * ([KhataDao.clearCheque]); bounced and cancelled post nothing and can be
 * put back to pending; only a pending cheque may be edited.
 */
@Composable
fun ChequesPane(dao: KhataDao, parties: List<PartyWithBalance>, words: Words, onLedgerChanged: () -> Unit) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    val cheques by remember { dao.observeCheques() }.collectAsState(emptyList())
    var form by remember { mutableStateOf<ChequeForm?>(null) }
    var acting by remember { mutableStateOf<ChequeWithParty?>(null) }
    var clearing by remember { mutableStateOf<ChequeWithParty?>(null) }
    var bouncing by remember { mutableStateOf<ChequeWithParty?>(null) }

    fun settle(ch: ChequeWithParty, status: Int?) {
        scope.launch {
            withContext(Dispatchers.IO) {
                dao.getCheque(ch.id)?.let { stored ->
                    dao.updateCheque(
                        if (status == null) stored.copy(status = ChequeStatus.PENDING, settledAt = null)
                        else stored.copy(status = status, settledAt = System.currentTimeMillis())
                    )
                }
            }
        }
    }

    val pending = cheques.count { it.status == ChequeStatus.PENDING }
    val summary = when {
        cheques.isEmpty() -> word(W.due_summary_empty)
        pending == 0 -> word(W.due_summary_all_settled)
        else -> word(W.due_summary_pending, pending)
    }
    // The Figma screen's three pills: pending, cleared, bounced (cancelled rides with bounced).
    var shownStatus by remember { mutableStateOf(ChequeStatus.PENDING) }
    val shown = cheques.filter { ch ->
        when (shownStatus) {
            ChequeStatus.PENDING -> ch.status == ChequeStatus.PENDING
            ChequeStatus.CLEARED -> ch.status == ChequeStatus.CLEARED
            else -> ch.status == ChequeStatus.BOUNCED || ch.status == ChequeStatus.CANCELLED
        }
    }
    FluentPage(word(W.cheques), summary, scroll = false, actions = {
        FluentButton(word(W.add_cheque), { form = ChequeForm(null) }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentPill(word(W.cheque_pending) + " · " + pending, shownStatus == ChequeStatus.PENDING) { shownStatus = ChequeStatus.PENDING }
            FluentPill(word(W.cheque_cleared), shownStatus == ChequeStatus.CLEARED) { shownStatus = ChequeStatus.CLEARED }
            FluentPill(word(W.cheque_bounced), shownStatus == ChequeStatus.BOUNCED) { shownStatus = ChequeStatus.BOUNCED }
        }
        if (cheques.isEmpty()) {
            EmptyState("ic_tile_cheques", word(W.no_cheques), word(W.cheque_not_money_yet), Modifier.weight(1f)) {
                FluentButton(word(W.add_cheque), { form = ChequeForm(null) }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
            }
        } else if (shown.isEmpty()) {
            EmptyState("ic_tile_cheques", word(W.due_summary_all_settled), null, Modifier.weight(1f))
        } else ListCard(Modifier.weight(1f)) {
            TableHead(listOf(word(W.customer) to 0, word(W.cheque_number_hint).substringBefore("(").trim() to 160, word(W.date_label) to 170, word(W.amount) to 120), endAligned = setOf(3))
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, it -> it.id }) { i, ch -> ChequeRow(ch, i) { acting = ch } }
            }
        }
    }

    acting?.let { ch ->
        val pending = ch.status == ChequeStatus.PENDING
        val canReopen = ch.status == ChequeStatus.BOUNCED || ch.status == ChequeStatus.CANCELLED
        AlertDialog(
            onDismissRequest = { acting = null },
            title = { Text(if (pending) word(W.cheque_actions) else Format.name(ch.partyName)) },
            text = {
                Column {
                    val Action: @Composable (String, Color, () -> Unit) -> Unit = { text, color, onClick ->
                        ActionRow(text, color) { acting = null; onClick() }
                    }
                    if (pending) {
                        // Edit only while pending: once cleared, the amount is also a ledger entry.
                        Action(word(W.edit_cheque), c.ink) { Gates.edit { form = ChequeForm(ch) } }
                        Action(word(W.mark_cleared), c.iOwe) { clearing = ch }
                        Action(word(W.mark_bounced), c.owedToMe) { bouncing = ch }
                        Action(word(W.mark_cancelled), c.ink) { settle(ch, ChequeStatus.CANCELLED) }
                    } else if (canReopen) {
                        // Bounced and cancelled never posted anything: back to pending moves no money.
                        Action(word(W.cheque_reopen), c.ink) { settle(ch, null) }
                    }
                    Action(word(W.delete_cheque), c.owedToMe) {
                        Gates.edit { scope.launch { withContext(Dispatchers.IO) { dao.softDeleteCheque(ch.id) } } }
                    }
                }
            },
            confirmButton = { TextButton({ acting = null }) { Text(word(W.cancel)) } }
        )
    }
    clearing?.let { ch ->
        AlertDialog(
            onDismissRequest = { clearing = null },
            title = { Text(word(W.mark_cleared)) },
            text = { Text(word(W.cleared_confirm, ch.partyName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearing = null
                        val note = buildString {
                            append(words.text(W.cheque_note_prefix, ch.chequeNumber?.takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty()).trim())
                            ch.bankName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        }
                        scope.launch {
                            withContext(Dispatchers.IO) { dao.clearCheque(ch.id, note) }
                            onLedgerChanged()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.iOwe)
                ) { Text(word(W.mark_cleared)) }
            },
            dismissButton = { TextButton({ clearing = null }) { Text(word(W.cancel)) } }
        )
    }
    bouncing?.let { ch ->
        AlertDialog(
            onDismissRequest = { bouncing = null },
            title = { Text(word(W.mark_bounced)) },
            text = { Text(word(W.bounced_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = { bouncing = null; settle(ch, ChequeStatus.BOUNCED) },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.mark_bounced)) }
            },
            dismissButton = { TextButton({ bouncing = null }) { Text(word(W.cancel)) } }
        )
    }
    form?.let { f ->
        DialogWindow(
            onCloseRequest = { form = null },
            onPreviewKeyEvent = escCloses { form = null },
            title = word(if (f.existing == null) W.add_cheque else W.edit_cheque),
            state = rememberDialogState(width = fitW(620.dp), height = fitH(640.dp))
        ) {
            Box(Modifier.fillMaxSize()) {
                ChequeEditor(
                    existing = f.existing,
                    parties = parties,
                    onSave = { cheque ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val old = f.existing
                                if (old == null) {
                                    dao.insertCheque(cheque)
                                } else {
                                    // copy() off the stored row: status, settledAt, ledgerEntryId and
                                    // createdAt survive; only what the form shows may change.
                                    dao.getCheque(old.id)?.let { stored ->
                                        dao.updateCheque(
                                            stored.copy(
                                                partyId = cheque.partyId,
                                                amount = cheque.amount,
                                                isReceived = cheque.isReceived,
                                                chequeNumber = cheque.chequeNumber,
                                                bankName = cheque.bankName,
                                                dueDate = cheque.dueDate,
                                                note = cheque.note
                                            )
                                        )
                                    }
                                }
                            }
                            form = null
                        }
                    },
                    onCancel = { form = null }
                )
            }
        }
    }
}

@Composable
private fun ActionRow(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth().press(onClick = onClick).padding(vertical = 12.dp),
        color = color, fontSize = 16.sp
    )
}

/** The form's subject: a new cheque (null) or one being edited. */
private class ChequeForm(val existing: ChequeWithParty?)

@Composable
private fun ChequeRow(ch: ChequeWithParty, index: Int, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    val pending = ch.status == ChequeStatus.PENDING
    val (statusWord, statusColour, statusTint) = when (ch.status) {
        ChequeStatus.PENDING -> Triple(W.cheque_pending, Look.warn, Look.warnTint)
        ChequeStatus.CLEARED -> Triple(W.cheque_cleared, Look.accent, Look.accentTint)
        ChequeStatus.BOUNCED -> Triple(W.cheque_bounced, c.owedToMe, Look.getTint)
        else -> Triple(W.cheque_cancelled, c.textMuted, Look.subtle)
    }
    // Settled cheques stay as a record, faded: history, not something to act on.
    Row(
        Modifier.fillMaxWidth().alpha(if (pending) 1f else 0.7f).press(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(ch.partyName, 32, index)
            Column {
                Text(Format.name(ch.partyName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(word(if (ch.isReceived) W.cheque_received else W.cheque_issued), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
        Column(Modifier.width(160.dp)) {
            Text(ch.chequeNumber?.takeIf { it.isNotBlank() }?.let { "#$it" } ?: "—", color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            ch.bankName?.takeIf { it.isNotBlank() }?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
        Column(Modifier.width(170.dp)) {
            Text(Format.dateOnly(ch.dueDate), color = c.ink, fontSize = 13.sp, maxLines = 1)
            if (pending) {
                // The due date is what sends the owner to the bank today.
                val days = daysUntil(ch.dueDate)
                Text(
                    when {
                        days == 0 -> word(W.due_today)
                        days > 0 -> plural(WP.due_in_days, days, days)
                        else -> plural(WP.overdue_by_days, -days, -days)
                    },
                    color = if (days <= 0) c.owedToMe else c.textMuted, fontSize = 12.sp, fontWeight = if (days <= 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1
                )
            } else Tag(word(statusWord), statusColour, statusTint)
        }
        Text(
            Format.money(ch.amount), Modifier.width(120.dp),
            color = if (ch.isReceived) Look.accent else c.owedToMe,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, softWrap = false
        )
    }
    Rule()
}

/** Whole days from today to [due], negative once overdue. */
private fun daysUntil(due: Long): Int {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val day = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, day).toInt()
}

@Composable
private fun ChequeEditor(
    existing: ChequeWithParty?,
    parties: List<PartyWithBalance>,
    onSave: (Cheque) -> Unit,
    onCancel: () -> Unit
) {
    val c = LocalRoshanColors.current
    var partyId by remember { mutableStateOf(existing?.partyId) }
    var search by remember { mutableStateOf("") }
    var received by remember { mutableStateOf(existing?.isReceived ?: true) }
    var amountText by remember { mutableStateOf(existing?.amount?.let { Calc.trim(it) } ?: "") }
    var number by remember { mutableStateOf(existing?.chequeNumber.orEmpty()) }
    var bank by remember { mutableStateOf(existing?.bankName.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val amount = Calc.evalAmount(amountText)
    val needParty = word(W.select_party_first)
    val needAmount = word(W.enter_valid_amount)
    val needDate = word(W.pick_date_first)

    DialogFrame(
        word(if (existing == null) W.add_cheque else W.edit_cheque), word(W.cheque_not_money_yet),
        onClose = onCancel,
        footerStart = { problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) } },
        footer = {
            FluentButton(word(W.cancel), onCancel)
            FluentButton(word(W.save), kind = ButtonKind.ACCENT, icon = "ic_check_plain", onClick = {
                val pid = partyId
                val date = due
                when {
                    pid == null -> problem = needParty
                    amount == null -> problem = needAmount
                    date == null -> problem = needDate
                    else -> onSave(
                        Cheque(
                            partyId = pid,
                            amount = amount,
                            isReceived = received,
                            chequeNumber = number.trim().ifEmpty { null },
                            bankName = bank.trim().ifEmpty { null },
                            dueDate = date,
                            note = note.trim().ifEmpty { null }
                        )
                    )
                }
            })
        }
    ) {
        TwoWay(word(W.cheque_received), word(W.cheque_issued), received) { received = it }
        // Who: picked from the book, so the cheque lands on a real party.
        PartyPicker(parties, partyId, { partyId = it; problem = null }, word(W.customer), word(W.cheque_party_hint), Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            FluentField(amountText, { amountText = it; problem = null }, Modifier.weight(1f), label = word(W.amount), prefix = com.innovation313.roshankhata.data.Currency.symbol, big = true, help = amount?.let { Format.money(it) } ?: " ", calc = com.innovation313.roshankhata.screens.CalcKind.MONEY)
            DateBox(word(W.pick_due_date), due?.let { Format.dateOnly(it) }, word(W.pick_due_date), { picking = true }, Modifier.weight(1f), tall = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FluentField(bank, { bank = it }, Modifier.weight(1f), label = word(W.bank_name_hint))
            FluentField(number, { number = it }, Modifier.weight(1f), label = word(W.cheque_number_hint))
        }
        FluentField(note, { note = it }, Modifier.fillMaxWidth(), label = word(W.note_optional), singleLine = false)
    }

    if (picking) {
        DayPicker(due, onPick = { due = it; problem = null; picking = false }, onDismiss = { picking = false })
    }
}
