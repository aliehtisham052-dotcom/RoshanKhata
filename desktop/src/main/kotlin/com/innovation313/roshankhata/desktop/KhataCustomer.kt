package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.DateWords
import com.innovation313.roshankhata.data.EntryWithItems
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.QrTag
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.balanceColour
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Several entries of the open account chosen to delete (the phone's select mode, Marhala 4): which are ticked,
 * and what the bar's buttons do. The book keeps it, so its question and the bin's answer are the window's.
 */
class EntryPick(
    val chosen: Set<Long>,
    val onToggle: (Long) -> Unit,
    val onAll: (List<Long>) -> Unit,
    val onDelete: () -> Unit,
    val onCancel: () -> Unit
)

/**
 * The chosen party's account as the owner's mockup draws it (Marhala 4, 25 Oct 2026, in place of the shared
 * PartyLedgerScreen's Material page): the face, name and phone, the balance with its side in words; the
 * promised day and the season record as chips; I gave · F2, I got · F3 and the account's tools; then the
 * entries as a ledger — what, I gave, I got and the balance after each, day by day (the phone's running
 * balance). The search finds notes, goods, numbers and amounts (entryMatches); the days and the order are the
 * owner's to choose. 25 Oct 2026, the audit: the WhatsApp bar and the search sat above the name and the
 * balance, and the entries said neither what was sold nor where the account stood after each.
 *
 * The phone's customer page in full (the audit's list): who he is under the name (s/o, village); the balance
 * copied as a ready line by a click on it; the promised day changed from its chip (or ⋮); the customer's QR
 * card; several entries chosen and deleted at once ([pick]).
 */
@Composable
fun CustomerPane(
    party: PartyWithBalance,
    face: ImageBitmap?,
    /** Newest first, as the book reads them, with their goods lines. */
    entries: List<EntryWithItems>,
    /** "s/o Muhammad · Khichi": whichever of the father's name and the village are known, or null. */
    identity: String?,
    /** The day they promised, said ("Promised: 15 Nov"), and the season record, one line a season. */
    promise: String?,
    seasons: List<String>,
    onGave: () -> Unit,
    onGot: () -> Unit,
    onRemind: () -> Unit,
    onStatement: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEntry: (LedgerEntry) -> Unit,
    /** The promised day's chooser; its chip opens it, and ⋮ too when [promiseInMenu] (the phone: agri shops). */
    onPromise: () -> Unit,
    promiseInMenu: Boolean,
    onQr: () -> Unit,
    /** Several entries being chosen to delete, or null; [onPickEntries] starts it. */
    pick: EntryPick?,
    onPickEntries: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalRoshanColors.current
    val shape = RoundedCornerShape(16.dp)
    var query by remember(party.id) { mutableStateOf("") }
    var oldestFirst by remember(party.id) { mutableStateOf(false) }
    var days by remember(party.id) { mutableStateOf(PartyWhen.ALL) }
    // The balance after each entry, in the book's order (oldest first): gave adds, got takes off — the party's
    // own balance rule (partiesWithBalance), so the newest row's figure is the balance above.
    // Summed as decimals, so 0.10 + 0.20 − 0.30 is a settled 0, never a residue printed as 0.00.
    val after = remember(entries) {
        var run = java.math.BigDecimal.ZERO
        entries.map { it.entry }.sortedWith(compareBy({ it.timestamp }, { it.id })).associate { e ->
            val amount = java.math.BigDecimal.valueOf(e.amount)
            run = if (e.isGiven) run + amount else run - amount
            e.id to run.toDouble()
        }
    }
    val goodsOf = remember(entries) { entries.associate { w -> w.entry.id to w.items.mapNotNull { it.itemName }.joinToString(" ") } }
    val window = days.window()
    val shown = remember(entries, query, oldestFirst, days) {
        entries.filter { w -> (window == null || w.entry.timestamp in window) && entryMatches(w.entry, goodsOf[w.entry.id], query) }
            .let { l -> if (oldestFirst) l.sortedWith(compareBy({ it.entry.timestamp }, { it.entry.id })) else l }
    }
    // The phone's tap on the balance: a ready line about it on the clipboard, and the side's words say so a moment.
    val clipLine = when {
        Money.isPositive(party.balance) -> word(W.copy_balance_owed, Format.isolate(party.name), Format.ltr(Format.money(party.balance)))
        Money.isNegative(party.balance) -> word(W.copy_balance_i_owe, Format.isolate(party.name), Format.ltr(Format.money(-party.balance)))
        else -> word(W.copy_balance_settled, Format.isolate(party.name), Format.ltr(Format.money(0.0)))
    }
    var copied by remember(party.id) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    Column(modifier.fillMaxSize().clip(shape).background(Look.layer).border(1.dp, Look.strokeCard, shape)) {
        // The head: who, how they stand, and what can be done.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PartyFace(party, face, 44)
                Column(Modifier.weight(1f)) {
                    Text(Format.name(party.name), color = c.ink, fontSize = 16.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(party.phone?.takeIf { it.isNotBlank() }?.let { Format.ltr(it) }, word(if (party.isCustomer) W.customer else W.supplier))
                            .joinToString(" · "),
                        color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    identity?.let { Text(it, color = c.textMuted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                HoverTip(word(W.copy_details)) {
                    Column(
                        Modifier.clip(RoundedCornerShape(10.dp)).press(RoundedCornerShape(10.dp)) {
                            runCatching {
                                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(clipLine), null)
                            }.onSuccess { copied = true }
                        }.padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(Format.money(kotlin.math.abs(party.balance)), color = balanceColour(party.balance), fontSize = 21.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        val tint = when {
                            Money.isPositive(party.balance) -> Look.getTint
                            Money.isNegative(party.balance) -> Look.chosen
                            else -> Look.subtle
                        }
                        Text(if (copied) "✓  " + word(W.copied) else word(sideOf(party.balance)),
                            Modifier.background(tint, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp),
                            color = if (Money.isZero(party.balance)) c.inkSoft else balanceColour(party.balance), fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                }
            }
            if (promise != null || seasons.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    promise?.let { p -> HoverTip(word(W.harvest_promise_menu)) { AccountChip(p, Look.warn, Look.warnTint, "ic_calendar", onPromise) } }
                    seasons.forEach { AccountChip(it, Look.olive, Look.oliveTint, null) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                WayButton(word(W.i_gave), "F2", "ic_arrow_gave", c.gaveFill, Modifier.weight(1f), onGave)
                WayButton(word(W.i_got), "F3", "ic_arrow_got", c.brandGreen, Modifier.weight(1f), onGot)
                TintAction("ic_action_whatsapp", word(W.send_reminder), Look.leaf, Look.leafTint, size = 40, onClick = onRemind)
                TintAction("ic_pdf_doc", word(W.statement_pdf), Look.clay, Look.clayTint, size = 40, onClick = onStatement)
                TintAction("ic_edit", word(W.edit_party), c.inkSoft, Look.subtle, size = 40, onClick = onEdit)
                Box {
                    var open by remember { mutableStateOf(false) }
                    TintAction("ic_more_vert", word(W.more_options), c.inkSoft, Look.subtle, size = 40) { open = true }
                    // The phone's customer menu, in its order (Edit and Statement have their own buttons here).
                    DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Look.layer) {
                        DropdownMenuItem(text = { Text(word(W.customer_qr), color = c.ink, fontSize = 14.sp) }, onClick = { open = false; onQr() })
                        if (promiseInMenu) {
                            DropdownMenuItem(text = { Text(word(W.harvest_promise_menu), color = c.ink, fontSize = 14.sp) }, onClick = { open = false; onPromise() })
                        }
                        DropdownMenuItem(text = { Text(word(W.select_entries_to_delete), color = c.ink, fontSize = 14.sp) },
                            onClick = { open = false; onPickEntries() }, enabled = entries.isNotEmpty())
                        HorizontalDivider(color = Look.line)
                        DropdownMenuItem(text = { Text(word(W.delete_party), color = c.owedToMe, fontSize = 14.sp) }, onClick = { open = false; onDelete() })
                    }
                }
            }
        }
        HorizontalDivider(color = Look.strokeCard)
        // The account's own search, its days and its order.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FluentField(value = query, onValueChange = { query = it }, placeholder = word(W.search_entries), leadingIcon = "ic_search", modifier = Modifier.weight(1f))
            Box {
                var open by remember { mutableStateOf(false) }
                TintAction("ic_calendar", if (days == PartyWhen.ALL) word(W.filter_by_date) else word(days.word), Look.slate, Look.slateTint,
                    active = days != PartyWhen.ALL) { open = true }
                DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Look.layer) {
                    PartyWhen.entries.forEach { w ->
                        DropdownMenuItem(text = {
                            Text((if (w == days) "✓  " else "     ") + word(w.word), color = if (w == days) c.brandGreenText else c.ink, fontSize = 14.sp,
                                fontWeight = if (w == days) FontWeight.Bold else FontWeight.Normal)
                        }, onClick = { open = false; days = w })
                    }
                }
            }
            FluentButton(word(if (oldestFirst) W.sort_oldest else W.sort_newest), { oldestFirst = !oldestFirst }, kind = ButtonKind.STANDARD, icon = "ic_sort_arrows")
        }
        // Choosing several (the phone's select mode): how many, Select all (the entries shown), Delete, Cancel.
        // What is deleted is what is seen: a tick the search or the days now hide is taken off, never binned unseen.
        if (pick != null) {
            val visible = remember(shown) { shown.mapTo(HashSet()) { it.entry.id } }
            LaunchedEffect(visible, pick.chosen) {
                if (!visible.containsAll(pick.chosen)) pick.onAll(pick.chosen.filter { it in visible })
            }
        }
        pick?.let { p ->
            KhataSelectBar(
                p.chosen.size,
                onAll = { p.onAll(shown.map { it.entry.id }) },
                onDelete = p.onDelete,
                onCancel = p.onCancel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }
        LedgerHead()
        if (shown.isEmpty()) {
            Text(
                word(if (entries.isEmpty()) W.no_entries_yet else W.entries_no_match),
                Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                color = c.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center
            )
        } else {
            val zone = java.time.ZoneId.systemDefault()
            val byDay = remember(shown) { shown.groupBy { java.time.Instant.ofEpochMilli(it.entry.timestamp).atZone(zone).toLocalDate() }.toList() }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                byDay.forEach { (day, rows) ->
                    item(key = "day-$day") { DayHead(rows.first().entry.timestamp, rows.size) }
                    items(rows, key = { it.entry.id }) { w ->
                        val id = w.entry.id
                        LedgerRow(w, after[id] ?: 0.0, picking = pick != null, ticked = pick?.chosen?.contains(id) == true) {
                            if (pick != null) pick.onToggle(id) else onEntry(w.entry)
                        }
                    }
                }
            }
        }
    }
}

/** When nobody is chosen yet: what the right side is for (25 Oct 2026, the audit: it stood empty). */
@Composable
fun NoPartyChosen(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.fillMaxSize().clip(shape).background(Look.layer).border(1.dp, Look.strokeCard, shape)) {
        EmptyState("ic_tile_khata", word(W.nav_khata), word(W.khata_pick_party))
    }
}

/**
 * The customer's own QR card (the phone's Customer QR, Marhala 4): the code that opens this account when it is
 * held to the counter's scanner, the name above it for the owner. Saved as a picture it is the code alone — the
 * name would tell anyone it is forwarded to whose it is; the code tells nobody, it opens only inside this book.
 * A customer from an old backup without a code is given one, saved before it is drawn, so a card never carries
 * a code the book does not know.
 */
@Composable
fun CustomerQrDialog(dao: KhataDao, partyId: Long, name: String, onClose: () -> Unit) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val scope = rememberCoroutineScope()
    var picture by remember(partyId) { mutableStateOf<java.awt.image.BufferedImage?>(null) }
    // What happened last: the file it was saved to, or why it was not (then said in red).
    var said by remember(partyId) { mutableStateOf<Pair<String, Boolean>?>(null) }
    LaunchedEffect(partyId) {
        withContext(Dispatchers.IO) {
            runCatching {
                val p = dao.getParty(partyId) ?: error("party $partyId")
                val token = p.qrToken ?: QrTag.newToken().also { fresh -> dao.updateParty(p.copy(qrToken = fresh)) }
                PhoneSync.qr(QrTag.payload(token), 520)
            }
        }.onSuccess { picture = it }.onFailure { said = failText(words, it) to true }
    }
    val shownPicture = remember(picture) { picture?.toComposeImageBitmap() }
    val saveTitle = word(W.customer_qr_save_picture)
    AppDialog(onCloseRequest = onClose, title = word(W.customer_qr), state = rememberDialogState(width = fitW(440.dp), height = fitH(580.dp))) {
        DialogFrame(
            title = word(W.customer_qr),
            subtitle = Format.name(name),
            onClose = onClose,
            footer = {
                FluentButton(word(W.cancel), onClose, kind = ButtonKind.SUBTLE)
                FluentButton(saveTitle, {
                    val img = picture ?: return@FluentButton
                    val dialog = FileDialog(null as Frame?, saveTitle, FileDialog.SAVE)
                    startIn(dialog, "Pictures")
                    dialog.file = "RoshanKhata_QR.png"
                    dialog.isVisible = true
                    val file = dialog.file?.let { File(dialog.directory, if (it.endsWith(".png", true)) it else "$it.png") } ?: return@FluentButton
                    scope.launch {
                        said = withContext(Dispatchers.IO) {
                            runCatching { check(javax.imageio.ImageIO.write(img, "png", file)) { "png" } }
                                .map { words.text(W.report_saved_to, file.absolutePath) to false }
                                .getOrElse { failText(words, it) to true }
                        }
                    }
                }, kind = ButtonKind.ACCENT, icon = "ic_download", enabled = picture != null)
            }
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(268.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Look.strokeCard, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center) {
                    shownPicture?.let { Image(it, contentDescription = word(W.customer_qr), modifier = Modifier.size(252.dp)) }
                }
            }
            Text(word(W.customer_qr_hint), color = c.textMuted, fontSize = 12.5.sp)
            said?.let { (text, trouble) -> Text(text, color = if (trouble) c.owedToMe else c.inkSoft, fontSize = 12.5.sp) }
        }
    }
}

/** The ledger's column heads: what, I gave, I got, the balance after it. */
@Composable
private fun LedgerHead() {
    val head = Look.warn
    Row(
        Modifier.fillMaxWidth().background(Look.layerAlt).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HeadCell(word(W.details), Modifier.weight(1f), false, head)
        HeadCell(word(W.i_gave), Modifier.width(LEDGER_FIGURE), true, head)
        HeadCell(word(W.i_got), Modifier.width(LEDGER_FIGURE), true, head)
        HeadCell(word(W.pdf_stmt_balance), Modifier.width(LEDGER_BALANCE), true, head)
    }
    HorizontalDivider(color = Look.line)
}

@Composable
private fun HeadCell(text: String, m: Modifier, end: Boolean, color: Color) = Text(
    text.uppercase(), m, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp,
    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (end) TextAlign.End else TextAlign.Start
)

/** A day's heading: Today, Yesterday or the day, and how many entries it holds. */
@Composable
private fun DayHead(at: Long, count: Int) {
    val c = LocalRoshanColors.current
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    val near = day == today || day == today.minusDays(1)
    // Asked each time, as DateWords says: it keeps its own formatters, and the shop's calendar or clock may change.
    val date = DateWords.formatter("EEE, d MMM").format(java.util.Date(at))
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 9.dp, bottom = 3.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(dayWords(at), color = Look.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text((if (near) "$date · " else "") + plural(WP.pdf_stmt_entries, count, count), color = c.textMuted, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * One entry: what it was (the goods, else how it was paid, else its note), when and its number, the two sides and
 * the balance after. While several are being chosen ([picking]) a tick stands before it and a click ticks it.
 */
@Composable
private fun LedgerRow(w: EntryWithItems, balanceAfter: Double, picking: Boolean, ticked: Boolean, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    val e = w.entry
    val goods = w.orderedItems().filter { !it.itemName.isNullOrBlank() }.joinToString(", ") { i ->
        i.itemName!!.trim() + (i.quantity?.let { q -> " · " + Format.qty(q, i.unit) } ?: "")
    }
    val note = e.note?.trim()?.takeIf { it.isNotEmpty() }
    // A property of a class in another module is never smart-cast: read into a val first.
    val method = e.paymentMethod
    val title = when {
        goods.isNotEmpty() -> goods
        method != null -> methodWords(method) + (note?.let { " · $it" } ?: "")
        note != null -> note
        else -> word(if (e.isGiven) W.i_gave else W.i_got)
    }
    val time = DateWords.formatter("h:mm a").format(java.util.Date(e.timestamp))
    val sub = listOfNotNull(
        time,
        e.entryNumber.takeIf { it.isNotBlank() }?.let { Format.ltr(it) },
        note.takeIf { goods.isNotEmpty() },
        if (e.isQarzeHasna) word(W.pdf_stmt_qarze_hasna) else null
    ).joinToString(" · ")
    val none = c.textMuted.copy(alpha = 0.55f)
    Row(
        Modifier.fillMaxWidth().background(eased(if (ticked) Look.chosen else Color.Transparent)).press(onClick = onClick)
            .padding(start = if (picking) 6.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (picking) Checkbox(checked = ticked, onCheckedChange = { onClick() })
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = c.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (e.isGiven) figure(e.amount) else "—", Modifier.width(LEDGER_FIGURE), color = if (e.isGiven) c.owedToMe else none,
            fontSize = 13.sp, fontWeight = if (e.isGiven) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        Text(if (!e.isGiven) figure(e.amount) else "—", Modifier.width(LEDGER_FIGURE), color = if (!e.isGiven) c.iOwe else none,
            fontSize = 13.sp, fontWeight = if (!e.isGiven) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
        Text(figure(balanceAfter, signed = true), Modifier.width(LEDGER_BALANCE),
            color = c.inkSoft, fontSize = 12.5.sp, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Look.line)
}

/**
 * A figure in the ledger's columns: the amount without the currency sign (the head says what it is). [signed]: the
 * balance column, where money the shop owes carries its minus — inside the left-to-right run, so an Urdu line
 * reads −500 and not 500−. A residue below half a paisa is a settled 0.
 */
private fun figure(v: Double, signed: Boolean = false): String {
    val minus = signed && Money.isNegative(v)
    val m = Format.money(if (Money.isZero(v)) 0.0 else v)
    return Format.ltr((if (minus) "−" else "") + m.substringAfter(' ', m))
}

@Composable
internal fun methodWords(m: String): String = word(
    when (m) {
        PaymentMethod.CASH -> W.payment_method_cash
        PaymentMethod.BANK -> W.payment_method_bank
        PaymentMethod.CHEQUE -> W.payment_method_cheque
        else -> W.payment_method_online
    }
)

/** A small fact under the name: the promised day (a click changes it), a season paid on time. */
@Composable
private fun AccountChip(text: String, ink: Color, tint: Color, icon: String?, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(26.dp).clip(shape).background(tint)
            .then(if (onClick != null) Modifier.press(shape, onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        icon?.let { Glyph(it, ink, 13) }
        Text(text, color = ink, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** I gave / I got (and the cashbook's Money in / out): the way's colour, its arrow, its words and its key, if any. */
@Composable
internal fun WayButton(text: String, key: String?, icon: String, fill: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(fill)
            .press(RoundedCornerShape(12.dp), wash = Color.White.copy(alpha = 0.12f), onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Glyph(icon, Color.White, 16)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (key != null) {
            Spacer(Modifier.width(8.dp))
            Text(key, Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp),
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

private val LEDGER_FIGURE = 80.dp
private val LEDGER_BALANCE = 84.dp
