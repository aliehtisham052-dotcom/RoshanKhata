package com.innovation313.roshankhata.desktop

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.Access
import com.innovation313.roshankhata.data.AuditAction
import com.innovation313.roshankhata.data.CustomerLink
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.KhataDatabase
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.ShopSettings
import com.innovation313.roshankhata.data.WhatsAppNumber
import com.innovation313.roshankhata.data.syncSql
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.words.LocalWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

/*
 * The customer's khata link on the computer (17 Nov 2026, audit L022): the phone's ⋮ → "Send khata link" on the open
 * customer's ⋮, on the shared rules (CustomerLink: the page sealed and put on the shop's hosting first, then the link
 * handed over; stopping takes it back and down). Gated EXPORT and written in Who did what as on the phone.
 *
 * Two differences from the phone, both the computer's own: the message is COPIED and WhatsApp is opened on the customer's
 * chat by the number alone (wa.me/<number>, as the computer's invoices are sent), never with the text in the address — the
 * link carries the page's key, and an address goes through the browser and WhatsApp's own web redirect; and the pages are
 * kept up to date by a quiet round every [KhataLinkDesk.GAP_MS] while the window is open (the phone's WorkManager send),
 * which sends nothing at all when no page changed, and nothing on a computer that never sent a link.
 */

/** This computer's own notes for the pages it sent (customer_link.properties beside app.properties): never in the book or a backup. */
class DeskLinkPlace(private val file: File) : CustomerLink.Place {
    private val props = Properties().apply {
        if (file.isFile) runCatching { file.reader(Charsets.UTF_8).use { load(it) } }
    }

    @Synchronized
    override fun get(key: String): String? = props.getProperty(key)

    @Synchronized
    override fun put(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        // A note that cannot be written is lost (the next send writes it again), never a failure of the window.
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writer(Charsets.UTF_8).use { props.store(it, "Roshan Khata customer link notes") }
            replaceAtomically(tmp, file)
        }
    }
}

object KhataLinkDesk {
    const val FILE = "customer_link.properties"
    /** Set the first time this computer sends a customer his link (the phone's "clink_used"): before it, no round runs. */
    private const val USED = "clink_used"
    /** How often the pages are sent again while the window is open, as the phone's send waits (CustomerLinkSync.GAP_MIN). */
    const val GAP_MS = 10 * 60_000L

    private val places = HashMap<String, DeskLinkPlace>()

    /** The one note file of this computer (one object per file, so two windows never write over each other's notes). */
    @Synchronized
    fun place(home: File): DeskLinkPlace = File(home, FILE).let { f -> places.getOrPut(f.absolutePath) { DeskLinkPlace(f) } }

    fun used(place: CustomerLink.Place): Boolean = place.get(USED) != null
    fun markUsed(place: CustomerLink.Place) = place.put(USED, "1")

    /** How the shop's pages look where the book has not said: the shop's name, its sign, the app's language. */
    fun look(settings: ShopSettings, language: String?): CustomerLink.Look =
        CustomerLink.Look(settings.businessName(), settings.currency(), language?.trim()?.takeIf { it.isNotEmpty() } ?: "en")

    /** The book's live sync key while it syncs: the phone and the computer then make the same links. */
    suspend fun bookKey(db: KhataDatabase): String? = runCatching { LiveSync.identity(db.syncSql())?.key }.getOrNull()

    /**
     * Why a link could not go now, in the owner's words: the phone's own list (PartyDetailActivity.linkTrouble), word for
     * word, so the two can never say it apart.
     */
    fun trouble(words: Words, r: CustomerLink.Result): String = when (r) {
        CustomerLink.Result.Unreachable -> words.text(W.clink_offline)
        CustomerLink.Result.Off -> words.text(W.clink_off)
        is CustomerLink.Result.Refused -> when (r.why) {
            "busy" -> words.text(W.clink_busy)
            "later", "day-limit" -> words.text(W.clink_limit_today)
            "full", "disk", "closed" -> words.text(W.clink_full)
            "wrong-pass" -> words.text(W.clink_wrong_pass)
            "halted" -> words.text(W.clink_halted)
            else -> words.text(W.clink_failed_code, r.why)
        }
        is CustomerLink.Result.Done -> words.text(W.clink_failed_code, if (r.lost > 0) "taken" else "waiting")
        else -> words.text(W.clink_failed_code, "not sent")
    }

    /** The phone's share, kept off the window's thread; a road to the server that fails is "no internet", anything else a fault by name. */
    suspend fun share(dao: KhataDao, db: KhataDatabase, place: CustomerLink.Place, scope: String, look: CustomerLink.Look,
                      partyUid: String, claim: Boolean): CustomerLink.Shared {
        markUsed(place)
        return runCatching { CustomerLink.share(dao, place, scope, look, partyUid, bookKey(db), claim) }.getOrElse {
            CustomerLink.Shared.NotYet(
                if (it is java.io.IOException) CustomerLink.Result.Unreachable
                else CustomerLink.Result.Refused("app " + it.javaClass.simpleName)
            )
        }
    }

    /** Every page that changed, sent now ([claim]: this copy writes the pages from now on); nothing on a computer that never sent one. */
    suspend fun flush(dao: KhataDao, db: KhataDatabase, place: CustomerLink.Place, scope: String, look: CustomerLink.Look,
                      claim: Boolean = false): CustomerLink.Result {
        if (!claim && !used(place)) return CustomerLink.Result.Idle
        return runCatching { CustomerLink.flush(dao, place, scope, look, bookKey(db), claim) }.getOrDefault(CustomerLink.Result.Unreachable)
    }
}

private sealed interface LinkStage {
    object Loading : LinkStage
    class Ask(val uid: String, val sent: Boolean) : LinkStage
    /** Another copy writes this book's pages; [sendAfter] the owner was sending a link when the server said so. */
    class OtherCopy(val uid: String, val sendAfter: Boolean) : LinkStage
    class Stopping(val uid: String) : LinkStage
    object Busy : LinkStage
    class Told(val text: String, val retry: (() -> Unit)? = null) : LinkStage
}

/**
 * The customer's khata link: send it (again), or stop it. [work] is the window's own supervised scope, so a send or a
 * stop finishes should the dialog close; [bookScope] names this book's notes (its file), as the phone's scope does.
 */
@Composable
fun KhataLinkDialog(
    party: PartyWithBalance,
    db: KhataDatabase,
    dao: KhataDao,
    settings: ShopSettings,
    home: File,
    bookScope: String,
    language: String?,
    work: CoroutineScope,
    onClose: () -> Unit
) {
    val words = LocalWords.current
    val c = LocalRoshanColors.current
    val place = remember(home) { KhataLinkDesk.place(home) }
    var stage by remember(party.id) { mutableStateOf<LinkStage>(LinkStage.Loading) }
    val look = KhataLinkDesk.look(settings, language)

    LaunchedEffect(party.id) {
        stage = withContext(Dispatchers.IO) {
            runCatching {
                val uid = dao.getParty(party.id)?.uid ?: return@runCatching null
                // The background round found another copy writing the pages: said first, with the way to take over.
                if (CustomerLink.otherCopy(place, bookScope)) LinkStage.OtherCopy(uid, sendAfter = false)
                else LinkStage.Ask(uid, CustomerLink.wasSent(dao, place, uid, KhataLinkDesk.bookKey(db)))
            }.getOrNull()
        } ?: LinkStage.Told(words.text(W.clink_not_now))
    }

    fun send(uid: String, claim: Boolean) {
        stage = LinkStage.Busy
        work.launch {
            val r = withContext(Dispatchers.IO) {
                KhataLinkDesk.share(dao, db, place, bookScope, look, uid, claim).also {
                    if (it is CustomerLink.Shared.Ready) runCatching { dao.log(AuditAction.KHATA_LINK_SENT, Access.userName, "parties", party.id) }
                }
            }
            stage = when (r) {
                is CustomerLink.Shared.Ready -> {
                    val shop = settings.businessName()?.takeIf { it.isNotBlank() } ?: words.text(W.app_name)
                    val message = words.text(W.clink_message, party.name, shop, r.url)
                    runCatching {
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(message), null)
                    }
                    // The chat by the number alone: the link (and its key) never goes into an address.
                    val number = party.phone?.let { WhatsAppNumber.of(it) }
                    if (number == null) LinkStage.Told(words.text(W.clink_copied_no_phone_pc))
                    else {
                        val opened = withContext(Dispatchers.IO) {
                            runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://wa.me/$number")) }
                        }
                        opened.fold({ LinkStage.Told(words.text(W.clink_copied_pc)) }, { LinkStage.Told(failText(words, it)) })
                    }
                }
                is CustomerLink.Shared.NotYet ->
                    if (r.why == CustomerLink.Result.NotHere) LinkStage.OtherCopy(uid, sendAfter = true)
                    // Nothing was marked as sent and nothing was written for the customer: said, with Try again.
                    else LinkStage.Told(KhataLinkDesk.trouble(words, r.why)) { send(uid, claim) }
            }
        }
    }

    fun takeOver(uid: String) {
        stage = LinkStage.Busy
        work.launch {
            val r = withContext(Dispatchers.IO) { KhataLinkDesk.flush(dao, db, place, bookScope, look, claim = true) }
            stage = when (r) {
                // This computer writes the pages now (or with its next round): on to the customer's link itself.
                is CustomerLink.Result.Done, CustomerLink.Result.Idle ->
                    LinkStage.Ask(uid, withContext(Dispatchers.IO) { CustomerLink.wasSent(dao, place, uid, KhataLinkDesk.bookKey(db)) })
                else -> LinkStage.Told(KhataLinkDesk.trouble(words, r))
            }
        }
    }

    fun stop(uid: String) {
        stage = LinkStage.Busy
        work.launch {
            val said = withContext(Dispatchers.IO) {
                val link = runCatching { CustomerLink.remove(dao, uid, place) }.getOrNull()
                runCatching { dao.log(AuditAction.KHATA_LINK_STOPPED, Access.userName, "parties", party.id) }
                val r = KhataLinkDesk.flush(dao, db, place, bookScope, look)
                // Down at the hosting when the book no longer waits to tell it (the phone's rule).
                val down = link == null || runCatching { dao.settingValue(CustomerLink.GONE + link.id) }.getOrNull() == null
                when {
                    down -> LinkStage.Told(words.text(W.clink_stopped_now))
                    r == CustomerLink.Result.NotHere -> LinkStage.OtherCopy(uid, sendAfter = false)
                    r == CustomerLink.Result.Unreachable -> LinkStage.Told(words.text(W.clink_stopped))
                    else -> LinkStage.Told(words.text(W.clink_stopped_waiting_pc))
                }
            }
            stage = said
        }
    }

    val title = word(W.clink_title)
    when (val s = stage) {
        LinkStage.Loading, LinkStage.Busy ->
            AppDialog(onCloseRequest = onClose, title = title, state = rememberDialogState(width = fitW(480.dp), height = fitH(240.dp))) {
                DialogFrame(title, subtitle = party.name, onClose = onClose, footer = {
                    FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
                }) {
                    Text(word(W.clink_sending), color = c.textMuted, fontSize = 14.sp)
                }
            }
        is LinkStage.Ask ->
            AppDialog(onCloseRequest = onClose, title = title, state = rememberDialogState(width = fitW(520.dp), height = fitH(340.dp))) {
                DialogFrame(title, subtitle = party.name, onClose = onClose, footerStart = {
                    if (s.sent) FluentButton(word(W.clink_stop), { stage = LinkStage.Stopping(s.uid) }, kind = ButtonKind.DANGER)
                }, footer = {
                    FluentButton(word(W.cancel), onClose, kind = ButtonKind.STANDARD)
                    FluentButton(word(if (s.sent) W.clink_send_again else W.clink_send), { send(s.uid, claim = false) },
                        kind = ButtonKind.ACCENT, icon = "ic_action_whatsapp")
                }) {
                    Text(word(if (s.sent) W.clink_body_sent else W.clink_body), color = c.ink, fontSize = 14.sp)
                }
            }
        is LinkStage.OtherCopy ->
            AskDialog(title, word(W.clink_other_copy_pc), word(W.clink_take_over_pc), subtitle = party.name,
                onConfirm = { if (s.sendAfter) send(s.uid, claim = true) else takeOver(s.uid) }, onDismiss = onClose)
        is LinkStage.Stopping ->
            AskDialog(word(W.clink_stop), word(W.clink_stop_body), word(W.clink_stop), subtitle = party.name, danger = true,
                onConfirm = { stop(s.uid) }, onDismiss = { stage = LinkStage.Ask(s.uid, sent = true) })
        is LinkStage.Told -> {
            val retry = s.retry
            if (retry == null) TellDialog(title, s.text, onClose, height = 300)
            else AskDialog(title, s.text, word(W.clink_try_again), subtitle = party.name, onConfirm = retry, onDismiss = onClose)
        }
    }
}
