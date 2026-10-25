package com.innovation313.roshankhata.desktop

import com.innovation313.roshankhata.data.audit
import com.innovation313.roshankhata.data.auditAmount
import com.innovation313.roshankhata.data.AuditAction as AuditActs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.innovation313.roshankhata.data.BackupCodec
import com.innovation313.roshankhata.data.DesktopDatabase
import com.innovation313.roshankhata.data.FileShopSettings
import com.innovation313.roshankhata.data.Money
import com.innovation313.roshankhata.data.PartyWithBalance
import com.innovation313.roshankhata.data.KhataDao
import com.innovation313.roshankhata.data.LedgerEntry
import com.innovation313.roshankhata.data.Party
import com.innovation313.roshankhata.screens.PartyLedgerScreen
import com.innovation313.roshankhata.screens.RecycleBinScreen
import com.innovation313.roshankhata.screens.balanceColour
import com.innovation313.roshankhata.screens.AboutScreen
import com.innovation313.roshankhata.screens.LocalRoshanColors
import com.innovation313.roshankhata.screens.RoshanTheme
import com.innovation313.roshankhata.ui.BidiPort
import com.innovation313.roshankhata.ui.Format
import com.innovation313.roshankhata.ui.NameSearch
import com.innovation313.roshankhata.words.AppLanguages
import com.innovation313.roshankhata.words.ProvideWords
import com.innovation313.roshankhata.words.W
import com.innovation313.roshankhata.words.WP
import com.innovation313.roshankhata.words.Words
import com.innovation313.roshankhata.words.desktopWords
import com.innovation313.roshankhata.words.plural
import com.innovation313.roshankhata.words.word
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import com.innovation313.roshankhata.data.LiveSync
import com.innovation313.roshankhata.data.syncSql
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale

/**
 * Roshan Khata on the computer, first version (KMP Phase 6a): the book's
 * customers with their balances, search, a phone backup restored into it,
 * and About. Everything it shows comes from the shared module — the
 * database, the backup format, the money format, the words.
 */
fun main(args: Array<String>) {
    // CI's packaged-runtime check (desktop.yml): the app's own launcher and runtime, nothing of the app opened.
    if (args.firstOrNull() == "--runtime-self-check") { RuntimeSelfCheck.main(args); return }
    // Live sync's port must be free again right after a restart (22 Oct 2026): Windows' JDK binds exclusively by
    // default, and then every connection still in TIME_WAIT (four minutes) keeps port 47313 from being opened.
    System.setProperty("sun.net.useExclusiveBind", "false")
    val home = DesktopDatabase.home()
    // Any crash is written down before anything else runs (Report a problem shows it).
    CrashLog.install(home)
    // Live sync's line to the relay is the JDK's own WebSocket (23 Oct 2026).
    com.innovation313.roshankhata.data.LiveLines.maker = com.innovation313.roshankhata.data.JdkLines
    // One Roshan Khata at a time on this computer (21 Oct 2026, SingleInstance).
    val lock = SingleInstance.acquire(home)
    if (lock == null) { SingleInstance.tellAlreadyOpen(); return }
    clearOldNativeCopies()
    // Books from a phone that were never answered (a crash, a closed window) do not stay on disk (R41).
    PhoneSync.clearIncoming(home)
    try { application { app(home) } } finally { lock.release() }
}

/**
 * SQLite's library is unpacked into Windows' TEMP folder every time the app
 * starts, and the old copies were never removed — some 3 MB a start, for ever
 * (21 Oct 2026, the critical review). Copies older than a day are cleared on
 * the way in; the one in use is locked by Windows and simply stays.
 */
private fun clearOldNativeCopies() {
    runCatching {
        val tmp = java.io.File(System.getProperty("java.io.tmpdir") ?: return)
        val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        tmp.listFiles { f -> f.isFile && f.name.startsWith("sqliteJni", ignoreCase = true) && f.name.endsWith(".dll", ignoreCase = true) && f.lastModified() < dayAgo }
            ?.forEach { runCatching { it.delete() } }
    }
}

/** A small window while the book is checked and opened, or why it could not be (21 Oct 2026). */
@Composable
private fun OpeningWindow(
    words: Words,
    failed: Boolean,
    onClose: () -> Unit,
    /** Why it could not be opened, for support (R85); shown small under the sentence. */
    detail: String? = null,
    /** Another business to open instead (R28): its button's words and what it does. */
    other: Pair<String, () -> Unit>? = null
) {
    Window(
        onCloseRequest = onClose,
        title = words.text(W.app_name),
        icon = painterResource("roshan_khata_icon.png"),
        state = rememberWindowState(width = 460.dp, height = if (failed) 340.dp else 180.dp, position = androidx.compose.ui.window.WindowPosition(Alignment.Center))
    ) {
        DeskTheme(dark = false) {
            val c = LocalRoshanColors.current
            Column(
                Modifier.fillMaxSize().background(c.surface).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!failed) {
                    androidx.compose.material3.CircularProgressIndicator(color = c.brandGreen)
                    Text(words.text(W.book_opening), color = c.ink, fontSize = 15.sp)
                } else {
                    Text(words.text(W.book_cannot_open), color = c.ink, fontSize = 15.sp)
                    detail?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 3) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        other?.let { (label, open) -> TextButton(open) { Text(label) } }
                        TextButton(onClose) { Text(words.text(W.close)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.ui.window.ApplicationScope.app(home: File) {
    // The open business: its own book and its own shop settings. Switching
    // closes the old book (DisposableEffect) before the new one is used.
    val businesses = remember { Businesses(home) }
    var active by remember { mutableStateOf(businesses.activeId()) }
    // The business open before the last switch: offered when the new one cannot be opened (R28, 23 Oct 2026).
    var previous by remember { mutableStateOf<Long?>(null) }
    val settings = remember(active) { FileShopSettings(businesses.settingsFile(active)).also { it.refreshDisplay() } }
    // Once a day, a checked copy of the open book (the phone's Snapshots); a failure never stops the app.
    val dailyDir = remember(active) { DailyCopies.dir(home, active) }
    // The app's language: the owner's choice (app.properties), else the computer's.
    // Counter mode and the wrong-PIN count belong to this computer: read before anything is shown.
    val appPrefs = remember {
        AppPrefs(File(home, "app.properties")).also {
            com.innovation313.roshankhata.data.Access.store = it.accessStore
            // Which computer this is comes from Windows, not from a file anyone can edit (22 Oct 2026, the review).
            com.innovation313.roshankhata.data.Access.machineId = { MachineId.read() }
            com.innovation313.roshankhata.data.Digits.nativeOnPrint = it.nativeDigits()
        }
    }
    var language by remember { mutableStateOf(appPrefs.language() ?: Locale.getDefault().toLanguageTag()) }
    val locale = remember(language) { Locale.forLanguageTag(language).also { Locale.setDefault(it) } }
    val words = remember(locale) { desktopWords(locale) }
    val rtl = BidiPort.isRtlLocale(locale)
    // The book, checked first (BookHealth) — on a worker thread, with a window up (21 Oct 2026).
    // The check reads the whole file; while it ran on the window's own thread no window appeared
    // for seconds and owners clicked the shortcut again. A book that cannot be read is left
    // exactly as it is, and the window says so.
    var opened by remember(active) { mutableStateOf<BookHealth.Opened?>(null) }
    var openFailed by remember(active) { mutableStateOf(false) }
    var openError by remember(active) { mutableStateOf<Throwable?>(null) }
    var emptyButSetAside by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active) {
        val outside = appPrefs.backupFolder()?.let { DailyCopies.outside(it, active) }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val o = BookHealth.open(businesses.dbFile(active), settings, listOfNotNull(dailyDir, outside))
                // An empty book beside one set aside earlier: said once, so no work goes into the wrong one.
                val empty = o.corruptKept == null && o.setAside.isNotEmpty() && o.db.khataDao().partiesWithBalanceOnce().isEmpty()
                o to empty
            }
        }
        result.onSuccess { (o, empty) ->
            // Kept as the one to open next time only once it opened (R28): a switch to a book that cannot be
            // opened was saved first, and every start then opened it, failed and closed.
            if (businesses.activeId() != active) runCatching { withContext(Dispatchers.IO) { businesses.switchTo(active) } }
            emptyButSetAside = empty; opened = o
        }.onFailure { e ->
            // The reason is kept (R85): errors.log, so Report a problem can send it.
            withContext(Dispatchers.IO) { runCatching { CrashLog.write(home, "Opening the book ${businesses.dbFile(active).name}", e) } }
            openError = e
            openFailed = true
        }
    }
    val book = opened
    if (book == null) {
        val back = previous ?: (if (active != 1L) 1L else null)
        OpeningWindow(
            words, failed = openFailed, onClose = { exitApplication() },
            detail = openError?.let { e -> (e.cause ?: e).let { it.message ?: it.javaClass.simpleName } },
            other = back?.takeIf { openFailed && it != active && businesses.list().any { b -> b.id == it } }?.let { id ->
                val name = FileShopSettings(businesses.settingsFile(id)).businessName()
                    ?: businesses.list().firstOrNull { it.id == id }?.name ?: words.text(W.app_name)
                (words.text(W.business_switch_open) + " " + name) to { previous = null; active = id }
            }
        )
        return
    }
    val db = book.db
    var recoveryTold by remember(active) { mutableStateOf(book.corruptKept == null && !emptyButSetAside) }
    // Live sync is let go of before the book closes (a business switch): a round in flight must not reopen the
    // old file through a closed handle (23 Oct 2026, the second review).
    DisposableEffect(db) { onDispose { runCatching { LiveDesk.detach(db) }; runCatching { db.close() } } }
    // The shop's logo, signature, stamp and payment QR for its invoices, one set per business.
    val images = remember(active) { com.innovation313.roshankhata.desktop.invoice.ProfileImages.forBusiness(home, active) }
    LaunchedEffect(db) { runCatching { DailyCopies.makeToday(db.khataDao(), settings, dailyDir) } }
    // Live sync with the phone (22 Oct 2026): listening while this book syncs.
    var liveFailed by remember(active) { mutableStateOf<String?>(null) }
    LaunchedEffect(db) {
        withContext(Dispatchers.IO) {
            val file = businesses.dbFile(active).name
            // A phone's book the owner chose to keep as a new business: this (new, empty) book takes it and joins.
            LiveDesk.takePending()?.let { (offer, data) ->
                runCatching {
                    LiveSync.disable(db.syncSql())
                    restore(data, db, settings, keepAccess = false)
                    LiveDesk.join(db, home, file, offer)
                }.onFailure { e ->
                    // Said, never passed over (22 Oct 2026, the review): the phone waits for a yes that is not coming.
                    liveFailed = e.message ?: e.javaClass.simpleName
                }
            }
            runCatching { LiveDesk.attach(db, home, file) }
        }
    }
    // Light, dark or as Windows is: the phone's Theme choice, kept for every shop on this computer.
    var theme by remember { mutableStateOf(appPrefs.theme()) }
    var textSize by remember { mutableStateOf(appPrefs.textSize()) }
    var invoiceFields by remember(active) { mutableStateOf(appPrefs.invoiceFields(active)) }
    // App Lock: with a PIN set, nothing of the book is composed until it is typed (LockScreen).
    // A counter computer opens on its own sign-in (CounterGate), a PIN already: App Lock is not asked as well.
    var unlocked by remember { mutableStateOf(!appPrefs.hasPin() || com.innovation313.roshankhata.data.Access.counterMode) }
    // And it comes back by itself after a while with nothing typed or clicked in any of the app's
    // windows (21 Oct 2026: it was asked once at the start, then the book stayed open all day).
    // Java's own event hook sees the dialog windows too, so typing in a long form keeps it open.
    val lastInput = remember { java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis()) }
    DisposableEffect(Unit) {
        val listener = java.awt.event.AWTEventListener {
            val at = System.currentTimeMillis()
            lastInput.set(at)
            // A manager's or the owner's session on a counter ends after five idle minutes (22 Oct 2026, the review).
            com.innovation313.roshankhata.data.Access.touch(at)
        }
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(listener, java.awt.AWTEvent.KEY_EVENT_MASK or java.awt.AWTEvent.MOUSE_EVENT_MASK or java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK)
        onDispose { toolkit.removeAWTEventListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            val idle = System.currentTimeMillis() - lastInput.get()
            if (unlocked && appPrefs.hasPin() && !com.innovation313.roshankhata.data.Access.counterMode && idle > APP_LOCK_IDLE_MS) unlocked = false
            com.innovation313.roshankhata.data.Access.expireIfIdle()
        }
    }
    // The shop's trade (its words, units and which pages show), re-read when the owner changes it.
    var tradeTick by remember { mutableIntStateOf(0) }
    val trade = remember(active, tradeTick) { TradeKit.of(settings.tradeKey(), settings.otherTradeItems()) }
    // The second place for the copies (USB, Google Drive folder): today's copy there on opening,
    // and again on closing so the day's last work leaves the computer too. A missing stick
    // never stops the app; the Daily copies page says it could not be reached.
    var outsideFolder by remember { mutableStateOf(appPrefs.backupFolder()) }
    val outsideDir = outsideFolder?.let { DailyCopies.outside(it, active) }
    LaunchedEffect(db, outsideDir) {
        // Not when the book was just rebuilt from an older copy: the outside one may be the newer.
        if (outsideDir != null && book.corruptKept == null) runCatching { DailyCopies.makeToday(db.khataDao(), settings, outsideDir, refresh = true) }
    }
    // Auto backup (20 Oct): today's copy, here and in the outside folder, rewritten every hour the
    // app stays open, so a day's work is never more than an hour behind in the copies.
    LaunchedEffect(db, outsideDir) {
        while (true) {
            kotlinx.coroutines.delay(60L * 60 * 1000)
            runCatching { DailyCopies.makeToday(db.khataDao(), settings, dailyDir, refresh = true) }
            if (outsideDir != null) runCatching { DailyCopies.makeToday(db.khataDao(), settings, outsideDir, refresh = true) }
        }
    }
    // A business switch (R76, 23 Oct 2026): the book being left gets its closing copies first, here and in the
    // outside folder, as on closing the app — switching used to cancel the hourly and after-sale copies and write
    // nothing, so up to an hour of that shop's work was in no copy. Then whoever was signed in is signed out
    // (shop A's manager is not a manager in shop B, 22 Oct) and the other book opens; it is kept as the one to
    // open next time only once it opened (R28).
    var switchingTo by remember { mutableStateOf<Long?>(null) }
    switchingTo?.let { to ->
        LaunchedEffect(to) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(90_000) {
                    runCatching { DailyCopies.makeToday(db.khataDao(), settings, dailyDir, refresh = true) }
                    outsideDir?.let { dir -> runCatching { DailyCopies.makeToday(db.khataDao(), settings, dir, refresh = true) } }
                }
            }
            com.innovation313.roshankhata.data.Access.signOut()
            previous = active
            switchingTo = null
            active = to
        }
    }
    // Keyboard shortcuts for the counter (Phase 9a): the open page fills these in.
    val keys = remember { KeyActions() }
    // Closing (21 Oct 2026): the window goes at once and the day's last copies are written after it,
    // here and in the outside folder. The closing copy used to run on the window's own thread, so a
    // slow USB stick left a frozen "Not responding" window — and this computer's own copy was not
    // refreshed on closing at all.
    var closing by remember { mutableStateOf(false) }
    val closeApp: () -> Unit = { closing = true }
    if (closing) LaunchedEffect(Unit) {
        Reminders.remove()
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeoutOrNull(90_000) {
                runCatching { DailyCopies.makeToday(db.khataDao(), settings, dailyDir, refresh = true) }
                outsideDir?.let { dir -> runCatching { DailyCopies.makeToday(db.khataDao(), settings, dir, refresh = true) } }
            }
        }
        runCatching { LiveDesk.detach(db) }
        runCatching { db.close() }
        exitApplication()
    }
    Window(
        visible = !closing,
        onCloseRequest = closeApp,
        onPreviewKeyEvent = { keys.handle(it) },
        title = words.text(W.app_name),
        icon = painterResource("roshan_khata_icon.png"),
        // Opened filling the screen: a laptop's 768 rows otherwise cut off the entry buttons.
        state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1100.dp, height = 740.dp)
    ) {
        val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
        DeskTheme(dark = when (theme) { ThemeChoice.SYSTEM -> systemDark; ThemeChoice.LIGHT -> false; ThemeChoice.DARK -> true }) {
            ProvideWords(words) {
                // The owner's text size on top of Windows' own scale, as the phone multiplies its own.
                val screen = androidx.compose.ui.platform.LocalDensity.current
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(screen.density, screen.fontScale * TEXT_FACTORS[textSize])
                ) {
                    if (!unlocked) LockScreen(appPrefs) { unlocked = true }
                    // A counter with nobody signed in: nothing of the book until someone does (21 Oct 2026).
                    else if (com.innovation313.roshankhata.data.Access.locked) key(active) { CounterGate(db.khataDao(), settings.businessName(), onClose = closeApp) }
                    else key(active) { GateHost(db.khataDao()) { CompositionLocalProvider(LocalTrade provides trade) { Book(
                        keys = keys,
                        db = db,
                        dao = db.khataDao(),
                        load = { db.khataDao().partiesWithBalanceOnce() },
                        shopName = { settings.businessName() },
                        settings = settings,
                        restore = { data, keepAccess -> restore(data, db, settings, keepAccess) },
                        backup = { file -> backup(file, db, settings) },
                        onLanguage = { tag -> appPrefs.setLanguage(tag); language = tag },
                        theme = theme,
                        onTheme = { t -> appPrefs.setTheme(t); theme = t },
                        textSize = textSize,
                        onTextSize = { level -> appPrefs.setTextSize(level); textSize = level },
                        invoiceFields = invoiceFields,
                        onInvoiceFields = { f -> appPrefs.setInvoiceFields(f, active); invoiceFields = f },
                        rtl = rtl,
                        words = words,
                        businesses = businesses,
                        activeBusiness = active,
                        // Another shop's book: whoever was signed in here is not signed in there (22 Oct 2026, the review:
                        // shop A's manager stayed a manager in shop B, and B's sales carried A's staff name).
                        onSwitch = { id -> if (id != active) switchingTo = id },
                        dailyDir = dailyDir,
                        images = images,
                        home = home,
                        outsideFolder = outsideFolder,
                        outsideDir = outsideDir,
                        onBackupFolder = { f -> appPrefs.setBackupFolder(f); outsideFolder = f },
                        onTradeChanged = { tradeTick++ },
                        appPrefs = appPrefs
                    ) } } }
                    // An owner's PIN from before six digits were asked: a longer one, once the owner is in (22 Oct 2026).
                    var changingOwnerPin by remember { mutableStateOf(false) }
                    if (com.innovation313.roshankhata.data.Access.ownerPinShort && !com.innovation313.roshankhata.data.Access.locked && !changingOwnerPin) {
                        AlertDialog(
                            onDismissRequest = { com.innovation313.roshankhata.data.Access.ownerPinShort = false },
                            title = { Text(word(W.access_owner_pin)) },
                            text = { Text(word(W.access_owner_pin_short)) },
                            confirmButton = { TextButton({ changingOwnerPin = true }) { Text(word(W.access_owner_pin_change)) } },
                            dismissButton = { TextButton({ com.innovation313.roshankhata.data.Access.ownerPinShort = false }) { Text(word(W.cancel)) } }
                        )
                    }
                    if (changingOwnerPin) OwnerPinDialog(db.khataDao(), hasPin = true) {
                        changingOwnerPin = false
                        com.innovation313.roshankhata.data.Access.ownerPinShort = false
                    }
                    liveFailed?.let { why ->
                        AlertDialog(
                            onDismissRequest = { liveFailed = null },
                            title = { Text(word(W.live_title)) },
                            text = { Text(word(W.live_join_failed, why)) },
                            confirmButton = { TextButton({ liveFailed = null }) { Text(word(W.ok)) } }
                        )
                    }
                    if (!recoveryTold) {
                        // The book was damaged and rebuilt (or could not be): said once, before any work.
                        // Or it is empty while a book set aside earlier still sits beside it (21 Oct 2026).
                        val from = book.recoveredFrom
                        AlertDialog(
                            onDismissRequest = { recoveryTold = true },
                            title = { Text(word(W.book_recovered_title)) },
                            text = {
                                Text(
                                    if (book.corruptKept == null && emptyButSetAside) words.text(W.book_set_aside_note, book.setAside.first().absolutePath)
                                    else (if (from != null) words.text(W.book_recovered_from, Format.dateTime(from.lastModified()))
                                    else words.text(W.book_recovered_none)) + "\n\n" + words.text(W.book_corrupt_kept, book.corruptKept?.absolutePath.orEmpty())
                                )
                            },
                            confirmButton = { TextButton({ recoveryTold = true }) { Text(word(W.ok)) } }
                        )
                    }
                }
            }
        }
    }
}

/** What a chosen file came to: a parsed book to confirm, or why it was refused. */
private sealed interface Picked {
    /** [live]: the book came with a phone's live pairing; on yes, this computer also joins its live sync. */
    data class Ready(
        val file: File,
        val data: BackupCodec.ParsedBackup,
        val ok: BackupCodec.ImportResult.Ok,
        val live: com.innovation313.roshankhata.data.LiveWire.Offer? = null
    ) : Picked
    data class Refused(val why: String) : Picked
}

/**
 * The whole book to [file] in the phone's format, streamed; returns the file's
 * size. Written on this computer's own disk first and then copied to where the
 * owner chose (22 Oct 2026, the review: straight to a USB stick, the export
 * held the book's transaction for as long as the stick took, and the counter
 * waited).
 */
private suspend fun backup(
    file: File,
    db: com.innovation313.roshankhata.data.KhataDatabase,
    settings: FileShopSettings
): Long = withContext(Dispatchers.IO) {
    val local = File.createTempFile("rk-backup-", ".tmp")
    try {
        local.bufferedWriter(Charsets.UTF_8).use {
            BackupCodec.exportTo(db.khataDao(), settings.businessName(), BackupCodec.profileToJson(settings), it)
        }
        java.nio.file.Files.copy(local.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } finally {
        local.delete()
    }
    file.length()
}

/** The ledger in one transaction, then the shop's details — the phone's order. */
private suspend fun restore(
    data: BackupCodec.ParsedBackup,
    db: com.innovation313.roshankhata.data.KhataDatabase,
    settings: FileShopSettings,
    /** False for a live pairing: the phone's book exactly, its PINs and counter keys too. */
    keepAccess: Boolean = true
) = withContext(Dispatchers.IO) {
    BackupCodec.restoreLedger(db.khataDao(), data, keepAccess)
    data.businessProfile?.let { BackupCodec.restoreProfile(settings, it) }
}

/** App Lock returns after this long with no key or click (15 minutes, a shop's quiet spell). */
private const val APP_LOCK_IDLE_MS = 15 * 60_000L

private fun reasonOf(failed: BackupCodec.ImportResult.Failed, words: Words): String {
    val text = when (failed.why) {
        BackupCodec.ImportResult.Why.UNREADABLE -> words.text(W.backup_err_unreadable)
        BackupCodec.ImportResult.Why.NOT_OURS -> words.text(W.backup_err_not_ours)
        BackupCodec.ImportResult.Why.TOO_NEW -> words.text(W.backup_err_too_new)
        BackupCodec.ImportResult.Why.INCOMPLETE -> words.text(W.backup_err_incomplete, failed.count)
    }
    return if (failed.detail.isNullOrBlank()) text else "$text\n(${failed.detail})"
}

@Composable
private fun Book(
    keys: KeyActions,
    db: com.innovation313.roshankhata.data.KhataDatabase,
    dao: KhataDao,
    load: suspend () -> List<PartyWithBalance>,
    shopName: () -> String?,
    settings: FileShopSettings,
    restore: suspend (BackupCodec.ParsedBackup, Boolean) -> Unit,
    backup: suspend (File) -> Long,
    onLanguage: (String) -> Unit,
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
    textSize: Int,
    onTextSize: (Int) -> Unit,
    invoiceFields: InvoiceFields,
    onInvoiceFields: (InvoiceFields) -> Unit,
    rtl: Boolean,
    words: Words,
    businesses: Businesses,
    activeBusiness: Long,
    onSwitch: (Long) -> Unit,
    dailyDir: File,
    images: com.innovation313.roshankhata.desktop.invoice.ProfileImages,
    home: File,
    outsideFolder: File?,
    outsideDir: File?,
    onBackupFolder: (File?) -> Unit,
    onTradeChanged: () -> Unit,
    appPrefs: AppPrefs
) {
    val c = LocalRoshanColors.current
    val scope = rememberCoroutineScope()
    var parties by remember { mutableStateOf<List<PartyWithBalance>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var entryWay by remember { mutableStateOf<Boolean?>(null) }
    var addParty by remember { mutableStateOf(false) }
    var backedUp by remember { mutableStateOf<String?>(null) }
    // A finished export or import: (title, what happened).
    var told by remember { mutableStateOf<Pair<String, String>?>(null) }
    var importing by remember { mutableStateOf<Pair<List<com.innovation313.roshankhata.data.CsvImport.Row>, Int>?>(null) }
    var choosingLanguage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var deleting by remember { mutableStateOf<LedgerEntry?>(null) }
    // A receipt waiting for the owner to pick the thermal printer (Phase 9e); a counter sale's
    // says whether it was paid in cash, for the drawer (21 Oct 2026).
    var receipt by remember { mutableStateOf<ThermalReceipt.Job?>(null) }
    // Auto backup after a sale (21 Oct 2026): a minute after the last ticket, return or void,
    // today's copy is written again — here and in the outside folder. A burst of sales is one copy
    // (each new one restarts the minute); the hourly copy and the copy on closing stay as they were.
    var saleCopyTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(saleCopyTick) {
        if (saleCopyTick == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(60_000)
        runCatching { DailyCopies.makeToday(dao, settings, dailyDir, refresh = true) }
        outsideDir?.let { runCatching { DailyCopies.makeToday(dao, settings, it, refresh = true) } }
    }
    var printer by remember { mutableStateOf(appPrefs.receipt()) }
    // Printed sheets in the language's own digits, offered where it has its own (21 Oct 2026).
    var nativeDigits by remember { mutableStateOf(appPrefs.nativeDigits()) }
    // The shop's logo for the receipt's top, read from this business's pictures when it is printed.
    fun receiptLogo(): java.awt.image.BufferedImage? = runCatching {
        images.file(com.innovation313.roshankhata.desktop.invoice.ProfileImages.Kind.LOGO).takeIf { it.isFile }?.let { javax.imageio.ImageIO.read(it) }
    }.getOrNull()
    var halves by remember { mutableStateOf<Pair<LedgerEntry, LedgerEntry>?>(null) }
    var bin by remember { mutableStateOf(false) }
    // The phone's book over WiFi (Backup → Send to computer on the phone): the QR window is open.
    var phoneSync by remember { mutableStateOf(false) }
    // Live sync with the phone (22 Oct 2026): its window is open.
    var liveSync by remember { mutableStateOf(false) }
    var liveAsk by remember { mutableStateOf(false) }
    // The device this book syncs with live (null when it does not): the header's badge and the Businesses list.
    var liveWith by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(db) {
        while (true) {
            val st = withContext(Dispatchers.IO) { runCatching { LiveSync.state(db.syncSql()) }.getOrNull() }
            val with = if (st?.on == true) st.peers.firstOrNull()?.let { p -> p.name?.takeIf { it.isNotBlank() } ?: p.device.take(6) } else null
            liveWith = with
            runCatching { businesses.setLive(activeBusiness, with) }
            // Which live book this business keeps (R29): another business offered the same book is sent here.
            if (st != null) {
                val keeps = if (st.on) withContext(Dispatchers.IO) { runCatching { LiveSync.identity(db.syncSql())?.book }.getOrNull() } else null
                runCatching { businesses.setBook(activeBusiness, keeps) }
            }
            kotlinx.coroutines.delay(3000)
        }
    }
    // The phone's rows written in by a live round: every page reads again.
    LaunchedEffect(Unit) { LiveSync.changes.drop(1).collect { reload++ } }
    // A phone that scanned the live code: its book (already read whole by LiveDesk) goes to the usual restore
    // confirmation, and stays there until the owner answers it (a lock or a sign-out in between shows it again).
    // A pairing that came while another business was open is not asked of this one (R40): it is let go, and the
    // owner told which business it came to (the phone gives up waiting on its own; scanning again starts afresh).
    var offerLeft by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        LiveDesk.offers.filterNotNull().collect { incoming ->
            val came = incoming.bookFile
            if (came != null && came != businesses.dbFile(activeBusiness).name) {
                LiveDesk.offers.compareAndSet(incoming, null)
                val there = businesses.list().firstOrNull { businesses.dbFile(it.id).name == came }
                offerLeft = there?.let { b -> FileShopSettings(businesses.settingsFile(b.id)).businessName() ?: b.name }
                    ?.takeIf { it.isNotBlank() } ?: words.text(W.app_name)
                return@collect
            }
            liveSync = false
            picked = Picked.Ready(incoming.file, incoming.data, incoming.ok, live = incoming.offer)
        }
    }
    offerLeft?.let { name ->
        AlertDialog(
            onDismissRequest = { offerLeft = null },
            title = { Text(word(W.live_title)) },
            text = { Text(word(W.live_pair_left_business, name)) },
            confirmButton = { TextButton(onClick = { offerLeft = null }) { Text(word(W.ok)) } }
        )
    }
    // This book's own sync id: a phone offering this very book is offered only Replace (22 Oct 2026, the review:
    // "join as a new business" made a second business with the same book, and both answered the phone).
    var thisBook by remember { mutableStateOf<String?>(null) }
    var livePeers by remember { mutableStateOf<List<LiveSync.Peer>>(emptyList()) }
    LaunchedEffect(db, reload) {
        thisBook = withContext(Dispatchers.IO) { runCatching { LiveSync.identity(db.syncSql())?.book }.getOrNull() }
        livePeers = withContext(Dispatchers.IO) { runCatching { LiveSync.state(db.syncSql()).takeIf { it.on }?.peers }.getOrNull().orEmpty() }
    }
    var deletingParty by remember { mutableStateOf<PartyWithBalance?>(null) }
    // Home first, as the phone opens on its Home.
    var section by remember { mutableStateOf(Section.HOME) }
    var choosingTheme by remember { mutableStateOf(false) }
    var choosingTextSize by remember { mutableStateOf(false) }
    var lockSettings by remember { mutableStateOf(false) }
    // A book that never chose its trade is asked before anything else (see the picker below).
    var choosingTrade by remember { mutableStateOf(Trades.of(settings.tradeKey()) == null) }
    // The Khata list's tabs, side, order and the several-at-once delete (the phone's KhataActivity).
    var partyType by remember { mutableStateOf(PartyType.ALL) }
    var partySide by remember { mutableStateOf(PartySide.ALL) }
    var partySort by remember { mutableStateOf(PartySort.RECENT) }
    var partyWhen by remember { mutableStateOf(PartyWhen.ALL) }
    var selecting by remember { mutableStateOf(false) }
    var chosenIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var deletingMany by remember { mutableStateOf(false) }
    // The bell's list (LedgerAlerts, the phone's own rules) and its window.
    var alerts by remember { mutableStateOf<List<com.innovation313.roshankhata.data.LedgerAlerts.Alert>>(emptyList()) }
    var showAlerts by remember { mutableStateOf(false) }
    // The newest backup this computer holds: a daily copy, or one saved by hand this session.
    var lastBackupAt by remember { mutableStateOf<Long?>(null) }
    // The counter's open ticket lives as long as this business's window, not the page (25 Oct 2026, the audit).
    val counterDraft = remember { com.innovation313.roshankhata.data.CounterDraft() }
    // Home's You gave, You got and Statement: the customer is chosen in a small window first.
    var picking by remember { mutableStateOf<HomePick?>(null) }
    var pickedFor by remember { mutableStateOf<Long?>(null) }
    // Home's "Add bill": Supplier bills opens with a new bill's form.
    var newBill by remember { mutableStateOf(false) }
    // The money totals as dots until the eye opens them, remembered (25 Oct 2026, the audit; the phone's BalancePrivacy).
    var totalsHidden by remember { mutableStateOf(appPrefs.balancesHidden()) }
    val setTotalsHidden = { v: Boolean -> totalsHidden = v; runCatching { appPrefs.setBalancesHidden(v) }; Unit }
    // Ctrl+F: the Khata page's search box takes the typing.
    var searchTick by remember { mutableIntStateOf(0) }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var editingParty by remember { mutableStateOf<Party?>(null) }
    // What to say after (null: nothing): a customer the bin keeps is said, not "deleted forever" (22 Oct 2026).
    var purging by remember { mutableStateOf<Pair<String, suspend () -> String?>?>(null) }
    // Whether this computer is a counter is the book's word as well as the settings file's
    // (21 Oct 2026): read each time the book opens or comes back from a copy.
    LaunchedEffect(reload) { withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.Access.applyBook(dao) } } }
    // The bin keeps things for the days its note promises, then lets them go, as on the phone.
    // Counted from the book's own newest work when the clock reads later than that (21 Oct 2026):
    // a clock that jumped years ahead emptied the whole bin at the next start.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val newest = runCatching { dao.lastEntryActivity() }.getOrDefault(0L)
            val anchor = if (newest > 0L) minOf(now, newest + 24L * 60 * 60 * 1000) else now
            val cutoff = anchor - 30L * 24 * 60 * 60 * 1000
            runCatching { dao.purgeOldEntries(cutoff); dao.purgeOldParties(cutoff) }
        }
    }
    // The computer's clock behind the copies it made itself (21 Oct 2026): a dead CMOS battery puts
    // a laptop back years, new work lands on the wrong days and old copies stop being cleared.
    // Said once each time the app opens, with both dates, until the clock is right.
    var clockBehind by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        clockBehind = withContext(Dispatchers.IO) {
            runCatching {
                if (DailyCopies.clockBehind(dailyDir)) DailyCopies.list(dailyDir).map { DailyCopies.dayOf(it) }.filter { it.isNotEmpty() }.maxOrNull() else null
            }.getOrNull()
        }
    }
    clockBehind?.let { newestDay ->
        AlertDialog(
            onDismissRequest = { clockBehind = null },
            title = { Text(word(W.clock_behind_title)) },
            text = { Text(words.text(W.clock_behind_body, newestDay, Format.dateOnly(System.currentTimeMillis()))) },
            confirmButton = { TextButton({ clockBehind = null }) { Text(word(W.ok)) } }
        )
    }
    var choosingBusiness by remember { mutableStateOf(false) }
    // An invoice just made from a counter sale, opened on the Invoices page.
    var invoiceToOpen by remember { mutableStateOf<Long?>(null) }
    // Who is at this computer (More → Who is at the counter): sign in, change, or sign out.
    var signingIn by remember { mutableStateOf(false) }
    if (signingIn) SignInDialog(dao) { signingIn = false }
    var statementFor by remember { mutableStateOf<PartyWithBalance?>(null) }
    // F2 you gave, F3 you got (on the open customer), Ctrl+N new customer, Ctrl+P statement,
    // F1 the Khata page. Each does only what its button does.
    keys.gave = { if (selected != null) { section = Section.KHATA; entryWay = true } }
    keys.got = { if (selected != null) { section = Section.KHATA; entryWay = false } }
    keys.newParty = { addParty = true }
    keys.statement = { parties.firstOrNull { it.id == selected }?.let { statementFor = it } }
    keys.khata = { section = Section.KHATA }
    LaunchedEffect(section) { if (section != Section.COUNTER) keys.page = null }
    keys.search = { section = Section.KHATA; searchTick++ }
    statementFor?.let { party ->
        val partyEntries by remember(party.id, reload) { dao.observeEntries(party.id) }.collectAsState(emptyList())
        StatementRangeDialog(party.name, partyEntries, onCancel = { statementFor = null }) { from, till ->
            statementFor = null
            scope.launch {
                val all = withContext(Dispatchers.IO) { dao.latestEntriesWithItems(party.id, Int.MAX_VALUE) }
                // A period: its own entries, the balance carried in before it, and the whole
                // account as the closing figure (the phone's ReportActivity rule).
                val sign = { e: com.innovation313.roshankhata.data.EntryWithItems -> if (e.entry.isGiven) e.entry.amount else -e.entry.amount }
                val rows = if (from == null) all else all.filter { it.entry.timestamp in from..till!! }
                val opening = if (from == null) 0.0 else all.filter { it.entry.timestamp < from }.sumOf(sign)
                StatementPrint.show(
                    StatementPrint(words, rtl, shopName() ?: words.text(W.app_name), party.name, party.phone, rows,
                        opening = opening, periodFrom = from, accountBalance = if (from == null) null else all.sumOf(sign)),
                    words.text(W.pdf_stmt_title, party.name)
                )
            }
        }
    }
    var shop by remember { mutableStateOf(shopName()) }
    if (choosingBusiness) BusinessesDialog(
        businesses, activeBusiness, onSwitch = { choosingBusiness = false; onSwitch(it) }, onClose = { choosingBusiness = false },
        // The open business is renamed through the app's own settings, and its header follows at once (R27).
        settingsOf = { id -> if (id == activeBusiness) settings else FileShopSettings(businesses.settingsFile(id)) },
        onRenamed = { id -> if (id == activeBusiness) shop = shopName() }
    )
    // The header's backup action, reached from the bell's row too (set where the action is built).
    var backupHook by remember { mutableStateOf({}) }
    fun backupNowLater() = backupHook()

    LaunchedEffect(reload) { parties = withContext(Dispatchers.IO) { load() } }
    LaunchedEffect(reload, section) {
        alerts = withContext(Dispatchers.IO) {
            runCatching {
                val conflicts = runCatching { LiveSync.moneyConflicts(db.syncSql()) }.getOrDefault(0)
                val syncOff = runCatching { LiveSync.offReason(db.syncSql()) == LiveSync.OFF_RESTORED }.getOrDefault(false)
                com.innovation313.roshankhata.data.LedgerAlerts.compute(words, dao, { Format.money(it) }, conflicts, syncOff)
            }.getOrDefault(emptyList())
        }
    }
    // The last real backup (25 Oct 2026, the audit): a "Back up now" file, or a copy in the outside folder
    // (a USB stick, Google Drive for Desktop). The daily copies on this computer's own disk are written on
    // opening, every hour and after sales, so counting them meant the week-old reminder could never come.
    LaunchedEffect(reload, outsideDir) {
        val newest = withContext(Dispatchers.IO) {
            listOfNotNull(
                appPrefs.lastSavedBackup(activeBusiness),
                outsideDir?.let { DailyCopies.list(it) }?.maxOfOrNull { it.lastModified() }
            ).maxOrNull()
        }
        if (newest != null && newest > (lastBackupAt ?: 0L)) lastBackupAt = newest
    }
    // A newer Windows build on the release page, asked once a day (UpdateCheck); null until known.
    var newerVersion by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { newerVersion = withContext(Dispatchers.IO) { runCatching { UpdateCheck.newer(appPrefs) }.getOrNull() } }
    // The day's reminder from the tray (Reminders), once the bell's list and the backup's age are known.
    var reminded by remember { mutableStateOf(false) }
    LaunchedEffect(alerts, lastBackupAt) {
        if (reminded) return@LaunchedEffect
        kotlinx.coroutines.delay(4000)
        reminded = true
        runCatching { Reminders.daily(appPrefs, words, alerts, lastBackupAt) }
    }
    // The second place for the daily copies, looked at once a minute: a stick pulled out or a Drive folder gone
    // stops those copies, and the bell says so (25 Oct 2026, the audit: a failed copy left the bell silent).
    var outsideMissing by remember { mutableStateOf(false) }
    LaunchedEffect(outsideFolder) {
        while (true) {
            outsideMissing = outsideFolder?.let { f -> withContext(Dispatchers.IO) { !f.isDirectory } } ?: false
            kotlinx.coroutines.delay(60_000)
        }
    }
    // The Live sync window, from More or a bell row: the backup right, and the owner's own PIN when one is set,
    // since joining a device hands it the whole book (22 Oct 2026).
    fun openLiveSync() = Gates.need(com.innovation313.roshankhata.data.Access.Permission.BACKUP) {
        scope.launch {
            val pin = withContext(Dispatchers.IO) { runCatching { com.innovation313.roshankhata.data.Access.ownerPin(dao) }.getOrNull() }
            if (pin == null) liveSync = true else liveAsk = true
        }
    }
    var reporting by remember { mutableStateOf(false) }
    // The bell's rows beyond the ledger's own: the backup's age and a newer version (the phone's HomeAlerts adds the same).
    val backupOld = lastBackupAt == null || System.currentTimeMillis() - lastBackupAt!! > Reminders.BACKUP_WEEK_MS
    val extraAlerts = listOfNotNull(
        if (backupOld) ExtraAlert(Section.DAILY, words.text(W.notif_backup_title),
            lastBackupAt?.let { words.text(W.na_backup, ((System.currentTimeMillis() - it) / (24L * 60 * 60 * 1000)).toInt()) } ?: words.text(W.notif_backup_body_pc)) { backupNowLater() } else null,
        newerVersion?.let { v -> ExtraAlert(Section.HELP, words.text(W.update_available, v), words.text(W.update_open)) { UpdateCheck.openPage() } },
        if (outsideMissing) ExtraAlert(Section.DAILY, words.text(W.auto_folder_title), words.text(W.auto_folder_missing)) { showAlerts = false; section = Section.DAILY } else null
    ) + alerts.filter { it.kind == com.innovation313.roshankhata.data.LedgerAlerts.Kind.SYNC }.map { a ->
        // Live sync's rows (conflicts, sync off) open the Live sync window, where they are dealt with
        // (25 Oct 2026, the audit: they opened Daily copies, which knows nothing of them).
        ExtraAlert(Section.DAILY, a.title, listOfNotNull(a.sub.takeIf { it.isNotEmpty() }, a.tag).joinToString(" · ")) { showAlerts = false; openLiveSync() }
    }
    // The ledger's own rows, each opening its page; live sync's went to extraAlerts above.
    val bookAlerts = alerts.filter { it.kind != com.innovation313.roshankhata.data.LedgerAlerts.Kind.SYNC }

    Column(Modifier.fillMaxSize().background(Look.base)) {
        // What needs the owner today, as the phone's Home: cheques due, stock expiring
        // inside the warning window, and customers paying later than their habit.
        val now = remember { System.currentTimeMillis() }
        val dueCheques by remember { dao.observeDueChequeCount(now) }.collectAsState(0)
        // Batches that still hold something, by the one rule (Marhala 3): a sold-out or written-off lot no longer counts.
        val expiring by remember { com.innovation313.roshankhata.data.Fefo.observeExpiringCount(dao, now + com.innovation313.roshankhata.data.ExpiryWindow.WARN_MS) }.collectAsState(0)
        val late by remember(reload) { lateCount(dao) }.collectAsState(0)
        // The whole book out is gated wherever it is asked for — the header, Home's quick action,
        // the bell's row (21 Oct 2026: only the header's button asked a cashier for the PIN) — and
        // the audit log says who took it. Owner-only BACKUP, as the phone's BackupActivity (R33,
        // 23 Oct 2026): the file carries the owner's and staff PIN hashes, and EXPORT let a manager
        // (or a cashier with a manager's PIN) carry them off. The CSV exports stay on EXPORT.
        fun backupNow() = Gates.need(com.innovation313.roshankhata.data.Access.Permission.BACKUP) backup@{
            val dialog = FileDialog(null as Frame?, words.text(W.save_backup), FileDialog.SAVE)
            // The phone's file name and .txt (WhatsApp sends .txt; the content is JSON).
            // Documents, not the program's own folder: an update or uninstall must not take the backup with it.
            startIn(dialog, "Documents")
            dialog.file = "RoshanKhata_Backup_" +
                java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH).format(java.util.Date()) + ".txt"
            dialog.isVisible = true
            val name = dialog.file ?: return@backup
            val file = File(dialog.directory, name)
            scope.launch {
                backedUp = runCatching { backup(file) }
                    .map { size ->
                        lastBackupAt = System.currentTimeMillis()
                        // Kept, so the header and the week-old reminder still know after a restart.
                        runCatching { appPrefs.setLastSavedBackup(activeBusiness, lastBackupAt!!) }
                        withContext(Dispatchers.IO) { runCatching { dao.log(com.innovation313.roshankhata.data.AuditAction.EXPORT, com.innovation313.roshankhata.data.Access.userName, detail = file.name) } }
                        words.text(W.backup_saved_at, file.absolutePath, (size / 1024).toInt())
                    }
                    .getOrElse { words.text(W.backup_failed) }
            }
        }
        backupHook = { backupNow() }
        fun restoreFromFile() {
            val dialog = FileDialog(null as Frame?, words.text(W.restore_from_file), FileDialog.LOAD)
            // Downloads: where a backup sent from the phone (WhatsApp, a browser) lands.
            startIn(dialog, "Downloads")
            dialog.isVisible = true
            val name = dialog.file ?: return
            val file = File(dialog.directory, name)
            scope.launch {
                val (result, data) = withContext(Dispatchers.IO) { BackupCodec.parseFile(file) }
                picked = when {
                    result is BackupCodec.ImportResult.Ok && data != null -> Picked.Ready(file, data, result)
                    result is BackupCodec.ImportResult.Failed -> Picked.Refused(reasonOf(result, words))
                    else -> Picked.Refused(words.text(W.backup_err_unreadable))
                }
            }
        }
        // Each open page's head, for the window's one top row (Marhala 2, the owner's mockup of 25 Oct 2026).
        val pageHeads = remember { androidx.compose.runtime.mutableStateMapOf<Section, PageHead>() }
        // The shop on top of the rail: its logo, its name, Profile · Switch shop.
        val shopCard: @Composable () -> Unit = {
            ShopCard(
                shop ?: word(W.app_name),
                Photos.bitmap(images.file(com.innovation313.roshankhata.desktop.invoice.ProfileImages.Kind.LOGO).absolutePath, 96),
                liveWith,
                onProfile = { section = Section.SHOP },
                onSwitch = { Gates.need(com.innovation313.roshankhata.data.Access.Permission.SETTINGS) { choosingBusiness = true } }
            )
        }
        // The top row: the open page's title and buttons, then backup, phone, bell, More, Add Party.
        val header: @Composable () -> Unit = { AppHeader(
            head = pageHeads[section],
            backupAt = lastBackupAt,
            backupOld = backupOld,
            onBackup = { backupNow() },
            onPhone = { Gates.need(com.innovation313.roshankhata.data.Access.Permission.BACKUP) { phoneSync = true } },
            alerts = bookAlerts.size + extraAlerts.size,
            onAlerts = { showAlerts = true },
            // What the rail does not hold, in the phone's groups (25 Oct 2026, the audit: the menu repeated the
            // rail's Daily copies, Help and Business Profile, and filed the shop's own settings under Display).
            menu = listOf(
                MenuLine(word(W.settings_group_data), null),
                MenuLine(word(W.restore_from_file)) { Gates.need(com.innovation313.roshankhata.data.Access.Permission.BACKUP) { restoreFromFile() } },
                // Joining a device hands it the whole book: the owner's own PIN, whoever is signed in (22 Oct 2026).
                MenuLine(word(W.live_title)) { openLiveSync() },
                MenuLine(word(W.recycle_bin)) { Gates.edit { bin = true } },
                MenuLine(word(W.home_sec_business), null),
                MenuLine(word(W.businesses_title)) { Gates.need(com.innovation313.roshankhata.data.Access.Permission.SETTINGS) { choosingBusiness = true } },
                MenuLine(word(W.trade_kicker_change)) { Gates.need(com.innovation313.roshankhata.data.Access.Permission.SETTINGS) { choosingTrade = true } },
                MenuLine(word(W.settings_group_display), null),
                MenuLine(word(W.language)) { choosingLanguage = true },
                MenuLine(word(W.theme)) { choosingTheme = true },
                MenuLine(word(W.text_size)) { choosingTextSize = true },
                *(if (com.innovation313.roshankhata.data.Digits.hasOwnDigits())
                    arrayOf(MenuLine(word(W.print_native_digits) + if (nativeDigits) "  \u2713" else "") {
                        nativeDigits = !nativeDigits
                        appPrefs.setNativeDigits(nativeDigits)
                    })
                else emptyArray<MenuLine>()),
                MenuLine(word(W.settings_group_security), null),
                MenuLine(word(W.app_lock)) { Gates.need(com.innovation313.roshankhata.data.Access.Permission.SETTINGS) { lockSettings = true } },
                MenuLine(com.innovation313.roshankhata.data.Access.current?.let { word(W.access_signed_in_as, it.name) } ?: word(W.access_who)) { signingIn = true },
                MenuLine(word(W.settings_group_help), null),
                MenuLine(word(W.report_problem)) { reporting = true },
                MenuLine(word(W.about_us)) { about = true }
            ),
            onAddParty = { addParty = true }
        ) }

        Row(Modifier.fillMaxSize()) {
            SectionRail(section, { section = it }, badges = mapOf(Section.FOLLOWUP to late), top = shopCard)
            Column(Modifier.weight(1f).fillMaxHeight()) {
            header()
            // A page eases in over the last (Motion.PAGE ms) instead of snapping, as Windows' own apps do.
            // The content area: the phone's page colour, flat under the top row (Marhala 2).
            androidx.compose.animation.Crossfade(
                section,
                Modifier.weight(1f).fillMaxWidth().background(Look.layerAlt),
                androidx.compose.animation.core.tween(Motion.PAGE), label = "page"
            ) { page ->
                // The page's head goes to the window's top row: a fresh one each time the page opens, so a page
                // that writes none (a locked one) never shows another visit's title or buttons.
                val head = remember { PageHead() }
                DisposableEffect(head) {
                    pageHeads[page] = head
                    onDispose { if (pageHeads[page] === head) pageHeads.remove(page) }
                }
                CompositionLocalProvider(LocalPageHead provides head) {
                Column(Modifier.fillMaxSize()) {
                // A merged page's tabs above it (Marhala 3): Supplier bills · Purchase rates · Company schemes, ...
                PageTabRow(page) { section = it }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                // A page a cashier may not open alone shows what it needs until a manager or the owner allows it.
                val need = page.need()
                var allowedHere by remember(page) { mutableStateOf(false) }
                if (need != null && !allowedHere && !com.innovation313.roshankhata.data.Access.can(need)) LockedPage(need) { allowedHere = true }
                else when (page) {
                    Section.HOME -> Box(Modifier.fillMaxSize()) {
                        HomePane(
                            parties = parties, late = late, alerts = bookAlerts, extra = extraAlerts,
                            figuresOf = { homeFigures(dao) }, reload = reload,
                            onOpen = { section = it },
                            onCustomer = { id -> selected = id; section = Section.KHATA },
                            // You gave, You got, Statement: whose, asked first (the open customer offered), then the entry
                            // or the statement (25 Oct 2026, the audit: You got only opened the Khata page).
                            onGave = { pickedFor = selected; picking = HomePick.GAVE },
                            onGot = { pickedFor = selected; picking = HomePick.GOT },
                            onStatement = { pickedFor = selected; picking = HomePick.STATEMENT },
                            onNewBill = { newBill = true; section = Section.BILLS },
                            hidden = totalsHidden, onHidden = setTotalsHidden
                        )
                    }
                    Section.COUNTER -> Box(Modifier.fillMaxSize()) {
                        CounterSalePane(
                            dao, parties, words, rtl, shop, com.innovation313.roshankhata.data.Currency.symbol, reload,
                            onChanged = { reload++; saleCopyTick++ }, onReceipt = { receipt = it }, keys = keys,
                            onInvoice = { id -> invoiceToOpen = id; section = Section.INVOICES },
                            // A cash sale rung up without a receipt still opens the drawer, through the printer used last.
                            onDrawer = {
                                val last = ThermalReceipt.lastPrinter
                                if (printer.drawer && last != null) scope.launch(Dispatchers.IO) { ThermalReceipt.openDrawer(last) }
                            },
                            printer = printer, hasLogo = images.has(com.innovation313.roshankhata.desktop.invoice.ProfileImages.Kind.LOGO),
                            onPrinter = { r -> appPrefs.setReceipt(r); printer = r },
                            paymentQr = images.file(com.innovation313.roshankhata.desktop.invoice.ProfileImages.Kind.QR).takeIf { it.isFile },
                            draft = counterDraft
                        )
                    }
                    Section.AUDIT -> Box(Modifier.fillMaxSize()) { AuditPane(dao) }
                    Section.INVENTORY -> Box(Modifier.fillMaxSize()) { InventoryPane(dao, words, reload, businesses = businesses, activeBusiness = activeBusiness) { reload++ } }
                    Section.SALES -> Box(Modifier.fillMaxSize()) { SalesReportPane(dao, words, rtl, shop, reload) }
                    Section.ORDERS -> Box(Modifier.fillMaxSize()) { OrdersPane(dao, parties, words, rtl, shop) { reload++ } }
                    Section.CASHBOOK -> Box(Modifier.fillMaxSize()) { CashbookPane(dao) }
                    Section.CHEQUES -> Box(Modifier.fillMaxSize()) { ChequesPane(dao, parties, words) { reload++ } }
                    Section.PLANS -> Box(Modifier.fillMaxSize()) { PlansPane(dao, parties, words) { reload++ } }
                    Section.EXPIRY -> Box(Modifier.fillMaxSize()) { ExpiryPane(dao) }
                    Section.BILLS -> Box(Modifier.fillMaxSize()) { BillsPane(dao, parties, words, home, startAdding = newBill, onStarted = { newBill = false }) { reload++ } }
                    Section.PRODUCTS -> Box(Modifier.fillMaxSize()) { ProductsPane(dao, words) }
                    Section.INVOICES -> Box(Modifier.fillMaxSize()) { InvoicesPane(dao, parties, words, rtl, shop, settings.businessAddress(), designs = { com.innovation313.roshankhata.desktop.invoice.Context(words, settings, images, rtl) }, fields = invoiceFields, onFields = onInvoiceFields, openId = invoiceToOpen) }
                    Section.INSIGHTS -> Box(Modifier.fillMaxSize()) { InsightsPane(dao, reload) }
                    Section.ZAKAT -> Box(Modifier.fillMaxSize()) { ZakatPane(dao) }
                    Section.RATES -> Box(Modifier.fillMaxSize()) { RateListPane(dao, words, rtl, shop) }
                    Section.STAFF -> Box(Modifier.fillMaxSize()) { StaffPane(dao) }
                    Section.FOLLOWUP -> Box(Modifier.fillMaxSize()) { FollowUpPane(dao, parties, words, shop) }
                    Section.AGING -> Box(Modifier.fillMaxSize()) { AgingPane(dao, words, rtl, shop, reload) { id -> selected = id; section = Section.KHATA } }
                    Section.CALC -> Box(Modifier.fillMaxSize()) { CalculatorPane() }
                    Section.SCHEMES -> Box(Modifier.fillMaxSize()) { SchemesPane(dao, reload) }
                    Section.CASHFLOW -> Box(Modifier.fillMaxSize()) { CashFlowPane(dao, reload) }
                    Section.BUY_RATES -> Box(Modifier.fillMaxSize()) { SupplierRatesPane(dao, reload) }
                    Section.DUPLICATES -> Box(Modifier.fillMaxSize()) { DuplicatesPane(dao, parties) { reload++ } }
                    Section.SEASONS -> Box(Modifier.fillMaxSize()) { SeasonsPane(dao, parties, reload) }
                    Section.HELP -> Box(Modifier.fillMaxSize()) { HelpPane(words) }
                    Section.REGISTER -> Box(Modifier.fillMaxSize()) { RegisterPane(dao, words, rtl, shop, reload) }
                    Section.STOCK -> Box(Modifier.fillMaxSize()) { StockPane(dao, words, rtl, shop, reload) }
                    Section.DAILY -> Box(Modifier.fillMaxSize()) { DailyCopiesPane(dao, settings, dailyDir, outsideFolder, outsideDir, onFolder = onBackupFolder, sync = db.syncSql()) { shop = shopName(); reload++ } }
                    Section.REPORT -> Box(Modifier.fillMaxSize()) { ReportPane(dao, words, rtl, shop, reload) }
                    Section.SHOP -> Box(Modifier.fillMaxSize()) { ShopDetailsPane(settings, images) { shop = shopName() } }
                    Section.CARD -> Box(Modifier.fillMaxSize()) { BusinessCardPane(settings, images, home, activeBusiness, words) { shop = shopName() } }
                    Section.POSTER -> Box(Modifier.fillMaxSize()) { PosterPane(settings, images, home, activeBusiness, words) }
                    Section.KHATA -> Column(Modifier.fillMaxSize()) {
                        // The page's head: the count, the eye, Add Party. The totals live once, in the two cards below,
                        // for the open tab (25 Oct 2026, the audit: the head said the whole book's under the same words).
                        PageHeader(
                            word(W.nav_khata),
                            plural(WP.customer_count, parties.size, parties.size),
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
                        ) {
                            // Add Party is the top row's one accent button: not twice (25 Oct 2026, the audit's duplicates).
                            IconAction(if (totalsHidden) "ic_eye_closed" else "ic_eye_open", word(W.net_balance), { setTotalsHidden(!totalsHidden) })
                        }
                        Row(Modifier.fillMaxSize()) {
                        // The book on the left, as the phone's Khata list.
                        Column(Modifier.weight(0.42f).fillMaxSize()) {
                            KhataControls(
                                type = partyType, onType = { partyType = it },
                                side = partySide, onSide = { partySide = it },
                                sort = partySort, onSort = { partySort = it },
                                whenFilter = partyWhen, onWhen = { partyWhen = it },
                                selecting = selecting, onSelecting = { on -> selecting = on; chosenIds = emptySet() },
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                            )
                            // Totals: what the shop will get, and what it will give, on the open tab.
                            val ofType = byType(parties, partyType)
                            val get = ofType.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
                            val give = ofType.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Total(word(W.you_will_get), if (totalsHidden) TOTALS_MASK else Format.money(get), c.owedToMe, Modifier.weight(1f))
                                Total(word(W.you_will_give), if (totalsHidden) TOTALS_MASK else Format.money(give), c.iOwe, Modifier.weight(1f))
                            }
                            // Today's cheques and expiring stock: their chips, as on the phone's Home.
                            if (dueCheques > 0 || expiring > 0) {
                                Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (dueCheques > 0) AlertChip(word(W.today_chip, word(W.nav_cheques), dueCheques)) { section = Section.CHEQUES }
                                    if (expiring > 0) AlertChip(word(W.today_chip, word(W.tile_expiry), expiring)) { section = Section.EXPIRY }
                                }
                            }
                            FluentField(
                                value = query,
                                onValueChange = { typed ->
                                    query = typed
                                    // A customer card held to a USB QR scanner (it types like a keyboard):
                                    // "RK1:" + token opens that customer, as the phone's camera scan does.
                                    com.innovation313.roshankhata.data.QrTag.parse(typed)?.let { token ->
                                        scope.launch {
                                            val p = withContext(Dispatchers.IO) { dao.partyByQrToken(token) }
                                            query = ""
                                            if (p != null) selected = p.id
                                            else told = words.text(W.app_name) to words.text(W.qr_no_customer)
                                        }
                                    }
                                },
                                placeholder = word(W.search_parties),
                                leadingIcon = "ic_search",
                                trailing = { KeyCap("Ctrl F") },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                    .focusRequester(searchFocus)
                            )
                            LaunchedEffect(searchTick) { if (searchTick > 0) runCatching { searchFocus.requestFocus() } }
                            // The phone's rule (KhataActivity.rank): search, side, type, then the chosen order;
                            // newest dealing first unless the owner picks another.
                            // Worked out once per change, not on every redraw (21 Oct 2026: a few thousand
                            // customers made each key in the search box wait for the whole list).
                            val shown = remember(parties, query, partySide, partyType, partySort, partyWhen) {
                                parties.shownBy(query, partySide, partyType, partySort, partyWhen.window())
                            }
                            if (selecting) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp)
                                        .background(Look.chosen, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(word(W.selected_count, chosenIds.size), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    TextButton({ chosenIds = shown.map { it.id }.toSet() }) { Text(word(W.select_all), color = c.brandGreenText) }
                                    TextButton({ Gates.edit { deletingMany = true } }, enabled = chosenIds.isNotEmpty()) { Text(word(W.delete), color = c.owedToMe) }
                                }
                            }
                            // One line however narrow the window: the count never wraps ("1209 custo mers"
                            // was the owner's photo, 20 Oct); the actions slide sideways instead.
                            Row(
                                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp).horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    plural(WP.customer_count, shown.size, shown.size),
                                    Modifier.padding(vertical = 8.dp),
                                    color = c.textMuted, fontSize = 13.sp, maxLines = 1, softWrap = false
                                )
                                Spacer(Modifier.width(12.dp))
                                // The whole book out (Phase 9b): every party to Excel, and every
                                // statement with money on it as one print job.
                                TextButton({ Gates.need(com.innovation313.roshankhata.data.Access.Permission.EXPORT) excel@{
                                    val dialog = FileDialog(null as Frame?, words.text(W.export_excel), FileDialog.SAVE)
                                    startIn(dialog, "Documents")
                                    dialog.file = "RoshanKhata_Customers_" +
                                        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(java.util.Date()) + ".csv"
                                    dialog.isVisible = true
                                    val name = dialog.file ?: return@excel
                                    val file = File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
                                    val headers = listOf(words.text(W.name), words.text(W.bp_phone), words.text(W.net_balance))
                                    scope.launch {
                                        told = words.text(W.export_excel) to runCatching {
                                            withContext(Dispatchers.IO) {
                                                file.writeText(BookExport.customersCsv(parties, headers), Charsets.UTF_8)
                                                dao.log(com.innovation313.roshankhata.data.AuditAction.EXPORT, com.innovation313.roshankhata.data.Access.userName, detail = file.name)
                                            }
                                        }.map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { failText(words, it) }
                                    }
                                } }, enabled = parties.isNotEmpty(), contentPadding = compactButton) { Text(word(W.export_excel), color = c.brandGreenText, fontSize = 13.sp, maxLines = 1) }
                                // Bringing customers and their balances in changes the book: a cashier waits for a
                                // manager's PIN, as for any change (22 Oct 2026, the review: it asked nobody).
                                TextButton({ Gates.need(com.innovation313.roshankhata.data.Access.Permission.EDIT_BOOK) importCsv@{
                                    val dialog = FileDialog(null as Frame?, words.text(W.import_csv), FileDialog.LOAD)
                                    startIn(dialog, "Downloads")
                                    dialog.isVisible = true
                                    val name = dialog.file ?: return@importCsv
                                    val file = File(dialog.directory, name)
                                    scope.launch {
                                        val rows = withContext(Dispatchers.IO) {
                                            runCatching { com.innovation313.roshankhata.data.CsvImport.parse(file.readText(Charsets.UTF_8)) }.getOrDefault(emptyList())
                                        }
                                        if (rows.isEmpty()) {
                                            told = words.text(W.import_csv) to words.text(W.import_csv_empty)
                                        } else {
                                            // Already in the book, or twice in the file: brought in once, never doubled.
                                            val seen = parties.toMutableList()
                                            val fresh = rows.filter { r ->
                                                !com.innovation313.roshankhata.data.CsvImport.alreadyIn(r, seen).also { dup ->
                                                    if (!dup) seen += PartyWithBalance(0, r.name, r.phone, true, null, 0.0)
                                                }
                                            }
                                            importing = fresh to (rows.size - fresh.size)
                                        }
                                    }
                                } }, contentPadding = compactButton) { Text(word(W.import_csv), color = c.brandGreenText, fontSize = 13.sp, maxLines = 1) }
                                TextButton({ Gates.need(com.innovation313.roshankhata.data.Access.Permission.EXPORT) {
                                    scope.launch {
                                        val owing = com.innovation313.roshankhata.data.NameOrder.sortedBy(parties.filter { kotlin.math.abs(it.balance) >= 0.005 }) { it.name }
                                        val sheets = withContext(Dispatchers.IO) {
                                            owing.map { p ->
                                                StatementPrint(words, rtl, shopName() ?: words.text(W.app_name), p.name, p.phone,
                                                    dao.latestEntriesWithItems(p.id, Int.MAX_VALUE))
                                            }
                                        }
                                        if (sheets.isNotEmpty()) {
                                            withContext(Dispatchers.IO) { runCatching { dao.log(com.innovation313.roshankhata.data.AuditAction.EXPORT, com.innovation313.roshankhata.data.Access.userName, detail = words.text(W.statement_pdf)) } }
                                            StatementPrint.show(AllStatements(sheets), words.text(W.statement_pdf))
                                        }
                                    }
                                } }, enabled = parties.any { kotlin.math.abs(it.balance) >= 0.005 }, contentPadding = compactButton) {
                                    Text(word(W.statement_pdf) + " · " + word(W.filter_type_all), color = c.brandGreenText, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                            // The list as a Fluent card (1.10.0): the open row tinted with a 3 dp accent bar,
                            // the name and its phone · last dealing, the balance and which way it runs.
                            LazyColumn(
                                Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                                    .clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card)
                            ) {
                                itemsIndexed(shown, key = { _, it -> it.id }) { i, p ->
                                    val ticked = p.id in chosenIds
                                    val open = p.id == selected || ticked
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .background(eased(if (open) Look.accentTint else Color.Transparent))
                                            .press {
                                                if (selecting) chosenIds = if (ticked) chosenIds - p.id else chosenIds + p.id
                                                else selected = p.id
                                            }
                                            .padding(end = 12.dp, top = 9.dp, bottom = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(Modifier.width(3.dp).height(22.dp).background(eased(if (open) Look.accent else Color.Transparent), RoundedCornerShape(2.dp)))
                                        Spacer(Modifier.width(9.dp))
                                        if (selecting) {
                                            androidx.compose.material3.Checkbox(
                                                checked = ticked,
                                                onCheckedChange = { on -> chosenIds = if (on) chosenIds + p.id else chosenIds - p.id }
                                            )
                                        }
                                        // The customer's face, when the computer has it (Photos: never on a statement).
                                        val face = Photos.bitmap(p.photoPath, 96)
                                        if (face != null) {
                                            androidx.compose.foundation.Image(
                                                face, contentDescription = null,
                                                modifier = Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else Avatar(p.name, 34, i)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(Format.name(p.name), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                listOfNotNull(p.phone?.let { Format.ltr(it) }, p.lastActivity.takeIf { it > 0 }?.let { Format.dateOnly(it) }).joinToString(" · "),
                                                color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                if (kotlin.math.abs(p.balance) < 0.005) "—" else Format.money(kotlin.math.abs(p.balance)),
                                                color = balanceColour(p.balance), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
                                            )
                                            Text(
                                                when {
                                                    Money.isPositive(p.balance) -> word(W.you_will_get)
                                                    Money.isNegative(p.balance) -> word(W.you_will_give)
                                                    else -> word(W.settled)
                                                },
                                                color = c.textMuted, fontSize = 11.sp, maxLines = 1, softWrap = false
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = Look.line)
                                }
                            }
                        }
                        // The chosen customer's page on the right: the shared ledger screen.
                        val party = parties.firstOrNull { it.id == selected }
                        Box(Modifier.weight(0.58f).fillMaxSize()) {
                            if (party != null) Column(Modifier.fillMaxSize()) {
                                // With their goods lines, so the search finds what was sold (25 Oct 2026, the audit: the box
                                // said "Notes or goods" and searched notes only).
                                val withItems by remember(party.id, reload) { dao.observeEntriesWithItems(party.id) }.collectAsState(emptyList())
                                val entries = remember(withItems) { withItems.map { it.entry } }
                                val goodsOf = remember(withItems) { withItems.associate { w -> w.entry.id to w.items.mapNotNull { it.itemName }.joinToString(" ") } }
                                // The phone's reminder bar: a WhatsApp reminder to a customer who owes, in the
                                // phone's own gentle words and with the day they promised, if any.
                                val number = party.phone?.let { com.innovation313.roshankhata.data.WhatsAppNumber.of(it) }
                                if (number != null && Money.isPositive(party.balance)) {
                                    Row(
                                        Modifier.fillMaxWidth().background(c.surface).padding(start = 20.dp, end = 20.dp, top = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlineAction(onClick = {
                                            scope.launch {
                                                val promised = withContext(Dispatchers.IO) { dao.getParty(party.id)?.harvestPromise }
                                                val text = reminderText(words, party.name, party.balance, promised, shopName())
                                                runCatching {
                                                    java.awt.Desktop.getDesktop().browse(java.net.URI("https://wa.me/$number?text=" +
                                                        java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")))
                                                }
                                            }
                                        }) {
                                            Text(word(W.remind_whatsapp) + "  \u00b7  " + word(W.send_reminder), color = c.brandGreenText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                // The phone's season record and promised day under the name (PartyDetail.refreshSeasonRecord).
                                val record = seasonRecord(dao, party, reload)
                                if (record.isNotEmpty()) {
                                    Text(
                                        record,
                                        Modifier.fillMaxWidth().background(c.surface).padding(start = 20.dp, end = 20.dp, top = 10.dp),
                                        color = c.brandGreenText, fontSize = 13.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                // The phone's entry search (notes or goods) and its newest / oldest order.
                                var entryQuery by remember(party.id) { mutableStateOf("") }
                                var oldestFirst by remember(party.id) { mutableStateOf(false) }
                                Row(
                                    Modifier.fillMaxWidth().background(c.surface).padding(start = 20.dp, end = 20.dp, top = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Outlined(
                                        value = entryQuery, onValueChange = { entryQuery = it },
                                        placeholder = { Text(word(W.search_entries)) }, singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterPill(word(W.sort_newest), !oldestFirst) { oldestFirst = false }
                                    FilterPill(word(W.sort_oldest), oldestFirst) { oldestFirst = true }
                                }
                                val shownEntries = entries
                                    .filter { e -> entryMatches(e, goodsOf[e.id], entryQuery) }
                                    .let { list -> if (oldestFirst) list.sortedBy { it.timestamp } else list.sortedByDescending { it.timestamp } }
                                PartyLedgerScreen(
                                    name = party.name,
                                    phone = party.phone,
                                    balance = party.balance,
                                    entries = shownEntries,
                                    onGave = { entryWay = true },
                                    onGot = { entryWay = false },
                                    onEntry = { editing = it },
                                    onDeleteParty = { Gates.edit { deletingParty = party } },
                                    onEditParty = {
                                        Gates.edit { scope.launch { editingParty = withContext(Dispatchers.IO) { dao.getParty(party.id) } } }
                                    },
                                    onStatement = { statementFor = party },
                                    modifier = Modifier.weight(1f),
                                    emptyWord = if (entryQuery.isNotBlank() && entries.isNotEmpty()) W.entries_no_match else W.no_entries_yet
                                )
                            }
                        }
                        }
                    }
                }
                }  // the page under its tabs
                }  // the tabs and the page
                }
            }
            }
        }
    }

    // A copy merged in instead of replacing the book (21 Oct 2026): opened from the restore question.
    var merging by remember { mutableStateOf<BackupCodec.ParsedBackup?>(null) }
    merging?.let { data ->
        MergeDialog(
            dao, data, shopName(),
            // The book is kept first, and nothing is merged unless it was (21 Oct 2026: a copy that
            // failed to write was passed over and the merge went ahead regardless).
            keepFirst = { DailyCopies.makeToday(dao, settings, dailyDir, label = "before-merge-" + System.currentTimeMillis()) ?: error(words.text(W.restore_keep_failed)) },
            onDone = { said -> told = words.text(W.merge_title) to said; reload++ },
            onClose = { merging = null }
        )
    }
    when (val p = picked) {
        is Picked.Refused -> AlertDialog(
            onDismissRequest = { picked = null },
            confirmButton = { TextButton({ picked = null }) { Text(word(W.ok)) } },
            text = { Text(word(W.restore_failed, p.why)) }
        )
        is Picked.Ready -> {
          // Whatever the answer, a book that came from a phone is not left on disk (R41, 23 Oct 2026);
          // a backup file the owner chose himself is never touched (forgetIncoming checks the folder).
          val letGo = { PhoneSync.forgetIncoming(home, p.file) }
          // What this restore would take away, in figures, and a tick before Replace (21 Oct 2026).
          val loss = rememberLoss(dao, p.data)
          var agreed by remember(p) { mutableStateOf(false) }
          val heldBy = p.live?.let { o -> businesses.holderOf(o.book, except = activeBusiness) }
          AlertDialog(
            // A live pairing's question is not lost to a click outside it: it comes back at once (23 Oct 2026,
            // the second review: the phone waited 15 minutes for an answer nobody could give).
            onDismissRequest = { if (!busy && p.live == null) { picked = null; letGo() } },
            title = { Text(word(W.restore_warning_title)) },
            text = {
              // The question scrolls, with a bar to say so (25 Oct 2026, the audit): on a laptop the
              // counts of what is replaced and the tick that allows Replace were cut off below the window.
              val restoreScroll = rememberScrollState()
              Box(Modifier.heightIn(max = 340.dp)) {
                Column(Modifier.verticalScroll(restoreScroll).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // A live pairing (22 Oct 2026): keeping this book and opening the phone's beside it comes first.
                    if (p.live != null) Text(word(W.live_pair_warning), color = c.ink, fontWeight = FontWeight.Medium)
                    // This book already keeps its ledger with another phone (22 Oct 2026, the review: a second phone
                    // replaced the first in silence; one book is one phone and one computer — a second phone joins
                    // as its own business).
                    val other = p.live?.let { o -> livePeers.firstOrNull { it.device != o.device } }
                    // The phone's book is kept live by another business here (R29, 23 Oct 2026): that business is
                    // opened instead; joining or replacing here would make two businesses of one book.
                    if (heldBy != null) Text(
                        word(W.live_pair_other_business, FileShopSettings(businesses.settingsFile(heldBy)).businessName()
                            ?: businesses.list().firstOrNull { it.id == heldBy }?.name ?: heldBy.toString()),
                        color = c.owedToMe, fontWeight = FontWeight.SemiBold
                    )
                    else if (other != null) Text(
                        word(W.live_pair_other_phone, other.name?.takeIf { it.isNotBlank() } ?: words.text(W.live_title)),
                        color = c.owedToMe, fontWeight = FontWeight.SemiBold
                    )
                    // A book that syncs with a phone stops syncing once it is replaced (22 Oct 2026, the review).
                    else if (thisBook != null) Text(word(W.restore_stops_live), color = c.ink, fontWeight = FontWeight.Medium)
                    // The four digits the phone shows for this pairing: yes only when they are the same (22 Oct 2026).
                    p.live?.code?.let { code -> Text(word(W.live_pair_code_check, code), color = c.ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                    // Whose book this is and which one it replaces, when the two names differ (R40; the phone's rule).
                    val copyShop = p.data.businessName?.trim()?.takeIf { it.isNotEmpty() }
                    val openShop = shopName()?.trim()?.takeIf { it.isNotEmpty() }
                    if (copyShop != null && openShop != null && !copyShop.equals(openShop, ignoreCase = true))
                        Text(word(W.restore_business_mismatch, copyShop, openShop), color = c.owedToMe, fontWeight = FontWeight.SemiBold)
                    LossNote(loss, agreed) { agreed = it }
                    Text(
                        word(
                            W.restore_warning_message,
                            p.ok.parties, p.ok.entries, p.ok.cheques, p.ok.cash, p.ok.plans, p.ok.bills, p.ok.invoices
                        )
                    )
                }
                androidx.compose.foundation.VerticalScrollbar(
                    androidx.compose.foundation.rememberScrollbarAdapter(restoreScroll),
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
              }
            },
            confirmButton = {
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Or keep this book and add only what the copy has that it lacks.
                // Not for a live pairing: the two devices must start from the very same book.
                if (p.live == null) TextButton(enabled = !busy, onClick = { merging = p.data; picked = null; letGo() }) { Text(word(W.merge_button)) }
                // The business that already keeps this book, opened (R29); nothing here is joined or replaced.
                if (heldBy != null) TextButton(enabled = !busy, onClick = {
                    LiveDesk.offers.value = null
                    picked = null
                    letGo()
                    onSwitch(heldBy)
                }) { Text(word(W.business_switch_open)) }
                // Nothing here is removed: the phone's book becomes a business of its own and that business joins.
                // Not when this computer already keeps this very book: then it is Replace (this copy made the phone's again).
                p.live?.takeIf { it.book != thisBook && heldBy == null }?.let { offer ->
                    TextButton(enabled = !busy, onClick = {
                        // Its own name, with the phone's after it, so the two books never read alike in the list.
                        val name = (p.data.businessName?.takeIf { it.isNotBlank() } ?: words.text(W.live_title)) +
                            offer.name.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        val b = businesses.create(name)
                        LiveDesk.keepPending(offer, p.data)
                        LiveDesk.offers.value = null
                        picked = null
                        letGo()
                        onSwitch(b.id)
                    }) { Text(word(W.live_join_new_business)) }
                }
                if (heldBy == null) TextButton(
                    enabled = !busy && mayReplace(loss, agreed),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                // This book as it stands, kept first: the restore can be undone from Daily copies.
                                // No copy, no restore (21 Oct 2026: a failed copy was passed over in silence and
                                // the book replaced anyway, while the screen promised it could be undone).
                                val kept = runCatching { DailyCopies.makeToday(dao, settings, dailyDir, label = "before-restore-" + System.currentTimeMillis()) }.getOrNull()
                                if (kept == null) {
                                    told = words.text(W.restore_warning_title) to words.text(W.restore_keep_failed)
                                } else {
                                    // A replaced book is no longer the one any phone was syncing: live sync pauses
                                    // first (so the restore's own writes are not sent), stops once the restore went
                                    // through, and a live pairing then joins anew. Run whole even when the screen
                                    // goes (22 Oct 2026, the review: a counter's sign-out after the restore cancelled
                                    // the join, and the phone waited for ever); a failure changes nothing.
                                    val dbFile = businesses.dbFile(activeBusiness).name
                                    val failure = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                                        val sql = db.syncSql()
                                        val wasLive = runCatching { LiveSync.pause(sql) }.getOrDefault(false)
                                        try {
                                            restore(p.data, p.live == null)
                                        } catch (e: Exception) {
                                            if (wasLive) runCatching { LiveSync.resume(sql) }
                                            return@withContext e
                                        }
                                        if (wasLive) LiveSync.disable(sql, LiveSync.OFF_RESTORED)
                                        else if (p.live != null) runCatching { LiveSync.disable(sql) }
                                        com.innovation313.roshankhata.data.NumberMark.mark = null
                                        p.live?.let { offer ->
                                            runCatching { LiveDesk.join(db, home, dbFile, offer) }.exceptionOrNull()?.let { return@withContext it }
                                        }
                                        null
                                    }
                                    if (p.live != null) LiveDesk.offers.value = null
                                    if (failure != null) {
                                        told = words.text(W.restore_warning_title) to
                                            if (p.live != null) words.text(W.live_join_failed, failure.message ?: failure.javaClass.simpleName)
                                            else failText(words, failure)
                                    } else {
                                        if (p.live == null && !LiveDesk.windowOpen()) LiveDesk.stop()
                                        done = true
                                        shop = shopName()
                                        reload++
                                    }
                                }
                            } catch (e: Exception) {
                                told = words.text(W.restore_warning_title) to failText(words, e)
                            } finally {
                                busy = false
                                picked = null
                                letGo()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.restore_replace)) }
              }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    // A live pairing said no to: let go here (the phone gives up waiting on its own).
                    if (p.live != null) LiveDesk.offers.value = null
                    picked = null
                    letGo()
                }) { Text(word(W.cancel)) }
            }
          )
        }
        null -> {}
    }
    if (done) {
        AlertDialog(
            onDismissRequest = { done = false },
            confirmButton = { TextButton({ done = false }) { Text(word(W.ok)) } },
            text = { Text(word(W.restore_file_no_images)) }
        )
    }
    val way = entryWay
    val forParty = parties.firstOrNull { it.id == selected }
    // A new entry asks twice before it saves, as the phone does: the same amount the
    // same way already written today (a double click looks like a second sale), and
    // a credit limit about to be passed. Both are warnings, never blocks.
    var confirming by remember { mutableStateOf<Pair<EntryDraft, String>?>(null) }
    // The open party's own row: its promised payment day (Party.harvestPromise).
    var partyRow by remember { mutableStateOf<Party?>(null) }
    LaunchedEffect(forParty?.id, reload) { partyRow = forParty?.let { p -> withContext(Dispatchers.IO) { dao.getParty(p.id) } } }
    val batchesFor: suspend (String) -> List<com.innovation313.roshankhata.data.BatchOption> = { name ->
        withContext(Dispatchers.IO) {
            // What is left of each batch as the counter would say it (Fefo.options, Marhala 3).
            dao.productByKey(com.innovation313.roshankhata.data.ProductName.key(name))?.let { com.innovation313.roshankhata.data.Fefo.options(dao, it) } ?: emptyList()
        }
    }
    /** After an entry is written: the promised day kept only while money is owed (the phone's saveEntry rule). */
    suspend fun keepPromise(partyId: Long, d: EntryDraft, after: Double) {
        if (partyRow?.harvestPromise != null && !Money.isPositive(after)) dao.setHarvestPromise(partyId, null)
        else if (d.dueTouched) dao.setHarvestPromise(partyId, if (Money.isPositive(after)) d.due else null)
    }
    fun saveNew(d: EntryDraft) {
        val before = forParty?.balance ?: 0.0
        scope.launch {
            withContext(Dispatchers.IO) {
                val id = dao.insertEntryWithItems(d.entry, listOfNotNull(linked(dao, d.item)))
                // Ticked on the form: the money came with the goods, so its cash "I got" is written, paired.
                val gotId = if (d.paidNow && d.entry.isGiven && d.entry.rateType == com.innovation313.roshankhata.data.RateType.CASH)
                    dao.recordCashForSale(id) else null
                val after = if (gotId != null) before else before + if (d.entry.isGiven) d.entry.amount else -d.entry.amount
                keepPromise(d.entry.partyId, d, after)
            }
            entryWay = null; confirming = null
            reload++
        }
    }
    if (way != null && forParty != null) {
        AppDialog(
            onCloseRequest = { entryWay = null },
            onPreviewKeyEvent = escCloses { entryWay = null },
            title = word(if (way) W.you_gave else W.you_got),
            state = rememberDialogState(width = fitW(520.dp), height = fitH(760.dp))
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                EntryEditor(
                    partyId = forParty.id, partyName = forParty.name, isGiven = way,
                    existing = null, existingItem = null,
                    isCustomer = partyRow?.isCustomer ?: true, batchesFor = batchesFor, promise = partyRow?.harvestPromise,
                    onSave = { d ->
                        scope.launch {
                            val e = d.entry
                            val dayStart = com.innovation313.roshankhata.data.Payroll.dayOf(e.timestamp)
                            val (twin, party) = withContext(Dispatchers.IO) {
                                dao.sameDayTwin(e.partyId, e.amount, e.isGiven, dayStart, dayStart + 24L * 60 * 60 * 1000 - 1) to dao.getParty(e.partyId)
                            }
                            val limit = party?.creditLimit
                            val projected = forParty.balance + (if (e.isGiven) e.amount else -e.amount)
                            val warnings = listOfNotNull(
                                twin?.let {
                                    words.text(W.twin_entry_body, Format.money(it.amount), words.text(if (e.isGiven) W.i_gave else W.i_got), Format.dateTime(it.timestamp))
                                },
                                if (e.isGiven && limit != null && limit > 0 && projected > limit && forParty.balance <= limit)
                                    words.text(W.limit_warning_message, forParty.name, Format.money(forParty.balance), Format.money(e.amount), Format.money(projected), Format.money(limit))
                                else null
                            )
                            if (warnings.isEmpty()) saveNew(d) else confirming = d to warnings.joinToString("\n\n")
                        }
                    },
                    onCancel = { entryWay = null }
                )
            }
        }
    }
    confirming?.let { (d, text) ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(word(W.twin_entry_title)) },
            text = { Text(text) },
            confirmButton = { TextButton({ saveNew(d) }) { Text(word(W.twin_entry_add)) } },
            dismissButton = { TextButton({ confirming = null }) { Text(word(W.cancel)) } }
        )
    }
    editing?.let { e ->
        var items by remember(e.id) { mutableStateOf<List<com.innovation313.roshankhata.data.EntryItem>?>(null) }
        // Which record wrote this entry, if any (21 Oct 2026): a bill's, a plan's, a cheque's or a
        // ticket's entry is changed there, so the two never disagree. "" = its own entry.
        var owner by remember(e.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(e.id) {
            owner = withContext(Dispatchers.IO) { runCatching { dao.entryOwner(e.id) }.getOrNull() } ?: ""
            items = withContext(Dispatchers.IO) { dao.itemsOfEntry(e.id) }
        }
        val loaded = items
        val by = owner
        if (by != null && by.isNotEmpty()) AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(word(W.entry_linked_title)) },
            text = { Text(word(com.innovation313.roshankhata.data.EntryOwner.wordFor(by))) },
            confirmButton = { TextButton({ editing = null }) { Text(word(W.ok)) } }
        )
        else if (loaded != null && by != null) AppDialog(
            onCloseRequest = { editing = null },
            onPreviewKeyEvent = escCloses { editing = null },
            title = word(W.edit),
            state = rememberDialogState(width = fitW(520.dp), height = fitH(760.dp))
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                EntryEditor(
                    partyId = e.partyId, partyName = forParty?.name.orEmpty(), isGiven = e.isGiven,
                    existing = e, existingItem = loaded.firstOrNull(), otherItems = loaded.drop(1),
                    isCustomer = partyRow?.isCustomer ?: true, batchesFor = batchesFor, promise = partyRow?.harvestPromise,
                    onSave = { d -> Gates.edit {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // The phone's edit: the entry and its goods lines, all or nothing.
                                // Only the first line is on the form; any others the phone wrote stay.
                                dao.updateEntryWithItems(d.entry, listOfNotNull(linked(dao, d.item)) + loaded.drop(1))
                                dao.audit(AuditActs.ENTRY_EDITED, "transactions", d.entry.id, auditAmount(d.entry.amount))
                                val sign = { x: LedgerEntry -> if (x.isGiven) x.amount else -x.amount }
                                if (d.dueTouched) keepPromise(e.partyId, d, (forParty?.balance ?: 0.0) - sign(e) + sign(d.entry))
                            }
                            editing = null
                            reload++
                        }
                    } },
                    onCancel = { editing = null },
                    onDelete = { Gates.edit { deleting = e; editing = null } },
                    onPrint = { scope.launch {
                        // The balance this entry left, not today's (25 Oct 2026, the audit): an old receipt or a part
                        // payment printed whatever the account stood at when the button was pressed.
                        val after = withContext(Dispatchers.IO) { runCatching { dao.balanceAfterEntry(e) }.getOrNull() }
                        // Every goods line goes on the receipt (25 Oct 2026, the audit: only the first one did).
                        val goodsLines = loaded.filter { !it.itemName.isNullOrBlank() }.map { i ->
                            ThermalReceipt.Line(
                                listOfNotNull(i.itemName, i.quantity?.let { q -> com.innovation313.roshankhata.ui.Calc.trim(q) + (com.innovation313.roshankhata.data.UnitWords.label(i.unit).let { u -> if (u.isEmpty()) "" else " $u" }) },
                                    i.rate?.let { r -> "@ " + Format.money(r) }, words.text(W.bonus_tag).takeIf { i.isBonus }).joinToString("  "), 20f
                            )
                        }
                        receipt = ThermalReceipt.Job(listOfNotNull(
                            ThermalReceipt.Line(shopName() ?: words.text(W.app_name), 26f, bold = true, centre = true),
                            ThermalReceipt.Line(Format.dateTime(e.timestamp) + "  " + e.entryNumber, 18f, centre = true),
                            ThermalReceipt.Line(""),
                            ThermalReceipt.Line(Format.name(forParty?.name.orEmpty()), 24f, bold = true),
                            forParty?.phone?.let { ThermalReceipt.Line(Format.ltr(it), 18f) },
                            ThermalReceipt.Line(words.text(if (e.isGiven) W.you_gave else W.you_got), 22f),
                            ThermalReceipt.Line(Format.money(e.amount), 34f, bold = true, centre = true)
                        ) + goodsLines + listOfNotNull(
                            e.note?.takeIf { it.isNotBlank() }?.let { ThermalReceipt.Line(it, 18f) },
                            ThermalReceipt.Line(""),
                            // The customer reads this: his side in his own words, never a bare figure (ReceiptBalance).
                            after?.let { b ->
                                ThermalReceipt.Line(words.text(W.balance_label) + ": " + com.innovation313.roshankhata.data.ReceiptBalance.text(words, b), 22f, bold = true)
                            },
                            ThermalReceipt.Line(words.text(W.app_name), 16f, centre = true)
                        ))
                    } }
                )
            }
        }
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(word(W.delete_entry_title)) },
            text = { Text(word(W.delete_entry_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launch {
                            // Half of a cash sale: ask about the other half, as the phone does.
                            val pair = withContext(Dispatchers.IO) {
                                e.pairedEntryId?.let { dao.getEntry(it) }?.takeIf { !it.isDeleted }
                            }
                            if (pair != null) {
                                halves = e to pair
                            } else {
                                withContext(Dispatchers.IO) { dao.softDeleteEntry(e.id); dao.audit(AuditActs.ENTRY_DELETED, "transactions", e.id, auditAmount(e.amount)) }
                                reload++
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.delete)) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(word(W.cancel)) } }
        )
    }
    halves?.let { (e, pair) ->
        AlertDialog(
            onDismissRequest = { halves = null },
            title = { Text(word(W.delete_pair_title)) },
            text = { Text(word(W.delete_pair_message, pair.entryNumber)) },
            confirmButton = {
                TextButton({
                    halves = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            dao.softDeleteEntry(e.id); dao.softDeleteEntry(pair.id)
                            dao.audit(AuditActs.ENTRY_DELETED, "transactions", e.id, auditAmount(e.amount)); dao.audit(AuditActs.ENTRY_DELETED, "transactions", pair.id)
                        }
                        reload++
                    }
                }) { Text(word(W.delete_pair_both)) }
            },
            dismissButton = {
                TextButton({
                    halves = null
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.softDeleteEntry(e.id); dao.audit(AuditActs.ENTRY_DELETED, "transactions", e.id, auditAmount(e.amount)) }
                        reload++
                    }
                }) { Text(word(W.delete_pair_one)) }
            }
        )
    }
    if (deletingMany) {
        val gone = parties.filter { it.id in chosenIds }
        val owing = gone.filter { !Money.isZero(it.balance) }
        AlertDialog(
            onDismissRequest = { deletingMany = false },
            title = { Text(plural(WP.delete_parties_title, gone.size, gone.size)) },
            text = {
                Text(
                    plural(WP.delete_parties_confirm, gone.size, gone.size) + if (owing.isEmpty()) "" else "\n\n" + plural(
                        WP.delete_parties_owing, owing.size, owing.size,
                        Format.money(owing.filter { Money.isPositive(it.balance) }.sumOf { it.balance }),
                        Format.money(owing.filter { Money.isNegative(it.balance) }.sumOf { -it.balance })
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingMany = false
                        scope.launch {
                            // The phone's bulk delete: every one and their entries, one timestamp, one transaction.
                            // A customer with posted counter tickets stays, and is named (22 Oct 2026, the review).
                            val kept = withContext(Dispatchers.IO) { dao.softDeleteParties(gone.map { it.id }) }
                            if (selected?.let { it in chosenIds && it !in kept } == true) selected = null
                            chosenIds = emptySet(); selecting = false
                            if (kept.isNotEmpty()) told = words.text(W.entry_linked_title) to
                                words.text(W.party_has_tickets, gone.filter { it.id in kept }.joinToString(", ") { com.innovation313.roshankhata.ui.Format.isolate(it.name) })
                            reload++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.delete)) }
            },
            dismissButton = { TextButton({ deletingMany = false }) { Text(word(W.cancel)) } }
        )
    }
    deletingParty?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingParty = null },
            title = { Text(word(W.delete_party_title)) },
            text = { Text(word(W.delete_party_confirm, p.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingParty = null
                        scope.launch {
                            // The phone's delete: the customer and their entries, one timestamp, one transaction.
                            val kept = withContext(Dispatchers.IO) { dao.softDeleteParties(listOf(p.id)) }
                            if (kept.isNotEmpty()) told = words.text(W.entry_linked_title) to
                                words.text(W.party_has_tickets, com.innovation313.roshankhata.ui.Format.isolate(p.name))
                            else selected = null
                            reload++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.delete)) }
            },
            dismissButton = { TextButton({ deletingParty = null }) { Text(word(W.cancel)) } }
        )
    }
    picking?.let { pick ->
        val close = { picking = null }
        val title = word(pick.word)
        AppDialog(onCloseRequest = close, onPreviewKeyEvent = escCloses(close), title = title, state = rememberDialogState(width = fitW(520.dp), height = fitH(520.dp))) {
            DialogFrame(title, onClose = close, footer = {
                FluentButton(word(W.cancel), close, kind = ButtonKind.STANDARD)
                FluentButton(title, {
                    val id = pickedFor
                    if (id != null) {
                        picking = null
                        selected = id
                        when (pick) {
                            HomePick.GAVE -> { section = Section.KHATA; entryWay = true }
                            HomePick.GOT -> { section = Section.KHATA; entryWay = false }
                            HomePick.STATEMENT -> parties.firstOrNull { it.id == id }?.let { statementFor = it }
                        }
                    }
                }, kind = ButtonKind.ACCENT, enabled = pickedFor != null)
            }) {
                PartyPicker(parties, pickedFor, { pickedFor = it }, word(W.customer), word(W.search_parties), Modifier.fillMaxWidth())
            }
        }
    }
    if (liveAsk) OverrideDialog(dao, word(W.live_needs_owner), ownerOnly = true, onClose = { liveAsk = false }) { liveSync = true }
    // The window can also give this book to a new phone: written as a backup is, on this computer's own disk (LiveGive).
    if (liveSync) LiveSyncDialog(db, onClose = { liveSync = false }, export = { f ->
        withContext(Dispatchers.IO) {
            f.bufferedWriter(Charsets.UTF_8).use { BackupCodec.exportTo(db.khataDao(), settings.businessName(), BackupCodec.profileToJson(settings), it) }
        }
    })
    // A live pairing just finished in this book (here or as a new business): every page reads again.
    LaunchedEffect(Unit) { LiveDesk.joined.drop(1).collect { shop = shopName(); reload++ } }
    if (phoneSync) {
        // The phone may also take this computer's book (Backup → Take the computer's book): the
        // same text a backup file holds, made fresh when asked.
        val giveBook: () -> ByteArray? = {
            runCatching {
                val out = java.io.StringWriter()
                kotlinx.coroutines.runBlocking { BackupCodec.exportTo(dao, settings.businessName(), BackupCodec.profileToJson(settings), out) }
                out.toString().toByteArray(Charsets.UTF_8)
            }.getOrNull()
        }
        PhoneSyncDialog(words, home, onClose = { phoneSync = false }, give = giveBook) { file ->
            phoneSync = false
            // The same confirmation as a backup file chosen by hand: nothing is replaced before the owner's yes.
            scope.launch {
                val (result, data) = withContext(Dispatchers.IO) { BackupCodec.parseFile(file) }
                picked = when {
                    result is BackupCodec.ImportResult.Ok && data != null -> Picked.Ready(file, data, result)
                    result is BackupCodec.ImportResult.Failed -> Picked.Refused(reasonOf(result, words))
                    else -> Picked.Refused(words.text(W.backup_err_unreadable))
                }
                // A book that could not be read has no question to wait for (R41).
                if (picked !is Picked.Ready) PhoneSync.forgetIncoming(home, file)
            }
        }
    }
    if (bin) {
        AppDialog(
            onCloseRequest = { bin = false },
            onPreviewKeyEvent = escCloses { bin = false },
            title = word(W.recycle_bin),
            state = rememberDialogState(width = fitW(560.dp), height = fitH(640.dp))
        ) {
            val binParties by remember(reload) { dao.observeDeletedParties() }.collectAsState(emptyList())
            val binEntries by remember(reload) { dao.observeDeletedEntries() }.collectAsState(emptyList())
            RecycleBinScreen(
                parties = binParties,
                entries = binEntries,
                partyName = { id -> parties.firstOrNull { it.id == id }?.name.orEmpty() },
                onRestoreParty = { p ->
                    scope.launch {
                        // The phone's restore: the customer and the entries binned with them.
                        withContext(Dispatchers.IO) {
                            dao.restoreParty(p.id)
                            p.deletedAt?.let { dao.restoreEntriesOfParty(p.id, it) }
                        }
                        reload++
                    }
                },
                onRestoreEntry = { e ->
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.restoreEntry(e.id) }
                        reload++
                    }
                },
                onPurgeParty = { p ->
                    // The money first, as on the phone (25 Oct 2026, the audit): a customer who still owes,
                    // or is owed, is never deleted forever without the figure in front of the owner.
                    scope.launch {
                        val bal = withContext(Dispatchers.IO) { runCatching { dao.binnedPartyBalance(p.id) }.getOrNull() }
                        val ask = words.text(W.delete_party_forever_confirm, p.name)
                        val money = bal?.takeIf { Money.isNotZero(it) }?.let { b ->
                            "\n\n" + words.text(if (Money.isPositive(b)) W.bin_party_owed_to_me else W.bin_party_i_owe, Format.money(b))
                        }.orEmpty()
                        purging = Pair(ask + money, suspend {
                            if (dao.purgeParty(p.id) > 0) null else words.text(W.bin_party_kept)
                        })
                    }
                },
                onPurgeEntry = { e -> purging = Pair(words.text(W.delete_entry_forever_confirm, e.entryNumber), suspend { dao.purgeEntry(e.id); null }) },
                onEmpty = {
                    purging = Pair(words.text(W.empty_bin_confirm), suspend {
                        // Entries binned on their own first; a binned customer's go with him (the cascade).
                        dao.purgeAllEntries(); dao.purgeAllParties()
                        dao.binnedPartyCount().takeIf { it > 0 }?.let { words.text(W.bin_emptied_kept, it) }
                    })
                }
            )
            purging?.let { (text, go) ->
                AlertDialog(
                    onDismissRequest = { purging = null },
                    title = { Text(word(W.delete_forever)) },
                    text = { Text(text) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                purging = null
                                scope.launch {
                                    val said = withContext(Dispatchers.IO) { runCatching { go() }.getOrElse { words.text(W.backup_failed) } }
                                    reload++
                                    if (said != null) told = words.text(W.delete_forever) to said
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                        ) { Text(word(W.delete_forever)) }
                    },
                    dismissButton = { TextButton({ purging = null }) { Text(word(W.cancel)) } }
                )
            }
        }
    }
    if (addParty || editingParty != null) {
        val existing = editingParty
        AppDialog(
            onCloseRequest = { addParty = false; editingParty = null },
            onPreviewKeyEvent = escCloses { addParty = false; editingParty = null },
            title = word(if (existing == null) W.add_party else W.edit_party),
            state = rememberDialogState(width = fitW(500.dp), height = fitH(680.dp))
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                PartyForm(
                    existing = existing,
                    book = parties,
                    onSave = { p ->
                        scope.launch {
                            val id = withContext(Dispatchers.IO) {
                                if (existing == null) dao.insertParty(p) else { dao.updateParty(p); p.id }
                            }
                            addParty = false; editingParty = null
                            reload++
                            selected = id
                            section = Section.KHATA
                        }
                    },
                    onCancel = { addParty = false; editingParty = null },
                    home = home,
                    businessId = activeBusiness
                )
            }
        }
    }
    if (reporting) ReportProblemDialog(words, home) { reporting = false }
    receipt?.let { job ->
        val lines = job.lines
        // The receipt as a picture too (the phone's EntryDetail share), for WhatsApp or a folder.
        val savePicture = {
            receipt = null
            val dialog = FileDialog(null as Frame?, words.text(W.receipt_save_picture), FileDialog.SAVE)
            startIn(dialog, "Pictures")
            dialog.file = "RoshanKhata_Receipt.png"
            dialog.isVisible = true
            val name = dialog.file
            if (name != null) {
                val file = File(dialog.directory, if (name.endsWith(".png", true)) name else "$name.png")
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching { javax.imageio.ImageIO.write(ThermalReceipt.image(lines, rtl, printer.width, receiptLogo().takeIf { printer.logo }), "png", file) }.isSuccess
                    }
                    told = words.text(W.print_receipt) to (if (ok) words.text(W.report_saved_to, file.absolutePath) else words.text(W.backup_failed))
                }
            }
        }
        val names = remember(job) { ThermalReceipt.printers() }
        fun printOn(name: String) {
            receipt = null
            scope.launch {
                val ok = withContext(Dispatchers.IO) { ThermalReceipt.send(name, ThermalReceipt.bytes(lines, rtl, printer, receiptLogo(), cash = job.cash)) }
                if (ok) ThermalReceipt.lastPrinter = name
                told = words.text(W.print_receipt) to words.text(if (ok) W.print_done else W.print_failed)
            }
        }
        // A counter ticket goes to the printer used last, without asking, while Windows still has it.
        val auto = if (job.direct) ThermalReceipt.lastPrinter?.takeIf { it in names } else null
        if (auto != null) LaunchedEffect(job) { printOn(auto) }
        else PrinterChoiceDialog(names, onCancel = { receipt = null }, onSavePicture = savePicture) { name -> printOn(name) }
    }
    importing?.let { (fresh, skipped) ->
        ImportPreviewDialog(fresh, skipped, onCancel = { importing = null }) { rows ->
            importing = null
            scope.launch {
                told = words.text(W.import_csv) to runCatching {
                    withContext(Dispatchers.IO) { dao.importOpeningBalances(rows, words.text(W.pdf_stmt_opening)) }
                }.map { words.text(W.import_csv_done, it) }.getOrElse { failText(words, it) }
                reload++
            }
        }
    }
    told?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { told = null },
            title = { Text(title) },
            confirmButton = { TextButton({ told = null }) { Text(word(W.ok)) } },
            text = { Text(text) }
        )
    }
    backedUp?.let { text ->
        AlertDialog(
            onDismissRequest = { backedUp = null },
            title = { Text(word(W.backup_done_title)) },
            confirmButton = { TextButton({ backedUp = null }) { Text(word(W.ok)) } },
            text = { Text(text) }
        )
    }
    if (choosingLanguage) {
        AppDialog(
            onCloseRequest = { choosingLanguage = false },
            onPreviewKeyEvent = escCloses { choosingLanguage = false },
            title = word(W.language),
            state = rememberDialogState(width = fitW(360.dp), height = fitH(560.dp))
        ) {
            Column(Modifier.fillMaxSize().background(c.surface).padding(12.dp)) {
                AppLanguages.ALL.forEach { lang ->
                    Text(
                        lang.nativeName,
                        Modifier.fillMaxWidth()
                            .press { choosingLanguage = false; onLanguage(lang.tag) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = c.ink, fontSize = 18.sp
                    )
                    Rule()
                }
            }
        }
    }
    // The phone's first-run question (ShopNameActivity): until a name is written the app shows its
    // own name where the shop's belongs — header, invoices, statements, cards. Asked on each
    // opening until a name is written; "Later" leaves it for Business Profile.
    // The phone's first run, step 2 before the name (ShopTypeActivity): the shop's trade, now
    // required (the owner, 20 Oct) — every unit, example and suggestion follows it.
    val tradeMandatory = Trades.of(settings.tradeKey()) == null
    if (choosingTrade) {
        AppDialog(
            onCloseRequest = { if (!tradeMandatory) choosingTrade = false },
            onPreviewKeyEvent = escCloses { if (!tradeMandatory) choosingTrade = false },
            title = word(W.trade_pick_title),
            // The first shop type must be chosen: no ✕ (Esc already did nothing then).
            closable = !tradeMandatory,
            state = rememberDialogState(width = fitW(900.dp), height = fitH(760.dp))
        ) {
            TradePicker(
                current = settings.tradeKey(), otherName = settings.otherTradeName(), otherItems = settings.otherTradeItems(),
                mandatory = tradeMandatory,
                onPick = { key, otherName, otherItems ->
                    settings.setTradeKey(key)
                    if (key == "OTHER") settings.setOtherTrade(otherName, otherItems)
                    choosingTrade = false
                    onTradeChanged()
                },
                onCancel = { choosingTrade = false }
            )
        }
    }
    var namingShop by remember { mutableStateOf(shop.isNullOrBlank()) }
    // No shortcut while a question in this window waits (R156, 23 Oct 2026): F2 or Ctrl+N stacked a form
    // over "Delete these customers?". Every AlertDialog of this window is named here; the app's dialogs
    // (AppDialog, drawn in the window since Marhala 2) are counted by OpenDialogs, which KeyActions reads.
    // One lambda for this book's life: it reads the states themselves, so it always sees the current answer.
    val questionOpen: () -> Boolean = remember {
        {
            picked != null || done || confirming != null || editing != null || deleting != null || halves != null ||
                deletingMany || deletingParty != null || told != null || backedUp != null || clockBehind != null || namingShop
        }
    }
    keys.busy = questionOpen
    // A book that leaves (a switch, the lock) does not keep the keys shut; a newer book's gate is left alone.
    DisposableEffect(Unit) { onDispose { if (keys.busy === questionOpen) keys.busy = { false } } }
    if (namingShop && !choosingTrade) {
        var typed by remember { mutableStateOf("") }
        // The shop's country, asked with its name (21 Oct 2026): the computer's own region when it is
        // one we know, else what the language suggests. Business Profile changes it later.
        val locale = java.util.Locale.getDefault()
        var country by remember {
            mutableStateOf(com.innovation313.roshankhata.data.ShopCountry.guess(locale.country.ifBlank { null }, settings.currency(), locale.toLanguageTag()))
        }
        AlertDialog(
            onDismissRequest = { namingShop = false },
            title = { Text(word(W.shop_name_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Outlined(
                        typed, { typed = it }, singleLine = true,
                        label = { Text(word(W.shop_name_field)) }, placeholder = { Text(word(W.shop_name_hint)) }
                    )
                    Text(word(W.country_ask_title), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.innovation313.roshankhata.data.ShopCountry.choices(locale).forEach { ctry ->
                            val name = if (ctry == com.innovation313.roshankhata.data.ShopCountry.OTHER) word(W.country_other) else ctry.displayName(locale)
                            FluentPill(name, ctry == country) { country = ctry }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = typed.isNotBlank(), onClick = {
                    settings.setBusinessName(typed.trim())
                    settings.setCountry(country)
                    if (settings.currency() == com.innovation313.roshankhata.data.Currency.DEFAULT && country != com.innovation313.roshankhata.data.ShopCountry.OTHER) settings.setCurrency(country.currencySign)
                    if (country.calendar != com.innovation313.roshankhata.data.ShopCalendar.Kind.GREGORIAN && settings.calendar() == com.innovation313.roshankhata.data.ShopCalendar.Kind.GREGORIAN) settings.setCalendar(country.calendar)
                    settings.refreshDisplay()
                    shop = shopName()
                    namingShop = false
                }) { Text(word(W.save)) }
            },
            dismissButton = { TextButton({ namingShop = false }) { Text(word(W.welcome_skip)) } }
        )
    }
    // The phone's first-run walkthrough, once, after the trade and the name are settled.
    var touring by remember { mutableStateOf(!appPrefs.tourSeen()) }
    if (touring && !choosingTrade && !namingShop) {
        AppDialog(
            onCloseRequest = { appPrefs.setTourSeen(); touring = false },
            onPreviewKeyEvent = escCloses { appPrefs.setTourSeen(); touring = false },
            title = word(W.app_name),
            state = rememberDialogState(width = fitW(620.dp), height = fitH(420.dp))
        ) {
            TourWindowContent { appPrefs.setTourSeen(); touring = false }
        }
    }
    // Every PDF / Statement / Print button opens its sheet here first (1.8.4).
    PreviewHost()
    if (lockSettings) {
        AppDialog(onCloseRequest = { lockSettings = false },onPreviewKeyEvent = escCloses { lockSettings = false }, title = word(W.app_lock), state = rememberDialogState(width = fitW(520.dp), height = fitH(460.dp))) {
            AppLockSettings(appPrefs) { lockSettings = false }
        }
    }
    if (showAlerts) {
        AppDialog(
            onCloseRequest = { showAlerts = false },
            onPreviewKeyEvent = escCloses { showAlerts = false },
            title = word(W.na_title),
            state = rememberDialogState(width = fitW(520.dp), height = fitH(600.dp))
        ) {
            AlertsList(bookAlerts, extraAlerts) { s -> showAlerts = false; section = s }
        }
    }
    if (choosingTextSize) {
        AppDialog(
            onCloseRequest = { choosingTextSize = false },
            onPreviewKeyEvent = escCloses { choosingTextSize = false },
            title = word(W.text_size),
            state = rememberDialogState(width = fitW(360.dp), height = fitH(340.dp))
        ) {
            Column(Modifier.fillMaxSize().background(c.surface).padding(12.dp)) {
                TEXT_SIZE_WORDS.forEachIndexed { level, name ->
                    Text(
                        (if (level == textSize) "\u2713  " else "     ") + word(name),
                        Modifier.fillMaxWidth()
                            .press { choosingTextSize = false; onTextSize(level) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = if (level == textSize) c.brandGreenText else c.ink,
                        // Each line in its own size, so the owner sees what it will look like.
                        fontSize = (16 * TEXT_FACTORS[level]).sp,
                        fontWeight = if (level == textSize) FontWeight.Bold else FontWeight.Normal
                    )
                    Rule()
                }
            }
        }
    }
    if (choosingTheme) {
        AppDialog(
            onCloseRequest = { choosingTheme = false },
            onPreviewKeyEvent = escCloses { choosingTheme = false },
            title = word(W.theme),
            state = rememberDialogState(width = fitW(360.dp), height = fitH(300.dp))
        ) {
            Column(Modifier.fillMaxSize().background(c.surface).padding(12.dp)) {
                ThemeChoice.entries.forEach { t ->
                    Text(
                        (if (t == theme) "\u2713  " else "     ") + word(t.word),
                        Modifier.fillMaxWidth()
                            .press { choosingTheme = false; onTheme(t) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = if (t == theme) c.brandGreenText else c.ink, fontSize = 18.sp,
                        fontWeight = if (t == theme) FontWeight.Bold else FontWeight.Normal
                    )
                    Rule()
                }
            }
        }
    }
    if (about) {
        AppDialog(
            onCloseRequest = { about = false },
            onPreviewKeyEvent = escCloses { about = false },
            title = word(W.about_us),
            state = rememberDialogState(width = fitW(520.dp), height = fitH(680.dp))
        ) {
            AboutScreen(
                icon = painterResource("roshan_khata_icon.png"),
                versionName = System.getProperty("jpackage.app-version") ?: "1.0.0",
                supportEmail = "innovation313.support@gmail.com",
                onPrivacyPolicy = {
                    runCatching {
                        java.awt.Desktop.getDesktop()
                            .browse(java.net.URI("https://aliehtisham052-dotcom.github.io/RoshanKhata/privacy-policy.html"))
                    }
                }
            )
        }
    }
}

/** Opens [dialog] in the user's own [folder] (Documents, Downloads) when it exists. */
internal fun startIn(dialog: FileDialog, folder: String) {
    val dir = File(System.getProperty("user.home"), folder)
    if (dir.isDirectory) dialog.directory = dir.absolutePath
}

@Composable
private fun Total(label: String, amount: String, color: Color, modifier: Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier.clip(Look.card).background(Look.layer).border(1.dp, Look.strokeCard, Look.card).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, color = c.inkSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(amount, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** A text button's padding where several share one line. */
private val compactButton = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

/** The whole ledger, or a period by two dates (the end date's whole day), before a statement prints. */
@Composable
private fun StatementRangeDialog(
    partyName: String,
    /** The customer's entries, for the phone's Report screen figures before any PDF exists. */
    entries: List<LedgerEntry>,
    onCancel: () -> Unit,
    onPrint: (from: Long?, till: Long?) -> Unit
) {
    val c = LocalRoshanColors.current
    var from by remember { mutableStateOf<Long?>(null) }
    var till by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    val day = 24L * 60 * 60 * 1000
    val bad = from != null && till != null && till!! < from!!
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(word(W.pdf_stmt_title, partyName)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ picking = true }) { Text(from?.let { Format.dateOnly(it) } ?: word(W.pick_start_date), color = c.brandGreenText) }
                    Text("\u2013", color = c.textMuted)
                    TextButton({ picking = false }) { Text(till?.let { Format.dateOnly(it) } ?: word(W.pick_end_date), color = c.brandGreenText) }
                }
                Text(
                    if (from == null || till == null) word(W.range_label_all) else word(W.range_label, Format.dateOnly(from!!), Format.dateOnly(till!!)),
                    color = c.ink
                )
                if (bad) Text(word(W.invalid_range), color = c.owedToMe)
                // The phone's Report screen (ReportActivity): what was given and got in the window and
                // its net, then the closing balance of the WHOLE account — a part-period total is not
                // what the customer owes.
                if (!bad) {
                    val f = from; val t = till
                    val window = if (f == null || t == null) null else f..(t + day - 1)
                    val inWindow = entries.filter { window == null || it.timestamp in window }
                    val gave = inWindow.filter { it.isGiven }.sumOf { it.amount }
                    val got = inWindow.filter { !it.isGiven }.sumOf { it.amount }
                    val closing = entries.sumOf { if (it.isGiven) it.amount else -it.amount }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                    if (window != null && inWindow.isEmpty()) {
                        Text(word(W.no_entries_in_range), color = c.textMuted)
                    } else {
                        Row { Text(word(W.i_gave), Modifier.weight(1f), color = c.inkSoft); Text(Format.money(gave), color = c.owedToMe, fontWeight = FontWeight.Bold) }
                        Row { Text(word(W.i_got), Modifier.weight(1f), color = c.inkSoft); Text(Format.money(got), color = c.iOwe, fontWeight = FontWeight.Bold) }
                        // With the side in words (25 Oct 2026, the audit): a bare "- Rs 5,000" read as money owed out.
                        Row { Text(word(W.net_change), Modifier.weight(1f), color = c.inkSoft); Text(Format.money(gave - got) + "  ·  " + word(com.innovation313.roshankhata.screens.balanceSideWord(gave - got)), color = balanceColour(gave - got), fontWeight = FontWeight.Bold) }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(
                        word(if (window == null) W.closing_note_all else W.closing_note_range, Format.money(closing) + " · " + word(com.innovation313.roshankhata.screens.balanceSideWord(closing))),
                        color = c.ink, fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !bad, onClick = {
                val f = from; val t = till
                if (f == null || t == null) onPrint(null, null) else onPrint(f, t + day - 1)
            }) { Text(word(W.ledger_pdf_button)) }
        },
        dismissButton = {
            Row {
                TextButton({ from = null; till = null }) { Text(word(W.range_all)) }
                FluentButton(word(W.cancel), onCancel, kind = ButtonKind.STANDARD)
            }
        }
    )
    picking?.let { start ->
        DayPicker(if (start) from else till, onPick = { d -> if (start) from = d else till = d; picking = null }, onDismiss = { picking = null })
    }
}

/** A goods line tied to its product by name, as the phone ties it (ProductName.key), when that product exists. */
private suspend fun linked(dao: KhataDao, item: com.innovation313.roshankhata.data.EntryItem?): com.innovation313.roshankhata.data.EntryItem? {
    val i = item ?: return null
    if (i.productId != null) return i
    val name = i.itemName ?: return i
    return i.copy(productId = dao.productByKey(com.innovation313.roshankhata.data.ProductName.key(name))?.id)
}

@Composable
private fun AlertChip(text: String, onClick: () -> Unit) {
    val c = LocalRoshanColors.current
    Text(
        text,
        Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(Look.getTint).press(androidx.compose.foundation.shape.CircleShape, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = c.owedToMe, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
    )
}

/** What each shortcut does on the open page; set by the page, read by the window. */
class KeyActions {
    var gave: () -> Unit = {}
    var got: () -> Unit = {}
    var newParty: () -> Unit = {}
    var statement: () -> Unit = {}
    var khata: () -> Unit = {}
    var search: () -> Unit = {}
    /** The open page's own keys (the counter's F-keys), asked first; null when the page has none. */
    var page: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null
    /** True while a question of the window is open (R156): no shortcut then, the counter's included. */
    var busy: () -> Boolean = { false }

    fun handle(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return false
        // The dialogs are drawn in this window since Marhala 2: their keys pass here first.
        if (busy() || OpenDialogs.count > 0) return false
        if (page?.invoke(e) == true) return true
        val k = e.key
        val ctrl = e.isCtrlPressed
        val act: (() -> Unit)? = when {
            k == androidx.compose.ui.input.key.Key.F1 -> khata
            k == androidx.compose.ui.input.key.Key.F2 -> gave
            k == androidx.compose.ui.input.key.Key.F3 -> got
            ctrl && k == androidx.compose.ui.input.key.Key.N -> newParty
            ctrl && k == androidx.compose.ui.input.key.Key.P -> statement
            ctrl && k == androidx.compose.ui.input.key.Key.F -> search
            else -> null
        }
        act?.invoke()
        return act != null
    }
}

/**
 * How many customers who owe are paying later than their own habit — the
 * phone's Home note (MainActivity.observeLate), the same rule as Follow-up:
 * a customer who named a day still ahead is not late.
 */
private fun lateCount(dao: KhataDao): kotlinx.coroutines.flow.Flow<Int> =
    dao.observePartiesWithBalance()
        .combine(dao.observeOwingLedgerPoints()) { parties, points -> parties to points }
        .combine(dao.observePromises()) { (parties, points), promiseRows ->
            val owing = parties.filter { Money.isPositive(it.balance) }.map { it.id }.toSet()
            val habits = com.innovation313.roshankhata.data.PaymentHabit.forAll(points)
            val promises = promiseRows.associate { it.partyId to it.due }
            val now = System.currentTimeMillis()
            val tz = java.util.TimeZone.getDefault()
            habits.keys.count { id ->
                id in owing && com.innovation313.roshankhata.data.FollowUpRank.lateByHabit(id, habits, promises, now, tz)
            }
        }
        .flowOn(Dispatchers.Default)

/**
 * The open customer's promised day and last seasons, in the phone's words
 * (PartyDetail.refreshSeasonRecord, SeasonText.record): customers only, the
 * three newest seasons, "cleared by harvest", "cleared N days after" or "due".
 */
@Composable
private fun seasonRecord(dao: KhataDao, party: PartyWithBalance, reload: Int): String {
    var rows by remember(party.id, reload) { mutableStateOf<List<com.innovation313.roshankhata.data.SeasonBook.PartySeason>>(emptyList()) }
    var promise by remember(party.id, reload) { mutableStateOf<Long?>(null) }
    LaunchedEffect(party.id, reload) {
        withContext(Dispatchers.IO) {
            val p = dao.getParty(party.id)
            promise = p?.harvestPromise
            rows = if (p?.isCustomer == true) runCatching {
                com.innovation313.roshankhata.data.SeasonBook.book(dao.seasonLinesOf(party.id))
            }.getOrDefault(emptyList()) else emptyList()
        }
    }
    val seasons = rows.sortedByDescending { it.season }.take(3).map { r ->
        val n = seasonName(r.season)
        val at = r.clearedAt
        if (at == null) word(W.season_rec_due, n, Format.money(r.outstanding))
        else {
            val d = com.innovation313.roshankhata.data.SeasonBook.daysBetween(com.innovation313.roshankhata.data.SeasonBook.harvestEnd(r.season), at)
            if (d > 0) plural(WP.season_rec_after, d, n, d) else word(W.season_rec_before, n)
        }
    }
    return listOfNotNull(promise?.let { word(W.harvest_promise_header, Format.dateOnly(it)) })
        .plus(seasons).joinToString("  \u00b7  ")
}

/**
 * A customer's entry against the page's search (the phone's rule): its note,
 * its goods, its number or its amount; Urdu, Persian or Hindi digits typed are
 * read as 0-9. Goods since 25 Oct 2026 (the audit: the box said "Notes or
 * goods" and searched notes only).
 */
internal fun entryMatches(e: com.innovation313.roshankhata.data.LedgerEntry, goods: String?, query: String): Boolean {
    val q = com.innovation313.roshankhata.data.Digits.toLatin(query.trim()).lowercase()
    if (q.isEmpty()) return true
    return e.note?.lowercase()?.contains(q) == true || goods?.lowercase()?.contains(q) == true ||
        e.entryNumber.lowercase().contains(q) || Format.money(e.amount).contains(q) ||
        com.innovation313.roshankhata.ui.Calc.trim(e.amount).contains(q)
}
