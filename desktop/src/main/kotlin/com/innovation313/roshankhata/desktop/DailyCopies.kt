package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily copies on the computer (KMP Phase 7bb), the phone's Snapshots: the
 * open business's book copied once a day, the last seven kept, each copy
 * read back and checked before it counts. Written in the backup format
 * (BackupCodec), so a copy is also a file the phone can restore.
 *
 * Which copies are kept is decided by the DAY IN THEIR NAME, not by the
 * file's time (21 Oct 2026). A laptop whose clock fell back to 2015 (a dead
 * CMOS battery) gave every new copy a 2015 file time, so the rotation took
 * the copy it had just written for the oldest and deleted it at once — the
 * backups stopped and nothing said so. Now the copy just written is never
 * the one deleted, and while the clock reads earlier than the newest copy's
 * day nothing is deleted at all ([clockBehind]). The day copies and the
 * safety copies taken before a restore or a merge are kept apart, so a busy
 * day of restores cannot push the week's copies out.
 */
object DailyCopies {
    private const val KEEP = 7
    /** Copies with a label (before-restore, before-merge): the newest few. */
    private const val KEEP_LABELLED = 5
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** One copy at a time in this app (the hourly one, the after-sale one, the closing one). */
    private val writing = kotlinx.coroutines.sync.Mutex()

    fun dir(home: File, businessId: Long) = File(home, "daily/b$businessId")

    /**
     * The owner's second place (Phase 9c): a USB stick or the Google Drive
     * folder. Each business gets its own folder there, and each computer its
     * own, named so a person browsing the stick knows what it is:
     * "Business-1 (SHOP-PC)". Today's copy there is rewritten when the app
     * opens and when it closes, so the day's last work leaves the computer too.
     *
     * The computer's name since 22 Oct 2026 (the review): a shop computer and
     * a home computer sharing one Google Drive folder both wrote
     * Business-1/<day>.txt, each over the other's, and a damaged book could be
     * rebuilt from the other computer's book. Copies an older version left in
     * "Business-1" stay there, for a restore by hand.
     */
    fun outside(folder: File, businessId: Long, computer: String = computerName()) =
        File(folder, "RoshanKhata-Backup/Business-$businessId ($computer)")

    /** This computer's name as Windows gives it (letters, digits, - and _ only); "computer" where there is none. */
    fun computerName(): String =
        System.getenv("COMPUTERNAME").orEmpty().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40).ifEmpty { "computer" }

    /** The day a copy is of, from its name (yyyy-MM-dd…), or "" for a name that has none. */
    fun dayOf(f: File): String = f.name.take(10).takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }.orEmpty()

    private fun isLabelled(f: File) = f.name.length > "yyyy-MM-dd.txt".length

    /** What a copy is: a day's own, or one kept before a restore or a merge (its label in the name). */
    enum class Kind { DAY, BEFORE_RESTORE, BEFORE_MERGE }

    /** A copy's kind from its name ("<day>-before-restore-<ms>.txt", "<day>-before-merge-<ms>.txt"). */
    fun kindOf(f: File): Kind = when {
        f.name.drop(10).startsWith("-before-restore") -> Kind.BEFORE_RESTORE
        f.name.drop(10).startsWith("-before-merge") -> Kind.BEFORE_MERGE
        else -> Kind.DAY
    }

    private val byNewest: Comparator<File> =
        compareByDescending<File> { dayOf(it) }.thenByDescending { it.lastModified() }

    /** The copies in [dir], newest day first (by name, then by file time within a day). */
    fun list(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.sortedWith(byNewest) ?: emptyList()

    /** The newest copy across [dirs] — what a rebuilt book is taken from first. */
    fun newestFirst(dirs: List<File>): List<File> = dirs.flatMap { list(it) }.sortedWith(byNewest)

    private fun dayText(now: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(now))

    /**
     * True when this computer's clock reads a day earlier than the newest copy
     * in [dir] was made for: the clock went back. Copies are still written,
     * but none is deleted until the clock is right again.
     */
    fun clockBehind(dir: File, now: Long = System.currentTimeMillis()): Boolean {
        val newest = list(dir).map { dayOf(it) }.filter { it.isNotEmpty() }.maxOrNull() ?: return false
        return newest > dayText(now)
    }

    /**
     * Today's copy (or a labelled one): written to a temporary file, read back
     * whole, and only then given its name. A copy that does not read back is
     * thrown away, never kept as if it were good. Returns the copy that is now
     * on disk — or null when none could be made, which a restore or a merge
     * takes as "stop" (the book is only replaced once it is safely kept).
     * Without [refresh], an existing copy of today is left as it is and returned.
     */
    suspend fun makeToday(
        dao: KhataDao,
        settings: FileShopSettings,
        dir: File,
        label: String? = null,
        refresh: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): File? = withContext(Dispatchers.IO) {
        writing.withLock {
            dir.mkdirs()
            val day = dayText(now)
            val name = if (label == null) "$day.txt" else "$day-$label.txt"
            val target = File(dir, name)
            if (label == null && target.isFile && !refresh) return@withLock target
            // Leftovers of a copy cut short (a power cut) go first; a fresh name for this one.
            dir.listFiles { f -> f.name.endsWith(".tmp") && now - f.lastModified() > DAY_MS }?.forEach { it.delete() }
            val tmp = File(dir, "$name.${System.nanoTime()}.tmp")
            // Written and read back on this computer's own disk first, and only then copied out
            // (22 Oct 2026, the review): written straight to a USB stick or the Google Drive folder,
            // the copy held the book's transaction for as long as the stick took, and a sale at
            // the counter waited for it. What reaches the folder is read back too.
            val local = runCatching { File.createTempFile("rk-copy-", ".tmp") }.getOrNull() ?: return@withLock null
            val written = try {
                runCatching {
                    local.bufferedWriter(Charsets.UTF_8).use {
                        BackupCodec.exportTo(dao, settings.businessName(), BackupCodec.profileToJson(settings), it)
                    }
                    BackupCodec.parseFile(local).first is BackupCodec.ImportResult.Ok && run {
                        java.nio.file.Files.copy(local.toPath(), tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        tmp.length() == local.length() && BackupCodec.parseFile(tmp).first is BackupCodec.ImportResult.Ok
                    } && runCatching {
                        java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        true
                    }.getOrDefault(false)
                }.getOrDefault(false)
            } finally {
                local.delete()
            }
            if (!written) {
                tmp.delete()
                return@withLock null
            }
            // The week's copies, and a few safety copies: the newest by their day. Never the
            // copy just written, and nothing at all while the clock reads behind the copies.
            if (!clockBehind(dir, now)) {
                val (labelled, days) = list(dir).filter { it != target }.partition { isLabelled(it) }
                days.drop(KEEP - (if (label == null) 1 else 0)).forEach { it.delete() }
                labelled.drop(KEEP_LABELLED - (if (label != null) 1 else 0)).forEach { it.delete() }
            }
            target
        }
    }
}

@Composable
fun DailyCopiesPane(
    dao: KhataDao,
    settings: FileShopSettings,
    dir: File,
    outsideFolder: File?,
    outsideDir: File?,
    onFolder: (File?) -> Unit,
    /** The open book's sync door: a restore pauses its live sync, and ends it once the restore went through. */
    sync: com.innovation313.roshankhata.data.SyncSql? = null,
    onRestored: () -> Unit
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var copies by remember { mutableStateOf<List<File>>(emptyList()) }
    // The chosen folder's own copies too (Marhala 4, part 9 — the audit: only this computer's were listed, so a
    // copy kept on the USB stick or in Drive could not be gone back to from here). Read when the page opens.
    var outside by remember { mutableStateOf<List<File>>(emptyList()) }
    var asking by remember { mutableStateOf<File?>(null) }
    var notice by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(version, outsideDir) {
        copies = withContext(Dispatchers.IO) { DailyCopies.list(dir) }
        outside = withContext(Dispatchers.IO) { runCatching { outsideDir?.takeIf { it.isDirectory }?.let { DailyCopies.list(it) } }.getOrNull().orEmpty() }
    }
    val restoredTitle = word(W.snapshot_restored_title)
    val damagedTitle = word(W.snapshot_unhealthy_title)
    val damagedBody = word(W.snapshot_unhealthy_body)
    val failed = word(W.snapshot_restore_failed)
    val keepFailed = word(W.restore_keep_failed)

    @Composable
    fun CopyRow(f: File) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                // A copy kept before a restore or a merge says so, as the phone's list (25 Oct 2026, the audit:
                // it read like any other day, and going back to it undid the restore unknowingly).
                val at = Format.dateTime(f.lastModified())
                Text(
                    when (DailyCopies.kindOf(f)) {
                        DailyCopies.Kind.BEFORE_RESTORE -> word(W.snapshot_before_restore, at)
                        DailyCopies.Kind.BEFORE_MERGE -> word(W.snapshot_before_merge, at)
                        DailyCopies.Kind.DAY -> at
                    },
                    color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Text(word(W.snapshot_size_kb, (f.length() / 1024).toInt()), color = c.textMuted, fontSize = 12.sp)
            }
            FluentButton(word(W.snapshot_restore_action), { asking = f }, kind = ButtonKind.STANDARD)
        }
        Rule()
    }

    FluentPage(word(W.snapshots_title), word(W.snapshots_subtitle), scroll = false) {
        Text(word(W.auto_backup_note), color = c.textMuted, fontSize = 13.sp)
        OutsideFolderCard(outsideFolder, outsideDir, onFolder)
        if (copies.isEmpty() && outside.isEmpty()) {
            EmptyState("ic_feature_backup", word(W.snapshots_empty), null, Modifier.weight(1f))
        } else ListCard(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (outside.isNotEmpty()) item(key = "here") { CopiesHead(word(W.snapshots_here_pc)) }
                if (copies.isEmpty()) item(key = "none-here") {
                    Text(word(W.snapshots_empty), Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = c.textMuted, fontSize = 13.sp)
                }
                items(copies, key = { "h/" + it.name }) { f -> CopyRow(f) }
                if (outside.isNotEmpty()) {
                    item(key = "there") { CopiesHead(word(W.auto_folder_copies)) }
                    items(outside, key = { "o/" + it.name }) { f -> CopyRow(f) }
                }
            }
        }
    }

    asking?.let { f ->
        // The copy is read before the question, so what it would take away can be said (21 Oct 2026).
        var parsed by remember(f) { mutableStateOf<BackupCodec.ParsedBackup?>(null) }
        LaunchedEffect(f) { parsed = withContext(Dispatchers.IO) { BackupCodec.parseFile(f).second } }
        val data = parsed
        val loss = if (data != null) rememberLoss(dao, data) else null
        var agreed by remember(f) { mutableStateOf(false) }
        var live by remember(f) { mutableStateOf(false) }
        LaunchedEffect(f) {
            live = withContext(Dispatchers.IO) { sync?.let { runCatching { com.innovation313.roshankhata.data.LiveSync.identity(it) != null }.getOrNull() } ?: false }
        }
        fun restore() {
            asking = null
            scope.launch {
                val (result, data) = withContext(Dispatchers.IO) { BackupCodec.parseFile(f) }
                notice = if (result !is BackupCodec.ImportResult.Ok || data == null) {
                    damagedTitle to damagedBody
                } else runCatching {
                    // Today's book is copied and kept first, so going back can be undone; no copy,
                    // no restore (21 Oct 2026: a failed copy was passed over in silence).
                    DailyCopies.makeToday(dao, settings, dir, label = "before-restore-" + System.currentTimeMillis())
                        ?: error(keepFailed)
                    // Not cancelled half way by the page closing (23 Oct 2026, the review), as Main.kt's restore:
                    // a cancelled resume left the book paused, then turned off at its next opening.
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                        // Live sync pauses first, so the restore's own writes are not sent to the phone as
                        // changes (22 Oct 2026, the review: they removed the phone's newer work); it stops once
                        // the restore went through (the two must be joined again), and a failure changes nothing.
                        val door = sync
                        val wasLive = door != null && runCatching { com.innovation313.roshankhata.data.LiveSync.pause(door) }.getOrDefault(false)
                        try {
                            BackupCodec.restoreLedger(dao, data)
                        } catch (e: Exception) {
                            if (wasLive && door != null) runCatching { com.innovation313.roshankhata.data.LiveSync.resume(door) }
                            throw e
                        }
                        if (wasLive && door != null) {
                            com.innovation313.roshankhata.data.LiveSync.disable(door, com.innovation313.roshankhata.data.LiveSync.OFF_RESTORED)
                            com.innovation313.roshankhata.data.NumberMark.mark = null
                        }
                        data.businessProfile?.let { BackupCodec.restoreProfile(settings, it) }
                    }
                    onRestored()
                    restoredTitle to ""
                }.getOrElse { e -> damagedTitle to (if (e is IllegalStateException) e.message ?: failed else failed) }
                version++
            }
        }
        val title = word(W.snapshot_restore_title)
        AppDialog(onCloseRequest = { asking = null }, onPreviewKeyEvent = escCloses { asking = null }, title = title,
            state = rememberDialogState(width = fitW(560.dp), height = fitH(520.dp))) {
            DialogFrame(title, subtitle = Format.dateTime(f.lastModified()), onClose = { asking = null }, footer = {
                FluentButton(word(W.cancel), { asking = null }, kind = ButtonKind.STANDARD)
                // A copy that does not read is still offered: the restore says it is damaged.
                FluentButton(word(W.snapshot_restore_action), { restore() }, kind = ButtonKind.DANGER, enabled = parsed == null || mayReplace(loss, agreed))
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (data != null) LossNote(loss, agreed) { agreed = it }
                    if (live) Text(word(W.restore_stops_live), color = c.ink, fontWeight = FontWeight.Medium)
                    Text(word(W.snapshot_restore_warning, Format.dateTime(f.lastModified())), color = c.ink, fontSize = 14.sp)
                }
            }
        }
    }
    notice?.let { (title, body) -> TellDialog(title, body, { notice = null }) }
}

/** A group's head in the copies list. */
@Composable
private fun CopiesHead(text: String) {
    Text(text, Modifier.fillMaxWidth().background(Look.layerAlt).padding(horizontal = 16.dp, vertical = 8.dp),
        color = LocalRoshanColors.current.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Rule()
}

/**
 * Where the second copy goes (Phase 9c). What it shows is read from the
 * folder itself, every few seconds while the page is open: the newest copy
 * there, or that the folder cannot be reached (the stick is out).
 */
@Composable
private fun OutsideFolderCard(folder: File?, dir: File?, onFolder: (File?) -> Unit) {
    val c = LocalRoshanColors.current
    val chooseTitle = word(W.auto_folder_choose)
    // Swing's chooser has no words of its own in the app's languages (R57, 23 Oct 2026): ours are handed to it.
    val chooserWords = ChooserWords(
        cancel = word(W.cancel), lookIn = word(W.fc_look_in), folder = word(W.fc_folder_name), ofType = word(W.fc_files_of_type),
        rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    )
    var reachable by remember { mutableStateOf(true) }
    var last by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(folder, dir) {
        while (true) {
            withContext(Dispatchers.IO) {
                reachable = folder?.isDirectory ?: true
                last = dir?.let { DailyCopies.list(it).firstOrNull() }
            }
            kotlinx.coroutines.delay(4000)
        }
    }
    Column(
        Modifier.fillMaxWidth().card().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(word(W.auto_folder_title), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(word(W.auto_folder_help), color = c.textMuted, fontSize = 13.sp)
        when {
            folder == null -> Text(word(W.auto_folder_none), color = c.textMuted, fontSize = 13.sp)
            !reachable -> Text(word(W.auto_folder_missing), color = c.owedToMe, fontSize = 13.sp)
            else -> {
                Text(folder.absolutePath, color = c.ink, fontSize = 13.sp)
                last?.let { Text(word(W.auto_folder_last, Format.dateTime(it.lastModified())), color = c.brandGreenText, fontSize = 13.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FillButton(chooseTitle, c.brandGreen) {
                chooseFolder(chooseTitle, folder, chooserWords)?.let(onFolder)
            }
            if (folder != null) FluentButton(word(W.auto_folder_stop), { onFolder(null) }, kind = ButtonKind.DANGER)
        }
    }
}

/** The folder picker's own words in the app's language, and its direction. */
private class ChooserWords(val cancel: String, val lookIn: String, val folder: String, val ofType: String, val rtl: Boolean)

/** Windows' own folder picker (FileDialog picks files only); null when cancelled. */
private fun chooseFolder(title: String, current: File?, words: ChooserWords): File? {
    runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
    // Read by the chooser's look when it is built, so they are set first (the JDK has these labels in English only).
    runCatching {
        val ui = javax.swing.UIManager.getDefaults()
        ui["FileChooser.cancelButtonText"] = words.cancel
        ui["FileChooser.openButtonText"] = title
        ui["FileChooser.saveButtonText"] = title
        ui["FileChooser.lookInLabelText"] = words.lookIn
        ui["FileChooser.fileNameLabelText"] = words.folder
        ui["FileChooser.folderNameLabelText"] = words.folder
        ui["FileChooser.filesOfTypeLabelText"] = words.ofType
    }
    val chooser = javax.swing.JFileChooser(current ?: File(System.getProperty("user.home"))).apply {
        dialogTitle = title
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        applyComponentOrientation(if (words.rtl) java.awt.ComponentOrientation.RIGHT_TO_LEFT else java.awt.ComponentOrientation.LEFT_TO_RIGHT)
    }
    return if (chooser.showDialog(null, title) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
