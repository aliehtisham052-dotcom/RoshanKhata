package com.innovation313.roshankhata.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
fun main() = application {
    val home = DesktopDatabase.home()
    // The open business: its own book and its own shop settings. Switching
    // closes the old book (DisposableEffect) before the new one is used.
    val businesses = remember { Businesses(home) }
    var active by remember { mutableStateOf(businesses.activeId()) }
    val db = remember(active) { DesktopDatabase.open(businesses.dbFile(active)) }
    DisposableEffect(db) { onDispose { db.close() } }
    val settings = remember(active) { FileShopSettings(businesses.settingsFile(active)).also { it.refreshDisplay() } }
    // Once a day, a checked copy of the open book (the phone's Snapshots); a failure never stops the app.
    val dailyDir = remember(active) { DailyCopies.dir(home, active) }
    LaunchedEffect(db) { runCatching { DailyCopies.makeToday(db.khataDao(), settings, dailyDir) } }
    // The app's language: the owner's choice (app.properties), else the computer's.
    val appPrefs = remember { AppPrefs(File(home, "app.properties")) }
    var language by remember { mutableStateOf(appPrefs.language() ?: Locale.getDefault().toLanguageTag()) }
    val locale = remember(language) { Locale.forLanguageTag(language).also { Locale.setDefault(it) } }
    val words = remember(locale) { desktopWords(locale) }
    val rtl = BidiPort.isRtlLocale(locale)
    // Keyboard shortcuts for the counter (Phase 9a): the open page fills these in.
    val keys = remember { KeyActions() }
    Window(
        onCloseRequest = { db.close(); exitApplication() },
        onPreviewKeyEvent = { keys.handle(it) },
        title = words.text(W.app_name),
        icon = painterResource("roshan_khata_icon.png"),
        // Opened filling the screen: a laptop's 768 rows otherwise cut off the entry buttons.
        state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1100.dp, height = 740.dp)
    ) {
        RoshanTheme {
            ProvideWords(words) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    key(active) { Book(
                        keys = keys,
                        dao = db.khataDao(),
                        load = { db.khataDao().partiesWithBalanceOnce() },
                        shopName = { settings.businessName() },
                        settings = settings,
                        restore = { data -> restore(data, db, settings) },
                        backup = { file -> backup(file, db, settings) },
                        onLanguage = { tag -> appPrefs.setLanguage(tag); language = tag },
                        rtl = rtl,
                        words = words,
                        businesses = businesses,
                        activeBusiness = active,
                        onSwitch = { id -> businesses.switchTo(id); active = id },
                        dailyDir = dailyDir
                    ) }
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
    rtl: Boolean,
    words: Words,
    businesses: Businesses,
    activeBusiness: Long,
    onSwitch: (Long) -> Unit,
    dailyDir: File
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
    var choosingLanguage by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LedgerEntry?>(null) }
    var deleting by remember { mutableStateOf<LedgerEntry?>(null) }
    var halves by remember { mutableStateOf<Pair<LedgerEntry, LedgerEntry>?>(null) }
    var bin by remember { mutableStateOf(false) }
    var deletingParty by remember { mutableStateOf<PartyWithBalance?>(null) }
    var section by remember { mutableStateOf(Section.KHATA) }
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
    statementFor?.let { party ->
        StatementRangeDialog(party.name, onCancel = { statementFor = null }) { from, till ->
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

    LaunchedEffect(reload) { parties = withContext(Dispatchers.IO) { load() } }

    Column(Modifier.fillMaxSize().background(c.pageBg)) {
        // The green header, as on the phone: the shop's name and the actions.
        Row(
            Modifier.fillMaxWidth().background(c.sectionKhata).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                shop ?: word(W.app_name),
                Modifier.weight(1f),
                color = c.onDark, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            HeaderButton(word(W.add_party)) { addParty = true }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.backup_now)) {
                val dialog = FileDialog(null as Frame?, words.text(W.save_backup), FileDialog.SAVE)
                // The phone's file name and .txt (WhatsApp sends .txt; the content is JSON).
                // Documents, not the program's own folder: an update or uninstall must not take the backup with it.
                startIn(dialog, "Documents")
                dialog.file = "RoshanKhata_Backup_" +
                    java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ENGLISH).format(java.util.Date()) + ".txt"
                dialog.isVisible = true
                val name = dialog.file ?: return@HeaderButton
                val file = File(dialog.directory, name)
                scope.launch {
                    backedUp = runCatching { backup(file) }
                        .map { size -> words.text(W.backup_saved_at, file.absolutePath, (size / 1024).toInt()) }
                        .getOrElse { words.text(W.backup_failed) }
                }
            }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.restore_from_file)) {
                val dialog = FileDialog(null as Frame?, words.text(W.restore_from_file), FileDialog.LOAD)
                // Downloads: where a backup sent from the phone (WhatsApp, a browser) lands.
                startIn(dialog, "Downloads")
                dialog.isVisible = true
                val name = dialog.file ?: return@HeaderButton
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
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.businesses_title)) { choosingBusiness = true }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.recycle_bin)) { bin = true }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.language)) { choosingLanguage = true }
            Spacer(Modifier.width(8.dp))
            HeaderButton(word(W.about_us)) { about = true }
        }

        Row(Modifier.fillMaxSize()) {
            SectionRail(section, { section = it })
            when (section) {
                Section.CASHBOOK -> Box(Modifier.weight(1f)) { CashbookPane(dao) }
                Section.CHEQUES -> Box(Modifier.weight(1f)) { ChequesPane(dao, parties, words) { reload++ } }
                Section.PLANS -> Box(Modifier.weight(1f)) { PlansPane(dao, parties, words) { reload++ } }
                Section.EXPIRY -> Box(Modifier.weight(1f)) { ExpiryPane(dao) }
                Section.BILLS -> Box(Modifier.weight(1f)) { BillsPane(dao, parties, words) { reload++ } }
                Section.PRODUCTS -> Box(Modifier.weight(1f)) { ProductsPane(dao, words) }
                Section.INVOICES -> Box(Modifier.weight(1f)) { InvoicesPane(dao, parties, words, rtl, shop, settings.businessAddress()) }
                Section.INSIGHTS -> Box(Modifier.weight(1f)) { InsightsPane(dao, reload) }
                Section.ZAKAT -> Box(Modifier.weight(1f)) { ZakatPane(dao) }
                Section.RATES -> Box(Modifier.weight(1f)) { RateListPane(dao, words, rtl, shop) }
                Section.STAFF -> Box(Modifier.weight(1f)) { StaffPane(dao) }
                Section.FOLLOWUP -> Box(Modifier.weight(1f)) { FollowUpPane(dao, parties, words, shop) }
                Section.CALC -> Box(Modifier.weight(1f)) { CalculatorPane() }
                Section.SCHEMES -> Box(Modifier.weight(1f)) { SchemesPane(dao, reload) }
                Section.CASHFLOW -> Box(Modifier.weight(1f)) { CashFlowPane(dao, reload) }
                Section.BUY_RATES -> Box(Modifier.weight(1f)) { SupplierRatesPane(dao, reload) }
                Section.DUPLICATES -> Box(Modifier.weight(1f)) { DuplicatesPane(dao, parties) { reload++ } }
                Section.SEASONS -> Box(Modifier.weight(1f)) { SeasonsPane(dao, parties, reload) }
                Section.HELP -> Box(Modifier.weight(1f)) { HelpPane(words) }
                Section.REGISTER -> Box(Modifier.weight(1f)) { RegisterPane(dao, words, rtl, shop, reload) }
                Section.STOCK -> Box(Modifier.weight(1f)) { StockPane(dao, words, rtl, shop, reload) }
                Section.DAILY -> Box(Modifier.weight(1f)) { DailyCopiesPane(dao, settings, dailyDir) { shop = shopName(); reload++ } }
                Section.REPORT -> Box(Modifier.weight(1f)) { ReportPane(dao, words, rtl, shop, reload) }
                Section.SHOP -> Box(Modifier.weight(1f)) { ShopDetailsPane(settings) { shop = shopName() } }
                Section.KHATA -> Row(Modifier.weight(1f).fillMaxSize()) {
                    // The book on the left, as the phone's Khata list.
                    Column(Modifier.weight(0.42f).fillMaxSize()) {
                        // Totals: what the shop will get, and what it will give.
                        val get = parties.filter { Money.isPositive(it.balance) }.sumOf { it.balance }
                        val give = parties.filter { Money.isNegative(it.balance) }.sumOf { -it.balance }
                        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Total(word(W.you_will_get), Format.money(get), c.owedToMe, Modifier.weight(1f))
                            Total(word(W.you_will_give), Format.money(give), c.iOwe, Modifier.weight(1f))
                        }
                        // What needs the owner today, as the phone's Home chips: cheques due
                        // and stock expiring inside the warning window. Each opens its page.
                        val now = remember { System.currentTimeMillis() }
                        val dueCheques by remember { dao.observeDueChequeCount(now) }.collectAsState(0)
                        val expiring by remember { dao.observeExpiringCount(now + com.innovation313.roshankhata.data.ExpiryWindow.WARN_MS) }.collectAsState(0)
                        if (dueCheques > 0 || expiring > 0) {
                            Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (dueCheques > 0) AlertChip(word(W.today_chip, word(W.nav_cheques), dueCheques)) { section = Section.CHEQUES }
                                if (expiring > 0) AlertChip(word(W.today_chip, word(W.tile_expiry), expiring)) { section = Section.EXPIRY }
                            }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(word(W.search_parties)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        )
                        val q = query.trim().lowercase()
                        // Newest dealing first, as the phone's default (SortMode.RECENT): the customer
                        // just served is at the top, not buried under A-Z.
                        val shown = parties.filter { NameSearch.matches(it.name, it.phone, q) }.sortedByDescending { it.lastActivity }
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                plural(WP.customer_count, shown.size, shown.size),
                                Modifier.weight(1f).padding(vertical = 10.dp),
                                color = c.textMuted, fontSize = 13.sp
                            )
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
                                    backedUp = runCatching {
                                        withContext(Dispatchers.IO) { file.writeText(BookExport.customersCsv(parties, headers), Charsets.UTF_8) }
                                    }.map { words.text(W.report_saved_to, file.absolutePath) }.getOrElse { it.message ?: it.toString() }
                                }
                            }, enabled = parties.isNotEmpty()) { Text(word(W.export_excel), color = c.brandGreenText, fontSize = 13.sp) }
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
                            }, enabled = parties.any { kotlin.math.abs(it.balance) >= 0.005 }) {
                                Text(word(W.statement_pdf) + " · " + word(W.filter_type_all), color = c.brandGreenText, fontSize = 13.sp)
                            }
                        }
                        LazyColumn(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                            items(shown, key = { it.id }) { p ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (p.id == selected) c.brandGreen.copy(alpha = 0.28f) else c.surface)
                                        .clickable { selected = p.id }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(Format.name(p.name), color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        p.phone?.let { Text(Format.ltr(it), color = c.textMuted, fontSize = 13.sp) }
                                    }
                                    Text(
                                        Format.customerBalance(p.balance),
                                        color = balanceColour(p.balance),
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                HorizontalDivider(color = c.pageBg)
                            }
                        }
                    }
                    // The chosen customer's page on the right: the shared ledger screen.
                    val party = parties.firstOrNull { it.id == selected }
                    Box(Modifier.weight(0.58f).fillMaxSize()) {
                        if (party != null) {
                            val entries by remember(party.id, reload) { dao.observeEntries(party.id) }.collectAsState(emptyList())
                            PartyLedgerScreen(
                                name = party.name,
                                phone = party.phone,
                                balance = party.balance,
                                entries = entries.sortedByDescending { it.timestamp },
                                onGave = { entryWay = true },
                                onGot = { entryWay = false },
                                onEntry = { editing = it },
                                onDeleteParty = { deletingParty = party },
                                onEditParty = {
                                    scope.launch { editingParty = withContext(Dispatchers.IO) { dao.getParty(party.id) } }
                                },
                                onStatement = { statementFor = party }
                            )
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
    fun saveNew(d: EntryDraft) {
        scope.launch {
            withContext(Dispatchers.IO) { dao.insertEntryWithItems(d.entry, listOfNotNull(linked(dao, d.item))) }
            entryWay = null; confirming = null
            reload++
        }
    }
    if (way != null && forParty != null) {
        DialogWindow(
            onCloseRequest = { entryWay = null },
            title = word(if (way) W.you_gave else W.you_got),
            state = rememberDialogState(width = 520.dp, height = 760.dp)
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                EntryEditor(
                    partyId = forParty.id, partyName = forParty.name, isGiven = way,
                    existing = null, existingItem = null,
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
            title = word(W.edit),
            state = rememberDialogState(width = 520.dp, height = 760.dp)
        ) {
            Box(Modifier.fillMaxSize().background(LocalRoshanColors.current.surface)) {
                EntryEditor(
                    partyId = e.partyId, partyName = forParty?.name.orEmpty(), isGiven = e.isGiven,
                    existing = e, existingItem = loaded.firstOrNull(),
                    onSave = { d ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // The phone's edit: the entry and its goods lines, all or nothing.
                                // Only the first line is on the form; any others the phone wrote stay.
                                dao.updateEntryWithItems(d.entry, listOfNotNull(linked(dao, d.item)) + loaded.drop(1))
                            }
                            editing = null
                            reload++
                        }
                    },
                    onCancel = { editing = null },
                    onDelete = { deleting = e; editing = null }
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
    if (bin) {
        DialogWindow(
            onCloseRequest = { bin = false },
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
                    onCancel = { addParty = false; editingParty = null }
                )
            }
        }
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
            title = word(W.language),
            state = rememberDialogState(width = 360.dp, height = 560.dp)
        ) {
            Column(Modifier.fillMaxSize().background(c.surface).padding(12.dp)) {
                AppLanguages.ALL.forEach { lang ->
                    Text(
                        lang.nativeName,
                        Modifier.fillMaxWidth()
                            .clickable { choosingLanguage = false; onLanguage(lang.tag) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = c.ink, fontSize = 18.sp
                    )
                    HorizontalDivider(color = c.pageBg)
                }
            }
        }
    }
    if (about) {
        DialogWindow(
            onCloseRequest = { about = false },
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
private fun HeaderButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Total(label: String, amount: String, color: Color, modifier: Modifier) {
    val c = LocalRoshanColors.current
    Column(modifier.background(c.surface, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text(label, color = c.textMuted, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(amount, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** The whole ledger, or a period by two dates (the end date's whole day), before a statement prints. */
@Composable
private fun StatementRangeDialog(partyName: String, onCancel: () -> Unit, onPrint: (from: Long?, till: Long?) -> Unit) {
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
                TextButton(onCancel) { Text(word(W.cancel)) }
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
        Modifier.background(c.gaveFill.copy(alpha = 0.18f), RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = c.owedToMe, fontSize = 13.sp, fontWeight = FontWeight.Bold
    )
}

/** What each shortcut does on the open page; set by the page, read by the window. */
class KeyActions {
    var gave: () -> Unit = {}
    var got: () -> Unit = {}
    var newParty: () -> Unit = {}
    var statement: () -> Unit = {}
    var khata: () -> Unit = {}

    fun handle(e: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return false
        val k = e.key
        val ctrl = e.isCtrlPressed
        val act: (() -> Unit)? = when {
            k == androidx.compose.ui.input.key.Key.F1 -> khata
            k == androidx.compose.ui.input.key.Key.F2 -> gave
            k == androidx.compose.ui.input.key.Key.F3 -> got
            ctrl && k == androidx.compose.ui.input.key.Key.N -> newParty
            ctrl && k == androidx.compose.ui.input.key.Key.P -> statement
            else -> null
        }
        act?.invoke()
        return act != null
    }
}
