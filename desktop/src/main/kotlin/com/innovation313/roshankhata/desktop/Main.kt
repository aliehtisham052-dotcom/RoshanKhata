package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
fun main() {
    val home = DesktopDatabase.home()
    // Any crash is written down before anything else runs (Report a problem shows it).
    CrashLog.install(home)
    application { app(home) }
}

@Composable
private fun androidx.compose.ui.window.ApplicationScope.app(home: File) {
    // The open business: its own book and its own shop settings. Switching
    // closes the old book (DisposableEffect) before the new one is used.
    val businesses = remember { Businesses(home) }
    var active by remember { mutableStateOf(businesses.activeId()) }
    val settings = remember(active) { FileShopSettings(businesses.settingsFile(active)).also { it.refreshDisplay() } }
    // Once a day, a checked copy of the open book (the phone's Snapshots); a failure never stops the app.
    val dailyDir = remember(active) { DailyCopies.dir(home, active) }
    // The app's language: the owner's choice (app.properties), else the computer's.
    val appPrefs = remember { AppPrefs(File(home, "app.properties")) }
    // The book, checked first (BookHealth): a damaged file is set aside and the newest good daily copy comes back.
    val opened = remember(active) {
        val outside = appPrefs.backupFolder()?.let { DailyCopies.outside(it, active) }
        BookHealth.open(businesses.dbFile(active), settings, listOfNotNull(dailyDir, outside))
    }
    val db = opened.db
    var recoveryTold by remember(active) { mutableStateOf(opened.corruptKept == null) }
    DisposableEffect(db) { onDispose { db.close() } }
    // The shop's logo, signature, stamp and payment QR for its invoices, one set per business.
    val images = remember(active) { com.innovation313.roshankhata.desktop.invoice.ProfileImages.forBusiness(home, active) }
    LaunchedEffect(db) { runCatching { DailyCopies.makeToday(db.khataDao(), settings, dailyDir) } }
    var language by remember { mutableStateOf(appPrefs.language() ?: Locale.getDefault().toLanguageTag()) }
    // Light, dark or as Windows is: the phone's Theme choice, kept for every shop on this computer.
    var theme by remember { mutableStateOf(appPrefs.theme()) }
    var textSize by remember { mutableStateOf(appPrefs.textSize()) }
    var invoiceFields by remember { mutableStateOf(appPrefs.invoiceFields()) }
    // App Lock: with a PIN set, nothing of the book is composed until it is typed (LockScreen).
    var unlocked by remember { mutableStateOf(!appPrefs.hasPin()) }
    // The shop's trade (its words, units and which pages show), re-read when the owner changes it.
    var tradeTick by remember { mutableIntStateOf(0) }
    val trade = remember(active, tradeTick) { TradeKit.of(settings.tradeKey(), settings.otherTradeItems()) }
    // The second place for the copies (USB, Google Drive folder): today's copy there on opening,
    // and again on closing so the day's last work leaves the computer too. A missing stick
    // never stops the app; the Daily copies page says it could not be reached.
    var outsideFolder by remember { mutableStateOf(appPrefs.backupFolder()) }
    val outsideDir = outsideFolder?.let { DailyCopies.outside(it, active) }
    LaunchedEffect(db, outsideDir) {
        if (outsideDir != null) runCatching { DailyCopies.makeToday(db.khataDao(), settings, outsideDir, refresh = true) }
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
    val locale = remember(language) { Locale.forLanguageTag(language).also { Locale.setDefault(it) } }
    val words = remember(locale) { desktopWords(locale) }
    val rtl = BidiPort.isRtlLocale(locale)
    // Keyboard shortcuts for the counter (Phase 9a): the open page fills these in.
    val keys = remember { KeyActions() }
    Window(
        onCloseRequest = {
            Reminders.remove()
            outsideDir?.let { dir ->
                runCatching { kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(20_000) { DailyCopies.makeToday(db.khataDao(), settings, dir, refresh = true) } } }
            }
            db.close(); exitApplication()
        },
        onPreviewKeyEvent = { keys.handle(it) },
        title = words.text(W.app_name),
        icon = painterResource("roshan_khata_icon.png"),
        // Opened filling the screen: a laptop's 768 rows otherwise cut off the entry buttons.
        state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1100.dp, height = 740.dp)
    ) {
        val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
        RoshanTheme(dark = when (theme) { ThemeChoice.SYSTEM -> systemDark; ThemeChoice.LIGHT -> false; ThemeChoice.DARK -> true }) {
            ProvideWords(words) {
                // The owner's text size on top of Windows' own scale, as the phone multiplies its own.
                val screen = androidx.compose.ui.platform.LocalDensity.current
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(screen.density, screen.fontScale * TEXT_FACTORS[textSize])
                ) {
                    if (!unlocked) LockScreen(appPrefs) { unlocked = true }
                    else key(active) { CompositionLocalProvider(LocalTrade provides trade) { Book(
                        keys = keys,
                        dao = db.khataDao(),
                        load = { db.khataDao().partiesWithBalanceOnce() },
                        shopName = { settings.businessName() },
                        settings = settings,
                        restore = { data -> restore(data, db, settings) },
                        backup = { file -> backup(file, db, settings) },
                        onLanguage = { tag -> appPrefs.setLanguage(tag); language = tag },
                        theme = theme,
                        onTheme = { t -> appPrefs.setTheme(t); theme = t },
                        textSize = textSize,
                        onTextSize = { level -> appPrefs.setTextSize(level); textSize = level },
                        invoiceFields = invoiceFields,
                        onInvoiceFields = { f -> appPrefs.setInvoiceFields(f); invoiceFields = f },
                        rtl = rtl,
                        words = words,
                        businesses = businesses,
                        activeBusiness = active,
                        onSwitch = { id -> businesses.switchTo(id); active = id },
                        dailyDir = dailyDir,
                        images = images,
                        home = home,
                        outsideFolder = outsideFolder,
                        outsideDir = outsideDir,
                        onBackupFolder = { f -> appPrefs.setBackupFolder(f); outsideFolder = f },
                        onTradeChanged = { tradeTick++ },
                        appPrefs = appPrefs
                    ) } }
                    if (!recoveryTold) {
                        // The book was damaged and rebuilt (or could not be): said once, before any work.
                        val from = opened.recoveredFrom
                        AlertDialog(
                            onDismissRequest = { recoveryTold = true },
                            title = { Text(word(W.book_recovered_title)) },
                            text = {
                                Text(
                                    (if (from != null) words.text(W.book_recovered_from, Format.dateTime(from.lastModified()))
                                    else words.text(W.book_recovered_none)) + "\n\n" + words.text(W.book_corrupt_kept, opened.corruptKept?.absolutePath.orEmpty())
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
    data class Ready(val file: File, val data: BackupCodec.ParsedBackup, val ok: BackupCodec.ImportResult.Ok) : Picked
    data class Refused(val why: String) : Picked
}

/** The whole book to [file] in the phone's format, streamed; returns the file's size. */
private suspend fun backup(
    file: File,
    db: com.innovation313.roshankhata.data.KhataDatabase,
    settings: FileShopSettings
): Long = withContext(Dispatchers.IO) {
    file.bufferedWriter(Charsets.UTF_8).use {
        BackupCodec.exportTo(db.khataDao(), settings.businessName(), BackupCodec.profileToJson(settings), it)
    }
    file.length()
}

/** The ledger in one transaction, then the shop's details — the phone's order. */
private suspend fun restore(
    data: BackupCodec.ParsedBackup,
    db: com.innovation313.roshankhata.data.KhataDatabase,
    settings: FileShopSettings
) = withContext(Dispatchers.IO) {
    BackupCodec.restoreLedger(db.khataDao(), data)
    data.businessProfile?.let { BackupCodec.restoreProfile(settings, it) }
}

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
    dao: KhataDao,
    load: suspend () -> List<PartyWithBalance>,
    shopName: () -> String?,
    settings: FileShopSettings,
    restore: suspend (BackupCodec.ParsedBackup) -> Unit,
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
    // A receipt waiting for the owner to pick the thermal printer (Phase 9e).
    var receipt by remember { mutableStateOf<List<ThermalReceipt.Line>?>(null) }
    var halves by remember { mutableStateOf<Pair<LedgerEntry, LedgerEntry>?>(null) }
    var bin by remember { mutableStateOf(false) }
    // The phone's book over WiFi (Backup → Send to computer on the phone): the QR window is open.
    var phoneSync by remember { mutableStateOf(false) }
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
    // Ctrl+F: the Khata page's search box takes the typing.
    var searchTick by remember { mutableIntStateOf(0) }
    val searchFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var editingParty by remember { mutableStateOf<Party?>(null) }
    var purging by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    // The bin keeps things for the days its note promises, then lets them go, as on the phone.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            runCatching { dao.purgeOldEntries(cutoff); dao.purgeOldParties(cutoff) }
        }
    }
    var choosingBusiness by remember { mutableStateOf(false) }
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
    if (choosingBusiness) BusinessesDialog(businesses, activeBusiness, onSwitch = { choosingBusiness = false; onSwitch(it) }, onClose = { choosingBusiness = false })
    var shop by remember { mutableStateOf(shopName()) }
    // The header's backup action, reached from the bell's row too (set where the action is built).
    var backupHook by remember { mutableStateOf({}) }
    fun backupNowLater() = backupHook()

    LaunchedEffect(reload) { parties = withContext(Dispatchers.IO) { load() } }
    LaunchedEffect(reload, section) {
        alerts = withContext(Dispatchers.IO) {
            runCatching { com.innovation313.roshankhata.data.LedgerAlerts.compute(words, dao) { Format.money(it) } }.getOrDefault(emptyList())
        }
    }
    LaunchedEffect(reload, outsideDir) {
        val newest = withContext(Dispatchers.IO) {
            (DailyCopies.list(dailyDir) + (outsideDir?.let { DailyCopies.list(it) } ?: emptyList())).maxOfOrNull { it.lastModified() }
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
    var reporting by remember { mutableStateOf(false) }
    // The bell's rows beyond the ledger's own: the backup's age and a newer version (the phone's HomeAlerts adds the same).
    val backupOld = lastBackupAt == null || System.currentTimeMillis() - lastBackupAt!! > Reminders.BACKUP_WEEK_MS
    val extraAlerts = listOfNotNull(
        if (backupOld) ExtraAlert(Section.DAILY, words.text(W.notif_backup_title),
            lastBackupAt?.let { words.text(W.na_backup, ((System.currentTimeMillis() - it) / (24L * 60 * 60 * 1000)).toInt()) } ?: words.text(W.notif_backup_body)) { backupNowLater() } else null,
        newerVersion?.let { v -> ExtraAlert(Section.HELP, words.text(W.update_available, v), words.text(W.update_open)) { UpdateCheck.openPage() } }
    )

    Column(Modifier.fillMaxSize().background(Look.base)) {
        // What needs the owner today, as the phone's Home: cheques due, stock expiring
        // inside the warning window, and customers paying later than their habit.
        val now = remember { System.currentTimeMillis() }
        val dueCheques by remember { dao.observeDueChequeCount(now) }.collectAsState(0)
        val expiring by remember { dao.observeExpiringCount(now + com.innovation313.roshankhata.data.ExpiryWindow.WARN_MS) }.collectAsState(0)
        val late by remember(reload) { lateCount(dao) }.collectAsState(0)
        fun backupNow() {
            val dialog = FileDialog(null as Frame?, words.text(W.save_backup), FileDialog.SAVE)
            // The phone's file name and .txt (WhatsApp sends .txt; the content is JSON).
            // Documents, not the program's own folder: an update or uninstall must not take the backup with it.
            startIn(dialog, "Documents")
            dialog.file = "RoshanKhata_Backup_" +
                java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH).format(java.util.Date()) + ".txt"
            dialog.isVisible = true
            val name = dialog.file ?: return
            val file = File(dialog.directory, name)
            scope.launch {
                backedUp = runCatching { backup(file) }
                    .map { size -> lastBackupAt = System.currentTimeMillis(); words.text(W.backup_saved_at, file.absolutePath, (size / 1024).toInt()) }
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
        // The phone's Home header, made wide: the shop, then backup, phone, bell, More, Add Party.
        AppHeader(
            shopName = shop ?: word(W.app_name),
            logo = Photos.bitmap(images.file(com.innovation313.roshankhata.desktop.invoice.ProfileImages.Kind.LOGO).absolutePath, 96),
            onShop = { choosingBusiness = true },
            backupLabel = lastBackupAt?.let { word(W.business_last_backup, Format.dateTime(it)) } ?: word(W.backup_now),
            onBackup = { backupNow() },
            onPhone = { phoneSync = true },
            alerts = alerts.size + extraAlerts.size,
            onAlerts = { showAlerts = true },
            // The phone's More sheet, in its groups: Data, Display, Help.
            menu = listOf(
                MenuLine(word(W.settings_group_data), null),
                MenuLine(word(W.restore_from_file)) { restoreFromFile() },
                MenuLine(word(W.businesses_title)) { choosingBusiness = true },
                MenuLine(word(W.recycle_bin)) { bin = true },
                MenuLine(word(W.snapshots_title)) { section = Section.DAILY },
                MenuLine(word(W.settings_group_display), null),
                MenuLine(word(W.language)) { choosingLanguage = true },
                MenuLine(word(W.theme)) { choosingTheme = true },
                MenuLine(word(W.text_size)) { choosingTextSize = true },
                MenuLine(word(W.business_profile)) { section = Section.SHOP },
                MenuLine(word(W.trade_kicker_change)) { choosingTrade = true },
                MenuLine(word(W.settings_group_security), null),
                MenuLine(word(W.app_lock)) { lockSettings = true },
                MenuLine(word(W.settings_group_help), null),
                MenuLine(word(W.help_support)) { section = Section.HELP },
                MenuLine(word(W.report_problem)) { reporting = true },
                MenuLine(word(W.about_us)) { about = true }
            ),
            onAddParty = { addParty = true }
        )

        Row(Modifier.fillMaxSize()) {
            SectionRail(section, { section = it }, badges = mapOf(Section.FOLLOWUP to late))
            // A page eases in over the last (Motion.PAGE ms) instead of snapping, as Windows' own apps do.
            // The content area (Fluent's layer-alt, 1.10.0): a shade lighter than the base,
            // its top start corner rounded against the rail, as Windows 11's own NavigationView.
            androidx.compose.animation.Crossfade(
                section,
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(topStart = 8.dp)).background(Look.layerAlt)
                    .border(1.dp, Look.strokeCard, RoundedCornerShape(topStart = 8.dp)),
                androidx.compose.animation.core.tween(Motion.PAGE), label = "page"
            ) { page ->
                when (page) {
                    Section.HOME -> Box(Modifier.fillMaxSize()) {
                        HomePane(
                            parties = parties, late = late, alerts = alerts, extra = extraAlerts,
                            figuresOf = { homeFigures(dao) }, reload = reload, shopName = shop,
                            onOpen = { section = it },
                            onAction = { a -> if (a == HomeAction.BACKUP) backupNow() else bin = true },
                            onCustomer = { id -> selected = id; section = Section.KHATA },
                            onAddParty = { addParty = true },
                            // You got: the Khata page with its F3 (the page fills keys.got in once it is open).
                            onGot = { section = Section.KHATA }
                        )
                    }
                    Section.COUNTER -> Box(Modifier.fillMaxSize()) {
                        CounterSalePane(
                            dao, parties, words, rtl, shop, com.innovation313.roshankhata.data.Currency.symbol, reload,
                            onChanged = { reload++ }, onReceipt = { receipt = it }, keys = keys
                        )
                    }
                    Section.CASHBOOK -> Box(Modifier.fillMaxSize()) { CashbookPane(dao) }
                    Section.CHEQUES -> Box(Modifier.fillMaxSize()) { ChequesPane(dao, parties, words) { reload++ } }
                    Section.PLANS -> Box(Modifier.fillMaxSize()) { PlansPane(dao, parties, words) { reload++ } }
                    Section.EXPIRY -> Box(Modifier.fillMaxSize()) { ExpiryPane(dao) }
                    Section.BILLS -> Box(Modifier.fillMaxSize()) { BillsPane(dao, parties, words, home) { reload++ } }
                    Section.PRODUCTS -> Box(Modifier.fillMaxSize()) { ProductsPane(dao, words) }
                    Section.INVOICES -> Box(Modifier.fillMaxSize()) { InvoicesPane(dao, parties, words, rtl, shop, settings.businessAddress(), designs = { com.innovation313.roshankhata.desktop.invoice.Context(words, settings, images, rtl) }, fields = invoiceFields, onFields = onInvoiceFields) }
                    Section.INSIGHTS -> Box(Modifier.fillMaxSize()) { InsightsPane(dao, reload) }
                    Section.ZAKAT -> Box(Modifier.fillMaxSize()) { ZakatPane(dao) }
                    Section.RATES -> Box(Modifier.fillMaxSize()) { RateListPane(dao, words, rtl, shop) }
                    Section.STAFF -> Box(Modifier.fillMaxSize()) { StaffPane(dao) }
                    Section.FOLLOWUP -> Box(Modifier.fillMaxSize()) { FollowUpPane(dao, parties, words, shop) }
                    Section.CALC -> Box(Modifier.fillMaxSize()) { CalculatorPane() }
                    Section.SCHEMES -> Box(Modifier.fillMaxSize()) { SchemesPane(dao, reload) }
                    Section.CASHFLOW -> Box(Modifier.fillMaxSize()) { CashFlowPane(dao, reload) }
                    Section.BUY_RATES -> Box(Modifier.fillMaxSize()) { SupplierRatesPane(dao, reload) }
                    Section.DUPLICATES -> Box(Modifier.fillMaxSize()) { DuplicatesPane(dao, parties) { reload++ } }
                    Section.SEASONS -> Box(Modifier.fillMaxSize()) { SeasonsPane(dao, parties, reload) }
                    Section.HELP -> Box(Modifier.fillMaxSize()) { HelpPane(words) }
                    Section.REGISTER -> Box(Modifier.fillMaxSize()) { RegisterPane(dao, words, rtl, shop, reload) }
                    Section.STOCK -> Box(Modifier.fillMaxSize()) { StockPane(dao, words, rtl, shop, reload) }
                    Section.DAILY -> Box(Modifier.fillMaxSize()) { DailyCopiesPane(dao, settings, dailyDir, outsideFolder, outsideDir, onFolder = onBackupFolder) { shop = shopName(); reload++ } }
                    Section.REPORT -> Box(Modifier.fillMaxSize()) { ReportPane(dao, words, rtl, shop, reload) }
                    Section.SHOP -> Box(Modifier.fillMaxSize()) { ShopDetailsPane(settings, images) { shop = shopName() } }
                    Section.CARD -> Box(Modifier.fillMaxSize()) { BusinessCardPane(settings, images, home, activeBusiness, words) }
                    Section.POSTER -> Box(Modifier.fillMaxSize()) { PosterPane(settings, images, home, activeBusiness, words) }
                    Section.KHATA -> Column(Modifier.fillMaxSize()) {
                        // The page's head (1.10.0): the count and the two totals in one line, Add Party on the end side.
                        val allGet = parties.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
                        val allGive = parties.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }
                        PageHeader(
                            word(W.nav_khata),
                            plural(WP.customer_count, parties.size, parties.size) + " · " + word(W.you_will_get) + " " + Format.money(allGet) + " · " + word(W.you_will_give) + " " + Format.money(allGive),
                            Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
                        ) {
                            FluentButton(word(W.add_party), { addParty = true }, kind = ButtonKind.ACCENT, icon = "ic_add_plain")
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
                                Total(word(W.you_will_get), Format.money(get), c.owedToMe, Modifier.weight(1f))
                                Total(word(W.you_will_give), Format.money(give), c.iOwe, Modifier.weight(1f))
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
                            val shown = parties.shownBy(query, partySide, partyType, partySort, partyWhen.window())
                            if (selecting) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp)
                                        .background(Look.chosen, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(word(W.selected_count, chosenIds.size), Modifier.weight(1f), color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    TextButton({ chosenIds = shown.map { it.id }.toSet() }) { Text(word(W.select_all), color = c.brandGreenText) }
                                    TextButton({ deletingMany = true }, enabled = chosenIds.isNotEmpty()) { Text(word(W.delete), color = c.owedToMe) }
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
                                TextButton({
                                    val dialog = FileDialog(null as Frame?, words.text(W.export_excel), FileDialog.SAVE)
                                    startIn(dialog, "Documents")
                                    dialog.file = "RoshanKhata_Customers_" +
                                        java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(java.util.Date()) + ".csv"
                                    dialog.isVisible = true
                                    val name = dialog.file ?: return@TextButton
                                    val file = File(dialog.directory, if (name.endsWith(".csv", true)) name else "$name.csv")
                                    val headers = listOf(words.text(W.name), words.text(W.bp_phone), words.text(W.net_balance))
                                    scope.launch {
                                        told = words.text(W.export_excel) to runCatching {
                                            withContext(Dispatchers.IO) { file.writeText(BookExport.customersCsv(parties, headers), Charsets.UTF_8) }
                                        }.map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { it.message ?: it.toString() }
                                    }
                                }, enabled = parties.isNotEmpty(), contentPadding = compactButton) { Text(word(W.export_excel), color = c.brandGreenText, fontSize = 13.sp, maxLines = 1) }
                                TextButton({
                                    val dialog = FileDialog(null as Frame?, words.text(W.import_csv), FileDialog.LOAD)
                                    startIn(dialog, "Downloads")
                                    dialog.isVisible = true
                                    val name = dialog.file ?: return@TextButton
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
                                }, contentPadding = compactButton) { Text(word(W.import_csv), color = c.brandGreenText, fontSize = 13.sp, maxLines = 1) }
                                TextButton({
                                    scope.launch {
                                        val owing = parties.filter { kotlin.math.abs(it.balance) >= 0.005 }.sortedBy { it.name.lowercase() }
                                        val sheets = withContext(Dispatchers.IO) {
                                            owing.map { p ->
                                                StatementPrint(words, rtl, shopName() ?: words.text(W.app_name), p.name, p.phone,
                                                    dao.latestEntriesWithItems(p.id, Int.MAX_VALUE))
                                            }
                                        }
                                        if (sheets.isNotEmpty()) StatementPrint.show(AllStatements(sheets), words.text(W.statement_pdf))
                                    }
                                }, enabled = parties.any { kotlin.math.abs(it.balance) >= 0.005 }, contentPadding = compactButton) {
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
                                val entries by remember(party.id, reload) { dao.observeEntries(party.id) }.collectAsState(emptyList())
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
                                val eq = entryQuery.trim().lowercase()
                                val shownEntries = entries
                                    .filter { e ->
                                        eq.isEmpty() || (e.note?.lowercase()?.contains(eq) == true) ||
                                            e.entryNumber.lowercase().contains(eq) || Format.money(e.amount).contains(eq)
                                    }
                                    .let { list -> if (oldestFirst) list.sortedBy { it.timestamp } else list.sortedByDescending { it.timestamp } }
                                PartyLedgerScreen(
                                    name = party.name,
                                    phone = party.phone,
                                    balance = party.balance,
                                    entries = shownEntries,
                                    onGave = { entryWay = true },
                                    onGot = { entryWay = false },
                                    onEntry = { editing = it },
                                    onDeleteParty = { deletingParty = party },
                                    onEditParty = {
                                        scope.launch { editingParty = withContext(Dispatchers.IO) { dao.getParty(party.id) } }
                                    },
                                    onStatement = { statementFor = party },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }

    when (val p = picked) {
        is Picked.Refused -> AlertDialog(
            onDismissRequest = { picked = null },
            confirmButton = { TextButton({ picked = null }) { Text(word(W.ok)) } },
            text = { Text(word(W.restore_failed, p.why)) }
        )
        is Picked.Ready -> AlertDialog(
            onDismissRequest = { if (!busy) picked = null },
            title = { Text(word(W.restore_warning_title)) },
            text = {
                Text(
                    word(
                        W.restore_warning_message,
                        p.ok.parties, p.ok.entries, p.ok.cheques, p.ok.cash, p.ok.plans, p.ok.bills, p.ok.invoices
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            restore(p.data)
                            busy = false
                            picked = null
                            done = true
                            shop = shopName()
                            reload++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.restore_replace)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { picked = null }) { Text(word(W.cancel)) } }
        )
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
            dao.productByKey(com.innovation313.roshankhata.data.ProductName.key(name))?.let { dao.batchOptionsForProduct(it.id) } ?: emptyList()
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
        DialogWindow(
            onCloseRequest = { entryWay = null },
            onPreviewKeyEvent = escCloses { entryWay = null },
            title = word(if (way) W.you_gave else W.you_got),
            state = rememberDialogState(width = 520.dp, height = 760.dp)
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
        LaunchedEffect(e.id) { items = withContext(Dispatchers.IO) { dao.itemsOfEntry(e.id) } }
        val loaded = items
        if (loaded != null) DialogWindow(
            onCloseRequest = { editing = null },
            onPreviewKeyEvent = escCloses { editing = null },
            title = word(W.edit),
            state = rememberDialogState(width = 520.dp, height = 760.dp)
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                EntryEditor(
                    partyId = e.partyId, partyName = forParty?.name.orEmpty(), isGiven = e.isGiven,
                    existing = e, existingItem = loaded.firstOrNull(),
                    isCustomer = partyRow?.isCustomer ?: true, batchesFor = batchesFor, promise = partyRow?.harvestPromise,
                    onSave = { d ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // The phone's edit: the entry and its goods lines, all or nothing.
                                // Only the first line is on the form; any others the phone wrote stay.
                                dao.updateEntryWithItems(d.entry, listOfNotNull(linked(dao, d.item)) + loaded.drop(1))
                                val sign = { x: LedgerEntry -> if (x.isGiven) x.amount else -x.amount }
                                if (d.dueTouched) keepPromise(e.partyId, d, (forParty?.balance ?: 0.0) - sign(e) + sign(d.entry))
                            }
                            editing = null
                            reload++
                        }
                    },
                    onCancel = { editing = null },
                    onDelete = { deleting = e; editing = null },
                    onPrint = {
                        val item = loaded.firstOrNull()
                        receipt = listOfNotNull(
                            ThermalReceipt.Line(shopName() ?: words.text(W.app_name), 26f, bold = true, centre = true),
                            ThermalReceipt.Line(Format.dateTime(e.timestamp) + "  " + e.entryNumber, 18f, centre = true),
                            ThermalReceipt.Line(""),
                            ThermalReceipt.Line(Format.name(forParty?.name.orEmpty()), 24f, bold = true),
                            forParty?.phone?.let { ThermalReceipt.Line(it, 18f) },
                            ThermalReceipt.Line(words.text(if (e.isGiven) W.you_gave else W.you_got), 22f),
                            ThermalReceipt.Line(Format.money(e.amount), 34f, bold = true, centre = true),
                            item?.takeIf { !it.itemName.isNullOrBlank() }?.let { i ->
                                ThermalReceipt.Line(
                                    listOfNotNull(i.itemName, i.quantity?.let { q -> com.innovation313.roshankhata.ui.Calc.trim(q) + (i.unit?.let { u -> " $u" } ?: "") },
                                        i.rate?.let { r -> "@ " + Format.money(r) }).joinToString("  "), 20f
                                )
                            },
                            e.note?.takeIf { it.isNotBlank() }?.let { ThermalReceipt.Line(it, 18f) },
                            ThermalReceipt.Line(""),
                            forParty?.let { p -> ThermalReceipt.Line(words.text(W.net_balance) + ": " + Format.money(p.balance), 22f, bold = true) },
                            ThermalReceipt.Line(words.text(W.app_name), 16f, centre = true)
                        )
                    }
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
                                withContext(Dispatchers.IO) { dao.softDeleteEntry(e.id) }
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
                        withContext(Dispatchers.IO) { dao.softDeleteEntry(e.id); dao.softDeleteEntry(pair.id) }
                        reload++
                    }
                }) { Text(word(W.delete_pair_both)) }
            },
            dismissButton = {
                TextButton({
                    halves = null
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.softDeleteEntry(e.id) }
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
                            withContext(Dispatchers.IO) { dao.softDeleteParties(gone.map { it.id }) }
                            if (selected?.let { it in chosenIds } == true) selected = null
                            chosenIds = emptySet(); selecting = false
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
                            withContext(Dispatchers.IO) { dao.softDeleteParties(listOf(p.id)) }
                            selected = null
                            reload++
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = c.owedToMe)
                ) { Text(word(W.delete)) }
            },
            dismissButton = { TextButton({ deletingParty = null }) { Text(word(W.cancel)) } }
        )
    }
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
            }
        }
    }
    if (bin) {
        DialogWindow(
            onCloseRequest = { bin = false },
            onPreviewKeyEvent = escCloses { bin = false },
            title = word(W.recycle_bin),
            state = rememberDialogState(width = 560.dp, height = 640.dp)
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
                onPurgeParty = { p -> purging = Pair(words.text(W.delete_party_forever_confirm, p.name), suspend { dao.purgeParty(p.id) }) },
                onPurgeEntry = { e -> purging = Pair(words.text(W.delete_entry_forever_confirm, e.entryNumber), suspend { dao.purgeEntry(e.id) }) },
                onEmpty = { purging = Pair(words.text(W.empty_bin_confirm), suspend { dao.purgeAllEntries(); dao.purgeAllParties() }) }
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
                                scope.launch { withContext(Dispatchers.IO) { go() }; reload++ }
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
        DialogWindow(
            onCloseRequest = { addParty = false; editingParty = null },
            onPreviewKeyEvent = escCloses { addParty = false; editingParty = null },
            title = word(if (existing == null) W.add_party else W.edit_party),
            state = rememberDialogState(width = 500.dp, height = 680.dp)
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
                    home = home
                )
            }
        }
    }
    if (reporting) ReportProblemDialog(words, home) { reporting = false }
    receipt?.let { lines ->
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
                    val ok = withContext(Dispatchers.IO) { runCatching { javax.imageio.ImageIO.write(ThermalReceipt.image(lines, rtl), "png", file) }.isSuccess }
                    told = words.text(W.print_receipt) to (if (ok) words.text(W.report_saved_to, file.absolutePath) else words.text(W.backup_failed))
                }
            }
        }
        PrinterChoiceDialog(remember { ThermalReceipt.printers() }, onCancel = { receipt = null }, onSavePicture = savePicture) { printer ->
            receipt = null
            scope.launch {
                val ok = withContext(Dispatchers.IO) { ThermalReceipt.send(printer, ThermalReceipt.bytes(lines, rtl)) }
                if (ok) ThermalReceipt.lastPrinter = printer
                told = words.text(W.print_receipt) to words.text(if (ok) W.print_done else W.print_failed)
            }
        }
    }
    importing?.let { (fresh, skipped) ->
        ImportPreviewDialog(fresh, skipped, onCancel = { importing = null }) { rows ->
            importing = null
            scope.launch {
                told = words.text(W.import_csv) to runCatching {
                    withContext(Dispatchers.IO) { dao.importOpeningBalances(rows, words.text(W.pdf_stmt_opening)) }
                }.map { words.text(W.import_csv_done, it) }.getOrElse { it.message ?: it.toString() }
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
        DialogWindow(
            onCloseRequest = { choosingLanguage = false },
            onPreviewKeyEvent = escCloses { choosingLanguage = false },
            title = word(W.language),
            state = rememberDialogState(width = 360.dp, height = 560.dp)
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
        DialogWindow(
            onCloseRequest = { if (!tradeMandatory) choosingTrade = false },
            onPreviewKeyEvent = escCloses { if (!tradeMandatory) choosingTrade = false },
            title = word(W.trade_pick_title),
            state = rememberDialogState(width = 900.dp, height = 760.dp)
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
    if (namingShop && !choosingTrade) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { namingShop = false },
            title = { Text(word(W.shop_name_title)) },
            text = {
                Outlined(
                    typed, { typed = it }, singleLine = true,
                    label = { Text(word(W.shop_name_field)) }, placeholder = { Text(word(W.shop_name_hint)) }
                )
            },
            confirmButton = {
                TextButton(enabled = typed.isNotBlank(), onClick = {
                    settings.setBusinessName(typed.trim())
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
        DialogWindow(
            onCloseRequest = { appPrefs.setTourSeen(); touring = false },
            onPreviewKeyEvent = escCloses { appPrefs.setTourSeen(); touring = false },
            title = word(W.app_name),
            state = rememberDialogState(width = 620.dp, height = 420.dp)
        ) {
            TourWindowContent { appPrefs.setTourSeen(); touring = false }
        }
    }
    // Every PDF / Statement / Print button opens its sheet here first (1.8.4).
    PreviewHost()
    if (lockSettings) {
        DialogWindow(onCloseRequest = { lockSettings = false },onPreviewKeyEvent = escCloses { lockSettings = false }, title = word(W.app_lock), state = rememberDialogState(width = 520.dp, height = 460.dp)) {
            AppLockSettings(appPrefs) { lockSettings = false }
        }
    }
    if (showAlerts) {
        DialogWindow(
            onCloseRequest = { showAlerts = false },
            onPreviewKeyEvent = escCloses { showAlerts = false },
            title = word(W.home_today),
            state = rememberDialogState(width = 520.dp, height = 600.dp)
        ) {
            AlertsList(alerts, extraAlerts) { s -> showAlerts = false; section = s }
        }
    }
    if (choosingTextSize) {
        DialogWindow(
            onCloseRequest = { choosingTextSize = false },
            onPreviewKeyEvent = escCloses { choosingTextSize = false },
            title = word(W.text_size),
            state = rememberDialogState(width = 360.dp, height = 340.dp)
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
        DialogWindow(
            onCloseRequest = { choosingTheme = false },
            onPreviewKeyEvent = escCloses { choosingTheme = false },
            title = word(W.theme),
            state = rememberDialogState(width = 360.dp, height = 300.dp)
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
        DialogWindow(
            onCloseRequest = { about = false },
            onPreviewKeyEvent = escCloses { about = false },
            title = word(W.about_us),
            state = rememberDialogState(width = 520.dp, height = 680.dp)
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
                        Row { Text(word(W.net_change), Modifier.weight(1f), color = c.inkSoft); Text(Format.customerBalance(gave - got), color = balanceColour(gave - got), fontWeight = FontWeight.Bold) }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                    Text(
                        word(if (window == null) W.closing_note_all else W.closing_note_range, Format.customerBalance(closing)),
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

    fun handle(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return false
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
