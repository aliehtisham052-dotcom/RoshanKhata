package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.innovation313.roshankhata.data.BusinessDeleteSummary
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.LiveCloud
import com.innovation313.roshankhata.data.syncSql
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import java.io.File
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Several businesses on one computer (KMP Phase 7z), the phone's Businesses
 * rules: each shop is its own book (its own database file and its own shop
 * settings), one is open at a time, and switching closes the old book
 * before the new one opens.
 *
 * Business 1 is the book the computer already had (khata.db,
 * shop.properties) and can never be deleted; others are khata_b<id>.db and
 * shop_b<id>.properties. The open business cannot be deleted either: switch
 * away first. The registry lives in businesses.properties, written whole
 * through a temporary file, so a crash mid-write leaves the old one.
 */
class Businesses(private val home: File) {

    data class Business(val id: Long, val name: String?)

    private val file = File(home, "businesses.properties")
    private val props = Properties().apply { if (file.isFile) file.reader(Charsets.UTF_8).use { load(it) } }

    fun dbFile(id: Long) = File(home, if (id == 1L) "khata.db" else "khata_b$id.db")
    fun settingsFile(id: Long) = File(home, if (id == 1L) "shop.properties" else "shop_b$id.properties")

    /** Every business, Business 1 first; Business 1 is always there. */
    fun list(): List<Business> {
        val ids = (props.getProperty(KEY_IDS)?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList())
        return (listOf(1L) + ids.filter { it != 1L }).distinct().map { Business(it, props.getProperty("name.$it")?.takeIf { n -> n.isNotBlank() }) }
    }

    /** The device a business's book syncs with live, or null; kept here so the list can say so without opening each book. */
    fun live(id: Long): String? = props.getProperty("live.$id")?.takeIf { it.isNotBlank() }

    fun setLive(id: Long, with: String?) {
        if (live(id) == with) return
        if (with == null) props.remove("live.$id") else props.setProperty("live.$id", with)
        save()
    }

    /**
     * The live book id a business keeps with its devices, or null when it does not sync (R29, 23 Oct 2026):
     * a phone's offer of a book another business here already keeps is sent to that business, never joined
     * a second time (two businesses with one book drifted apart once the phone dropped the older one).
     */
    fun book(id: Long): String? = props.getProperty("book.$id")?.takeIf { it.isNotBlank() }

    @Synchronized
    fun setBook(id: Long, value: String?) {
        if (book(id) == value) return
        if (value == null) props.remove("book.$id") else props.setProperty("book.$id", value)
        save()
    }

    /** The business other than [except] that keeps [bookId] live, or null. */
    fun holderOf(bookId: String, except: Long): Long? = list().firstOrNull { it.id != except && book(it.id) == bookId }?.id

    fun activeId(): Long = props.getProperty(KEY_ACTIVE)?.toLongOrNull()?.takeIf { id -> list().any { it.id == id } } ?: 1L

    /** The highest id ever given out here (kept by create and delete). */
    private fun highest(): Long = props.getProperty(KEY_HIGHEST)?.toLongOrNull() ?: 0L

    /**
     * The highest business id any file on this computer still names: a computer that deleted its top
     * business before KEY_HIGHEST was kept still has that shop's copies, pictures or settings lying there.
     */
    fun leftoverMax(): Long {
        val top = Regex("""^(?:khata_b(\d+)\.db.*|shop_b(\d+)\.properties.*|card_b(\d+)\.properties.*|poster_b(\d+)\.properties.*|party_photos_b(\d+)|entry_signatures_b(\d+)|reminders_b(\d+)\.properties.*)$""")
        val inHome = home.listFiles()?.mapNotNull { f -> top.find(f.name)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toLongOrNull() }.orEmpty()
        val folder = Regex("""^b(\d+)$""")
        val inDirs = listOf("daily", "profile").flatMap { d ->
            File(home, d).listFiles()?.mapNotNull { f -> folder.find(f.name)?.groupValues?.get(1)?.toLongOrNull() }.orEmpty()
        }
        return (inHome + inDirs).maxOrNull() ?: 0L
    }

    /**
     * A new business. Its id is past every id this computer ever gave out, not only the ones still listed
     * (R04/R43, 23 Oct 2026, the phone's 21 Oct fix): a deleted shop's number went to the next new shop, and
     * with it the old shop's daily copies, logo, signature, stamp, payment QR and card/poster settings.
     */
    @Synchronized
    fun create(name: String): Business {
        val id = maxOf(list().maxOf { it.id }, highest(), runCatching { leftoverMax() }.getOrDefault(0L)) + 1
        props.setProperty(KEY_HIGHEST, id.toString())
        props.setProperty(KEY_IDS, (list().map { it.id } + id).joinToString(","))
        props.setProperty("name.$id", name.trim())
        save()
        return Business(id, name.trim())
    }

    @Synchronized
    fun rename(id: Long, name: String) { props.setProperty("name.$id", name.trim()); save() }

    @Synchronized
    fun switchTo(id: Long) { props.setProperty(KEY_ACTIVE, id.toString()); save() }

    fun canDelete(id: Long): Boolean = id != 1L && id != activeId()

    /**
     * The business removed from this computer: its book, settings, daily copies, shop pictures, customer
     * photos, card and poster settings and any book set aside after damage, then its row (R04/R43, 23 Oct
     * 2026). The outside folder (USB, Drive) is left alone. A book Windows will not let go of (antivirus,
     * another program) keeps everything and throws, so the window can say the delete did not happen.
     * Slow (it may open the book and reach the relays): never on the window's thread.
     */
    @Synchronized
    fun delete(id: Long) {
        check(canDelete(id)) { "Business $id cannot be deleted (it is Business 1 or the open one)" }
        val db = dbFile(id)
        // A book that synced live says goodbye to the relays first (best effort, 23 Oct 2026): the phone must not
        // keep leaving parcels for a book that is gone.
        // Only a real SQLite book is opened for it (its first 16 bytes say so): a file that is not one must never be
        // held open by a failed open while it is being deleted (Windows keeps an open file from going).
        val isBook = runCatching { db.inputStream().use { s -> String(s.readNBytes(16), Charsets.US_ASCII) } == "SQLite format 3\u0000" }.getOrDefault(false)
        if (isBook) runCatching {
            val book = DesktopDatabase.open(db)
            try {
                kotlinx.coroutines.runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(5_000L) { LiveCloud.leaveAll(book.syncSql()) }
                }
            } finally { book.close() }
        }
        // The book first: one Windows holds stays, and with it everything else and the row.
        listOf(db, File(db.path + "-wal"), File(db.path + "-shm"), File(db.path + "-journal")).forEach { it.delete() }
        if (db.exists()) throw java.io.IOException("The book ${db.name} could not be removed: another program is using it")
        // Everything else that is this business's by its number.
        val sameName = { f: File -> f.name.startsWith(db.name + ".corrupt-") || f.name.startsWith(db.name + ".recovering") }
        home.listFiles()?.filter(sameName)?.forEach { it.delete() }
        listOf(settingsFile(id), File(settingsFile(id).path + ".tmp"), File(home, "card_b$id.properties"), File(home, "poster_b$id.properties"),
            File(home, "reminders_b$id.properties"), File(home, "reminders_b$id.properties.tmp"))
            .forEach { it.delete() }
        listOf(DailyCopies.dir(home, id), File(home, "profile/b$id"), Photos.partyDir(home, id), EntrySignatures.dir(home, id)).forEach { it.deleteRecursively() }
        // The highest id stays remembered, so this one is never given out again.
        props.setProperty(KEY_HIGHEST, maxOf(highest(), id).toString())
        props.remove("live.$id")
        props.remove("book.$id")
        props.setProperty(KEY_IDS, list().map { it.id }.filter { it != id && it != 1L }.joinToString(","))
        props.remove("name.$id")
        save()
    }

    private fun save() {
        home.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer(Charsets.UTF_8).use { props.store(it, "Roshan Khata businesses") }
        // Swapped in one step (21 Oct 2026): Windows will not rename over a file, and the old way deleted
        // the settings first — a power cut in between lost them all (App Lock, counter mode, the backup folder).
        replaceAtomically(tmp, file)
    }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_ACTIVE = "active"
        const val KEY_HIGHEST = "highest"
    }
}

/**
 * The businesses on this computer: open one, add one, rename, and delete
 * one that is not Business 1 and not open — after its own numbers are shown
 * and its name typed back, as on the phone.
 */
@Composable
fun BusinessesDialog(
    businesses: Businesses,
    active: Long,
    onSwitch: (Long) -> Unit,
    onClose: () -> Unit,
    /**
     * A business's shop settings: the open one's own instance for the open business (R27, 23 Oct 2026). A
     * second instance renamed the file while the app's kept the old name in memory and wrote it back on its
     * next save, and the open shop printed the old name until a restart.
     */
    settingsOf: (Long) -> FileShopSettings = { FileShopSettings(businesses.settingsFile(it)) },
    onRenamed: (Long) -> Unit = {}
) {
    val c = LocalRoshanColors.current
    val words = com.innovation313.roshankhata.words.LocalWords.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val list = remember(version) { businesses.list() }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Businesses.Business?>(null) }
    // The summary is null when the book could not be read (R75): it can still be deleted, its figures unknown.
    var deleting by remember { mutableStateOf<Pair<Businesses.Business, BusinessDeleteSummary?>?>(null) }
    var unreadable by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val needName = word(W.business_name_needed)
    var problem by remember { mutableStateOf<String?>(null) }

    fun shown(b: Businesses.Business): String =
        settingsOf(b.id).businessName() ?: b.name ?: "Roshan Khata"

    AppDialog(
        onCloseRequest = onClose, onPreviewKeyEvent = escCloses(onClose),
        title = word(W.businesses_title),
        state = rememberDialogState(width = fitW(520.dp), height = fitH(640.dp))
    ) {
        Column(
            Modifier.fillMaxSize().background(c.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            list.forEach { b ->
                val on = b.id == active
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (on) c.brandGreen.copy(alpha = 0.25f) else c.pageBg, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(shown(b), Modifier.weight(1f), color = c.ink,
                        fontWeight = FontWeight.Bold)
                    // The book that syncs live with a phone, told apart from the computer's own at a glance (22 Oct 2026).
                    businesses.live(b.id)?.let { with -> LiveBadge(with) }
                    if (!on) FluentButton(word(W.business_switch_open), { onSwitch(b.id) }, kind = ButtonKind.SUBTLE)
                    TextButton({ renaming = b }) {
                        Text(word(W.business_rename), color = c.ink)
                    }
                    if (businesses.canDelete(b.id)) TextButton(enabled = !busy, onClick = {
                        scope.launch {
                            // A damaged, foreign or newer book must still be deletable (R75, 23 Oct 2026): its
                            // error used to escape here, so the question never came and the window could close.
                            val summary = withContext(Dispatchers.IO) {
                                runCatching {
                                    // Opened on its own and closed again: never the open book's instance.
                                    val db = DesktopDatabase.open(businesses.dbFile(b.id))
                                    try { db.khataDao().deleteSummary() } finally { db.close() }
                                }
                            }
                            unreadable = summary.exceptionOrNull()?.let { failText(words, it) }
                            deleting = b to summary.getOrNull()
                        }
                    }) {
                        Text(word(W.business_delete), color = c.owedToMe)
                    }
                }
            }
            Outlined(
                newName, { newName = it; problem = null }, singleLine = true,
                label = { Text(word(W.business_name_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            problem?.let { Text(it, color = c.owedToMe) }
            FillButton(word(W.business_add), c.brandGreen) {
                if (newName.isBlank()) { problem = needName; return@FillButton }
                val b = businesses.create(newName)
                // The new shop's own name in its own settings, so statements print it.
                FileShopSettings(businesses.settingsFile(b.id)).setBusinessName(newName)
                newName = ""
                onSwitch(b.id)
            }
        }
    }

    renaming?.let { b ->
        var name by remember(b) { mutableStateOf(shown(b)) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(word(W.business_rename)) },
            text = { Outlined(name, { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    businesses.rename(b.id, name)
                    settingsOf(b.id).setBusinessName(name)
                    renaming = null; version++
                    onRenamed(b.id)
                }) { Text(word(W.save)) }
            },
            dismissButton = { TextButton({ renaming = null }) { Text(word(W.cancel)) } }
        )
    }

    deleting?.let { (b, summary) ->
        val name = shown(b)
        var typed by remember(b) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = null },
            title = { Text(word(W.business_delete_confirm_title, name)) },
            text = {
                Column {
                    if (summary != null) Text(
                        word(W.business_delete_confirm_body,
                            summary.partyCount,
                            // Which way that shop's net runs, in that shop's own sign (25 Oct 2026, the audit;
                            // the phone's rule of 23 Oct): it printed unsigned, in the open shop's currency.
                            word(com.innovation313.roshankhata.screens.balanceSideWord(summary.netBalance)) + " " +
                                Format.money(summary.netBalance, settingsOf(b.id).currency()))
                    )
                    else unreadable?.let { Text(it, color = c.owedToMe) }
                    Outlined(typed, { typed = it }, singleLine = true,
                        label = { Text(word(W.business_delete_type_hint, name)) })
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && typed.trim() == name.trim(), onClick = {
                    // Off the window's thread (R43/R75): it may open the book and say goodbye to the relays.
                    busy = true
                    scope.launch {
                        val failed = withContext(Dispatchers.IO) { runCatching { businesses.delete(b.id) }.exceptionOrNull() }
                        problem = failed?.let { failText(words, it) }
                        busy = false
                        deleting = null; version++
                    }
                }) { Text(word(W.delete_forever), color = c.owedToMe) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text(word(W.cancel)) } }
        )
    }
}
