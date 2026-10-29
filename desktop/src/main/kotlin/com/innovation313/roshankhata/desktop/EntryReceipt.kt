package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.EntryItem
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.PaymentMethod
import com.innovation313.roshankhata.data.ReceiptGoods
import com.innovation313.roshankhata.data.SeasonBook
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * An entry as its receipt (Marhala 4, part 6b: the phone's EntryDetailActivity; on the computer a click on an
 * entry opened its editor at once): which way and how much on its colour, who, the number and when, the goods
 * as the receipt says them (ReceiptGoods, shared with the phone), the note, how it was paid and the balance it
 * left in the customer's own words; on an agri shop's sale the season it is credit for and the dealer's spray
 * advice per item; the bill's photo and the customer's signature. Edit, Delete, Print, and an invoice from a
 * sale. An entry another record wrote (a bill, a plan, a cheque, a ticket) is changed there, and says so.
 */
@Composable
fun EntryReceiptDialog(
    dao: KhataDao,
    entryId: Long,
    partyName: String,
    home: File,
    businessId: Long,
    /** Bumped by the window after any change to the book: the receipt reads again. */
    reload: Int,
    onClose: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    onPrint: (LedgerEntry, List<EntryItem>) -> Unit,
    /** A sale's invoice: the one made from it (its id), or a new one to make (null). */
    onInvoice: (LedgerEntry, Long?) -> Unit,
    onChanged: () -> Unit
) {
    AppDialog(onCloseRequest = onClose, title = word(W.receipt_title), state = rememberDialogState(width = fitW(560.dp), height = fitH(720.dp))) {
        EntryReceiptCard(dao, entryId, partyName, home, businessId, reload, onClose, onEdit, onDelete, onPrint, onInvoice, onChanged)
    }
}

/** The receipt card itself, in its frame (the proof pictures draw it without the dialog around it). */
@Composable
fun EntryReceiptCard(
    dao: KhataDao,
    entryId: Long,
    partyName: String,
    home: File,
    businessId: Long,
    reload: Int,
    onClose: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit,
    onPrint: (LedgerEntry, List<EntryItem>) -> Unit,
    onInvoice: (LedgerEntry, Long?) -> Unit,
    onChanged: () -> Unit
) {
    val c = LocalRoshanColors.current
    val words = LocalWords.current
    val trade = LocalTrade.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var entry by remember { mutableStateOf<LedgerEntry?>(null) }
    var items by remember { mutableStateOf<List<EntryItem>>(emptyList()) }
    var after by remember { mutableStateOf<Double?>(null) }
    var invoiceId by remember { mutableStateOf<Long?>(null) }
    var owner by remember { mutableStateOf<String?>(null) }
    var signature by remember { mutableStateOf<java.awt.image.BufferedImage?>(null) }
    var signedAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(entryId, reload, version) {
        withContext(Dispatchers.IO) {
            val e = runCatching { dao.getEntry(entryId) }.getOrNull()
            entry = e
            if (e != null) {
                items = runCatching { dao.itemsOfEntry(entryId) }.getOrDefault(emptyList()).sortedWith(compareBy({ it.lineNo }, { it.id }))
                after = runCatching { dao.balanceAfterEntry(e) }.getOrNull()
                invoiceId = if (e.isGiven) runCatching { dao.invoiceForEntry(e.id) }.getOrNull() else null
                owner = runCatching { dao.entryOwner(e.id) }.getOrNull().orEmpty()
                signature = EntrySignatures.load(home, businessId, e.id)
                signedAt = EntrySignatures.signedAt(home, businessId, e.id)
            }
        }
    }
    val e = entry
    var linkedSaid by remember { mutableStateOf(false) }
    var advising by remember { mutableStateOf<EntryItem?>(null) }
    var choosingLine by remember { mutableStateOf(false) }
    var choosingSeason by remember { mutableStateOf(false) }
    var signing by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf(false) }
    val title = word(if (e?.isGiven != false) W.receipt_title else W.payment_receipt)
    run {
        DialogFrame(
            title,
            subtitle = e?.let { Format.ltr(it.entryNumber) + "  ·  " + Format.dateTime(it.timestamp) },
            onClose = onClose,
            footerStart = {
                if (e != null && !e.isDeleted) FluentButton(word(W.delete), { if (!owner.isNullOrEmpty()) linkedSaid = true else onDelete(e) }, kind = ButtonKind.DANGER)
            },
            footer = {
                if (e != null) {
                    IconAction("ic_print", word(W.print_receipt), { onPrint(e, items) })
                    if (e.isGiven && !e.isDeleted) FluentButton(word(if (invoiceId != null) W.entry_open_invoice else W.entry_make_invoice), { onInvoice(e, invoiceId) }, icon = "ic_tile_invoice")
                    if (!e.isDeleted) FluentButton(word(W.edit), { if (!owner.isNullOrEmpty()) linkedSaid = true else onEdit(e) }, kind = ButtonKind.ACCENT, icon = "ic_edit")
                }
            }
        ) {
            if (e == null) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                // Which way and how much, on the khata's own colours (red: given, the party owes; green: got).
                val way = if (e.isGiven) c.owedToMe else Look.accent
                Column(
                    Modifier.fillMaxWidth().clip(Look.card).background(if (e.isGiven) Look.getTint else Look.accentTint).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Glyph(if (e.isGiven) "ic_arrow_gave" else "ic_arrow_got", way, 16)
                        Text(word(if (e.isGiven) W.you_gave else W.you_got), color = way, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(Format.money(e.amount), color = c.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Text(Format.name(partyName), color = c.inkSoft, fontSize = 14.sp, maxLines = 1)
                }
                // The goods as the receipt says them: each item priced, the total, the rate type, the difference.
                ReceiptGoods.text(words, items, e.rateType, e.amount)?.let { goods ->
                    ReceiptRow(word(W.goods_label), goods)
                }
                e.note?.takeIf { it.isNotBlank() }?.let { ReceiptRow(word(W.note_label), it) }
                // How the money came, only when it was recorded (an unrecorded method is not cash), with the
                // payment's reference after it (schema 39): "Bank transfer · Ref: TID 778…".
                PaymentMethod.said(words, e.paymentMethod, e.paymentRef)?.let { ReceiptRow(word(W.payment_method_label), it) }
                // The balance this entry left, in the customer's own words (ReceiptBalance), not today's.
                ReceiptRow(word(W.balance_label), after?.let { com.innovation313.roshankhata.data.ReceiptBalance.text(words, it) } ?: "—")

                // A sale's season (an agri shop that farms by Rabi and Kharif): by its date, or the one the owner chose.
                if (e.isGiven && !e.isDeleted && trade.seasons) {
                    val byDate = SeasonBook.seasonOf(e.timestamp)
                    val own = SeasonBook.Season.fromKey(e.season)
                    val shown = own ?: byDate
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(word(W.season_entry, if (own == null) word(W.season_from_date, seasonName(shown)) else seasonName(shown)), Modifier.weight(1f), color = c.ink, fontSize = 14.sp)
                        FluentButton(word(W.season_change), { choosingSeason = true }, kind = ButtonKind.SUBTLE)
                    }
                }
                // The dealer's spray advice on a sale's items (an agri shop's): one item at once, or which one first.
                val goodsLines = items.filter { !it.itemName.isNullOrBlank() }
                if (e.isGiven && !e.isDeleted && trade.sprayAdvice && goodsLines.isNotEmpty()) {
                    FluentButton(word(W.advice_write), { if (goodsLines.size == 1) advising = goodsLines[0] else choosingLine = true }, kind = ButtonKind.STANDARD, icon = "ic_edit")
                }
                // The bill this entry came from: its picture (kept on this computer, never on a statement).
                val bill = e.billPhotoPath?.takeIf { it.isNotBlank() }
                if (bill != null) {
                    val thumb = Photos.bitmap(bill, 480)
                    if (thumb != null) {
                        Text(word(W.bill_photo_label), color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Image(thumb, contentDescription = word(W.view_bill_photo),
                            modifier = Modifier.heightIn(max = 160.dp).clip(Look.ctl).press(Look.ctl) { photo = true }, contentScale = ContentScale.Fit)
                    }
                }
                // The customer's signature, with when it was taken; taken or changed here as on the phone.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val sig = signature
                    if (sig != null) {
                        val pic = remember(sig) { sig.toComposeImageBitmap() }
                        Image(pic, contentDescription = word(W.sig_label), modifier = Modifier.height(54.dp).width(140.dp).clip(Look.ctl).background(Color.White))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(word(W.sig_label), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(signedAt?.let { word(W.sig_signed_at, Format.dateTime(it)) } ?: word(W.sig_none), color = c.textMuted, fontSize = 12.sp)
                    }
                    if (!e.isDeleted) FluentButton(word(if (sig != null) W.sig_retake else W.sig_take), { signing = true }, kind = ButtonKind.SUBTLE)
                }
            }
        }
    }
    if (linkedSaid) {
        val by = owner.orEmpty()
        TellDialog(word(W.entry_linked_title), word(com.innovation313.roshankhata.data.EntryOwner.wordFor(by)), { linkedSaid = false })
    }
    if (photo && e != null) PhotoDialog(word(W.bill_photo_label), e.billPhotoPath) { photo = false }
    if (signing && e != null) SignatureDialog(
        title = word(W.sig_title),
        subtitle = Format.name(partyName) + "  ·  " + word(if (e.isGiven) W.you_gave else W.you_got) + " " + Format.money(e.amount),
        onDone = { picture ->
            signing = false
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { EntrySignatures.save(home, businessId, e.id, picture) } }
                version++
            }
        },
        onCancel = { signing = false }
    )
    if (choosingLine && e != null) {
        val goodsLines = items.filter { !it.itemName.isNullOrBlank() }
        PickDialog(word(W.advice_pick_line), goodsLines.map { it.itemName.orEmpty() }, onPick = { i -> choosingLine = false; advising = goodsLines[i] }, onDismiss = { choosingLine = false })
    }
    advising?.let { item ->
        AdviceDialog(item, onDismiss = { advising = null }) { crop, pest, dose ->
            advising = null
            scope.launch {
                withContext(Dispatchers.IO) { dao.setLineAdvice(item.id, crop, pest, dose) }
                version++
                onChanged()
            }
        }
    }
    if (choosingSeason && e != null) {
        val byDate = SeasonBook.seasonOf(e.timestamp)
        val options = listOf(byDate.previous(), byDate, byDate.next())
        val shown = SeasonBook.Season.fromKey(e.season) ?: byDate
        val labels = options.map { if (it == byDate) word(W.season_from_date, seasonName(it)) else seasonName(it) }
        PickDialog(word(W.season_pick), labels, chosen = options.indexOf(shown), onPick = { i ->
            choosingSeason = false
            val pick = options[i]
            scope.launch {
                withContext(Dispatchers.IO) { dao.setEntrySeason(e.id, if (pick == byDate) null else pick.key) }
                version++
                onChanged()
            }
        }, onDismiss = { choosingSeason = false })
    }
}

/** A label over its value on the receipt card. */
@Composable
private fun ReceiptRow(label: String, value: String) {
    val c = LocalRoshanColors.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = c.ink, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/**
 * One choice from a short list (the phone's ChoiceSheet), in the app's frame; [chosen] marked. [textSizes], when
 * given, draws each line in its own size (the text size choice shows what each will look like).
 */
@Composable
fun PickDialog(title: String, labels: List<String>, chosen: Int = -1, onPick: (Int) -> Unit, onDismiss: () -> Unit, textSizes: List<Float>? = null) {
    val c = LocalRoshanColors.current
    AppDialog(onCloseRequest = onDismiss, title = title, state = rememberDialogState(width = fitW(440.dp), height = fitH((190 + 49 * labels.size).dp))) {
        DialogFrame(title, onClose = onDismiss, footer = { FluentButton(word(W.cancel), onDismiss, kind = ButtonKind.STANDARD) }) {
            Column {
                labels.forEachIndexed { i, label ->
                    val on = i == chosen
                    Row(
                        Modifier.fillMaxWidth().clip(Look.ctl).background(if (on) Look.accentTint else Color.Transparent).press(Look.ctl) { onPick(i) }.padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, Modifier.weight(1f), color = c.ink, fontSize = (textSizes?.getOrNull(i) ?: 15f).sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        if (on) Glyph("ic_check_plain", Look.accent, 16)
                    }
                    if (i < labels.lastIndex) Rule()
                }
            }
        }
    }
}

/** The dealer's spray advice on one item (the phone's dialog_line_advice): crop, pest, dose; blank is none. */
@Composable
private fun AdviceDialog(item: EntryItem, onDismiss: () -> Unit, onSave: (String?, String?, String?) -> Unit) {
    var crop by remember(item.id) { mutableStateOf(item.crop.orEmpty()) }
    var pest by remember(item.id) { mutableStateOf(item.pest.orEmpty()) }
    var dose by remember(item.id) { mutableStateOf(item.dose.orEmpty()) }
    val title = word(W.advice_title, item.itemName.orEmpty())
    AppDialog(onCloseRequest = onDismiss, title = title, state = rememberDialogState(width = fitW(480.dp), height = fitH(420.dp))) {
        DialogFrame(title, onClose = onDismiss, footer = {
            FluentButton(word(W.cancel), onDismiss, kind = ButtonKind.STANDARD)
            FluentButton(word(W.save), { onSave(crop.trim().ifEmpty { null }, pest.trim().ifEmpty { null }, dose.trim().ifEmpty { null }) }, kind = ButtonKind.ACCENT, icon = "ic_check_plain")
        }) {
            FluentField(crop, { crop = it }, Modifier.fillMaxWidth(), placeholder = word(W.advice_crop_hint))
            FluentField(pest, { pest = it }, Modifier.fillMaxWidth(), placeholder = word(W.advice_pest_hint))
            FluentField(dose, { dose = it }, Modifier.fillMaxWidth(), placeholder = word(W.advice_dose_hint))
            Text(word(W.advice_disclaimer), color = LocalRoshanColors.current.textMuted, fontSize = 12.sp)
        }
    }
}
