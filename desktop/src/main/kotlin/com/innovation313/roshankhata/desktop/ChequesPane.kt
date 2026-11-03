package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
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
fun ChequesPane(
    dao: KhataDao, parties: List<PartyWithBalance>, words: Words,
    /** The app's folder on this computer, where the cheques' pictures are kept (schema 43). */
    home: java.io.File,
    /** The pictures the other businesses' books point at, so a changed picture is never deleted from under them. */
    otherBooks: suspend () -> Set<String>? = { emptySet() },
    onLedgerChanged: () -> Unit
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    // A cheque's picture (schema 43): shown large with Change and Remove, or chosen for one that has none — whatever
    // its state; a bounced cheque's picture, with the bank's memo, is the one most worth keeping.
    var viewingPhoto by remember { mutableStateOf<ChequeWithParty?>(null) }
    var photoProblem by remember { mutableStateOf<String?>(null) }
    val pickTitle = word(W.cheque_photo)
    fun setPhoto(id: Long, path: String?) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { Photos.purgingOrphans(dao, home, otherBooks) { dao.setChequePhotoPath(id, path) } }.isSuccess
            }
            if (!ok) photoProblem = words.text(W.photo_save_failed)
        }
    }
    fun pickFor(id: Long) {
        val src = Photos.pick(pickTitle) ?: return
        scope.launch {
            val path = withContext(Dispatchers.IO) { Photos.keep(home, src) }
            if (path == null) photoProblem = words.text(W.photo_save_failed) else setPhoto(id, path)
        }
    }
    val cheques by remember { dao.observeCheques() }.collectAsState(emptyList())
    var form by remember { mutableStateOf<ChequeForm?>(null) }
    var acting by remember { mutableStateOf<ChequeWithParty?>(null) }
    var clearing by remember { mutableStateOf<ChequeWithParty?>(null) }
    var bouncing by remember { mutableStateOf<ChequeWithParty?>(null) }

    // The stamp (3 Nov 2026, the owner's mockup "Cheque — cleared / bounced stamp"): while it lands on the
    // cheque's row the list stays as it was; then the row leaves its tab and the rows below rise into its place.
    var frozen by remember { mutableStateOf<List<ChequeWithParty>?>(null) }
    var stamping by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    // Which stamp is the latest: an earlier one ending never cuts a later one short.
    val stampTurn = remember { intArrayOf(0) }

    /** Writes [write] at once; once it is written the stamp lands, and only then does the list move on. */
    fun stamped(ch: ChequeWithParty, status: Int, after: () -> Unit = {}, write: suspend () -> Boolean) {
        val turn = ++stampTurn[0]
        frozen = cheques
        scope.launch {
            try {
                if (withContext(Dispatchers.IO) { write() }) {
                    after()
                    if (turn == stampTurn[0]) stamping = ch.id to status
                    kotlinx.coroutines.delay(CHEQUE_STAMP_MS)
                }
            } finally {
                if (turn == stampTurn[0]) {
                    stamping = null
                    frozen = null
                }
            }
        }
    }

    fun settle(ch: ChequeWithParty, status: Int?) {
        // Only from the state that allows it, in one transaction (25 Oct 2026, the audit): a cheque the
        // phone cleared meanwhile is never marked bounced here, so its payment cannot be posted twice.
        if (status != null) {
            stamped(ch, status) { runCatching { dao.settleCheque(ch.id, status) }.getOrDefault(false) }
            return
        }
        scope.launch { withContext(Dispatchers.IO) { runCatching { dao.settleCheque(ch.id, null) } } }
    }

    val pending = cheques.count { it.status == ChequeStatus.PENDING }
    val summary = when {
        cheques.isEmpty() -> word(W.due_summary_empty)
        pending == 0 -> word(W.due_summary_all_settled)
        else -> word(W.due_summary_pending, pending)
    }
    // The Figma screen's three pills: pending, cleared, bounced (cancelled rides with bounced).
    var shownStatus by remember { mutableStateOf(ChequeStatus.PENDING) }
    val shown = (frozen ?: cheques).filter { ch ->
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
            // Each tab says what it has none of (25 Oct 2026, the audit: an empty Cleared tab said "All settled").
            EmptyState("ic_tile_cheques", word(when (shownStatus) {
                ChequeStatus.PENDING -> W.due_summary_all_settled
                ChequeStatus.CLEARED -> W.cheque_none_cleared
                else -> W.cheque_none_bounced
            }), null, Modifier.weight(1f))
        } else ListCard(Modifier.weight(1f)) {
            TableHead(listOf(word(W.customer) to 0, word(W.cheque_number_hint).substringBefore("(").trim() to 160, word(W.date_label) to 170, word(W.amount) to 120), endAligned = setOf(3))
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(shown, key = { _, it -> it.id }) { i, ch ->
                    ChequeRow(ch, i, stamp = stamping?.takeIf { it.first == ch.id }?.second, modifier = Modifier.animateItem(
                        fadeInSpec = androidx.compose.animation.core.tween(200),
                        placementSpec = androidx.compose.animation.core.tween(260, easing = DeskMotion.Standard),
                        fadeOutSpec = androidx.compose.animation.core.tween(200, easing = DeskMotion.StandardAccelerate)
                    )) { acting = ch }
                }
            }
        }
    }

    // The cheque's actions, then each question, in the app's own dialogs (Marhala 4: Material's had no frame of ours).
    acting?.let { ch ->
        val pending = ch.status == ChequeStatus.PENDING
        val canReopen = ch.status == ChequeStatus.BOUNCED || ch.status == ChequeStatus.CANCELLED
        AppDialog(onCloseRequest = { acting = null }, title = word(W.cheque_actions), state = rememberDialogState(width = fitW(460.dp), height = fitH(540.dp))) {
            DialogFrame(Format.name(ch.partyName), subtitle = chequeLine(ch), onClose = { acting = null },
                footer = { FluentButton(word(W.cancel), { acting = null }, kind = ButtonKind.STANDARD) }) {
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
                    // Its picture (schema 43), whatever its state.
                    if (!ch.photoPath.isNullOrBlank()) Action(word(W.view_photo), c.ink) { viewingPhoto = ch }
                    else Action(word(W.attach_photo), c.ink) { Gates.edit { pickFor(ch.id) } }
                    Action(word(W.delete_cheque), c.owedToMe) {
                        Gates.edit { scope.launch { withContext(Dispatchers.IO) { dao.softDeleteCheque(ch.id) } } }
                    }
                }
            }
        }
    }
    clearing?.let { ch ->
        AskDialog(
            word(W.mark_cleared), word(W.cleared_confirm, ch.partyName), word(W.mark_cleared),
            subtitle = chequeLine(ch),
            onConfirm = {
                clearing = null
                val note = buildString {
                    append(words.text(W.cheque_note_prefix, ch.chequeNumber?.takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty()).trim())
                    ch.bankName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                }
                stamped(ch, ChequeStatus.CLEARED, after = onLedgerChanged) { dao.clearCheque(ch.id, note) != null }
            },
            onDismiss = { clearing = null }
        )
    }
    bouncing?.let { ch ->
        AskDialog(
            word(W.mark_bounced), word(W.bounced_confirm), word(W.mark_bounced),
            subtitle = chequeLine(ch), danger = true,
            onConfirm = { bouncing = null; settle(ch, ChequeStatus.BOUNCED) },
            onDismiss = { bouncing = null }
        )
    }
    form?.let { f ->
        AppDialog(
            onCloseRequest = { form = null },
            onPreviewKeyEvent = escCloses { form = null },
            title = word(if (f.existing == null) W.add_cheque else W.edit_cheque),
            state = rememberDialogState(width = fitW(620.dp), height = fitH(640.dp))
        ) {
            Box(Modifier.fillMaxSize()) {
                ChequeEditor(
                    existing = f.existing,
                    parties = parties,
                    onSave = { cheque, photo ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val old = f.existing
                                if (old == null) {
                                    // The cheque's picture is copied in only now, with the cheque (a form left leaves nothing).
                                    dao.insertCheque(cheque.copy(photoPath = photo?.let { Photos.keep(home, it) }))
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
    viewingPhoto?.let { ch ->
        PhotoDialog(
            word(W.cheque_photo), ch.photoPath, onClose = { viewingPhoto = null },
            onChange = { viewingPhoto = null; Gates.edit { pickFor(ch.id) } },
            onRemove = { viewingPhoto = null; Gates.edit { setPhoto(ch.id, null) } }
        )
    }
    photoProblem?.let { TellDialog(word(W.cheque_photo), it, onClose = { photoProblem = null }) }
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

/** A cheque said in one line under a dialog's title: its number, bank, amount and day. */
private fun chequeLine(ch: ChequeWithParty): String = listOfNotNull(
    ch.chequeNumber?.takeIf { it.isNotBlank() }?.let { "#$it" },
    ch.bankName?.takeIf { it.isNotBlank() },
    Format.money(ch.amount),
    Format.dateOnly(ch.dueDate)
).joinToString(" · ")

/** How long a stamp is on its row before the list moves on: it lands, is held to be read, and fades. */
private const val CHEQUE_STAMP_MS = 180L + 560L + 180L + 40L

@Composable
private fun ChequeRow(ch: ChequeWithParty, index: Int, stamp: Int?, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    // While the stamp lands, the row wears the status it was given (its tag turns under the stamp).
    val status = stamp ?: ch.status
    val pending = status == ChequeStatus.PENDING
    val (statusWord, statusColour, statusTint) = when (status) {
        ChequeStatus.PENDING -> Triple(W.cheque_pending, Look.warn, Look.warnTint)
        ChequeStatus.CLEARED -> Triple(W.cheque_cleared, Look.accent, Look.accentTint)
        ChequeStatus.BOUNCED -> Triple(W.cheque_bounced, c.owedToMe, Look.getTint)
        else -> Triple(W.cheque_cancelled, c.textMuted, Look.subtle)
    }
    Column(modifier) {
    Box {
    // Settled cheques stay as a record, faded: history, not something to act on (not while their stamp lands).
    Row(
        Modifier.fillMaxWidth().alpha(if (pending || stamp != null) 1f else 0.7f).press(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(ch.partyName, 32, index, rounded = true, id = ch.partyId)
            Column {
                Text(Format.name(ch.partyName), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(word(if (ch.isReceived) W.cheque_received else W.cheque_issued), color = c.textMuted, fontSize = 12.sp, maxLines = 1)
            }
        }
        Column(Modifier.width(160.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(ch.chequeNumber?.takeIf { it.isNotBlank() }?.let { "#$it" } ?: "—", Modifier.weight(1f, fill = false), color = c.ink, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                // Its picture is kept (schema 43).
                if (!ch.photoPath.isNullOrBlank()) Glyph("ic_camera", c.textMuted, 14)
            }
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
    if (stamp != null) ChequeStamp(stamp, Modifier.align(Alignment.Center))
    }
    Rule()
    }
}

/**
 * The stamp itself (the owner's mockup): CLEARED in green, BOUNCED in red, CANCELLED in grey, a little turned. It
 * lands in 180 ms from half again its size, an echo of its edge spreads and fades behind it, it is held to be read
 * and fades before the row leaves.
 */
@Composable
private fun ChequeStamp(status: Int, modifier: Modifier) {
    val c = LocalRoshanColors.current
    val (wordName, ink) = when (status) {
        ChequeStatus.CLEARED -> W.cheque_cleared to Look.accent
        ChequeStatus.BOUNCED -> W.cheque_bounced to c.owedToMe
        else -> W.cheque_cancelled to c.textMuted
    }
    val land = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val echo = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val leave = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch { land.animateTo(1f, androidx.compose.animation.core.tween(180, easing = DeskMotion.EmphasizedDecelerate)) }
        launch { echo.animateTo(1f, androidx.compose.animation.core.tween(300, delayMillis = 120, easing = DeskMotion.Standard)) }
        launch { leave.animateTo(1f, androidx.compose.animation.core.tween(180, delayMillis = 180 + 560, easing = DeskMotion.StandardAccelerate)) }
    }
    val text = word(wordName).uppercase()
    // Spaced capitals read as a stamp in Latin letters; joined scripts are never spaced apart.
    val latin = text.all { !it.isLetter() || it.code < 0x0250 }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.graphicsLayer { rotationZ = -10f }) {
        Box(
            Modifier.matchParentSize().graphicsLayer {
                val k = 1f + 0.14f * echo.value
                scaleX = k; scaleY = k
                alpha = if (echo.value > 0f) 0.35f * (1f - echo.value) else 0f
            }.border(3.dp, ink, shape)
        )
        Text(
            text,
            Modifier.graphicsLayer {
                val k = 1.5f - 0.5f * land.value
                scaleX = k; scaleY = k
                alpha = land.value * (1f - leave.value)
            }.border(3.dp, ink, shape).padding(horizontal = 12.dp, vertical = 3.dp),
            color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            letterSpacing = if (latin) 2.sp else 0.sp
        )
    }
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
    /** The cheque, and for a new one the picture chosen (schema 43), copied in when it is written. */
    onSave: (Cheque, java.io.File?) -> Unit,
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
    var photo by remember { mutableStateOf<java.io.File?>(null) }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val amount = Calc.evalAmount(amountText)
    val needParty = word(W.select_party_first)
    val needAmount = word(W.enter_valid_amount)
    val needDate = word(W.pick_date_first)

    // The cheque's rule is said whole in the form (25 Oct 2026, the audit: as the subtitle it was cut to one line).
    DialogFrame(
        word(if (existing == null) W.add_cheque else W.edit_cheque), existing?.let { Format.name(it.partyName) },
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
                        ),
                        photo
                    )
                }
            })
        }
    ) {
        Text(word(W.cheque_not_money_yet), color = c.textMuted, fontSize = 12.5.sp)
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
        // A new cheque's picture (schema 43); one already written changes it from its own actions.
        if (existing == null) FormPhoto(word(W.cheque_photo), photo) { photo = it }
    }

    if (picking) {
        DayPicker(due, onPick = { due = it; problem = null; picking = false }, onDismiss = { picking = false })
    }
}
