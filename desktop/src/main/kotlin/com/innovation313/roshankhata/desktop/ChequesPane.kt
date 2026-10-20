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

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val pending = cheques.count { it.status == ChequeStatus.PENDING }
            val summary = when {
                cheques.isEmpty() -> word(W.due_summary_empty)
                pending == 0 -> word(W.due_summary_all_settled)
                else -> word(W.due_summary_pending, pending)
            }
            Box(Modifier.weight(1f)) { PaneTitle(word(W.cheques), summary) }
            FillButton(word(W.add_cheque), c.brandGreen) { form = ChequeForm(null) }
        }
        if (cheques.isEmpty()) {
            Text(word(W.no_cheques), Modifier.padding(top = 24.dp), color = c.textMuted, fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize().background(c.surface, RoundedCornerShape(14.dp))) {
                items(cheques, key = { it.id }) { ch -> ChequeRow(ch) { acting = ch } }
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
                        Action(word(W.edit_cheque), c.ink) { form = ChequeForm(ch) }
                        Action(word(W.mark_cleared), c.iOwe) { clearing = ch }
                        Action(word(W.mark_bounced), c.owedToMe) { bouncing = ch }
                        Action(word(W.mark_cancelled), c.ink) { settle(ch, ChequeStatus.CANCELLED) }
                    } else if (canReopen) {
                        // Bounced and cancelled never posted anything: back to pending moves no money.
                        Action(word(W.cheque_reopen), c.ink) { settle(ch, null) }
                    }
                    Action(word(W.delete_cheque), c.owedToMe) {
                        scope.launch { withContext(Dispatchers.IO) { dao.softDeleteCheque(ch.id) } }
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
            state = rememberDialogState(width = 520.dp, height = 760.dp)
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
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
private fun ChequeRow(ch: ChequeWithParty, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    val pending = ch.status == ChequeStatus.PENDING
    val (statusWord, statusColour) = when (ch.status) {
        ChequeStatus.PENDING -> W.cheque_pending to c.ink
        ChequeStatus.CLEARED -> W.cheque_cleared to c.iOwe
        ChequeStatus.BOUNCED -> W.cheque_bounced to c.owedToMe
        else -> W.cheque_cancelled to c.textMuted
    }
    // Settled cheques stay as a record, faded: history, not something to act on.
    Row(
        Modifier.fillMaxWidth().alpha(if (pending) 1f else 0.6f).press(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(5.dp).height(64.dp).background(statusColour))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(Format.name(ch.partyName), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val detail = buildList {
                add(word(if (ch.isReceived) W.cheque_received else W.cheque_issued))
                ch.chequeNumber?.takeIf { it.isNotBlank() }?.let { add("#$it") }
                ch.bankName?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")
            Text(detail, color = c.inkSoft, fontSize = 13.sp)
            if (pending) {
                // The due date is what sends the owner to the bank today.
                val days = daysUntil(ch.dueDate)
                Text(
                    when {
                        days == 0 -> word(W.due_today)
                        days > 0 -> plural(WP.due_in_days, days, days)
                        else -> plural(WP.overdue_by_days, -days, -days)
                    } + "  ·  " + Format.dateOnly(ch.dueDate),
                    color = if (days <= 0) c.owedToMe else c.ink, fontSize = 13.sp
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.End) {
            Text(
                Format.money(ch.amount),
                color = if (ch.isReceived) c.iOwe else c.owedToMe,
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Text(word(statusWord), color = statusColour, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
    HorizontalDivider(color = c.pageBg)
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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Who: picked from the book, so the cheque lands on a real party.
        val chosen = parties.firstOrNull { it.id == partyId }
        if (chosen != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.name(chosen.name), Modifier.weight(1f), color = c.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                TextButton({ partyId = null }) { Text(word(W.edit), color = c.brandGreenText) }
            }
        } else {
            OutlinedTextField(
                value = search, onValueChange = { search = it; problem = null },
                label = { Text(word(W.cheque_party_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            val q = search.trim().lowercase()
            val shown = parties.filter { NameSearch.matches(it.name, it.phone, q) }.take(50)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp).background(c.pageBg, RoundedCornerShape(10.dp))) {
                items(shown, key = { it.id }) { p ->
                    Text(
                        Format.name(p.name) + (p.phone?.let { "  ·  " + Format.ltr(it) } ?: ""),
                        Modifier.fillMaxWidth().press { partyId = p.id; problem = null }.padding(horizontal = 12.dp, vertical = 9.dp),
                        color = c.ink, fontSize = 14.sp
                    )
                }
            }
        }
        TwoWay(word(W.cheque_received), word(W.cheque_issued), received) { received = it }
        OutlinedTextField(
            value = amountText, onValueChange = { amountText = it; problem = null },
            label = { Text(word(W.amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Text(amount?.let { Format.money(it) } ?: "", color = c.textMuted, fontSize = 14.sp)
        OutlinedTextField(
            value = number, onValueChange = { number = it },
            label = { Text(word(W.cheque_number_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = bank, onValueChange = { bank = it },
            label = { Text(word(W.bank_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ picking = true }) { Text(word(W.pick_due_date), color = c.brandGreenText) }
            Spacer(Modifier.width(8.dp))
            Text(due?.let { Format.dateOnly(it) } ?: "", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text(word(W.note_optional)) }, modifier = Modifier.fillMaxWidth()
        )
        Text(word(W.cheque_not_money_yet), color = c.textMuted, fontSize = 12.sp)
        problem?.let { Text(it, color = c.owedToMe, fontSize = 13.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            TextButton(onClick = onCancel) { Text(word(W.cancel)) }
            FillButton(word(W.save), c.brandGreen) {
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
            }
        }
    }

    if (picking) {
        DayPicker(due, onPick = { due = it; problem = null; picking = false }, onDismiss = { picking = false })
    }
}
